package app.personal.banglaai.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.personal.banglaai.MainActivity
import app.personal.banglaai.security.PinStore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the REAL app screens with Robolectric's native graphics (Skia) at the S24 Ultra's size class (≈412×915 dp).
 * These are build-machine renders, NOT screenshots from a phone: fonts/colours/system bars can differ on the device.
 * Output: PNG files in build/screenshots (copied to docs/screenshots by hand when the owner wants to review them).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-port-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val out = File("build/screenshots").apply { mkdirs() }

    private fun shot(name: String) {
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun splashLockHome() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        // first run: no PIN yet -> setup screen is what the owner sees after the splash
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(300); shot("1_splash")
        rule.mainClock.advanceTimeBy(1500); rule.mainClock.autoAdvance = true; rule.waitForIdle(); shot("2_first_setup")
        // set a PIN through the real setup screen, then lock/unlock path
        rule.onNodeWithText("নতুন PIN").performTextInput("123456")
        rule.onNodeWithText("আবার PIN লিখুন").performTextInput("123456")
        rule.onNodeWithText("PIN সেট করুন").performClick(); rule.waitForIdle()
        shot("3_home")
        rule.onNodeWithText("Files").performClick(); rule.waitForIdle()
        rule.onNodeWithText("নির্দেশ (লিখেও দিতে পারেন / শোনা লেখা ঠিক করুন)").performTextInput("দ্য প্রিন্স থেকে দশ নম্বর পৃষ্ঠা পড়ো")
        rule.onNodeWithText("Send").performScrollTo().performClick(); rule.waitForIdle(); shot("4_home_voice")
        rule.onNodeWithText("← Home").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Setup").performClick(); rule.waitForIdle()
        rule.onNodeWithText("ℹ️ What works and what is not tested yet").performClick(); rule.waitForIdle()
        rule.onNodeWithText("ফিচারের অবস্থা (সৎ তালিকা)").performScrollTo(); rule.waitForIdle(); shot("5_home_status")
        rule.onNodeWithText("← Home").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Assistant").performClick(); rule.waitForIdle()
        rule.onNodeWithText("প্রশ্ন").performTextInput("ইসলামে জ্ঞান অর্জনের গুরুত্ব কী?")
        rule.onNodeWithText("Send").performClick(); rule.waitForIdle(); shot("7_chat_no_model")
        rule.onNodeWithText("← Back").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Setup").performClick(); rule.waitForIdle()
        rule.onNodeWithText("🩺 Phone check (microphone, voice, model test)").performClick(); rule.waitForIdle(); shot("8_diagnostics")
        rule.onNodeWithText("← Back").performClick(); rule.waitForIdle()
        rule.onNodeWithText("📚 Quick answers (short topic cards with sources)").performScrollTo().performClick(); rule.waitForIdle(); shot("11_knowledge")
        rule.onNodeWithText("← Back").performClick(); rule.waitForIdle()
        rule.onNodeWithText("← Home").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Books").performScrollTo().performClick(); rule.waitForIdle(); shot("12_library")
        rule.onNodeWithText("← Back").performClick(); rule.waitForIdle()
        FaceLoopConfig.disabled = true   // the portrait's endless frame loop would keep Compose from ever becoming idle
        rule.onNodeWithText("Voice Call").performClick(); rule.waitForIdle(); shot("9_voice_call_idle")
        rule.onNodeWithText("← Back").performClick(); rule.waitForIdle()
        rule.onNodeWithText("🔒 Lock").performClick(); rule.waitForIdle(); shot("6_locked")
    }
}
