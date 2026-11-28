package com.scamsense.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneLocked
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scamsense.app.BuildConfig
import com.scamsense.app.data.AppSettings
import com.scamsense.app.data.TextScale
import com.scamsense.app.data.ThemeMode
import com.scamsense.app.ui.components.BigButton
import com.scamsense.app.ui.components.IconChip
import com.scamsense.app.ui.components.ShieldOrb
import com.scamsense.app.ui.components.enterUp
import com.scamsense.app.ui.components.glowBackdrop
import com.scamsense.app.ui.theme.Lime
import com.scamsense.app.ui.theme.Mint
import com.scamsense.app.ui.theme.Navy900
import com.scamsense.app.ui.theme.riskColors
import com.scamsense.core.engine.RiskLevel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onSaved: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenCallGuard: () -> Unit = {},
) {
    var name by rememberSaveable { mutableStateOf(settings.caregiverName) }
    var phone by rememberSaveable { mutableStateOf(settings.caregiverPhone) }
    var phoneError by rememberSaveable { mutableStateOf<String?>(null) }
    var newWord by rememberSaveable { mutableStateOf("") }
    var editingCaregiver by rememberSaveable { mutableStateOf(!settings.hasCaregiver) }

    // Settings load asynchronously on first launch; sync the form once they arrive.
    LaunchedEffect(settings.caregiverName, settings.caregiverPhone) {
        if (name.isBlank()) name = settings.caregiverName
        if (phone.isBlank()) phone = settings.caregiverPhone
        if (settings.hasCaregiver && phone == settings.caregiverPhone) editingCaregiver = false
    }

    val context = LocalContext.current
    val screening = remember { isCallScreeningEnabled(context) }
    val guardOn = screening || settings.assistantPaired

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ---------------------------------------------------------------- status hero
        Box(Modifier.enterUp(0)) { StatusHero(settings, guardOn) }

        // ---------------------------------------------------------------- caregiver
        Group("ผู้ดูแล / ลูกหลาน", index = 1) {
            if (!editingCaregiver && settings.hasCaregiver) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val initial = settings.caregiverName.trim().firstOrNull { it.isLetter() }?.toString() ?: "?"
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).glowBackdrop(),
                        contentAlignment = Alignment.Center,
                    ) { Text(initial, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(settings.caregiverName.ifBlank { "ผู้ดูแล" }, style = MaterialTheme.typography.titleMedium)
                        Text(settings.caregiverPhone, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { editingCaregiver = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Rounded.Edit, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("แก้ไข", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.size(10.dp))
                Text(
                    "เมื่อพบอันตราย กดปุ่มเดียวเพื่อส่งคำเตือนถึงคนนี้ (ผ่านแอป SMS คุณเห็นข้อความก่อนส่งเสมอ)",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconChip(Icons.Rounded.FamilyRestroom, MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "ใส่ชื่อและเบอร์ของคนที่ไว้ใจ เมื่อพบอันตรายจะส่งคำเตือนถึงเขาได้ทันที",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("ชื่อ เช่น ลูกสาว สมใจ") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    shape = RoundedCornerShape(20.dp),
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
                    shape = RoundedCornerShape(20.dp),
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
                        if (phone.isNotBlank()) editingCaregiver = false
                    }
                })
            }
        }

        // ---------------------------------------------------------------- call guard
        Group("ป้องกันสายโทร", index = 2) {
            val c = riskColors(if (guardOn) RiskLevel.SAFE else RiskLevel.SUSPICIOUS)
            NavRow(
                icon = Icons.Rounded.PhoneLocked, tint = c.main,
                title = if (guardOn) "เปิดอยู่" else "ยังไม่เปิด",
                subtitle = "กรองสายเบอร์อันตราย และฟังระหว่างคุยเพื่อเตือนหรือวางสายให้",
                onClick = onOpenCallGuard,
            )
        }

        // ---------------------------------------------------------------- display
        Group("ขนาดตัวอักษร", index = 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextScale.entries.forEach { s ->
                    ChoiceTile(
                        selected = settings.textScale == s,
                        onClick = { onUpdate { it.copy(textScale = s) } },
                        label = s.label,
                        modifier = Modifier.weight(1f),
                    ) {
                        // A live sample at that size: easier to choose than a word.
                        Text("ก", fontSize = when (s) { TextScale.NORMAL -> 18.sp; TextScale.LARGE -> 26.sp; TextScale.EXTRA_LARGE -> 34.sp }, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Group("ธีม", index = 4) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeMode.entries.forEach { m ->
                    ChoiceTile(
                        selected = settings.themeMode == m,
                        onClick = { onUpdate { it.copy(themeMode = m) } },
                        label = m.label,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            when (m) { ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto; ThemeMode.LIGHT -> Icons.Rounded.LightMode; ThemeMode.DARK -> Icons.Rounded.DarkMode },
                            contentDescription = null, modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        }

        // ---------------------------------------------------------------- block words
        Group("คำที่ต้องการบล็อก", index = 5) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconChip(Icons.Rounded.Block, riskColors(RiskLevel.DANGEROUS).main)
                Spacer(Modifier.width(12.dp))
                Text(
                    "ถ้าข้อความมีคำเหล่านี้ จะเตือนว่าอันตรายทันที เหมาะให้ลูกหลานช่วยตั้ง",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newWord,
                    onValueChange = { newWord = it.take(40) },
                    label = { Text("เพิ่มคำ") },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
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
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.heightIn(min = 44.dp),
                        )
                    }
                }
            }
        }

        if (settings.blockedNumbers.isNotEmpty()) {
            Group("เบอร์ที่รายงานไว้ (${settings.blockedNumbers.size})", index = 6) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    settings.blockedNumbers.forEach { n ->
                        InputChip(
                            selected = false,
                            onClick = { onUpdate { s -> s.copy(blockedNumbers = s.blockedNumbers - n) } },
                            label = { Text(n, style = MaterialTheme.typography.labelLarge) },
                            trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = "ลบเบอร์ $n", Modifier.size(18.dp)) },
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.heightIn(min = 44.dp),
                        )
                    }
                }
            }
        }

        // ---------------------------------------------------------------- privacy
        Group("ความเป็นส่วนตัว", index = 7) {
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .toggleable(settings.saveHistory, role = Role.Switch, onValueChange = { v -> onUpdate { it.copy(saveHistory = v) } })
                    .heightIn(min = 64.dp)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconChip(Icons.Rounded.History, MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("บันทึกประวัติการตรวจ", style = MaterialTheme.typography.titleSmall)
                    Text("เก็บแบบเข้ารหัสในเครื่องนี้เท่านั้น", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.saveHistory, onCheckedChange = null)
            }
            Spacer(Modifier.size(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    "ข้อความที่ตรวจไม่ออกจากเครื่อง และข้อมูลที่บันทึกถูกเข้ารหัสด้วยกุญแจใน Android Keystore " +
                        "อินเทอร์เน็ตใช้เฉพาะตอนดาวน์โหลดโมเดลเสียงครั้งแรก",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---------------------------------------------------------------- about
        Group("เกี่ยวกับ", index = 8) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconChip(Icons.Rounded.Info, MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("ScamSense เวอร์ชัน ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall)
                    Text("ซอฟต์แวร์เสรี (GNU GPL v3)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.size(10.dp))
            Text(
                "This software is a fork of ShizuCallRecorder, available at https://github.com/kitsumed/ShizuCallRecorder. " +
                    "ส่วนดึงเสียงสายดัดแปลงมาจาก CallVault (https://github.com/madkongo/CallVault) ซึ่งพัฒนาต่อจาก ShizuCallRecorder " +
                    "เป็นฉบับแก้ไข ไม่ได้รับการรับรองจากผู้พัฒนาเดิม • แปลงเสียงด้วย whisper.cpp (MIT) • Shizuku API (Apache-2.0) • ฟอนต์ IBM Plex Sans Thai (OFL)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(8.dp))
    }
}

/** Navy glow summary at the top: is the family set up, is call protection on. */
@Composable
private fun StatusHero(settings: AppSettings, guardOn: Boolean) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).glowBackdrop().padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShieldOrb(size = 84.dp, pulse = com.scamsense.app.ui.components.breathing())
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("การตั้งค่า", style = MaterialTheme.typography.headlineSmall, color = Color.White,
                modifier = Modifier.semantics { heading() })
            StatusLine(settings.hasCaregiver, if (settings.hasCaregiver) "ผู้ดูแล: ${settings.caregiverName.ifBlank { settings.caregiverPhone }}" else "ยังไม่ได้ตั้งผู้ดูแล")
            StatusLine(guardOn, if (guardOn) "ป้องกันสายโทร: เปิด" else "ป้องกันสายโทร: ยังไม่เปิด")
        }
    }
}

@Composable
private fun StatusLine(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (ok) Mint else Color(0xFFFFC651)))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.9f), maxLines = 1)
    }
}

/** A titled frosted card. */
@Composable
private fun Group(title: String, index: Int = 1, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.enterUp(index), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 6.dp, top = 6.dp).semantics { heading() })
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), content = content)
        }
    }
}

@Composable
private fun NavRow(icon: ImageVector, tint: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconChip(icon, tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = tint)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Big square choice with a preview on top and a label below; selected = navy (lime in dark). */
@Composable
private fun ChoiceTile(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.3f
    val bg = when {
        selected && dark -> Lime
        selected -> Navy900
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        selected && dark -> Navy900
        selected -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = bg,
        contentColor = fg,
        modifier = modifier.heightIn(min = 96.dp).semantics { this.selected = selected },
    ) {
        Column(
            Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.heightIn(min = 40.dp), contentAlignment = Alignment.Center) { preview() }
            Spacer(Modifier.size(4.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
        }
    }
}
