package app.personal.banglaai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.personal.banglaai.doc.DocWorkflow
import app.personal.banglaai.jobs.JobStatus
import app.personal.banglaai.jobs.PageStatus
import app.personal.banglaai.ocr.TesseractOcr
import app.personal.banglaai.security.KeyVault
import app.personal.banglaai.storage.ModelStore
import app.personal.banglaai.storage.Vault
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Clean-install OCR check on a REAL Android runtime: the five bundled language packs are copied from the APK's own assets (no earlier install's files are relied on:
 * the app's tessdata folder is deleted first), then photo-style bitmaps and a scanned (image-only) PDF are recognised. Text is drawn with the device's own fonts.
 */
@RunWith(AndroidJUnit4::class)
class OcrAllLanguagesTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("OCR16K", s)
    private fun draw(text: String, size: Float = 96f): Bitmap {
        val b = Bitmap.createBitmap(1400, 260, Bitmap.Config.ARGB_8888); val c = Canvas(b); c.drawColor(Color.WHITE)
        c.drawText(text, 30f, 170f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = size }); return b
    }
    private fun ratio(a: String, b: String): Double {
        val x = a.filter { !it.isWhitespace() }; val y = b.filter { !it.isWhitespace() }; if (x.isEmpty() && y.isEmpty()) return 1.0
        val d = Array(x.length + 1) { IntArray(y.length + 1) { 0 } }
        for (i in 0..x.length) d[i][0] = i; for (j in 0..y.length) d[0][j] = j
        for (i in 1..x.length) for (j in 1..y.length) d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (x[i - 1] == y[j - 1]) 0 else 1)
        return 1.0 - d[x.length][y.length].toDouble() / maxOf(x.length, y.length)
    }

    @Test fun allFiveLanguagePacksComeFromTheApkAndRecogniseText() {
        assertTrue("APK must carry all packs", TesseractOcr.missingPacks(ctx).isEmpty())
        File(ctx.noBackupFilesDir, "tess").deleteRecursively()   // clean install: nothing from an earlier run
        val samples = listOf(
            TesseractOcr.Lang.ENG to "Hello World 2026", TesseractOcr.Lang.BEN to "জ্ঞান অর্জন করা", TesseractOcr.Lang.ARA to "العلم نور والجهل ظلام",
            TesseractOcr.Lang.URD to "علم حاصل کرنا ضروری ہے", TesseractOcr.Lang.HIN to "पुस्तकालय में कहानी")
        TesseractOcr(ctx).use { t ->
            for ((l, text) in samples) {
                val r = t.recognize(draw(text), listOf(l), 6); val sim = ratio(text, r.text)
                log("${l.code}: expected=[$text] got=[${r.text}] similarity=${"%.2f".format(sim)} conf=${r.meanConf}")
                assertTrue("${l.code} similarity $sim", sim >= 0.5)
            }
        }
        for (l in TesseractOcr.Lang.entries) assertEquals(l.size, File(ctx.noBackupFilesDir, "tess/tessdata/${l.code}.traineddata").length())
    }

    @Test fun photoAndScannedPdfGoThroughTheRealWorkflow() {
        val keys = KeyVault(ctx); check(keys.unlockWithPin("246810".toCharArray())); val vault = Vault(ctx, keys)
        vault.list().forEach { vault.delete(it.id) }; val wf = DocWorkflow(ctx, vault, ModelStore(ctx))
        // photo (PNG) of Arabic text
        val png = File(ctx.cacheDir, "photo.png"); FileOutputStream(png).use { draw("العلم نور والجهل ظلام").compress(Bitmap.CompressFormat.PNG, 100, it) }
        val photo = vault.import(Uri.fromFile(png)); assertTrue(wf.isImage(photo))
        assertEquals(JobStatus.DONE, wf.extract(photo, false) { _, _ -> })
        assertEquals(JobStatus.DONE, wf.ocr(photo, listOf(TesseractOcr.Lang.ARA)) { _, _ -> })
        val ar = wf.texts.get(photo.id, 1)!!; log("photo ara -> [${ar.text}]"); assertTrue(ar.ocr && ratio("العلم نور والجهل ظلام", ar.text) >= 0.5)
        // scanned PDF: pages are pictures only (no text layer)
        val pdfFile = File(ctx.cacheDir, "scan.pdf")
        PdfDocument().also { d ->
            listOf("Hello World 2026", "জ্ঞান অর্জন করা").forEachIndexed { i, t ->
                val p = d.startPage(PdfDocument.PageInfo.Builder(1400, 260, i + 1).create()); p.canvas.drawBitmap(draw(t), 0f, 0f, null); d.finishPage(p) }
            FileOutputStream(pdfFile).use { d.writeTo(it) }; d.close() }
        val scan = vault.import(Uri.fromFile(pdfFile))
        assertEquals(JobStatus.DONE, wf.extract(scan, false) { _, _ -> })
        val job = wf.job(scan, DocWorkflow.Kind.Extract)!!; assertEquals("no text layer -> both pages unreadable before OCR", 2, job.count(PageStatus.UNREADABLE))
        assertEquals(JobStatus.DONE, wf.ocr(scan, listOf(TesseractOcr.Lang.ENG, TesseractOcr.Lang.BEN)) { _, _ -> })
        val p1 = wf.texts.get(scan.id, 1)!!.text; val p2 = wf.texts.get(scan.id, 2)!!.text; log("scan p1=[$p1] p2=[$p2]")
        assertTrue(p1.contains("Hello")); assertTrue(ratio("জ্ঞান অর্জন করা", p2) >= 0.5)
        assertTrue("temporary plaintext removed", File(ctx.cacheDir, "pdf-${scan.id}").exists().not())
    }
}
