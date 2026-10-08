package app.personal.banglaai.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.OutputStream

/** One captured page waiting to be saved (not yet in the vault). [rotation] is applied when saving; the camera file itself is untouched until then. */
class ScanPage(val file: File, rotation: Int = 0) { var rotation by mutableIntStateOf(rotation) }

object ScanPdf {
    /** Decodes [f] no larger than about [maxSide] px on its long side (bounded memory) and applies [rotation] degrees. */
    fun load(f: File, rotation: Int, maxSide: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(f.path, o)
        if (o.outWidth <= 0) return null
        var s = 1; while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= maxSide) s *= 2
        val b = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        return if (rotation % 360 == 0) b else Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(rotation.toFloat()) }, true).also { if (it !== b) b.recycle() }
    }

    /** Several pages -> one PDF (one page per photo, page size = photo size scaled to 595pt wide), one bitmap in memory at a time. */
    fun write(pages: List<ScanPage>, out: OutputStream) {
        val pdf = PdfDocument()
        try {
            pages.forEachIndexed { i, p ->
                val bmp = load(p.file, p.rotation, 2200) ?: throw java.io.IOException("পৃষ্ঠা ${i + 1} পড়া যায়নি")
                val w = 595; val h = (w.toFloat() * bmp.height / bmp.width).toInt().coerceAtLeast(1)
                val pg = pdf.startPage(PdfDocument.PageInfo.Builder(w, h, i + 1).create())
                pg.canvas.drawBitmap(bmp, null, android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat()), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                bmp.recycle()
                pdf.finishPage(pg)
            }
            pdf.writeTo(out)
        } finally { pdf.close() }
    }

    /** One photo with its rotation baked in, as JPEG bytes. */
    fun jpeg(p: ScanPage): ByteArray {
        val bmp = load(p.file, p.rotation, 4096) ?: throw java.io.IOException("ছবি পড়া যায়নি")
        return java.io.ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it); bmp.recycle() }.toByteArray()
    }
}

/** Shown after the camera: look at every captured page, rotate, retake the last one, add more pages, discard all, or save (nothing is stored until Save). */
@Composable
fun ScanReviewScreen(pages: List<ScanPage>, busy: Boolean, onAddPage: () -> Unit, onRetakeLast: () -> Unit, onDiscard: () -> Unit, onSave: () -> Unit) {
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Scan review — ${pages.size} page${if (pages.size == 1) "" else "s"}", style = MaterialTheme.typography.titleMedium)
        Text("সংরক্ষণ (Save) না চাপা পর্যন্ত কিছু ভল্টে রাখা হয় না। ছবি ঘোরানো যায়; ক্রপ ও OCR সংরক্ষণের পর খোলা পর্দায় করা যায়।", style = MaterialTheme.typography.bodySmall)
        pages.forEachIndexed { i, p ->
            val bmp by produceState<Bitmap?>(null, p.file, p.rotation) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ScanPdf.load(p.file, p.rotation, 900) } }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Page ${i + 1}", style = MaterialTheme.typography.labelMedium)
                    bmp?.let { Image(it.asImageBitmap(), contentDescription = "Page ${i + 1}", modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) } ?: Text("…")
                    OutlinedButton(enabled = !busy, onClick = { p.rotation = (p.rotation + 90) % 360 }) { Text("Rotate") }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy, onClick = onAddPage) { Text("Add page") }
            OutlinedButton(enabled = !busy && pages.isNotEmpty(), onClick = onRetakeLast) { Text("Retake last") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy && pages.isNotEmpty(), onClick = onSave) { Text(if (busy) "Saving…" else if (pages.size > 1) "Save as PDF" else "Save") }
            OutlinedButton(enabled = !busy, onClick = onDiscard) { Text("Discard") }
        }
    }
}
