package app.personal.banglaai.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    private fun ratio(a: Color, b: Color): Double { val l1 = a.luminance().toDouble(); val l2 = b.luminance().toDouble(); return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05) }

    @Test fun bothSchemesKeepTextAbove4_5To1() {
        for ((name, c) in listOf("dark" to Ps.colors, "light" to Ps.lightColors)) {
            val pairs = listOf("text/background" to (c.onBackground to c.background), "text/surface" to (c.onSurface to c.surface), "muted/variant" to (c.onSurfaceVariant to c.surfaceVariant),
                "primary/background" to (c.primary to c.background), "secondary/background" to (c.secondary to c.background), "tertiary/background" to (c.tertiary to c.background),
                "error/background" to (c.error to c.background), "onPrimary/primary" to (c.onPrimary to c.primary), "onError/error" to (c.onError to c.error))
            for ((label, p) in pairs) assertTrue("$name $label = ${"%.2f".format(ratio(p.first, p.second))}", ratio(p.first, p.second) >= 4.5)
        }
    }
}
