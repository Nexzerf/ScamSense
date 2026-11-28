package com.scamsense.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scamsense.app.ui.components.ChoicePill
import com.scamsense.app.ui.components.IconChip
import com.scamsense.app.ui.components.ScoreRing
import com.scamsense.app.ui.components.ShieldOrb
import com.scamsense.app.ui.components.enterUp
import com.scamsense.app.ui.components.glowBackdrop
import com.scamsense.app.ui.theme.Lime
import com.scamsense.app.ui.theme.Navy900
import com.scamsense.app.ui.theme.label
import com.scamsense.app.ui.theme.riskColors
import com.scamsense.app.ui.theme.riskHero
import com.scamsense.core.engine.CheckType
import com.scamsense.core.engine.RiskLevel
import com.scamsense.core.engine.RiskResult
import java.util.Calendar

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    items: List<RiskResult>,
    saveHistory: Boolean,
    onOpen: (RiskResult) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    onStartCheck: () -> Unit = {},
) {
    var confirmClear by remember { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf<RiskLevel?>(null) }

    if (items.isEmpty()) {
        EmptyHistory(saveHistory, onStartCheck, modifier)
        return
    }

    // A filter whose level no longer has any entries falls back to "all".
    val active = filter?.takeIf { f -> items.any { it.level == f } }
    val shown = remember(items, active) { if (active == null) items else items.filter { it.level == active } }
    val reveal = com.scamsense.app.ui.components.rememberFirstReveal()
    val groups = remember(shown) { shown.groupBy { dayLabel(it.analyzedAt) } }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Box(Modifier.enterUp(0)) { SummaryHero(items) } }
        item {
            // Wraps instead of scrolling sideways: every filter is visible at once.
            androidx.compose.foundation.layout.FlowRow(
                Modifier.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChoicePill("ทั้งหมด ${items.size}", active == null, { filter = null })
                listOf(RiskLevel.DANGEROUS, RiskLevel.SUSPICIOUS, RiskLevel.SAFE).forEach { level ->
                    val n = items.count { it.level == level }
                    if (n > 0) ChoicePill("${level.label()} $n", active == level, { filter = if (active == level) null else level },
                        leading = { Box(Modifier.size(10.dp).clip(CircleShape).background(riskHero(level).accent)) })
                }
            }
        }
        groups.forEach { (day, rows) ->
            item(key = "h-$day") {
                Text(day, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp).semantics { heading() })
            }
            items(rows, key = { it.analyzedAt }) { r ->
                Box(Modifier.enterUp(2 + shown.indexOf(r), enabled = reveal).animateItem()) { HistoryRow(r) { onOpen(r) } }
            }
        }
        item {
            OutlinedButton(
                onClick = { confirmClear = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = 8.dp),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Rounded.DeleteOutline, null)
                Spacer(Modifier.width(8.dp))
                Text("ลบประวัติทั้งหมด", style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("ลบประวัติทั้งหมด?") },
            text = { Text("ประวัติการตรวจทั้งหมดจะถูกลบออกจากเครื่อง และกู้คืนไม่ได้") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClearAll() }) {
                    Text("ลบ", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("ยกเลิก", style = MaterialTheme.typography.labelLarge) }
            },
        )
    }
}

/** Navy glow card: how many checks, and how many were dangerous / suspicious / safe. */
@Composable
private fun SummaryHero(items: List<RiskResult>) {
    val danger = items.count { it.level == RiskLevel.DANGEROUS }
    val susp = items.count { it.level == RiskLevel.SUSPICIOUS }
    val safe = items.size - danger - susp
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).glowBackdrop().padding(22.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "ตรวจแล้ว ${items.size} ครั้ง อันตราย $danger น่าสงสัย $susp ปลอดภัย $safe"
            },
    ) {
        Text("ประวัติการตรวจ", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.8f))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(com.scamsense.app.ui.components.countUp(items.size).toString(), style = MaterialTheme.typography.displayMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("ครั้ง", style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.padding(bottom = 8.dp))
        }
        Spacer(Modifier.size(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatPill("อันตราย", danger, riskHero(RiskLevel.DANGEROUS).accent, Modifier.weight(1f))
            StatPill("น่าสงสัย", susp, riskHero(RiskLevel.SUSPICIOUS).accent, Modifier.weight(1f))
            StatPill("ปลอดภัย", safe, riskHero(RiskLevel.SAFE).accent, Modifier.weight(1f))
        }
        Spacer(Modifier.size(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("เก็บแบบเข้ารหัส อยู่ในเครื่องนี้เท่านั้น", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun StatPill(label: String, value: Int, accent: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.09f)).padding(vertical = 10.dp, horizontal = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
        }
        Text(com.scamsense.app.ui.components.countUp(value).toString(), style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HistoryRow(r: RiskResult, onClick: () -> Unit) {
    val c = riskColors(r.level)
    val (icon, typeName) = when (r.type) {
        CheckType.MESSAGE -> Icons.Rounded.Sms to "ข้อความ"
        CheckType.LINK -> Icons.Rounded.Link to "ลิงก์"
        CheckType.PHONE -> Icons.Rounded.Phone to "เบอร์โทร"
        CheckType.TRANSACTION -> Icons.Rounded.Payments to "การโอนเงิน"
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "$typeName ${r.level.label()} คะแนน ${r.score} ${relativeTime(r.analyzedAt)} แตะเพื่อดูรายละเอียด"
        },
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(r.score, c.main)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = c.container, contentColor = c.onContainer) {
                        Text(r.level.label(), Modifier.padding(horizontal = 12.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(typeName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Spacer(Modifier.size(6.dp))
                Text(
                    r.inputPreview.ifBlank { r.matchedScamType ?: r.summary },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(relativeTime(r.analyzedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyHistory(saveHistory: Boolean, onStartCheck: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(180.dp).clip(CircleShape).glowBackdrop(), contentAlignment = Alignment.Center) {
            ShieldOrb(size = 168.dp, pulse = com.scamsense.app.ui.components.breathing())
        }
        Spacer(Modifier.size(24.dp))
        Text("ยังไม่มีประวัติการตรวจ", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() })
        Spacer(Modifier.size(6.dp))
        Text(
            if (saveHistory) "ตรวจข้อความ ลิงก์ หรือเบอร์โทรครั้งแรก แล้วผลจะเก็บไว้ที่นี่ แบบเข้ารหัสในเครื่องนี้เท่านั้น"
            else "คุณปิดการบันทึกประวัติไว้ เปิดได้ที่หน้าตั้งค่า",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(24.dp))
        Button(
            onClick = onStartCheck,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Navy900, contentColor = Lime),
        ) {
            Icon(Icons.Rounded.Mic, null, Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Text("เริ่มตรวจเลย", style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
    }
}

/** "วันนี้", "เมื่อวาน", or a short Thai date. */
internal fun dayLabel(ms: Long, now: Long = System.currentTimeMillis()): String {
    fun dayIndex(t: Long) = Calendar.getInstance().apply { timeInMillis = t }.let { it.get(Calendar.YEAR) * 400 + it.get(Calendar.DAY_OF_YEAR) }
    return when (dayIndex(now) - dayIndex(ms)) {
        0 -> "วันนี้"
        1 -> "เมื่อวาน"
        else -> java.text.SimpleDateFormat("d MMMM", java.util.Locale("th", "TH")).format(java.util.Date(ms))
    }
}

internal fun relativeTime(ms: Long, now: Long = System.currentTimeMillis()): String {
    val diff = (now - ms).coerceAtLeast(0) / 1000
    return when {
        diff < 60 -> "เมื่อสักครู่"
        diff < 3600 -> "${diff / 60} นาทีที่แล้ว"
        diff < 86_400 -> "${diff / 3600} ชั่วโมงที่แล้ว"
        diff < 7 * 86_400 -> "${diff / 86_400} วันที่แล้ว"
        else -> java.text.SimpleDateFormat("d MMM", java.util.Locale("th", "TH")).format(java.util.Date(ms))
    }
}
