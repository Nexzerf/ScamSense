/*
 * Adapted from CallVault (https://github.com/madkongo/CallVault), a fork of ShizuCallRecorder
 * (https://github.com/kitsumed/ShizuCallRecorder), Copyright (C) kitsumed (Med).
 * Licensed under the GNU GPL v3 with additional Section 7 terms — see LICENSE and NOTICE.md.
 * Modified for ScamSense (logging, packaging, live streaming instead of file recording).
 */
package com.scamsense.app.livecall.shell

import android.annotation.SuppressLint
import android.app.Application
import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioRecord
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

private const val TAG = "SS:Shell"

/**
 * Some vendor ROMs dereference the current Application inside AudioRecord. Give the bare
 * shell process a minimal Application that presents itself as com.android.shell.
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
internal object ShellApplication {
    private const val SHELL_PACKAGE = "com.android.shell"
    private const val SHELL_UID = 2000
    @Volatile private var installed = false

    @Synchronized
    fun install(): Boolean {
        if (installed) return true
        return runCatching {
            val atCls = Class.forName("android.app.ActivityThread")
            val thread = atCls.getMethod("currentActivityThread").invoke(null) ?: newActivityThread(atCls)
            val system = atCls.getDeclaredMethod("getSystemContext").apply { isAccessible = true }.invoke(thread) as Context
            val app = Host().apply { attach(ShellContext(system)) }
            atCls.getDeclaredField("mInitialApplication").apply { isAccessible = true }.set(thread, app)
            installed = true
            true
        }.onFailure { Log.w(TAG, "shell Application install failed: ${it.javaClass.simpleName}") }.getOrDefault(false)
    }

    private fun newActivityThread(atCls: Class<*>): Any {
        val thread = atCls.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        atCls.getDeclaredField("sCurrentActivityThread").apply { isAccessible = true }.set(null, thread)
        atCls.getDeclaredField("mSystemThread").apply { isAccessible = true }.setBoolean(thread, true)
        return thread
    }

    private class Host : Application() {
        fun attach(base: Context) = attachBaseContext(base)
    }

    private class ShellContext(base: Context) : ContextWrapper(base) {
        override fun getPackageName(): String = SHELL_PACKAGE
        override fun getOpPackageName(): String = SHELL_PACKAGE
        override fun getApplicationContext(): Context = this
        @RequiresApi(Build.VERSION_CODES.S)
        override fun getAttributionSource(): AttributionSource =
            AttributionSource.Builder(SHELL_UID).setPackageName(SHELL_PACKAGE).build()
    }
}

/** Opens / starts an AudioRecord in the shell process, repairing vendor quirks once. */
internal object HostAudioRecord {

    @SuppressLint("MissingPermission")
    fun open(source: Int, sampleRate: Int, channelMask: Int, encoding: Int, bufferBytes: Int): AudioRecord? {
        val first = runCatching { AudioRecord(source, sampleRate, channelMask, encoding, bufferBytes) }
        first.getOrNull()?.let { return it }
        val error = first.exceptionOrNull()!!
        if (!isVendorNpe(error, "<init>") || !ShellApplication.install()) {
            Log.w(TAG, "AudioRecord(source=$source) failed: ${error.javaClass.simpleName}")
            return null
        }
        return runCatching { AudioRecord(source, sampleRate, channelMask, encoding, bufferBytes) }.getOrNull()
    }

    fun start(record: AudioRecord) {
        val error = runCatching { record.startRecording() }.exceptionOrNull() ?: return
        if (!isVendorNpe(error, "startRecording") || !ShellApplication.install()) throw error
        record.startRecording()
    }

    /** An NPE thrown from vendor code called by AudioRecord.[method] (not AudioRecord itself). */
    private fun isVendorNpe(error: Throwable, method: String): Boolean {
        var root = error
        while (root.cause != null && root.cause !== root) root = root.cause!!
        if (root !is NullPointerException) return false
        val frames = root.stackTrace
        val at = frames.indexOfFirst { it.className == "android.media.AudioRecord" && it.methodName == method }
        return at > 0 && frames[0].className != "android.media.AudioRecord"
    }
}
