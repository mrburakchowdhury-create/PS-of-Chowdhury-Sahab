package app.personal.banglaai.ai

import org.junit.Assert.*
import org.junit.Test

class ChunkedTranslateTest {
    private val long = (1..400).joinToString(" ") { "This is sentence number $it of a long spoken transcript." }   // > 3,500 UTF-16 units

    @Test fun wholeTranscriptIsTranslatedInOrderWithNothingLost() {
        assertTrue(long.length > 3500)
        val seen = ArrayList<String>(); var last = 0 to 0
        val r = ChunkedTranslate.run(long, { d, t -> last = d to t }) { seen += it; "[" + it.length + "]" }
        assertTrue(r.complete); assertTrue(seen.size > 1); assertTrue(seen.all { it.length <= ChunkedTranslate.SINGLE_LIMIT })
        assertEquals(long.replace(Regex("\\s+"), " "), seen.joinToString(" ").replace(Regex("\\s+"), " "))
        assertEquals(seen.size to seen.size, last)
    }

    @Test fun shortTextIsOneRequest() { var n = 0; val r = ChunkedTranslate.run("hello") { n++; "হ্যালো" }; assertEquals(1, n); assertEquals("হ্যালো", r.text); assertTrue(r.complete) }

    @Test fun failureKeepsEarlierPartsAndSaysWhichPartFailed() {
        var n = 0
        val r = ChunkedTranslate.run(long) { if (++n == 3) error("model busy") else "ok$n" }
        assertFalse(r.complete); assertEquals(2, r.done); assertEquals("ok1\n\nok2", r.text); assertTrue(r.failure!!.contains("3/"))
    }
}
