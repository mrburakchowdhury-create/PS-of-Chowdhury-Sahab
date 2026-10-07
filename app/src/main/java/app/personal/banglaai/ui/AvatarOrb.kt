package app.personal.banglaai.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.personal.banglaai.call.VoiceCallMachine.Phase

val SkyBlue = Color(0xFF5CC8FF)

/**
 * The call "face": the owner's photo inside a round sky-blue glowing ring. The motion is honest, not a fake face:
 *  - always: slow breathing and a tiny sway;
 *  - listening: the ring swells with the real microphone level ([level]);
 *  - thinking: a light arc circles the ring;
 *  - speaking: a soft pulse on every spoken word ([wordTick] changes per word) and small equaliser bars under the photo.
 * It does not move the lips of the photo (that would need a face-animation model).
 */
@Composable
fun AvatarOrb(photo: ImageBitmap?, phase: Phase, level: Float, wordTick: Int, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "orb")
    val breath by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "spin")
    val bars by t.animateFloat(0f, 1f, infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse), label = "bars")
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(wordTick) { if (wordTick > 0) { pulse.snapTo(1f); pulse.animateTo(0f, tween(260)) } }
    val lv by animateFloatAsState(if (phase == Phase.Listening) level else 0f, tween(120), label = "level")
    val ring = when (phase) { Phase.Listening -> 0.35f + 0.65f * lv; Phase.Speaking -> 0.45f + 0.55f * pulse.value; Phase.Thinking -> 0.5f; else -> 0.25f }

    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2); val base = size.minDimension * 0.34f
            for (i in 3 downTo 1) {   // soft glow rings, growing with activity
                val r = base * (1f + 0.10f * i + 0.10f * ring * i) + base * 0.03f * breath
                drawCircle(SkyBlue.copy(alpha = (0.07f + 0.10f * ring) / i * 1.6f), r, c)
            }
            drawCircle(SkyBlue, base * 1.04f, c, style = Stroke(width = size.minDimension * 0.012f))
            if (phase == Phase.Thinking) drawArc(Color.White, spin, 70f, false, Offset(c.x - base * 1.12f, c.y - base * 1.12f), Size(base * 2.24f, base * 2.24f),
                style = Stroke(width = size.minDimension * 0.016f, cap = StrokeCap.Round))
        }
        val s = 1f + 0.015f * breath + 0.035f * pulse.value
        val photoMod = Modifier.fillMaxSize(0.68f).scale(s).rotate((breath - 0.5f) * 2.4f).clip(CircleShape)
        if (photo != null) Image(photo, "আপনার ছবি", photoMod, contentScale = ContentScale.Crop)
        else TalkingFace(phase == Phase.Speaking, wordTick, photoMod)
        if (phase == Phase.Speaking) Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until 5) {
                val h = 6f + 18f * (if (i % 2 == 0) bars else 1f - bars) * (0.5f + 0.5f * pulse.value.coerceAtLeast(0.3f))
                Box(Modifier.width(5.dp).height(h.dp).clip(RoundedCornerShape(3.dp)).background(SkyBlue))
            }
        }
    }
}
