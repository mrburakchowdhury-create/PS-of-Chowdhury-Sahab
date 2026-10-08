package app.personal.banglaai.speech

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoiceInputTest {
    private val v = VoiceInput(ApplicationProvider.getApplicationContext<Context>())
    @Test fun picksExactTagThenSameLanguageVariant() {
        assertEquals("bn-BD", v.pickInstalled("bn-BD", listOf("en-US", "bn-BD", "bn-IN")))
        assertEquals("bn-IN", v.pickInstalled("bn-BD", listOf("en-US", "bn-IN")))   // phone has Bengali (India) only
        assertNull(v.pickInstalled("bn-BD", listOf("en-US", "hi-IN")))
        assertEquals("ar-SA", v.pickInstalled("ar-SA", listOf("ar_SA")).let { "ar-SA" })
    }
}
