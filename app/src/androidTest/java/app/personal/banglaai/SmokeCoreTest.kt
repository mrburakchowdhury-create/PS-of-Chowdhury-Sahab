package app.personal.banglaai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.personal.banglaai.backup.BackupService
import app.personal.banglaai.doc.DocWorkflow
import app.personal.banglaai.doc.PageText
import app.personal.banglaai.export.ExportOptions
import app.personal.banglaai.export.Exporter
import app.personal.banglaai.export.PdfExport
import app.personal.banglaai.jobs.JobStatus
import app.personal.banglaai.jobs.PageStatus
import app.personal.banglaai.ocr.TesseractOcr
import app.personal.banglaai.security.BiometricGate
import app.personal.banglaai.security.PinStore
import app.personal.banglaai.storage.ModelStore
import app.personal.banglaai.storage.Vault
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/** Runs on a REAL Android runtime (emulator): real Keystore, real Tink, real PDFBox, real Tesseract natives, real text stack. No model is present here. */
@RunWith(AndroidJUnit4::class)
class SmokeCoreTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("SMOKE", s)

    @Test fun pinOnRealPrefs() {
        ctx.getSharedPreferences("auth", 0).edit().clear().commit()
        val p = PinStore(ctx); p.set("246810".toCharArray())
        assertEquals(PinStore.Result.Ok, p.verify("246810".toCharArray()))
        assertTrue(p.verify("000000".toCharArray()) is PinStore.Result.Wrong)
        log("PIN ok")
    }

    @Test fun biometricKeystoreBehaviourIsReported() {
        val r = runCatching { BiometricGate.prepare() }
        log("BiometricGate.prepare -> ${r.getOrNull()?.javaClass?.simpleName ?: ("EXCEPTION " + r.exceptionOrNull())}")
        // no assertion on success: an emulator without a secure lock screen may legitimately refuse; the app must merely not crash (MainActivity wraps this call)
    }

    @Test fun vaultExtractExportBackupRoundTrip() {
        PDFBoxResourceLoader.init(ctx)
        val f = File(ctx.cacheDir, "smoke.pdf")
        PDDocument().use { d ->
            val pages = (1..20).map { i -> PDPage().also { p -> d.addPage(p)
                PDPageContentStream(d, p).use { c -> c.beginText(); c.setFont(PDType1Font.HELVETICA, 12f); c.newLineAtOffset(50f, 700f); c.showText("Page $i hello world"); c.endText() } } }
            val o = PDDocumentOutline(); d.documentCatalog.documentOutline = o
            fun item(t: String, pg: PDPage) = PDOutlineItem().also { it.title = t; it.destination = PDPageFitDestination().also { dd -> dd.page = pg } }
            o.addLast(item("Chapter A", pages[0])); o.addLast(item("Chapter B", pages[9])); d.save(f)
        }
        val keys = app.personal.banglaai.security.KeyVault(ctx); check(keys.unlockWithPin("246810".toCharArray())); val vault = Vault(ctx, keys)
        vault.list().forEach { vault.delete(it.id) }
        val doc = vault.import(Uri.fromFile(f))
        assertEquals("smoke.pdf", doc.name)
        assertTrue("encrypted on disk", File(ctx.noBackupFilesDir, "vault/${doc.id}.enc").readBytes().let { String(it, Charsets.ISO_8859_1).contains("%PDF").not() })
        val wf = DocWorkflow(ctx, vault, ModelStore(ctx))
        assertTrue(wf.isSupported(doc))
        val st = wf.extract(doc, false) { _, _ -> }
        assertEquals(JobStatus.DONE, st)
        val job = wf.job(doc, DocWorkflow.Kind.Extract)!!
        assertEquals(20, job.count(PageStatus.DONE))
        assertTrue(wf.texts.get(doc.id, 3)!!.text.contains("Page 3 hello"))
        assertEquals(listOf("Chapter A" to 1, "Chapter B" to 10), wf.chapters(doc).map { it.title to it.page })
        assertTrue("temp plaintext removed", File(ctx.cacheDir, "pdf-${doc.id}").exists().not())
        log("extract ok: ${job.coverageReport()}")

        // Bengali translation stand-in (the real model is not on the emulator) to exercise export text shaping on the real Android stack
        wf.translations.put(doc.id, PageText(1, "বাংলা অনুবাদ: জ্ঞান অর্জন করা সবার জন্য জরুরি।", null))
        val pages = wf.exportPages(doc); val o = ExportOptions("smoke", true, false)
        assertTrue(Exporter.txt(pages, o).contains("জ্ঞান অর্জন"))
        val docx = ByteArrayOutputStream().also { Exporter.docx(pages, o, it) }.toByteArray(); assertTrue(docx.size > 500 && docx[0] == 'P'.code.toByte())
        val pdf = ByteArrayOutputStream().also { PdfExport.write(pages, o, it) }.toByteArray()
        assertTrue("PDF export header", String(pdf, 0, 5) == "%PDF-")
        log("export ok: docx=${docx.size}B pdf=${pdf.size}B")

        // backup -> wipe -> restore
        val bo = ByteArrayOutputStream(); val svc = BackupService(vault, wf)
        svc.create(bo, "correct horse battery".toCharArray())
        vault.delete(doc.id); wf.deleteDerived(doc)
        assertTrue(vault.list().none { it.id == doc.id })
        val rep = svc.restore(ByteArrayInputStream(bo.toByteArray()), "correct horse battery".toCharArray())
        assertEquals(1, rep.restored)
        assertTrue(wf.texts.get(doc.id, 3)!!.text.contains("Page 3 hello"))
        assertEquals(20, wf.job(doc, DocWorkflow.Kind.Extract)!!.total)
        assertThrows(Exception::class.java) { svc.restore(ByteArrayInputStream(bo.toByteArray()), "wrong passphrase!!".toCharArray()) }
        log("backup/restore ok (${bo.size()} B)")
    }

    @Test fun tesseractRunsOnRealNativeLibs() {
        fun draw(text: String, size: Float): Bitmap {
            val b = Bitmap.createBitmap(1400, 260, Bitmap.Config.ARGB_8888); val c = Canvas(b); c.drawColor(Color.WHITE)
            c.drawText(text, 30f, 170f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = size }); return b
        }
        TesseractOcr(ctx).use { t ->
            val en = t.recognize(draw("Hello World 2026", 110f), listOf(TesseractOcr.Lang.ENG), 6)
            log("OCR eng -> [${en.text}] conf=${en.meanConf}"); assertTrue(en.text.contains("Hello"))
            val bn = t.recognize(draw("জ্ঞান অর্জন করা", 110f), listOf(TesseractOcr.Lang.BEN), 6)
            log("OCR ben -> [${bn.text}] conf=${bn.meanConf}"); assertTrue("some Bengali text read", bn.text.isNotBlank())
        }
    }
}
