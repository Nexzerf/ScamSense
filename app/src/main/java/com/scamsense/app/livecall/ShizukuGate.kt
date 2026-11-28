package com.scamsense.app.livecall

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.scamsense.app.BuildConfig
import com.scamsense.app.livecall.shell.CallAudioUserService
import androidx.core.net.toUri
import rikka.shizuku.Shizuku

/** Everything ScamSense needs from Shizuku: status, permission, and the shell helper. */
object ShizukuGate {

    enum class Status { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }

    const val PERMISSION_REQUEST = 4207
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    fun status(context: Context): Status {
        val installed = runCatching { context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0); true }.getOrDefault(false)
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        return when {
            !running && !installed -> Status.NOT_INSTALLED
            !running -> Status.NOT_RUNNING
            runCatching { Shizuku.isPreV11() }.getOrDefault(true) -> Status.NOT_RUNNING
            runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false) -> Status.READY
            else -> Status.NO_PERMISSION
        }
    }

    fun requestPermission(onResult: (Boolean) -> Unit) {
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode != PERMISSION_REQUEST) return
                Shizuku.removeRequestPermissionResultListener(this)
                onResult(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        runCatching {
            Shizuku.addRequestPermissionResultListener(listener)
            Shizuku.requestPermission(PERMISSION_REQUEST)
        }.onFailure { onResult(false) }
    }

    /** Opens Shizuku's Play Store page (falls back to the web page, then the official site). */
    fun openInstallPage(context: Context): Boolean {
        val targets = listOf(
            "market://details?id=$SHIZUKU_PACKAGE",
            "https://play.google.com/store/apps/details?id=$SHIZUKU_PACKAGE",
            "https://shizuku.rikka.app/download/",
        )
        return targets.any { uri ->
            runCatching {
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri.toUri())
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }.getOrDefault(false)
        }
    }

    /** Opens Developer options, where Wireless debugging lives. */
    fun openDeveloperOptions(context: Context): Boolean = runCatching {
        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)

    fun launchShizukuApp(context: Context): Boolean = runCatching {
        val i = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE) ?: return false
        context.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)

    private fun args(context: Context) = Shizuku.UserServiceArgs(ComponentName(context.packageName, CallAudioUserService::class.java.name))
        .daemon(false)
        .processNameSuffix("callaudio")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    /** Binds the shell helper; [onReady] receives null if it could not start. */
    fun bind(context: Context, onError: (String) -> Unit = {}, onReady: (ICallAudioService?) -> Unit): ServiceConnection? {
        if (status(context) != Status.READY) { onError("not-ready"); onReady(null); return null }
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val alive = binder != null && binder.pingBinder()
                if (!alive) onError("binder-dead")
                onReady(if (alive) ICallAudioService.Stub.asInterface(binder) else null)
            }
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        return runCatching { Shizuku.bindUserService(args(context), conn); conn }
            .onFailure { onError("bind-${it.javaClass.simpleName}: ${it.message.orEmpty().take(120)}"); onReady(null) }
            .getOrNull()
    }

    fun unbind(context: Context, conn: ServiceConnection?) {
        if (conn == null) return
        runCatching { Shizuku.unbindUserService(args(context), conn, true) }
    }
}
