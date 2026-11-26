package com.scamsense.app.calls

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.scamsense.app.MainActivity
import com.scamsense.app.R
import com.scamsense.core.calls.CallGuard

object CallNotifications {
    private const val CHANNEL_ID = "call_guard"
    const val ACTION_CHECK_CALL = "com.scamsense.app.action.CHECK_CALL"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "ป้องกันสายโทร", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "แจ้งเตือนเมื่อมีสายจากเบอร์อันตรายหรือเบอร์ที่ไม่รู้จัก"
            },
        )
    }

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Shows what ScamSense did with a call. Tapping opens the voice check ("เล่าให้ฟัง"). */
    fun show(context: Context, decision: CallGuard.Decision) {
        if (!canPost(context)) return
        ensureChannel(context)
        val open = Intent(context, MainActivity::class.java)
            .setAction(ACTION_CHECK_CALL)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(
            context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = when (decision.action) {
            CallGuard.Action.ALLOW_WITH_WARNING -> "${decision.detail}\nแตะเพื่อเล่าให้ ScamSense ช่วยตรวจ"
            else -> decision.detail
        }
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle("${decision.title}: ${decision.display}")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(decision.display.hashCode(), n)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }
}
