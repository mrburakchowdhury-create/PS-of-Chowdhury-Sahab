package app.personal.banglaai.ui

import android.graphics.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import app.personal.banglaai.R
import app.personal.banglaai.call.FaceMotion

/**
 * The owner's portrait, animated as a 2-D puppet: the lower face/beard moves down with an opening mouth while speaking (rhythm + a lift on every word
 * the speech engine reports), the eyes blink at irregular intervals. The photo itself is kept unchanged otherwise.
 * Honest limit: this is not learned lip-sync; mouth shapes follow rhythm and words, not individual sounds.
 */
@Composable
fun TalkingFace(speaking: Boolean, wordTick: Int, modifier: Modifier = Modifier, fixedTimeMs: Long? = null, previewBlink: Boolean = false) {
    val ctx = LocalContext.current
    val bmp = remember { BitmapFactory.decodeResource(ctx.resources, R.drawable.avatar_default, BitmapFactory.Options().apply { inScaled = false }) }
    var now by remember { mutableLongStateOf(fixedTimeMs ?: 0L) }
    var lastWord by remember { mutableLongStateOf(-10_000L) }
    var blinkStart by remember { mutableLongStateOf(if (previewBlink) (fixedTimeMs ?: 0L) - 64L else -10_000L) }
    var nextBlink by remember { mutableLongStateOf(1800L) }
    var blinks by remember { mutableIntStateOf(0) }
    LaunchedEffect(wordTick) { lastWord = now }
    if (fixedTimeMs == null && !FaceLoopConfig.disabled) LaunchedEffect(Unit) {
        while (true) androidx.compose.runtime.withFrameMillis { t ->
            now = t
            if (t >= nextBlink) { blinkStart = t; blinks++; nextBlink = t + FaceMotion.nextBlinkDelayMs(blinks) }
        }
    }
    val open = FaceMotion.jaw(now, speaking, now - lastWord)
    val blink = FaceMotion.blink(now - blinkStart)
    val verts = remember(open) { FaceMotion.vertices(open) }
    val fill = remember { Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG) }
    val darker = remember { Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(.88f,0f,0f,0f,0f, 0f,.88f,0f,0f,0f, 0f,0f,.88f,0f,0f, 0f,0f,0f,1f,0f))) } }
    val mask = remember { Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN); shader = RadialGradient(0f, 0f, 1f, intArrayOf(Color.WHITE, Color.WHITE, 0), floatArrayOf(0f, .65f, 1f), Shader.TileMode.CLAMP) } }
    val mouth = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(0f, 0f, 1f, intArrayOf(0xFF2A0E10.toInt(), 0xFF3A1417.toInt(), 0x00000000), floatArrayOf(0f, .72f, 1f), Shader.TileMode.CLAMP) } }

    Canvas(modifier) {
        val sc = size.width / FaceMotion.SIZE
        drawIntoCanvas { c ->
            val cv = c.nativeCanvas
            cv.save(); cv.scale(sc, sc)
            cv.drawBitmapMesh(bmp, FaceMotion.COLS.size - 1, FaceMotion.ROWS.size - 1, verts, 0, null, 0, fill)
            if (open > 0.03f) {   // dark mouth opening just under the upper teeth (soft edge)
                val h = open * FaceMotion.MAX_JAW_PX * 1.15f
                cv.save(); cv.translate(FaceMotion.MOUTH_X, FaceMotion.MOUTH_Y + h / 2f); cv.scale(30f, h / 2f + 1.5f)
                cv.drawCircle(0f, 0f, 1f, mouth); cv.restore()
            }
            if (blink > 0.03f) FaceMotion.EYES.forEach { (ex, ey) ->   // eyelid: skin from just below the eye, feathered
                val hh = 14f * blink; val w = 34f
                val layer = RectF(ex - w - 4, ey - hh - 4, ex + w + 4, ey + hh + 4)
                cv.saveLayer(layer, null)
                cv.drawBitmap(bmp, Rect((ex - w).toInt(), (ey + 26).toInt(), (ex + w).toInt(), (ey + 26 + 2 * hh).toInt().coerceAtLeast((ey + 28).toInt())), RectF(ex - w, ey - hh, ex + w, ey + hh), darker)
                cv.save(); cv.translate(ex, ey); cv.scale(w + 3, hh + 2); cv.drawRect(-2f, -2f, 2f, 2f, mask); cv.restore()
                cv.restore()
            }
            cv.restore()
        }
    }
}

/** Tests switch the endless frame loop off so Compose can become idle; the app never changes it. */
object FaceLoopConfig { @Volatile var disabled = false }
