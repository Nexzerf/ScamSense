package com.scamsense.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallAssistantTest {
    private val a = CallAssistant()

    @Test fun scamOnFirstAnswerHangsUp() {
        val (step, t) = a.onTurn(emptyList(), "ผมโทรจากสถานีตำรวจ บัญชีของคุณพัวพันคดีฟอกเงิน ต้องโอนเงินไปบัญชีปลอดภัยเพื่อตรวจสอบ", 0)
        assertTrue(step is CallAssistant.Step.HangUpScam)
        assertEquals(1, t.size)
    }

    @Test fun otpRequestOverTwoTurnsHangsUp() {
        val (s1, t1) = a.onTurn(emptyList(), "สวัสดีครับ ผมเป็นเจ้าหน้าที่ธนาคาร", 0)
        assertTrue(s1 is CallAssistant.Step.Ask)
        val (s2, _) = a.onTurn(t1, "บัญชีของคุณถูกระงับ ขอรหัส OTP ที่ส่งไปด่วนครับ", 0)
        assertTrue(s2 is CallAssistant.Step.HangUpScam)
    }

    @Test fun normalCallerIsConnectedAfterTwoAnswers() {
        val (s1, t1) = a.onTurn(emptyList(), "สวัสดีค่ะ โทรจากคลินิกทันตกรรม เรื่องนัดทำฟันวันพฤหัส", 0)
        assertTrue(s1 is CallAssistant.Step.Ask)
        val (s2, _) = a.onTurn(t1, "ชื่อพยาบาลสมศรีค่ะ คุณยายเป็นคนไข้ประจำของคลินิก", 0)
        assertTrue("was $s2", s2 is CallAssistant.Step.Connect)
    }

    @Test fun silenceTwiceHangsUp() {
        val (s1, _) = a.onTurn(emptyList(), null, 0)
        assertTrue(s1 is CallAssistant.Step.Ask)
        val (s2, _) = a.onTurn(emptyList(), "  ", 1)
        assertEquals(CallAssistant.Step.HangUpSilent, s2)
    }

    @Test fun persistentlySuspiciousCallerOnlyLeavesAMessage() {
        var t = emptyList<String>()
        var step: CallAssistant.Step = CallAssistant.Step.Ask("")
        for (said in listOf("สวัสดีครับ", "มีสินเชื่อ อนุมัติไว แอดไลน์ได้เลยครับ", "ดอกเบี้ยต่ำครับ สนใจไหม")) {
            val r = a.onTurn(t, said, 0); step = r.first; t = r.second
            if (step !is CallAssistant.Step.Ask) break
        }
        assertTrue("was $step", step is CallAssistant.Step.TakeMessage || step is CallAssistant.Step.HangUpScam)
    }

    @Test fun spoofedCallerRejectedBeforeTalking() {
        assertNotNull(a.screenCaller("+6981234567"))
        assertEquals(null, a.screenCaller("+66812345678"))
    }

    @Test fun whisperMasksNumbers() {
        val w = a.whisper(listOf("โอนเข้าบัญชี 1234567890 ครับ"))
        assertTrue(!w.contains("1234567"))
    }
}
