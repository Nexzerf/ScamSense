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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.fillMaxSize
import com.scamsense.app.ui.theme.label
import com.scamsense.app.ui.components.pressScale
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Settings
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
    onOpenHistory: () -> Unit = {},
    onOpenResult: (RiskResult) -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val screening = remember { isCallScreeningEnabled(context) }

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Page title + one quiet action, like the other tabs.
        Row(Modifier.padding(top = 12.dp).enterUp(0), verticalAlignment = Alignment.CenterVertically) {
            Text("ScamSense", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f).semantics { heading() })
            com.scamsense.app.ui.components.GlassIconButton(Icons.Rounded.Settings, "ตั้งค่า", onOpenSettings)
        }

        Box(Modifier.enterUp(1)) {
            SpeakDial(onSpeak = { onCheck(CheckType.MESSAGE, true) }, onType = { onCheck(CheckType.MESSAGE, false) })
        }

        Text("ตรวจอย่างอื่น", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp).enterUp(2))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.enterUp(2)) {
            HomeRow(Icons.Rounded.Link, "ลิงก์", "เว็บที่ถูกส่งมา") { onCheck(CheckType.LINK, false) }
            HomeRow(Icons.Rounded.PhoneInTalk, "เบอร์โทร", "เบอร์ที่โทรเข้ามา") { onCheck(CheckType.PHONE, false) }
            HomeRow(Icons.Rounded.Payments, "ก่อนโอนเงิน", "ตอบ 3 ข้อ รู้ทันที", onClick = onTransaction)
        }

        if (history.isNotEmpty()) {
            Row(Modifier.padding(top = 4.dp).enterUp(3), verticalAlignment = Alignment.CenterVertically) {
                Text("ล่าสุด", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = onOpenHistory) {
                    Text("ดูทั้งหมด", style = MaterialTheme.typography.labelLarge)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.enterUp(3)) {
                history.take(3).forEach { r -> RecentRow(r) { onOpenResult(r) } }
            }
        }

        Text("การป้องกัน", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp).enterUp(4))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.enterUp(4)) {
            HomeRow(Icons.Rounded.PhoneLocked, "ป้องกันสายโทร", if (screening) "เปิดอยู่" else "ยังไม่เปิด",
                badge = if (screening) RiskLevel.SAFE else RiskLevel.SUSPICIOUS, onClick = onOpenCallGuard)
            HomeRow(Icons.Rounded.FamilyRestroom, if (settings.hasCaregiver) settings.caregiverName.ifBlank { "ผู้ดูแล" } else "เพิ่มผู้ดูแล",
                if (settings.hasCaregiver) "แตะเพื่อโทร" else "รับคำเตือนเมื่อพบอันตราย",
                onClick = if (settings.hasCaregiver) onCallCaregiver else onOpenSettings)
            HomeRow(Icons.Rounded.SupportAgent, "สายด่วน 1441", "โดนหลอกหรือไม่แน่ใจ โทรได้ 24 ชม.", onClick = onCallHotline)
        }
    }
}

/** Round "tap to speak" dial: the one big action, in the brand blue on white. */
@Composable
private fun SpeakDial(onSpeak: () -> Unit, onType: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val tick = MaterialTheme.colorScheme.outline
    val pulse = com.scamsense.app.ui.components.breathing()
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2
                    val c = center
                    // tick marks around a 260° dial
                    for (k in 0..52) {
                        val a = Math.toRadians((140.0 + 260.0 * k / 52))
                        val long = k % 13 == 0
                        val r1 = r - 2.dp.toPx()
                        val r2 = r1 - (if (long) 12.dp.toPx() else 6.dp.toPx())
                        drawLine(tick.copy(alpha = if (long) 0.8f else 0.4f),
                            androidx.compose.ui.geometry.Offset(c.x + (r1 * kotlin.math.cos(a)).toFloat(), c.y + (r1 * kotlin.math.sin(a)).toFloat()),
                            androidx.compose.ui.geometry.Offset(c.x + (r2 * kotlin.math.cos(a)).toFloat(), c.y + (r2 * kotlin.math.sin(a)).toFloat()),
                            strokeWidth = 1.5.dp.toPx())
                    }
                    val inset = 26.dp.toPx()
                    val arc = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
                    val tl = androidx.compose.ui.geometry.Offset(inset, inset)
                    drawArc(track, 140f, 260f, false, tl, arc, style = androidx.compose.ui.graphics.drawscope.Stroke(10.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    drawArc(primary, 140f, 170f, false, tl, arc, style = androidx.compose.ui.graphics.drawscope.Stroke(10.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }
                val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                androidx.compose.material3.Surface(
                    onClick = onSpeak,
                    interactionSource = interaction,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(132.dp).scale(pulse).pressScale(interaction, 0.94f)
                        .semantics { contentDescription = "แตะแล้วพูดสิ่งที่ได้รับ เพื่อตรวจ" },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Rounded.Mic, null, tint = primary, modifier = Modifier.size(44.dp))
                        Text("แตะแล้วพูด", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Text("พูดหรือวางข้อความที่ได้รับ เพื่อตรวจว่าเป็นมิจฉาชีพไหม",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onType,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                Icon(Icons.Rounded.Edit, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("พิมพ์หรือวางข้อความ", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

/** List row in the reference style: icon in a soft circle, title, subtitle, optional badge, chevron. */
@Composable
private fun HomeRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    badge: RiskLevel? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth().pressScale(interaction),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (badge != null) {
                val c = riskColors(badge)
                androidx.compose.material3.Surface(shape = CircleShape, color = c.container, contentColor = c.onContainer) {
                    Text(if (badge == RiskLevel.SAFE) "เปิด" else "ปิด", Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.width(6.dp))
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Recent check: what it was, when, and a soft risk badge. */
@Composable
private fun RecentRow(r: RiskResult, onClick: () -> Unit) {
    val (icon, type) = when (r.type) {
        CheckType.MESSAGE -> Icons.Rounded.Sms to "ข้อความ"
        CheckType.LINK -> Icons.Rounded.Link to "ลิงก์"
        CheckType.PHONE -> Icons.Rounded.PhoneInTalk to "เบอร์โทร"
        CheckType.TRANSACTION -> Icons.Rounded.Payments to "การโอนเงิน"
    }
    val c = riskColors(r.level)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(r.inputPreview.ifBlank { r.matchedScamType ?: type }, style = MaterialTheme.typography.titleSmall, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text("$type • ${relativeTime(r.analyzedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.Surface(shape = CircleShape, color = c.container, contentColor = c.onContainer) {
                Text(r.level.label(), Modifier.padding(horizontal = 10.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

