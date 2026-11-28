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
enum class WhisperModel(
    val id: String, val label: String, val fileName: String, val minBytes: Long, val approxMb: Int,
    /**
     * Downloaded as a Hugging Face (transformers) checkpoint and converted + shrunk on the
     * phone, because no phone-sized whisper.cpp build of it is published.
     */
    val hfRepo: String? = null,
) {
    SMALL("small", "แม่นยำกว่า แต่ต้องใช้เครื่องแรง RAM 6 GB ขึ้นไป (~180 MB)", "ggml-small-q5_1.bin", 150_000_000, 181),
    BASE("base", "เร็ว เหมาะกับเครื่องราคาประหยัด (~57 MB) แนะนำ", "ggml-base-q5_1.bin", 40_000_000, 57),
    THAI(
        "thai", "ฝึกด้วยภาษาไทย (Thonburian Whisper, ม.มหิดล) ดาวน์โหลดแล้วแปลงในเครื่อง ใช้เน็ตครั้งเดียว ~0.5–1 GB",
        "ggml-thai-q5_1.bin", 20_000_000, 700, hfRepo = "biodatlab/distill-whisper-th-small",
    ),
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
        if (model.hfRepo != null) downloadAndConvert(context, model, model.hfRepo, onProgress)
        else withContext(Dispatchers.IO) {
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

    /**
     * Downloads a Hugging Face Whisper checkpoint (config.json, vocab.json, safetensors),
     * converts it to whisper.cpp format on the phone ([HfWhisperToGgml]) and shrinks it to
     * q5_1. Progress: download 0–75 %, convert 75–92 %, shrink 95 %.
     */
    private suspend fun downloadAndConvert(context: Context, model: WhisperModel, repo: String, onProgress: (Float) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            val work = File(dir(context), "hf-work").apply { deleteRecursively(); mkdirs() }
            val f16 = File(dir(context), "convert-f16.part")
            val quant = File(dir(context), "convert-q.part")
            val target = file(context, model)
            runCatching {
                val base = "https://huggingface.co/$repo/resolve/main/"
                val config = File(work, "config.json").also { fetch(base + "config.json", it) }
                val vocab = File(work, "vocab.json")
                if (!fetchOrMissing(base + "vocab.json", vocab)) fetch("https://huggingface.co/openai/whisper-small/resolve/main/vocab.json", vocab)

                // Single file, or shards listed in model.safetensors.index.json.
                val names = run {
                    val index = File(work, "index.json")
                    if (fetchOrMissing(base + "model.safetensors.index.json", index)) {
                        org.json.JSONObject(index.readText()).getJSONObject("weight_map").let { m ->
                            m.keys().asSequence().map { m.getString(it) }.toSortedSet().toList()
                        }
                    } else listOf("model.safetensors")
                }
                val sizes = names.map { contentLength(base + it) }
                val total = sizes.sum().coerceAtLeast(1)
                val free = android.os.StatFs(dir(context).path).availableBytes
                if (free < total * 16 / 10) throw IOException("not enough storage")
                var done = 0L
                val shards = names.map { name ->
                    File(work, name).also { f ->
                        fetch(base + name, f) { n -> done += n; onProgress((0.75f * done / total).coerceAtMost(0.75f)) }
                    }
                }
                coroutineContext.ensureActive()
                val job = coroutineContext[kotlinx.coroutines.Job]
                com.scamsense.core.asr.HfWhisperToGgml.convert(config, vocab, shards, f16,
                    onProgress = { p -> onProgress(0.75f + 0.17f * p.done / p.total.coerceAtLeast(1)) },
                    isCancelled = { job?.isActive == false })
                work.deleteRecursively() // free space before shrinking
                onProgress(0.95f)
                if (!WhisperNative.quantize(f16.path, quant.path) || !hasGgmlMagic(quant)) throw IOException("quantize failed")
                if (!quant.renameTo(target)) throw IOException("could not save model")
                onProgress(1f)
                target
            }.also { work.deleteRecursively(); f16.delete(); quant.delete() }
        }

    private fun open(url: String): HttpsURLConnection =
        (URL(url).openConnection() as HttpsURLConnection).apply { connectTimeout = 15_000; readTimeout = 30_000 }

    private fun contentLength(url: String): Long {
        val c = open(url).apply { requestMethod = "HEAD" }
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode} for $url")
            // getContentLengthLong() needs API 24; read the header directly.
            return (c.getHeaderField("Content-Length") ?: c.getHeaderField("X-Linked-Size"))?.toLongOrNull()?.coerceAtLeast(0) ?: 0
        } finally { c.disconnect() }
    }

    /** @return false when the file does not exist on the server (404). */
    private suspend fun fetchOrMissing(url: String, dest: File): Boolean {
        val c = open(url).apply { requestMethod = "HEAD" }
        val code = try { c.responseCode } finally { c.disconnect() }
        if (code == 404) return false
        fetch(url, dest)
        return true
    }

    private suspend fun fetch(url: String, dest: File, onBytes: (Long) -> Unit = {}) {
        val c = open(url)
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode} for $url")
            c.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        onBytes(n.toLong())
                    }
                }
            }
        } finally { c.disconnect() }
    }

    /**
     * Imports a whisper.cpp model chosen by the user (e.g. Thonburian Whisper converted on a
     * PC with tools/convert_thonburian.py). f16/f32 files are shrunk to q5_1 on the phone.
     */
    suspend fun importModel(context: Context, uri: android.net.Uri, onPhase: (String) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            val model = WhisperModel.THAI
            val target = file(context, model)
            val raw = File(dir(context), "import.part")
            val quant = File(dir(context), "import-q.part")
            runCatching {
                onPhase("กำลังคัดลอกไฟล์…")
                val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("cannot open file")
                stream.use { input ->
                    raw.outputStream().use { out ->
                        val buf = ByteArray(256 * 1024)
                        var total = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > MAX_IMPORT_BYTES) throw IOException("file too large")
                            out.write(buf, 0, n)
                        }
                    }
                }
                if (raw.length() < model.minBytes || !hasGgmlMagic(raw)) throw IOException("not a whisper.cpp model")
                val ftype = headerFtype(raw)
                if (ftype == 0 || ftype == 1) {
                    onPhase("กำลังย่อโมเดลให้เร็วขึ้น อาจใช้เวลาหลายนาที…")
                    if (!WhisperNative.quantize(raw.path, quant.path) || !hasGgmlMagic(quant)) throw IOException("quantize failed")
                    raw.delete()
                    if (!quant.renameTo(target)) throw IOException("could not save model")
                } else {
                    if (!raw.renameTo(target)) throw IOException("could not save model")
                }
                target
            }.also { raw.delete(); quant.delete() }
        }

    /** The 11th header int: 0 = f32, 1 = f16, otherwise already quantized. */
    private fun headerFtype(f: File): Int = runCatching {
        f.inputStream().use { s ->
            val b = ByteArray(48)
            if (s.read(b) != 48) return -1
            java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt(44)
        }
    }.getOrDefault(-1)

    private const val MAX_IMPORT_BYTES = 2_000_000_000L

    /** whisper.cpp model files start with the uint32 magic 0x67676d6c ("lmgg" on disk). */
    private fun hasGgmlMagic(f: File): Boolean = runCatching {
        f.inputStream().use { s ->
            val b = ByteArray(4)
            s.read(b) == 4 && b[0] == 0x6c.toByte() && b[1] == 0x6d.toByte() && b[2] == 0x67.toByte() && b[3] == 0x67.toByte()
        }
    }.getOrDefault(false)
}
