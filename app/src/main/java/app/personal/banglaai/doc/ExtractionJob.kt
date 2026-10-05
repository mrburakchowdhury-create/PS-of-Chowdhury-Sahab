package app.personal.banglaai.doc

import app.personal.banglaai.jobs.JobState
import app.personal.banglaai.jobs.JobStatus
import app.personal.banglaai.jobs.JobStore
import app.personal.banglaai.jobs.PageStatus
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Encrypted per-page text store (derived data, separate from the untouched original). */
class PageTextStore(private val dir: File, private val seal: (ByteArray, ByteArray) -> ByteArray, private val unseal: (ByteArray, ByteArray) -> ByteArray) {
    init { dir.mkdirs() }
    private fun f(docId: String, page: Int) = File(File(dir, docId).apply { mkdirs() }, "$page.txt.enc")
    fun put(docId: String, p: PageText) {
        val body = org.json.JSONObject().put("t", p.text).put("l", p.printedLabel ?: org.json.JSONObject.NULL).put("o", p.ocr).toString()
        val tmp = File(f(docId, p.page).path + ".tmp")
        tmp.writeBytes(seal(body.toByteArray(Charsets.UTF_8), "$docId/${p.page}".toByteArray()))
        check(tmp.renameTo(f(docId, p.page)))
    }
    fun get(docId: String, page: Int): PageText? {
        val x = f(docId, page); if (!x.exists()) return null
        val o = org.json.JSONObject(String(unseal(x.readBytes(), "$docId/$page".toByteArray()), Charsets.UTF_8))
        return PageText(page, o.getString("t"), if (o.isNull("l")) null else o.getString("l"), ocr = o.optBoolean("o", false))
    }
    fun deleteDoc(docId: String) { File(dir, docId).deleteRecursively() }
}

/**
 * Runs text extraction page by page with checkpoints. Cancellation/pause is checked between pages; already DONE pages
 * are never reprocessed on resume. Pages with no text are marked UNREADABLE (OCR candidates) instead of being "filled in".
 */
class ExtractionJob(
    private val job: JobState, private val extractor: PdfExtractor, private val texts: PageTextStore, private val jobs: JobStore,
    private val checkpointEvery: Int = 25,
) {
    private val stop = AtomicBoolean(false)
    @Volatile var stopStatus = JobStatus.PAUSED; private set

    fun pause() { stopStatus = JobStatus.PAUSED; stop.set(true) }
    fun cancel() { stopStatus = JobStatus.CANCELLED; stop.set(true) }

    /** Returns the final job status. [onProgress] gets (donePages, total). Safe to call again after pause (resume). */
    fun run(retryFailed: Boolean = false, onProgress: (Int, Int) -> Unit = { _, _ -> }): JobStatus {
        stop.set(false); job.status = JobStatus.RUNNING; jobs.save(job)
        var sinceCheckpoint = 0
        for (p in job.remaining(retryFailed)) {
            if (stop.get()) { job.status = stopStatus; jobs.save(job); return job.status }
            val r = extractor.page(p)
            when {
                r.failed -> job.mark(p, PageStatus.FAILED)
                r.text.isBlank() -> job.mark(p, PageStatus.UNREADABLE)
                else -> { texts.put(job.docId, r); job.mark(p, PageStatus.DONE) }
            }
            if (++sinceCheckpoint >= checkpointEvery) { jobs.save(job); sinceCheckpoint = 0 }
            onProgress(job.count(PageStatus.DONE), job.total)
        }
        job.status = JobStatus.DONE; jobs.save(job)
        return job.status
    }
}
