/*
 * Part of ScamSense. The capture approach (shell-uid AudioRecord with vendor workarounds)
 * is adapted from CallVault (https://github.com/madkongo/CallVault), a fork of
 * ShizuCallRecorder (https://github.com/kitsumed/ShizuCallRecorder), Copyright (C) kitsumed (Med).
 * Licensed under the GNU GPL v3 with additional Section 7 terms — see LICENSE and NOTICE.md.
 */
package com.scamsense.app.livecall.shell

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Looper
import androidx.annotation.Keep
import java.io.BufferedOutputStream
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.IOException
import kotlin.system.exitProcess

/**
 * Entry point of the call-audio helper: a bare `app_process` started through Shizuku as
 * the shell user (`CLASSPATH=<apk> app_process /system/bin <this class> <source> <rate>`).
 *
 * It deliberately does not use Shizuku's "user service" starter: on some ColorOS builds that
 * starter crashes inside LoadedApk.makeApplication before any app code runs.
 *
 * Protocol on stdout: one text line `OK <source>` or `ERR <reason>`, then raw 16-bit
 * little-endian mono PCM until the app closes the pipe (or stdin reaches EOF).
 * Nothing is written to storage.
 */
@Keep
object ShellMain {

    @Keep
    @JvmStatic
    fun main(args: Array<String>) {
        val out = BufferedOutputStream(FileOutputStream(FileDescriptor.out), 8_192)
        fun reply(line: String) { out.write((line + "\n").toByteArray()); out.flush() }

        val source = args.getOrNull(0)?.toIntOrNull() ?: MediaRecorder.AudioSource.VOICE_DOWNLINK
        val rate = args.getOrNull(1)?.toIntOrNull()?.takeIf { it in 8_000..48_000 } ?: 16_000

        // The framework expects a main looper and an ActivityThread with an Application
        // in the process; a bare app_process has neither.
        runCatching { if (Looper.getMainLooper() == null) Looper.prepareMainLooper() }
        ShellApplication.install()

        val channel = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(rate, channel, encoding).coerceAtLeast(4_096)
        val bufferBytes = maxOf(minBuf, rate) // ≈ 0.5 s
        val candidates = if (source == MediaRecorder.AudioSource.MIC) listOf(source)
        else listOf(source, MediaRecorder.AudioSource.VOICE_CALL).distinct()

        var record: AudioRecord? = null
        var used = -1
        for (s in candidates) {
            val rec = HostAudioRecord.open(s, rate, channel, encoding, bufferBytes) ?: continue
            if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); continue }
            val started = runCatching { HostAudioRecord.start(rec) }.isSuccess &&
                rec.recordingState == AudioRecord.RECORDSTATE_RECORDING
            if (!started) { rec.release(); continue }
            record = rec
            used = s
            break
        }
        val rec = record ?: run {
            reply("ERR no-audio-source")
            exitProcess(2)
        }
        reply("OK $used")

        // The app ends the session by closing our stdin (or killing the process).
        Thread({
            runCatching { while (System.`in`.read() >= 0) Unit }
            runCatching { rec.stop() }
            exitProcess(0)
        }, "stdin-watch").apply { isDaemon = true; start() }

        val buf = ByteArray(3_200) // 100 ms at 16 kHz
        try {
            while (true) {
                val n = rec.read(buf, 0, buf.size)
                if (n < 0) break
                if (n > 0) { out.write(buf, 0, n); out.flush() }
            }
        } catch (_: IOException) {
            // The app closed the pipe: the call ended or listening stopped.
        } finally {
            runCatching { rec.stop() }
            runCatching { rec.release() }
        }
        exitProcess(0)
    }
}
