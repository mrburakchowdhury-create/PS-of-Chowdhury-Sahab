package app.personal.banglaai.library

import android.content.Context
import android.net.Uri
import app.personal.banglaai.doc.DocWorkflow
import app.personal.banglaai.doc.PageText
import app.personal.banglaai.storage.Vault
import app.personal.banglaai.storage.VaultDoc
import java.io.File

/**
 * The bundled books as ordinary vault documents, so "open The Prince", "read page 10", chapters, questions, translation, notes and export all work on
 * them like on the owner's own files. The book text comes from the APK (assets); the copy in the vault is encrypted like everything else.
 * A "page" here is a passage of about [PAGE_CHARS] characters inside one chapter; its printed label is the chapter heading ("CHAPTER I · অংশ 2").
 * Installing is idempotent: a book already in the vault is reused, a deleted one is installed again.
 */
class BundledBooks(private val context: Context, private val vault: Vault, private val wf: DocWorkflow) {
    private val prefs = context.applicationContext.getSharedPreferences("library", Context.MODE_PRIVATE)
    private fun key(b: BundledBook) = "bundled_${b.id}"

    /** Ids of vault documents that are bundled books. */
    fun docIds(): Set<String> = Library.bundled.mapNotNull { prefs.getString(key(it), null) }.toSet()
    fun bookOf(doc: VaultDoc): BundledBook? = Library.bundled.firstOrNull { prefs.getString(key(it), null) == doc.id }

    fun installedDoc(b: BundledBook, docs: List<VaultDoc> = vault.list()): VaultDoc? = prefs.getString(key(b), null)?.let { id -> docs.firstOrNull { it.id == id } }

    /** Blocking (disk + encryption): call from a background thread. */
    @Synchronized fun ensureInstalled(b: BundledBook, docs: List<VaultDoc> = vault.list()): VaultDoc {
        installedDoc(b, docs)?.let { return it }
        val raw = context.assets.open(b.file).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val tmp = File(File(context.cacheDir, "bundled").apply { mkdirs() }, docName(b))
        val doc: VaultDoc
        try { tmp.writeText(raw); doc = vault.import(Uri.fromFile(tmp)) } finally { tmp.delete() }
        val (pages, chapters) = paginate(b, raw)
        try { wf.installPages(doc, pages, chapters) } catch (t: Throwable) { runCatching { vault.delete(doc.id); wf.deleteDerived(doc) }; throw t }
        prefs.edit().putString(key(b), doc.id).apply()
        return doc
    }

    /** Installs every missing book; returns how many were newly installed. */
    fun ensureAll(onProgress: (done: Int, total: Int, title: String) -> Unit = { _, _, _ -> }): Int {
        var n = 0; val docs = vault.list()
        Library.bundled.forEachIndexed { i, b -> onProgress(i, Library.bundled.size, shortTitle(b)); if (installedDoc(b, docs) == null) { ensureInstalled(b, docs); n++ } }
        onProgress(Library.bundled.size, Library.bundled.size, ""); return n
    }

    /** The page of an installed book that contains [passageText]; null if it cannot be found. */
    fun pageContaining(doc: VaultDoc, passageText: String): Int? {
        val total = wf.job(doc, DocWorkflow.Kind.Extract)?.total ?: return null
        val needle = norm(passageText).take(70); if (needle.length < 15) return null
        for (p in 1..total) if (norm(wf.texts.get(doc.id, p)?.text.orEmpty()).contains(needle)) return p
        return null
    }

    companion object {
        const val PAGE_CHARS = 2400
        fun shortTitle(b: BundledBook) = b.title.substringBefore(" (")
        fun docName(b: BundledBook) = shortTitle(b) + ".txt"
        /** Extra words (Bengali/English names) that a spoken file name may match, e.g. "দ্য প্রিন্স" for "The Prince.txt". */
        fun aliasesFor(docName: String): String = Library.bundled.firstOrNull { docName.equals(docName(it), true) }?.let { it.aliases + " " + it.author } ?: ""
        private fun norm(s: String) = s.replace(Regex("\\s+"), " ").trim()

        /** Splits a book into pages that never cross a chapter border, with the chapter list (title + first page). Pure function: unit-tested. */
        fun paginate(b: BundledBook, raw: String): Pair<List<PageText>, List<DocWorkflow.Chapter>> {
            val passages = Library.chunk(shortTitle(b), raw, PAGE_CHARS)
            val pages = passages.mapIndexed { i, p -> PageText(i + 1, p.text, p.label) }
            val chapters = ArrayList<DocWorkflow.Chapter>(); var last = ""
            passages.forEachIndexed { i, p -> val base = p.label.substringBeforeLast(" · অংশ"); if (base != last && !base.startsWith("অনুচ্ছেদ-গুচ্ছ")) { chapters += DocWorkflow.Chapter(base, i + 1); last = base } }
            return pages to chapters
        }
    }
}
