package com.scamsense.core.engine

import java.text.Normalizer

/**
 * Extracts links from free text and scores each one with offline heuristics.
 * Links are NEVER opened or fetched: checking must not expose the user or tip off
 * the scammer that the link was clicked.
 */
object UrlAnalyzer {

    data class ParsedUrl(
        val raw: String,
        val scheme: String?,
        val host: String,
        val path: String,
        val hasUserInfo: Boolean,
    )

    data class Analysis(val url: ParsedUrl, val signals: List<RiskSignal>, val block: Boolean)

    private const val MAX_URLS = 10

    private fun isZeroWidthJoiner(c: Char): Boolean = c.code in 0x200B..0x200D || c.code == 0xFEFF

    /**
     * Finds URL-like tokens. Tokens are maximal runs of non-space, non-Thai characters,
     * so a link glued to Thai text ("คลิกbit.ly/abcนะ") is still found, and look-alike
     * non-Latin letters stay inside the token (so homograph hosts can be detected).
     * Linear time; no regex backtracking.
     */
    fun extract(text: String): List<ParsedUrl> {
        val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC)
        val out = LinkedHashMap<String, ParsedUrl>()
        val sb = StringBuilder()
        fun flush() {
            if (sb.isNotEmpty()) {
                val token = sb.toString().trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '"', '\'', '>', '…')
                    .trimStart('(', '[', '{', '"', '\'', '<')
                parse(token)?.let { val key = it.host + it.path; if (key !in out) out[key] = it }
                sb.setLength(0)
            }
        }
        for (c in nfkc) {
            if (c.isWhitespace() || TextNormalizer.isThai(c) || isZeroWidthJoiner(c)) flush() else sb.append(c)
            if (out.size >= MAX_URLS) break
        }
        flush()
        return out.values.take(MAX_URLS)
    }

    /** Parses a candidate token; returns null when it does not look like a link. */
    fun parse(token: String): ParsedUrl? {
        if (token.length < 4 || token.length > 2_048 || '.' !in token) return null
        var rest = token
        var scheme: String? = null
        val schemeEnd = rest.indexOf("://")
        if (schemeEnd in 1..10) {
            scheme = rest.substring(0, schemeEnd).lowercase()
            if (scheme != "http" && scheme != "https") return null
            rest = rest.substring(schemeEnd + 3)
        }
        val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) rest.length else it }
        var authority = rest.substring(0, authorityEnd)
        val path = rest.substring(authorityEnd)
        val hasUserInfo = '@' in authority
        // "name@example.com" without a scheme is an e-mail address, not a link.
        if (hasUserInfo && scheme == null) return null
        if (hasUserInfo) authority = authority.substringAfterLast('@')
        // Strip port (but keep IPv6 literals intact).
        val host = if (authority.startsWith("[")) {
            authority.substringBefore(']') + "]"
        } else {
            authority.substringBefore(':')
        }.lowercase().trimEnd('.')

        if (host.isEmpty() || host.startsWith('.') || ".." in host) return null
        val isIp = isIpv4(host) || host.startsWith("[")
        if (!isIp) {
            val tld = host.substringAfterLast('.', "")
            val looksLikeDomain = tld.length >= 2 && tld.all { it.isLetter() } && host.contains('.')
            if (!looksLikeDomain) return null
            // Without an explicit scheme or "www.", require a known TLD so "1.500" or "ok.แล้ว" are ignored.
            if (scheme == null && !host.startsWith("www.") && tld !in KnowledgeBase.knownTlds && !tld.startsWith("xn--")) {
                return null
            }
        }
        return ParsedUrl(token, scheme, host, path, hasUserInfo)
    }

    fun analyze(url: ParsedUrl): Analysis {
        val signals = mutableListOf<RiskSignal>()
        var block = false
        val host = url.host
        val display = shorten(url.raw)

        if (url.hasUserInfo) {
            signals += RiskSignal(SignalCategory.LINK, "ลิงก์มีเครื่องหมาย @ หลอกตา",
                "ส่วนหน้า @ เป็นแค่ข้อความหลอกตา เว็บที่จะเปิดจริงคือ $host", 35, display)
        }
        if (isIpv4(host) || host.startsWith("[")) {
            signals += RiskSignal(SignalCategory.LINK, "ลิงก์เป็นตัวเลข ไม่มีชื่อเว็บ",
                "เว็บของธนาคารหรือหน่วยงานจริงจะมีชื่อเว็บชัดเจน ไม่ใช่ตัวเลข", 30, display)
        }
        val nonAscii = host.any { it.code > 127 }
        if (nonAscii || host.split('.').any { it.startsWith("xn--") }) {
            signals += RiskSignal(SignalCategory.LINK, "ชื่อเว็บใช้ตัวอักษรหน้าตาคล้ายกัน",
                "ชื่อเว็บนี้ใช้ตัวอักษรพิเศษที่ดูเหมือนตัวอังกฤษ เพื่อปลอมเป็นเว็บจริง", 40, display)
            block = true
        }
        val registrable = registrableDomain(host)
        if (registrable in KnowledgeBase.urlShorteners || host in KnowledgeBase.urlShorteners) {
            signals += RiskSignal(SignalCategory.LINK, "ลิงก์ย่อ ซ่อนปลายทางจริง",
                "ลิงก์ย่อทำให้มองไม่เห็นว่าจะพาไปเว็บไหน มิจฉาชีพนิยมใช้มาก", 30, display)
        }
        val tld = host.substringAfterLast('.')
        if (tld in KnowledgeBase.suspiciousTlds) {
            signals += RiskSignal(SignalCategory.LINK, "นามสกุลเว็บ .$tld พบบ่อยในเว็บหลอกลวง",
                "เว็บแบบนี้จดชื่อได้ถูกและง่าย หน่วยงานจริงในไทยมักใช้ .co.th .go.th หรือ .com", 15, display)
        }
        if (url.scheme == "http") {
            signals += RiskSignal(SignalCategory.LINK, "ลิงก์ไม่เข้ารหัส (http)",
                "ข้อมูลที่กรอกในเว็บนี้อาจถูกดักอ่านได้ เว็บธนาคารจริงใช้ https เสมอ", 10, display)
        }
        if (url.path.lowercase().substringBefore('?').endsWith(".apk")) {
            signals += RiskSignal(SignalCategory.LINK, "ลิงก์ดาวน์โหลดแอปนอก Play Store (.apk)",
                "ไฟล์ .apk ที่ส่งมาทางข้อความมักเป็นแอปดูดเงิน ห้ามติดตั้งเด็ดขาด", 50, display)
            block = true
        }
        val impersonated = impersonatedBrand(host)
        if (impersonated != null) {
            signals += RiskSignal(SignalCategory.LINK, "ชื่อเว็บปลอมเป็น \"$impersonated\"",
                "ชื่อเว็บมีคำว่า $impersonated แต่ไม่ใช่เว็บทางการ เป็นเทคนิคเว็บปลอมที่พบบ่อย", 45, display)
            block = true
        }
        if (host.count { it == '.' } >= 4) {
            signals += RiskSignal(SignalCategory.LINK, "ชื่อเว็บยาวซ้อนกันหลายชั้น",
                "การซ้อนชื่อเว็บหลายชั้นใช้เพื่อหลอกให้ดูเหมือนเว็บจริง", 10, display)
        }
        if (host.count { it.isDigit() } >= 4 && !isIpv4(host)) {
            signals += RiskSignal(SignalCategory.LINK, "ชื่อเว็บมีตัวเลขปนจำนวนมาก",
                "เว็บทางการไม่ค่อยใช้ตัวเลขจำนวนมากในชื่อ", 5, display)
        }
        if (signals.isEmpty()) {
            when {
                registrable == "line.me" -> signals += RiskSignal(SignalCategory.LINK, "ลิงก์ชวนเพิ่มเพื่อนใน LINE",
                    "ใครก็สร้างบัญชี LINE ได้ มิจฉาชีพมักชวนแอดไลน์เพื่อคุยต่อแล้วหลอกโอนเงิน", 12, display)
                isOfficial(host) -> signals += RiskSignal(SignalCategory.INFO, "เป็นเว็บทางการที่รู้จัก",
                    "ชื่อเว็บตรงกับเว็บทางการ แต่ก็ยังควรระวังถ้ามีคนส่งมาให้กรอกรหัส OTP", -15, display)
                host.endsWith(".go.th") || host.endsWith(".ac.th") || host.endsWith(".or.th") ->
                    signals += RiskSignal(SignalCategory.INFO, "เป็นเว็บของหน่วยงานในไทย",
                        "ชื่อเว็บ .go.th .or.th .ac.th จดได้เฉพาะหน่วยงานที่ผ่านการตรวจสอบ", -10, display)
                else -> signals += RiskSignal(SignalCategory.LINK, "ลิงก์ที่ไม่รู้จัก",
                    "ระบบไม่พบสัญญาณอันตรายชัดเจน แต่ถ้าไม่แน่ใจ อย่ากรอกข้อมูลหรือรหัสใด ๆ", 8, display)
            }
        }
        return Analysis(url, signals, block)
    }

    fun isIpv4(host: String): Boolean {
        val parts = host.split('.')
        return parts.size == 4 && parts.all { p -> p.isNotEmpty() && p.length <= 3 && p.all { it.isDigit() } && p.toInt() <= 255 }
    }

    /** eTLD+1 approximation tuned for Thai second-level domains (co.th, go.th …). */
    fun registrableDomain(host: String): String {
        val labels = host.split('.')
        if (labels.size <= 2) return host
        val secondLevel = setOf("co", "or", "go", "ac", "in", "net", "mi", "com", "org", "gov", "edu")
        val last = labels.last()
        return if (last.length == 2 && labels[labels.size - 2] in secondLevel) {
            labels.takeLast(3).joinToString(".")
        } else {
            labels.takeLast(2).joinToString(".")
        }
    }

    private fun isOfficial(host: String): Boolean {
        val reg = registrableDomain(host)
        return KnowledgeBase.brandDomains.values.any { reg in it }
    }

    /** Returns the brand a host pretends to be, or null if none / it is the official site. */
    fun impersonatedBrand(host: String): String? {
        val registrable = registrableDomain(host)
        val tokens = host.split('.', '-', '_').filter { it.isNotEmpty() }
        if (KnowledgeBase.brandDomains.values.any { registrable in it }) return null
        for (brand in KnowledgeBase.brandDomains.keys) {
            val hit = tokens.any { t ->
                if (brand.length < 5) t == brand else t == brand || t.startsWith(brand) || t.endsWith(brand)
            }
            if (hit) return brand
        }
        return null
    }

    private fun shorten(s: String) = if (s.length <= 60) s else s.take(57) + "…"
}
