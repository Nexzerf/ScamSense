/*
 * Adapted from CallVault (https://github.com/madkongo/CallVault), a fork of ShizuCallRecorder
 * (https://github.com/kitsumed/ShizuCallRecorder), Copyright (C) kitsumed (Med).
 * Licensed under the GNU GPL v3 with additional Section 7 terms — see LICENSE and NOTICE.md.
 * Modified for ScamSense (logging, packaging, live streaming instead of file recording).
 */
package com.scamsense.app.livecall.shell

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import androidx.annotation.Keep
import com.scamsense.app.livecall.ICallAudioService
import java.io.IOException
import kotlin.system.exitProcess

/**
 * Runs in a separate process started by Shizuku with the shell uid, which is allowed to
 * capture call audio. It only streams raw PCM to the app through a pipe: nothing is
 * written to storage here, and speech recognition happens in the app process.
 */
@Keep
class CallAudioUserService : ICallAudioService.Stub {

    @Keep constructor() { ShellAttribution.detachFromApp() }

    @Keep constructor(@Suppress("UNUSED_PARAMETER") context: Context) { ShellAttribution.detachFromApp() }

    @Volatile private var record: AudioRecord? = null
    @Volatile private var pump: Thread? = null
    @Volatile private var activeSource = -1

    @Synchronized
    override fun startStream(preferredSource: Int, sampleRate: Int, sink: ParcelFileDescriptor?): Int {
        if (sink == null || sampleRate !in 8_000..48_000) { runCatching { sink?.close() }; return -1 }
        stopStream()
        val channel = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channel, encoding).coerceAtLeast(4_096)
        val bufferBytes = maxOf(minBuf, sampleRate) // ≈ 0.5 s of audio
        for (source in listOf(preferredSource, MediaRecorder.AudioSource.VOICE_CALL).distinct()) {
            val rec = HostAudioRecord.open(source, sampleRate, channel, encoding, bufferBytes) ?: continue
            if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); continue }
            val started = runCatching { HostAudioRecord.start(rec) }.isSuccess &&
                rec.recordingState == AudioRecord.RECORDSTATE_RECORDING
            if (!started) { rec.release(); continue }
            record = rec
            activeSource = source
            pump = Thread({ pumpTo(rec, sink) }, "ScamSense-CallAudio").apply { isDaemon = true; start() }
            Log.i(TAG, "streaming call audio source=$source rate=$sampleRate")
            return source
        }
        runCatching { sink.close() }
        return -1
    }

    private fun pumpTo(rec: AudioRecord, pfd: ParcelFileDescriptor) {
        val buf = ByteArray(3_200) // 100 ms at 16 kHz mono 16-bit
        try {
            ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { os ->
                while (record === rec && !Thread.currentThread().isInterrupted) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n < 0) break
                    if (n > 0) os.write(buf, 0, n)
                }
            }
        } catch (_: IOException) {
            // The app closed its end (call ended / app stopped listening).
        } finally {
            if (record === rec) stopStream()
        }
    }

    @Synchronized
    override fun stopStream() {
        val rec = record ?: return
        record = null
        activeSource = -1
        runCatching { rec.stop() }
        runCatching { rec.release() }
        pump?.interrupt()
        pump = null
    }

    override fun endCall(): Boolean = runCatching {
        ProcessBuilder("input", "keyevent", "KEYCODE_ENDCALL").redirectErrorStream(true).start().waitFor() == 0
    }.getOrDefault(false)

    override fun describe(): String =
        "uid=${Process.myUid()} context=${runCatching { java.io.File("/proc/self/attr/current").readText().trim() }.getOrDefault("?")} " +
            "source=$activeSource"

    override fun destroy() {
        stopStream()
        exitProcess(0)
    }

    private companion object {
        const val TAG = "SS:CallAudio"
    }
}
