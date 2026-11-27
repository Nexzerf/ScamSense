package com.scamsense.core.live

import com.scamsense.core.live.LiveCallMonitor.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class LiveCallTest {

    // ------------------------------------------------------------------ monitor

    @Test fun scamSpreadOverSentencesEscalatesToHangUp() {
        val m = LiveCallMonitor()
        val actions = listOf(
            "สวัสดีครับ ผมโทรจากสถานีตำรวจนครบาล",
            "ตอนนี้บัญชีของคุณพัวพันกับคดีฟอกเงิน",
            "คุณต้องโอนเงินทั้งหมดไปที่บัญชีปลอดภัยเพื่อตรวจสอบ ห้ามวางสาย",
        ).map { m.onTranscript(it).action }
        assertEquals(Action.HANG_UP, actions.last { it != Action.NONE })
        assertTrue(m.latest!!.shouldBlock)
    }

    @Test fun withoutAutoHangUpItOnlyWarns() {
        val m = LiveCallMonitor(autoHangUp = false)
        val first = m.onTranscript("ขอรหัส OTP ที่ส่งไปทาง SMS ด่วนครับ บัญชีคุณจะถูกระงับ").action
        assertEquals(Action.WARN, first)
        assertTrue(m.latest!!.shouldBlock)
        assertEquals(Action.NONE, m.onTranscript("ห้ามวางสายนะครับ").action)
    }

    @Test fun normalConversationNeverWarns() {
        val m = LiveCallMonitor()
        val actions = listOf(
            "ฮัลโหลแม่ วันนี้ทานข้าวหรือยัง",
            "อาทิตย์นี้หนูจะพาหลานไปเยี่ยมนะ",
            "อากาศเย็นแล้ว ใส่เสื้อหนา ๆ ด้วย",
        ).map { m.onTranscript(it).action }
        assertTrue(actions.all { it == Action.NONE })
    }

    @Test fun eachLevelFiresOnlyOnce() {
        val m = LiveCallMonitor(autoHangUp = false)
        val fired = (1..5).map { m.onTranscript("ด่วน ขอรหัส OTP และเลขบัญชีธนาคารที่ลิงก์นี้").action }
        assertEquals(1, fired.count { it != Action.NONE })
    }

    @Test fun whisperHallucinationsAreIgnored() {
        assertNull(LiveCallMonitor.clean("[เพลง]"))
        assertNull(LiveCallMonitor.clean("ซับไตเติ้ลโดย ..."))
        assertEquals("ขอรหัสหน่อย", LiveCallMonitor.clean("(เสียงดนตรี) ขอรหัสหน่อย"))
    }

    // ------------------------------------------------------------------ chunker

    private fun tone(ms: Int, amp: Double = 8000.0) =
        ShortArray(16 * ms) { (amp * sin(2 * PI * 300 * it / 16_000.0)).toInt().toShort() }

    private fun silence(ms: Int) = ShortArray(16 * ms) { ((it % 7) - 3).toShort() } // faint noise

    @Test fun utteranceEndsAfterSilence() {
        val c = SpeechChunker()
        assertTrue(c.feed(silence(500)).isEmpty())
        assertTrue(c.feed(tone(1_500)).isEmpty())
        val out = c.feed(silence(800))
        assertEquals(1, out.size)
        assertTrue(out[0].size in 16 * 1_500..16 * 2_400)
    }

    @Test fun longSpeechIsCutForRealTime() {
        val c = SpeechChunker(maxSegmentMs = 7_000)
        val out = c.feed(tone(16_000))
        assertTrue("got ${out.size}", out.size >= 2)
    }

    @Test fun shortClicksAreDropped() {
        val c = SpeechChunker()
        val out = c.feed(silence(300) + tone(200) + silence(1_000))
        assertTrue(out.isEmpty())
    }

    @Test fun flushReturnsTrailingSpeech() {
        val c = SpeechChunker()
        c.feed(tone(1_200))
        val last = c.flush()
        assertTrue(last != null && last.size >= 16 * 1_000)
    }

    @Test fun oddSizedWritesAreHandled() {
        val c = SpeechChunker()
        val all = silence(200) + tone(1_500) + silence(900)
        var got = 0
        var i = 0
        while (i < all.size) {
            val n = minOf(37, all.size - i)
            got += c.feed(all.copyOfRange(i, i + n)).size
            i += n
        }
        assertEquals(1, got)
    }
}
