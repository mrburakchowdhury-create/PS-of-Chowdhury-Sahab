package app.personal.banglaai.call

import org.junit.Assert.*
import org.junit.Test

class FaceMotionTest {
    @Test fun nothingMovesAboveTheTeethOrWhenClosed() {
        assertEquals(0f, FaceMotion.jawShift(350f, 300f, 16f), 0f)
        assertEquals(0f, FaceMotion.jawShift(350f, 450f, 0f), 0f)
        assertTrue(FaceMotion.jawShift(350f, 460f, 16f) > 10f)
        assertEquals(0f, FaceMotion.jawShift(60f, 460f, 16f), 0f)     // far sides stay put
        assertEquals(0f, FaceMotion.jawShift(350f, 650f, 16f), 0f)     // neck/collar stays put
    }
    @Test fun meshMatchesGridAndIsMonotoneInY() {
        val v = FaceMotion.vertices(1f)
        assertEquals(FaceMotion.ROWS.size * FaceMotion.COLS.size * 2, v.size)
        for (c in FaceMotion.COLS.indices) for (r in 0 until FaceMotion.ROWS.size - 1) {
            val a = v[(r * FaceMotion.COLS.size + c) * 2 + 1]; val b = v[((r + 1) * FaceMotion.COLS.size + c) * 2 + 1]
            assertTrue("row $r col $c folds over", b > a)
        }
        assertArrayEquals(FaceMotion.vertices(0f), FaceMotion.restVertices(), 0f)
    }
    @Test fun jawStaysClosedWhenSilentAndOpensWhenTalking() {
        assertEquals(0f, FaceMotion.jaw(1234, false, 0), 0f)
        val m = (0L..1000L step 20).map { FaceMotion.jaw(it, true, 100) }
        assertTrue(m.max() > 0.7f); assertTrue(m.all { it in 0f..1f }); assertTrue(m.min() < 0.2f)
    }
    @Test fun blinkShapeAndDelays() {
        assertEquals(0f, FaceMotion.blink(-1), 0f); assertEquals(0f, FaceMotion.blink(500), 0f)
        assertEquals(1f, FaceMotion.blink(64), 1e-3f)
        assertTrue((0..50).all { FaceMotion.nextBlinkDelayMs(it) in 2500..5500 })
    }
}
