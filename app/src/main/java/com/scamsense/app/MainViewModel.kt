package com.scamsense.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.scamsense.app.data.AppSettings
import com.scamsense.app.data.AssistantClient
import com.scamsense.core.api.CallRecordDto
import com.scamsense.core.engine.AnalysisOutcome
import com.scamsense.core.engine.CheckType
import com.scamsense.core.engine.RiskResult
import com.scamsense.core.engine.TextNormalizer
import com.scamsense.core.engine.TransactionInput
import com.scamsense.core.engine.UserRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StartupState(val ready: Boolean = false, val progress: Float = 0f, val status: String = "กำลังเริ่มต้น…")

data class SharedText(val id: Long, val text: String)

class MainViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<AppSettings> = container.settingsRepository.settings
    val history: StateFlow<List<RiskResult>> = container.historyRepository.items

    private val _startup = MutableStateFlow(StartupState())
    val startup: StateFlow<StartupState> = _startup.asStateFlow()

    private val _analyzing = MutableStateFlow(false)
    val analyzing: StateFlow<Boolean> = _analyzing.asStateFlow()

    private val _inputError = MutableStateFlow<String?>(null)
    val inputError: StateFlow<String?> = _inputError.asStateFlow()

    private val _currentResult = MutableStateFlow<RiskResult?>(null)
    val currentResult: StateFlow<RiskResult?> = _currentResult.asStateFlow()

    private val _sharedText = MutableStateFlow<SharedText?>(null)
    val sharedText: StateFlow<SharedText?> = _sharedText.asStateFlow()

    /** Notification tap from the call guard: open the voice check. */
    private val _voiceCheck = Channel<Unit>(Channel.CONFLATED)
    val voiceCheckRequests = _voiceCheck.receiveAsFlow()

    // ---- AI call assistant ----
    private val _assistantCalls = MutableStateFlow<List<CallRecordDto>>(emptyList())
    val assistantCalls: StateFlow<List<CallRecordDto>> = _assistantCalls.asStateFlow()
    private val _assistantBusy = MutableStateFlow(false)
    val assistantBusy: StateFlow<Boolean> = _assistantBusy.asStateFlow()
    private val _assistantMessage = MutableStateFlow<String?>(null)
    val assistantMessage: StateFlow<String?> = _assistantMessage.asStateFlow()

    // ---- live listening model download
    private val _modelDownload = MutableStateFlow<Float?>(null)
    val modelDownload: StateFlow<Float?> = _modelDownload.asStateFlow()

    fun downloadModel(model: com.scamsense.app.livecall.WhisperModel) {
        if (_modelDownload.value != null) return
        viewModelScope.launch {
            _modelDownload.value = 0f
            val r = com.scamsense.app.livecall.WhisperModels.download(container.appContext, model) { p -> _modelDownload.value = p }
            _modelDownload.value = null
            _assistantMessage.value = if (r.isSuccess) "ดาวน์โหลดโมเดลเสร็จแล้ว" else "ดาวน์โหลดไม่สำเร็จ ตรวจสอบอินเทอร์เน็ตแล้วลองใหม่"
        }
    }

    /** One-shot "go to the result screen" events. */
    private val _showResult = Channel<Unit>(Channel.CONFLATED)
    val showResult = _showResult.receiveAsFlow()

    init {
        viewModelScope.launch { startUp() }
    }

    private suspend fun startUp() {
        val began = System.currentTimeMillis()
        step(0.15f, "กำลังเปิดข้อมูลที่เข้ารหัสไว้…")
        runCatching { container.settingsRepository.load() }
        step(0.4f, "กำลังโหลดประวัติการตรวจ…")
        runCatching { container.historyRepository.load() }
        step(0.7f, "กำลังเตรียมฐานข้อมูลกลโกง…")
        withContext(Dispatchers.Default) { runCatching { container.analyzer.warmUp() } }
        step(1f, "พร้อมปกป้องคุณแล้ว")
        // Keep the brand screen visible briefly so the transition feels deliberate, not janky.
        val remaining = MIN_SPLASH_MS - (System.currentTimeMillis() - began)
        if (remaining > 0) delay(remaining)
        _startup.value = _startup.value.copy(ready = true)
    }

    private suspend fun step(progress: Float, status: String) {
        _startup.value = StartupState(ready = false, progress = progress, status = status)
        delay(120)
    }

    fun clearInputError() { _inputError.value = null }

    fun analyze(type: CheckType, text: String) {
        if (_analyzing.value) return
        _inputError.value = null
        viewModelScope.launch {
            _analyzing.value = true
            val began = System.currentTimeMillis()
            val s = settings.value
            val outcome = withContext(Dispatchers.Default) {
                runCatching { container.analyzer.analyze(type, text, s.toRules()) }
                    .getOrElse { AnalysisOutcome.Invalid("ขออภัย เกิดข้อผิดพลาดในการตรวจ กรุณาลองใหม่") }
            }
            holdForAnimation(began)
            when (outcome) {
                is AnalysisOutcome.Invalid -> _inputError.value = outcome.message
                is AnalysisOutcome.Success -> publish(outcome.result)
            }
            _analyzing.value = false
        }
    }

    fun analyzeTransaction(input: TransactionInput) {
        if (_analyzing.value) return
        viewModelScope.launch {
            _analyzing.value = true
            val began = System.currentTimeMillis()
            val s = settings.value
            val result = withContext(Dispatchers.Default) {
                runCatching { container.analyzer.analyzeTransaction(input, s.toRules()) }.getOrNull()
            }
            holdForAnimation(began)
            if (result != null) publish(result) else _inputError.value = "ขออภัย เกิดข้อผิดพลาด กรุณาลองใหม่"
            _analyzing.value = false
        }
    }

    private suspend fun publish(result: RiskResult) {
        _currentResult.value = result
        if (settings.value.saveHistory) container.historyRepository.add(result)
        _showResult.send(Unit)
    }

    /** Analysis takes a few ms; a short, consistent animation reassures users it "really checked". */
    private suspend fun holdForAnimation(began: Long) {
        val remaining = MIN_ANALYSIS_MS - (System.currentTimeMillis() - began)
        if (remaining > 0) delay(remaining)
    }

    fun openHistoryItem(result: RiskResult) {
        _currentResult.value = result
        _showResult.trySend(Unit)
    }

    fun onSharedText(raw: CharSequence?) {
        val text = TextNormalizer.sanitizeInput(raw)
        if (text.isNotEmpty()) _sharedText.value = SharedText(System.nanoTime(), text)
    }

    fun consumeSharedText() { _sharedText.value = null }

    // ---------------------------------------------------------------- settings / history

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { container.settingsRepository.update(transform) }
    }

    fun blockNumber(display: String) = updateSettings { s ->
        val digits = display.filter { it.isDigit() || it == '+' }
        if (digits.isEmpty() || s.blockedNumbers.any { it.filter { c -> c.isDigit() || c == '+' } == digits }) s
        else s.copy(blockedNumbers = (s.blockedNumbers + display).takeLast(200))
    }

    fun deleteHistoryItem(analyzedAt: Long) {
        viewModelScope.launch { container.historyRepository.remove(analyzedAt) }
    }

    fun clearHistory() {
        viewModelScope.launch { container.historyRepository.clear() }
    }

    fun requestVoiceCheck() { _voiceCheck.trySend(Unit) }

    fun clearAssistantMessage() { _assistantMessage.value = null }

    fun pairAssistant(server: String, code: String) {
        if (_assistantBusy.value) return
        val client = container.assistantClient
        val base = client.normaliseServer(server)
        if (base == null) { _assistantMessage.value = "ที่อยู่เซิร์ฟเวอร์ต้องขึ้นต้นด้วย https://"; return }
        if (code.trim().length < 6) { _assistantMessage.value = "รหัสจับคู่ไม่ครบ"; return }
        viewModelScope.launch {
            _assistantBusy.value = true
            when (val r = client.pair(base, code)) {
                is AssistantClient.Result.Ok -> {
                    container.settingsRepository.update {
                        it.copy(
                            assistantServer = base,
                            assistantToken = r.value.token,
                            assistantNumber = r.value.assistantNumber,
                            assistantName = r.value.subscriberName,
                            forwardingConfirmed = false,
                        )
                    }
                    _assistantMessage.value = "จับคู่สำเร็จ ขั้นต่อไป: ตั้งค่าโอนสาย"
                }
                is AssistantClient.Result.Error -> _assistantMessage.value = r.message
            }
            _assistantBusy.value = false
        }
    }

    fun refreshAssistantCalls() {
        val s = settings.value
        if (!s.assistantPaired || _assistantBusy.value) return
        viewModelScope.launch {
            _assistantBusy.value = true
            when (val r = container.assistantClient.calls(s.assistantServer, s.assistantToken)) {
                is AssistantClient.Result.Ok -> _assistantCalls.value = r.value
                is AssistantClient.Result.Error -> _assistantMessage.value = r.message
            }
            _assistantBusy.value = false
        }
    }

    fun deleteAssistantCalls() {
        val s = settings.value
        if (!s.assistantPaired) return
        viewModelScope.launch {
            when (val r = container.assistantClient.deleteCalls(s.assistantServer, s.assistantToken)) {
                is AssistantClient.Result.Ok -> { _assistantCalls.value = emptyList(); _assistantMessage.value = "ลบประวัติสายบนเซิร์ฟเวอร์แล้ว" }
                is AssistantClient.Result.Error -> _assistantMessage.value = r.message
            }
        }
    }

    /** Forget the pairing locally. Call forwarding must also be cancelled on the SIM. */
    fun unpairAssistant() {
        _assistantCalls.value = emptyList()
        updateSettings {
            it.copy(assistantServer = "", assistantToken = "", assistantNumber = "", assistantName = "", forwardingConfirmed = false)
        }
    }

    private fun AppSettings.toRules() = UserRules(customBlockWords, blockedNumbers)

    companion object {
        private const val MIN_SPLASH_MS = 1_100L
        private const val MIN_ANALYSIS_MS = 650L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ScamSenseApplication
                MainViewModel(app.container)
            }
        }
    }
}
