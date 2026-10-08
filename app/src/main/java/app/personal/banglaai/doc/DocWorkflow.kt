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
import app.personal.banglaai.importer.readUpTo
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
    /** The owner's corrections of extracted/OCR text, kept as a SEPARATE version; the extracted text itself is never overwritten. */
    val fixes = PageTextStore(File(base, "fix"), vault::seal, vault::unseal)
    val savedQa = app.personal.banglaai.userdata.SavedQa(app.personal.banglaai.userdata.SealedList(File(base, "savedqa.enc"), vault::seal, vault::unseal))
    val glossary = app.personal.banglaai.userdata.Glossary(app.personal.banglaai.userdata.SealedList(File(base, "glossary.enc"), vault::seal, vault::unseal))
    val vocab = app.personal.banglaai.userdata.Vocab(app.personal.banglaai.userdata.SealedList(File(base, "vocab.enc"), vault::seal, vault::unseal))
    data class Chapter(val title: String, val page: Int)
    private fun chapterStore(doc: VaultDoc) = app.personal.banglaai.userdata.SealedList(File(File(base, "chapters").apply { mkdirs() }, "${doc.id}.enc"), vault::seal, vault::unseal)
    fun chapters(doc: VaultDoc): List<Chapter> = runCatching { chapterStore(doc).read().map { Chapter(it.getString("t"), it.getInt("p")) } }.getOrDefault(emptyList())
    private fun saveChapters(doc: VaultDoc, list: List<Chapter>) = chapterStore(doc).write(list.map { org.json.JSONObject().put("t", it.title).put("p", it.page) })
    val marks = app.personal.banglaai.userdata.Marks(app.personal.banglaai.userdata.SealedList(File(base, "marks.enc"), vault::seal, vault::unseal))

    val reading = app.personal.banglaai.userdata.ReadingPosition(app.personal.banglaai.userdata.SealedList(File(base, "reading.enc"), vault::seal, vault::unseal))

    @Volatile private var extraction: ExtractionJob? = null
    @Volatile private var translation: TranslationJob? = null
    @Volatile private var llm: LocalLlm? = null
    @Volatile private var ocrJob: OcrJob? = null
    /** Why the last job paused itself (heat / battery / space), or about a guessed text encoding; shown on the document screen. */
    @Volatile var pauseReason: String? = null
    @Volatile var textNote: String? = null
    private fun guard(): () -> String? = { app.personal.banglaai.doc.ResourceGuard.check(context) }
    fun isCode(doc: VaultDoc) = app.personal.banglaai.importer.OfficeImporter.extension(doc.name.lowercase()) in app.personal.banglaai.importer.OfficeImporter.codeExtensions

    enum class Kind(val key: String) { Extract("extract"), Translate("translate") }
    fun jobId(doc: VaultDoc, k: Kind) = "${doc.id}-${k.key}"
    fun job(doc: VaultDoc, k: Kind): JobState? = jobs.load(jobId(doc, k))

    /** The text everything else works from (reading, questions, translation, export): the owner's correction if there is one, otherwise the extracted text. */
    fun effective(docId: String, page: Int): PageText? = fixes.get(docId, page) ?: texts.get(docId, page)
    private val effectiveSource = PageSource { d, p -> effective(d, p) }

    /** Saves the owner's corrected text for [page]. The translation of that page (made from the old text) is put back to "pending" so a resume redoes ONLY that page. */
    fun saveFix(doc: VaultDoc, page: Int, text: String) {
        val orig = texts.get(doc.id, page) ?: PageText(page, "", null)
        fixes.put(doc.id, PageText(page, text, orig.printedLabel, ocr = false))
        markTranslationStale(doc, page)
    }
    fun clearFix(doc: VaultDoc, page: Int) { fixes.delete(doc.id, page); markTranslationStale(doc, page) }
    private fun markTranslationStale(doc: VaultDoc, page: Int) {
        val j = job(doc, Kind.Translate) ?: return
        if (page in 1..j.total && j.pageStatus(page) == app.personal.banglaai.jobs.PageStatus.DONE) { j.mark(page, app.personal.banglaai.jobs.PageStatus.PENDING); if (j.status == JobStatus.DONE) j.status = JobStatus.PAUSED; jobs.save(j) }
        translations.delete(doc.id, page)
    }

    fun isImage(doc: VaultDoc) = doc.name.lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") }
    fun isSupported(doc: VaultDoc) = isImage(doc) || doc.name.lowercase().let { it.endsWith(".pdf") || it.endsWith(".txt") || app.personal.banglaai.importer.OfficeImporter.extension(it) in app.personal.banglaai.importer.OfficeImporter.supported || app.personal.banglaai.importer.OfficeImporter.extension(it) in app.personal.banglaai.importer.OfficeImporter.codeExtensions }

    @Volatile private var officeStop = false
    fun pause() { officeStop = true; extraction?.pause(); translation?.pause(); ocrJob?.pause(); llm?.cancel() }
    fun cancel() { officeStop = true; extraction?.cancel(); translation?.cancel(); ocrJob?.cancel(); llm?.cancel() }

    /** Blocking; call on a background thread. Safe to call again to resume (finished pages are skipped). */
    fun extract(doc: VaultDoc, retryFailed: Boolean, onProgress: (Int, Int) -> Unit): JobStatus {
        val tmp = File(context.cacheDir, "pdf-${doc.id}")
        try {
            val name = doc.name.lowercase()
            if (isImage(doc)) return extractImage(doc, onProgress)
            if (app.personal.banglaai.importer.OfficeImporter.extension(name) in app.personal.banglaai.importer.OfficeImporter.codeExtensions) return extractCode(doc, onProgress)
            if (name.endsWith(".txt")) return extractTxt(doc, onProgress)
            if (app.personal.banglaai.importer.OfficeImporter.extension(name) in app.personal.banglaai.importer.OfficeImporter.supported) return extractOffice(doc, onProgress)
            PdfExtractor.init(context)
            val opened = vault.open(doc.id).use { PdfExtractor.open(it, tmp) }
            when (opened) {
                is PdfExtractor.OpenResult.PasswordProtected -> error("PDF-এ পাসওয়ার্ড আছে — এই সংস্করণে সমর্থিত নয়")
                is PdfExtractor.OpenResult.Malformed -> error("PDF নষ্ট বা পড়া যায়নি (${opened.reason})")
                is PdfExtractor.OpenResult.Ok -> opened.extractor.use { ex ->
                    val id = jobId(doc, Kind.Extract)
                    val job = jobs.load(id) ?: JobState(id, doc.id, Kind.Extract.key, ex.pageCount)
                    runCatching { saveChapters(doc, ex.outline().map { (t, p) -> Chapter(t, p) }) }
                    val runner = ExtractionJob(job, ex, texts, jobs, pauseCheck = guard()).also { extraction = it }
                    pauseReason = null
                    return runner.run(retryFailed, onProgress).also { pauseReason = runner.pausedBecause }
                }
            }
        } finally { extraction = null; tmp.deleteRecursively() }   // never leave plaintext temp data behind
    }

    /** OCR only for pages the text extraction found empty. Explicit button only. PDF documents only. */
    fun ocr(doc: VaultDoc, langs: List<TesseractOcr.Lang>, onProgress: (Int, Int) -> Unit): JobStatus {
        if (isImage(doc)) return ocrImage(doc, langs)
        require(doc.name.lowercase().endsWith(".pdf")) { "OCR শুধু PDF ও ছবির জন্য" }
        val job = job(doc, Kind.Extract) ?: error("আগে 'লেখা বের করুন' চালান")
        val tmp = File(context.cacheDir, "pdf-${doc.id}")
        try {
            PdfExtractor.init(context)
            val opened = vault.open(doc.id).use { PdfExtractor.open(it, tmp) } as? PdfExtractor.OpenResult.Ok ?: error("PDF খোলা যায়নি")
            opened.extractor.use { ex -> TesseractOcr(context).use { t ->
                pauseReason = null
                return OcrJob(job, ex, t, langs, texts, jobs, pauseCheck = guard()).also { ocrJob = it }.let { r -> r.run(onProgress).also { pauseReason = r.pausedBecause } }
            } }
        } finally { ocrJob = null; tmp.deleteRecursively() }
    }

    /**
     * The ORIGINAL page as a picture, for viewing next to the extracted text. PDF only. The vault file is decrypted into a private scratch copy that is deleted before this returns
     * (see MainActivity: leftovers are also swept at start); the bitmap lives in memory only. Returns null with [error] set if the PDF cannot be opened.
     */
    fun renderOriginal(doc: VaultDoc, page: Int, dpi: Float = 110f): Result<android.graphics.Bitmap> = runCatching {
        require(doc.name.lowercase().endsWith(".pdf")) { "মূল পৃষ্ঠার ছবি শুধু PDF-এর জন্য" }
        val tmp = File(context.cacheDir, "pdf-view-${doc.id}")
        try {
            PdfExtractor.init(context)
            val opened = vault.open(doc.id).use { PdfExtractor.open(it, tmp) } as? PdfExtractor.OpenResult.Ok ?: error("PDF খোলা যায়নি")
            opened.extractor.use { it.renderPageColor(page, dpi) }
        } finally { tmp.deleteRecursively() }
    }

    /**
     * Stores ready-made pages (a bundled book split into passages) as if they had been extracted from [doc]: per-page text with a printed label (the chapter),
     * a chapter list and a finished extraction job — so reading, questions, translation, marks and export work on it exactly like on any file.
     */
    fun installPages(doc: VaultDoc, pages: List<PageText>, chapters: List<Chapter>) {
        val job = JobState(jobId(doc, Kind.Extract), doc.id, Kind.Extract.key, pages.size)
        pages.forEachIndexed { i, p -> texts.put(doc.id, p.copy(page = i + 1)); job.mark(i + 1, app.personal.banglaai.jobs.PageStatus.DONE) }
        saveChapters(doc, chapters); job.status = JobStatus.DONE; jobs.save(job)
    }

    /** A picture has no text layer: it becomes a 1-page document whose page waits for OCR (never guessed). */
    private fun extractImage(doc: VaultDoc, onProgress: (Int, Int) -> Unit): JobStatus {
        val job = JobState(jobId(doc, Kind.Extract), doc.id, Kind.Extract.key, 1)
        job.mark(1, app.personal.banglaai.jobs.PageStatus.UNREADABLE); job.status = JobStatus.DONE; jobs.save(job); onProgress(1, 1)
        return job.status
    }

    /** OCR of an imported picture (decoded in memory, downsampled to keep RAM bounded). */
    private fun ocrImage(doc: VaultDoc, langs: List<TesseractOcr.Lang>): JobStatus {
        val job = job(doc, Kind.Extract) ?: error("আগে 'লেখা বের করুন' চালান")
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        vault.open(doc.id).use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1; while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 4000) sample *= 2
        val bmp = vault.open(doc.id).use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) } ?: error("ছবি পড়া যায়নি")
        try {
            TesseractOcr(context).use { t ->
                val r = t.recognize(bmp, langs)
                if (r.text.isNotBlank()) { texts.put(doc.id, PageText(1, r.text, null, ocr = true)); job.mark(1, app.personal.banglaai.jobs.PageStatus.DONE) }
                jobs.save(job)
            }
        } finally { bmp.recycle() }
        return JobStatus.DONE
    }

    /** Decodes the stored picture downsampled to at most [maxDim] px on its longest side (the original encrypted file is never changed). */
    fun loadImage(doc: VaultDoc, maxDim: Int = 2400): android.graphics.Bitmap? {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        vault.open(doc.id).use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1; while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDim) sample *= 2
        return vault.open(doc.id).use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) }
    }

    /** Stores OCR text of a picture (or of a selected part of it) as the page text, marked as OCR so the UI warns it may contain mistakes. */
    fun saveOcrText(doc: VaultDoc, text: String) {
        val job = job(doc, Kind.Extract) ?: JobState(jobId(doc, Kind.Extract), doc.id, Kind.Extract.key, 1)
        texts.put(doc.id, PageText(1, text, null, ocr = true)); job.mark(1, app.personal.banglaai.jobs.PageStatus.DONE)
        if (job.status != JobStatus.DONE) job.status = JobStatus.DONE
        jobs.save(job)
    }

    /** DOCX/PPTX/XLSX/EPUB/CSV: sections (slide/sheet/chapter/part) become "pages". Refused files raise a clear Bengali message. */
    private fun extractOffice(doc: VaultDoc, onProgress: (Int, Int) -> Unit): JobStatus {
        officeStop = false
        val sections = vault.open(doc.id).use { app.personal.banglaai.importer.OfficeImporter.extract(doc.name, it) { officeStop } }
        val id = jobId(doc, Kind.Extract)
        val job = JobState(id, doc.id, Kind.Extract.key, sections.size.coerceAtLeast(1))
        if (sections.isEmpty()) job.mark(1, app.personal.banglaai.jobs.PageStatus.UNREADABLE)
        sections.forEachIndexed { i, sec ->
            if (sec.text.isBlank()) job.mark(i + 1, app.personal.banglaai.jobs.PageStatus.UNREADABLE)
            else { texts.put(doc.id, PageText(i + 1, sec.text, sec.title)); job.mark(i + 1, app.personal.banglaai.jobs.PageStatus.DONE) }
        }
        if (app.personal.banglaai.importer.OfficeImporter.extension(doc.name) == "epub") runCatching { saveChapters(doc, sections.mapIndexed { i, sec -> Chapter(sec.title, i + 1) }) }
        job.status = JobStatus.DONE; jobs.save(job); onProgress(job.total, job.total)
        return job.status
    }

    /** Source-code file: line-based sections (indentation kept), original untouched, nothing run. */
    private fun extractCode(doc: VaultDoc, onProgress: (Int, Int) -> Unit): JobStatus {
        val text = vault.open(doc.id).use { String(plainBytes(it), Charsets.UTF_8) }
        val sections = app.personal.banglaai.importer.OfficeImporter.splitCode(text).ifEmpty { listOf(app.personal.banglaai.importer.Section("খালি", "")) }
        val id = jobId(doc, Kind.Extract); val job = JobState(id, doc.id, Kind.Extract.key, sections.size)
        sections.forEachIndexed { i, sec ->
            if (sec.text.isBlank()) job.mark(i + 1, app.personal.banglaai.jobs.PageStatus.UNREADABLE)
            else { texts.put(doc.id, PageText(i + 1, sec.text, sec.title)); job.mark(i + 1, app.personal.banglaai.jobs.PageStatus.DONE) }
        }
        job.status = JobStatus.DONE; jobs.save(job); onProgress(job.total, job.total); return job.status
    }

    /** Plain-text/code files are read whole into memory: refuse absurdly large ones with a clear message instead of running out of memory. */
    private fun plainBytes(i: java.io.InputStream): ByteArray = i.readUpTo(MAX_TEXT_FILE + 1).also { require(it.size <= MAX_TEXT_FILE) { "টেক্সট/কোড ফাইল ${MAX_TEXT_FILE shr 20} MB-এর বেশি বড় — ভাগ করে দিন" } }
    private companion object { const val MAX_TEXT_FILE = 64L shl 20 }

    private fun extractTxt(doc: VaultDoc, onProgress: (Int, Int) -> Unit): JobStatus {
        val dec = vault.open(doc.id).use { app.personal.banglaai.importer.TextDecoding.decode(plainBytes(it)) }
        textNote = if (dec.guessed) "এই TXT ফাইলের এনকোডিং UTF-8 নয়; ${dec.charset} ধরে পড়া হয়েছে (আন্দাজ) — অক্ষর ভাঙা দেখালে ফাইলটি UTF-8-এ সেভ করে আবার দিন।" else null
        val text = dec.text
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
    private fun prefs() = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    /** Name of the model the owner chose to use (several can be stored, but only ONE is ever loaded into RAM). */
    var chosenModel: String?
        get() = prefs().getString("model_pref", null)
        set(v) { prefs().edit().putString("model_pref", v).apply(); LocalLlm.releaseAll() }   // the old model's RAM is given back

    fun isUsable(m: app.personal.banglaai.storage.ModelFile) = m.name.endsWith(".litertlm") &&
        KnownModels.verdict(m.name, m.size, m.sha256).let { v -> v !is KnownModels.Verdict.Mismatch && v !is KnownModels.Verdict.Pending }

    /** The chosen model if it is stored and verified, else the first verified one (E2B sorts before E4B, i.e. the lighter one). */
    /** true when a model file exists but none is usable (partial/damaged/wrong type) — different advice from "no model". */
    fun modelDamaged(): Boolean = models.list().let { it.isNotEmpty() && it.none(::isUsable) }
    fun usableModel(): File? = models.list().filter(::isUsable).let { ok -> ok.firstOrNull { it.name == chosenModel } ?: ok.firstOrNull() }
        ?.let { File(context.noBackupFilesDir, "models/${it.name}") }

    fun translate(doc: VaultDoc, retryFailed: Boolean, onProgress: (Int, Int) -> Unit): JobStatus {
        require(!isCode(doc)) { "সোর্স-কোড ফাইল অনুবাদ করা হয় না — মূল কোড অক্ষত থাকে; কোড বুঝতে 'কোড ব্যাখ্যা' ব্যবহার করুন" }
        val ext = job(doc, Kind.Extract) ?: error("আগে 'লেখা বের করুন' চালান")
        val model = usableModel() ?: error("কোনো AI মডেল বসানো নেই — আগে মডেল import করুন (স্থানীয় ছাড়া অনুবাদ করা হবে না)")
        val id = jobId(doc, Kind.Translate)
        val job = jobs.load(id) ?: JobState(id, doc.id, Kind.Translate.key, ext.total)
        LocalLlm(model, File(context.cacheDir, "llm"), background = true).use { m ->
            llm = m
            try {
                val runner = TranslationJob(job, effectiveSource, translations, jobs, { m.ask(Prompts.TRANSLATE_SYSTEM, Prompts.translate(it, glossary.promptPairs(it))) },
                    pauseCheck = guard(), alreadyTarget = { t -> t.count { c -> c.isLetter() } >= 20 && app.personal.banglaai.speech.SpeechText.isMostlyBengali(t) }).also { translation = it }
                pauseReason = null
                return runner.run(retryFailed, onProgress).also { pauseReason = runner.pausedBecause }
            } finally { translation = null; llm = null }
        }
    }

    /** Builds export pages from stored text/translation; pages without translation are included and reported as missing. */
    fun exportPages(doc: VaultDoc): List<app.personal.banglaai.export.ExportPage> {
        val total = job(doc, Kind.Extract)?.total ?: 0
        return (1..total).map { p ->
            val src = effective(doc.id, p); val bn = translations.get(doc.id, p)
            app.personal.banglaai.export.ExportPage(p, src?.printedLabel, src?.text, bn?.text, src?.ocr == true)
        }
    }

    fun deleteDerived(doc: VaultDoc) {
        texts.deleteDoc(doc.id); translations.deleteDoc(doc.id); fixes.deleteDoc(doc.id); savedQa.deleteDoc(doc.id)
        Kind.entries.forEach { jobs.delete(jobId(doc, it)) }
        marks.deleteDoc(doc.id); vocab.deleteDoc(doc.id); reading.delete(doc.id); app.personal.banglaai.library.LibraryStore(context).remove(doc.id); File(File(base, "chapters"), "${doc.id}.enc").delete()
    }
}
