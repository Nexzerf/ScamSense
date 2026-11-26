package com.scamsense.server

/** All configuration comes from environment variables (12-factor; no secrets in files). */
data class Config(
    val port: Int = 8080,
    /** Public HTTPS base URL Twilio calls, e.g. https://assist.example.com (used for signatures). */
    val publicBaseUrl: String,
    val dbPath: String = "scamsense.db",
    val twilioAccountSid: String? = null,
    val twilioAuthToken: String? = null,
    /** Only for local development. Production must validate Twilio signatures. */
    val allowUnsignedWebhooks: Boolean = false,
    val adminToken: String? = null,
    val sttLanguage: String = "th-TH",
    /** Optional Twilio speechModel (e.g. a Google/Deepgram model that supports Thai). */
    val speechModel: String? = null,
    val ttsVoice: String = "Google.th-TH-Standard-A",
    val retentionDays: Int = 30,
    val maxTurns: Int = 3,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): Config {
            fun s(k: String) = env[k]?.trim()?.takeIf { it.isNotEmpty() }
            val base = s("PUBLIC_BASE_URL") ?: error("PUBLIC_BASE_URL is required (https://…)")
            require(base.startsWith("https://") || s("ALLOW_UNSIGNED_WEBHOOKS") == "true") {
                "PUBLIC_BASE_URL must be https"
            }
            return Config(
                port = s("PORT")?.toInt() ?: 8080,
                publicBaseUrl = base.trimEnd('/'),
                dbPath = s("DB_PATH") ?: "scamsense.db",
                twilioAccountSid = s("TWILIO_ACCOUNT_SID"),
                twilioAuthToken = s("TWILIO_AUTH_TOKEN"),
                allowUnsignedWebhooks = s("ALLOW_UNSIGNED_WEBHOOKS") == "true",
                adminToken = s("ADMIN_TOKEN")?.also { require(it.length >= 24) { "ADMIN_TOKEN must be >= 24 chars" } },
                sttLanguage = s("STT_LANGUAGE") ?: "th-TH",
                speechModel = s("SPEECH_MODEL"),
                ttsVoice = s("TTS_VOICE") ?: "Google.th-TH-Standard-A",
                retentionDays = s("RETENTION_DAYS")?.toInt() ?: 30,
                maxTurns = s("MAX_TURNS")?.toInt()?.coerceIn(2, 5) ?: 3,
            )
        }
    }
}
