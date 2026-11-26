package com.scamsense.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scamsense.app.data.AppSettings
import com.scamsense.core.engine.CheckType
import com.scamsense.core.engine.RiskLevel
import com.scamsense.core.engine.RiskResult
import com.scamsense.core.engine.RiskSignal
import com.scamsense.core.engine.SignalCategory
import com.scamsense.app.ui.components.BigButton
import com.scamsense.app.ui.components.BigOutlinedButton
import com.scamsense.app.ui.components.IconRow
import com.scamsense.app.ui.components.RiskGauge
import com.scamsense.app.ui.components.SectionTitle
import com.scamsense.app.ui.theme.label
import com.scamsense.app.ui.theme.riskColors
import com.scamsense.app.util.rememberSpeaker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ResultScreen(
    result: RiskResult,
    settings: AppSettings,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onNotifyCaregiver: () -> Unit,
    onSetupCaregiver: () -> Unit,
    onShare: () -> Unit,
    onCallHotline: () -> Unit,
    onBlockNumber: (String) -> Unit,
) {
    val colors = riskColors(result.level)
    val haptic = LocalHapticFeedback.current
    val speaker = rememberSpeaker()
    val alreadyBlocked = remember(settings.blockedNumbers, result.extractedPhones) {
        val blocked = settings.blockedNumbers.map { n -> n.filter { it.isDigit() } }.toSet()
        result.extractedPhones.isNotEmpty() && result.extractedPhones.all { p -> p.filter { it.isDigit() } in blocked }
    }
    var blockedNow by remember { mutableStateOf(false) }

    LaunchedEffect(result.analyzedAt) {
        if (result.level == RiskLevel.DANGEROUS) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        // ------------------------------------------------------------- verdict header
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
                .background(colors.main)
                .statusBarsPadding()
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "ย้อนกลับ", tint = colors.onMain)
                }
                Text(
                    "ผลการตรวจ" + typeSuffix(result.type),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onMain,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDone, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "ปิด กลับหน้าหลัก", tint = colors.onMain)
                }
            }
            if (result.shouldBlock) BlockBanner(colors.onMain)
            RiskGauge(result.score, result.level, onColor = colors.onMain)
            Text(
                result.level.label(),
                style = MaterialTheme.typography.headlineLarge,
                color = colors.onMain,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                result.summary,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onMain,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 28.dp, vertical = 6.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            result.matchedScamType?.let { type ->
                Surface(shape = CircleShape, color = colors.onMain.copy(alpha = 0.18f), contentColor = colors.onMain) {
                    Text("ลักษณะ: $type", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Column(
            Modifier.padding(20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // ------------------------------------------------------------- primary actions
            if (result.level != RiskLevel.SAFE) {
                if (settings.hasCaregiver) {
                    BigButton(
                        "แจ้งเตือน ${settings.caregiverName.ifBlank { "ผู้ดูแล" }}",
                        onClick = onNotifyCaregiver,
                        icon = Icons.Rounded.Sms,
                        containerColor = colors.main,
                        contentColor = colors.onMain,
                    )
                } else {
                    BigButton(
                        "ตั้งค่าผู้ดูแล เพื่อส่งคำเตือน",
                        onClick = onSetupCaregiver,
                        icon = Icons.Rounded.PersonSearch,
                        containerColor = colors.main,
                        contentColor = colors.onMain,
                    )
                }
                BigOutlinedButton("โทรสายด่วน 1441 (24 ชม.)", onClick = onCallHotline, icon = Icons.Rounded.Call, contentColor = colors.main)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigOutlinedButton("ส่งให้ลูกหลาน", onClick = onShare, icon = Icons.Rounded.Share, modifier = Modifier.weight(1f))
                if (speaker.available) {
                    BigOutlinedButton(
                        if (speaker.speaking) "หยุดอ่าน" else "ฟังผล",
                        onClick = { if (speaker.speaking) speaker.stop() else speaker.speak(spokenText(result)) },
                        icon = if (speaker.speaking) Icons.Rounded.StopCircle else Icons.Rounded.PlayCircle,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (result.type == CheckType.PHONE && result.extractedPhones.isNotEmpty()) {
                val done = alreadyBlocked || blockedNow
                BigOutlinedButton(
                    if (done) "บันทึกเบอร์นี้ว่าเป็นมิจฉาชีพแล้ว" else "รายงานว่าเบอร์นี้เป็นมิจฉาชีพ",
                    onClick = {
                        if (!done) {
                            result.extractedPhones.forEach(onBlockNumber)
                            blockedNow = true
                        }
                    },
                    icon = Icons.Rounded.Block,
                    contentColor = riskColors(RiskLevel.DANGEROUS).main,
                )
            }

            // ------------------------------------------------------------- reasons
            SectionTitle("ทำไมระบบประเมินแบบนี้")
            ResultCard {
                val shown = result.signals.filter { it.weight > 0 || it.category == SignalCategory.INFO }.take(8)
                shown.forEachIndexed { i, s ->
                    SignalRow(s)
                    if (i < shown.lastIndex) Spacer(Modifier.height(14.dp))
                }
            }

            // ------------------------------------------------------------- checked content
            if (result.inputPreview.isNotBlank() || result.extractedUrls.isNotEmpty() || result.extractedPhones.isNotEmpty()) {
                SectionTitle("สิ่งที่ตรวจ")
                ResultCard {
                    if (result.inputPreview.isNotBlank()) {
                        Text("\"${result.inputPreview}\"", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    result.extractedUrls.forEach { url ->
                        Spacer(Modifier.height(10.dp))
                        // Deliberately plain text: links from a checked message are never clickable.
                        IconRow(Icons.Rounded.Link, colors.main, url, body = if (result.level == RiskLevel.SAFE) null else "ห้ามกดลิงก์นี้")
                    }
                    result.extractedPhones.forEach { p ->
                        Spacer(Modifier.height(10.dp))
                        IconRow(Icons.Rounded.Phone, colors.main, p)
                    }
                }
            }

            // ------------------------------------------------------------- advice
            SectionTitle("ควรทำอย่างไรต่อ")
            ResultCard {
                result.advice.forEachIndexed { i, a ->
                    IconRow(Icons.Rounded.CheckCircle, MaterialTheme.colorScheme.primary, a)
                    if (i < result.advice.lastIndex) Spacer(Modifier.height(12.dp))
                }
            }

            BigButton("ตรวจรายการอื่น", onClick = onDone)
            Text(
                "ตรวจเมื่อ ${formatTime(result.analyzedAt)} • วิเคราะห์ในเครื่อง ${maxOf(1, result.elapsedMs)} มิลลิวินาที",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                "ผลนี้เป็นการประเมินเบื้องต้น ถ้าไม่แน่ใจ ให้ถามลูกหลานหรือโทร 1441 ก่อนทำอะไรเสมอ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BlockBanner(onColor: Color) {
    val t = rememberInfiniteTransition(label = "block")
    val pulse by t.animateFloat(1f, 1.04f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")
    Surface(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).scale(pulse),
        shape = RoundedCornerShape(18.dp),
        color = onColor.copy(alpha = 0.16f),
        contentColor = onColor,
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Block, null, Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Text("หยุด! อย่าทำตามข้อความนี้", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ResultCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) { content() }
    }
}

@Composable
private fun SignalRow(s: RiskSignal) {
    val level = when {
        s.weight >= 25 -> RiskLevel.DANGEROUS
        s.weight > 0 -> RiskLevel.SUSPICIOUS
        else -> RiskLevel.SAFE
    }
    val tint = if (s.category == SignalCategory.INFO && s.weight <= 0) MaterialTheme.colorScheme.primary else riskColors(level).main
    IconRow(
        icon = iconFor(s.category),
        tint = tint,
        title = s.title,
        body = s.detail,
        trailing = s.evidence?.let { ev ->
            {
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(ev, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    )
}

private fun iconFor(c: SignalCategory): ImageVector = when (c) {
    SignalCategory.SENSITIVE_INFO -> Icons.Rounded.Key
    SignalCategory.URGENCY -> Icons.Rounded.Schedule
    SignalCategory.IMPERSONATION -> Icons.Rounded.PersonSearch
    SignalCategory.MONEY, SignalCategory.TRANSACTION -> Icons.Rounded.Payments
    SignalCategory.THREAT -> Icons.Rounded.Gavel
    SignalCategory.ACTION -> Icons.Rounded.TouchApp
    SignalCategory.REMOTE_ACCESS -> Icons.Rounded.PhonelinkLock
    SignalCategory.SECRECY -> Icons.Rounded.Lock
    SignalCategory.SIMILAR_SCAM -> Icons.Rounded.Warning
    SignalCategory.LINK -> Icons.Rounded.Link
    SignalCategory.PHONE -> Icons.Rounded.Phone
    SignalCategory.CUSTOM -> Icons.Rounded.Block
    SignalCategory.INFO -> Icons.Rounded.Info
}

private fun typeSuffix(t: CheckType) = when (t) {
    CheckType.MESSAGE -> "ข้อความ"
    CheckType.LINK -> "ลิงก์"
    CheckType.PHONE -> "เบอร์โทร"
    CheckType.TRANSACTION -> "การโอนเงิน"
}

private fun spokenText(r: RiskResult): String = buildString {
    append("ผลการตรวจ ${r.level.label()} คะแนนความเสี่ยง ${r.score} จาก 100. ")
    append(r.summary).append(". ")
    r.signals.filter { it.weight > 0 }.take(3).forEach { append(it.title).append(". ") }
    r.advice.take(2).forEach { append(it).append(". ") }
}

private fun formatTime(ms: Long): String =
    SimpleDateFormat("d MMM yyyy HH:mm", Locale("th", "TH")).format(Date(ms))
