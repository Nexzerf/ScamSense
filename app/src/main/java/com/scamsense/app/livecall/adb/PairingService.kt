package com.scamsense.app.livecall.adb

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.app.ServiceCompat
import com.scamsense.app.MainActivity
import com.scamsense.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One-time pairing for built-in mode. Android shows the 6-digit pairing code in a Settings
 * dialog that closes if the user switches apps, so ScamSense asks for the code in a
 * notification reply field (the user types it without leaving Settings), then pairs in this
 * short-lived foreground service.
 */
class PairingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val code = intent?.let { RemoteInput.getResultsFromIntent(it)?.getCharSequence(KEY_CODE)?.toString() }
        if (code == null) { stopSelf(); return START_NOT_STICKY }
        ensureChannel(this)
        val working = base(this, "กำลังจับคู่กับ ScamSense…", "อย่าเพิ่งปิดหน้าตั้งค่า อาจใช้เวลาประมาณ 10–30 วินาที").setOngoing(true)
            .setProgress(0, 0, true).build()
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, working,
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        } catch (_: RuntimeException) {
            stopSelf(); return START_NOT_STICKY
        }
        _state.value = State.Working
        scope.launch {
            val result = BuiltInShell.pairAndSetup(applicationContext, code)
            _state.value = if (result is BuiltInShell.Result.Ok) State.Done else State.Failed((result as BuiltInShell.Result.Failed).userMessage)
            ServiceCompat.stopForeground(this@PairingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            val nm = getSystemService(NotificationManager::class.java)
            if (result is BuiltInShell.Result.Ok) {
                nm?.notify(NOTIFICATION_ID, base(this@PairingService, "จับคู่สำเร็จ ✓",
                    "ScamSense ฟังระหว่างคุยได้แล้ว โดยไม่ต้องใช้แอป Shizuku").setAutoCancel(true).build())
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val msg = (result as BuiltInShell.Result.Failed).userMessage
                // Offer the reply field again so the user can retry with a fresh code.
                nm?.notify(NOTIFICATION_ID, codeRequest(this@PairingService, "จับคู่ไม่สำเร็จ", "$msg\nใส่รหัสใหม่ได้ที่นี่"))
            }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    sealed interface State {
        data object Idle : State
        data object Waiting : State
        data object Working : State
        data object Done : State
        data class Failed(val message: String) : State
    }

    companion object {
        private const val CHANNEL_ID = "adb_pairing"
        private const val NOTIFICATION_ID = 4201
        private const val KEY_CODE = "pairing_code"

        private val _state = MutableStateFlow<State>(State.Idle)
        /** For the setup screen. */
        val state: StateFlow<State> = _state.asStateFlow()

        /** Shows the "type the pairing code here" notification. */
        fun requestCode(context: Context) {
            // Wireless debugging (and so pairing) exists from Android 11.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) { _state.value = State.Failed("ต้องใช้ Android 11 ขึ้นไป"); return }
            ensureChannel(context)
            _state.value = State.Waiting
            context.getSystemService(NotificationManager::class.java)?.notify(
                NOTIFICATION_ID,
                codeRequest(context, "ใส่รหัสจับคู่ 6 หลัก ที่นี่",
                    "ในหน้าการแก้ไขข้อบกพร่องแบบไร้สาย แตะ \"จับคู่อุปกรณ์ด้วยรหัสจับคู่\" แล้วพิมพ์รหัสที่เห็นลงในช่องนี้"),
            )
        }

        @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
        private fun codeRequest(context: Context, title: String, text: String) = run {
            val remoteInput = RemoteInput.Builder(KEY_CODE).setLabel("รหัส 6 หลัก").build()
            // Mutable: the system writes the typed code into this intent.
            val pi = PendingIntent.getForegroundService(
                context, 1, Intent(context, PairingService::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
            )
            val action = NotificationCompat.Action.Builder(R.drawable.ic_stat_shield, "ใส่รหัส", pi)
                .addRemoteInput(remoteInput).setAllowGeneratedReplies(false).build()
            base(context, title, text).addAction(action).setOnlyAlertOnce(false).build()
        }

        private fun base(context: Context, title: String, text: String): NotificationCompat.Builder {
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_shield)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(open)
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return // channels exist from Android 8
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "จับคู่การฟังระหว่างคุย", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "ใช้ครั้งเดียวตอนตั้งค่า เพื่อใส่รหัสจับคู่"
                })
            }
        }
    }
}
