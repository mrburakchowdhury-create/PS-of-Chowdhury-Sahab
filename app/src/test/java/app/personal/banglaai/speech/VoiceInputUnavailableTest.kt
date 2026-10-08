package app.personal.banglaai.speech

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** No on-device recogniser (the situation on a phone without the offline pack): clear Bengali message, never a silent fallback to the default (possibly cloud) recogniser. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoiceInputUnavailableTest {
    @Test fun explainsAndDoesNotFallBackToTheDefaultRecogniser() {
        org.robolectric.shadows.ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(false)
        val v = VoiceInput(ApplicationProvider.getApplicationContext<Context>())
        assertFalse(v.available())
        var text: String? = null; var err: String? = null
        v.listen({ text = it }, { err = it })
        assertNull(text); assertNotNull(err)
        assertTrue(err!!.contains("অফলাইন")); assertTrue(err!!.contains("ইন্টারনেট-ভিত্তিক বিকল্পে নিজে থেকে যাওয়া হয় না"))
    }
}
