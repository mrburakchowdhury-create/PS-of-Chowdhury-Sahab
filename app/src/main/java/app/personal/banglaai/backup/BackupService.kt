package app.personal.banglaai.backup

import app.personal.banglaai.doc.DocWorkflow
import app.personal.banglaai.doc.PageText
import app.personal.banglaai.importer.readUpTo
import app.personal.banglaai.jobs.JobState
import app.personal.banglaai.jobs.JobStatus
import app.personal.banglaai.jobs.PageStatus
import app.personal.banglaai.storage.Vault
import app.personal.banglaai.storage.VaultDoc
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Local encrypted backup (originals + extracted text + translations + job coverage + the owner's notes/bookmarks/glossary/vocabulary/reading positions),
 * written wherever the user chooses; nothing is uploaded.
 *
 * RESTORE IS ALL-OR-NOTHING:
 *  1. The whole encrypted payload is read and AUTHENTICATED to its very end (a cut-off or altered file fails here), every entry is validated
 *     (names, duplicates, sizes, counts, page numbers, manifest/end totals) and the originals are written ENCRYPTED into a private staging folder.
 *     The live vault is not touched in this phase, so a wrong passphrase, a truncated/corrupt file, a storage problem or a cancel leaves it exactly as it was.
 *  2. Only then is it published: derived data first, then the originals are renamed into place and the index is written once (the commit point).
 *     A journal records the new ids first, so a crash in between is cleaned up by [recoverInterrupted]; a failure before the commit is rolled back immediately.
 * Documents whose id already exists in the vault are left untouched (never overwritten).
 */
class BackupService(private val vault: Vault, private val wf: DocWorkflow, private val freeSpace: () -> Long = { vault.usableSpace() }) {
    data class Report(val restored: Int, val skipped: Int, val userItems: Int = 0)
    class StorageFull(msg: String) : Exception(msg)

    // ---------------------------------------------------------------- create

    fun create(out: OutputStream, pass: CharArray) {
        var entries = 0; var origBytes = 0L
        ZipOutputStream(PassphraseBackup.encryptTo(out, pass)).use { z ->
            fun entry(name: String, bytes: ByteArray) { z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry(); entries++ }
            val docs = vault.list()
            entry("manifest.json", JSONObject().put("version", VERSION).put("docs", docs.size).toString().toByteArray())
            for (d in docs) {
                val ext = wf.job(d, DocWorkflow.Kind.Extract); val tr = wf.job(d, DocWorkflow.Kind.Translate)
                val meta = JSONObject().put("id", d.id).put("name", d.name).put("added", d.added)
                    .put("extract", ext?.pages?.toList()?.let(::JSONArray) ?: JSONObject.NULL).put("translate", tr?.pages?.toList()?.let(::JSONArray) ?: JSONObject.NULL)
                entry("d/${d.id}/meta.json", meta.toString().toByteArray(Charsets.UTF_8))
                z.putNextEntry(ZipEntry("d/${d.id}/orig")); origBytes += vault.open(d.id).use { it.copyTo(z) }; z.closeEntry(); entries++
                for (p in 1..(ext?.total ?: 0)) {
                    wf.texts.get(d.id, p)?.let { entry("d/${d.id}/t/$p.json", pageJson(it)) }
                    wf.translations.get(d.id, p)?.let { entry("d/${d.id}/b/$p.json", pageJson(it)) }
                }
            }
            entry("u/marks.json", JSONArray(wf.marks.exportJson()).toString().toByteArray(Charsets.UTF_8))
            entry("u/glossary.json", JSONArray(wf.glossary.exportJson()).toString().toByteArray(Charsets.UTF_8))
            entry("u/vocab.json", JSONArray(wf.vocab.exportJson()).toString().toByteArray(Charsets.UTF_8))
            entry("u/reading.json", JSONArray(wf.reading.exportJson()).toString().toByteArray(Charsets.UTF_8))
            z.putNextEntry(ZipEntry("end.json")); z.write(JSONObject().put("entries", entries).put("docs", docs.size).put("origBytes", origBytes).toString().toByteArray()); z.closeEntry()
        }
    }

    private fun pageJson(p: PageText) = JSONObject().put("t", p.text).put("l", p.printedLabel ?: JSONObject.NULL).put("o", p.ocr).toString().toByteArray(Charsets.UTF_8)

    // ---------------------------------------------------------------- restore

    private class StagedDoc(val id: String) {
        var meta: JSONObject? = null; var origFile: File? = null; var origSize = -1L
        val texts = HashMap<Int, PageText>(); val trans = HashMap<Int, PageText>()
        var extract: IntArray? = null; var translate: IntArray? = null
    }
    private class Staged { val docs = LinkedHashMap<String, StagedDoc>(); val user = HashMap<String, List<JSONObject>>(); var manifestDocs = -1; var version = 0; var end: JSONObject? = null; var entries = 0; var origBytes = 0L }

    /** Throws [PassphraseBackup.BadBackup] (wrong passphrase / damaged / unacceptable file), [StorageFull], or CancellationException; in every case the live vault is unchanged. */
    fun restore(input: InputStream, pass: CharArray, cancelled: () -> Boolean = { false }): Report {
        if (!RUNNING.compareAndSet(false, true)) throw IllegalStateException("আরেকটি ব্যাকআপ-ফেরানো চলছে")
        CANCEL.set(false)
        val staging = vault.newStaging()
        val caller = cancelled; val cancelled = { caller() || CANCEL.get() }
        try {
            recoverInterrupted(alreadyLocked = true)
            val st = try { stage(input, pass, staging, cancelled) }
                catch (e: PassphraseBackup.BadBackup) { throw e }
                catch (e: java.util.concurrent.CancellationException) { throw e }
                catch (e: StorageFull) { throw e }
                catch (e: IOException) { if (isNoSpace(e)) throw StorageFull("ফোনে জায়গা শেষ — আগের ভল্ট অপরিবর্তিত আছে") else throw bad("পাসফ্রেজ ভুল, অথবা ফাইল অসম্পূর্ণ/নষ্ট", e) }
                catch (e: GeneralSecurityException) { throw bad("পাসফ্রেজ ভুল, অথবা ফাইল অসম্পূর্ণ/নষ্ট", e) }
            validate(st)
            if (cancelled()) throw java.util.concurrent.CancellationException("বাতিল")
            return publish(st)
        } finally { vault.deleteStaging(staging); RUNNING.set(false) }
    }

    private fun bad(msg: String, cause: Throwable? = null) = PassphraseBackup.BadBackup("$msg — আপনার ভল্ট অপরিবর্তিত আছে").also { if (cause != null) it.initCause(cause) }
    private fun isNoSpace(e: IOException) = (e.message ?: "").let { it.contains("ENOSPC") || it.contains("No space left", true) }

    private fun stage(input: InputStream, pass: CharArray, staging: File, cancelled: () -> Boolean): Staged {
        val st = Staged(); val seen = HashSet<String>(); var smallBytes = 0L
        val dec = PassphraseBackup.decryptFrom(input, pass)
        val z = ZipInputStream(dec)
        while (true) {
            if (cancelled()) throw java.util.concurrent.CancellationException("বাতিল")
            val e = z.nextEntry ?: break
            if (++st.entries > MAX_ENTRIES) throw bad("ব্যাকআপে এন্ট্রির সংখ্যা সীমার বেশি")
            val name = e.name
            val parts = name.split("/")
            if (name.length > 200 || name.contains('\u0000') || name.contains('\\') || parts.any { it.isEmpty() || it == "." || it == ".." }) throw bad("ব্যাকআপে অগ্রহণযোগ্য ফাইলের নাম")
            if (!seen.add(name)) throw bad("ব্যাকআপে একই এন্ট্রি দুবার আছে")
            if (e.isDirectory) throw bad("ব্যাকআপে অপ্রত্যাশিত ফোল্ডার-এন্ট্রি")
            if (st.version == 0 && name != "manifest.json") throw bad("ব্যাকআপের শুরুতে manifest নেই")
            when {
                name == "manifest.json" -> {
                    val m = parseObj(small(z, MAX_SMALL)); st.version = m.optInt("version", -1); st.manifestDocs = m.optInt("docs", -1)
                    if (st.version !in 1..VERSION) throw bad("এই ব্যাকআপ নতুন বা অজানা সংস্করণের (${m.opt("version")})")
                    if (st.manifestDocs !in 0..MAX_DOCS) throw bad("ব্যাকআপে ডকুমেন্ট-সংখ্যা অগ্রহণযোগ্য")
                }
                name == "end.json" -> { st.end = parseObj(small(z, MAX_SMALL)); st.entries-- }   // the end marker is not counted in its own total
                parts.size == 2 && parts[0] == "u" && parts[1] in USER_FILES.map { "$it.json" } && st.version >= 2 -> {
                    val b = small(z, MAX_USER); smallBytes += b.size
                    st.user[parts[1].removeSuffix(".json")] = runCatching { JSONArray(String(b, Charsets.UTF_8)).let { a -> (0 until a.length()).map { a.getJSONObject(it) } } }.getOrElse { throw bad("ব্যাকআপের ব্যবহারকারী-ডেটা পড়া যায়নি") }
                }
                parts.size >= 3 && parts[0] == "d" -> {
                    val id = parts[1]; if (!ID.matches(id)) throw bad("ব্যাকআপে অগ্রহণযোগ্য ডকুমেন্ট-আইডি")
                    val d = st.docs.getOrPut(id) { if (st.docs.size >= MAX_DOCS) throw bad("ডকুমেন্ট-সংখ্যা সীমার বেশি"); StagedDoc(id) }
                    when {
                        parts.size == 3 && parts[2] == "meta.json" -> { d.meta = parseMeta(small(z, MAX_SMALL), id, d); }
                        parts.size == 3 && parts[2] == "orig" -> {
                            if (d.meta == null) throw bad("ডকুমেন্টের তথ্য (meta) মূল ফাইলের আগে আসেনি")
                            val room = freeSpace() - MIN_FREE
                            if (room <= 0) throw StorageFull("ফোনে পর্যাপ্ত খালি জায়গা নেই — আগের ভল্ট অপরিবর্তিত আছে")
                            val f = File(staging, "$id.enc")
                            d.origSize = try { vault.encryptInto(f, id, NoClose(z), minOf(room, MAX_DOC_BYTES), cancelled) }
                                catch (ex: IllegalArgumentException) { if (room < MAX_DOC_BYTES) throw StorageFull("ফোনে জায়গা কম — আগের ভল্ট অপরিবর্তিত আছে") else throw bad(ex.message ?: "ফাইল সীমার বেশি বড়") }
                            d.origFile = f; st.origBytes += d.origSize
                            if (st.origBytes > MAX_TOTAL_BYTES) throw bad("ব্যাকআপের মোট আকার সীমার বেশি")
                        }
                        parts.size == 4 && (parts[2] == "t" || parts[2] == "b") && PAGE.matches(parts[3]) -> {
                            if (d.meta == null) throw bad("ডকুমেন্টের তথ্য (meta) আগে আসেনি")
                            val n = parts[3].removeSuffix(".json").toInt()
                            val raw = small(z, MAX_SMALL); smallBytes += raw.size
                            if (smallBytes > MAX_TEXT_TOTAL) throw bad("ব্যাকআপের লেখা অংশ সীমার বেশি বড়")
                            val page = parsePage(raw, n) ?: throw bad("একটি পৃষ্ঠার লেখা পড়া যায়নি")
                            (if (parts[2] == "t") d.texts else d.trans)[n] = page
                        }
                        else -> throw bad("ব্যাকআপে অজানা এন্ট্রি: ${parts.drop(2).joinToString("/").take(40)}")
                    }
                }
                else -> throw bad("ব্যাকআপে অজানা এন্ট্রি")
            }
        }
        // Read to the very end of the ENCRYPTED stream: this is what authenticates the last segment (a file cut after a complete entry fails here).
        var tail = 0L; val buf = ByteArray(8192)
        while (true) { val n = dec.read(buf); if (n < 0) break; tail += n; if (tail > MAX_TRAILER) throw bad("ব্যাকআপের শেষে অপ্রত্যাশিত বাড়তি তথ্য") }
        if (st.version == 0) throw bad("ফাইলটি ব্যাকআপ নয়")
        return st
    }

    private fun validate(st: Staged) {
        if (st.docs.size != st.manifestDocs) throw bad("ব্যাকআপে ডকুমেন্টের সংখ্যা মেলেনি (লেখা ${st.manifestDocs}, পাওয়া ${st.docs.size})")
        for (d in st.docs.values) {
            if (d.meta == null || d.origFile == null) throw bad("একটি ডকুমেন্টের তথ্য বা মূল ফাইল অনুপস্থিত")
            val total = d.extract?.size ?: 0
            if (d.texts.keys.any { it !in 1..maxOf(total, 1) }) throw bad("একটি পৃষ্ঠা-নম্বর সীমার বাইরে")
            val tt = d.translate?.size ?: total
            if (d.trans.keys.any { it !in 1..maxOf(tt, total, 1) }) throw bad("একটি অনুবাদ-পৃষ্ঠার নম্বর সীমার বাইরে")
        }
        if (st.version >= 2) {
            val e = st.end ?: throw bad("ব্যাকআপ অসম্পূর্ণ (শেষ-চিহ্ন নেই)")
            if (e.optInt("entries", -1) != st.entries || e.optInt("docs", -1) != st.docs.size || e.optLong("origBytes", -1) != st.origBytes) throw bad("ব্যাকআপের মোট হিসাব মেলেনি — ফাইল অসম্পূর্ণ বা পরিবর্তিত")
            if (USER_FILES.any { it !in st.user }) throw bad("ব্যাকআপে ব্যবহারকারী-ডেটা অংশ অনুপস্থিত")
        }
    }

    private fun publish(st: Staged): Report {
        val fresh = st.docs.values.filter { !vault.contains(it.id) }
        val skipped = st.docs.size - fresh.size
        if (fresh.isEmpty()) return Report(0, skipped, mergeUser(st))
        val journal = journalFile(); journal.parentFile?.mkdirs()
        journal.writeBytes(vault.seal(JSONArray(fresh.map { it.id }).toString().toByteArray(), JOURNAL_AAD))   // written BEFORE anything becomes visible
        try {
            for (d in fresh) {
                d.texts.forEach { (p, t) -> wf.texts.put(d.id, t.copy(page = p)) }
                d.trans.forEach { (p, t) -> wf.translations.put(d.id, t.copy(page = p)) }
                for ((arr, kind) in listOf(d.extract to DocWorkflow.Kind.Extract, d.translate to DocWorkflow.Kind.Translate)) if (arr != null)
                    wf.jobs.save(JobState("${d.id}-${kind.key}", d.id, kind.key, arr, JobStatus.PAUSED))
            }
            vault.publishStaged(fresh.map { d -> VaultDoc(d.id, d.meta!!.getString("name"), d.origSize, d.meta!!.getLong("added")) to d.origFile!! })   // commit point
        } catch (t: Throwable) {
            fresh.forEach { rollbackDerived(it.id) }; journal.delete(); throw t
        }
        journal.delete()
        return Report(fresh.size, skipped, mergeUser(st))
    }

    private fun mergeUser(st: Staged): Int {
        if (st.version < 2) return 0
        val ids = vault.list().map { it.id }.toSet(); var n = 0
        runCatching { n += wf.marks.mergeJson(st.user["marks"].orEmpty(), ids) }
        runCatching { n += wf.glossary.mergeJson(st.user["glossary"].orEmpty()) }
        runCatching { n += wf.vocab.mergeJson(st.user["vocab"].orEmpty(), ids) }
        runCatching { n += wf.reading.mergeJson(st.user["reading"].orEmpty(), ids) }
        return n
    }

    private fun rollbackDerived(id: String) {
        runCatching { wf.texts.deleteDoc(id) }; runCatching { wf.translations.deleteDoc(id) }
        DocWorkflow.Kind.entries.forEach { k -> runCatching { wf.jobs.delete(wf.jobId(VaultDoc(id, "", 0, 0), k)) } }
    }

    /** Cleans up after a restore that was killed half-way: ids in the journal that never reached the index lose their derived data; orphan files and staging folders go. */
    fun recoverInterrupted() = recoverInterrupted(alreadyLocked = false)
    private fun recoverInterrupted(alreadyLocked: Boolean) {
        if (!alreadyLocked && !RUNNING.compareAndSet(false, true)) return
        try {
            val j = journalFile()
            if (j.exists()) {
                runCatching {
                    val a = JSONArray(String(vault.unseal(j.readBytes(), JOURNAL_AAD), Charsets.UTF_8))
                    for (i in 0 until a.length()) a.getString(i).let { id -> if (!vault.contains(id)) rollbackDerived(id) }
                }
                j.delete()
            }
            vault.removeOrphans()
            if (!alreadyLocked) vault.removeStaleStaging()
        } finally { if (!alreadyLocked) RUNNING.set(false) }
    }

    private fun journalFile() = File(vault.journalDir(), "restore.journal")   // not ".enc": the orphan sweep must not take it

    // ---------------------------------------------------------------- parsing helpers

    private fun small(z: InputStream, limit: Int): ByteArray = z.readUpTo(limit + 1L).also { if (it.size > limit) throw bad("ব্যাকআপের একটি অংশ অনুমোদিত সীমার চেয়ে বড়") }
    private fun parseObj(b: ByteArray): JSONObject = runCatching { JSONObject(String(b, Charsets.UTF_8)) }.getOrElse { throw bad("ব্যাকআপের একটি তথ্য-ফাইল পড়া যায়নি") }

    private fun parseMeta(b: ByteArray, id: String, d: StagedDoc): JSONObject {
        val m = parseObj(b)
        if (m.optString("id") != id) throw bad("ডকুমেন্টের আইডি মেলেনি")
        val name = m.optString("name").filter { it.code >= 32 && it != '/' && it != '\\' }.trim().take(255)
        if (name.isEmpty() || m.optLong("added", -1) < 0) throw bad("ডকুমেন্টের নাম বা তারিখ অগ্রহণযোগ্য")
        m.put("name", name)
        d.extract = statusArray(m, "extract"); d.translate = statusArray(m, "translate")
        return m
    }

    private fun statusArray(m: JSONObject, key: String): IntArray? {
        if (m.isNull(key) || !m.has(key)) return null
        val a = m.optJSONArray(key) ?: throw bad("কাজের অবস্থা-তালিকা অগ্রহণযোগ্য")
        if (a.length() > MAX_PAGES) throw bad("পৃষ্ঠা-সংখ্যা সীমার বেশি")
        return IntArray(a.length()) { i -> a.getInt(i).also { if (it !in 0 until PageStatus.entries.size) throw bad("পৃষ্ঠার অবস্থা-কোড অগ্রহণযোগ্য") } }
    }

    private fun parsePage(b: ByteArray, page: Int): PageText? = runCatching {
        val o = JSONObject(String(b, Charsets.UTF_8))
        PageText(page, o.getString("t"), if (o.isNull("l")) null else o.getString("l"), ocr = o.optBoolean("o"))
    }.getOrNull()

    private class NoClose(private val s: InputStream) : InputStream() {
        override fun read() = s.read(); override fun read(b: ByteArray, o: Int, l: Int) = s.read(b, o, l); override fun close() {}
    }

    companion object {
        const val VERSION = 2
        const val MAX_DOCS = 5_000; const val MAX_ENTRIES = 1_000_000; const val MAX_PAGES = 100_000
        const val MAX_SMALL = 1 shl 20                       // one page / meta / manifest
        const val MAX_USER = 16 shl 20                       // notes, glossary, vocabulary, positions
        const val MAX_TEXT_TOTAL = 512L shl 20               // all page texts together
        const val MAX_DOC_BYTES = 8L shl 30; const val MAX_TOTAL_BYTES = 64L shl 30
        const val MAX_TRAILER = 64L shl 20
        const val MIN_FREE = 200L shl 20
        private val USER_FILES = listOf("marks", "glossary", "vocab", "reading")
        private val ID = Regex("[A-Za-z0-9-]{8,64}"); private val PAGE = Regex("[0-9]{1,6}\\.json")
        private val JOURNAL_AAD = "restore-journal".toByteArray()
        private val RUNNING = AtomicBoolean(false); private val CANCEL = AtomicBoolean(false)
        /** Asks a running restore to stop at its next safe point (the owner's cancel button, or the app locking). The live vault is untouched until the final publish step. */
        fun requestCancel() { CANCEL.set(true) }
    }
}
