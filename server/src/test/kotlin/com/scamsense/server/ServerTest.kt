package com.scamsense.server

import com.scamsense.core.api.CallListResponse
import com.scamsense.core.api.CallOutcome
import com.scamsense.core.api.PairResponse
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ServerTest {
    private val base = "https://assist.example.test"
    private val authToken = "twilio-test-auth-token"
    private val admin = "admin-token-0123456789abcdef"
    private val db = File.createTempFile("scamsense", ".db").also { it.deleteOnExit() }
    private val cfg = Config(publicBaseUrl = base, dbPath = db.path, twilioAuthToken = authToken, adminToken = admin)
    private val sent = mutableListOf<Triple<String, String, String>>()
    private val json = Json { ignoreUnknownKeys = true }

    @After fun cleanup() { db.delete() }

    private fun app(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val store = Store(cfg.dbPath)
        application { scamSenseModule(cfg, store, { f, t, b -> sent += Triple(f, t, b) }, CallAssistant()) }
        block()
    }

    /** Posts a Twilio-style webhook with a valid X-Twilio-Signature. */
    private suspend fun ApplicationTestBuilder.twilio(path: String, params: Map<String, String>, sign: Boolean = true) =
        client.post(path) {
            contentType(ContentType.Application.FormUrlEncoded)
            if (sign) header("X-Twilio-Signature", Security.twilioSignature(authToken, base + path, params))
            setBody(params.entries.map { it.key to it.value }.formUrlEncode())
        }

    private suspend fun ApplicationTestBuilder.setupSubscriber(): String {
        val res = client.post("/admin/subscribers") {
            header(HttpHeaders.Authorization, "Bearer $admin")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"สมศรี","phone":"081-234-5678","caregiverPhone":"089-999-9999","assistantNumber":"02-123-4567"}""")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        val code = json.parseToJsonElement(res.bodyAsText()).jsonObject["pairingCode"]!!.jsonPrimitive.content
        val pair = client.post("/api/pair") { contentType(ContentType.Application.Json); setBody("""{"code":"$code"}""") }
        assertEquals(HttpStatusCode.OK, pair.status)
        val pr = json.decodeFromString(PairResponse.serializer(), pair.bodyAsText())
        assertEquals("+6621234567", pr.assistantNumber)
        // Pairing codes are single-use.
        val again = client.post("/api/pair") { contentType(ContentType.Application.Json); setBody("""{"code":"$code"}""") }
        assertEquals(HttpStatusCode.NotFound, again.status)
        return pr.token
    }

    private suspend fun ApplicationTestBuilder.calls(token: String) =
        json.decodeFromString(CallListResponse.serializer(), client.get("/api/calls") { header(HttpHeaders.Authorization, "Bearer $token") }.bodyAsText()).calls

    @Test fun scamCallIsHungUpAlertedAndLogged() = app {
        val token = setupSubscriber()
        val call = mapOf("CallSid" to "CA1", "From" to "+66899990000", "To" to "+6621234567")
        val greet = twilio("/voice/incoming", call).bodyAsText()
        assertTrue(greet.contains("<Gather input=\"speech\" language=\"th-TH\""))
        assertTrue(greet.contains("ผู้ช่วยรับสายอัตโนมัติ"))

        val turn = twilio("/voice/turn", call + ("SpeechResult" to "ผมโทรจากสถานีตำรวจ บัญชีคุณพัวพันคดีฟอกเงิน ต้องโอนเงินเข้าบัญชี 1234567890 ซึ่งเป็นบัญชีปลอดภัยเพื่อตรวจสอบ ห้ามวางสาย"))
            .bodyAsText()
        assertTrue(turn.contains("<Hangup/>"))
        assertFalse(turn.contains("<Dial"))

        // SMS to the protected person AND the caregiver, sent from the assistant number.
        assertEquals(setOf("+66812345678", "+66899999999"), sent.map { it.second }.toSet())
        assertTrue(sent.all { it.first == "+6621234567" && it.third.contains("วางสายมิจฉาชีพ") })

        val log = calls(token)
        assertEquals(1, log.size)
        assertEquals(CallOutcome.BLOCKED, log[0].outcome)
        assertTrue(log[0].transcript.single().contains("บัญชีปลอดภัย"))
        assertFalse("account number masked", log[0].transcript.single().contains("1234567"))
    }

    @Test fun safeCallIsWhisperedAndConnected() = app {
        setupSubscriber()
        val call = mapOf("CallSid" to "CA2", "From" to "+66812220000", "To" to "+6621234567")
        twilio("/voice/incoming", call)
        twilio("/voice/turn", call + ("SpeechResult" to "สวัสดีค่ะ โทรจากคลินิกทันตกรรม เรื่องนัดทำฟัน"))
        val r = twilio("/voice/turn", call + ("SpeechResult" to "ชื่อพยาบาลสมศรีค่ะ เป็นคนไข้ประจำ")).bodyAsText()
        assertTrue(r, r.contains("<Dial callerId=\"+6621234567\""))
        assertTrue(r.contains(">+66812345678</Number>"))
        val whisperPath = Regex("""url="$base(/voice/whisper\?call=\d+)"""").find(r)!!.groupValues[1]
        val w = twilio(whisperPath, mapOf("CallSid" to "CA2-child")).bodyAsText()
        assertTrue(w.contains("คลินิกทันตกรรม"))
        assertTrue(sent.isEmpty())
    }

    @Test fun unsignedOrForgedWebhooksAreRejected() = app {
        setupSubscriber()
        val call = mapOf("CallSid" to "CA3", "From" to "+66899990000", "To" to "+6621234567")
        assertEquals(HttpStatusCode.Forbidden, twilio("/voice/incoming", call, sign = false).status)
        val forged = client.post("/voice/incoming") {
            contentType(ContentType.Application.FormUrlEncoded)
            header("X-Twilio-Signature", Security.twilioSignature("wrong-token", "$base/voice/incoming", call))
            setBody(call.entries.map { it.key to it.value }.formUrlEncode())
        }
        assertEquals(HttpStatusCode.Forbidden, forged.status)
    }

    @Test fun spokenTwimlInjectionIsEscaped() = app {
        setupSubscriber()
        val call = mapOf("CallSid" to "CA4", "From" to "+66812220000", "To" to "+6621234567")
        twilio("/voice/incoming", call)
        twilio("/voice/turn", call + ("SpeechResult" to "นัดหมอครับ"))
        val r = twilio("/voice/turn", call + ("SpeechResult" to "</Say><Dial>+15550000</Dial> ชื่อสมชายครับ")).bodyAsText()
        val w = twilio(Regex("""url="$base(/voice/whisper\?call=\d+)"""").find(r)!!.groupValues[1], mapOf("CallSid" to "x")).bodyAsText()
        assertFalse(w.contains("<Dial>"))
        assertTrue(w.contains("&lt;/Say&gt;"))
    }

    @Test fun bouncedBridgeCallIsRejectedToPreventLoop() = app {
        setupSubscriber()
        val r = twilio("/voice/incoming", mapOf("CallSid" to "CA5", "From" to "+6621234567", "To" to "+6621234567")).bodyAsText()
        assertTrue(r.contains("<Reject"))
    }

    @Test fun spoofedCallerHungUpImmediately() = app {
        setupSubscriber()
        val r = twilio("/voice/incoming", mapOf("CallSid" to "CA6", "From" to "+6971234567", "To" to "+6621234567")).bodyAsText()
        assertTrue(r.contains("<Hangup/>"))
        assertEquals(2, sent.size)
    }

    @Test fun apiRequiresValidTokenAndCanWipeData() = app {
        val token = setupSubscriber()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/calls").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/calls") { header(HttpHeaders.Authorization, "Bearer " + "x".repeat(43)) }.status)
        val call = mapOf("CallSid" to "CA7", "From" to "+6971234567", "To" to "+6621234567")
        twilio("/voice/incoming", call)
        assertEquals(1, calls(token).size)
        assertEquals(HttpStatusCode.NoContent, client.delete("/api/calls") { header(HttpHeaders.Authorization, "Bearer $token") }.status)
        assertEquals(0, calls(token).size)
    }

    @Test fun adminApiProtectedAndPairingRateLimited() = app {
        assertEquals(HttpStatusCode.Unauthorized, client.post("/admin/subscribers") { header(HttpHeaders.Authorization, "Bearer nope") }.status)
        var limited = false
        repeat(15) {
            val r = client.post("/api/pair") { contentType(ContentType.Application.Json); setBody("""{"code":"ABCDEFGH"}""") }
            if (r.status == HttpStatusCode.TooManyRequests) limited = true
        }
        assertTrue(limited)
    }
}
