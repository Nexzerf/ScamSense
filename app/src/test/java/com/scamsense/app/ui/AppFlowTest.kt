package com.scamsense.app.ui

import android.content.Intent
import android.os.Looper
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scamsense.app.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/** End-to-end: real MainActivity, ViewModel, engine and navigation. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class AppFlowTest {

    @get:Rule val compose = createEmptyComposeRule()

    private lateinit var activity: MainActivity

    @Before fun launch() {
        activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
    }

    /** Advances Robolectric's clock (coroutine delays) until [text] is on screen. */
    private fun waitForText(text: String, timeoutMs: Long = 15_000) {
        var waited = 0L
        while (waited < timeoutMs) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
            if (compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(5) // let Dispatchers.Default work finish
            waited += 100
        }
        throw AssertionError("Timed out waiting for \"$text\"")
    }

    private fun screenshot(name: String) {
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        activity.saveScreenshot(name)
    }

    @Test fun startupThenCheckMessageShowsDangerResult() {
        waitForText("แตะแล้วพูด")
        compose.onNodeWithText("พิมพ์หรือวางข้อความ").performClick()
        waitForText("ตรวจข้อความ")
        compose.onNode(hasSetTextAction()).performTextInput(
            "ด่วน! บัญชีของคุณถูกระงับ กรุณากรอกรหัส OTP ที่ http://kbank-secure.xyz ภายใน 24 ชั่วโมง",
        )
        compose.onNodeWithText("ตรวจสอบเลย").performClick()
        waitForText("หยุด! อย่าทำตามข้อความนี้")
        screenshot("20_flow_result")
        compose.onNodeWithText("ตรวจรายการอื่น").performScrollTo().performClick()
        waitForText("แตะแล้วพูด")
        // History tab shows the check we just made.
        compose.onNodeWithContentDescription("ประวัติ").performClick()
        waitForText("ทั้งหมด 1")
        screenshot("21_flow_history")
    }

    @Test fun emptyPhoneInputShowsFriendlyError() {
        waitForText("แตะแล้วพูด")
        compose.onNodeWithText("เบอร์โทร").performClick()
        waitForText("ตรวจเบอร์โทร")
        compose.onNode(hasSetTextAction()).performTextInput("ไม่มีเบอร์")
        compose.onNodeWithText("ตรวจสอบเลย").performClick()
        waitForText("ไม่พบเบอร์โทร")
    }

    @Test fun sharedTextFromAnotherAppIsCheckedAutomatically() {
        waitForText("แตะแล้วพูด")
        run {
            activity.onNewIntent(
                Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "ให้ติดตั้ง AnyDesk เพื่อให้เจ้าหน้าที่ตรวจสอบบัญชี แล้วโอนเงินไปบัญชีปลอดภัย"),
            )
        }
        waitForText("หยุด! อย่าทำตามข้อความนี้")
    }

    @Test fun maliciousShareIntentWithHugeTextDoesNotCrash() {
        waitForText("แตะแล้วพูด")
        run {
            activity.onNewIntent(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "\u0000ด่วน".repeat(200_000)),
            )
            activity.onNewIntent(Intent(Intent.ACTION_SEND).setType("image/png")) // wrong type ignored
            activity.onNewIntent(Intent("com.evil.ACTION").putExtra(Intent.EXTRA_TEXT, "x")) // unknown action ignored
        }
        waitForText("ผลการตรวจ")
        assertTrue(true)
    }
}
