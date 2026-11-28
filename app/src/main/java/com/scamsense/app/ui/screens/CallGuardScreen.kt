package com.scamsense.app.ui.screens

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.rounded.PhoneLocked
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PhoneForwarded
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneDisabled
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import com.scamsense.app.ui.components.GlowHeader
import com.scamsense.app.ui.components.enterUp
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.scamsense.app.data.AppSettings
import com.scamsense.app.ui.components.BigButton
import com.scamsense.app.ui.components.BigOutlinedButton
import com.scamsense.app.ui.components.SectionTitle
import com.scamsense.app.ui.theme.riskColors
import com.scamsense.core.calls.CallGuard
import com.scamsense.core.engine.RiskLevel

/** Whether ScamSense currently holds the system call-screening role. */
fun isCallScreeningEnabled(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val rm = context.getSystemService(RoleManager::class.java) ?: return false
    return rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallGuardScreen(
    settings: AppSettings,
    assistantBusy: Boolean,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onPair: (server: String, code: String) -> Unit,
    onUnpair: () -> Unit,
    onDialCode: (String) -> Unit,
    onOpenCallLog: () -> Unit,
    onBack: () -> Unit,
    modelDownload: Float? = null,
    onDownloadModel: (com.scamsense.app.livecall.WhisperModel) -> Unit = {},
    liveStateOverride: LiveGuardState? = null,
) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val screeningOn = remember(refresh) { isCallScreeningEnabled(context) }
    val notificationsOn = remember(refresh) {
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    Scaffold(
        topBar = {
            GlowHeader(
                "ป้องกันสายโทร", onBack,
                subtitle = "กรองสายเบอร์อันตราย และฟังระหว่างคุย",
                icon = Icons.Rounded.PhoneLocked,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProgressOverview(
                listOf(
                    "กรองสาย" to screeningOn,
                    "ฟังสาย" to settings.liveGuardEnabled,
                ),
                Modifier.padding(top = 8.dp).enterUp(0),
            )
            Text(
                "ตรวจเฉพาะสายจากเบอร์ที่ไม่อยู่ในรายชื่อ สายจากคนในรายชื่อผู้ติดต่อจะดังตามปกติเสมอ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.enterUp(1),
            )

            // ---------------------------------------------------------------- step 1
            com.scamsense.app.ui.components.StepHeader(1, "เปิดการกรองสาย", done = screeningOn, modifier = Modifier.enterUp(2))
            GuardCard(Modifier.enterUp(2)) {
                StatusRow(Icons.Rounded.Shield, "กรองสายก่อนเครื่องดัง", screeningOn,
                    if (screeningOn) "เปิดอยู่ — บล็อกเบอร์อันตรายให้อัตโนมัติ" else "ยังไม่ได้เปิด")
                if (!screeningOn) {
                    Spacer(Modifier.size(12.dp))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        BigButton("เปิดการกรองสาย", icon = Icons.Rounded.Shield, onClick = {
                            val rm = context.getSystemService(RoleManager::class.java)
                            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                                roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                            }
                        })
                    } else {
                        Text("ต้องใช้ Android 10 ขึ้นไป เครื่องนี้ยังใช้การตรวจเบอร์ด้วยตัวเองได้ที่หน้าหลัก",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.size(14.dp))
                StatusRow(Icons.Rounded.NotificationsActive, "การแจ้งเตือน", notificationsOn,
                    if (notificationsOn) "เปิดอยู่" else "เปิดเพื่อให้รู้ว่าบล็อกสายใดไป")
                if (!notificationsOn && Build.VERSION.SDK_INT >= 33) {
                    Spacer(Modifier.size(12.dp))
                    BigOutlinedButton("อนุญาตการแจ้งเตือน", icon = Icons.Rounded.NotificationsActive,
                        onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) })
                }
            }

            Text("ตัวเลือกการกรองสาย", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp).enterUp(3))
            GuardCard(Modifier.enterUp(3)) {
                ToggleRow("เตือนเมื่อมีสายจากเบอร์ไม่รู้จัก", "ขึ้นข้อความเตือนพร้อมวิธีสังเกตมิจฉาชีพ", settings.callWarnings) { v ->
                    onUpdate { it.copy(callWarnings = v) }
                }
                Spacer(Modifier.size(10.dp))
                ToggleRow(
                    "บล็อกสายจากต่างประเทศทั้งหมด",
                    "ระวัง: ถ้ามีลูกหลานอยู่ต่างประเทศ ให้บันทึกเบอร์เขาในรายชื่อผู้ติดต่อก่อน",
                    settings.blockInternational,
                ) { v -> onUpdate { it.copy(blockInternational = v) } }
                Spacer(Modifier.size(6.dp))
                Text("เบอร์ +697 / +698 และเบอร์ที่คุณรายงานไว้ จะถูกบล็อกเสมอ",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // ---------------------------------------------------------------- step 2: AI assistant
            if (com.scamsense.app.Features.AI_CALL_ASSISTANT) {
                com.scamsense.app.ui.components.StepHeader(2, "ผู้ช่วย AI รับสายแทน", done = settings.assistantPaired, modifier = Modifier.enterUp(4))
                if (!settings.assistantPaired) {
                    PairCard(assistantBusy, onPair)
                } else {
                    AssistantCard(settings, onUpdate, onDialCode, onOpenCallLog, onUnpair)
                }
            }

            // ---------------------------------------------------------------- step 3: live listening
            val liveState = liveStateOverride ?: rememberLiveGuardState(settings, refresh)
            LiveGuardSection(settings, liveState, modelDownload, onUpdate, onDownloadModel, onRefresh = { refresh++ })
            Spacer(Modifier.size(12.dp))
        }
    }
}

@Composable
private fun PairCard(busy: Boolean, onPair: (String, String) -> Unit) {
    var server by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    GuardCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.SmartToy, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                "สายจากเบอร์ที่ไม่รู้จักจะถูกส่งให้ผู้ช่วย AI รับก่อน ผู้ช่วยจะถามธุระ แปลงเสียงเป็นข้อความ แล้วตรวจหาคำของมิจฉาชีพ " +
                    "ถ้าอันตราย ผู้ช่วยจะวางสายให้และส่ง SMS เตือนคุณกับลูกหลาน ถ้าปลอดภัยจะโอนสายมาให้คุณ",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.size(12.dp))
        Text("ให้ลูกหลานหรือผู้ดูแลระบบเป็นคนตั้งค่า", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.size(8.dp))
        OutlinedTextField(
            value = server, onValueChange = { server = it.take(200) },
            label = { Text("ที่อยู่เซิร์ฟเวอร์ (https://…)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(8.dp))
        OutlinedTextField(
            value = code, onValueChange = { v -> code = v.filter { it.isLetterOrDigit() }.take(12).uppercase() },
            label = { Text("รหัสจับคู่") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(12.dp))
        BigButton(if (busy) "กำลังจับคู่…" else "จับคู่กับผู้ช่วย", enabled = !busy && server.isNotBlank() && code.length >= 6,
            onClick = { onPair(server, code) }, icon = Icons.Rounded.SmartToy)
    }
}

@Composable
private fun AssistantCard(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onDialCode: (String) -> Unit,
    onOpenCallLog: () -> Unit,
    onUnpair: () -> Unit,
) {
    val number = settings.assistantNumber
    GuardCard {
        StatusRow(Icons.Rounded.SmartToy, "จับคู่แล้ว: ${settings.assistantName.ifBlank { "ผู้ช่วย AI" }}", true, "เบอร์ผู้ช่วย $number")
        Spacer(Modifier.size(16.dp))
        Text("ตั้งค่าโอนสายในซิม", style = MaterialTheme.typography.titleSmall)
        Text("กดแต่ละปุ่ม แล้วกดโทรในหน้าจอโทรศัพท์ เครือข่ายจะตอบกลับว่าตั้งค่าสำเร็จ",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(10.dp))
        BigOutlinedButton("ก. โอนสายเมื่อไม่ว่าง/ถูกปฏิเสธ (จำเป็น)", icon = Icons.AutoMirrored.Rounded.PhoneForwarded,
            onClick = { onDialCode(CallGuard.ForwardingCodes.busy(number)) })
        Spacer(Modifier.size(8.dp))
        BigOutlinedButton("ข. โอนสายเมื่อไม่รับสาย (แนะนำ)", icon = Icons.AutoMirrored.Rounded.PhoneForwarded,
            onClick = { onDialCode(CallGuard.ForwardingCodes.noAnswer(number)) })
        Spacer(Modifier.size(8.dp))
        BigOutlinedButton("ค. โอนสายเมื่อปิดเครื่อง (แนะนำ)", icon = Icons.AutoMirrored.Rounded.PhoneForwarded,
            onClick = { onDialCode(CallGuard.ForwardingCodes.unreachable(number)) })
        Spacer(Modifier.size(10.dp))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp)
                .toggleable(settings.forwardingConfirmed, role = Role.Checkbox) { v -> onUpdate { it.copy(forwardingConfirmed = v) } },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = settings.forwardingConfirmed, onCheckedChange = null)
            Spacer(Modifier.width(8.dp))
            Text("ตั้งค่าโอนสายข้อ ก. เรียบร้อยแล้ว", style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.size(6.dp))
        ToggleRow(
            "ส่งสายจากเบอร์ไม่รู้จักให้ผู้ช่วยรับก่อน",
            if (settings.forwardingConfirmed) "สายจะไม่ดังที่เครื่อง จนกว่าผู้ช่วยตรวจว่าปลอดภัย"
            else "ต้องตั้งค่าโอนสายข้อ ก. ก่อน ไม่อย่างนั้นสายที่ถูกปฏิเสธจะหายไปเฉย ๆ",
            settings.sendUnknownToAssistant,
        ) { v -> onUpdate { it.copy(sendUnknownToAssistant = v) } }
        Spacer(Modifier.size(14.dp))
        BigButton("ดูสายที่ผู้ช่วยรับแทน", icon = Icons.Rounded.History, onClick = onOpenCallLog)
        Spacer(Modifier.size(8.dp))
        BigOutlinedButton("ยกเลิกการโอนสายทั้งหมด", icon = Icons.Rounded.PhoneDisabled,
            contentColor = riskColors(RiskLevel.SUSPICIOUS).main, onClick = {
                onUpdate { it.copy(forwardingConfirmed = false) }
                onDialCode(CANCEL_ALL_CONDITIONAL_FORWARDING)
            })
        Spacer(Modifier.size(8.dp))
        BigOutlinedButton("ยกเลิกการจับคู่", icon = Icons.Rounded.LinkOff,
            contentColor = riskColors(RiskLevel.DANGEROUS).main, onClick = onUnpair)
    }
}

/** GSM code that deactivates all conditional call forwarding (busy / no answer / unreachable). */
private const val CANCEL_ALL_CONDITIONAL_FORWARDING = "##004#"

@Composable
private fun GuardCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) { Column(Modifier.fillMaxWidth().padding(18.dp)) { content() } }
}

@Composable
private fun StatusRow(icon: ImageVector, title: String, ok: Boolean, subtitle: String) {
    val c = riskColors(if (ok) RiskLevel.SAFE else RiskLevel.SUSPICIOUS)
    Row(verticalAlignment = Alignment.CenterVertically) {
        com.scamsense.app.ui.components.IconChip(if (ok) Icons.Rounded.CheckCircle else icon, c.main)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** "Ready 2/3" overview with a segmented progress bar and one chip per protection layer. */
@Composable
private fun ProgressOverview(steps: List<Pair<String, Boolean>>, modifier: Modifier = Modifier) {
    val done = steps.count { it.second }
    val anim by androidx.compose.animation.core.animateFloatAsState(done / steps.size.toFloat(),
        androidx.compose.animation.core.tween(800), label = "progress")
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ระดับการป้องกัน", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("พร้อม $done/${steps.size}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.size(10.dp))
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(
                    Modifier.fillMaxWidth(anim).height(10.dp).clip(RoundedCornerShape(5.dp))
                        .background(androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(com.scamsense.app.ui.theme.Electric, com.scamsense.app.ui.theme.Mint))),
                )
            }
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                steps.forEach { (name, ok) ->
                    val c = riskColors(if (ok) RiskLevel.SAFE else RiskLevel.SUSPICIOUS)
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(c.container).padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Shield, null, tint = c.main, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(name, style = MaterialTheme.typography.labelSmall, color = c.onContainer, maxLines = 1)
                    }
                }
            }
        }
    }
}
