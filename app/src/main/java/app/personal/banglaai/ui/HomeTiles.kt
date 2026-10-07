package app.personal.banglaai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One home button: rounded card, round coloured icon badge on top, coloured label band below (each job has its own colour). All label/band pairs keep white text ≥ 4.5:1. */
class Tile(val label: String, val icon: ImageVector, val accent: Color, val band: Color, val enabled: Boolean = true, val onClick: () -> Unit)

@Composable
fun HomeTile(t: Tile, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.alpha(if (t.enabled) 1f else 0.45f).clip(shape)
            .background(Brush.verticalGradient(listOf(t.accent.copy(alpha = 0.22f), Color(0xFF071A38))))
            .clickable(enabled = t.enabled, role = Role.Button, onClick = t.onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(74.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(50.dp).clip(CircleShape).background(Brush.linearGradient(listOf(t.accent, t.band))), contentAlignment = Alignment.Center) {
                Icon(t.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 46.dp).background(t.band).padding(horizontal = 6.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
            Text(t.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 17.sp)
        }
    }
}

/** Three tiles per row, equal widths (short last row is padded with empty space). */
@Composable
fun TileGrid(tiles: List<Tile>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { HomeTile(it, Modifier.weight(1f).fillMaxHeight()) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

object TileStyle {
    // accent (icon badge / glow) and band (label background) per job
    val call = Color(0xFF29B6F6) to Color(0xFF0B5E8A)
    val chat = Color(0xFFB388FF) to Color(0xFF5B3A9E)
    val add = Color(0xFF4CD08A) to Color(0xFF1B6B45)
    val files = Color(0xFFF2C14E) to Color(0xFF7A5608)
    val camera = Color(0xFFFF7BA9) to Color(0xFF9C2E5B)
    val voice = Color(0xFF19E3FF) to Color(0xFF0E6B78)
    val code = Color(0xFF7C9CFF) to Color(0xFF3040A0)
    val model = Color(0xFFFF9B54) to Color(0xFF9A4510)
    val terms = Color(0xFFA5E04F) to Color(0xFF4C6F12)
    val settings = Color(0xFF9FB4D0) to Color(0xFF3F5470)
    val diag = Color(0xFFFF6B6B) to Color(0xFF9C2B2B)
    val status = Color(0xFF56CCF2) to Color(0xFF24607A)
}
