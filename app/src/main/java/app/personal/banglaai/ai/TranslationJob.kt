package app.personal.banglaai.ai

import app.personal.banglaai.doc.PageText
import app.personal.banglaai.doc.PageTextStore
import app.personal.banglaai.jobs.JobState
import app.personal.banglaai.jobs.JobStatus
import app.personal.banglaai.jobs.JobStore
import app.personal.banglaai.jobs.PageStatus
import java.util.concurrent.atomic.AtomicBoolean

fun interface Translator { fun translate(text: String): String }

/**
 * Full-document Bengali translation, page by page, with checkpoints (kind = "translate").
 * Source text comes from the extraction output; translations go to a separate store (never overwrite the original text).
 * A page is DONE only if every chunk of it was translated; one failing chunk marks the whole page FAILED and nothing is
 * stored for it, so the coverage report never claims a partly-translated page is complete.
 * Pages with no extracted text (OCR pending) stay in the report as UNREADABLE — they are not counted as translated.
 */
class TranslationJob(
    private val job: JobState, private val source: app.personal.banglaai.doc.PageSource, private val target: PageTextStore,
    private val jobs: JobStore, private val translator: Translator, private val maxChunk: Int = 1200,
    private val checkpointEvery: Int = 5, private val pauseCheck: () -> String? = { null },
    /** Pages whose text is already in the target language are copied, not sent to the model. */
    private val alreadyTarget: (String) -> Boolean = { false },
) {
    @Volatile var pausedBecause: String? = null; private set
    private val stop = AtomicBoolean(false)
    @Volatile var stopStatus = JobStatus.PAUSED; private set
    fun pause() { stopStatus = JobStatus.PAUSED; stop.set(true) }
    fun cancel() { stopStatus = JobStatus.CANCELLED; stop.set(true) }

    fun run(retryFailed: Boolean = false, onProgress: (Int, Int) -> Unit = { _, _ -> }): JobStatus {
        stop.set(false); job.status = JobStatus.RUNNING; jobs.save(job)
        var since = 0
        for (p in job.remaining(retryFailed)) {
            if (stop.get()) { job.status = stopStatus; jobs.save(job); return job.status }
            pauseCheck()?.let { pausedBecause = it; job.status = JobStatus.PAUSED; jobs.save(job); return job.status }
            val src = source.get(job.docId, p)
            if (src == null || src.text.isBlank()) { job.mark(p, PageStatus.UNREADABLE) }
            else if (alreadyTarget(src.text)) { target.put(job.docId, PageText(p, src.text, src.printedLabel)); job.mark(p, PageStatus.DONE) }
            else try {
                val parts = Chunker.split(src.text, maxChunk).map { c ->
                    if (stop.get()) throw InterruptedException()   // pause/cancel mid-page: page stays PENDING, redone fully on resume
                    translator.translate(c).also { require(it.isNotBlank()) { "empty translation" } }
                }
                target.put(job.docId, PageText(p, parts.joinToString("\n\n"), src.printedLabel))
                job.mark(p, PageStatus.DONE)
            } catch (e: InterruptedException) {
                job.status = stopStatus; jobs.save(job); return job.status
            } catch (e: Throwable) {
                if (e is app.personal.banglaai.security.VaultLockedException) { job.status = JobStatus.PAUSED; runCatching { jobs.save(job) }; return job.status }   // locked mid-page: paused, not failed
                // pause/cancel makes the running model call throw: that is an interruption, not a failed page (page stays PENDING and is redone whole on resume)
                if (stop.get()) { job.status = stopStatus; jobs.save(job); return job.status }
                job.mark(p, PageStatus.FAILED)
            }
            if (++since >= checkpointEvery) { jobs.save(job); since = 0 }
            onProgress(job.count(PageStatus.DONE), job.total)
        }
        job.status = JobStatus.DONE; jobs.save(job); return job.status
    }
}
