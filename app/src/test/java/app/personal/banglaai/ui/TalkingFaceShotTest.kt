package app.personal.banglaai.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the talking portrait in three states (closed, mouth open, blink) so the animation can be reviewed without a phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-port-xxhdpi")
class TalkingFaceShotTest {
    @get:Rule val rule = createComposeRule()
    private fun shot(name: String, speaking: Boolean, t: Long, blink: Boolean) {
        rule.mainClock.autoAdvance = false
        rule.setContent { TalkingFace(speaking, 0, Modifier.size(200.dp), fixedTimeMs = t, previewBlink = blink) }
        rule.mainClock.advanceTimeBy(100)
        val out = File("build/screenshots").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun closed() = shot("10a_face_closed", false, 1000L, false)
    @Test fun open() = shot("10b_face_open", true, 45L, false)
    @Test fun blink() = shot("10c_face_blink", false, 1000L, true)
}
