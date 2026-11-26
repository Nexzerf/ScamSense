package com.scamsense.core.engine

/**
 * Entry point of the detection engine. Pure Kotlin (no Android APIs) so it is fast to
 * unit-test and safe to run on a background dispatcher. Thread-safe: all state is
 * immutable after construction.
 *
 * Scoring:
 *  1. Every signal carries a weight; the score is the clamped sum (0..100).
 *  2. Dangerous combinations add a bonus (e.g. asking for an OTP *and* urging haste).
 *  3. Some combinations mean "block" (stop, do not follow the message) and force the
 *     score to at least [BLOCK_FLOOR].
 */
class ScamAnalyzer(
    private val keywordEngine: KeywordEngine = KeywordEngine(KnowledgeBase.rules),
    private val similarityEngine: SimilarityEngine = SimilarityEngine(KnowledgeBase.templates),
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Runs a throw-away analysis so class loading / JIT happens during the loading screen. */
    fun warmUp() {
        analyze(CheckType.MESSAGE, "บัญชีของคุณถูกระงับ กรุณากรอกรหัส OTP ที่ https://example.xyz ด่วน 0812345678")
    }

    fun analyze(type: CheckType, rawInput: String, rules: UserRules = UserRules()): AnalysisOutcome {
        val input = TextNormalizer.sanitizeInput(rawInput)
        val started = System.nanoTime()
        return when (type) {
            CheckType.MESSAGE -> {
                if (TextNormalizer.compact(TextNormalizer.normalize(input)).length < 2) {
                    AnalysisOutcome.Invalid("กรุณาพิมพ์ วาง หรือพูดข้อความที่ต้องการตรวจ")
                } else {
                    AnalysisOutcome.Success(analyzeText(type, input, rules, started))
                }
            }
            CheckType.LINK -> {
                if (UrlAnalyzer.extract(input).isEmpty()) {
                    AnalysisOutcome.Invalid("ไม่พบลิงก์ในข้อความ ลองวางลิงก์ เช่น https://example.com")
                } else {
                    AnalysisOutcome.Success(analyzeText(type, input, rules, started))
                }
            }
            CheckType.PHONE -> {
                val phones = PhoneAnalyzer.extract(input, minDigits = 3)
                if (phones.isEmpty()) {
                    AnalysisOutcome.Invalid("ไม่พบเบอร์โทร กรุณาพิมพ์เบอร์ เช่น 081-234-5678")
                } else {
                    AnalysisOutcome.Success(analyzePhones(input, phones, rules, started))
                }
            }
            CheckType.TRANSACTION ->
                AnalysisOutcome.Invalid("กรุณาใช้หน้าตรวจการโอนเงิน")
        }
    }

    // ------------------------------------------------------------------ text / links

    private fun analyzeText(type: CheckType, input: String, rules: UserRules, started: Long): RiskResult {
        val ctx = TextContext(input)
        val signals = mutableListOf<RiskSignal>()
        var block = false

        // 1) Keyword rules
        val hits = keywordEngine.match(ctx)
        val hitIds = hits.map { it.rule.id }.toMutableSet()
        val otpNotice = isGenuineOtpNotice(ctx)
        for (hit in hits) {
            // A real bank OTP SMS mentions "OTP" but tells you NOT to share it.
            if (otpNotice && hit.rule.id == KnowledgeBase.CREDENTIALS_RULE) {
                hitIds.remove(hit.rule.id)
                continue
            }
            signals += RiskSignal(hit.rule.category, hit.rule.title, hit.rule.detail, hit.rule.weight, "พบคำว่า \"${hit.matchedTerm}\"")
        }
        if (otpNotice) {
            signals += RiskSignal(
                SignalCategory.INFO, "ดูเหมือนข้อความแจ้งรหัส OTP จากระบบ",
                "ข้อความลักษณะนี้มักมาจากธนาคารจริง แต่ห้ามบอกรหัสนี้กับใครเด็ดขาด แม้จะอ้างว่าเป็นเจ้าหน้าที่",
                0,
            )
        }

        // 2) User-defined block words
        val custom = keywordEngine.matchCustom(ctx, rules.customBlockWords)
        if (custom.isNotEmpty()) {
            signals += RiskSignal(
                SignalCategory.CUSTOM, "พบคำที่คุณตั้งให้บล็อก",
                "ข้อความนี้มีคำที่คุณหรือผู้ดูแลตั้งไว้ว่าอันตราย", 45,
                custom.joinToString(", ") { "\"$it\"" },
            )
            block = true
        }

        // 3) Similarity to known scam scripts
        val match = similarityEngine.bestMatch(ctx.compact)
        var scamType: String? = null
        if (match != null) {
            val pct = (match.similarity * 100).toInt()
            val weight = when {
                match.similarity >= SIM_HIGH -> 35
                match.similarity >= SIM_MEDIUM -> 22
                match.similarity >= SIM_LOW -> 10
                else -> 0
            }
            if (weight > 0) {
                scamType = match.template.scamType
                signals += RiskSignal(
                    SignalCategory.SIMILAR_SCAM,
                    "ข้อความคล้ายกลโกง \"${match.template.scamType}\"",
                    "เนื้อหาคล้ายข้อความหลอกลวงที่เคยพบ ประมาณ $pct% แม้คำจะไม่ตรงกันทุกคำ",
                    weight,
                )
            }
        }

        // 4) Links found in the text
        val urls = UrlAnalyzer.extract(input)
        for (u in urls) {
            val a = UrlAnalyzer.analyze(u)
            signals += a.signals
            block = block || a.block
        }

        // 5) Phone numbers found in the text (only strong warnings)
        val phones = PhoneAnalyzer.extract(input, minDigits = 9)
        for (p in phones) {
            val a = PhoneAnalyzer.analyze(p, rules.blockedNumbers, fromMessage = true)
            signals += a.signals
            block = block || a.block
        }

        // 6) Combinations
        val hasCredentials = KnowledgeBase.CREDENTIALS_RULE in hitIds
        if (hasCredentials && (hitIds.any { it in KnowledgeBase.blockPartners } || urls.isNotEmpty())) {
            signals += RiskSignal(SignalCategory.SENSITIVE_INFO, "ขอรหัสลับร่วมกับการเร่งหรือส่งลิงก์",
                "นี่คือรูปแบบหลอกขโมยบัญชีที่ชัดเจน ห้ามกรอกหรือบอกรหัสใด ๆ", 20)
            block = true
        }
        if (hitIds.any { it in KnowledgeBase.alwaysBlockRules }) block = true
        if ("threat_legal" in hitIds && "authority" in hitIds) {
            signals += RiskSignal(SignalCategory.THREAT, "อ้างเจ้าหน้าที่ร่วมกับการข่มขู่",
                "รูปแบบเดียวกับแก๊งคอลเซ็นเตอร์ ให้วางสายและโทร 1441 เพื่อขอคำปรึกษา", 15)
        }
        if ("identity" in hitIds && (hitIds.any { it in KnowledgeBase.blockPartners } || urls.isNotEmpty())) {
            signals += RiskSignal(SignalCategory.SENSITIVE_INFO, "ขอข้อมูลส่วนตัวพร้อมเร่งรัดหรือส่งลิงก์",
                "อย่าส่งข้อมูลบัตรประชาชนหรือเลขบัญชีให้ใครทางข้อความ", 10)
        }
        val moneyRules = setOf("money_transfer", "prize_refund", "loan", "investment", "job_scam")
        if (hitIds.any { it in moneyRules } && ("urgency" in hitIds || "action_link" in hitIds || "secrecy" in hitIds)) {
            signals += RiskSignal(SignalCategory.MONEY, "เรื่องเงินร่วมกับการเร่งหรือให้ติดต่อช่องทางอื่น",
                "การเร่งให้จ่ายเงินหรือย้ายไปคุยในไลน์ เป็นขั้นตอนหลักของกลโกงออนไลน์", 10)
        }
        if (scamType == null) scamType = hits.maxByOrNull { it.rule.weight }?.takeIf { it.rule.weight >= 20 }?.rule?.title

        return buildResult(type, input, signals, block, scamType, urls.map { it.raw }, phones.map { it.display }, started)
    }

    private fun isGenuineOtpNotice(ctx: TextContext): Boolean {
        val mentionsCode = "otp" in ctx.latinTokens || ctx.compact.contains("รหัส")
        val tellsNotToShare = KnowledgeBase.otpNoticePhrases.any { ctx.compact.contains(it) }
        val hasCode = ctx.latinTokens.any { t -> t.length in 4..8 && t.all { it.isDigit() } }
        return mentionsCode && tellsNotToShare && hasCode && UrlAnalyzer.extract(ctx.original).isEmpty()
    }

    // ------------------------------------------------------------------ phones

    private fun analyzePhones(input: String, phones: List<PhoneAnalyzer.ParsedPhone>, rules: UserRules, started: Long): RiskResult {
        val signals = mutableListOf<RiskSignal>()
        var block = false
        for (p in phones.take(3)) {
            val a = PhoneAnalyzer.analyze(p, rules.blockedNumbers, fromMessage = false)
            signals += a.signals
            block = block || a.block
        }
        return buildResult(CheckType.PHONE, input, signals, block, null, emptyList(), phones.map { it.display }, started)
    }

    // ------------------------------------------------------------------ transactions

    fun analyzeTransaction(input: TransactionInput, rules: UserRules = UserRules()): RiskResult {
        val started = System.nanoTime()
        val signals = mutableListOf<RiskSignal>()
        var block = false
        for (s in input.situations) {
            signals += RiskSignal(SignalCategory.TRANSACTION, s.label, adviceFor(s), s.weight)
            block = block || s.block
        }
        if (!input.knowsRecipientPersonally) {
            signals += RiskSignal(SignalCategory.TRANSACTION, "ไม่รู้จักผู้รับเงินเป็นการส่วนตัว",
                "เงินที่โอนให้คนแปลกหน้า มักตามคืนไม่ได้", 15)
        }
        val amount = input.amount
        if (amount != null && amount >= 50_000) {
            signals += RiskSignal(SignalCategory.TRANSACTION, "จำนวนเงินสูงมาก",
                "ยอด ${formatBaht(amount)} บาท ควรปรึกษาลูกหลานหรือธนาคารก่อนโอนทุกครั้ง", 15)
        } else if (amount != null && amount >= 10_000) {
            signals += RiskSignal(SignalCategory.TRANSACTION, "จำนวนเงินค่อนข้างสูง",
                "ยอด ${formatBaht(amount)} บาท ควรตรวจสอบให้แน่ใจก่อนโอน", 8)
        }
        val note = TextNormalizer.sanitizeInput(input.note)
        var scamType: String? = null
        if (note.isNotBlank()) {
            val noteResult = analyzeText(CheckType.MESSAGE, note, rules, started)
            signals += noteResult.signals.filter { it.weight != 0 || it.category != SignalCategory.INFO }
            block = block || noteResult.shouldBlock
            scamType = noteResult.matchedScamType
        }
        if (scamType == null) scamType = input.situations.maxByOrNull { it.weight }?.takeIf { it.weight >= 30 }?.label
        return buildResult(CheckType.TRANSACTION, note, signals, block, scamType, emptyList(), emptyList(), started)
    }

    private fun adviceFor(s: TransactionSituation): String = when (s) {
        TransactionSituation.SAFE_ACCOUNT -> "ไม่มีหน่วยงานใดให้โอนเงินไปตรวจสอบ นี่คือกลโกงแน่นอน"
        TransactionSituation.AUTHORITY_CLAIM -> "เจ้าหน้าที่จริงไม่ขอให้โอนเงิน ให้วางสายแล้วโทรหาหน่วยงานนั้นเอง"
        TransactionSituation.CALL_INSTRUCTED -> "คนที่โทรมาสั่งให้โอนเงิน มักเป็นมิจฉาชีพ"
        TransactionSituation.FEE_FIRST -> "เงินกู้หรือรางวัลจริง ไม่ต้องจ่ายเงินก่อน"
        TransactionSituation.HIGH_RETURN -> "การลงทุนที่การันตีกำไรสูง มักเป็นแชร์ลูกโซ่"
        TransactionSituation.ONLINE_ONLY -> "มิจฉาชีพมักสร้างความสนิทใจทางออนไลน์ก่อนขอเงิน"
        TransactionSituation.SECRECY -> "การห้ามบอกใคร คือการตัดคุณจากคนที่จะช่วยเตือน"
        TransactionSituation.URGENT -> "ยิ่งถูกเร่ง ยิ่งต้องหยุดคิดและถามคนใกล้ตัวก่อน"
        TransactionSituation.NAME_MISMATCH -> "บริษัทจริงรับเงินผ่านบัญชีชื่อบริษัท ไม่ใช่บัญชีบุคคล"
        TransactionSituation.TOO_GOOD_DEAL -> "ของถูกเกินจริงและไม่รับเก็บเงินปลายทาง มักเป็นร้านปลอม"
        TransactionSituation.REMOTE_APP -> "แอปที่ให้ควบคุมเครื่อง ทำให้มิจฉาชีพโอนเงินออกเองได้"
    }

    // ------------------------------------------------------------------ result

    private fun buildResult(
        type: CheckType,
        input: String,
        rawSignals: List<RiskSignal>,
        block: Boolean,
        scamType: String?,
        urls: List<String>,
        phones: List<String>,
        started: Long,
    ): RiskResult {
        // Same title twice (e.g. two links both "http") -> keep the strongest one.
        val signals = rawSignals
            .groupBy { it.title }
            .map { (_, group) -> group.maxBy { it.weight } }
            .sortedByDescending { it.weight }
            .toMutableList()

        var score = signals.sumOf { it.weight }.coerceIn(0, 100)
        if (block) score = maxOf(score, BLOCK_FLOOR)
        val level = levelFor(score)

        if (signals.none { it.weight > 0 || it.category == SignalCategory.INFO }) {
            signals += RiskSignal(SignalCategory.INFO, "ไม่พบคำหรือรูปแบบที่เป็นอันตราย",
                "ระบบตรวจคำ ลิงก์ เบอร์โทร และความคล้ายกับกลโกงที่รู้จักแล้ว ไม่พบสิ่งผิดปกติ", 0)
        }

        val summary = when {
            block -> "หยุดก่อน! นี่คือรูปแบบของมิจฉาชีพ อย่าทำตาม อย่าโอนเงิน และอย่าให้ข้อมูลใด ๆ"
            level == RiskLevel.DANGEROUS -> "อันตราย มีหลายสัญญาณของมิจฉาชีพ อย่าทำตามข้อความนี้"
            level == RiskLevel.SUSPICIOUS -> "น่าสงสัย ควรตรวจสอบก่อน ถามลูกหลานหรือโทรถามหน่วยงานโดยตรง"
            else -> "ยังไม่พบสัญญาณอันตราย แต่ถ้ามีคนขอเงินหรือรหัส ให้ระวังไว้เสมอ"
        }

        val elapsed = (System.nanoTime() - started) / 1_000_000
        return RiskResult(
            type = type,
            score = score,
            level = level,
            shouldBlock = block,
            summary = summary,
            signals = signals,
            advice = buildAdvice(type, level, block, signals),
            matchedScamType = if (level == RiskLevel.SAFE) null else scamType,
            inputPreview = TextNormalizer.preview(input),
            extractedUrls = urls,
            extractedPhones = phones,
            analyzedAt = clock(),
            elapsedMs = elapsed,
        )
    }

    private fun buildAdvice(type: CheckType, level: RiskLevel, block: Boolean, signals: List<RiskSignal>): List<String> {
        val cats = signals.filter { it.weight > 0 }.map { it.category }.toSet()
        val advice = LinkedHashSet<String>()
        if (block || level == RiskLevel.DANGEROUS) {
            advice += "อย่าตอบกลับ อย่ากดลิงก์ และอย่าโทรกลับเบอร์ในข้อความ"
        }
        if (SignalCategory.SENSITIVE_INFO in cats) advice += "ห้ามบอกรหัส OTP รหัสผ่าน หรือเลขบัตรกับใครเด็ดขาด"
        if (SignalCategory.MONEY in cats || type == CheckType.TRANSACTION && level != RiskLevel.SAFE) {
            advice += "ยังไม่ต้องโอนเงิน ให้ปรึกษาลูกหลานหรือคนที่ไว้ใจก่อน"
        }
        if (SignalCategory.IMPERSONATION in cats || SignalCategory.THREAT in cats) {
            advice += "ถ้าอ้างเป็นหน่วยงาน ให้วางสาย แล้วโทรหาหน่วยงานนั้นจากเบอร์ทางการด้วยตัวเอง"
        }
        if (SignalCategory.REMOTE_ACCESS in cats || SignalCategory.LINK in cats && block) {
            advice += "ห้ามติดตั้งแอปหรือให้สิทธิ์ควบคุมเครื่องตามที่ถูกบอก"
        }
        if (type == CheckType.PHONE && level != RiskLevel.SAFE) advice += "บล็อกเบอร์นี้ และไม่ต้องรับสายอีก"
        if (level != RiskLevel.SAFE) advice += "ถ้าโอนเงินไปแล้ว รีบโทรธนาคารของคุณ และสายด่วน 1441 ทันที"
        if (level == RiskLevel.SAFE) advice += "ถ้าไม่แน่ใจ ส่งให้ลูกหลานช่วยดู หรือโทรสายด่วน 1441 ได้ตลอด 24 ชั่วโมง"
        return advice.toList()
    }

    companion object {
        const val SUSPICIOUS_THRESHOLD = 30
        const val DANGEROUS_THRESHOLD = 60
        const val BLOCK_FLOOR = 85
        const val SIM_HIGH = 0.45
        const val SIM_MEDIUM = 0.32
        const val SIM_LOW = 0.22

        fun levelFor(score: Int): RiskLevel = when {
            score >= DANGEROUS_THRESHOLD -> RiskLevel.DANGEROUS
            score >= SUSPICIOUS_THRESHOLD -> RiskLevel.SUSPICIOUS
            else -> RiskLevel.SAFE
        }

        fun formatBaht(amount: Double): String = String.format(java.util.Locale.US, "%,.0f", amount)
    }
}
