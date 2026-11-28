package com.scamsense.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Link
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.scamsense.app.data.AppSettings
import com.scamsense.app.livecall.LiveSelfTest
import com.scamsense.app.livecall.ShizukuGate
import kotlinx.coroutines.launch
import com.scamsense.app.livecall.WhisperModel
import com.scamsense.app.livecall.WhisperModels
import com.scamsense.app.livecall.WhisperNative
import com.scamsense.app.ui.components.BigButton
import com.scamsense.app.ui.components.BigOutlinedButton
import com.scamsense.app.ui.components.SectionTitle
import com.scamsense.app.ui.components.enterUp
import com.scamsense.app.ui.theme.riskColors
import com.scamsense.core.engine.RiskLevel

/** Readiness of each prerequisite; separated from Android calls so previews/tests can pass it in. */
data class LiveGuardState(
    val supported: Boolean,
    val shizuku: ShizukuGate.Status,
    val phonePermission: Boolean,
    val modelInstalled: Boolean,
    val nativeAvailable: Boolean,
    /** Exempt from battery optimisation: Android 12+ then lets the listener start by itself. */
    val backgroundAllowed: Boolean = true,
    /** Built-in mode: ScamSense paired with Wireless debugging itself (no Shizuku app). */
    val builtInPaired: Boolean = false,
    /** Built-in mode can switch Wireless debugging back on by itself (after reboots). */
    val autoEnable: Boolean = false,
) {
    val shellReady get() = builtInPaired || shizuku == ShizukuGate.Status.READY
    val ready get() = supported && shellReady && phonePermission && modelInstalled && nativeAvailable
}

@Composable
fun rememberLiveGuardState(settings: AppSettings, refreshKey: Any?): LiveGuardState {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    return remember(tick, refreshKey, settings.liveModel) {
        LiveGuardState(
            supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
            shizuku = ShizukuGate.status(context),
            phonePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED,
            modelInstalled = WhisperModels.isInstalled(context, WhisperModel.byId(settings.liveModel)),
            nativeAvailable = WhisperNative.isAvailable,
            backgroundAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                com.scamsense.app.livecall.LiveCallService.isBackgroundStartAllowed(context),
            builtInPaired = com.scamsense.app.livecall.adb.BuiltInShell.isPaired(context),
            autoEnable = com.scamsense.app.livecall.adb.BuiltInShell.canToggleWirelessDebugging(context),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveGuardSection(
    settings: AppSettings,
    state: LiveGuardState,
    downloadProgress: Float?,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onDownloadModel: (WhisperModel) -> Unit,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val phoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onRefresh() }

    com.scamsense.app.ui.components.StepHeader(if (com.scamsense.app.Features.AI_CALL_ASSISTANT) 3 else 2, "ฟังระหว่างคุย (ทดลอง)", done = settings.liveGuardEnabled && state.ready,
        modifier = Modifier.enterUp(5))
    Card(
        modifier = Modifier.enterUp(5),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(
                "เมื่อรับสายจากเบอร์ที่ไม่อยู่ในรายชื่อ ScamSense จะฟังเสียงปลายสาย แปลงเป็นข้อความในเครื่อง " +
                    "ถ้าเจอคำของมิจฉาชีพจะเตือนทันที หรือวางสายให้ ไม่มีการบันทึกเสียงและไม่ส่งเสียงออกนอกเครื่อง",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!state.supported) {
                Spacer(Modifier.height(10.dp))
                Text("ต้องใช้ Android 11 ขึ้นไป", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall)
                return@Column
            }
            Spacer(Modifier.height(14.dp))

            // ---- connection to the shell (built-in pairing first, Shizuku as an alternative)
            BuiltInPairing(state, onRefresh)
            if (!state.builtInPaired) {
                var showShizuku by remember { mutableStateOf(state.shizuku == ShizukuGate.Status.READY) }
                TextButton(onClick = { showShizuku = !showShizuku }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (showShizuku) "ซ่อนวิธีใช้แอป Shizuku" else "หรือใช้แอป Shizuku แทน (ทางเลือก)", style = MaterialTheme.typography.labelLarge)
                }
                if (showShizuku) {
                    val shizukuOk = state.shizuku == ShizukuGate.Status.READY
                    Check(Icons.Rounded.Terminal, "Shizuku", shizukuOk, when (state.shizuku) {
                        ShizukuGate.Status.READY -> "พร้อมใช้งาน"
                        ShizukuGate.Status.NOT_INSTALLED -> "ยังไม่ได้ติดตั้ง"
                        ShizukuGate.Status.NOT_RUNNING -> "ยังไม่ได้เปิด — ต้องเปิดใหม่ทุกครั้งที่รีสตาร์ทเครื่อง"
                        ShizukuGate.Status.NO_PERMISSION -> "ยังไม่ได้อนุญาตให้ ScamSense ใช้"
                    })
                    when (state.shizuku) {
                        ShizukuGate.Status.NOT_INSTALLED -> Action("ติดตั้ง Shizuku จาก Play Store (ฟรี)") { ShizukuGate.openInstallPage(context) }
                        ShizukuGate.Status.NOT_RUNNING -> Action("เปิดแอป Shizuku") { ShizukuGate.launchShizukuApp(context) }
                        ShizukuGate.Status.NO_PERMISSION -> Action("อนุญาตให้ใช้ Shizuku") { ShizukuGate.requestPermission { onRefresh() } }
                        ShizukuGate.Status.READY -> Unit
                    }
                    if (state.shizuku != ShizukuGate.Status.READY) ShizukuGuide(onOpenDevOptions = { ShizukuGate.openDeveloperOptions(context) })
                }
            }
            Spacer(Modifier.height(12.dp))

            // ---- phone state permission
            Check(Icons.Rounded.PhoneInTalk, "รู้ว่ารับสายหรือวางสายแล้ว", state.phonePermission,
                if (state.phonePermission) "อนุญาตแล้ว" else "ต้องอนุญาต เพื่อเริ่มฟังตอนรับสาย")
            if (!state.phonePermission) Action("อนุญาต") { phoneLauncher.launch(Manifest.permission.READ_PHONE_STATE) }
            Spacer(Modifier.height(12.dp))

            // ---- background start (battery optimisation exemption)
            Check(Icons.Rounded.BatteryChargingFull, "เริ่มฟังเองได้ทันทีที่มีสาย", state.backgroundAllowed,
                if (state.backgroundAllowed) "อนุญาตแล้ว" else "แนะนำ: ถ้าไม่อนุญาต ต้องแตะแจ้งเตือนเพื่อเริ่มฟังทุกครั้ง")
            if (!state.backgroundAllowed) Action("อนุญาตให้ทำงานเบื้องหลัง") {
                // Sideloaded safety feature (not on Play): the exemption is what lets Android 12+
                // start the call listener when a scam call arrives.
                @android.annotation.SuppressLint("BatteryLife")
                val ok = runCatching {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.fromParts("package", context.packageName, null)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                ok.onFailure { onRefresh() }
            }
            Spacer(Modifier.height(12.dp))

            // ---- speech model
            val model = WhisperModel.byId(settings.liveModel)
            Check(Icons.Rounded.Hearing, "โมเดลแปลงเสียงภาษาไทย", state.modelInstalled && state.nativeAvailable, when {
                !state.nativeAvailable -> "เครื่องนี้ไม่รองรับ"
                state.modelInstalled -> "ติดตั้งแล้ว (${WhisperModels.file(context, model).length() / 1_000_000} MB)"
                else -> "ยังไม่ได้ดาวน์โหลด"
            })
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WhisperModel.entries.forEach { m ->
                    com.scamsense.app.ui.components.ChoicePill(
                        when (m) { WhisperModel.SMALL -> "แม่นยำ"; WhisperModel.BASE -> "เร็ว"; WhisperModel.THAI -> "ภาษาไทย" },
                        selected = m == model,
                        onClick = { onUpdate { it.copy(liveModel = m.id) } },
                        modifier = Modifier.weight(1f),
                        horizontalPadding = 6.dp,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(model.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!state.modelInstalled && state.nativeAvailable) {
                Spacer(Modifier.height(8.dp))
                if (downloadProgress != null) {
                    LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)))
                    Text(
                        when {
                            model.hfRepo == null || downloadProgress < 0.75f -> "กำลังดาวน์โหลด ${(downloadProgress * 100).toInt()}%"
                            downloadProgress < 0.94f -> "กำลังแปลงโมเดล ${(downloadProgress * 100).toInt()}%"
                            else -> "กำลังย่อโมเดลให้เร็วขึ้น อาจใช้เวลาหลายนาที อย่าปิดแอป…"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    BigOutlinedButton(
                        if (model.hfRepo != null) "ดาวน์โหลดและแปลงโมเดลภาษาไทย (ใช้ Wi-Fi)" else "ดาวน์โหลดโมเดล (${model.approxMb} MB, แนะนำ Wi-Fi)",
                        icon = Icons.Rounded.Download, onClick = { onDownloadModel(model) },
                    )
                }
            }
            // Optional, for people who converted the model on a PC: kept small and last so the
            // one-tap download above is the obvious path.
            if (model.hfRepo != null && state.nativeAvailable && downloadProgress == null) {
                ImportModel(installed = state.modelInstalled, onDone = onRefresh)
            }
            if (state.shellReady && state.modelInstalled && state.nativeAvailable) {
                Spacer(Modifier.height(12.dp))
                SelfTest(model, onUseFast = { onUpdate { it.copy(liveModel = WhisperModel.BASE.id) } })
            }
            Spacer(Modifier.height(14.dp))

            // ---- switches
            Toggle(
                "เปิดฟังระหว่างคุย",
                if (state.ready) "พร้อมทำงานกับสายถัดไปจากเบอร์ที่ไม่รู้จัก" else "ทำข้อด้านบนให้ครบก่อน และเปิด \"กรองสาย\" ในข้อ 1",
                settings.liveGuardEnabled && state.ready, enabled = state.ready,
            ) { v -> onUpdate { it.copy(liveGuardEnabled = v) } }
            Toggle("วางสายให้อัตโนมัติเมื่อแน่ใจว่าเป็นมิจฉาชีพ", "นับถอยหลัง 4 วินาที แตะยกเลิกได้ ถ้าปิดไว้จะเตือนอย่างเดียว",
                settings.liveAutoHangUp, enabled = true) { v -> onUpdate { it.copy(liveAutoHangUp = v) } }
            Spacer(Modifier.height(10.dp))
            Text(
                "ส่วนดึงเสียงสายดัดแปลงจาก CallVault และ ShizuCallRecorder (GPL-3.0) • แปลงเสียงด้วย whisper.cpp (MIT)",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val GUIDE_STEPS = listOf(
    "ติดตั้งแอป Shizuku จาก Play Store (ปุ่มด้านบน)",
    "เปิดโหมดนักพัฒนา: ตั้งค่า → เกี่ยวกับโทรศัพท์ → แตะ \"หมายเลขบิลด์\" 7 ครั้ง",
    "เชื่อมต่อ Wi‑Fi (ไม่ต้องมีอินเทอร์เน็ตก็ได้)",
    "เปิดแอป Shizuku → \"เริ่มผ่าน Wireless debugging\" → \"จับคู่\" ระบบจะขึ้นการแจ้งเตือนรอรหัส",
    "ไปที่ ตัวเลือกสำหรับนักพัฒนา → การแก้ไขข้อบกพร่องแบบไร้สาย → เปิด แล้วแตะ \"จับคู่อุปกรณ์ด้วยรหัสจับคู่\"",
    "พิมพ์รหัส 6 หลักลงในการแจ้งเตือนของ Shizuku",
    "กลับไปแอป Shizuku กด \"เริ่ม\" แล้วกลับมาที่ ScamSense กด \"อนุญาตให้ใช้ Shizuku\"",
)

/** Step-by-step Thai guide for the person setting Shizuku up (usually a family member). */
@Composable
private fun ShizukuGuide(onOpenDevOptions: () -> Unit) {
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    Spacer(Modifier.height(8.dp))
    Text(
        if (open) "ซ่อนวิธีเปิด Shizuku" else "ดูวิธีเปิด Shizuku ทีละขั้น",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
            .toggleable(open, role = Role.Button) { open = it }.padding(vertical = 10.dp),
    )
    if (!open) return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp),
    ) {
        GUIDE_STEPS.forEachIndexed { i, step ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.width(10.dp))
                Text(step, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        BigOutlinedButton("เปิดตัวเลือกสำหรับนักพัฒนา", onClick = onOpenDevOptions)
        Spacer(Modifier.height(8.dp))
        Text(
            "สำคัญ: หลังปิด-เปิดเครื่องใหม่ ต้องเปิดแอป Shizuku แล้วกด \"เริ่ม\" อีกครั้ง (Android 13 ขึ้นไปตั้งให้เริ่มเองได้ในการตั้งค่าของ Shizuku เมื่อต่อ Wi‑Fi ที่เชื่อถือ) " +
                "• มือถือ OPPO / OnePlus / Realme: เปิด \"Disable system optimization\" ในตัวเลือกสำหรับนักพัฒนาก่อน",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Check(icon: ImageVector, title: String, ok: Boolean, subtitle: String) {
    val c = riskColors(if (ok) RiskLevel.SAFE else RiskLevel.SUSPICIOUS)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.container), contentAlignment = Alignment.Center) {
            Icon(if (ok) Icons.Rounded.CheckCircle else icon, null, tint = c.main, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Action(label: String, onClick: () -> Unit) {
    Spacer(Modifier.height(8.dp))
    BigButton(label, onClick = onClick)
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * Lets the family check the whole chain without making a call: speak into the phone,
 * see the words it understood, how long that took, and the scam score.
 */
@Composable
private fun SelfTest(model: WhisperModel, onUseFast: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf("") }
    var outcome by remember(model) { mutableStateOf<LiveSelfTest.Outcome?>(null) }
    BigOutlinedButton(
        if (running) phase.ifBlank { "กำลังเตรียม…" } else "ทดสอบการฟัง: กดแล้วพูดใส่มือถือ 6 วินาที",
        icon = Icons.Rounded.Hearing,
        onClick = {
            if (running || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@BigOutlinedButton
            running = true
            outcome = null
            scope.launch {
                outcome = runCatching { LiveSelfTest.run(context.applicationContext, model) { p -> scope.launch { phase = p } } }
                    .getOrElse { LiveSelfTest.Outcome.Failed("ทดสอบไม่สำเร็จ: ${it.javaClass.simpleName}") }
                running = false
                phase = ""
            }
        },
    )
    Text("ลองพูด เช่น \"ขอรหัส OTP ด่วน แล้วโอนเงินเข้าบัญชีปลอดภัย\"",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    when (val o = outcome) {
        null -> Unit
        is LiveSelfTest.Outcome.Failed -> Text(o.reason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        is LiveSelfTest.Outcome.Heard -> {
            val slow = o.seconds > o.audioSeconds * 1.5f
            Spacer(Modifier.height(6.dp))
            Text("ได้ยินว่า: \u201c${o.text.ifBlank { "(ไม่เป็นคำพูด)" }}\u201d", style = MaterialTheme.typography.bodyMedium)
            Text("แปลงเสียงใช้เวลา ${"%.1f".format(o.seconds)} วินาที" + if (slow) " — ช้าเกินไปสำหรับระหว่างคุย" else " — เร็วพอ",
                style = MaterialTheme.typography.bodySmall,
                color = if (slow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            o.result?.let { r ->
                Text("ผลตรวจ: ${r.score}/100 ${when (r.level) { RiskLevel.DANGEROUS -> "อันตราย"; RiskLevel.SUSPICIOUS -> "น่าสงสัย"; else -> "ปลอดภัย" }}",
                    style = MaterialTheme.typography.titleSmall)
            }
            if (slow && model == WhisperModel.SMALL) Action("เปลี่ยนเป็นโมเดล \"เร็ว\"", onUseFast)
        }
    }
}

/**
 * Thonburian Whisper (Thai-trained) has no public whisper.cpp build in a phone-sized
 * variant, so the family converts it on a PC (tools/convert_thonburian.py) and imports the
 * .bin here; the app shrinks it to q5_1 on the phone.
 */
@Composable
private fun ImportModel(installed: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        error = null
        phase = "กำลังเตรียม…"
        scope.launch {
            val r = WhisperModels.importModel(context.applicationContext, uri) { p -> scope.launch { phase = p } }
            phase = null
            r.onFailure { error = "นำเข้าไม่สำเร็จ: ไฟล์ต้องเป็นโมเดล whisper.cpp (.bin) ที่แปลงด้วยสคริปต์ของ ScamSense" }
            onDone()
        }
    }
    if (phase != null) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)))
        Text(phase.orEmpty(), style = MaterialTheme.typography.bodySmall)
        return
    }
    TextButton(
        onClick = { picker.launch(arrayOf("application/octet-stream", "*/*")) },
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Text(
            if (installed) "สำหรับผู้พัฒนา: นำเข้าไฟล์ .bin แทนของเดิม" else "สำหรับผู้พัฒนา: มีไฟล์ .bin ที่แปลงไว้แล้ว? นำเข้าเอง (ไม่จำเป็น)",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}

private val PAIR_STEPS = listOf(
    "เปิดโหมดนักพัฒนา: ตั้งค่า → เกี่ยวกับโทรศัพท์ → แตะ \"หมายเลขบิลด์\" 7 ครั้ง",
    "ต่อ Wi‑Fi (ไม่ต้องมีอินเทอร์เน็ตก็ได้)",
    "กดปุ่ม \"เริ่มจับคู่\" ด้านล่าง ScamSense จะเปิดหน้าตัวเลือกสำหรับนักพัฒนาให้",
    "เปิด \"การแก้ไขข้อบกพร่องแบบไร้สาย\" แล้วแตะเข้าไป → \"จับคู่อุปกรณ์ด้วยรหัสจับคู่\"",
    "ลากแถบแจ้งเตือนลงมา ในการแจ้งเตือนของ ScamSense พิมพ์รหัส 6 หลัก เว้นวรรค ตามด้วยเลขพอร์ต (ตัวเลขหลัง : ในบรรทัด IP) เช่น 123456 41235 แล้วกดส่ง",
)

/**
 * Built-in pairing: ScamSense connects to the phone's own Wireless debugging, so no Shizuku
 * app is needed. Pairing happens once; afterwards ScamSense reconnects (and switches Wireless
 * debugging on) by itself.
 */
@Composable
private fun BuiltInPairing(state: LiveGuardState, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val pairing by com.scamsense.app.livecall.adb.PairingService.state.collectAsStateWithLifecycle()
    var showLog by remember { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        com.scamsense.app.livecall.adb.PairingService.requestCode(context)
        ShizukuGate.openDeveloperOptions(context)
    }
    LaunchedEffect(pairing) { if (pairing is com.scamsense.app.livecall.adb.PairingService.State.Done) onRefresh() }

    if (state.builtInPaired) {
        Check(Icons.Rounded.Link, "จับคู่ในแอปแล้ว", true,
            if (state.autoEnable) "ไม่ต้องใช้แอป Shizuku • เปิดการเชื่อมต่อให้เองเมื่อมีสาย แม้รีสตาร์ทเครื่อง"
            else "ไม่ต้องใช้แอป Shizuku • ต้องเปิด \"การแก้ไขข้อบกพร่องแบบไร้สาย\" ค้างไว้")
        // Fallback when automatic discovery is slow on a phone: the Wireless debugging page
        // shows "IP address & Port"; the number after ':' is the connect port.
        var portText by remember { mutableStateOf(com.scamsense.app.livecall.adb.BuiltInShell.savedConnectPort(context)?.toString().orEmpty()) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.OutlinedTextField(
                value = portText,
                onValueChange = { v -> portText = v.filter { it.isDigit() }.take(5) },
                label = { Text("พอร์ตเชื่อมต่อ (ถ้าเชื่อมต่อช้า)") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = {
                    portText.toIntOrNull()?.takeIf { it in 1024..65535 }?.let {
                        com.scamsense.app.livecall.adb.BuiltInShell.setConnectPort(context, it)
                        android.widget.Toast.makeText(context, "บันทึกพอร์ตแล้ว", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("บันทึก", style = MaterialTheme.typography.labelLarge) }
        }
        Text("ดูได้ที่ การแก้ไขข้อบกพร่องแบบไร้สาย → \"ที่อยู่ IP และพอร์ต\" ตัวเลขหลังเครื่องหมาย :",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { showLog = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("ดูบันทึกการเชื่อมต่อ", style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = { com.scamsense.app.livecall.adb.BuiltInShell.forget(context); onRefresh() }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("ยกเลิกการจับคู่", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            }
        }
    } else {
        Check(Icons.Rounded.Link, "จับคู่ในแอป (แนะนำ)", false, "ไม่ต้องลงแอปอื่น ทำครั้งเดียว ให้ลูกหลานช่วยได้")
        Spacer(Modifier.height(8.dp))
        PAIR_STEPS.forEachIndexed { i, step ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(24.dp))
                Text(step, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        when (val p = pairing) {
            is com.scamsense.app.livecall.adb.PairingService.State.Working -> {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)))
                Text("กำลังจับคู่… อย่าเพิ่งปิดหน้าตั้งค่า", style = MaterialTheme.typography.bodySmall)
            }
            is com.scamsense.app.livecall.adb.PairingService.State.Failed ->
                Text(p.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            is com.scamsense.app.livecall.adb.PairingService.State.Waiting ->
                Text("รอรหัสจับคู่ในการแจ้งเตือนของ ScamSense…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            else -> Unit
        }
        Action("เริ่มจับคู่") {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                com.scamsense.app.livecall.adb.PairingService.requestCode(context)
                ShizukuGate.openDeveloperOptions(context)
            }
        }
        if (pairing is com.scamsense.app.livecall.adb.PairingService.State.Failed) {
            TextButton(onClick = { showLog = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("ดูบันทึกการเชื่อมต่อ (ส่งให้ผู้พัฒนา)", style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    if (showLog) {
        val log = remember { com.scamsense.app.livecall.adb.AdbLog.text().ifBlank { "(ยังไม่มีบันทึก)" } }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text("บันทึกการเชื่อมต่อ") },
            text = {
                androidx.compose.foundation.layout.Box(Modifier.heightIn(max = 360.dp)) {
                    Text(log, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_TEXT, "ScamSense connection log\n$log")
                    runCatching { context.startActivity(android.content.Intent.createChooser(send, "ส่งบันทึก")) }
                }) { Text("ส่งต่อ") }
            },
            dismissButton = { TextButton(onClick = { showLog = false }) { Text("ปิด") } },
        )
    }
}
