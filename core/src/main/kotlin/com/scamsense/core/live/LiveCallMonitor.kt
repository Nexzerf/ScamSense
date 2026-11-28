package com.scamsense.core.live

import com.scamsense.core.engine.AnalysisOutcome
import com.scamsense.core.engine.CheckType
import com.scamsense.core.engine.RiskLevel
import com.scamsense.core.engine.RiskResult
import com.scamsense.core.engine.ScamAnalyzer
import com.scamsense.core.engine.TextNormalizer

/**
 * Decides, utterance by utterance, what to do while a call is in progress.
 *
 * The caller's words arrive as speech-to-text lines. The recent window is re-checked with
 * the same [ScamAnalyzer] the app uses for messages, so a scam script is recognised even
 * when it is spread over several sentences ("ผมโทรจากตำรวจ" … "บัญชีคุณพัวพันคดี" … "ต้องโอน…").
 *
 * Escalation only goes up, and each level fires once, so the user is not nagged:
 * NONE → CAUTION (suspicious) → WARN (dangerous) → HANG_UP (block-level, if allowed).
 */
class LiveCallMonitor(
    private val analyzer: ScamAnalyzer = ScamAnalyzer(),
    private val autoHangUp: Boolean = true,
    private val windowLines: Int = 12,
) {
    enum class Action { NONE, CAUTION, WARN, HANG_UP }

    data class Update(val action: Action, val result: RiskResult?)

    private val lines = ArrayList<String>()
    private var lastResult: RiskResult? = null
    private var escalation = Action.NONE

    val transcript: List<String> get() = lines.toList()
    val latest: RiskResult? get() = lastResult

    fun onTranscript(raw: String): Update {
        val text = clean(raw) ?: return Update(Action.NONE, lastResult)
        lines += text
        val window = lines.takeLast(windowLines).joinToString(" ")
        val result = (analyzer.analyze(CheckType.MESSAGE, window, speech = true) as? AnalysisOutcome.Success)?.result
            ?: return Update(Action.NONE, lastResult)
        lastResult = result
        val wanted = when {
            result.shouldBlock && autoHangUp -> Action.HANG_UP
            result.shouldBlock || result.level == RiskLevel.DANGEROUS -> Action.WARN
            result.level == RiskLevel.SUSPICIOUS -> Action.CAUTION
            else -> Action.NONE
        }
        return if (wanted.ordinal > escalation.ordinal) {
            escalation = wanted
            Update(wanted, result)
        } else {
            Update(Action.NONE, result)
        }
    }

    companion object {
        /**
         * Whisper sometimes "hears" captions or music tags in silence or noise. Those lines
         * are dropped so they can neither trigger nor dilute a decision.
         */
        private val HALLUCINATIONS = listOf(
            "ซับไตเติ้ล", "คำบรรยาย", "subtitles", "thanks for watching", "ขอบคุณที่รับชม",
            "กดติดตาม", "subscribe", "โปรดติดตามตอนต่อไป",
        )

        fun clean(raw: String): String? {
            var t = TextNormalizer.sanitizeInput(raw)
            // Remove bracketed annotations: [เพลง], (เสียงดนตรี), *music*
            t = t.replace(Regex("""\[[^\]]{0,40}]|\([^)]{0,40}\)|\*[^*]{0,40}\*"""), " ").trim()
            if (TextNormalizer.compact(TextNormalizer.normalize(t)).length < 2) return null
            val lower = t.lowercase()
            if (HALLUCINATIONS.any { lower.contains(it) }) return null
            return t.take(500)
        }
    }
}
