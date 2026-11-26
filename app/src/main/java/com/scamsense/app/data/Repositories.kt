package com.scamsense.app.data

import com.scamsense.core.engine.CheckType
import com.scamsense.core.engine.RiskLevel
import com.scamsense.core.engine.RiskResult
import com.scamsense.core.engine.RiskSignal
import com.scamsense.core.engine.SignalCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class TextScale(val factor: Float, val label: String) {
    NORMAL(1.0f, "ปกติ"),
    LARGE(1.15f, "ใหญ่"),
    EXTRA_LARGE(1.3f, "ใหญ่มาก"),
}

enum class ThemeMode(val label: String) { SYSTEM("ตามเครื่อง"), LIGHT("สว่าง"), DARK("มืด") }

data class AppSettings(
    val caregiverName: String = "",
    val caregiverPhone: String = "",
    val textScale: TextScale = TextScale.LARGE,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val saveHistory: Boolean = true,
    val customBlockWords: List<String> = emptyList(),
    val blockedNumbers: List<String> = emptyList(),
    // ---- Call protection (phase 1: on-device screening) ----
    val callWarnings: Boolean = true,
    val blockInternational: Boolean = false,
    // ---- AI call assistant (phase 2) ----
    val assistantServer: String = "",
    val assistantToken: String = "",
    val assistantNumber: String = "",
    val assistantName: String = "",
    /** User confirmed busy-forwarding to the assistant number was set up on the SIM. */
    val forwardingConfirmed: Boolean = false,
    val sendUnknownToAssistant: Boolean = true,
) {
    val hasCaregiver: Boolean get() = caregiverPhone.isNotBlank()
    val assistantPaired: Boolean get() = assistantToken.isNotBlank() && assistantServer.isNotBlank()
    /** Only reject unknown calls when they will really land on the assistant (paired + forwarding set). */
    val assistantActive: Boolean get() = assistantPaired && forwardingConfirmed && sendUnknownToAssistant
}

/** Settings persisted encrypted (they contain a caregiver's phone number). */
class SettingsRepository(private val store: BlobStore) {

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    private val mutex = Mutex()
    @Volatile private var loaded = false

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _settings.value = store.read()?.let(::decode) ?: AppSettings()
            loaded = true
        }
    }

    /** For background entry points (call screening) that may run before the UI started. */
    suspend fun ensureLoaded(): AppSettings {
        if (!loaded) load()
        return _settings.value
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = transform(_settings.value)
            _settings.value = next
            runCatching { store.write(encode(next)) }
        }
    }

    private fun encode(s: AppSettings): String = JSONObject()
        .put("caregiverName", s.caregiverName)
        .put("caregiverPhone", s.caregiverPhone)
        .put("textScale", s.textScale.name)
        .put("themeMode", s.themeMode.name)
        .put("saveHistory", s.saveHistory)
        .put("customBlockWords", JSONArray(s.customBlockWords))
        .put("blockedNumbers", JSONArray(s.blockedNumbers))
        .put("callWarnings", s.callWarnings)
        .put("blockInternational", s.blockInternational)
        .put("assistantServer", s.assistantServer)
        .put("assistantToken", s.assistantToken)
        .put("assistantNumber", s.assistantNumber)
        .put("assistantName", s.assistantName)
        .put("forwardingConfirmed", s.forwardingConfirmed)
        .put("sendUnknownToAssistant", s.sendUnknownToAssistant)
        .toString()

    private fun decode(json: String): AppSettings? = runCatching {
        val o = JSONObject(json)
        AppSettings(
            caregiverName = o.optString("caregiverName").take(60),
            caregiverPhone = o.optString("caregiverPhone").take(20),
            textScale = enumOr(o.optString("textScale"), TextScale.LARGE),
            themeMode = enumOr(o.optString("themeMode"), ThemeMode.SYSTEM),
            saveHistory = o.optBoolean("saveHistory", true),
            customBlockWords = o.optJSONArray("customBlockWords").toStringList(MAX_LIST),
            blockedNumbers = o.optJSONArray("blockedNumbers").toStringList(MAX_LIST),
            callWarnings = o.optBoolean("callWarnings", true),
            blockInternational = o.optBoolean("blockInternational", false),
            assistantServer = o.optString("assistantServer").take(200),
            assistantToken = o.optString("assistantToken").take(200),
            assistantNumber = o.optString("assistantNumber").take(20),
            assistantName = o.optString("assistantName").take(60),
            forwardingConfirmed = o.optBoolean("forwardingConfirmed", false),
            sendUnknownToAssistant = o.optBoolean("sendUnknownToAssistant", true),
        )
    }.getOrNull()

    companion object {
        const val MAX_LIST = 200
    }
}

/** Check history, newest first, capped and encrypted at rest. */
class HistoryRepository(private val store: BlobStore) {

    private val _items = MutableStateFlow<List<RiskResult>>(emptyList())
    val items: StateFlow<List<RiskResult>> = _items.asStateFlow()
    private val mutex = Mutex()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock { _items.value = store.read()?.let(::decodeList) ?: emptyList() }
    }

    suspend fun add(result: RiskResult) = withContext(Dispatchers.IO) {
        mutex.withLock {
            _items.update { (listOf(result) + it).take(MAX_ITEMS) }
            persist()
        }
    }

    suspend fun remove(analyzedAt: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            _items.update { list -> list.filterNot { it.analyzedAt == analyzedAt } }
            persist()
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _items.value = emptyList()
            store.delete()
        }
    }

    private fun persist() {
        val arr = JSONArray()
        _items.value.forEach { arr.put(encode(it)) }
        runCatching { store.write(arr.toString()) }
    }

    private fun encode(r: RiskResult): JSONObject = JSONObject()
        .put("type", r.type.name)
        .put("score", r.score)
        .put("level", r.level.name)
        .put("block", r.shouldBlock)
        .put("summary", r.summary)
        .put("scamType", r.matchedScamType ?: JSONObject.NULL)
        .put("preview", r.inputPreview)
        .put("urls", JSONArray(r.extractedUrls))
        .put("phones", JSONArray(r.extractedPhones))
        .put("at", r.analyzedAt)
        .put("ms", r.elapsedMs)
        .put("advice", JSONArray(r.advice))
        .put("signals", JSONArray().apply {
            r.signals.forEach { s ->
                put(JSONObject()
                    .put("c", s.category.name)
                    .put("t", s.title)
                    .put("d", s.detail)
                    .put("w", s.weight)
                    .put("e", s.evidence ?: JSONObject.NULL))
            }
        })

    private fun decodeList(json: String): List<RiskResult> = runCatching {
        val arr = JSONArray(json)
        (0 until minOf(arr.length(), MAX_ITEMS)).mapNotNull { i -> runCatching { decode(arr.getJSONObject(i)) }.getOrNull() }
    }.getOrDefault(emptyList())

    private fun decode(o: JSONObject): RiskResult {
        val sigArr = o.getJSONArray("signals")
        val signals = (0 until sigArr.length()).map { i ->
            val s = sigArr.getJSONObject(i)
            RiskSignal(
                category = enumOr(s.optString("c"), SignalCategory.INFO),
                title = s.getString("t"),
                detail = s.optString("d"),
                weight = s.optInt("w"),
                evidence = s.optNullableString("e"),
            )
        }
        return RiskResult(
            type = enumOr(o.optString("type"), CheckType.MESSAGE),
            score = o.getInt("score").coerceIn(0, 100),
            level = enumOr(o.optString("level"), RiskLevel.SAFE),
            shouldBlock = o.optBoolean("block"),
            summary = o.optString("summary"),
            signals = signals,
            advice = o.optJSONArray("advice").toStringList(20),
            matchedScamType = o.optNullableString("scamType"),
            inputPreview = o.optString("preview"),
            extractedUrls = o.optJSONArray("urls").toStringList(10),
            extractedPhones = o.optJSONArray("phones").toStringList(10),
            analyzedAt = o.getLong("at"),
            elapsedMs = o.optLong("ms"),
        )
    }

    companion object {
        const val MAX_ITEMS = 100
    }
}

private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default

private fun JSONArray?.toStringList(max: Int): List<String> {
    if (this == null) return emptyList()
    return (0 until minOf(length(), max)).mapNotNull { optString(it, null) }
}

private fun JSONObject.optNullableString(key: String): String? =
    if (isNull(key)) null else optString(key, null)
