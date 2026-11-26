package com.scamsense.core.calls

import com.scamsense.core.engine.KnowledgeBase
import com.scamsense.core.engine.PhoneAnalyzer

/**
 * Decides what to do with an incoming call *before it rings*, using only the caller's
 * number. Android only sends numbers that are NOT in the user's contacts to the
 * screening service, so family and friends are never affected.
 */
object CallGuard {

    enum class Action {
        /** Let it ring normally. */
        ALLOW,
        /** Let it ring, but show a warning notification. */
        ALLOW_WITH_WARNING,
        /** Reject silently: known-bad number. */
        BLOCK,
        /** Reject so the carrier forwards it (busy-forwarding) to the AI call assistant. */
        SEND_TO_ASSISTANT,
    }

    data class Policy(
        val blockedNumbers: List<String> = emptyList(),
        val blockInternational: Boolean = false,
        /** True when the AI call assistant is paired and call forwarding was set up. */
        val assistantEnabled: Boolean = false,
        /** The assistant's phone number; calls bridged from it were already screened. */
        val assistantNumber: String? = null,
    )

    data class Decision(val action: Action, val title: String, val detail: String, val display: String)

    fun decide(rawNumber: String?, policy: Policy): Decision {
        val number = rawNumber?.trim().orEmpty()
        if (number.isEmpty() || number.none { it.isDigit() }) {
            return if (policy.assistantEnabled) {
                Decision(Action.SEND_TO_ASSISTANT, "เบอร์ซ่อน", "ส่งให้ผู้ช่วย AI รับสายและตรวจก่อน", "ไม่แสดงเบอร์")
            } else {
                Decision(Action.ALLOW_WITH_WARNING, "สายจากเบอร์ซ่อน",
                    "ระวัง! ถ้าปลายสายขอให้โอนเงิน ขอรหัส OTP หรืออ้างเป็นตำรวจ ให้วางสายทันที", "ไม่แสดงเบอร์")
            }
        }
        val phone = PhoneAnalyzer.classify(number)
        val shown = phone.display

        val assistant = policy.assistantNumber?.takeIf { it.isNotBlank() }?.let { PhoneAnalyzer.classify(it).digits }
        if (assistant != null && phone.digits == assistant) {
            return Decision(Action.ALLOW, "สายที่ผ่านการตรวจจากผู้ช่วย AI แล้ว",
                "ผู้ช่วยคุยกับผู้โทรแล้ว ไม่พบความเสี่ยง จึงโอนสายมาให้คุณ", shown)
        }

        val analysis = PhoneAnalyzer.analyze(phone, policy.blockedNumbers, fromMessage = false)
        if (analysis.block) {
            val s = analysis.signals.maxBy { it.weight }
            return Decision(Action.BLOCK, "บล็อกสายอันตรายให้แล้ว", s.title, shown)
        }
        if (phone.kind == PhoneAnalyzer.Kind.INTERNATIONAL && policy.blockInternational) {
            return Decision(Action.BLOCK, "บล็อกสายจากต่างประเทศให้แล้ว",
                "คุณตั้งค่าไว้ให้บล็อกสายต่างประเทศ (เปิด/ปิดได้ในหน้าป้องกันสายโทร)", shown)
        }
        if (policy.assistantEnabled) {
            return Decision(Action.SEND_TO_ASSISTANT, "ส่งให้ผู้ช่วย AI รับสายก่อน",
                "เบอร์นี้ไม่อยู่ในรายชื่อ ผู้ช่วยจะถามธุระและตรวจว่าเป็นมิจฉาชีพหรือไม่", shown)
        }
        val official = KnowledgeBase.officialNumbers[phone.digits]
        val detail = when {
            official != null -> "เบอร์นี้ตรงกับ $official แต่มิจฉาชีพปลอมเบอร์ได้ ถ้าขอข้อมูลหรือให้โอนเงิน ให้วางสายแล้วโทรกลับเอง"
            phone.kind == PhoneAnalyzer.Kind.INTERNATIONAL -> "สายจากต่างประเทศ แก๊งคอลเซ็นเตอร์มักใช้ ถ้าไม่มีคนรู้จักอยู่ต่างประเทศ ไม่ควรรับ"
            else -> "เบอร์นี้ไม่อยู่ในรายชื่อ ถ้าปลายสายขอเงิน ขอรหัส OTP หรืออ้างเป็นเจ้าหน้าที่ ให้วางสายทันที"
        }
        return Decision(Action.ALLOW_WITH_WARNING, "สายจากเบอร์ที่ไม่รู้จัก", detail, shown)
    }

    /**
     * GSM call-forwarding codes (supported by Thai carriers' networks; the user's own
     * dialer sends them). Busy-forwarding is what makes "reject" hand the call to the AI.
     */
    object ForwardingCodes {
        fun busy(target: String) = "**67*${clean(target)}#"
        fun noAnswer(target: String) = "**61*${clean(target)}#"
        fun unreachable(target: String) = "**62*${clean(target)}#"
        const val CANCEL_BUSY = "##67#"
        const val CANCEL_NO_ANSWER = "##61#"
        const val CANCEL_UNREACHABLE = "##62#"
        private fun clean(n: String) = n.filter { it.isDigit() || it == '+' }
    }
}
