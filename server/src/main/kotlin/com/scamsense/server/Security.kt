package com.scamsense.server

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Security {
    private val random = SecureRandom()
    private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no 0/O/1/I

    fun newToken(): String {
        val b = ByteArray(32).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    }

    fun newPairingCode(length: Int = 8): String =
        (1..length).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }.joinToString("")

    fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    /**
     * Twilio request signature: Base64(HMAC-SHA1(authToken, fullUrl + Σ sorted(key+value))).
     * https://www.twilio.com/docs/usage/security#validating-requests
     */
    fun twilioSignature(authToken: String, url: String, params: Map<String, String>): String {
        val data = StringBuilder(url)
        params.keys.sorted().forEach { k -> data.append(k).append(params[k]) }
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(authToken.toByteArray(), "HmacSHA1")) }
        return Base64.getEncoder().encodeToString(mac.doFinal(data.toString().toByteArray(Charsets.UTF_8)))
    }

    fun validTwilioSignature(authToken: String, url: String, params: Map<String, String>, header: String?): Boolean =
        header != null && constantTimeEquals(twilioSignature(authToken, url, params), header)
}

/** Fixed-window limiter, e.g. pairing attempts per IP (brute-force protection). */
class RateLimiter(private val max: Int, private val windowMs: Long, private val clock: () -> Long = System::currentTimeMillis) {
    private data class Window(val start: Long, var count: Int)
    private val windows = ConcurrentHashMap<String, Window>()

    fun allow(key: String): Boolean {
        val now = clock()
        if (windows.size > 10_000) windows.entries.removeIf { now - it.value.start > windowMs }
        val w = windows.compute(key) { _, old ->
            if (old == null || now - old.start > windowMs) Window(now, 1) else old.apply { count++ }
        }!!
        return w.count <= max
    }
}
