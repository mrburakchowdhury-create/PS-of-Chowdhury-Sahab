package app.personal.banglaai.image

import org.junit.Assert.*
import org.junit.Test

class CropMathTest {
    @Test fun cornersMoveAndStayInside() {
        val r = CropMath.drag(NRect.Full, Corner.TL, 0.3f, 0.2f)
        assertEquals(NRect(0.3f, 0.2f, 1f, 1f), r)
        assertEquals(NRect(0f, 0f, 1f, 1f), CropMath.drag(NRect.Full, Corner.BR, 0.5f, 0.5f))   // cannot leave the picture
        val tiny = CropMath.drag(NRect(0.2f, 0.2f, 0.5f, 0.5f), Corner.TL, 5f, 5f)
        assertEquals(0.5f - CropMath.MIN, tiny.l, 1e-6f); assertEquals(0.5f - CropMath.MIN, tiny.t, 1e-6f)   // minimum size kept
    }
    @Test fun pixelsAreInsideAndNonEmpty() {
        assertArrayEquals(intArrayOf(100, 50, 300, 100), CropMath.toPixels(NRect(0.25f, 0.25f, 1f, 0.75f), 400, 200))
        val p = CropMath.toPixels(NRect(0.999f, 0.999f, 1f, 1f), 10, 10); assertTrue(p[2] >= 1 && p[3] >= 1 && p[0] + p[2] <= 10 && p[1] + p[3] <= 10)
    }
    @Test fun fitCentresImage() {
        val f = CropMath.fit(200, 100, 400f, 400f)   // scale 2 -> 400x200, centred vertically
        assertArrayEquals(floatArrayOf(0f, 100f, 400f, 200f), f, 1e-4f)
    }
    @Test fun zoomPanRotate() {
        assertEquals(6f, CropMath.clampZoom(99f), 0f); assertEquals(1f, CropMath.clampZoom(0.2f), 0f)
        assertEquals(0f, CropMath.clampPan(50f, 1f, 400f), 0f); assertEquals(200f, CropMath.clampPan(900f, 2f, 400f), 0f)
        assertEquals(270, CropMath.rotate(0, -90)); assertEquals(0, CropMath.rotate(270, 90))
    }
}
