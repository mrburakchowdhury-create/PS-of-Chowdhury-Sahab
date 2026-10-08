package app.personal.banglaai.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.personal.banglaai.doc.DocWorkflow
import app.personal.banglaai.image.*
import app.personal.banglaai.ocr.TesseractOcr
import app.personal.banglaai.storage.VaultDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * View a stored picture: pinch-zoom/pan, rotate in 90° steps, drag-crop, then OCR just the chosen part and (if the owner wants) keep that text as the page text.
 * The encrypted original is never modified; rotation/crop exist only for viewing and OCR. Status: maths unit-tested; touch feel and OCR quality device-test pending.
 */
@Composable
fun ImageEditScreen(doc: VaultDoc, wf: DocWorkflow, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var src by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    var rot by remember { mutableIntStateOf(0) }
    var cropMode by remember { mutableStateOf(false) }
    var crop by remember { mutableStateOf(NRect.Full) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var langs by remember { mutableStateOf(setOf(TesseractOcr.Lang.ENG)) }
    var busy by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(doc.id) { src = withContext(Dispatchers.IO) { runCatching { wf.loadImage(doc) }.getOrNull() }; failed = src == null }
    val shown = remember(src, rot) { src?.let { s -> if (rot == 0) s else Bitmap.createBitmap(s, 0, 0, s.width, s.height, Matrix().apply { postRotate(rot.toFloat()) }, true) } }

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = onBack) { Text("← Back") }; Text("ছবি: ${doc.name}", Modifier.weight(1f), maxLines = 1) }
        if (failed) Text("ছবি পড়া যায়নি।", color = MaterialTheme.colorScheme.error)
        shown?.let { bmp ->
            BoxWithConstraints(Modifier.fillMaxWidth().height(420.dp).clipToBounds()) {
                val bw = constraints.maxWidth.toFloat(); val bh = constraints.maxHeight.toFloat()
                val f = CropMath.fit(bmp.width, bmp.height, bw, bh)
                if (!cropMode) {
                    Image(bmp.asImageBitmap(), "ছবি", Modifier.fillMaxSize()
                        .pointerInput(bw, bh) { detectTransformGestures { _, p, z, _ ->
                            zoom = CropMath.clampZoom(zoom * z)
                            pan = Offset(CropMath.clampPan(pan.x + p.x, zoom, bw), CropMath.clampPan(pan.y + p.y, zoom, bh))
                        } }
                        .graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = pan.x, translationY = pan.y), contentScale = ContentScale.Fit)
                } else {
                    Image(bmp.asImageBitmap(), "ছবি", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    var active by remember { mutableStateOf<Corner?>(null) }
                    Canvas(Modifier.fillMaxSize().pointerInput(f) {
                        detectDragGestures(
                            onDragStart = { o ->
                                val pts = mapOf(Corner.TL to Offset(f[0] + crop.l * f[2], f[1] + crop.t * f[3]), Corner.TR to Offset(f[0] + crop.r * f[2], f[1] + crop.t * f[3]),
                                    Corner.BL to Offset(f[0] + crop.l * f[2], f[1] + crop.b * f[3]), Corner.BR to Offset(f[0] + crop.r * f[2], f[1] + crop.b * f[3]))
                                val best = pts.minByOrNull { (it.value - o).getDistance() }
                                active = best?.takeIf { (it.value - o).getDistance() < 90f * density }?.key
                            },
                            onDragEnd = { active = null }, onDragCancel = { active = null },
                        ) { change, d -> active?.let { c -> change.consume(); crop = CropMath.drag(crop, c, d.x / f[2], d.y / f[3]) } }
                    }) {
                        val tl = Offset(f[0] + crop.l * f[2], f[1] + crop.t * f[3]); val sz = Size(crop.w * f[2], crop.h * f[3])
                        val dim = Color.Black.copy(alpha = 0.55f)
                        drawRect(dim, Offset.Zero, Size(size.width, tl.y)); drawRect(dim, Offset(0f, tl.y + sz.height), Size(size.width, size.height - tl.y - sz.height))
                        drawRect(dim, Offset(0f, tl.y), Size(tl.x, sz.height)); drawRect(dim, Offset(tl.x + sz.width, tl.y), Size(size.width - tl.x - sz.width, sz.height))
                        drawRect(SkyBlue, tl, sz, style = Stroke(3f))
                        listOf(tl, tl + Offset(sz.width, 0f), tl + Offset(0f, sz.height), tl + Offset(sz.width, sz.height)).forEach { drawCircle(SkyBlue, 14f, it) }
                    }
                }
            }
            Text(if (cropMode) "কোণের বিন্দু টেনে অংশ বেছে নিন।" else "দুই আঙুলে বড়/ছোট করুন, আঙুল টেনে সরান।${if (zoom > 1f) " (${"%.1f".format(zoom)}×)" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { rot = CropMath.rotate(rot, -90); crop = NRect.Full; zoom = 1f; pan = Offset.Zero }) { Text("⟲ ঘোরান") }
                OutlinedButton(onClick = { rot = CropMath.rotate(rot, 90); crop = NRect.Full; zoom = 1f; pan = Offset.Zero }) { Text("⟳ ঘোরান") }
                OutlinedButton(onClick = { zoom = 1f; pan = Offset.Zero }, enabled = zoom > 1f) { Text("১×") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = cropMode, onClick = { cropMode = !cropMode; zoom = 1f; pan = Offset.Zero }, label = { Text("✂️ কাটুন") })
                if (cropMode) OutlinedButton(onClick = { crop = NRect.Full }) { Text("পুরো ছবি") }
            }
            Text("লেখা পড়ুন (${if (cropMode && crop != NRect.Full) "নির্বাচিত অংশ" else "পুরো ছবি"}) — ভাষা:", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { TesseractOcr.Lang.entries.forEach { l -> FilterChip(selected = l in langs, onClick = { langs = if (l in langs) langs - l else langs + l }, label = { Text(l.label) }) } }
            Button(enabled = !busy && langs.isNotEmpty(), onClick = {
                busy = true; msg = null
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            val r = CropMath.toPixels(if (cropMode) crop else NRect.Full, bmp.width, bmp.height)
                            val part = Bitmap.createBitmap(bmp, r[0], r[1], r[2], r[3])
                            try { TesseractOcr(ctx).use { it.recognize(part, langs.toList()).text } } finally { if (part !== bmp) part.recycle() }
                        }
                    }.onSuccess { text = it; if (it.isBlank()) msg = "কোনো লেখা পাওয়া যায়নি (অন্য ভাষা বা পরিষ্কার অংশ চেষ্টা করুন)" }.onFailure { msg = "OCR ব্যর্থ: ${it.message ?: it.javaClass.simpleName}" }
                    busy = false
                }
            }) { Text(if (busy) "পড়া হচ্ছে…" else "🔎 লেখা পড়ুন (OCR)") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            msg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (text.isNotBlank()) {
                OutlinedTextField(text, { text = it }, label = { Text("পাওয়া লেখা (ভুল থাকতে পারে — ঠিক করে নিন)") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { wf.saveOcrText(doc, text); msg = "পৃষ্ঠার লেখা হিসেবে সংরক্ষিত। এখন অনুবাদ/ব্যাখ্যা করা যাবে।" }) { Text("এটিকে এই পৃষ্ঠার লেখা হিসেবে রাখুন") }
            }
        }
    }
}
