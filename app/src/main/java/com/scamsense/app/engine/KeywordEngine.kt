package com.scamsense.app.engine

/**
 * Matches keyword rules against a [TextContext].
 *
 * Matching strategy per term (chosen once, at construction):
 *  - Short pure-Latin terms (<= 5 letters, e.g. "otp", "pin") must match a whole Latin
 *    token, so "shopping" does not trigger "pin".
 *  - Everything else is matched as a substring of the *compacted* text, which ignores
 *    spaces/punctuation used for obfuscation ("บ.ช. ธนาคาร").
 *  - Thai terms of 7+ characters additionally allow one typo/edit (approximate substring
 *    matching, Sellers' algorithm, O(n·m)), catching deliberate misspellings.
 */
class KeywordEngine(rules: List<KeywordRule>) {

    data class Hit(val rule: KeywordRule, val matchedTerm: String)

    private class Term(val original: String, val compact: String, val tokenOnly: Boolean, val fuzzy: Boolean)

    private class CompiledRule(val rule: KeywordRule, val terms: List<Term>)

    private val compiled: List<CompiledRule> = rules.map { compileRule(it) }

    fun match(ctx: TextContext): List<Hit> = compiled.mapNotNull { matchRule(it, ctx) }

    /** One-off matching for user-defined words (compiled per call; lists are tiny). */
    fun matchCustom(ctx: TextContext, words: List<String>): List<String> =
        words.filter { it.isNotBlank() }
            .map { compileTerm(it) }
            .filter { it.compact.isNotEmpty() && termMatches(it, ctx) }
            .map { it.original }

    private fun compileRule(rule: KeywordRule) =
        CompiledRule(rule, rule.terms.map { compileTerm(it) }.filter { it.compact.isNotEmpty() })

    private fun compileTerm(term: String): Term {
        val compact = TextNormalizer.compact(TextNormalizer.normalize(term))
        val pureLatin = compact.all { it in 'a'..'z' }
        val thaiCount = compact.count { TextNormalizer.isThai(it) }
        return Term(
            original = term.trim(),
            compact = compact,
            tokenOnly = pureLatin && compact.length <= 5,
            fuzzy = thaiCount >= 7,
        )
    }

    private fun matchRule(rule: CompiledRule, ctx: TextContext): Hit? {
        // Exact pass first (cheap), then the fuzzy pass only if needed.
        rule.terms.firstOrNull { exactMatch(it, ctx) }?.let { return Hit(rule.rule, it.original) }
        rule.terms.firstOrNull { it.fuzzy && fuzzyMatch(it, ctx) }?.let { return Hit(rule.rule, it.original) }
        return null
    }

    private fun termMatches(term: Term, ctx: TextContext) =
        exactMatch(term, ctx) || (term.fuzzy && fuzzyMatch(term, ctx))

    private fun exactMatch(term: Term, ctx: TextContext): Boolean =
        if (term.tokenOnly) term.compact in ctx.latinTokens else ctx.compact.contains(term.compact)

    private fun fuzzyMatch(term: Term, ctx: TextContext): Boolean {
        val text = if (ctx.compact.length > FUZZY_WINDOW) ctx.compact.substring(0, FUZZY_WINDOW) else ctx.compact
        return approxContains(text, term.compact, maxEdits = 1)
    }

    companion object {
        /** Bound fuzzy work so worst-case cost stays ~O(2000 × term length). */
        private const val FUZZY_WINDOW = 2_000

        /** True if [pattern] occurs in [text] with at most [maxEdits] insertions/deletions/substitutions. */
        fun approxContains(text: String, pattern: String, maxEdits: Int): Boolean {
            val m = pattern.length
            if (m == 0) return true
            if (text.length + maxEdits < m) return false
            var prev = IntArray(m + 1) { it }
            var cur = IntArray(m + 1)
            if (prev[m] <= maxEdits) return true
            for (c in text) {
                cur[0] = 0 // a match may start anywhere in the text
                for (j in 1..m) {
                    val cost = if (pattern[j - 1] == c) 0 else 1
                    cur[j] = minOf(prev[j - 1] + cost, prev[j] + 1, cur[j - 1] + 1)
                }
                if (cur[m] <= maxEdits) return true
                val t = prev; prev = cur; cur = t
            }
            return false
        }
    }
}
