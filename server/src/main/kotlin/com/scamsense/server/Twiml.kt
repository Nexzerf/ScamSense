package com.scamsense.server

/**
 * Minimal TwiML builder. Every piece of text (including caller transcripts that we
 * read back) is XML-escaped, so a caller cannot inject TwiML verbs by speaking.
 */
class Twiml(private val cfg: Config) {
    private val sb = StringBuilder("""<?xml version="1.0" encoding="UTF-8"?><Response>""")

    fun say(text: String) = apply {
        sb.append("""<Say voice="${esc(cfg.ttsVoice)}" language="${esc(cfg.sttLanguage)}">""").append(esc(text)).append("</Say>")
    }

    fun gather(action: String, prompt: String) = apply {
        sb.append("""<Gather input="speech" language="${esc(cfg.sttLanguage)}" speechTimeout="auto" timeout="6" """)
        cfg.speechModel?.let { sb.append("""speechModel="${esc(it)}" """) }
        sb.append("""actionOnEmptyResult="true" action="${esc(action)}" method="POST">""")
        say(prompt)
        sb.append("</Gather>")
    }

    fun dial(callerId: String, number: String, whisperUrl: String, timeoutSec: Int = 25) = apply {
        sb.append("""<Dial callerId="${esc(callerId)}" timeout="$timeoutSec" answerOnBridge="true">""")
        sb.append("""<Number url="${esc(whisperUrl)}">""").append(esc(number)).append("</Number></Dial>")
    }

    fun hangup() = apply { sb.append("<Hangup/>") }

    fun reject() = apply { sb.append("""<Reject reason="busy"/>""") }

    fun build(): String = sb.append("</Response>").toString()

    companion object {
        fun esc(s: String): String {
            val out = StringBuilder(s.length + 16)
            for (c in s) {
                when (c) {
                    '&' -> out.append("&amp;")
                    '<' -> out.append("&lt;")
                    '>' -> out.append("&gt;")
                    '"' -> out.append("&quot;")
                    '\'' -> out.append("&apos;")
                    else -> if (c >= ' ' || c == '\n' || c == '\t') out.append(c) // drop XML-illegal control chars
                }
            }
            return out.toString()
        }
    }
}
