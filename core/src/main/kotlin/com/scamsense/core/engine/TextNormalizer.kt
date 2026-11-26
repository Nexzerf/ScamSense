package com.scamsense.core.engine

import java.text.Normalizer

/**
 * Normalizes Thai/English text so that keyword and similarity matching is robust to
 * the tricks scammers use to dodge filters: zero-width characters, Thai digits,
 * full-width letters, look-alike Cyrillic/Greek letters, and punctuation/spaces
 * inserted between letters (e.g. "บ.ช.ธนาคาร", "O T P").
 *
 * All functions are pure and linear-time (no backtracking regex), so they are safe
 * against ReDoS even for hostile input.
 */
object TextNormalizer {

    /** Hard cap on analysed input; protects memory/CPU from oversized shared text. */
    const val MAX_INPUT_CHARS = 5_000

    private val HOMOGLYPHS: Map<Char, Char> = mapOf(
        // Cyrillic
        'а' to 'a', 'е' to 'e', 'о' to 'o', 'р' to 'p', 'с' to 'c', 'х' to 'x',
        'у' to 'y', 'і' to 'i', 'ј' to 'j', 'ѕ' to 's', 'ԁ' to 'd', 'ӏ' to 'l',
        'к' to 'k', 'м' to 'm', 'н' to 'h', 'т' to 't', 'в' to 'b',
        // Greek
        'ο' to 'o', 'α' to 'a', 'ν' to 'v', 'κ' to 'k', 'τ' to 't', 'ι' to 'i',
        'ρ' to 'p', 'ε' to 'e',
    )

    /**
     * Cleans untrusted input (from the keyboard, clipboard, speech or other apps):
     * removes control characters except newlines/tabs and caps the length.
     */
    fun sanitizeInput(raw: CharSequence?): String {
        if (raw == null) return ""
        val limit = minOf(raw.length, MAX_INPUT_CHARS)
        val sb = StringBuilder(limit)
        for (i in 0 until limit) {
            val c = raw[i]
            if (c == '\n' || c == '\t' || !Character.isISOControl(c)) sb.append(c)
        }
        return sb.toString().trim()
    }

    fun isThai(c: Char): Boolean = c in 'ก'..'๛'

    private fun isZeroWidth(c: Char): Boolean =
        c.code in 0x200B..0x200F || c.code == 0x2060 || c.code == 0xFEFF || c.code == 0x00AD

    /** Lower-cased, NFKC-normalized text with look-alikes folded and whitespace collapsed. */
    fun normalize(text: String): String {
        val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC)
        val sb = StringBuilder(nfkc.length)
        var lastWasSpace = false
        for (raw in nfkc) {
            if (isZeroWidth(raw)) continue
            var c = raw
            if (c in '๐'..'๙') c = '0' + (c - '๐') // Thai digits -> ASCII
            c = HOMOGLYPHS[c] ?: c.lowercaseChar()
            c = HOMOGLYPHS[c] ?: c
            if (c.isWhitespace()) {
                if (!lastWasSpace && sb.isNotEmpty()) sb.append(' ')
                lastWasSpace = true
            } else {
                sb.append(c)
                lastWasSpace = false
            }
        }
        // "เเ" (two sara e) is a common way to fake "แ" and dodge filters.
        return sb.toString().trim().replace("เเ", "แ")
    }

    /**
     * Keeps only Thai letters, ASCII letters and digits. Used for substring matching
     * so that "บ.ช. ธนาคาร" and "O-T-P" still match "บชธนาคาร" and "otp".
     */
    fun compact(normalized: String): String {
        val sb = StringBuilder(normalized.length)
        for (c in normalized) {
            if (isThai(c) || c in 'a'..'z' || c in '0'..'9') sb.append(c)
        }
        return sb.toString()
    }

    /**
     * Latin words and digit runs, split separately ("รหัสotp123" -> otp, 123).
     * Runs of single letters ("O T P", "o.t.p") are also joined into one token so
     * spacing tricks cannot hide short keywords.
     */
    fun latinTokens(normalized: String): Set<String> {
        val out = HashSet<String>()
        val sb = StringBuilder()
        val singles = StringBuilder()
        var mode = 0 // 0 none, 1 letters, 2 digits
        fun flushSingles() {
            if (singles.length >= 2) out.add(singles.toString())
            singles.setLength(0)
        }
        fun flush() {
            if (sb.isNotEmpty()) {
                val tok = sb.toString()
                out.add(tok)
                if (mode == 1 && tok.length == 1) singles.append(tok) else flushSingles()
            }
            sb.setLength(0)
        }
        for (c in normalized) {
            val m = when (c) {
                in 'a'..'z' -> 1
                in '0'..'9' -> 2
                else -> 0
            }
            if (m != mode) flush()
            // Thai letters or line breaks end a run of spaced single letters.
            if (m == 0 && (isThai(c) || c == '\n')) flushSingles()
            if (m != 0) sb.append(c)
            mode = m
        }
        flush()
        flushSingles()
        return out
    }

    /**
     * Masks long digit runs (account numbers, ID cards, phone numbers) for storage and
     * sharing, keeping the last 3 digits so the user can still recognise them.
     */
    fun maskSensitiveDigits(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            if (text[i].isDigit()) {
                var j = i
                var digits = 0
                while (j < text.length && (text[j].isDigit() || text[j] == '-' || text[j] == ' ')) {
                    if (text[j].isDigit()) digits++
                    j++
                }
                // Trim trailing separators back out of the run.
                while (j > i && !text[j - 1].isDigit()) j--
                val run = text.substring(i, j)
                if (digits >= 6) {
                    var keep = 3
                    val masked = StringBuilder(run.length)
                    for (k in run.indices.reversed()) {
                        val ch = run[k]
                        if (ch.isDigit()) {
                            masked.append(if (keep > 0) ch else '•')
                            keep--
                        } else {
                            masked.append(ch)
                        }
                    }
                    sb.append(masked.reverse())
                } else {
                    sb.append(run)
                }
                i = j
            } else {
                sb.append(text[i])
                i++
            }
        }
        return sb.toString()
    }

    fun preview(text: String, maxChars: Int = 280): String {
        val masked = maskSensitiveDigits(text.trim())
        return if (masked.length <= maxChars) masked else masked.take(maxChars).trimEnd() + "…"
    }
}

/** Pre-computed views of one input text, shared by all analyzers. */
class TextContext(val original: String) {
    val normalized: String = TextNormalizer.normalize(original)
    val compact: String = TextNormalizer.compact(normalized)
    val latinTokens: Set<String> = TextNormalizer.latinTokens(normalized)
}
