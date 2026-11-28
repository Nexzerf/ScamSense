package com.scamsense.core.asr

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Converts a Hugging Face Whisper checkpoint (config.json + vocab.json + *.safetensors) to the
 * whisper.cpp ggml format, on the phone, without Python or PyTorch.
 *
 * It reproduces whisper.cpp's models/convert-h5-to-ggml.py (MIT) byte for byte: same header,
 * same mel filters (librosa "slaney" mel, which openai/whisper ships as mel_filters.npz), same
 * vocabulary encoding, same tensor renaming and the same f16/f32 choice per tensor.
 * Tensors are streamed in chunks, so memory use stays small even for large embeddings.
 */
object HfWhisperToGgml {

    class Progress(val done: Long, val total: Long)

    private const val GGML_MAGIC = 0x67676d6c

    private val CONV_MAP = mapOf(
        "self_attn.k_proj" to "attn.key",
        "self_attn.q_proj" to "attn.query",
        "self_attn.v_proj" to "attn.value",
        "self_attn.out_proj" to "attn.out",
        "self_attn_layer_norm" to "attn_ln",
        "encoder_attn.q_proj" to "cross_attn.query",
        "encoder_attn.v_proj" to "cross_attn.value",
        "encoder_attn.out_proj" to "cross_attn.out",
        "encoder_attn_layer_norm" to "cross_attn_ln",
        "fc1" to "mlp.0",
        "fc2" to "mlp.2",
        "final_layer_norm" to "mlp_ln",
        "encoder.layer_norm.bias" to "encoder.ln_post.bias",
        "encoder.layer_norm.weight" to "encoder.ln_post.weight",
        "encoder.embed_positions.weight" to "encoder.positional_embedding",
        "decoder.layer_norm.bias" to "decoder.ln.bias",
        "decoder.layer_norm.weight" to "decoder.ln.weight",
        "decoder.embed_positions.weight" to "decoder.positional_embedding",
        "decoder.embed_tokens.weight" to "decoder.token_embedding.weight",
        "proj_out.weight" to "decoder.proj.weight",
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @param shards the model's safetensors file(s), in any order
     * @return number of tensors written
     */
    fun convert(
        config: File, vocab: File, shards: List<File>, out: File,
        onProgress: (Progress) -> Unit = {}, isCancelled: () -> Boolean = { false },
    ): Int {
        val cfg = json.parseToJsonElement(config.readText()).jsonObject
        fun hp(key: String) = cfg[key]?.jsonPrimitive?.intOrNull ?: throw IOException("config.json: missing $key")
        val nMels = hp("num_mel_bins")
        val maxLength = cfg["max_length"]?.jsonPrimitive?.intOrNull ?: cfg["max_target_positions"]?.jsonPrimitive?.intOrNull ?: 448

        val tensors = shards.flatMap { readIndex(it) }
        if (tensors.isEmpty()) throw IOException("no tensors in safetensors")
        val total = tensors.sumOf { it.elements }
        var done = 0L
        var written = 0

        BufferedOutputStream(out.outputStream(), 1 shl 20).use { os ->
            val w = LeWriter(os)
            w.int(GGML_MAGIC)
            w.int(hp("vocab_size")); w.int(hp("max_source_positions")); w.int(hp("d_model"))
            w.int(hp("encoder_attention_heads")); w.int(hp("encoder_layers")); w.int(maxLength)
            w.int(hp("d_model")); w.int(hp("decoder_attention_heads")); w.int(hp("decoder_layers"))
            w.int(nMels); w.int(1) // use_f16

            val filters = melFilters(nMels)
            w.int(nMels); w.int(N_FFT_BINS)
            for (v in filters) w.float(v)

            writeVocab(w, vocab)

            for (t in tensors) {
                if (isCancelled()) throw IOException("cancelled")
                if (t.name == "proj_out.weight") { done += t.elements; continue } // tied to token embedding
                val name = ggmlName(t.name)
                var shape = t.shape.filter { it != 1L } // numpy squeeze()
                if (name == "encoder.conv1.bias" || name == "encoder.conv2.bias") shape = listOf(shape[0], 1L)
                val nDims = shape.size
                val f32 = nDims < 2 || name in setOf(
                    "encoder.conv1.bias", "encoder.conv2.bias", "encoder.positional_embedding", "decoder.positional_embedding",
                )
                val nameBytes = name.toByteArray(Charsets.UTF_8)
                w.int(nDims); w.int(nameBytes.size); w.int(if (f32) 0 else 1)
                for (i in nDims - 1 downTo 0) w.int(shape[i].toInt())
                os.write(nameBytes)
                streamTensor(t, os, f32) { n -> done += n; onProgress(Progress(done, total)) }
                written++
            }
        }
        return written
    }

    // ------------------------------------------------------------------ safetensors

    private class TensorRef(val file: File, val name: String, val dtype: String, val shape: List<Long>, val begin: Long, val end: Long) {
        val elements: Long get() = shape.fold(1L) { a, b -> a * b }
    }

    private fun readIndex(file: File): List<TensorRef> = RandomAccessFile(file, "r").use { raf ->
        val lenBytes = ByteArray(8).also { raf.readFully(it) }
        val headerLen = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).long
        if (headerLen <= 0 || headerLen > 100_000_000) throw IOException("bad safetensors header")
        val header = ByteArray(headerLen.toInt()).also { raf.readFully(it) }
        val base = 8 + headerLen
        val obj = json.parseToJsonElement(String(header, Charsets.UTF_8)).jsonObject
        obj.filterKeys { it != "__metadata__" }.map { (name, v) ->
            val o = v.jsonObject
            val offs = o.getValue("data_offsets").jsonArray
            TensorRef(file, name, o.getValue("dtype").jsonPrimitive.content,
                o.getValue("shape").jsonArray.map { it.jsonPrimitive.long },
                base + offs[0].jsonPrimitive.long, base + offs[1].jsonPrimitive.long)
        }.sortedBy { it.begin }
    }

    private fun streamTensor(t: TensorRef, os: OutputStream, f32: Boolean, onElements: (Long) -> Unit) {
        val inSize = when (t.dtype) { "F32" -> 4; "F16", "BF16" -> 2; else -> throw IOException("unsupported dtype ${t.dtype}") }
        if (t.end - t.begin != t.elements * inSize) throw IOException("size mismatch for ${t.name}")
        RandomAccessFile(t.file, "r").use { raf ->
            raf.seek(t.begin)
            val chunkElems = 1 shl 18
            val inBuf = ByteArray(chunkElems * inSize)
            val outBuf = ByteBuffer.allocate(chunkElems * 4).order(ByteOrder.LITTLE_ENDIAN)
            var left = t.elements
            while (left > 0) {
                val n = min(left, chunkElems.toLong()).toInt()
                raf.readFully(inBuf, 0, n * inSize)
                val inBb = ByteBuffer.wrap(inBuf, 0, n * inSize).order(ByteOrder.LITTLE_ENDIAN)
                outBuf.clear()
                for (i in 0 until n) {
                    val v = when (t.dtype) {
                        "F32" -> inBb.float
                        "F16" -> halfToFloat(inBb.short)
                        else -> java.lang.Float.intBitsToFloat((inBb.short.toInt() and 0xffff) shl 16)
                    }
                    // The reference converter casts everything to float16 first.
                    val h = floatToHalf(v)
                    if (f32) outBuf.putFloat(halfToFloat(h)) else outBuf.putShort(h)
                }
                os.write(outBuf.array(), 0, outBuf.position())
                left -= n
                onElements(n.toLong())
            }
        }
    }

    // ------------------------------------------------------------------ names & vocab

    /** model.encoder.layers.3.self_attn.k_proj.weight → encoder.blocks.3.attn.key.weight */
    fun ggmlName(hfName: String): String {
        val nn = hfName.split('.').drop(1).toMutableList() // drop "model"
        if (nn.size > 1 && nn[1] == "layers") {
            nn[1] = "blocks"
            val mid = nn.subList(3, nn.size - 1).joinToString(".")
            val mapped = if (mid == "encoder_attn.k_proj") {
                if (nn[0] == "encoder") "attn.key" else "cross_attn.key"
            } else CONV_MAP[mid] ?: throw IOException("unknown tensor $hfName")
            return (nn.subList(0, 3) + mapped + nn.last()).joinToString(".")
        }
        val name = nn.joinToString(".")
        return CONV_MAP[name] ?: name
    }

    private fun writeVocab(w: LeWriter, vocab: File) {
        val tokens = json.parseToJsonElement(vocab.readText()).jsonObject
        val decoder = byteDecoder()
        w.int(tokens.size)
        tokens.entries.sortedBy { it.value.jsonPrimitive.int }.forEach { (key, _) ->
            val bytes = ByteArray(key.length) { i ->
                (decoder[key[i]] ?: throw IOException("vocab: unexpected character")).toByte()
            }
            w.int(bytes.size)
            w.os.write(bytes)
        }
    }

    /** GPT-2 bytes_to_unicode(), inverted: printable stand-in character → original byte. */
    private fun byteDecoder(): Map<Char, Int> {
        val bs = ArrayList<Int>()
        bs += ('!'.code..'~'.code); bs += ('¡'.code..'¬'.code); bs += ('®'.code..'ÿ'.code)
        val cs = ArrayList(bs)
        var n = 0
        for (b in 0 until 256) if (b !in bs) { bs += b; cs += 256 + n; n++ }
        return cs.indices.associate { cs[it].toChar() to bs[it] }
    }

    // ------------------------------------------------------------------ mel filters

    private const val N_FFT_BINS = 201 // n_fft = 400

    /** librosa.filters.mel(sr=16000, n_fft=400, n_mels) with Slaney scale and norm. */
    fun melFilters(nMels: Int): FloatArray {
        val sr = 16_000.0
        val fft = DoubleArray(N_FFT_BINS) { it * (sr / 2) / (N_FFT_BINS - 1) }
        val fSp = 200.0 / 3
        val minLogHz = 1000.0
        val minLogMel = minLogHz / fSp
        val logStep = ln(6.4) / 27
        fun hzToMel(f: Double) = if (f >= minLogHz) minLogMel + ln(max(f, 1e-10) / minLogHz) / logStep else f / fSp
        fun melToHz(m: Double) = if (m >= minLogMel) minLogHz * exp(logStep * (m - minLogMel)) else m * fSp
        val top = hzToMel(sr / 2)
        val step = top / (nMels + 1) // numpy.linspace(0, top, n + 2)
        val melF = DoubleArray(nMels + 2) { melToHz(if (it == nMels + 1) top else it * step) }
        val out = FloatArray(nMels * N_FFT_BINS)
        for (i in 0 until nMels) {
            val enorm = 2.0 / (melF[i + 2] - melF[i])
            for (k in 0 until N_FFT_BINS) {
                val lower = -(melF[i] - fft[k]) / (melF[i + 1] - melF[i])
                val upper = (melF[i + 2] - fft[k]) / (melF[i + 2] - melF[i + 1])
                // librosa keeps the weights in a float32 array: round once, then again after the norm
                val w = max(0.0, min(lower, upper)).toFloat()
                out[i * N_FFT_BINS + k] = (w.toDouble() * enorm).toFloat()
            }
        }
        return out
    }

    // ------------------------------------------------------------------ helpers

    private class LeWriter(val os: OutputStream) {
        private val b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        fun int(v: Int) { b.clear(); b.putInt(v); os.write(b.array(), 0, 4) }
        fun float(v: Float) { b.clear(); b.putFloat(v); os.write(b.array(), 0, 4) }
    }

    /** IEEE 754 binary16, round to nearest even (as numpy astype(float16)). */
    fun floatToHalf(f: Float): Short {
        val bits = java.lang.Float.floatToRawIntBits(f)
        val sign = (bits ushr 16) and 0x8000
        val exp = (bits ushr 23) and 0xff
        var mant = bits and 0x7fffff
        if (exp == 0xff) return (sign or 0x7c00 or (if (mant != 0) 0x200 else 0)).toShort() // inf / nan
        val e = exp - 127 + 15
        if (e >= 0x1f) return (sign or 0x7c00).toShort() // overflow → inf
        if (e <= 0) {
            if (e < -10) return sign.toShort() // underflow → ±0
            mant = mant or 0x800000
            val shift = 14 - e
            var h = mant ushr shift
            val rem = mant and ((1 shl shift) - 1)
            val half = 1 shl (shift - 1)
            if (rem > half || (rem == half && (h and 1) == 1)) h++
            return (sign or h).toShort()
        }
        var h = (e shl 10) or (mant ushr 13)
        val rem = mant and 0x1fff
        if (rem > 0x1000 || (rem == 0x1000 && (h and 1) == 1)) h++ // may carry into the exponent: still correct
        return (sign or h).toShort()
    }

    fun halfToFloat(h: Short): Float {
        val v = h.toInt() and 0xffff
        val sign = (v and 0x8000) shl 16
        val exp = (v ushr 10) and 0x1f
        val mant = v and 0x3ff
        val bits = when {
            exp == 0 && mant == 0 -> sign
            exp == 0 -> return (if (sign != 0) -1f else 1f) * mant * 5.9604645e-8f // subnormal: mant × 2^-24
            exp == 0x1f -> sign or 0x7f800000 or (mant shl 13)
            else -> sign or ((exp - 15 + 127) shl 23) or (mant shl 13)
        }
        return java.lang.Float.intBitsToFloat(bits)
    }
}
