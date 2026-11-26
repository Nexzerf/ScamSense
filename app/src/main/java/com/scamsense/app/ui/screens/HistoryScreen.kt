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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scamsense.core.engine.CheckType
import com.scamsense.core.engine.RiskResult
import com.scamsense.app.ui.components.LevelBadge
import com.scamsense.app.ui.components.SectionTitle
import com.scamsense.app.ui.theme.label
import com.scamsense.app.ui.theme.riskColors

@Composable
fun HistoryScreen(
    items: List<RiskResult>,
    saveHistory: Boolean,
    onOpen: (RiskResult) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClear by remember { mutableStateOf(false) }

    if (items.isEmpty()) {
        Column(
            modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(96.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.History, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.size(20.dp))
            Text("ยังไม่มีประวัติการตรวจ", style = MaterialTheme.typography.titleLarge)
            Text(
                if (saveHistory) "ผลการตรวจจะถูกเก็บไว้ที่นี่ แบบเข้ารหัส อยู่ในเครื่องนี้เท่านั้น"
                else "คุณปิดการบันทึกประวัติไว้ในหน้าตั้งค่า",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("ประวัติการตรวจ (${items.size})", Modifier.weight(1f))
                TextButton(onClick = { confirmClear = true }) {
                    Icon(Icons.Rounded.DeleteSweep, null)
                    Spacer(Modifier.width(6.dp))
                    Text("ลบทั้งหมด", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        items(items, key = { it.analyzedAt }) { r -> HistoryRow(r) { onOpen(r) } }
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

@Composable
private fun HistoryRow(r: RiskResult, onClick: () -> Unit) {
    val c = riskColors(r.level)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "${r.level.label()} คะแนน ${r.score} ${relativeTime(r.analyzedAt)}"
        },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).background(c.container, CircleShape), contentAlignment = Alignment.Center) {
                Text(r.score.toString(), style = MaterialTheme.typography.titleLarge, color = c.onContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when (r.type) {
                            CheckType.MESSAGE -> Icons.Rounded.Sms
                            CheckType.LINK -> Icons.Rounded.Link
                            CheckType.PHONE -> Icons.Rounded.Phone
                            CheckType.TRANSACTION -> Icons.Rounded.Payments
                        },
                        null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    LevelBadge(r.level)
                    Spacer(Modifier.weight(1f))
                    Text(relativeTime(r.analyzedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.size(4.dp))
                Text(
                    r.inputPreview.ifBlank { r.matchedScamType ?: r.summary },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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
