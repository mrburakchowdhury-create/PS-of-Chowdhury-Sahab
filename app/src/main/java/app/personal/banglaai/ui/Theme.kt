package app.personal.banglaai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

/**
 * "Cyber navy" look in the owner's logo colours: deep navy background, electric-cyan primary, gold secondary, terminal-green accent.
 * Always dark (the app is private and the artwork is navy). Contrast ratios (WCAG, computed): body text on background 17.5:1, on cards 13.9:1,
 * muted text 8.5:1, cyan on card 10:1, button text on cyan 11.5:1, error on card 6.3:1 — all above the 4.5:1 minimum.
 */
object Ps {
    val Navy = Color(0xFF000C21); val NavyHigh = Color(0xFF031433); val Surface = Color(0xFF06173A); val Card = Color(0xFF0B2150); val Container = Color(0xFF12306B)
    val Cyan = Color(0xFF19E3FF); val Gold = Color(0xFFF2C14E); val Green = Color(0xFF3DFF9A); val Ice = Color(0xFFEAF3FF); val Muted = Color(0xFFA9C1E6)
    val Error = Color(0xFFFF7B97); val Outline = Color(0xFF4A78C2)

    val colors = darkColorScheme(
        primary = Cyan, onPrimary = Color(0xFF001A26), primaryContainer = Color(0xFF00394A), onPrimaryContainer = Color(0xFFBFF5FF),
        secondary = Gold, onSecondary = Color(0xFF1A1200), secondaryContainer = Container, onSecondaryContainer = Ice,
        tertiary = Green, onTertiary = Color(0xFF00210F), tertiaryContainer = Color(0xFF003A21), onTertiaryContainer = Color(0xFFB6FFD6),
        background = Navy, onBackground = Ice, surface = Surface, onSurface = Ice, surfaceVariant = Card, onSurfaceVariant = Muted,
        surfaceContainerLowest = Navy, surfaceContainerLow = Surface, surfaceContainer = Card, surfaceContainerHigh = Container, surfaceContainerHighest = Container,
        outline = Outline, outlineVariant = Color(0xFF1D3F78), error = Error, onError = Color(0xFF3B0012), errorContainer = Color(0xFF5C1226), onErrorContainer = Color(0xFFFFD9E1),
    )

    /** Light option: same hues, darkened so every text/background pair stays above 4.5:1 (primary 0A5C70 on F6FAFF ≈ 7.2:1, text 0A1B3D ≈ 16:1). */
    val lightColors = lightColorScheme(
        primary = Color(0xFF0A5C70), onPrimary = Color.White, primaryContainer = Color(0xFFCDEFF8), onPrimaryContainer = Color(0xFF001F28),
        secondary = Color(0xFF7A5900), onSecondary = Color.White, secondaryContainer = Color(0xFFFFE9B0), onSecondaryContainer = Color(0xFF261A00),
        tertiary = Color(0xFF006B3C), onTertiary = Color.White, tertiaryContainer = Color(0xFFB6F2D0), onTertiaryContainer = Color(0xFF002111),
        background = Color(0xFFF6FAFF), onBackground = Color(0xFF0A1B3D), surface = Color(0xFFFFFFFF), onSurface = Color(0xFF0A1B3D),
        surfaceVariant = Color(0xFFE3ECF8), onSurfaceVariant = Color(0xFF33496E),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF0F5FC), surfaceContainer = Color(0xFFE8EFFA), surfaceContainerHigh = Color(0xFFDDE7F5), surfaceContainerHighest = Color(0xFFD3DFF0),
        outline = Color(0xFF5B76A3), outlineVariant = Color(0xFFB9C8E0), error = Color(0xFFB3002F), onError = Color.White, errorContainer = Color(0xFFFFD9E1), onErrorContainer = Color(0xFF40000F),
    )

    private val base = Typography()
    val typography = Typography(
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 0.3.sp),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 23.sp),
        bodySmall = base.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    )
    val Gradient = Brush.verticalGradient(listOf(NavyHigh, Navy))

    /** Technical text (checksums, code) — monospace, still readable. */
    val Mono = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
}

/** [fontScale] is the owner's own text-size choice (0.85–1.6) applied on top of the phone's font setting. */
@Composable
fun PsTheme(fontScale: Float, mode: String = "dark", content: @Composable () -> Unit) {
    val d = LocalDensity.current
    val dark = when (mode) { "light" -> false; "system" -> androidx.compose.foundation.isSystemInDarkTheme(); else -> true }
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * fontScale)) {
        MaterialTheme(colorScheme = if (dark) Ps.colors else Ps.lightColors, typography = Ps.typography) {
            // Plain Text() must default to the light "ice" colour: a transparent Surface would otherwise leave it black on navy.
            CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                Box(Modifier.fillMaxSize().background(if (dark) Ps.Gradient else Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background)))) { content() }
            }
        }
    }
}
