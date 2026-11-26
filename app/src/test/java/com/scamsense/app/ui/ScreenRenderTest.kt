package com.scamsense.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scamsense.app.StartupState
import com.scamsense.app.data.AppSettings
import com.scamsense.app.data.TextScale
import com.scamsense.app.engine.AnalysisOutcome
import com.scamsense.app.engine.CheckType
import com.scamsense.app.engine.RiskResult
import com.scamsense.app.engine.ScamAnalyzer
import com.scamsense.app.engine.TransactionInput
import com.scamsense.app.engine.TransactionSituation
import com.scamsense.app.ui.screens.CheckScreen
import com.scamsense.app.ui.screens.HistoryScreen
import com.scamsense.app.ui.screens.HomeScreen
import com.scamsense.app.ui.screens.LoadingScreen
import com.scamsense.app.ui.screens.ResultScreen
import com.scamsense.app.ui.screens.SettingsScreen
import com.scamsense.app.ui.screens.TransactionScreen
import com.scamsense.app.ui.theme.ScamSenseTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every screen with real Compose + native Android graphics (Robolectric) to
 * catch crashes/layout bugs and to produce reviewable screenshots in
 * app/build/screenshots/. Device: 411×891dp (typical modern Android phone).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenRenderTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val analyzer = ScamAnalyzer()
    private val caregiver = AppSettings(caregiverName = "ลูกสาว สมใจ", caregiverPhone = "0812345678")

    private fun result(type: CheckType, text: String): RiskResult =
        (analyzer.analyze(type, text) as AnalysisOutcome.Success).result

    private val dangerMsg = "ติดต่อจากสถานีตำรวจ บัญชีของท่านพัวพันคดีฟอกเงิน ต้องโอนเงินไปบัญชีปลอดภัยเพื่อตรวจสอบ ห้ามวางสาย ห้ามบอกใคร"
    private val parcelMsg = "พัสดุของท่านจัดส่งไม่สำเร็จ กรุณาอัปเดตที่อยู่และชำระค่าธรรมเนียม 25 บาท ที่ https://thpost-delivery.top/pay"

    private fun shoot(name: String, dark: Boolean = false, scale: Float = 1.15f, content: @Composable () -> Unit) {
        compose.setContent {
            ScamSenseTheme(darkTheme = dark, textScale = scale) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize()) { content() }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_500) // let entry animations (gauge count-up) finish
        compose.waitForIdle()
        val bmp = compose.activity.saveScreenshot(name)
        assertTrue(bmp.width > 0 && bmp.height > 0)
    }

    @Test fun loading() = shoot("01_loading") {
        LoadingScreen(StartupState(ready = false, progress = 0.7f, status = "กำลังเตรียมฐานข้อมูลกลโกง…"))
    }

    @Test fun home() {
        val history = listOf(result(CheckType.MESSAGE, dangerMsg), result(CheckType.MESSAGE, "พรุ่งนี้แม่จะไปหาที่บ้านนะ"))
        shoot("02_home") {
            HomeScreen(caregiver, history, { _, _ -> }, {}, {}, {}, {})
        }
        compose.onNodeWithText("กดแล้วพูดได้เลย").assertIsDisplayed()
    }

    @Test fun homeNoCaregiverDark() = shoot("02b_home_dark", dark = true) {
        HomeScreen(AppSettings(), emptyList(), { _, _ -> }, {}, {}, {}, {})
    }

    @Test fun checkMessage() = shoot("03_check_message") {
        CheckScreen(CheckType.MESSAGE, false, dangerMsg, false, null, {}, {}, {})
    }

    @Test fun checkPhoneWithError() = shoot("03b_check_phone_error") {
        CheckScreen(CheckType.PHONE, false, "abc", false, "ไม่พบเบอร์โทร กรุณาพิมพ์เบอร์ เช่น 081-234-5678", {}, {}, {})
    }

    @Test fun analyzing() = shoot("04_analyzing") {
        CheckScreen(CheckType.MESSAGE, false, dangerMsg, true, null, {}, {}, {})
    }

    @Test fun resultDangerBlocked() {
        shoot("05_result_danger") {
            ResultScreen(result(CheckType.MESSAGE, dangerMsg), caregiver, {}, {}, {}, {}, {}, {}, {})
        }
        compose.onNodeWithText("หยุด! อย่าทำตามข้อความนี้").assertIsDisplayed()
    }

    @Test fun resultParcelLink() = shoot("05b_result_parcel") {
        ResultScreen(result(CheckType.MESSAGE, parcelMsg), AppSettings(), {}, {}, {}, {}, {}, {}, {})
    }

    @Test fun resultSuspiciousPhone() = shoot("06_result_suspicious_phone") {
        ResultScreen(result(CheckType.PHONE, "+855 12 345 678"), caregiver, {}, {}, {}, {}, {}, {}, {})
    }

    @Test fun resultSafe() = shoot("07_result_safe") {
        ResultScreen(result(CheckType.MESSAGE, "พรุ่งนี้แม่จะไปหาที่บ้านนะ ทำกับข้าวไว้ด้วย คิดถึงหลาน"), caregiver, {}, {}, {}, {}, {}, {}, {})
    }

    @Test fun resultDangerDark() = shoot("05c_result_danger_dark", dark = true) {
        ResultScreen(result(CheckType.LINK, "https://scb-thailand-verify.top/account"), caregiver, {}, {}, {}, {}, {}, {}, {})
    }

    @Test fun transaction() = shoot("08_transaction") {
        TransactionScreen(false, {}, {})
    }

    @Test fun transactionResult() = shoot("08b_transaction_result") {
        val r = analyzer.analyzeTransaction(
            TransactionInput(80_000.0, false, setOf(TransactionSituation.SAFE_ACCOUNT, TransactionSituation.AUTHORITY_CLAIM)),
        )
        ResultScreen(r, caregiver, {}, {}, {}, {}, {}, {}, {})
    }

    @Test fun history() = shoot("09_history") {
        HistoryScreen(
            listOf(
                result(CheckType.MESSAGE, dangerMsg),
                result(CheckType.LINK, "bit.ly/3xYz9"),
                result(CheckType.PHONE, "081-234-5678"),
                result(CheckType.MESSAGE, parcelMsg),
            ).mapIndexed { i, r -> r.copy(analyzedAt = r.analyzedAt - i * 3_600_000L) },
            true, {}, {},
        )
    }

    @Test fun historyEmpty() = shoot("09b_history_empty") { HistoryScreen(emptyList(), true, {}, {}) }

    @Test fun settings() = shoot("10_settings") {
        SettingsScreen(caregiver.copy(customBlockWords = listOf("บัญชีปลอดภัย", "โครงการพิเศษ")), {}, {})
    }

    @Test fun homeExtraLargeText() = shoot("11_home_xl_text", scale = TextScale.EXTRA_LARGE.factor) {
        HomeScreen(caregiver, emptyList(), { _, _ -> }, {}, {}, {}, {})
    }

    @Test fun checkScreenExampleChipFillsText() {
        var analyzed: String? = null
        compose.setContent {
            ScamSenseTheme { CheckScreen(CheckType.MESSAGE, false, null, false, null, { analyzed = it }, {}, {}) }
        }
        compose.onNodeWithText("ตัวอย่าง: พัสดุ").performClick()
        compose.onNodeWithText("ตรวจสอบเลย").performClick()
        assertTrue(analyzed?.contains("พัสดุ") == true)
    }
}
