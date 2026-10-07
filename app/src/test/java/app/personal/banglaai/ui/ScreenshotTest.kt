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
        rule.onNodeWithText("ভয়েস নির্দেশ").performClick(); rule.waitForIdle()
        rule.onNodeWithText("নির্দেশ (লিখেও দিতে পারেন / শোনা লেখা ঠিক করুন)").performTextInput("ইতিহাস ফাইলটা খোলো এবং পনেরো নম্বর পৃষ্ঠা থেকে পড়া শুরু করো")
        rule.onNodeWithText("পাঠান").performScrollTo().performClick(); rule.waitForIdle(); shot("4_home_voice")
        rule.onNodeWithText("← হোম").performClick(); rule.waitForIdle()
        rule.onNodeWithText("ফিচারের অবস্থা").performClick(); rule.waitForIdle()
        rule.onNodeWithText("ফিচারের অবস্থা (সৎ তালিকা)").performScrollTo(); rule.waitForIdle(); shot("5_home_status")
        rule.onNodeWithText("← হোম").performClick(); rule.waitForIdle()
        rule.onNodeWithText("সহকারী").performClick(); rule.waitForIdle()
        rule.onNodeWithText("প্রশ্ন").performTextInput("ইসলামে জ্ঞান অর্জনের গুরুত্ব কী?")
        rule.onNodeWithText("পাঠান").performClick(); rule.waitForIdle(); shot("7_chat_no_model")
        rule.onNodeWithText("← ফিরে যান").performClick(); rule.waitForIdle()
        rule.onNodeWithText("ফোন পরীক্ষা").performClick(); rule.waitForIdle(); shot("8_diagnostics")
        rule.onNodeWithText("← ফিরে যান").performClick(); rule.waitForIdle()
        FaceLoopConfig.disabled = true   // the portrait's endless frame loop would keep Compose from ever becoming idle
        rule.onNodeWithText("ভয়েস কল").performClick(); rule.waitForIdle(); shot("9_voice_call_idle")
        rule.onNodeWithText("← ফিরে যান").performClick(); rule.waitForIdle()
        rule.onNodeWithText("🔒 বন্ধ করুন").performClick(); rule.waitForIdle(); shot("6_locked")
    }
}
