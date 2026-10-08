package app.personal.banglaai.speech

import org.junit.Assert.*
import org.junit.Test

class ReadingPlanSnapshotTest {
    @Test fun snapshotKeepsBoundedPagesAndNoLongerCallsTheSource() {
        var calls = 0
        val plan = ReadingPlan({ p -> calls++; "Page $p. Text." }, 100)
        val snap = plan.snapshot(10, maxPages = 5, maxChars = 1000)
        val callsAfterSnapshot = calls
        assertEquals(10, snap.startAt(10)!!.page)
        assertNotNull(snap.segmentAt(snap.startAt(14)!!))   // inside the kept window
        assertNull(snap.startAt(15))                         // beyond maxPages: not kept, nothing read
        assertEquals("the source (the vault) is not touched again", callsAfterSnapshot, calls)
    }
    @Test fun snapshotRespectsTheCharacterBudget() {
        val plan = ReadingPlan({ "x".repeat(400) }, 50)
        val snap = plan.snapshot(1, maxPages = 40, maxChars = 1000)
        assertNotNull(snap.startAt(1)); assertNotNull(snap.startAt(2)); assertNull(snap.startAt(3))
    }
}
