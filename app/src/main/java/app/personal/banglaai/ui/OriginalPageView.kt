package app.personal.banglaai.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.personal.banglaai.doc.DocWorkflow
import app.personal.banglaai.storage.VaultDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The original PDF page as a picture (pinch to zoom, drag, rotate) so extracted text can be compared with the real page. Nothing is written anywhere; leaving the screen or locking drops the picture. */
@Composable
fun OriginalPageView(doc: VaultDoc, wf: DocWorkflow, page: Int) {
    var shown by remember(doc.id) { mutableStateOf(false) }
    if (!shown) { OutlinedButton(onClick = { shown = true }, modifier = Modifier.fillMaxWidth()) { Text("🖼 মূল পৃষ্ঠা দেখুন (পৃষ্ঠা $page)") }; return }
    var bmp by remember(doc.id, page) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var err by remember(doc.id, page) { mutableStateOf<String?>(null) }
    var scale by remember(doc.id, page) { mutableFloatStateOf(1f) }
    var off by remember(doc.id, page) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var rot by remember(doc.id, page) { mutableFloatStateOf(0f) }
    LaunchedEffect(doc.id, page) {
        val r = withContext(Dispatchers.IO) { wf.renderOriginal(doc, page) }
        bmp = r.getOrNull(); err = r.exceptionOrNull()?.let { "পৃষ্ঠার ছবি তৈরি হয়নি: ${it.message ?: it.javaClass.simpleName}" }
    }
    DisposableEffect(doc.id, page) { onDispose { bmp?.recycle(); bmp = null } }
    val state = rememberTransformableState { zoom, pan, _ -> scale = (scale * zoom).coerceIn(1f, 6f); off += pan }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("মূল পৃষ্ঠা $page — ছবি", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val b = bmp
        if (b == null && err == null) Text("পৃষ্ঠা আঁকা হচ্ছে…")
        if (b != null) Box(Modifier.fillMaxWidth().heightIn(max = 420.dp).clipToBounds().transformable(state)) {
            Image(b.asImageBitmap(), "মূল পৃষ্ঠা $page", Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale; translationX = off.x; translationY = off.y; rotationZ = rot }, contentScale = ContentScale.FillWidth)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { rot = (rot + 90f) % 360f }) { Text("⟳ ঘোরান") }
            OutlinedButton(onClick = { scale = 1f; off = androidx.compose.ui.geometry.Offset.Zero; rot = 0f }) { Text("রিসেট") }
            OutlinedButton(onClick = { shown = false }) { Text("Close") }
        }
    }
}
