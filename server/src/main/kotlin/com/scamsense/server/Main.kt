package com.scamsense.server

import com.scamsense.core.api.CallListResponse
import com.scamsense.core.api.CallOutcome
import com.scamsense.core.api.ErrorResponse
import com.scamsense.core.api.PairRequest
import com.scamsense.core.api.PairResponse
import com.scamsense.core.engine.RiskResult
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

fun main() {
    val cfg = Config.fromEnv()
    val store = Store(cfg.dbPath).apply { retentionDays = cfg.retentionDays }
    val sms: SmsSender = if (cfg.twilioAccountSid != null && cfg.twilioAuthToken != null) {
        TwilioSms(cfg.twilioAccountSid, cfg.twilioAuthToken)
    } else NoopSms
    if (cfg.twilioAuthToken == null && !cfg.allowUnsignedWebhooks) {
        LoggerFactory.getLogger("main").warn("TWILIO_AUTH_TOKEN not set: all voice webhooks will be rejected (403)")
    }
    embeddedServer(Netty, port = cfg.port) {
        scamSenseModule(cfg, store, sms, CallAssistant(maxTurns = cfg.maxTurns))
    }.start(wait = true)
}

@Serializable
data class NewSubscriberRequest(val name: String, val phone: String, val caregiverPhone: String? = null, val assistantNumber: String)

@Serializable
data class PairingCodeResponse(val subscriberId: Long, val pairingCode: String, val expiresAt: Long)

/** Normalises Thai/international numbers to E.164 (+66…), the format Twilio uses. */
fun toE164(raw: String): String {
    val t = raw.trim()
    val digits = t.filter { it.isDigit() }
    return when {
        t.startsWith("+") -> "+$digits"
        digits.startsWith("00") -> "+" + digits.drop(2)
        digits.startsWith("0") -> "+66" + digits.drop(1)
        else -> "+$digits"
    }
}

fun Application.scamSenseModule(
    cfg: Config,
    store: Store,
    sms: SmsSender,
    assistant: CallAssistant,
    clock: () -> Long = System::currentTimeMillis,
) {
    val log = LoggerFactory.getLogger("ScamSense")
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val pairLimiter = RateLimiter(max = 10, windowMs = 60_000)
    install(ContentNegotiation) { json(json) }

    /** Reads Twilio form params and verifies X-Twilio-Signature (fail closed). */
    suspend fun ApplicationCall.twilioParams(): Map<String, String>? {
        val len = request.header(HttpHeaders.ContentLength)?.toLongOrNull() ?: 0
        if (len > 64 * 1024) { respond(HttpStatusCode.PayloadTooLarge); return null }
        val p = receiveParameters()
        val params = p.names().associateWith { p[it].orEmpty() }
        if (!cfg.allowUnsignedWebhooks) {
            val token = cfg.twilioAuthToken
            val url = cfg.publicBaseUrl + request.uri
            if (token == null || !Security.validTwilioSignature(token, url, params, request.header("X-Twilio-Signature"))) {
                respond(HttpStatusCode.Forbidden)
                return null
            }
        }
        return params
    }

    suspend fun ApplicationCall.twiml(body: String) = respondText(body, ContentType.Application.Xml)

    fun alert(sub: Subscriber, body: String) {
        listOfNotNull(sub.phone, sub.caregiverPhone?.takeIf { it.isNotBlank() }).forEach {
            runCatching { sms.send(sub.assistantNumber, it, body) }
        }
    }

    fun finish(sid: String, outcome: CallOutcome, r: RiskResult?, fallbackType: String? = null) {
        store.finishCall(
            sid, outcome, r?.score ?: if (outcome == CallOutcome.BLOCKED) 100 else 0,
            r?.level?.name ?: if (outcome == CallOutcome.BLOCKED) "DANGEROUS" else "SAFE",
            r?.matchedScamType ?: fallbackType,
            r?.signals?.filter { it.weight > 0 }?.take(5)?.map { it.title } ?: listOfNotNull(fallbackType),
        )
    }

    /** Turns the assistant's decision into TwiML + side effects. */
    suspend fun ApplicationCall.act(step: CallAssistant.Step, sub: Subscriber, session: CallSession, transcript: List<String>) {
        val sid = session.callSid
        when (step) {
            is CallAssistant.Step.Ask -> twiml(Twiml(cfg).gather("${cfg.publicBaseUrl}/voice/turn", step.prompt).build())
            is CallAssistant.Step.HangUpScam -> {
                finish(sid, CallOutcome.BLOCKED, step.result, step.reason)
                alert(sub, Alerts.scamBlocked(session.caller, step.result?.matchedScamType ?: step.reason, transcript))
                log.info("call {} blocked as scam", session.id)
                // Polite, neutral ending: the scammer learns nothing about why.
                twiml(Twiml(cfg).say("ขอบคุณค่ะ ขณะนี้เจ้าของเบอร์ไม่สะดวกรับสาย").hangup().build())
            }
            is CallAssistant.Step.Connect -> {
                finish(sid, CallOutcome.CONNECTED, step.result)
                twiml(
                    Twiml(cfg).say("ขอบคุณค่ะ กรุณารอสักครู่ กำลังโอนสายค่ะ")
                        .dial(sub.assistantNumber, sub.phone, "${cfg.publicBaseUrl}/voice/whisper?call=${session.id}")
                        .say("เจ้าของเบอร์ไม่สะดวกรับสาย กรุณาติดต่อใหม่ภายหลังค่ะ").hangup().build(),
                )
            }
            is CallAssistant.Step.TakeMessage -> {
                finish(sid, CallOutcome.MESSAGE_TAKEN, step.result)
                alert(sub, Alerts.messageTaken(session.caller, transcript))
                twiml(Twiml(cfg).say("ขอบคุณค่ะ ระบบบันทึกข้อความไว้แล้ว เจ้าของเบอร์จะติดต่อกลับหากต้องการค่ะ").hangup().build())
            }
            CallAssistant.Step.HangUpSilent -> {
                finish(sid, CallOutcome.NO_SPEECH, null)
                twiml(Twiml(cfg).hangup().build())
            }
        }
    }

    suspend fun ApplicationCall.subscriberFromToken(): Subscriber? {
        val auth = request.header(HttpHeaders.Authorization)
        val token = auth?.removePrefix("Bearer ")?.trim()?.takeIf { auth.startsWith("Bearer ") && it.length in 20..200 }
        val sub = token?.let { store.subscriberByToken(Security.sha256(it)) }
        if (sub == null) respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized"))
        return sub
    }

    suspend fun ApplicationCall.requireAdmin(): Boolean {
        val admin = cfg.adminToken
        if (admin == null) { respond(HttpStatusCode.NotFound); return false }
        val given = request.header(HttpHeaders.Authorization)?.removePrefix("Bearer ")?.trim().orEmpty()
        if (!Security.constantTimeEquals(given, admin)) { respond(HttpStatusCode.Unauthorized); return false }
        return true
    }

    fun issuePairingCode(subscriberId: Long): PairingCodeResponse {
        val code = Security.newPairingCode()
        val expires = clock() + 15 * 60_000
        store.setPairingCode(subscriberId, Security.sha256(code), expires)
        return PairingCodeResponse(subscriberId, code, expires)
    }

    routing {
        get("/health") { call.respondText("ok") }

        // ------------------------------------------------------------- voice webhooks (Twilio)
        post("/voice/incoming") {
            val p = call.twilioParams() ?: return@post
            val sid = p["CallSid"].orEmpty()
            val from = p["From"].orEmpty().let { if (it.any(Char::isDigit)) toE164(it) else "" }
            val to = toE164(p["To"].orEmpty())
            val sub = store.subscriberByAssistantNumber(to)
            when {
                sid.isBlank() || sub == null ->
                    call.twiml(Twiml(cfg).say("ขออภัยค่ะ หมายเลขนี้ไม่พร้อมให้บริการ").hangup().build())
                // A bridged call bounced back by the user's own forwarding: stop the loop.
                store.isAssistantNumber(from) -> call.twiml(Twiml(cfg).reject().build())
                else -> {
                    val session = store.startCall(sub.id, sid, from)
                    val pre = assistant.screenCaller(from)
                    if (pre != null) call.act(pre, sub, session, emptyList())
                    else call.twiml(Twiml(cfg).gather("${cfg.publicBaseUrl}/voice/turn", assistant.greeting(sub.name)).build())
                }
            }
        }

        post("/voice/turn") {
            val p = call.twilioParams() ?: return@post
            val session = store.session(p["CallSid"].orEmpty())
            val sub = session?.let { store.subscriber(it.subscriberId) }
            if (session == null || sub == null || session.outcome != null) {
                call.twiml(Twiml(cfg).hangup().build()); return@post
            }
            val (step, transcript) = assistant.onTurn(session.transcript, p["SpeechResult"], session.silentTurns)
            // Store masked text only (account / ID numbers spoken by the caller).
            val masked = transcript.map { com.scamsense.core.engine.TextNormalizer.maskSensitiveDigits(it) }
            store.recordTurn(session.callSid, masked, if (p["SpeechResult"].isNullOrBlank()) session.silentTurns + 1 else session.silentTurns)
            call.act(step, sub, session, masked)
        }

        post("/voice/whisper") {
            call.twilioParams() ?: return@post
            val id = call.request.queryParameters["call"]?.toLongOrNull()
            val transcript = id?.let { cid -> store.recentCallTranscript(cid) }.orEmpty()
            call.twiml(Twiml(cfg).say(assistant.whisper(transcript)).build())
        }

        // ------------------------------------------------------------- app API
        post("/api/pair") {
            val ip = call.request.origin.remoteAddress
            if (!pairLimiter.allow(ip)) { call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("too many attempts")); return@post }
            val req = runCatching { call.receive<PairRequest>() }.getOrNull()
            val code = req?.code?.trim()?.uppercase().orEmpty()
            if (code.length !in 6..12) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("รหัสจับคู่ไม่ถูกต้อง")); return@post }
            val token = Security.newToken()
            val sub = store.redeemPairingCode(Security.sha256(code), Security.sha256(token))
            if (sub == null) { call.respond(HttpStatusCode.NotFound, ErrorResponse("รหัสจับคู่ไม่ถูกต้องหรือหมดอายุ")); return@post }
            call.respond(PairResponse(token = token, subscriberName = sub.name, assistantNumber = sub.assistantNumber))
        }

        get("/api/calls") {
            val sub = call.subscriberFromToken() ?: return@get
            call.respond(CallListResponse(store.recentCalls(sub.id)))
        }

        delete("/api/calls") {
            val sub = call.subscriberFromToken() ?: return@delete
            store.deleteCalls(sub.id)
            call.respond(HttpStatusCode.NoContent)
        }

        // ------------------------------------------------------------- admin API (disabled without ADMIN_TOKEN)
        post("/admin/subscribers") {
            if (!call.requireAdmin()) return@post
            val r = runCatching { call.receive<NewSubscriberRequest>() }.getOrNull()
            if (r == null || r.name.isBlank() || r.phone.count(Char::isDigit) < 9 || r.assistantNumber.count(Char::isDigit) < 9) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("name, phone and assistantNumber are required")); return@post
            }
            val id = runCatching {
                store.addSubscriber(r.name.take(60), toE164(r.phone), r.caregiverPhone?.takeIf { it.isNotBlank() }?.let(::toE164), toE164(r.assistantNumber))
            }.getOrElse { call.respond(HttpStatusCode.Conflict, ErrorResponse("assistantNumber already assigned")); return@post }
            call.respond(issuePairingCode(id))
        }

        post("/admin/subscribers/{id}/pairing-code") {
            if (!call.requireAdmin()) return@post
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null || store.subscriber(id) == null) { call.respond(HttpStatusCode.NotFound); return@post }
            call.respond(issuePairingCode(id))
        }
    }
}
