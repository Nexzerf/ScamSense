package com.scamsense.app.livecall

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.annotation.RequiresApi
import com.scamsense.core.engine.AnalysisOutcome
import com.scamsense.core.engine.CheckType
import com.scamsense.core.engine.RiskResult
import com.scamsense.core.engine.ScamAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * "Speak into the phone" check of the whole listening chain without a phone call:
 * Shizuku helper → audio pipe → whisper.cpp → scam engine. It uses the microphone through
 * the same shell helper, so it proves Shizuku, the model and the speed on this phone.
 */
@RequiresApi(Build.VERSION_CODES.R)
object LiveSelfTest {

    sealed interface Outcome {
        data class Heard(val text: String, val seconds: Float, val audioSeconds: Float, val result: RiskResult?) : Outcome
        data class Failed(val reason: String) : Outcome
    }

    private const val RATE = 16_000
    private const val BIND_TIMEOUT_MS = 30_000L

    suspend fun run(context: Context, model: WhisperModel, recordSeconds: Int = 6, onPhase: (String) -> Unit = {}): Outcome {
        if (!WhisperNative.isAvailable) return Outcome.Failed("เครื่องนี้ไม่รองรับตัวแปลงเสียง")
        if (!WhisperModels.isInstalled(context, model)) return Outcome.Failed("ยังไม่ได้ดาวน์โหลดโมเดล")
        if (ShizukuGate.status(context) != ShizukuGate.Status.READY) return Outcome.Failed("Shizuku ยังไม่พร้อม")

        onPhase("กำลังเชื่อมต่อตัวดึงเสียง… (ยังไม่ต้องพูด)")
        var conn: android.content.ServiceConnection? = null
        var error: String? = null
        // Starting the helper process loads the whole APK in a fresh process: on budget
        // phones the first start can take well over 10 s.
        val svc = withTimeoutOrNull(BIND_TIMEOUT_MS) {
            withContext(Dispatchers.Main) { // Shizuku binding belongs on the main thread
                suspendCancellableCoroutine<ICallAudioService?> { cont ->
                    conn = ShizukuGate.bind(context, onError = { error = it }) { s -> if (cont.isActive) cont.resume(s) }
                }
            }
        }
        try {
            if (svc == null) return Outcome.Failed(
                "เชื่อมต่อตัวดึงเสียงผ่าน Shizuku ไม่สำเร็จ (รหัส: ${error ?: "timeout-${BIND_TIMEOUT_MS / 1000}s"}) " +
                    "ลองกดทดสอบอีกครั้ง ถ้ายังไม่ได้ ให้ส่งรหัสนี้ให้ผู้พัฒนา",
            )
            onPhase("พูดเลย! กำลังฟัง $recordSeconds วินาที")
            val pcm = withContext(Dispatchers.IO) { record(svc, recordSeconds) }
                ?: return Outcome.Failed("เปิดไมโครโฟนผ่าน Shizuku ไม่ได้")
            var peak = 0
            for (v in pcm) { val a = kotlin.math.abs(v.toInt()); if (a > peak) peak = a }
            if (peak < 200) return Outcome.Failed("ไม่ได้ยินเสียงเลย ลองพูดดังขึ้นใกล้ไมโครโฟน")

            onPhase("กำลังแปลงเสียงเป็นข้อความ…")
            val floats = FloatArray(pcm.size) { pcm[it] / 32768f }
            return withContext(Dispatchers.Default) {
                val started = SystemClock.elapsedRealtime()
                val text = WhisperNative.load(WhisperModels.file(context, model).path)?.use { it.transcribe(floats, "th") }
                    ?: return@withContext Outcome.Failed("โหลดโมเดลแปลงเสียงไม่สำเร็จ ลองลบแล้วดาวน์โหลดใหม่")
                val secs = (SystemClock.elapsedRealtime() - started) / 1000f
                val result = (ScamAnalyzer().analyze(CheckType.MESSAGE, text) as? AnalysisOutcome.Success)?.result
                Outcome.Heard(text, secs, pcm.size / RATE.toFloat(), result)
            }
        } finally {
            runCatching { svc?.stopStream() }
            withContext(Dispatchers.Main) { ShizukuGate.unbind(context, conn) }
        }
    }

    private fun record(svc: ICallAudioService, seconds: Int): ShortArray? {
        val pipe = ParcelFileDescriptor.createPipe()
        val used = runCatching { svc.startStream(MediaRecorder.AudioSource.MIC, RATE, pipe[1]) }.getOrDefault(-1)
        runCatching { pipe[1].close() }
        if (used < 0) { runCatching { pipe[0].close() }; return null }
        val want = RATE * 2 * seconds
        val bytes = ByteArray(want)
        var got = 0
        ParcelFileDescriptor.AutoCloseInputStream(pipe[0]).use { input ->
            while (got < want) {
                val n = input.read(bytes, got, want - got)
                if (n < 0) break
                got += n
            }
        }
        runCatching { svc.stopStream() }
        val out = ShortArray(got / 2)
        for (i in out.indices) out[i] = ((bytes[2 * i + 1].toInt() shl 8) or (bytes[2 * i].toInt() and 0xff)).toShort()
        return out
    }
}
