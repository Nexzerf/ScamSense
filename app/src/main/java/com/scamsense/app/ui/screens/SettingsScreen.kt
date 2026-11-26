package com.scamsense.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.scamsense.app.BuildConfig
import com.scamsense.app.data.AppSettings
import com.scamsense.app.data.TextScale
import com.scamsense.app.data.ThemeMode
import com.scamsense.app.ui.components.BigButton
import com.scamsense.app.ui.components.SectionTitle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onSaved: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf(settings.caregiverName) }
    var phone by rememberSaveable { mutableStateOf(settings.caregiverPhone) }
    var phoneError by rememberSaveable { mutableStateOf<String?>(null) }
    var newWord by rememberSaveable { mutableStateOf("") }

    // Settings load asynchronously on first launch; sync the form once they arrive.
    LaunchedEffect(settings.caregiverName, settings.caregiverPhone) {
        if (name.isBlank()) name = settings.caregiverName
        if (phone.isBlank()) phone = settings.caregiverPhone
    }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // ---------------------------------------------------------------- caregiver
        SectionTitle("ผู้ดูแล / ลูกหลาน")
        SettingsCard {
            Text(
                "เมื่อพบข้อความอันตราย คุณกดส่งคำเตือนถึงผู้ดูแลได้ทันที (ผ่านแอป SMS ของเครื่อง คุณเห็นข้อความก่อนส่งเสมอ)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = { Text("ชื่อ เช่น ลูกสาว สมใจ") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(10.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { v -> phone = v.filter { it.isDigit() || it in "+- " }.take(20); phoneError = null },
                label = { Text("เบอร์โทรผู้ดูแล") },
                singleLine = true,
                isError = phoneError != null,
                supportingText = phoneError?.let { { Text(it) } },
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(12.dp))
            BigButton("บันทึกผู้ดูแล", icon = Icons.Rounded.Save, onClick = {
                val digits = phone.count { it.isDigit() }
                if (phone.isNotBlank() && digits !in 9..15) {
                    phoneError = "เบอร์โทรควรมี 9–15 หลัก"
                } else {
                    onUpdate { it.copy(caregiverName = name.trim(), caregiverPhone = phone.trim()) }
                    onSaved(if (phone.isBlank()) "ลบผู้ดูแลแล้ว" else "บันทึกผู้ดูแลแล้ว")
                }
            })
        }

        // ---------------------------------------------------------------- display
        SectionTitle("การแสดงผล")
        SettingsCard {
            Text("ขนาดตัวอักษร", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.size(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TextScale.entries.forEachIndexed { i, s ->
                    SegmentedButton(
                        selected = settings.textScale == s,
                        onClick = { onUpdate { it.copy(textScale = s) } },
                        shape = SegmentedButtonDefaults.itemShape(i, TextScale.entries.size),
                        modifier = Modifier.heightIn(min = 52.dp),
                    ) { Text(s.label, style = MaterialTheme.typography.labelLarge) }
                }
            }
            Spacer(Modifier.size(16.dp))
            Text("ธีม", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.size(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = settings.themeMode == m,
                        onClick = { onUpdate { it.copy(themeMode = m) } },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        modifier = Modifier.heightIn(min = 52.dp),
                    ) { Text(m.label, style = MaterialTheme.typography.labelLarge) }
                }
            }
        }

        // ---------------------------------------------------------------- block words
        SectionTitle("คำที่ต้องการบล็อก")
        SettingsCard {
            Text(
                "ถ้าข้อความมีคำเหล่านี้ ระบบจะเตือนว่าอันตรายทันที เหมาะให้ลูกหลานช่วยตั้ง",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newWord,
                    onValueChange = { newWord = it.take(40) },
                    label = { Text("เพิ่มคำ") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                FilledTonalIconButton(
                    onClick = {
                        val w = newWord.trim()
                        if (w.length >= 2) {
                            onUpdate { s -> if (w in s.customBlockWords) s else s.copy(customBlockWords = (s.customBlockWords + w).takeLast(200)) }
                            newWord = ""
                        }
                    },
                    modifier = Modifier.size(56.dp),
                ) { Icon(Icons.Rounded.Add, contentDescription = "เพิ่มคำที่บล็อก") }
            }
            if (settings.customBlockWords.isNotEmpty()) {
                Spacer(Modifier.size(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    settings.customBlockWords.forEach { w ->
                        InputChip(
                            selected = false,
                            onClick = { onUpdate { s -> s.copy(customBlockWords = s.customBlockWords - w) } },
                            label = { Text(w, style = MaterialTheme.typography.labelLarge) },
                            trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = "ลบคำ $w", Modifier.size(18.dp)) },
                            modifier = Modifier.heightIn(min = 44.dp),
                        )
                    }
                }
            }
        }

        if (settings.blockedNumbers.isNotEmpty()) {
            SectionTitle("เบอร์ที่รายงานไว้")
            SettingsCard {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    settings.blockedNumbers.forEach { n ->
                        InputChip(
                            selected = false,
                            onClick = { onUpdate { s -> s.copy(blockedNumbers = s.blockedNumbers - n) } },
                            label = { Text(n, style = MaterialTheme.typography.labelLarge) },
                            trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = "ลบเบอร์ $n", Modifier.size(18.dp)) },
                            modifier = Modifier.heightIn(min = 44.dp),
                        )
                    }
                }
            }
        }

        // ---------------------------------------------------------------- privacy
        SectionTitle("ความเป็นส่วนตัว")
        SettingsCard {
            Row(
                Modifier.fillMaxWidth()
                    .toggleable(settings.saveHistory, role = Role.Switch, onValueChange = { v -> onUpdate { it.copy(saveHistory = v) } })
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("บันทึกประวัติการตรวจ", style = MaterialTheme.typography.titleSmall)
                    Text("เก็บแบบเข้ารหัสในเครื่องนี้เท่านั้น", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.saveHistory, onCheckedChange = null)
            }
            Spacer(Modifier.size(14.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    "ScamSense ไม่ใช้อินเทอร์เน็ต ไม่ขอสิทธิ์ใด ๆ และไม่ส่งข้อความของคุณออกจากเครื่อง ข้อมูลที่บันทึกถูกเข้ารหัสด้วยกุญแจใน Android Keystore",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            "ScamSense เวอร์ชัน ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) { content() }
    }
}
