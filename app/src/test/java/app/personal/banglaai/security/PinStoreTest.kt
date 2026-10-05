package app.personal.banglaai.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PinStoreTest {
    private fun store() = PinStore(ApplicationProvider.getApplicationContext<Context>())

    @Test fun correctPinUnlocks() {
        val s = store(); s.set("123456".toCharArray())
        assertTrue(s.isSet())
        assertEquals(PinStore.Result.Ok, s.verify("123456".toCharArray()))
    }

    @Test fun pinIsNeverStoredInPlaintext() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        PinStore(ctx).set("987654".toCharArray())
        val all = ctx.getSharedPreferences("auth", Context.MODE_PRIVATE).all.values.joinToString()
        assertFalse(all.contains("987654"))
    }

    @Test fun wrongPinsEscalateDelayButNeverWipe() {
        val s = store(); s.set("1111".toCharArray())
        assertTrue(s.verify("0".toCharArray()) is PinStore.Result.Wrong)
        assertTrue(s.verify("0".toCharArray()) is PinStore.Result.Wrong)
        val third = s.verify("0".toCharArray())
        assertTrue(third is PinStore.Result.Locked)
        // even the right PIN is refused during the lock, and the PIN record is still there
        assertTrue(s.verify("1111".toCharArray()) is PinStore.Result.Locked)
        assertTrue(s.isSet())
        assertTrue(s.lockedSeconds() > 0)
    }
}
