package com.scamsense.core.eval

import com.scamsense.core.engine.AnalysisOutcome
import com.scamsense.core.engine.CheckType
import com.scamsense.core.engine.RiskLevel
import com.scamsense.core.engine.ScamAnalyzer
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Speech recognition misspells Thai: same-sounding letters (ภ/พ, ญ/ย, ศ/ส, ธ/ท, ร/ล …)
 * and dropped tone marks. Sound-alike matching (speech = true) must keep catching scams
 * in such transcripts without flagging ordinary conversation.
 */
class SpeechRobustnessTest {

    private val analyzer = ScamAnalyzer()

    private data class Item(val label: String, val text: String)

    private fun load(): List<Item> =
        javaClass.getResourceAsStream("/eval/thai_scam_benchmark.tsv")!!.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line -> line.split('\t').let { Item(it[1], it[3]) } }

    /** Typical recogniser errors, applied deterministically. */
    private fun garble(text: String): String {
        val swaps = mapOf('ภ' to 'พ', 'ญ' to 'ย', 'ศ' to 'ส', 'ษ' to 'ส', 'ธ' to 'ท', 'ณ' to 'น', 'ร' to 'ล', 'ข' to 'ค', 'ใ' to 'ไ')
        val sb = StringBuilder()
        var marks = 0
        for (c in text) {
            when {
                c in "่้๊๋์" -> if (++marks % 2 == 0) sb.append(c) // drop every other tone mark
                c in swaps -> sb.append(swaps.getValue(c))
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun flagged(text: String, speech: Boolean): Boolean =
        (analyzer.analyze(CheckType.MESSAGE, text, speech = speech) as AnalysisOutcome.Success).result.level != RiskLevel.SAFE

    @Test fun garbledScamsAreStillCaught() {
        val scams = load().filter { it.label == "scam" }
        val plain = scams.count { flagged(garble(it.text), speech = false) }.toDouble() / scams.size
        val sound = scams.count { flagged(garble(it.text), speech = true) }.toDouble() / scams.size
        println("garbled recall: text-mode=%.3f speech-mode=%.3f (n=%d)".format(plain, sound, scams.size))
        // Looser matching (6+ letters) reaches 0.85 but raises false alarms to 10%; prefer fewer false alarms.
        assertTrue("speech-mode recall $sound", sound >= 0.80)
        assertTrue("speech mode must not be worse than text mode", sound >= plain)
    }

    @Test fun ordinaryConversationStaysSafe() {
        val benign = load().filter { it.label == "benign" }
        val fpr = benign.count { flagged(garble(it.text), speech = true) }.toDouble() / benign.size
        println("speech-mode false-positive rate: %.3f (n=%d)".format(fpr, benign.size))
        assertTrue("false-positive rate $fpr", fpr <= 0.10)
        listOf(
            "สวัสดีค่ะแม่ วันนี้กินข้าวหรือยัง เดี๋ยวเย็นนี้หนูกลับบ้านนะ",
            "พรุ่งนี้หมอนัดตรวจตอนเก้าโมง อย่าลืมเอาบัตรไปด้วยนะครับ",
            "ฝนตกหนักเลย ขับรถช้า ๆ นะ ถึงบ้านแล้วโทรบอกด้วย",
        ).forEach { assertTrue(it, !flagged(garble(it), speech = true)) }
    }

    @Test fun typicalMisheardScamCallIsDangerous() {
        // "ขอรหัส OTP ด่วน แล้วโอนเงินเข้าบัญชีปลอดภัย" as a weak recogniser might write it
        val heard = "ขอละหัดโอทีพีด่วน แล้วโอนเงินเค้าบันชีปลอดพัย"
        val r = (analyzer.analyze(CheckType.MESSAGE, heard, speech = true) as AnalysisOutcome.Success).result
        println("misheard: ${r.score} ${r.level} block=${r.shouldBlock}")
        assertTrue("score ${r.score}", r.level == RiskLevel.DANGEROUS)
    }
}
