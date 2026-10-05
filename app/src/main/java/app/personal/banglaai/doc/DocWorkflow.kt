package app.personal.banglaai.doc

import android.content.Context
import app.personal.banglaai.ai.LocalLlm
import app.personal.banglaai.ai.Prompts
import app.personal.banglaai.ai.TranslationJob
import app.personal.banglaai.jobs.JobState
import app.personal.banglaai.jobs.JobStatus
import app.personal.banglaai.jobs.JobStore
import app.personal.banglaai.ocr.OcrJob
import app.personal.banglaai.ocr.TesseractOcr
import app.personal.banglaai.storage.KnownModels
import app.personal.banglaai.storage.ModelStore
import app.personal.banglaai.storage.Vault
import app.personal.banglaai.storage.VaultDoc
import java.io.File

/**
 * Glue between the vault, extraction and translation jobs. Every public action here is started only by an explicit
 * button press in the UI; nothing runs automatically after import. Status: compiles; on-phone behaviour is device-test pending.
 */
class DocWorkflow(private val context: Context, private val vault: Vault, private val models: ModelStore) {
    private val base = File(context.noBackupFilesDir, "derived")
    val jobs = JobStore(File(base, "jobs"), vault::seal, vault::unseal)
    val texts = PageTextStore(File(base, "text"), vault::seal, vault::unseal)
    val translations = PageTextStore(File(base, "bn"), vault::seal, vault::unseal)

    @Volatile private var extraction: ExtractionJob? = null
    @Volatile private var translation: TranslationJob? = null
    @Volatile private var llm: LocalLlm? = null
    @Volatile private var ocrJob: OcrJob? = null

    enum class Kind(val key: String) { Extract("extract"), Translate("translate") }
    fun jobId(doc: VaultDoc, k: Kind) = "${doc.id}-${k.key}"
    fun job(doc: VaultDoc, k: Kind): JobState? = jobs.load(jobId(doc, k))

    fun isSupported(doc: VaultDoc) = doc.name.lowercase().let { it.endsWith(".pdf") || it.endsWith(".txt") }

    fun pause() { extraction?.pause(); translation?.pause(); ocrJob?.pause(); llm?.cancel() }
    fun cancel() { extraction?.cancel(); translation?.cancel(); ocrJob?.cancel(); llm?.cancel() }

    /** Blocking; call on a background thread. Safe to call again to resume (finished pages are skipped). */
    fun extract(doc: VaultDoc, retryFailed: Boolean, onProgress: (Int, Int) -> Unit): JobStatus {
        val tmp = File(context.cacheDir, "pdf-${doc.id}")
        try {
            val name = doc.name.lowercase()
            if (name.endsWith(".txt")) return extractTxt(doc, onProgress)
            PdfExtractor.init(context)
            val opened = vault.open(doc.id).use { PdfExtractor.open(it, tmp) }
            when (opened) {
                is PdfExtractor.OpenResult.PasswordProtected -> error("PDF-এ পাসওয়ার্ড আছে — এই সংস্করণে সমর্থিত নয়")
                is PdfExtractor.OpenResult.Malformed -> error("PDF নষ্ট বা পড়া যায়নি (${opened.reason})")
                is PdfExtractor.OpenResult.Ok -> opened.extractor.use { ex ->
                    val id = jobId(doc, Kind.Extract)
                    val job = jobs.load(id) ?: JobState(id, doc.id, Kind.Extract.key, ex.pageCount)
                    val runner = ExtractionJob(job, ex, texts, jobs).also { extraction = it }
                    return runner.run(retryFailed, onProgress)
                }
            }
        } finally { extraction = null; tmp.deleteRecursively() }   // never leave plaintext temp data behind
    }

    /** OCR only for pages the text extraction found empty. Explicit button only. PDF documents only. */
    fun ocr(doc: VaultDoc, langs: List<TesseractOcr.Lang>, onProgress: (Int, Int) -> Unit): JobStatus {
        require(doc.name.lowercase().endsWith(".pdf")) { "OCR এখন শুধু PDF-এর জন্য (ছবি import পরের ধাপ)" }
        val job = job(doc, Kind.Extract) ?: error("আগে 'লেখা বের করুন' চালান")
        val tmp = File(context.cacheDir, "pdf-${doc.id}")
        try {
            PdfExtractor.init(context)
            val opened = vault.open(doc.id).use { PdfExtractor.open(it, tmp) } as? PdfExtractor.OpenResult.Ok ?: error("PDF খোলা যায়নি")
            opened.extractor.use { ex -> TesseractOcr(context).use { t ->
                return OcrJob(job, ex, t, langs, texts, jobs).also { ocrJob = it }.run(onProgress)
            } }
        } finally { ocrJob = null; tmp.deleteRecursively() }
    }

    private fun extractTxt(doc: VaultDoc, onProgress: (Int, Int) -> Unit): JobStatus {
        val text = vault.open(doc.id).use { String(it.readBytes(), Charsets.UTF_8) }
        val pages = app.personal.banglaai.ai.Chunker.split(text, 3000).ifEmpty { listOf("") }
        val id = jobId(doc, Kind.Extract)
        val job = JobState(id, doc.id, Kind.Extract.key, pages.size)
        pages.forEachIndexed { i, t ->
            if (t.isBlank()) job.mark(i + 1, app.personal.banglaai.jobs.PageStatus.UNREADABLE)
            else { texts.put(doc.id, PageText(i + 1, t, null)); job.mark(i + 1, app.personal.banglaai.jobs.PageStatus.DONE) }
        }
        job.status = JobStatus.DONE; jobs.save(job); onProgress(job.total, job.total)
        return job.status
    }

    /** The first imported model that is not a known-bad copy. Returns null if none is imported. */
    fun usableModel(): File? = models.list().firstOrNull { it.name.endsWith(".litertlm") &&
        KnownModels.verdict(it.name, it.size, it.sha256) !is KnownModels.Verdict.Mismatch }
        ?.let { File(context.noBackupFilesDir, "models/${it.name}") }

    fun translate(doc: VaultDoc, retryFailed: Boolean, onProgress: (Int, Int) -> Unit): JobStatus {
        val ext = job(doc, Kind.Extract) ?: error("আগে 'লেখা বের করুন' চালান")
        val model = usableModel() ?: error("কোনো AI মডেল বসানো নেই — আগে মডেল import করুন (স্থানীয় ছাড়া অনুবাদ করা হবে না)")
        val id = jobId(doc, Kind.Translate)
        val job = jobs.load(id) ?: JobState(id, doc.id, Kind.Translate.key, ext.total)
        LocalLlm(model, File(context.cacheDir, "llm")).use { m ->
            llm = m
            try {
                val runner = TranslationJob(job, texts, translations, jobs, { m.ask(Prompts.TRANSLATE_SYSTEM, Prompts.translate(it)) }).also { translation = it }
                return runner.run(retryFailed, onProgress)
            } finally { translation = null; llm = null }
        }
    }

    fun deleteDerived(doc: VaultDoc) {
        texts.deleteDoc(doc.id); translations.deleteDoc(doc.id)
        Kind.entries.forEach { jobs.delete(jobId(doc, it)) }
    }
}
