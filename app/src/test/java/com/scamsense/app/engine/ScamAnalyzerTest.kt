package com.scamsense.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScamAnalyzerTest {

    private val analyzer = ScamAnalyzer()

    private fun check(type: CheckType, text: String, rules: UserRules = UserRules()): RiskResult {
        val outcome = analyzer.analyze(type, text, rules)
        assertTrue("expected success for: $text but was $outcome", outcome is AnalysisOutcome.Success)
        return (outcome as AnalysisOutcome.Success).result
    }

    private fun msg(text: String, rules: UserRules = UserRules()) = check(CheckType.MESSAGE, text, rules)

    // ------------------------------------------------------------- scam messages

    @Test fun callCenterPoliceScamIsBlocked() {
        val r = msg("ติดต่อจากตำรวจ บัญชีของท่านพัวพันคดีฟอกเงิน ต้องโอนเงินไปบัญชีปลอดภัยเพื่อตรวจสอบ ห้ามวางสาย")
        assertEquals(RiskLevel.DANGEROUS, r.level)
        assertTrue(r.shouldBlock)
        assertTrue(r.score >= ScamAnalyzer.BLOCK_FLOOR)
    }

    @Test fun otpPhishingWithLinkIsBlocked() {
        val r = msg("ธนาคารแจ้ง บัญชีของคุณถูกระงับ กรุณากรอกรหัส OTP ที่ http://kbank-secure.xyz/login ภายใน 24 ชั่วโมง")
        assertEquals(RiskLevel.DANGEROUS, r.level)
        assertTrue(r.shouldBlock)
        assertTrue(r.extractedUrls.isNotEmpty())
    }

    @Test fun obfuscatedKeywordsStillDetected() {
        // dots/spaces between letters, zero-width space, Thai digits, double sara-e
        val r = msg("ขอ บ.ช.ธนาคาร และ O T P ด​่วน ภายใน ๒๔ ชั่วโมง เเจ้งกลับด้วย")
        assertTrue("score=${r.score} ${r.signals}", r.level != RiskLevel.SAFE)
        assertTrue(r.signals.any { it.category == SignalCategory.SENSITIVE_INFO })
        assertTrue("spaced O T P should be caught", r.signals.any { it.title.contains("OTP") })
        assertTrue(r.shouldBlock)
    }

    @Test fun paraphrasedScamCaughtBySimilarity() {
        // Not an exact template: re-worded parcel scam.
        val r = msg("แจ้งจากไปรษณีย์ พัสดุของท่านจัดส่งไม่ได้ เพราะที่อยู่ไม่ครบ โปรดอัปเดตข้อมูลและจ่ายค่าธรรมเนียมเล็กน้อยผ่านลิงก์")
        assertTrue(r.signals.any { it.category == SignalCategory.SIMILAR_SCAM })
        assertTrue("score=${r.score}", r.level != RiskLevel.SAFE)
        assertNotNull(r.matchedScamType)
    }

    @Test fun fuzzyTypoIsDetected() {
        // "ค่าธรรมเนียม" with one character dropped
        val r = msg("โอนค่าธรรมเนีม ก่อนรับเงินกู้นะคะ")
        assertTrue(r.signals.any { it.title.contains("โอนเงิน") || it.title.contains("ค่าธรรมเนียม") })
    }

    @Test fun investmentScamIsDangerous() {
        val r = msg("ลงทุนน้อย กำไรต่อวัน 10% การันตีผลตอบแทน ถอนได้ทันที สนใจแอดไลน์ด่วน")
        assertEquals(RiskLevel.DANGEROUS, r.level)
    }

    @Test fun remoteAccessIsAlwaysBlocked() {
        val r = msg("เจ้าหน้าที่จะช่วยคืนเงิน ให้ติดตั้ง AnyDesk แล้วส่งรหัสมา")
        assertTrue(r.shouldBlock)
    }

    @Test fun relativeImpersonationIsFlagged() {
        val r = msg("แม่ครับ นี่ผมเอง เปลี่ยนเบอร์ใหม่ มีเรื่องด่วนมาก ช่วยโอนเงินให้ก่อน อย่าเพิ่งบอกพ่อนะ")
        assertTrue("score=${r.score}", r.level != RiskLevel.SAFE)
    }

    @Test fun customBlockWordBlocks() {
        val r = msg("สวัสดี สนใจสมัครสมาชิกโครงการพิเศษไหม", UserRules(customBlockWords = listOf("โครงการพิเศษ")))
        assertTrue(r.shouldBlock)
        assertEquals(RiskLevel.DANGEROUS, r.level)
    }

    // ------------------------------------------------------------- benign messages

    @Test fun familyMessageIsSafe() {
        val r = msg("พรุ่งนี้แม่จะไปหาที่บ้านนะ ทำกับข้าวไว้ด้วย คิดถึงหลาน")
        assertEquals(RiskLevel.SAFE, r.level)
        assertFalse(r.shouldBlock)
        assertNull(r.matchedScamType)
    }

    @Test fun genuineOtpNoticeIsNotFlaggedAsScam() {
        val r = msg("รหัส OTP ของคุณคือ 482913 (Ref: AB12) ใช้ภายใน 5 นาที ห้ามเปิดเผยรหัสนี้แก่ผู้อื่น")
        assertEquals("score=${r.score} ${r.signals}", RiskLevel.SAFE, r.level)
        assertFalse(r.shouldBlock)
    }

    @Test fun doctorAppointmentIsSafe() {
        val r = msg("โรงพยาบาลแจ้งนัดตรวจสุขภาพวันจันทร์ที่ 5 เวลา 9.00 น. กรุณามาก่อนเวลา 15 นาที")
        assertEquals("score=${r.score} ${r.signals}", RiskLevel.SAFE, r.level)
    }

    @Test fun normalBankIncomingNoticeIsSafe() {
        val r = msg("บัญชี xxx-x-x1234-x มีเงินเข้า 1,500.00 บาท วันที่ 12/10 ยอดคงเหลือ 25,300.00 บาท")
        assertEquals("score=${r.score} ${r.signals}", RiskLevel.SAFE, r.level)
    }

    @Test fun wordsInsideOtherWordsDoNotTrigger() {
        // "pin" inside "shopping", "ศาล" inside "ศาลา"
        val r = msg("ไปshoppingที่ห้างกัน แล้วนั่งพักที่ศาลาริมน้ำ")
        assertEquals("score=${r.score} ${r.signals}", RiskLevel.SAFE, r.level)
    }

    @Test fun emailIsNotTreatedAsLink() {
        val r = msg("ส่งเอกสารมาที่ somchai@gmail.com ได้เลยครับ")
        assertTrue(r.extractedUrls.isEmpty())
        assertEquals(RiskLevel.SAFE, r.level)
    }

    // ------------------------------------------------------------- links

    @Test fun brandImpersonationLinkIsBlocked() {
        val r = check(CheckType.LINK, "https://scb-thailand-verify.top/account")
        assertTrue(r.shouldBlock)
        assertTrue(r.signals.any { it.title.contains("scb") })
    }

    @Test fun officialBankLinkIsSafe() {
        val r = check(CheckType.LINK, "https://www.kasikornbank.com/th/personal")
        assertEquals(RiskLevel.SAFE, r.level)
        assertEquals(0, r.score)
    }

    @Test fun shortenedLinkIsSuspicious() {
        val r = check(CheckType.LINK, "bit.ly/3xYz9")
        assertEquals(RiskLevel.SUSPICIOUS, r.level)
    }

    @Test fun apkLinkIsBlocked() {
        val r = check(CheckType.LINK, "ดาวน์โหลดที่ http://refund-gov.online/app/pea.apk")
        assertTrue(r.shouldBlock)
    }

    @Test fun homographLinkIsBlocked() {
        val r = check(CheckType.LINK, "https://kаsikornbank.com/login") // Cyrillic "а"
        assertTrue(r.shouldBlock)
    }

    @Test fun ipAddressLinkIsSuspicious() {
        val r = check(CheckType.LINK, "http://103.45.67.89/bank/login")
        assertTrue(r.level != RiskLevel.SAFE)
    }

    @Test fun linkGluedToThaiTextIsExtracted() {
        val r = msg("รับเงินคืนคลิกbit.ly/abc123นะคะ")
        assertEquals(listOf("bit.ly/abc123"), r.extractedUrls)
    }

    @Test fun linkModeWithoutLinkIsInvalid() {
        assertTrue(analyzer.analyze(CheckType.LINK, "ไม่มีลิงก์เลย 1.500 บาท") is AnalysisOutcome.Invalid)
    }

    // ------------------------------------------------------------- phones

    @Test fun spoofedInternationalPrefixIsBlocked() {
        val r = check(CheckType.PHONE, "+697 1234 5678")
        assertTrue(r.shouldBlock)
        assertEquals(RiskLevel.DANGEROUS, r.level)
    }

    @Test fun foreignNumberIsSuspicious() {
        val r = check(CheckType.PHONE, "+855 12 345 678")
        assertEquals(RiskLevel.SUSPICIOUS, r.level)
    }

    @Test fun ordinaryThaiMobileIsSafe() {
        val r = check(CheckType.PHONE, "081-234-5678")
        assertEquals(RiskLevel.SAFE, r.level)
        assertEquals(listOf("081-234-5678"), r.extractedPhones)
    }

    @Test fun plus66IsTreatedAsThai() {
        val r = check(CheckType.PHONE, "+66 81 234 5678")
        assertEquals(listOf("081-234-5678"), r.extractedPhones)
        assertEquals(RiskLevel.SAFE, r.level)
    }

    @Test fun officialHotlineIsRecognised() {
        val r = check(CheckType.PHONE, "1441")
        assertTrue(r.signals.any { it.title.contains("AOC") })
        assertEquals(RiskLevel.SAFE, r.level)
    }

    @Test fun reportedNumberIsBlocked() {
        val r = check(CheckType.PHONE, "0812345678", UserRules(blockedNumbers = listOf("081-234-5678")))
        assertTrue(r.shouldBlock)
    }

    @Test fun phoneGluedToThaiWordIsExtracted() {
        val r = msg("ติดต่อกลับโทร+6971234567ด่วน")
        assertTrue(r.shouldBlock)
    }

    // ------------------------------------------------------------- transactions

    @Test fun safeAccountTransferIsBlocked() {
        val r = analyzer.analyzeTransaction(
            TransactionInput(80_000.0, knowsRecipientPersonally = false,
                situations = setOf(TransactionSituation.SAFE_ACCOUNT, TransactionSituation.AUTHORITY_CLAIM)),
        )
        assertTrue(r.shouldBlock)
        assertEquals(RiskLevel.DANGEROUS, r.level)
    }

    @Test fun payingKnownPersonSmallAmountIsSafe() {
        val r = analyzer.analyzeTransaction(TransactionInput(500.0, knowsRecipientPersonally = true, situations = emptySet()))
        assertEquals(RiskLevel.SAFE, r.level)
    }

    // ------------------------------------------------------------- robustness / speed

    @Test fun hugeInputIsCappedAndFast() {
        val huge = "ด่วน โอนเงิน ".repeat(20_000)
        val start = System.nanoTime()
        val r = msg(huge)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("took $ms ms", ms < 1_500)
        assertTrue(r.score in 0..100)
    }

    @Test fun pathologicalInputDoesNotHang() {
        val evil = "a.".repeat(3000) + "@" + "1-".repeat(3000)
        val start = System.nanoTime()
        analyzer.analyze(CheckType.MESSAGE, evil)
        analyzer.analyze(CheckType.LINK, evil)
        analyzer.analyze(CheckType.PHONE, evil)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("took $ms ms", ms < 2_000)
    }

    @Test fun emptyInputIsInvalid() {
        assertTrue(analyzer.analyze(CheckType.MESSAGE, "   \n ") is AnalysisOutcome.Invalid)
        assertTrue(analyzer.analyze(CheckType.PHONE, "abc") is AnalysisOutcome.Invalid)
    }

    @Test fun previewMasksAccountNumbers() {
        val r = msg("โอนเข้าบัญชี 123-4-56789-0 ด่วน")
        assertFalse(r.inputPreview.contains("56789"))
        assertTrue(r.inputPreview.contains("9-0") || r.inputPreview.contains("890"))
    }
}
