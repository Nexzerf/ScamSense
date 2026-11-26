package com.scamsense.app.ui.screens

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.PhoneCallback
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scamsense.app.ui.theme.RiskColors
import com.scamsense.app.ui.theme.riskColors
import com.scamsense.core.api.CallOutcome
import com.scamsense.core.api.CallRecordDto
import com.scamsense.core.engine.PhoneAnalyzer
import com.scamsense.core.engine.RiskLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLogScreen(
    calls: List<CallRecordDto>,
    busy: Boolean,
    onRefresh: () -> Unit,
    onDeleteAll: () -> Unit,
    onBack: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { onRefresh() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("สายที่ผู้ช่วยรับแทน", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "ย้อนกลับ")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.Refresh, "รีเฟรช") }
                    IconButton(onClick = { confirm = true }, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.DeleteSweep, "ลบทั้งหมด") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (calls.isEmpty() && !busy) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Rounded.SmartToy, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(12.dp))
                    Text("ยังไม่มีสายที่ผู้ช่วยรับแทน", style = MaterialTheme.typography.titleLarge)
                    Text("เมื่อมีสายจากเบอร์ไม่รู้จัก ผู้ช่วยจะรับและสรุปให้ที่นี่",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(calls, key = { it.id }) { CallCard(it) }
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("ลบประวัติสายบนเซิร์ฟเวอร์?") },
            text = { Text("ข้อความที่ผู้ช่วยถอดจากสายทั้งหมดจะถูกลบถาวร") },
            confirmButton = { TextButton(onClick = { confirm = false; onDeleteAll() }) { Text("ลบ", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("ยกเลิก") } },
        )
    }
}

private data class OutcomeStyle(val label: String, val icon: ImageVector, val colors: RiskColors)

@Composable
private fun styleFor(o: CallOutcome): OutcomeStyle = when (o) {
    CallOutcome.BLOCKED -> OutcomeStyle("วางสายมิจฉาชีพให้แล้ว", Icons.Rounded.CallEnd, riskColors(RiskLevel.DANGEROUS))
    CallOutcome.CONNECTED -> OutcomeStyle("ปลอดภัย โอนสายให้แล้ว", Icons.Rounded.PhoneCallback, riskColors(RiskLevel.SAFE))
    CallOutcome.MESSAGE_TAKEN -> OutcomeStyle("น่าสงสัย รับฝากข้อความไว้", Icons.Rounded.Voicemail, riskColors(RiskLevel.SUSPICIOUS))
    CallOutcome.NO_SPEECH -> OutcomeStyle("ไม่มีเสียงตอบ วางสายแล้ว", Icons.Rounded.VolumeOff, riskColors(RiskLevel.SUSPICIOUS))
}

@Composable
private fun CallCard(c: CallRecordDto) {
    val st = styleFor(c.outcome)
    var expanded by rememberSaveable(c.id) { mutableStateOf(c.outcome == CallOutcome.BLOCKED) }
    Card(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(st.colors.container, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(st.icon, null, tint = st.colors.main, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(st.label, style = MaterialTheme.typography.titleSmall, color = st.colors.main)
                    Text("${if (c.caller.isBlank()) "ไม่แสดงเบอร์" else PhoneAnalyzer.classify(c.caller).display} • ${relativeTime(c.startedAt)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${c.score}", style = MaterialTheme.typography.titleLarge, color = st.colors.main)
            }
            c.scamType?.let {
                Spacer(Modifier.size(8.dp))
                Text("ลักษณะ: $it", style = MaterialTheme.typography.labelLarge)
            }
            if (expanded) {
                if (c.reasons.isNotEmpty()) {
                    Spacer(Modifier.size(8.dp))
                    c.reasons.take(4).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
                if (c.transcript.isNotEmpty()) {
                    Spacer(Modifier.size(10.dp))
                    Text("ผู้โทรพูดว่า", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    c.transcript.forEach { line ->
                        Box(
                            Modifier.padding(top = 6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) { Text("\"$line\"", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            } else if (c.transcript.isNotEmpty()) {
                Spacer(Modifier.size(6.dp))
                Text("แตะเพื่อดูว่าผู้โทรพูดอะไร", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
