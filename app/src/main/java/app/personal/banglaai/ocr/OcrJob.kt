package app.personal.banglaai.ocr

import app.personal.banglaai.doc.PageText
import app.personal.banglaai.doc.PageTextStore
import app.personal.banglaai.doc.PdfExtractor
import app.personal.banglaai.jobs.JobState
import app.personal.banglaai.jobs.JobStatus
import app.personal.banglaai.jobs.JobStore
import app.personal.banglaai.jobs.PageStatus
import java.util.concurrent.atomic.AtomicBoolean

/**
 * OCR for pages the text extraction marked UNREADABLE (no text layer). Runs only when the user presses the OCR button.
 * Results are stored flagged ocr=true and are never mixed silently with real extracted text. Pages where OCR also finds
 * nothing stay UNREADABLE; they are not guessed.
 */
class OcrJob(
    private val job: JobState, private val ex: PdfExtractor, private val ocr: TesseractOcr,
    private val langs: List<TesseractOcr.Lang>, private val texts: PageTextStore, private val jobs: JobStore,
    private val checkpointEvery: Int = 5,
) {
    private val stop = AtomicBoolean(false)
    @Volatile var stopStatus = JobStatus.PAUSED; private set
    fun pause() { stopStatus = JobStatus.PAUSED; stop.set(true); ocr.stop() }
    fun cancel() { stopStatus = JobStatus.CANCELLED; stop.set(true); ocr.stop() }

    /** Re-processes only UNREADABLE pages; DONE pages (text or earlier OCR) are untouched. Returns the job status. */
    fun run(onProgress: (Int, Int) -> Unit = { _, _ -> }): JobStatus {
        stop.set(false); val prevStatus = job.status; job.status = JobStatus.RUNNING; jobs.save(job)
        val todo = (1..job.total).filter { job.pageStatus(it) == PageStatus.UNREADABLE }
        var n = 0
        for (p in todo) {
            if (stop.get()) { job.status = stopStatus; jobs.save(job); return job.status }
            val bmp = ex.renderPage(p)
            try {
                val r = ocr.recognize(bmp, langs)
                if (stop.get()) { job.status = stopStatus; jobs.save(job); return job.status }
                if (r.text.isNotBlank()) { texts.put(job.docId, PageText(p, r.text, null, ocr = true)); job.mark(p, PageStatus.DONE) }
            } catch (e: Throwable) { job.mark(p, PageStatus.FAILED) } finally { bmp.recycle() }
            if (++n % checkpointEvery == 0) jobs.save(job)
            onProgress(n, todo.size)
        }
        job.status = JobStatus.DONE; jobs.save(job); return job.status
    }
}
