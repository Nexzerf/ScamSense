package com.scamsense.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNormalizerTest {

    @Test fun thaiDigitsBecomeAscii() {
        assertEquals("24 ชั่วโมง", TextNormalizer.normalize("๒๔ ชั่วโมง"))
    }

    @Test fun zeroWidthAndPunctuationRemovedInCompact() {
        val c = TextNormalizer.compact(TextNormalizer.normalize("O.T​P"))
        assertEquals("otp", c)
    }

    @Test fun cyrillicLookalikesFolded() {
        assertEquals("kbank", TextNormalizer.normalize("КВаnк"))
    }

    @Test fun sanitizeCapsLengthAndStripsControls() {
        val s = TextNormalizer.sanitizeInput("a\u0000b\u0007c" + "x".repeat(10_000))
        assertTrue(s.startsWith("abc"))
        assertTrue(s.length <= TextNormalizer.MAX_INPUT_CHARS)
    }

    @Test fun latinTokensSplitLettersAndDigits() {
        assertEquals(setOf("otp", "123"), TextNormalizer.latinTokens("รหัสotp123"))
    }

    @Test fun spacedSingleLettersJoined() {
        assertTrue("otp" in TextNormalizer.latinTokens(TextNormalizer.normalize("ขอ O T P ด่วน")))
        assertTrue("otp" in TextNormalizer.latinTokens(TextNormalizer.normalize("o.t.p")))
        assertFalse("ab" in TextNormalizer.latinTokens(TextNormalizer.normalize("a ก b")))
    }

    @Test fun maskKeepsLastThreeDigits() {
        assertEquals("•••-•-•••89-0", TextNormalizer.maskSensitiveDigits("123-4-56789-0"))
        assertEquals("ราคา 500 บาท", TextNormalizer.maskSensitiveDigits("ราคา 500 บาท"))
    }

    @Test fun approxContainsAllowsOneEdit() {
        assertTrue(KeywordEngine.approxContains("โอนค่าธรรมเนีมก่อน", "ค่าธรรมเนียม", 1))
        assertFalse(KeywordEngine.approxContains("สวัสดีตอนเช้า", "ค่าธรรมเนียม", 1))
    }
}
