package com.scamsense.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.scamsense.core.engine.TransactionInput
import com.scamsense.core.engine.TransactionSituation
import com.scamsense.app.ui.components.AnalyzingIndicator
import com.scamsense.app.ui.components.BigButton
import com.scamsense.app.ui.components.SectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionScreen(
    analyzing: Boolean,
    onAnalyze: (TransactionInput) -> Unit,
    onBack: () -> Unit,
) {
    var amount by rememberSaveable { mutableStateOf("") }
    var knows by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var note by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            GlowHeader(
                "ตรวจก่อนโอนเงิน", onBack,
                subtitle = "ตอบ 3 ข้อ รู้ทันทีว่าเสี่ยงไหม",
                icon = Icons.Rounded.Payments,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        AnimatedContent(analyzing, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tx", modifier = Modifier.padding(padding)) { busy ->
            if (busy) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AnalyzingIndicator(label = "กำลังประเมินการโอน…") }
                return@AnimatedContent
            }
            Column(
                Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StepTitle(1, "จะโอนเท่าไร", Modifier.padding(top = 8.dp).enterUp(0))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' }.take(12) },
                    label = { Text("จำนวนเงิน (บาท)") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall,
                    prefix = { Text("฿ ", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(24.dp),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier.fillMaxWidth().enterUp(1),
                )
                StepTitle(2, "ผู้รับเงินเป็นใคร", Modifier.enterUp(2))
                Card(
                    modifier = Modifier.enterUp(2),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        Modifier.fillMaxWidth()
                            .toggleable(value = knows, role = Role.Switch, onValueChange = { knows = it })
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        com.scamsense.app.ui.components.IconChip(Icons.Rounded.Person, MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("รู้จักผู้รับเงินเป็นการส่วนตัว", style = MaterialTheme.typography.titleSmall)
                            Text("เคยเจอตัวจริง หรือเป็นญาติ/เพื่อนที่ติดต่อได้", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = knows, onCheckedChange = null)
                    }
                }

                StepTitle(3, "มีเหตุการณ์เหล่านี้ไหม", Modifier.enterUp(3))
                Text(
                    if (selected.isEmpty()) "เลือกได้หลายข้อ ถ้าไม่มีเลยก็ข้ามได้" else "เลือกแล้ว ${selected.size} ข้อ",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.enterUp(3),
                )
                TransactionSituation.entries.forEachIndexed { idx, s ->
                    val checked = s.name in selected
                    val border by androidx.compose.animation.animateColorAsState(
                        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "b",
                    )
                    val fill by androidx.compose.animation.animateColorAsState(
                        if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface, label = "f",
                    )
                    Card(
                        modifier = Modifier.enterUp(4 + idx),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = fill),
                        border = BorderStroke(if (checked) 2.dp else 1.dp, border),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 60.dp)
                                .toggleable(value = checked, role = Role.Checkbox, onValueChange = {
                                    selected = if (checked) selected - s.name else selected + s.name
                                })
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CheckDot(checked)
                            Spacer(Modifier.width(10.dp))
                            Text(s.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(1000) },
                    label = { Text("เขาบอกว่าอะไร (ไม่บังคับ)") },
                    minLines = 2,
                    maxLines = 5,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    shape = RoundedCornerShape(24.dp),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                BigButton(
                    "ประเมินความเสี่ยง",
                    icon = Icons.Rounded.Search,
                    onClick = {
                        onAnalyze(
                            TransactionInput(
                                amount = amount.toDoubleOrNull(),
                                knowsRecipientPersonally = knows,
                                situations = TransactionSituation.entries.filter { it.name in selected }.toSet(),
                                note = note,
                            ),
                        )
                    },
                )
                Spacer(Modifier.size(8.dp))
            }
        }
    }
}

@Composable
private fun StepTitle(n: Int, text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(30.dp).clip(androidx.compose.foundation.shape.CircleShape).background(com.scamsense.app.ui.theme.Navy900),
            contentAlignment = Alignment.Center,
        ) { Text("$n", style = MaterialTheme.typography.labelLarge, color = com.scamsense.app.ui.theme.Lime) }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    }
}

/** Round check that fills and pops when ticked (bigger and clearer than a checkbox). */
@Composable
private fun CheckDot(checked: Boolean) {
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (checked) 1f else 0f, androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 500f), label = "dot",
    )
    Box(
        Modifier.size(30.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .border(2.dp, if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(30.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp)) }
    }
}
