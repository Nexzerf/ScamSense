package com.scamsense.core.asr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class HfWhisperToGgmlTest {

    @Test fun tensorNamesFollowWhisperCpp() {
        val n = HfWhisperToGgml::ggmlName
        assertEquals("encoder.blocks.3.attn.key.weight", n("model.encoder.layers.3.self_attn.k_proj.weight"))
        assertEquals("decoder.blocks.1.cross_attn.key.weight", n("model.decoder.layers.1.encoder_attn.k_proj.weight"))
        assertEquals("decoder.blocks.0.cross_attn_ln.bias", n("model.decoder.layers.0.encoder_attn_layer_norm.bias"))
        assertEquals("encoder.blocks.0.mlp.0.weight", n("model.encoder.layers.0.fc1.weight"))
        assertEquals("decoder.token_embedding.weight", n("model.decoder.embed_tokens.weight"))
        assertEquals("encoder.ln_post.bias", n("model.encoder.layer_norm.bias"))
        assertEquals("encoder.conv1.weight", n("model.encoder.conv1.weight"))
    }

    @Test fun halfPrecisionRoundTripsEveryValue() {
        for (h in 0 until 0x10000) {
            val exp = (h ushr 10) and 0x1f
            if (exp == 0x1f && (h and 0x3ff) != 0) continue // NaN payloads
            val f = HfWhisperToGgml.halfToFloat(h.toShort())
            assertEquals("half 0x%04x".format(h), h, HfWhisperToGgml.floatToHalf(f).toInt() and 0xffff)
        }
        // ties round to even, like numpy
        assertEquals(0x3c00, HfWhisperToGgml.floatToHalf(1f + 1f / 2048).toInt() and 0xffff)
        assertEquals(0x3c02, HfWhisperToGgml.floatToHalf(1f + 3f / 2048).toInt() and 0xffff)
        assertEquals(0x7c00, HfWhisperToGgml.floatToHalf(1e6f).toInt() and 0xffff)
    }

    @Test fun melFiltersMatchOpenAiWhisper() {
        val f = HfWhisperToGgml.melFilters(80)
        assertEquals(80 * 201, f.size)
        // Values of whisper's mel_filters.npz (mel_80): 391 non-zero weights, max 0.025880683.
        assertEquals(391, f.count { it != 0f })
        assertEquals(0.025880683f, f.max(), 1e-8f)
    }

    /**
     * Byte-for-byte check against whisper.cpp's convert-h5-to-ggml.py on a synthetic
     * checkpoint. Run with SS_HF_FIXTURE=<dir with config.json, vocab.json, *.safetensors,
     * reference.bin>; skipped otherwise (the fixture is ~200 MB).
     */
    @Test fun matchesReferenceConverter() {
        val dir = System.getenv("SS_HF_FIXTURE")?.let(::File)
        assumeTrue(dir != null && File(dir, "reference.bin").exists())
        dir!!
        val out = File.createTempFile("ggml", ".bin")
        try {
            HfWhisperToGgml.convert(
                File(dir, "config.json"), File(dir, "vocab.json"),
                dir.listFiles { f -> f.name.endsWith(".safetensors") }!!.toList(), out,
            )
            val ref = File(dir, "reference.bin").readBytes()
            val got = out.readBytes()
            assertEquals(ref.size, got.size)
            assertArrayEquals(ref, got)
        } finally {
            out.delete()
        }
    }
}
