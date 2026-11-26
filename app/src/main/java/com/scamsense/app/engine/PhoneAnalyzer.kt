package com.scamsense.app.engine

/**
 * Classifies Thai and international phone numbers.
 *
 * We intentionally ship no "known scam numbers" list: such lists go stale fast and can
 * wrongly label a real person's number. Instead we use structural signals plus the
 * numbers the user has reported on this device.
 */
object PhoneAnalyzer {

    enum class Kind { THAI_MOBILE, THAI_LANDLINE, SHORT_CODE, PREMIUM, INTERNATIONAL, UNUSUAL }

    data class ParsedPhone(val display: String, val digits: String, val kind: Kind)

    data class Analysis(val phone: ParsedPhone, val signals: List<RiskSignal>, val block: Boolean)

    private const val MAX_PHONES = 10

    /**
     * Pulls phone-number candidates from free text: runs of digits that may contain
     * spaces, dashes, dots or brackets, optionally starting with "+".
     * [minDigits] is 9 for messages (to skip amounts like 100000) and 3 when the user
     * typed a number on purpose.
     */
    fun extract(text: String, minDigits: Int = 9): List<ParsedPhone> {
        val normalized = TextNormalizer.normalize(text)
        val out = LinkedHashMap<String, ParsedPhone>()
        var i = 0
        while (i < normalized.length && out.size < MAX_PHONES) {
            val c = normalized[i]
            val startsNumber = c.isDigit() || (c == '+' && i + 1 < normalized.length && normalized[i + 1].isDigit())
            // Skip digits glued to Latin letters/digits ("abc123", "x0812…"); Thai words may precede
            // a number directly ("โทร0812345678"), so they do not block extraction.
            val prev = if (i > 0) normalized[i - 1] else ' '
            if (!startsNumber || prev.isDigit() || prev in 'a'..'z') {
                i++
                continue
            }
            var j = i
            val sb = StringBuilder()
            while (j < normalized.length) {
                val ch = normalized[j]
                if (ch.isDigit() || (ch == '+' && j == i)) sb.append(ch)
                else if (ch == ' ' || ch == '-' || ch == '.' || ch == '(' || ch == ')') {
                    // A separator only continues the number if a digit follows soon.
                    if (j + 1 >= normalized.length || !(normalized[j + 1].isDigit() || normalized[j + 1] in " -().")) break
                } else break
                j++
            }
            val raw = sb.toString()
            val digitCount = raw.count { it.isDigit() }
            if (digitCount in minDigits..15) {
                val parsed = classify(raw)
                if (parsed.digits !in out) out[parsed.digits] = parsed
            }
            i = maxOf(j, i + 1)
        }
        return out.values.toList()
    }

    fun classify(raw: String): ParsedPhone {
        val plus = raw.startsWith("+")
        var digits = raw.filter { it.isDigit() }
        var international = plus
        if (!plus && digits.startsWith("00")) {
            digits = digits.removePrefix("00")
            international = true
        }
        if (international && digits.startsWith("66")) {
            digits = "0" + digits.removePrefix("66")
            international = false
        }
        val kind = when {
            international -> Kind.INTERNATIONAL
            digits.length == 10 && digits.startsWith("1900") -> Kind.PREMIUM
            digits.length in 3..5 && digits.startsWith("1") -> Kind.SHORT_CODE
            digits.length == 10 && digits[0] == '0' && digits[1] in "689" -> Kind.THAI_MOBILE
            digits.length == 9 && digits[0] == '0' && digits[1] in "234567" -> Kind.THAI_LANDLINE
            else -> Kind.UNUSUAL
        }
        val canonical = if (international) "+$digits" else digits
        return ParsedPhone(display = formatForDisplay(canonical, kind), digits = canonical, kind = kind)
    }

    /**
     * @param fromMessage true when the number was found inside a message; then we only
     * raise clear warnings and stay quiet about ordinary numbers (which could be the
     * user's own bank account or a delivery driver).
     */
    fun analyze(phone: ParsedPhone, blockedNumbers: Collection<String>, fromMessage: Boolean): Analysis {
        val signals = mutableListOf<RiskSignal>()
        var block = false
        val d = phone.digits
        val shown = phone.display

        val blockedCanon = blockedNumbers.map { classify(it).digits }.toSet()
        if (d in blockedCanon) {
            signals += RiskSignal(SignalCategory.PHONE, "เบอร์นี้คุณเคยรายงานว่าเป็นมิจฉาชีพ",
                "เบอร์นี้อยู่ในรายการบล็อกของคุณ ไม่ควรรับสายหรือโทรกลับ", 60, shown)
            block = true
        }
        val official = KnowledgeBase.officialNumbers[d]
        when (phone.kind) {
            Kind.INTERNATIONAL -> {
                val body = d.removePrefix("+")
                if (KnowledgeBase.spoofedPrefixes.any { body.startsWith(it) }) {
                    signals += RiskSignal(SignalCategory.PHONE, "เบอร์ขึ้นต้น +${body.take(3)} เป็นเบอร์ปลอม",
                        "รหัสประเทศนี้ไม่มีอยู่จริง เป็นเบอร์ที่แก๊งคอลเซ็นเตอร์ปลอมขึ้น ห้ามรับสายหรือโทรกลับ", 60, shown)
                    block = true
                } else {
                    signals += RiskSignal(SignalCategory.PHONE, "เบอร์โทรจากต่างประเทศ",
                        "แก๊งคอลเซ็นเตอร์มักโทรจากต่างประเทศ ถ้าไม่มีคนรู้จักอยู่ต่างประเทศ ไม่ควรรับสาย", 30, shown)
                }
            }
            Kind.PREMIUM -> signals += RiskSignal(SignalCategory.PHONE, "เบอร์บริการเสียเงิน (1900)",
                "โทรเข้าเบอร์นี้คิดค่าบริการสูงกว่าปกติ", 20, shown)
            Kind.UNUSUAL -> if (!fromMessage) {
                signals += RiskSignal(SignalCategory.PHONE, "รูปแบบเบอร์ไม่ปกติ",
                    "เบอร์นี้ไม่ตรงกับรูปแบบเบอร์ในไทย อาจเป็นเบอร์ที่ปลอมขึ้นหรือพิมพ์ผิด", 20, shown)
            }
            Kind.SHORT_CODE, Kind.THAI_MOBILE, Kind.THAI_LANDLINE -> if (!fromMessage && official == null && !block) {
                signals += RiskSignal(SignalCategory.INFO, "ไม่พบเบอร์นี้ในรายการอันตราย",
                    "เบอร์ในไทยรูปแบบปกติ แต่ถ้าไม่รู้จักผู้โทร อย่าให้ข้อมูลส่วนตัวหรือโอนเงิน", 10, shown)
            }
        }
        if (official != null && !fromMessage) {
            signals += RiskSignal(SignalCategory.INFO, "ตรงกับเบอร์ทางการของ $official",
                "แต่มิจฉาชีพสามารถปลอมเบอร์ที่โชว์ได้ ถ้ามีคนโทรมาขอข้อมูล ให้วางสายแล้วโทรกลับเบอร์นี้ด้วยตัวเอง", 0, shown)
        }
        return Analysis(phone, signals, block)
    }

    private fun formatForDisplay(canonical: String, kind: Kind): String = when (kind) {
        Kind.THAI_MOBILE -> "${canonical.substring(0, 3)}-${canonical.substring(3, 6)}-${canonical.substring(6)}"
        Kind.THAI_LANDLINE -> if (canonical.startsWith("02")) {
            "02-${canonical.substring(2, 5)}-${canonical.substring(5)}"
        } else {
            "${canonical.substring(0, 3)}-${canonical.substring(3, 6)}-${canonical.substring(6)}"
        }
        else -> canonical
    }
}
