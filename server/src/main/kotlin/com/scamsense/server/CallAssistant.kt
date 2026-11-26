package com.scamsense.server

import com.scamsense.core.engine.AnalysisOutcome
import com.scamsense.core.engine.CheckType
import com.scamsense.core.engine.PhoneAnalyzer
import com.scamsense.core.engine.RiskLevel
import com.scamsense.core.engine.RiskResult
import com.scamsense.core.engine.ScamAnalyzer
import com.scamsense.core.engine.TextNormalizer

/**
 * The assistant's conversation policy — pure logic, no I/O, fully unit-tested.
 *
 * Turn 1: greet, disclose that this is an automated assistant, ask why they are calling.
 * After each answer the cumulative transcript runs through the shared [ScamAnalyzer]:
 *  - dangerous / block pattern  -> polite hang-up + SMS alert (scammer is not tipped off)
 *  - safe and we heard enough   -> whisper a summary to the user and connect the call
 *  - unclear                    -> one more question, then take a message instead of connecting
 */
class CallAssistant(private val analyzer: ScamAnalyzer = ScamAnalyzer(), private val maxTurns: Int = 3) {

    sealed interface Step {
        data class Ask(val prompt: String) : Step
        data class HangUpScam(val result: RiskResult?, val reason: String) : Step
        data class Connect(val result: RiskResult?) : Step
        data class TakeMessage(val result: RiskResult?) : Step
        data object HangUpSilent : Step
    }

    fun greeting(subscriberName: String): String =
        "สวัสดีค่ะ ที่นี่ผู้ช่วยรับสายอัตโนมัติของคุณ$subscriberName สายนี้ถูกบันทึกและถอดเป็นข้อความเพื่อความปลอดภัย " +
            "กรุณาแจ้งชื่อและธุระที่โทรมาค่ะ"

    /** Number-only check before answering (spoofed +697/+698, etc.). */
    fun screenCaller(caller: String): Step? {
        if (caller.isBlank()) return null
        val a = PhoneAnalyzer.analyze(PhoneAnalyzer.classify(caller), emptyList(), fromMessage = false)
        return if (a.block) Step.HangUpScam(null, a.signals.maxBy { it.weight }.title) else null
    }

    /**
     * @param transcript what the caller said in previous turns
     * @param speech what they just said (null/blank = silence)
     */
    fun onTurn(transcript: List<String>, speech: String?, silentTurns: Int): Pair<Step, List<String>> {
        val heard = speech?.let { TextNormalizer.sanitizeInput(it).take(1_000) }?.takeIf { it.isNotBlank() }
        if (heard == null) {
            val silent = silentTurns + 1
            return if (silent >= 2) Step.HangUpSilent to transcript
            else Step.Ask("ขออภัยค่ะ ไม่ได้ยินเสียง กรุณาแจ้งชื่อและธุระที่โทรมาอีกครั้งค่ะ") to transcript
        }
        val all = (transcript + heard).takeLast(10)
        val turns = all.size
        val result = (analyzer.analyze(CheckType.MESSAGE, all.joinToString(" ")) as? AnalysisOutcome.Success)?.result

        if (result != null && (result.shouldBlock || result.level == RiskLevel.DANGEROUS)) {
            return Step.HangUpScam(result, result.matchedScamType ?: "พบรูปแบบมิจฉาชีพ") to all
        }
        val suspicious = result?.level == RiskLevel.SUSPICIOUS
        return when {
            turns == 1 -> Step.Ask("ขอบคุณค่ะ ขอทราบชื่อ และเกี่ยวข้องกับเจ้าของเบอร์อย่างไรคะ") to all
            suspicious && turns < maxTurns ->
                Step.Ask("ขอทราบรายละเอียดเพิ่มเติม และหน่วยงานหรือเบอร์สำหรับติดต่อกลับค่ะ") to all
            suspicious -> Step.TakeMessage(result) to all
            else -> Step.Connect(result) to all
        }
    }

    /** Short spoken summary the protected person hears before the call connects. */
    fun whisper(transcript: List<String>): String {
        val said = TextNormalizer.preview(transcript.joinToString(" "), 140)
        return "สายจากผู้ช่วย สแกมเซนส์ ผู้โทรแจ้งว่า $said ระบบตรวจแล้วไม่พบความเสี่ยงค่ะ"
    }
}
