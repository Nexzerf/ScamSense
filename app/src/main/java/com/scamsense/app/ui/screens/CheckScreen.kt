package com.scamsense.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.scamsense.app.engine.CheckType
import com.scamsense.app.engine.TextNormalizer
import com.scamsense.app.ui.components.AnalyzingIndicator
import com.scamsense.app.ui.components.BigButton
import com.scamsense.app.util.rememberSpeechInput
import kotlinx.coroutines.launch

private data class CheckCopy(val title: String, val hint: String, val label: String, val examples: List<Pair<String, String>>)

private fun copyFor(type: CheckType) = when (type) {
    CheckType.LINK -> CheckCopy(
        "ตรวจลิงก์", "วางลิงก์ที่ได้รับ ระบบจะตรวจโดยไม่เปิดลิงก์นั้น", "ลิงก์ เช่น https://…",
        listOf(
            "ลิงก์ปลอมธนาคาร" to "https://kbank-verify-account.xyz/login",
            "ลิงก์ย่อ" to "bit.ly/3xRf9Qa",
            "เว็บธนาคารจริง" to "https://www.kasikornbank.com",
        ),
    )
    CheckType.PHONE -> CheckCopy(
        "ตรวจเบอร์โทร", "พิมพ์เบอร์ที่โทรเข้ามาหรือที่อยู่ในข้อความ", "เบอร์โทร เช่น 081-234-5678",
        listOf(
            "เบอร์ +697" to "+697 2345 6789",
            "เบอร์ต่างประเทศ" to "+855 12 345 678",
            "สายด่วน 1441" to "1441",
        ),
    )
    else -> CheckCopy(
        "ตรวจข้อความ", "วางข้อความจาก SMS, LINE หรือพูดสิ่งที่ปลายสายบอก", "ข้อความที่ได้รับ",
        listOf(
            "ตัวอย่าง: อ้างเป็นตำรวจ" to "ติดต่อจากสถานีตำรวจ บัญชีของท่านพัวพันคดีฟอกเงิน ต้องโอนเงินไปบัญชีปลอดภัยเพื่อตรวจสอบ ห้ามวางสาย ห้ามบอกใคร",
            "ตัวอย่าง: พัสดุ" to "พัสดุของท่านจัดส่งไม่สำเร็จ กรุณาอัปเดตที่อยู่และชำระค่าธรรมเนียม 25 บาท ที่ https://thpost-delivery.top/pay",
            "ตัวอย่าง: ข้อความปกติ" to "พรุ่งนี้แม่จะไปหาที่บ้านนะ ทำกับข้าวไว้ด้วย",
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CheckScreen(
    type: CheckType,
    autoVoice: Boolean,
    initialText: String?,
    analyzing: Boolean,
    error: String?,
    onAnalyze: (String) -> Unit,
    onClearError: () -> Unit,
    onBack: () -> Unit,
) {
    val copy = remember(type) { copyFor(type) }
    var text by rememberSaveable { mutableStateOf(initialText.orEmpty()) }
    var voiceLaunched by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val speech = rememberSpeechInput { spoken ->
        text = TextNormalizer.sanitizeInput(if (text.isBlank()) spoken else "$text $spoken")
        onClearError()
    }
    fun startVoice() {
        if (!speech.start()) {
            scope.launch { snackbar.showSnackbar("เครื่องนี้ยังไม่รองรับการพูด กรุณาพิมพ์หรือวางข้อความแทน") }
        }
    }
    LaunchedEffect(autoVoice) {
        if (autoVoice && !voiceLaunched) {
            voiceLaunched = true
            startVoice()
        }
    }
    // A new share while this screen is open replaces the text.
    LaunchedEffect(initialText) {
        if (!initialText.isNullOrBlank()) text = initialText
    }

    fun submit() {
        keyboard?.hide()
        onAnalyze(text)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(copy.title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "ย้อนกลับ")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        AnimatedContent(
            targetState = analyzing,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "check",
            modifier = Modifier.padding(padding),
        ) { busy ->
            if (busy) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AnalyzingIndicator() }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(copy.hint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            text = if (it.length > TextNormalizer.MAX_INPUT_CHARS) it.take(TextNormalizer.MAX_INPUT_CHARS) else it
                            if (error != null) onClearError()
                        },
                        label = { Text(copy.label) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = if (type == CheckType.MESSAGE) 180.dp else 72.dp),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        minLines = if (type == CheckType.MESSAGE) 5 else 1,
                        maxLines = if (type == CheckType.MESSAGE) 12 else 3,
                        shape = RoundedCornerShape(20.dp),
                        isError = error != null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = when (type) {
                                CheckType.PHONE -> KeyboardType.Phone
                                CheckType.LINK -> KeyboardType.Uri
                                else -> KeyboardType.Text
                            },
                            imeAction = if (type == CheckType.MESSAGE) ImeAction.Default else ImeAction.Search,
                            autoCorrectEnabled = false,
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { submit() }),
                        trailingIcon = if (text.isNotEmpty()) {
                            {
                                IconButton(onClick = { text = ""; onClearError() }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "ล้างข้อความ")
                                }
                            }
                        } else null,
                    )

                    if (error != null) {
                        Row(Modifier.semantics { liveRegion = LiveRegionMode.Assertive }, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(
                            onClick = {
                                val pasted = readClipboard(context)
                                if (pasted.isNullOrBlank()) {
                                    scope.launch { snackbar.showSnackbar("ไม่มีข้อความที่คัดลอกไว้") }
                                } else {
                                    text = TextNormalizer.sanitizeInput(pasted)
                                    onClearError()
                                }
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Icon(Icons.Rounded.ContentPaste, null)
                            Spacer(Modifier.width(8.dp))
                            Text("วางข้อความ", style = MaterialTheme.typography.labelLarge)
                        }
                        FilledTonalButton(
                            onClick = { startVoice() },
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Icon(Icons.Rounded.Mic, null)
                            Spacer(Modifier.width(8.dp))
                            Text("พูด", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    BigButton("ตรวจสอบเลย", onClick = { submit() }, icon = Icons.Rounded.Search, enabled = text.isNotBlank())

                    Text("ลองตัวอย่าง", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        copy.examples.forEach { (label, sample) ->
                            AssistChip(
                                onClick = { text = sample; onClearError() },
                                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.heightIn(min = 48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface),
                            )
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                }
            }
        }
    }
}

/** Reads the clipboard only when the user taps "paste" (Android 13+ shows its own notice). */
private fun readClipboard(context: Context): String? = try {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
} catch (_: SecurityException) {
    null
}
