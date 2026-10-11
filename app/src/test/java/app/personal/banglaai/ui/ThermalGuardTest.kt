package app.personal.banglaai.ui

import app.personal.banglaai.ai.LocalLlm
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class ThermalGuardTest {
    @After fun reset() { ThermalGuard.status = 0; LocalLlm.preferredThreads = 2; LocalLlm.threads = 2 }

    @Test fun capsThreadsWhileWarmAndRestoresWhenNormalAgain() {
        LocalLlm.preferredThreads = 6; LocalLlm.threads = 6
        ThermalGuard.status = ThermalGuard.WARN; ThermalGuard.applyThreads()
        assertEquals(2, LocalLlm.threads)       // capped, never above the cool preset, while warm
        assertEquals(6, LocalLlm.preferredThreads)   // the owner's own choice is remembered, not overwritten

        ThermalGuard.status = 0; ThermalGuard.applyThreads()
        assertEquals(6, LocalLlm.threads)       // full speed back once normal
    }

    @Test fun neverCapsBelowAnAlreadyCoolPreference() {
        LocalLlm.preferredThreads = 2; LocalLlm.threads = 2
        ThermalGuard.status = ThermalGuard.STOP; ThermalGuard.applyThreads()
        assertEquals(2, LocalLlm.threads)
    }
}
