package com.scamsense.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.scamsense.app.engine.RiskLevel
import com.scamsense.app.engine.RiskResult
import com.scamsense.app.engine.TextNormalizer

/**
 * Every outbound action opens the user's own app with the content pre-filled, so the
 * user always sees and confirms what is sent, and ScamSense needs no SMS/CALL permission.
 */
object Intents {

    const val AOC_HOTLINE = "1441"

    fun dial(context: Context, number: String): Boolean {
        val clean = number.filter { it.isDigit() || it == '+' }
        if (clean.isEmpty()) return false
        return start(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", clean, null)))
    }

    fun smsCaregiver(context: Context, phone: String, body: String): Boolean {
        val clean = phone.filter { it.isDigit() || it == '+' }
        if (clean.isEmpty()) return false
        val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", clean, null))
            .putExtra("sms_body", body)
        return start(context, intent)
    }

    fun shareText(context: Context, text: String, title: String = "ส่งต่อผลการตรวจ"): Boolean {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        return start(context, Intent.createChooser(send, title))
    }

    /** Human-readable report for family members. Digits in the message are masked. */
    fun buildReport(result: RiskResult, caregiverName: String? = null): String = buildString {
        val greet = caregiverName?.takeIf { it.isNotBlank() }?.let { "ถึง $it: " } ?: ""
        append(greet)
        append(
            when {
                result.shouldBlock -> "⚠️ ScamSense เตือน: อันตราย ควรบล็อก"
                result.level == RiskLevel.DANGEROUS -> "⚠️ ScamSense เตือน: อันตราย"
                result.level == RiskLevel.SUSPICIOUS -> "⚠️ ScamSense เตือน: น่าสงสัย"
                else -> "ScamSense: ความเสี่ยงต่ำ"
            },
        )
        append(" (คะแนนเสี่ยง ${result.score}/100)\n")
        result.matchedScamType?.let { append("ลักษณะ: $it\n") }
        val reasons = result.signals.filter { it.weight > 0 }.take(3)
        if (reasons.isNotEmpty()) {
            append("เหตุผล:\n")
            reasons.forEach { append("• ${it.title}\n") }
        }
        if (result.inputPreview.isNotBlank()) {
            append("ข้อความที่ตรวจ: \"${TextNormalizer.preview(result.inputPreview, 160)}\"\n")
        }
        append("ช่วยดูให้หน่อยนะ ก่อนจะทำอะไรต่อ")
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    /** Extracts text another app shared with us (SEND / PROCESS_TEXT). Untrusted → sanitized. */
    fun extractSharedText(intent: Intent?): CharSequence? {
        if (intent == null) return null
        return try {
            when (intent.action) {
                Intent.ACTION_SEND -> if (intent.type?.startsWith("text/") == true) {
                    intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
                } else null
                Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
                else -> null
            }
        } catch (_: RuntimeException) {
            // Malformed extras (BadParcelableException etc.) from a hostile sender.
            null
        }
    }
}
