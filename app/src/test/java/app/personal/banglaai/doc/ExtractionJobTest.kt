package app.personal.banglaai.doc

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.personal.banglaai.jobs.JobState
import app.personal.banglaai.jobs.JobStatus
import app.personal.banglaai.jobs.JobStore
import app.personal.banglaai.jobs.PageStatus
import app.personal.banglaai.storage.Vault
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Synthetic-PDF tests (NOT real books, NOT on the phone). Timings printed here are from the build machine's JVM only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExtractionJobTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var aead: StreamingAead
    private lateinit var jobs: JobStore
    private lateinit var texts: PageTextStore

    @Before fun setUp() {
        PdfExtractor.init(ApplicationProvider.getApplicationContext<Context>())
        StreamingAeadConfig.register()
        aead = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM_HKDF_4KB")).getPrimitive(StreamingAead::class.java)
        val seal = { b: ByteArray, a: ByteArray -> Vault.sealWith(aead, b, a) }
        val unseal = { b: ByteArray, a: ByteArray -> Vault.unsealWith(aead, b, a) }
        jobs = JobStore(tmp.newFolder("jobs"), seal, unseal)
        texts = PageTextStore(tmp.newFolder("texts"), seal, unseal)
    }

    /** blankPages: physical pages left empty (stand-in for scanned pages with no text layer). */
    private fun makePdf(pages: Int, blankPages: Set<Int> = emptySet()): File {
        val f = tmp.newFile("synthetic$pages.pdf")
        PDDocument().use { d ->
            for (i in 1..pages) {
                val p = PDPage(); d.addPage(p)
                if (i in blankPages) continue
                PDPageContentStream(d, p).use { c ->
                    c.beginText(); c.setFont(PDType1Font.HELVETICA, 11f); c.newLineAtOffset(50f, 700f)
                    c.showText("Page $i synthetic line one"); c.newLineAtOffset(0f, -14f)
                    c.showText("Chapter ${(i - 1) / 50 + 1} paragraph ${i * 7} end"); c.endText()
                }
            }
            d.save(f)
        }
        return f
    }

    private fun open(f: File): PdfExtractor =
        (PdfExtractor.open(f.inputStream(), tmp.newFolder()) as PdfExtractor.OpenResult.Ok).extractor

    private fun runFull(pages: Int, blank: Set<Int>) {
        val f = makePdf(pages, blank)
        val t0 = System.nanoTime()
        val rt = Runtime.getRuntime(); System.gc(); val m0 = rt.totalMemory() - rt.freeMemory()
        open(f).use { ex ->
            assertEquals(pages, ex.pageCount)
            val job = JobState("j$pages", "doc$pages", "extract", pages)
            val st = ExtractionJob(job, ex, texts, jobs).run()
            assertEquals(JobStatus.DONE, st)
            assertEquals(pages - blank.size, job.count(PageStatus.DONE))
            assertEquals(blank.size, job.count(PageStatus.UNREADABLE))
            assertEquals(0, job.count(PageStatus.PENDING))
            // spot check content + page mapping, including reload of the persisted checkpoint
            assertTrue(texts.get("doc$pages", 3)!!.text.contains("Page 3 synthetic"))
            assertTrue(texts.get("doc$pages", 498)!!.text.contains("Page 498 synthetic"))
            assertNull(texts.get("doc$pages", blank.first()))
            val back = jobs.load("j$pages")!!
            assertEquals(blank.size, back.count(PageStatus.UNREADABLE))
            println("coverage($pages): " + back.coverageReport())
        }
        println("RESULT pages=$pages seconds=%.1f heapDeltaMB=%d (build-machine JVM, not S24)".format(
            (System.nanoTime() - t0) / 1e9, (rt.totalMemory() - rt.freeMemory() - m0) / (1 shl 20)))
    }

    @Test fun pages500() = runFull(500, setOf(7, 250, 499))
    @Test fun pages1000() = runFull(1000, setOf(1, 500, 1000))

    @Test fun pauseThenResumeNeverRedoesFinishedPages() {
        val f = makePdf(100)
        open(f).use { ex ->
            val job = JobState("jp", "docp", "extract", 100)
            val runner = ExtractionJob(job, ex, texts, jobs, checkpointEvery = 10)
            val st = runner.run { done, _ -> if (done == 40) runner.pause() }
            assertEquals(JobStatus.PAUSED, st)
            assertEquals(40, job.count(PageStatus.DONE))
            // simulated app restart: reload checkpoint, status must not auto-resume
            val reloaded = jobs.load("jp")!!
            assertEquals(JobStatus.PAUSED, reloaded.status)
            assertEquals(60, reloaded.remaining().size)
            assertEquals(41, reloaded.remaining().first())
            assertEquals(JobStatus.DONE, ExtractionJob(reloaded, ex, texts, jobs).run())
            assertEquals(100, reloaded.count(PageStatus.DONE))
        }
    }

    @Test fun cancelledJobStaysCancelledAndKeepsDoneWork() {
        open(makePdf(60)).use { ex ->
            val job = JobState("jc", "docc", "extract", 60)
            val runner = ExtractionJob(job, ex, texts, jobs)
            val st = runner.run { done, _ -> if (done == 10) runner.cancel() }
            assertEquals(JobStatus.CANCELLED, st)
            assertEquals(JobStatus.CANCELLED, jobs.load("jc")!!.status)
            assertEquals(10, jobs.load("jc")!!.count(PageStatus.DONE))
        }
    }

    @Test fun runningStatusBecomesPausedAfterCrash() {
        val j = JobState("jr", "d", "extract", 5).apply { status = JobStatus.RUNNING }
        jobs.save(j)
        assertEquals(JobStatus.PAUSED, jobs.load("jr")!!.status)
    }

    @Test fun garbageFileIsReportedMalformedNotCrash() {
        val r = PdfExtractor.open("not a pdf at all".byteInputStream(), tmp.newFolder())
        assertTrue(r is PdfExtractor.OpenResult.Malformed)
    }

    @Test fun storedPageTextIsEncryptedOnDisk() {
        texts.put("docz", PageText(1, "গোপন লেখা SECRETWORD", "iv"))
        val raw = File(tmp.root, "texts/docz/1.txt.enc").readBytes()
        assertFalse(String(raw, Charsets.ISO_8859_1).contains("SECRETWORD"))
        assertEquals("iv", texts.get("docz", 1)!!.printedLabel)
        assertEquals("গোপন লেখা SECRETWORD", texts.get("docz", 1)!!.text)
    }
}
