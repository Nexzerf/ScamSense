package com.scamsense.app.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.PhoneLocked
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scamsense.app.data.AppSettings
import com.scamsense.core.engine.CheckType
import com.scamsense.core.engine.RiskLevel
import com.scamsense.core.engine.RiskResult
import com.scamsense.app.ui.components.ActionTile
import com.scamsense.app.ui.components.SectionTitle
import com.scamsense.app.ui.components.ShieldOrb
import com.scamsense.app.ui.components.enterUp
import com.scamsense.app.ui.components.glowBackdrop
import com.scamsense.app.ui.theme.Lime
import com.scamsense.app.ui.theme.Navy900
import com.scamsense.app.ui.theme.riskColors
import java.util.Calendar

private val tips = listOf(
    "ธนาคารจะไม่ส่งลิงก์ให้กรอกรหัส OTP หรือรหัสผ่าน ทาง SMS หรือไลน์",
    "ตำรวจจริงไม่โทรมาให้โอนเงินเพื่อ \"ตรวจสอบ\" ถ้ามีคนพูดแบบนี้ ให้วางสายทันที",
    "ลูกหลานโทรมาจากเบอร์ใหม่ขอยืมเงิน? โทรกลับเบอร์เดิมที่คุณรู้จักก่อนเสมอ",
    "อย่าติดตั้งแอปจากลิงก์ที่ส่งมาทางข้อความ ให้ติดตั้งจาก Play Store เท่านั้น",
    "การลงทุนที่ \"การันตีกำไรทุกวัน\" ไม่มีอยู่จริง",
    "ถ้าถูกเร่งให้รีบ ยิ่งต้องหยุดคิด และถามคนที่ไว้ใจก่อน",
)

@Composable
fun HomeScreen(
    settings: AppSettings,
    history: List<RiskResult>,
    onCheck: (CheckType, Boolean) -> Unit,
    onTransaction: () -> Unit,
    onCallHotline: () -> Unit,
    onCallCaregiver: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenCallGuard: () -> Unit = {},
) {
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "สวัสดีตอนเช้า"
            in 12..16 -> "สวัสดีตอนบ่าย"
            in 17..20 -> "สวัสดีตอนเย็น"
            else -> "สวัสดี"
        }
    }
    val tip = remember { tips[(System.currentTimeMillis() / 86_400_000L % tips.size).toInt()] }
    val dangerCount = remember(history) { history.count { it.level == RiskLevel.DANGEROUS } }

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Column(Modifier.enterUp(0)) {
            Text(greeting, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "วันนี้มีอะไรน่าสงสัยไหม?",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
        }

        Box(Modifier.enterUp(1)) {
            HeroCard(onSpeak = { onCheck(CheckType.MESSAGE, true) }, onType = { onCheck(CheckType.MESSAGE, false) })
        }

        SectionTitle("เลือกสิ่งที่ต้องการตรวจ", Modifier.enterUp(2))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.enterUp(3)) {
            ActionTile("ข้อความ", "SMS, LINE, แชท", Icons.Rounded.Sms, Color(0xFF2563EB),
                { onCheck(CheckType.MESSAGE, false) }, Modifier.weight(1f))
            ActionTile("ลิงก์", "เว็บที่ถูกส่งมา", Icons.Rounded.Link, Color(0xFF7C3AED),
                { onCheck(CheckType.LINK, false) }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.enterUp(4)) {
            ActionTile("เบอร์โทร", "เบอร์ที่โทรเข้ามา", Icons.Rounded.PhoneInTalk, Color(0xFF0E7490),
                { onCheck(CheckType.PHONE, false) }, Modifier.weight(1f))
            ActionTile("การโอนเงิน", "ก่อนกดโอน ตรวจก่อน", Icons.Rounded.Payments, Color(0xFFB45309),
                onTransaction, Modifier.weight(1f))
        }

        Box(Modifier.enterUp(5)) { CallGuardCard(settings, onOpenCallGuard) }

        Box(Modifier.enterUp(6)) { CaregiverCard(settings, onCallCaregiver, onOpenSettings) }

        Box(Modifier.enterUp(7)) { HotlineCard(onCallHotline) }

        Card(
            modifier = Modifier.enterUp(8),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Lightbulb, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("รู้ทันกลโกงวันนี้", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(tip, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }

        if (history.isNotEmpty()) {
            val danger = riskColors(RiskLevel.DANGEROUS)
            Text(
                "ตรวจไปแล้ว ${history.size} ครั้ง • พบอันตราย $dangerCount ครั้ง",
                style = MaterialTheme.typography.bodyMedium,
                color = if (dangerCount > 0) danger.main else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeroCard(onSpeak: () -> Unit, onType: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .glowBackdrop(),
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShieldOrb(size = 92.dp, pulse = com.scamsense.app.ui.components.breathing())
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("ตรวจก่อนเชื่อ", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text(
                        "พูด พิมพ์ หรือวางข้อความที่ได้รับ ScamSense บอกได้ทันทีว่าน่าไว้ใจไหม",
                        style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.82f),
                    )
                }
            }
            Button(
                onClick = onSpeak,
                modifier = Modifier.fillMaxWidth().heightIn(min = 66.dp),
                shape = RoundedCornerShape(33.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Navy900),
            ) {
                Icon(Icons.Rounded.Mic, null, Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text("กดแล้วพูดได้เลย", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onType,
                modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                shape = RoundedCornerShape(29.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White.copy(alpha = 0.10f), contentColor = Color.White),
            ) {
                Icon(Icons.Rounded.Edit, null, Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("พิมพ์หรือวางข้อความ", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun CaregiverCard(settings: AppSettings, onCall: () -> Unit, onSetup: () -> Unit) {
    Card(
        onClick = if (settings.hasCaregiver) onCall else onSetup,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.FamilyRestroom, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                if (settings.hasCaregiver) {
                    Text("ผู้ดูแลของคุณ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(settings.caregiverName.ifBlank { settings.caregiverPhone }, style = MaterialTheme.typography.titleMedium)
                    Text("แตะเพื่อโทรหา", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("เพิ่มลูกหลานหรือผู้ดูแล", style = MaterialTheme.typography.titleMedium)
                    Text("เมื่อพบอันตราย ส่งข้อความเตือนให้เขาได้ทันที", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(
                if (settings.hasCaregiver) Icons.Rounded.Call else Icons.AutoMirrored.Rounded.ArrowForward,
                null, tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CallGuardCard(settings: AppSettings, onOpen: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val screening = remember { isCallScreeningEnabled(context) }
    val on = screening || settings.assistantPaired
    val c = riskColors(if (on) RiskLevel.SAFE else RiskLevel.SUSPICIOUS)
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(c.container), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PhoneLocked, null, tint = c.main, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("ป้องกันสายโทร", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        settings.assistantActive -> "เปิดอยู่ • ผู้ช่วย AI รับสายเบอร์แปลกให้ก่อน"
                        screening -> "เปิดอยู่ • บล็อกเบอร์อันตรายให้อัตโนมัติ"
                        else -> "ยังไม่เปิด • แตะเพื่อเปิดการกรองสายและการฟังระหว่างคุย"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (on) c.main else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun HotlineCard(onCall: () -> Unit) {
    val danger = riskColors(RiskLevel.DANGEROUS)
    Card(
        onClick = onCall,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = danger.container),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(danger.main), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.SupportAgent, null, tint = danger.onMain, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("โดนหลอก หรือไม่แน่ใจ?", style = MaterialTheme.typography.titleMedium, color = danger.onContainer)
                Text("โทรสายด่วน 1441 ศูนย์ AOC ตลอด 24 ชม.", style = MaterialTheme.typography.bodyMedium, color = danger.onContainer)
            }
            Icon(Icons.Rounded.Call, null, tint = danger.main, modifier = Modifier.size(28.dp))
        }
    }
}
