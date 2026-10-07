package app.personal.banglaai

import android.util.Log
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Drives the real app UI on the emulator: first run -> PIN setup -> home -> assistant (no model) -> phone check. Fails on any crash or missing screen. */
@RunWith(AndroidJUnit4::class)
class SmokeUiTest {
    @get:Rule val rule = createEmptyComposeRule()
    private fun waitText(t: String, ms: Long = 120_000) = rule.waitUntil(ms) { rule.onAllNodesWithTextCompat(t) }
    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTextCompat(t: String) =
        onAllNodes(androidx.compose.ui.test.hasText(t)).fetchSemanticsNodes().isNotEmpty()

    @Test fun firstRunToDiagnostics() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ctx.getSharedPreferences("auth", 0).edit().clear().commit()
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("প্রথম সেটআপ"); Log.i("SMOKE", "UI: setup screen shown after splash")
            rule.onNodeWithText("নতুন PIN").performTextInput("123456"); rule.onNodeWithText("আবার PIN লিখুন").performTextInput("123456")
            rule.onNodeWithText("PIN সেট করুন").performClick()
            waitText("শুরুর ধাপ"); Log.i("SMOKE", "UI: home shown")
            rule.onNodeWithText("💬 সহকারী (প্রশ্ন / অনুবাদ)").performScrollTo().performClick()
            waitText("সহকারী")
            rule.onNodeWithText("প্রশ্ন").performTextInput("পরীক্ষা"); rule.onNodeWithText("পাঠান").performClick()
            waitText("কোনো AI মডেল বসানো নেই"); Log.i("SMOKE", "UI: assistant explains missing model")
            rule.onNodeWithText("← ফিরে যান").performClick()
            rule.onNodeWithText("🩺 ফোন পরীক্ষা").performScrollTo().performClick()
            waitText("ফোনের অবস্থা পরীক্ষা"); Thread.sleep(4000); Log.i("SMOKE", "UI: diagnostics shown")
        }
    }
}
