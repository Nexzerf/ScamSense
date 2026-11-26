package com.scamsense.app.data

import com.scamsense.core.api.CallListResponse
import com.scamsense.core.api.CallRecordDto
import com.scamsense.core.api.ErrorResponse
import com.scamsense.core.api.PairRequest
import com.scamsense.core.api.PairResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Talks to the user's own ScamSense call-assistant server. HTTPS only; the device token
 * is sent as a Bearer header and never logged. Responses are size-capped.
 */
class AssistantClient(private val json: Json = Json { ignoreUnknownKeys = true }) {

    sealed interface Result<out T> {
        data class Ok<T>(val value: T) : Result<T>
        data class Error(val message: String) : Result<Nothing>
    }

    /** Validates and normalises a server address typed by the user. */
    fun normaliseServer(input: String): String? {
        val raw = input.trim()
        if (raw.isEmpty() || raw.any { it.isWhitespace() }) return null
        val withScheme = if ("://" in raw) raw else "https://$raw"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        val host = uri.host
        if (!uri.scheme.equals("https", ignoreCase = true) || host.isNullOrBlank() || '.' !in host || uri.userInfo != null) return null
        if (!(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") || uri.rawQuery != null || uri.rawFragment != null) return null
        // Rebuilt from validated parts only.
        return "https://" + host.lowercase() + if (uri.port > 0) ":${uri.port}" else ""
    }

    suspend fun pair(server: String, code: String): Result<PairResponse> = request(
        server, "/api/pair", "POST", token = null,
        body = json.encodeToString(PairRequest.serializer(), PairRequest(code.trim().uppercase())),
    ) { json.decodeFromString(PairResponse.serializer(), it) }

    suspend fun calls(server: String, token: String): Result<List<CallRecordDto>> =
        request(server, "/api/calls", "GET", token, null) { json.decodeFromString(CallListResponse.serializer(), it).calls }

    suspend fun deleteCalls(server: String, token: String): Result<Unit> =
        request(server, "/api/calls", "DELETE", token, null) { }

    private suspend fun <T> request(
        server: String,
        path: String,
        method: String,
        token: String?,
        body: String?,
        parse: (String) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        val base = normaliseServer(server) ?: return@withContext Result.Error("ที่อยู่เซิร์ฟเวอร์ต้องขึ้นต้นด้วย https://")
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(base + path).openConnection() as HttpsURLConnection).apply {
                requestMethod = method
                connectTimeout = 10_000
                readTimeout = 15_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
                token?.let { setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { readCapped(it) }.orEmpty()
            when {
                code in 200..299 -> Result.Ok(parse(text))
                code == 401 -> Result.Error("การจับคู่หมดอายุ กรุณาจับคู่ใหม่")
                code == 429 -> Result.Error("ลองบ่อยเกินไป กรุณารอสักครู่")
                else -> Result.Error(runCatching { json.decodeFromString(ErrorResponse.serializer(), text).error }
                    .getOrDefault("เซิร์ฟเวอร์ตอบกลับผิดพลาด ($code)"))
            }
        } catch (_: IOException) {
            Result.Error("เชื่อมต่อเซิร์ฟเวอร์ไม่ได้ ตรวจสอบอินเทอร์เน็ตแล้วลองใหม่")
        } catch (_: RuntimeException) {
            Result.Error("ข้อมูลจากเซิร์ฟเวอร์ไม่ถูกต้อง")
        } finally {
            conn?.disconnect()
        }
    }

    /** Reads at most [MAX_BYTES] (InputStream.readNBytes needs API 33). */
    private fun readCapped(input: java.io.InputStream): String {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        while (out.size() < MAX_BYTES) {
            val n = input.read(buf, 0, minOf(buf.size, MAX_BYTES - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toString("UTF-8")
    }

    private companion object {
        const val MAX_BYTES = 512 * 1024
    }
}
