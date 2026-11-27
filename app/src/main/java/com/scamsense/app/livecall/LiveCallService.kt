package com.scamsense.app.livecall

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.scamsense.app.MainActivity
import com.scamsense.app.R
import com.scamsense.app.ScamSenseApplication
import com.scamsense.core.engine.RiskLevel
import com.scamsense.core.engine.TextNormalizer
import com.scamsense.core.live.LiveCallMonitor
import com.scamsense.core.live.SpeechChunker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Listens to the other party during a call from an unknown number and warns — or hangs
 * up — as soon as the conversation matches a scam script.
 *
 * Pipeline: Shizuku shell helper (VOICE_DOWNLINK PCM) → pipe → [SpeechChunker] →
 * whisper.cpp (Thai, on device) → [LiveCallMonitor] (same engine as the app) → action.
 * Audio is never written to storage; only the final verdict and a masked transcript
 * preview are kept in the encrypted history.
 */
@RequiresApi(Build.VERSION_CODES.R) // started only on Android 11+ (see ScamCallScreeningService)
class LiveCallService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var audio: ICallAudioService? = null
    private var shizukuConn: ServiceConnection? = null
    private var readPipe: ParcelFileDescriptor? = null
    private var captureJob: Job? = null
    private var sttJob: Job? = null
    private var timeoutJob: Job? = null
    private var hangUpJob: Job? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var monitor = LiveCallMonitor()
    private var caller = ""
    private var answered = false
    private var finished = false
    private var callbackRegistration: Any? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_WATCH -> startWatching(intent.getStringExtra(EXTRA_CALLER).orEmpty())
            ACTION_HANG_UP -> hangUpNow()
            ACTION_CANCEL_HANG_UP -> { hangUpJob?.cancel(); hangUpJob = null; notifyStatus("ยกเลิกการวางสายอัตโนมัติแล้ว", RiskLevel.DANGEROUS, withActions = true) }
            ACTION_STOP -> finish()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    // ------------------------------------------------------------------ lifecycle

    private fun startWatching(display: String) {
        if (answered) return
        caller = display
        ensureChannel(this)
        val n = buildNotification("ScamSense พร้อมฟังสายนี้", "สายจาก ${display.ifBlank { "เบอร์ที่ไม่รู้จัก" }} — จะเริ่มตรวจเมื่อรับสาย", RiskLevel.SAFE, false)
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, n,
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        } catch (_: RuntimeException) {
            stopSelf(); return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            finish(); return
        }
        listenToCallState()
        initTts()
        // Ringing that is never answered: give up after a while.
        timeoutJob = scope.launch { delay(RING_TIMEOUT_MS); if (!answered) finish() }
    }

    private fun onCallState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> if (!answered) { answered = true; timeoutJob?.cancel(); beginCapture() }
            TelephonyManager.CALL_STATE_IDLE -> finish()
        }
    }

    private fun beginCapture() {
        scope.launch {
            val settings = (application as ScamSenseApplication).container.settingsRepository.ensureLoaded()
            monitor = LiveCallMonitor(autoHangUp = settings.liveAutoHangUp)
            val model = WhisperModel.byId(settings.liveModel)
            if (!WhisperModels.isInstalled(this@LiveCallService, model) || !WhisperNative.isAvailable) {
                notifyStatus("ยังฟังไม่ได้: ไม่มีโมเดลแปลงเสียง", RiskLevel.SUSPICIOUS, false); return@launch
            }
            shizukuConn = ShizukuGate.bind(this@LiveCallService) { svc ->
                if (svc == null) {
                    notifyStatus("ยังฟังไม่ได้: Shizuku ไม่ทำงาน", RiskLevel.SUSPICIOUS, false)
                } else {
                    audio = svc
                    scope.launch { stream(svc, model) }
                }
            }
        }
    }

    private suspend fun stream(svc: ICallAudioService, model: WhisperModel) {
        val pipe = ParcelFileDescriptor.createPipe()
        val used = runCatching { svc.startStream(MediaRecorder.AudioSource.VOICE_DOWNLINK, SAMPLE_RATE, pipe[1]) }.getOrDefault(-1)
        runCatching { pipe[1].close() }
        if (used < 0) {
            runCatching { pipe[0].close() }
            notifyStatus("เครื่องนี้ไม่อนุญาตให้ฟังเสียงในสาย", RiskLevel.SUSPICIOUS, false)
            return
        }
        readPipe = pipe[0]
        notifyStatus("กำลังฟังและตรวจสายนี้…", RiskLevel.SAFE, false)

        // Keep at most two utterances queued: if transcription falls behind, prefer fresh speech.
        val utterances = Channel<FloatArray>(2, BufferOverflow.DROP_OLDEST)
        captureJob = scope.launch(Dispatchers.IO) {
            val chunker = SpeechChunker(sampleRate = SAMPLE_RATE)
            val bytes = ByteArray(3_200)
            val shorts = ShortArray(bytes.size / 2 + 1)
            var carry = -1
            try {
                ParcelFileDescriptor.AutoCloseInputStream(pipe[0]).use { input ->
                    while (isActive) {
                        val n = input.read(bytes)
                        if (n < 0) break
                        var count = 0
                        var i = 0
                        if (carry >= 0 && n > 0) { shorts[count++] = ((bytes[0].toInt() shl 8) or carry).toShort(); i = 1; carry = -1 }
                        while (i + 1 < n) { shorts[count++] = ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xff)).toShort(); i += 2 }
                        if (i < n) carry = bytes[i].toInt() and 0xff
                        chunker.feed(shorts, count).forEach { utterances.trySend(it) }
                    }
                }
            } catch (_: java.io.IOException) {
                // helper closed the pipe (call ended)
            }
            chunker.flush()?.let { utterances.trySend(it) }
            utterances.close()
        }
        sttJob = scope.launch {
            val whisper = WhisperNative.load(WhisperModels.file(this@LiveCallService, model).path)
            if (whisper == null) {
                notifyStatus("โหลดโมเดลแปลงเสียงไม่สำเร็จ", RiskLevel.SUSPICIOUS, false)
                return@launch
            }
            whisper.use { w ->
                for (pcm in utterances) {
                    val text = w.transcribe(pcm, "th")
                    if (text.isNotBlank()) onUpdate(monitor.onTranscript(text))
                }
            }
        }
    }

    // ------------------------------------------------------------------ decisions

    private fun onUpdate(u: LiveCallMonitor.Update) {
        val r = u.result ?: return
        val why = r.signals.firstOrNull { it.weight > 0 }?.title.orEmpty()
        when (u.action) {
            LiveCallMonitor.Action.NONE -> Unit
            LiveCallMonitor.Action.CAUTION -> {
                vibrate(longArrayOf(0, 200, 150, 200))
                notifyStatus("ระวัง สายนี้น่าสงสัย: $why", RiskLevel.SUSPICIOUS, withActions = true)
            }
            LiveCallMonitor.Action.WARN -> {
                vibrate(longArrayOf(0, 500, 200, 500, 200, 500))
                speak("คำเตือนจาก สแกมเซนส์ สายนี้อาจเป็นมิจฉาชีพ อย่าโอนเงิน และอย่าบอกรหัสใด ๆ")
                notifyStatus("อันตราย! อาจเป็นมิจฉาชีพ: $why", RiskLevel.DANGEROUS, withActions = true)
            }
            LiveCallMonitor.Action.HANG_UP -> {
                vibrate(longArrayOf(0, 800, 200, 800))
                speak("สแกมเซนส์ พบมิจฉาชีพ กำลังวางสายให้")
                notifyStatus("พบมิจฉาชีพ! จะวางสายให้ใน ${HANG_UP_DELAY_MS / 1000} วินาที (แตะยกเลิกได้)", RiskLevel.DANGEROUS, withActions = true, countdown = true)
                hangUpJob = scope.launch { delay(HANG_UP_DELAY_MS); hangUpNow() }
            }
        }
    }

    private fun hangUpNow() {
        hangUpJob?.cancel()
        val ok = runCatching { audio?.endCall() == true }.getOrDefault(false)
        if (!ok) notifyStatus("วางสายอัตโนมัติไม่สำเร็จ กรุณากดวางสายเอง", RiskLevel.DANGEROUS, withActions = false)
    }

    // ------------------------------------------------------------------ finish

    private fun finish() {
        if (finished) return
        finished = true
        scope.launch {
            runCatching { audio?.stopStream() }
            runCatching { readPipe?.close() }
            // Let the last utterances finish transcribing so the verdict is complete.
            withTimeoutOrNull(FINAL_STT_TIMEOUT_MS) { captureJob?.join(); sttJob?.join() }
            saveResult()
            ShizukuGate.unbind(this@LiveCallService, shizukuConn)
            unregisterCallState()
            tts?.shutdown()
            ServiceCompat.stopForeground(this@LiveCallService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun saveResult() {
        val result = monitor.latest ?: return
        val lines = monitor.transcript
        if (lines.isEmpty()) return
        val container = (application as ScamSenseApplication).container
        val settings = container.settingsRepository.ensureLoaded()
        val saved = result.copy(
            summary = "สายจาก ${caller.ifBlank { "เบอร์ที่ไม่รู้จัก" }}: ${result.summary}",
            inputPreview = TextNormalizer.preview(lines.joinToString(" "), 280),
            extractedPhones = listOfNotNull(caller.takeIf { it.isNotBlank() }),
            analyzedAt = System.currentTimeMillis(),
        )
        if (settings.saveHistory) container.historyRepository.add(saved)
        if (saved.level != RiskLevel.SAFE) {
            val nm = getSystemService(NotificationManager::class.java)
            runCatching {
                nm?.notify(SUMMARY_ID, buildNotification(
                    "สายเมื่อกี้: ${if (saved.level == RiskLevel.DANGEROUS) "อันตราย" else "น่าสงสัย"} (${saved.score}/100)",
                    "${saved.matchedScamType ?: saved.summary} — แตะเพื่อดูและส่งให้ลูกหลาน", saved.level, false, autoCancel = true,
                ))
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    @Suppress("DEPRECATION")
    private fun listenToCallState() {
        val tm = getSystemService(TelephonyManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = onCallState(state)
            }
            runCatching { tm.registerTelephonyCallback(ContextCompat.getMainExecutor(this), cb) }
            callbackRegistration = cb
        } else {
            val l = object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) = onCallState(state)
            }
            runCatching { tm.listen(l, PhoneStateListener.LISTEN_CALL_STATE) }
            callbackRegistration = l
        }
    }

    @Suppress("DEPRECATION")
    private fun unregisterCallState() {
        val tm = getSystemService(TelephonyManager::class.java) ?: return
        val r = callbackRegistration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && r is TelephonyCallback) {
            runCatching { tm.unregisterTelephonyCallback(r) }
        } else if (r is PhoneStateListener) {
            runCatching { tm.listen(r, PhoneStateListener.LISTEN_NONE) }
        }
        callbackRegistration = null
    }

    private fun initTts() {
        tts = TextToSpeech(applicationContext) { status ->
            val t = tts ?: return@TextToSpeech
            if (status == TextToSpeech.SUCCESS) {
                val res = t.setLanguage(Locale("th", "TH"))
                // Played on the call's voice path: the user hears it in the earpiece.
                t.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).build())
                ttsReady = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }

    private fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "scamsense-live")
    }

    private fun vibrate(pattern: LongArray) {
        val v = getSystemService(Vibrator::class.java) ?: return
        runCatching { v.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
    }

    private fun notifyStatus(text: String, level: RiskLevel, withActions: Boolean, countdown: Boolean = false) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.notify(NOTIFICATION_ID, buildNotification("ScamSense ฟังสายนี้อยู่", text, level, withActions, countdown = countdown)) }
    }

    private fun buildNotification(
        title: String, text: String, level: RiskLevel, withActions: Boolean,
        countdown: Boolean = false, autoCancel: Boolean = false,
    ): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setOnlyAlertOnce(level == RiskLevel.SAFE)
            .setOngoing(!autoCancel)
            .setAutoCancel(autoCancel)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(if (level == RiskLevel.SAFE) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MAX)
            .setColor(when (level) { RiskLevel.DANGEROUS -> 0xFFC81E1E.toInt(); RiskLevel.SUSPICIOUS -> 0xFFB45309.toInt(); else -> 0xFF1E4FD8.toInt() })
        if (withActions) {
            b.addAction(0, "วางสายเลย", service(ACTION_HANG_UP, 1))
            if (countdown) b.addAction(0, "ยกเลิก ยังคุยต่อ", service(ACTION_CANCEL_HANG_UP, 2))
        }
        if (!autoCancel) b.addAction(0, "หยุดฟังสายนี้", service(ACTION_STOP, 3))
        return b.build()
    }

    private fun service(action: String, code: Int) = PendingIntent.getService(
        this, code, Intent(this, LiveCallService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE,
    )

    override fun onDestroy() {
        unregisterCallState()
        tts?.shutdown()
        ShizukuGate.unbind(this, shizukuConn)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_WATCH = "com.scamsense.app.live.WATCH"
        const val ACTION_HANG_UP = "com.scamsense.app.live.HANG_UP"
        const val ACTION_CANCEL_HANG_UP = "com.scamsense.app.live.CANCEL_HANG_UP"
        const val ACTION_STOP = "com.scamsense.app.live.STOP"
        const val EXTRA_CALLER = "caller"
        private const val CHANNEL_ID = "live_call"
        private const val NOTIFICATION_ID = 4101
        private const val SUMMARY_ID = 4102
        private const val SAMPLE_RATE = 16_000
        private const val RING_TIMEOUT_MS = 90_000L
        private const val HANG_UP_DELAY_MS = 4_000L
        private const val FINAL_STT_TIMEOUT_MS = 15_000L

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "ฟังระหว่างคุย", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "เตือนทันทีเมื่อปลายสายพูดแบบมิจฉาชีพ"
                })
            }
        }

        /** Started by the call screening service for calls from numbers not in contacts. */
        fun watch(context: Context, callerDisplay: String): Boolean = runCatching {
            ContextCompat.startForegroundService(context,
                Intent(context, LiveCallService::class.java).setAction(ACTION_WATCH).putExtra(EXTRA_CALLER, callerDisplay))
            true
        }.getOrDefault(false)
    }
}
