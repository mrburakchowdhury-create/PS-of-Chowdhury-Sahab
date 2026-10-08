package app.personal.banglaai.doc

import android.os.PowerManager
import org.junit.Assert.*
import org.junit.Test

class ResourceGuardTest {
    private val gb = 1L shl 30
    @Test fun allFineMeansNoPause() { assertNull(ResourceGuard.decide(PowerManager.THERMAL_STATUS_LIGHT, 80, false, 5 * gb)) }
    @Test fun lowSpacePauses() { assertNotNull(ResourceGuard.decide(null, 80, false, 10L * 1024 * 1024)) }
    @Test fun severeHeatPauses() { assertNotNull(ResourceGuard.decide(PowerManager.THERMAL_STATUS_SEVERE, 80, true, 5 * gb)) }
    @Test fun lowBatteryPausesOnlyWhenNotCharging() {
        assertNotNull(ResourceGuard.decide(null, 5, false, 5 * gb)); assertNull(ResourceGuard.decide(null, 5, true, 5 * gb))
    }
}
