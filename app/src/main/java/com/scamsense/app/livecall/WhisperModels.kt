package com.scamsense.app.livecall

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.coroutineContext

/** Speech models downloaded once (Wi-Fi recommended) into app-private storage. */
enum class WhisperModel(val id: String, val label: String, val fileName: String, val minBytes: Long, val approxMb: Int) {
    SMALL("small", "แม่นยำกว่า แต่ต้องใช้เครื่องแรง RAM 6 GB ขึ้นไป (~180 MB)", "ggml-small-q5_1.bin", 150_000_000, 181),
    BASE("base", "เร็ว เหมาะกับเครื่องราคาประหยัด (~57 MB) แนะนำ", "ggml-base-q5_1.bin", 40_000_000, 57),
    ;

    val url: String get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$fileName"

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id } ?: BASE
    }
}

object WhisperModels {

    private fun dir(context: Context) = File(context.noBackupFilesDir, "models").apply { mkdirs() }

    fun file(context: Context, model: WhisperModel) = File(dir(context), model.fileName)

    fun isInstalled(context: Context, model: WhisperModel): Boolean {
        val f = file(context, model)
        return f.length() >= model.minBytes && hasGgmlMagic(f)
    }

    fun delete(context: Context, model: WhisperModel) {
        file(context, model).delete()
    }

    /**
     * Downloads over HTTPS (system CAs only, per network_security_config) to a .part file,
     * checks size and the ggml magic header, then atomically renames.
     */
    suspend fun download(context: Context, model: WhisperModel, onProgress: (Float) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            val target = file(context, model)
            val part = File(target.path + ".part")
            runCatching {
                val conn = URL(model.url).openConnection() as HttpsURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                try {
                    if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
                    val total = conn.contentLength.toLong().takeIf { it > 0 } ?: (model.approxMb * 1_000_000L)
                    if (total > 1_000_000_000L) throw IOException("unexpected size")
                    conn.inputStream.use { input ->
                        part.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            var lastReport = 0L
                            while (true) {
                                coroutineContext.ensureActive()
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                if (done - lastReport > 1_000_000) { onProgress((done.toFloat() / total).coerceIn(0f, 1f)); lastReport = done }
                            }
                        }
                    }
                } finally {
                    conn.disconnect()
                }
                if (part.length() < model.minBytes || !hasGgmlMagic(part)) throw IOException("downloaded file is not a valid model")
                if (!part.renameTo(target)) throw IOException("could not save model")
                onProgress(1f)
                target
            }.onFailure { part.delete() }
        }

    /** whisper.cpp model files start with the uint32 magic 0x67676d6c ("lmgg" on disk). */
    private fun hasGgmlMagic(f: File): Boolean = runCatching {
        f.inputStream().use { s ->
            val b = ByteArray(4)
            s.read(b) == 4 && b[0] == 0x6c.toByte() && b[1] == 0x6d.toByte() && b[2] == 0x67.toByte() && b[3] == 0x67.toByte()
        }
    }.getOrDefault(false)
}
