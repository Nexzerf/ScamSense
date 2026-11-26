package com.scamsense.core.engine

/** What the user asked us to check. */
enum class CheckType { MESSAGE, LINK, PHONE, TRANSACTION }

enum class RiskLevel { SAFE, SUSPICIOUS, DANGEROUS }

enum class SignalCategory {
    SENSITIVE_INFO,
    URGENCY,
    IMPERSONATION,
    MONEY,
    THREAT,
    ACTION,
    REMOTE_ACCESS,
    SECRECY,
    SIMILAR_SCAM,
    LINK,
    PHONE,
    TRANSACTION,
    CUSTOM,
    INFO,
}

/**
 * One reason that contributed to the score.
 * [weight] may be negative for trust signals (e.g. an official government domain).
 */
data class RiskSignal(
    val category: SignalCategory,
    val title: String,
    val detail: String,
    val weight: Int,
    val evidence: String? = null,
)

data class RiskResult(
    val type: CheckType,
    val score: Int,
    val level: RiskLevel,
    val shouldBlock: Boolean,
    val summary: String,
    val signals: List<RiskSignal>,
    val advice: List<String>,
    val matchedScamType: String?,
    val inputPreview: String,
    val extractedUrls: List<String>,
    val extractedPhones: List<String>,
    val analyzedAt: Long,
    val elapsedMs: Long,
)

/** User-defined rules: extra block words and numbers the user has reported. */
data class UserRules(
    val customBlockWords: List<String> = emptyList(),
    val blockedNumbers: List<String> = emptyList(),
)

/** Answers from the "check a money transfer" questionnaire. */
data class TransactionInput(
    val amount: Double?,
    val knowsRecipientPersonally: Boolean,
    val situations: Set<TransactionSituation>,
    val note: String = "",
)

enum class TransactionSituation(val label: String, val weight: Int, val block: Boolean = false) {
    SAFE_ACCOUNT("ถูกบอกให้โอนเงินไป \"บัญชีปลอดภัย\" หรือเพื่อ \"ตรวจสอบเงิน\"", 50, block = true),
    AUTHORITY_CLAIM("ผู้ติดต่ออ้างเป็นตำรวจ เจ้าหน้าที่รัฐ หรือธนาคาร", 35),
    CALL_INSTRUCTED("มีคนโทรหรือแชทมาบอกให้โอน", 30),
    FEE_FIRST("ต้องจ่ายค่าธรรมเนียมก่อน จึงจะได้เงินกู้หรือรางวัล", 40),
    HIGH_RETURN("ชวนลงทุน ได้ผลตอบแทนสูงหรือการันตีกำไร", 35),
    ONLINE_ONLY("ผู้รับเป็นคนรู้จักทางออนไลน์ ยังไม่เคยเจอตัวจริง", 30),
    SECRECY("ถูกบอกว่าห้ามบอกใคร หรือห้ามวางสาย", 35),
    URGENT("ถูกเร่งให้โอนด่วน ไม่มีเวลาคิด", 20),
    NAME_MISMATCH("ชื่อบัญชีผู้รับไม่ตรงกับบริษัทหรือคนที่อ้าง", 30),
    TOO_GOOD_DEAL("ซื้อของออนไลน์ราคาถูกผิดปกติ ให้โอนก่อนส่งของ", 20),
    REMOTE_APP("ถูกขอให้ติดตั้งแอปหรือแชร์หน้าจอ", 45, block = true),
}

sealed interface AnalysisOutcome {
    data class Success(val result: RiskResult) : AnalysisOutcome
    data class Invalid(val message: String) : AnalysisOutcome
}
