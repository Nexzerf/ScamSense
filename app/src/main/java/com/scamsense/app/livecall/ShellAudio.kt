package com.scamsense.app.livecall

import android.content.Context
import com.scamsense.app.livecall.shell.ShellMain
import rikka.shizuku.Shizuku
import java.io.Closeable
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Starts the shell-uid audio helper ([ShellMain]) as a plain process through Shizuku and
 * hands back its PCM stream. Also ends calls from the shell.
 */
object ShellAudio {

    class Stream internal constructor(private val process: Process, val source: Int) : Closeable {
        /** Raw 16-bit little-endian mono PCM. */
        val input: InputStream get() = process.inputStream

        override fun close() {
            runCatching { process.outputStream.close() } // stdin EOF: the helper exits by itself
            runCatching { process.inputStream.close() }
            runCatching { process.destroy() }
        }
    }

    /** Outcome of [open]: a stream, or a short reason code for the user / developer. */
    sealed interface Opened {
        data class Ok(val stream: Stream) : Opened
        data class Failed(val reason: String) : Opened
    }

    private const val HEADER_TIMEOUT_S = 30L

    /** Blocking (up to ~30 s on slow phones): call from a background thread. */
    fun open(context: Context, source: Int, sampleRate: Int): Opened {
        if (ShizukuGate.status(context) != ShizukuGate.Status.READY) return Opened.Failed("shizuku-not-ready")
        val apk = context.applicationInfo.sourceDir
        if (apk.contains('\'')) return Opened.Failed("apk-path")
        val script = "CLASSPATH='$apk' exec /system/bin/app_process /system/bin " +
            "${ShellMain::class.java.name} $source $sampleRate"
        val process = start(arrayOf("sh", "-c", script)) ?: return Opened.Failed("shizuku-process")

        // Wait for the helper's status line; it reports which audio source opened.
        val exec = Executors.newSingleThreadExecutor()
        val header = try {
            exec.submit<String?> { readLine(process.inputStream) }.get(HEADER_TIMEOUT_S, TimeUnit.SECONDS)
        } catch (_: Exception) {
            null
        } finally {
            exec.shutdownNow()
        }
        if (header != null && header.startsWith("OK ")) {
            return Opened.Ok(Stream(process, header.removePrefix("OK ").trim().toIntOrNull() ?: source))
        }
        val reason = header?.removePrefix("ERR ")?.trim()?.ifBlank { null }
            ?: stderrTail(process) ?: "timeout"
        runCatching { process.destroy() }
        return Opened.Failed(reason)
    }

    /** Ends the ongoing call (KEYCODE_ENDCALL from the shell). Blocking. */
    fun endCall(): Boolean = runCatching {
        val p = start(arrayOf("input", "keyevent", "KEYCODE_ENDCALL")) ?: return false
        p.waitFor() == 0
    }.getOrDefault(false)

    /**
     * Shizuku 13 keeps newProcess private in favour of user services, but user services
     * cannot start on some ColorOS builds; the method is stable across Shizuku 11–13.
     */
    private fun start(cmd: Array<String>): Process? = runCatching {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java,
        )
        m.isAccessible = true
        m.invoke(null, cmd, null, null) as Process
    }.getOrNull()

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (sb.length < 200) {
            val c = input.read()
            if (c < 0) return sb.toString().ifEmpty { null }
            if (c == '\n'.code) return sb.toString()
            sb.append(c.toChar())
        }
        return sb.toString()
    }

    private fun stderrTail(process: Process): String? = runCatching {
        val err = process.errorStream
        val bytes = ByteArray(minOf(err.available(), 4_096))
        val n = if (bytes.isEmpty()) 0 else err.read(bytes)
        if (n <= 0) null else String(bytes, 0, n).lines().lastOrNull { it.isNotBlank() }?.trim()?.take(160)
    }.getOrNull()
}
