package app.personal.banglaai.call

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Motion maths for the built-in talking portrait (pure, JVM-tested). All positions are in the 700×700 px space of `avatar_default`,
 * where the mouth centre is ≈ (350, 402). This is a 2-D "puppet" of one fixed photo — jaw lowering, a dark mouth opening and eyelid blinks —
 * not a learned lip-sync model, so it will not match every sound exactly.
 */
object FaceMotion {
    const val SIZE = 700f
    const val MOUTH_X = 350f
    const val MOUTH_Y = 402f
    const val MAX_JAW_PX = 16f
    val EYES = listOf(292f to 293f, 397f to 285f)     // eye centres (left, right in the image)

    /** Uniform mesh lines: Canvas.drawBitmapMesh samples the texture evenly across the grid, so the rest grid must be uniform. */
    val COLS = FloatArray(29) { it * 25f }
    val ROWS = FloatArray(71) { it * 10f }

    private fun smooth(a: Float, b: Float, x: Float): Float { val t = ((x - a) / (b - a)).coerceIn(0f, 1f); return t * t * (3 - 2 * t) }

    /** Vertical shift of the point (x, y) when the jaw is opened by [d] px: nothing above the teeth, full on the lower lip/beard, fading out at the neck and sides. */
    fun jawShift(x: Float, y: Float, d: Float): Float =
        d * smooth(402f, 432f, y) * (1f - smooth(500f, 590f, y)) * (1f - smooth(130f, 230f, abs(x - MOUTH_X)))

    /** Mesh vertices (x,y pairs, row-major) for jaw opening [open] in 0..1. */
    fun vertices(open: Float): FloatArray {
        val d = open.coerceIn(0f, 1f) * MAX_JAW_PX
        val v = FloatArray(ROWS.size * COLS.size * 2); var k = 0
        for (y in ROWS) for (x in COLS) { v[k++] = x; v[k++] = y + jawShift(x, y, d) }
        return v
    }
    fun restVertices() = vertices(0f)

    /**
     * Jaw opening over time while talking: a syllable rhythm (~5.5 per second, rectified sine) whose strength is lifted by each word the speech engine
     * reports ([sinceWordMs] = time since the last word tick) and which relaxes to closed when not [speaking].
     */
    fun jaw(timeMs: Long, speaking: Boolean, sinceWordMs: Long): Float {
        if (!speaking) return 0f
        val syll = abs(sin(2.0 * PI * 5.5 * timeMs / 1000.0)).toFloat()
        val word = (1f - (sinceWordMs / 260f)).coerceIn(0f, 1f)          // word accent decays in ~0.26 s
        val amp = 0.45f + 0.55f * word
        return (syll * amp).coerceIn(0f, 1f)
    }

    /** Eyelid closure 0..1 for a blink that started [sinceBlinkStartMs] ago (≈ 160 ms long); 0 when no blink is running. */
    fun blink(sinceBlinkStartMs: Long): Float {
        if (sinceBlinkStartMs < 0 || sinceBlinkStartMs > 160) return 0f
        val t = sinceBlinkStartMs / 160f
        return if (t < 0.4f) t / 0.4f else (1f - t) / 0.6f
    }

    /** Next blink delay: irregular, 2.5–5.5 s (a deterministic hash of the blink count, so tests are stable). */
    fun nextBlinkDelayMs(count: Int): Long = 2500L + ((count * 7919L + 13L) % 3000L)
}
