package com.scamsense.app.engine

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * "Meaning-level" matching without a heavy ML model.
 *
 * Thai is written without spaces, so word-level models (Word2Vec etc.) need a
 * segmenter and a large model file. Instead we embed each text as a TF-IDF weighted
 * vector of character 2- and 3-grams (hashed into a fixed space) and compare with
 * cosine similarity. This captures paraphrases, re-ordered phrases and small
 * spelling changes, runs in well under a millisecond per comparison, and needs
 * no network or model download.
 */
class SimilarityEngine(private val templates: List<ScamTemplate>) {

    data class Match(val template: ScamTemplate, val similarity: Double)

    private class SparseVector(val indices: IntArray, val values: FloatArray)

    private val idf: FloatArray
    private val templateVectors: List<SparseVector>

    init {
        val docFreq = IntArray(DIMENSIONS)
        val templateCounts = templates.map { t ->
            val counts = ngramCounts(TextNormalizer.compact(TextNormalizer.normalize(t.text)))
            for (k in counts.keys) docFreq[k]++
            counts
        }
        val n = templates.size.toDouble()
        idf = FloatArray(DIMENSIONS) { i -> (ln((n + 1.0) / (docFreq[i] + 1.0)) + 1.0).toFloat() }
        templateVectors = templateCounts.map { toVector(it) }
    }

    /** Best matching known scam template, or null when the text is too short to judge. */
    fun bestMatch(compactText: String): Match? {
        if (compactText.length < MIN_CHARS || templates.isEmpty()) return null
        val query = toVector(ngramCounts(compactText))
        if (query.indices.isEmpty()) return null
        var best = -1
        var bestSim = 0.0
        for (i in templateVectors.indices) {
            val s = dot(query, templateVectors[i])
            if (s > bestSim) {
                bestSim = s
                best = i
            }
        }
        return if (best >= 0) Match(templates[best], bestSim) else null
    }

    private fun ngramCounts(s: String): Map<Int, Int> {
        val counts = HashMap<Int, Int>()
        for (n in NGRAM_MIN..NGRAM_MAX) {
            if (s.length < n) continue
            for (i in 0..s.length - n) {
                val h = hashGram(s, i, n)
                counts[h] = (counts[h] ?: 0) + 1
            }
        }
        return counts
    }

    private fun hashGram(s: String, start: Int, n: Int): Int {
        var h = 0x811C9DC5.toInt() xor n // FNV-1a, seeded with n so 2- and 3-grams differ
        for (i in start until start + n) {
            h = h xor s[i].code
            h *= 0x01000193
        }
        return (h ushr 1) % DIMENSIONS
    }

    private fun toVector(counts: Map<Int, Int>): SparseVector {
        val keys = counts.keys.sorted().toIntArray()
        val values = FloatArray(keys.size)
        var norm = 0.0
        for ((i, k) in keys.withIndex()) {
            // Sub-linear TF damps repeated grams ("ด่วนๆๆๆ") from dominating.
            val w = ((1.0 + ln(counts.getValue(k).toDouble())) * idf[k]).toFloat()
            values[i] = w
            norm += w.toDouble() * w
        }
        val inv = if (norm > 0) (1.0 / sqrt(norm)).toFloat() else 0f
        for (i in values.indices) values[i] *= inv
        return SparseVector(keys, values)
    }

    private fun dot(a: SparseVector, b: SparseVector): Double {
        var i = 0
        var j = 0
        var sum = 0.0
        while (i < a.indices.size && j < b.indices.size) {
            val ai = a.indices[i]
            val bj = b.indices[j]
            when {
                ai == bj -> { sum += a.values[i] * b.values[j]; i++; j++ }
                ai < bj -> i++
                else -> j++
            }
        }
        return sum
    }

    companion object {
        private const val DIMENSIONS = 1 shl 15
        private const val NGRAM_MIN = 2
        private const val NGRAM_MAX = 3
        const val MIN_CHARS = 12
    }
}
