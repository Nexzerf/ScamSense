package com.scamsense.server

import com.scamsense.core.engine.TextNormalizer
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

fun interface SmsSender {
    fun send(from: String, to: String, body: String)
}

/** Sends SMS through Twilio's Messages API (JDK HTTP client; no extra dependency). */
class TwilioSms(private val accountSid: String, private val authToken: String) : SmsSender {
    private val log = LoggerFactory.getLogger(TwilioSms::class.java)
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    override fun send(from: String, to: String, body: String) {
        val form = mapOf("From" to from, "To" to to, "Body" to body).entries
            .joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, Charsets.UTF_8) }
        val auth = Base64.getEncoder().encodeToString("$accountSid:$authToken".toByteArray())
        val req = HttpRequest.newBuilder(URI("https://api.twilio.com/2010-04-01/Accounts/$accountSid/Messages.json"))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Basic $auth")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()
        http.sendAsync(req, HttpResponse.BodyHandlers.discarding()).whenComplete { r, e ->
            if (e != null || (r != null && r.statusCode() >= 300)) log.warn("SMS send failed: status={} error={}", r?.statusCode(), e?.javaClass?.simpleName)
        }
    }
}

/** Used when Twilio credentials are not configured (dev). Never logs message content. */
object NoopSms : SmsSender {
    private val log = LoggerFactory.getLogger("NoopSms")
    override fun send(from: String, to: String, body: String) = log.info("SMS disabled (no Twilio credentials); skipped one alert")
}

object Alerts {
    fun scamBlocked(caller: String, scamType: String?, transcript: List<String>): String = buildString {
        append("ScamSense: ผู้ช่วยวางสายมิจฉาชีพให้แล้ว\n")
        append("จาก: ${caller.ifBlank { "ไม่แสดงเบอร์" }}\n")
        scamType?.let { append("ลักษณะ: $it\n") }
        if (transcript.isNotEmpty()) append("เขาพูดว่า: \"${TextNormalizer.preview(transcript.joinToString(" "), 120)}\"\n")
        append("อย่าโทรกลับเบอร์นี้ หากไม่แน่ใจโทร 1441")
    }

    fun messageTaken(caller: String, transcript: List<String>): String =
        "ScamSense: สายน่าสงสัยจาก ${caller.ifBlank { "ไม่แสดงเบอร์" }} ผู้ช่วยรับฝากข้อความไว้: " +
            "\"${TextNormalizer.preview(transcript.joinToString(" "), 140)}\" ตรวจสอบก่อนโทรกลับนะคะ"
}
