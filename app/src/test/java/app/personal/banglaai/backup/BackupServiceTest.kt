package app.personal.banglaai.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.personal.banglaai.doc.DocWorkflow
import app.personal.banglaai.doc.PageText
import app.personal.banglaai.jobs.JobState
import app.personal.banglaai.jobs.JobStatus
import app.personal.banglaai.jobs.PageStatus
import app.personal.banglaai.security.KeyVault
import app.personal.banglaai.security.Pepper
import app.personal.banglaai.storage.ModelStore
import app.personal.banglaai.storage.Vault
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Restore must be all-or-nothing. Real Vault + KeyVault + DocWorkflow on the Robolectric file system (the Keystore layer is replaced by a fake; everything else is real code).
 * Not an on-device test: no real Keystore, no real storage-full condition (simulated through the free-space provider).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupServiceTest {
    @get:Rule val tmp = TemporaryFolder()
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val pass = "correct horse battery".toCharArray()
    private lateinit var vault: Vault; private lateinit var wf: DocWorkflow; private lateinit var svc: BackupService
    private val pepper = object : Pepper { override fun wrap(b: ByteArray) = b.reversedArray(); override fun unwrap(b: ByteArray) = b.reversedArray() }

    @Before fun setUp() {
        File(ctx.noBackupFilesDir, "vault").deleteRecursively(); File(ctx.noBackupFilesDir, "derived").deleteRecursively()
        ctx.getSharedPreferences("vault_key2", Context.MODE_PRIVATE).edit().clear().commit()
        val keys = KeyVault(ctx, pepper, 1000); check(keys.unlockWithPin("123456".toCharArray()))
        vault = Vault(ctx, keys); wf = DocWorkflow(ctx, vault, ModelStore(ctx)); svc = BackupService(vault, wf)
    }

    private fun addDoc(name: String, content: ByteArray): app.personal.banglaai.storage.VaultDoc {
        val f = tmp.newFile(name); f.writeBytes(content); return vault.import(Uri.fromFile(f))
    }

    /** Two documents with text, translation, job coverage, plus notes/bookmarks/glossary/vocab/reading position. */
    private fun fixture(): Pair<app.personal.banglaai.storage.VaultDoc, app.personal.banglaai.storage.VaultDoc> {
        val a = addDoc("এক.pdf", ByteArray(200_000) { (it % 251).toByte() })
        val b = addDoc("two.txt", "অনুবাদের জন্য লেখা العلم نور".toByteArray())
        for (d in listOf(a, b)) {
            val ext = JobState("${d.id}-extract", d.id, "extract", 3); ext.mark(1, PageStatus.DONE); ext.mark(2, PageStatus.DONE); ext.mark(3, PageStatus.UNREADABLE); ext.status = JobStatus.DONE; wf.jobs.save(ext)
            val tr = JobState("${d.id}-translate", d.id, "translate", 3); tr.mark(1, PageStatus.DONE); wf.jobs.save(tr)
            wf.texts.put(d.id, PageText(1, "Page one of ${d.name}", "i")); wf.texts.put(d.id, PageText(2, "পৃষ্ঠা দুই", null, ocr = true))
            wf.translations.put(d.id, PageText(1, "প্রথম পৃষ্ঠার অনুবাদ", "i"))
        }
        wf.marks.add(a.id, 2, app.personal.banglaai.userdata.Mark.Kind.NOTE, "আমার নোট"); wf.marks.add(b.id, 1, app.personal.banglaai.userdata.Mark.Kind.BOOKMARK, "")
        wf.glossary.add("sultan", "সুলতান"); wf.vocab.add("zakat", "যাকাত", a.id, 2); wf.reading.put(a.id, 2, 3)
        return a to b
    }

    private fun backupBytes(): ByteArray = ByteArrayOutputStream().also { svc.create(it, pass) }.toByteArray()
    private fun wipe() {
        vault.list().forEach { d -> wf.deleteDerived(d); vault.delete(d.id) }
        wf.glossary.all().forEach { wf.glossary.remove(it.source) }
    }
    private fun snapshot(): String {
        val sb = StringBuilder()
        for (d in vault.list().sortedBy { it.id }) {
            sb.append("DOC ${d.id} ${d.name} ${d.size} ${d.added}\n")
            sb.append("  bytes ").append(vault.open(d.id).use { it.readBytes() }.contentHashCode()).append('\n')
            for (k in DocWorkflow.Kind.entries) wf.job(d, k)?.let { sb.append("  job ${k.key} ${it.pages.toList()} \n") }
            for (p in 1..5) { wf.texts.get(d.id, p)?.let { sb.append("  t$p ${it.text}|${it.printedLabel}|${it.ocr}\n") }; wf.translations.get(d.id, p)?.let { sb.append("  b$p ${it.text}|${it.printedLabel}\n") } }
            wf.marks.forDoc(d.id).sortedBy { it.page }.forEach { sb.append("  mark ${it.page} ${it.kind} ${it.text}\n") }
            wf.reading.get(d.id)?.let { sb.append("  read $it\n") }
        }
        wf.glossary.all().forEach { sb.append("GL ${it.source}=${it.bengali}\n") }; wf.vocab.all().forEach { sb.append("VOC ${it.term}=${it.meaning}@${it.page}\n") }
        return sb.toString()
    }
    private fun liveState() = snapshot() + vaultFiles()
    private fun vaultFiles() = File(ctx.noBackupFilesDir, "vault").walkTopDown().filter { it.isFile }.map { it.relativeTo(ctx.noBackupFilesDir).path }.sorted().joinToString(",")
    private fun noStagingLeft() = assertTrue("staging folders must be gone", File(ctx.noBackupFilesDir, "vault").listFiles { f -> f.name.startsWith("staging-") }.orEmpty().isEmpty())

    // ---------------------------------------------------------------------------------------------------

    @Test fun roundTripRestoresEverySupportedKindOfData() {
        fixture(); val before = snapshot(); assertTrue(before.contains("আমার নোট")); assertTrue(before.contains("GL sultan")); assertTrue(before.contains("VOC zakat")); assertTrue(before.contains("read (2, 3)"))
        val bytes = backupBytes()
        wipe(); assertEquals(0, vault.list().size)
        val r = svc.restore(ByteArrayInputStream(bytes), pass)
        assertEquals(2, r.restored); assertEquals(0, r.skipped); assertTrue(r.userItems >= 4)
        assertEquals(before.replace(Regex("job (extract|translate) .*\\n"), ""), snapshot().replace(Regex("job (extract|translate) .*\\n"), ""))   // content equal ...
        // ... and job coverage equal too (restored jobs come back PAUSED so nothing runs by itself)
        for (d in vault.list()) { assertEquals(listOf(1, 1, 3), wf.job(d, DocWorkflow.Kind.Extract)!!.pages.toList()); assertEquals(JobStatus.PAUSED, wf.job(d, DocWorkflow.Kind.Extract)!!.status) }
        noStagingLeft()
    }

    @Test fun existingDocumentIsNeverOverwritten() {
        val (a, _) = fixture(); val bytes = backupBytes()
        wf.texts.put(a.id, PageText(1, "LOCAL EDIT", null))
        val r = svc.restore(ByteArrayInputStream(bytes), pass)
        assertEquals(0, r.restored); assertEquals(2, r.skipped)
        assertEquals("LOCAL EDIT", wf.texts.get(a.id, 1)!!.text)
    }

    @Test fun wrongPasswordLeavesTheVaultUntouched() {
        fixture(); val bytes = backupBytes(); wipe(); addDoc("keep.txt", "KEEP".toByteArray()); val before = liveState()
        try { svc.restore(ByteArrayInputStream(bytes), "wrong passphrase!!".toCharArray()); fail() } catch (e: PassphraseBackup.BadBackup) { assertTrue(e.message!!.contains("অপরিবর্তিত")) }
        assertEquals(before, liveState()); noStagingLeft()
    }

    @Test fun truncatedBackupIsRejectedAtEveryCutPointAndNothingChanges() {
        fixture(); val bytes = backupBytes(); wipe(); addDoc("keep.txt", "KEEP".toByteArray()); val before = liveState()
        val cuts = (listOf(1, 8, 30, 100, bytes.size / 3, bytes.size / 2, bytes.size - 2000, bytes.size - 300, bytes.size - 100, bytes.size - 40, bytes.size - 17, bytes.size - 16, bytes.size - 1)).filter { it in 1 until bytes.size }
        for (c in cuts) {
            try { svc.restore(ByteArrayInputStream(bytes.copyOf(c)), pass); fail("cut at $c of ${bytes.size} must be rejected") } catch (e: PassphraseBackup.BadBackup) { }
            assertEquals("cut $c", before, liveState())
        }
        noStagingLeft()
    }

    @Test fun corruptedByteAnywhereIsRejected() {
        fixture(); val bytes = backupBytes(); wipe(); val before = liveState()
        for (pos in listOf(30, bytes.size / 4, bytes.size / 2, bytes.size - 50)) {
            val bad = bytes.clone().also { it[pos] = (it[pos] + 1).toByte() }
            try { svc.restore(ByteArrayInputStream(bad), pass); fail("flip at $pos") } catch (e: PassphraseBackup.BadBackup) { }
            assertEquals(before, liveState())
        }
    }

    @Test fun cancelDuringStagingChangesNothing() {
        fixture(); val bytes = backupBytes(); wipe(); val before = liveState()
        var calls = 0
        try { svc.restore(ByteArrayInputStream(bytes), pass) { ++calls > 6 }; fail() } catch (e: java.util.concurrent.CancellationException) { }
        assertEquals(before, liveState()); noStagingLeft()
    }

    @Test fun notEnoughStorageIsReportedAndNothingChanges() {
        fixture(); val bytes = backupBytes(); wipe(); val before = liveState()
        val small = BackupService(vault, wf, freeSpace = { 50L shl 20 })   // below the safety margin
        try { small.restore(ByteArrayInputStream(bytes), pass); fail() } catch (e: BackupService.StorageFull) { }
        assertEquals(before, liveState()); noStagingLeft()
    }

    @Test fun failureWhilePublishingRollsEverythingBack() {
        val (a, _) = fixture(); val bytes = backupBytes(); wipe(); val before = liveState()
        // the index cannot be written (a directory squats on its temp name): files are renamed into place, then the commit fails -> everything must be undone
        File(File(ctx.noBackupFilesDir, "vault"), "index.tmp").mkdirs()
        try { svc.restore(ByteArrayInputStream(bytes), pass); fail("commit must fail") } catch (e: Exception) { assertFalse(e is PassphraseBackup.BadBackup) }
        assertNull(wf.texts.get(a.id, 1)); assertNull(wf.translations.get(a.id, 1)); assertNull(wf.job(a, DocWorkflow.Kind.Extract))
        assertFalse(File(File(ctx.noBackupFilesDir, "vault"), "${a.id}.enc").exists())
        assertFalse(File(File(ctx.noBackupFilesDir, "vault"), "restore.journal").exists())
        File(File(ctx.noBackupFilesDir, "vault"), "index.tmp").delete()
        assertEquals(before, liveState()); noStagingLeft()
    }

    @Test fun interruptedRestoreIsCleanedByRecovery() {
        val (a, _) = fixture(); val bytes = backupBytes(); wipe()
        // simulate a crash after derived data + journal were written but before the index commit
        val journal = File(File(ctx.noBackupFilesDir, "vault"), "restore.journal")
        journal.writeBytes(vault.seal(JSONArray(listOf(a.id)).toString().toByteArray(), "restore-journal".toByteArray()))
        wf.texts.put(a.id, PageText(1, "half-restored", null)); File(File(ctx.noBackupFilesDir, "vault"), "${a.id}.enc").writeBytes(byteArrayOf(9))
        svc.recoverInterrupted()
        assertNull(wf.texts.get(a.id, 1)); assertFalse(journal.exists()); assertEquals(0, vault.list().size)
        assertFalse(File(File(ctx.noBackupFilesDir, "vault"), "${a.id}.enc").exists())
        assertEquals(2, svc.restore(ByteArrayInputStream(bytes), pass).restored)   // and a normal restore works afterwards
    }

    // ---- hostile / malformed containers (built by hand, then encrypted like a real backup) ----

    private fun container(entries: (ZipOutputStream) -> Unit): ByteArray {
        val zb = ByteArrayOutputStream(); ZipOutputStream(zb).use(entries)
        val out = ByteArrayOutputStream(); PassphraseBackup.encryptTo(out, pass, 100_000).use { it.write(zb.toByteArray()) }; return out.toByteArray()
    }
    private fun ZipOutputStream.e(name: String, body: String) { putNextEntry(ZipEntry(name)); write(body.toByteArray()); closeEntry() }
    private fun meta(id: String) = JSONObject().put("id", id).put("name", "x.txt").put("added", 1L).put("extract", JSONObject.NULL).put("translate", JSONObject.NULL).toString()
    private val ID1 = "11111111-2222-3333-4444-555555555555"
    private fun expectBad(b: ByteArray, what: String) {
        val before = liveState()
        try { svc.restore(ByteArrayInputStream(b), pass); fail("must be rejected: $what") } catch (e: PassphraseBackup.BadBackup) { }
        assertEquals(what, before, liveState()); noStagingLeft()
    }

    @Test fun v1BackupStillRestores() {
        val b = container { z -> z.e("manifest.json", """{"version":1,"docs":1}"""); z.e("d/$ID1/meta.json", meta(ID1)); z.e("d/$ID1/orig", "hello"); z.e("d/$ID1/t/1.json", """{"t":"page","l":null,"o":false}""") }
        val r = svc.restore(ByteArrayInputStream(b), pass); assertEquals(1, r.restored)
        assertEquals("page", wf.texts.get(ID1, 1)!!.text)
    }

    @Test fun pathTraversalAndBadIdsAreRejected() {
        expectBad(container { z -> z.e("manifest.json", """{"version":1,"docs":1}"""); z.e("d/../evil/meta.json", "{}") }, "dotdot")
        expectBad(container { z -> z.e("manifest.json", """{"version":1,"docs":1}"""); z.e("d/a/b/../../meta.json", "{}") }, "dotdot2")
        expectBad(container { z -> z.e("manifest.json", """{"version":1,"docs":1}"""); z.e("d/bad id!/meta.json", "{}") }, "bad id")
        expectBad(container { z -> z.e("manifest.json", """{"version":1,"docs":1}"""); z.e("d/$ID1/meta.json", meta("22222222-2222-3333-4444-555555555555")) }, "id mismatch")
    }

    @Test fun duplicateEntriesAreRejected() {
        val raw = ByteArrayOutputStream(); ZipOutputStream(raw).use { z -> z.e("manifest.json", """{"version":1,"docs":1}"""); z.e("d/$ID1/meta.json", meta(ID1)); z.e("d/$ID1/meta.jsxn", meta(ID1)); z.e("d/$ID1/orig", "a") }
        val patched = String(raw.toByteArray(), Charsets.ISO_8859_1).replace("meta.jsxn", "meta.json").toByteArray(Charsets.ISO_8859_1)   // same-length rename: a true duplicate
        val out = ByteArrayOutputStream(); PassphraseBackup.encryptTo(out, pass, 100_000).use { it.write(patched) }
        expectBad(out.toByteArray(), "duplicate entry")
    }

    @Test fun manifestAndEndCountsMustMatch() {
        expectBad(container { z -> z.e("manifest.json", """{"version":1,"docs":2}"""); z.e("d/$ID1/meta.json", meta(ID1)); z.e("d/$ID1/orig", "a") }, "manifest says 2 docs")
        expectBad(container { z -> z.e("manifest.json", """{"version":1,"docs":1}"""); z.e("d/$ID1/meta.json", meta(ID1)) }, "missing orig")
        expectBad(container { z -> z.e("manifest.json", """{"version":99,"docs":0}""") }, "unknown version")
        expectBad(container { z -> z.e("d/$ID1/meta.json", meta(ID1)) }, "no manifest first")
        // v2 without end marker / with wrong totals / without user data parts
        expectBad(container { z -> z.e("manifest.json", """{"version":2,"docs":0}"""); for (n in listOf("marks", "glossary", "vocab", "reading")) z.e("u/$n.json", "[]") }, "no end marker")
        expectBad(container { z -> z.e("manifest.json", """{"version":2,"docs":0}"""); for (n in listOf("marks", "glossary", "vocab", "reading")) z.e("u/$n.json", "[]"); z.e("end.json", """{"entries":99,"docs":0,"origBytes":0}""") }, "wrong totals")
    }

    @Test fun pageNumbersAndStatusCodesMustBeValid() {
        val m = JSONObject().put("id", ID1).put("name", "x").put("added", 1L).put("extract", JSONArray(listOf(1, 1))).put("translate", JSONObject.NULL).toString()
        expectBad(container { z -> z.e("manifest.json", """{"version":1,"docs":1}"""); z.e("d/$ID1/meta.json", m); z.e("d/$ID1/orig", "a"); z.e("d/$ID1/t/7.json", """{"t":"x","l":null,"o":false}""") }, "page 7 of 2")
        val bad = JSONObject().put("id", ID1).put("name", "x").put("added", 1L).put("extract", JSONArray(listOf(1, 9))).put("translate", JSONObject.NULL).toString()
        expectBad(container { z -> z.e("manifest.json", """{"version":1,"docs":1}"""); z.e("d/$ID1/meta.json", bad); z.e("d/$ID1/orig", "a") }, "status code 9")
        expectBad(container { z -> z.e("manifest.json", """{"version":1,"docs":1}"""); z.e("d/$ID1/meta.json", meta(ID1)); z.e("d/$ID1/orig", "a"); z.e("d/$ID1/t/abc.json", "{}") }, "page name")
    }

    @Test fun oversizedSmallEntryAndUnknownEntriesAreRejected() {
        expectBad(container { z -> z.e("manifest.json", """{"version":1,"docs":1}"""); z.e("d/$ID1/meta.json", meta(ID1)); z.e("d/$ID1/orig", "a"); z.e("d/$ID1/t/1.json", "x".repeat((1 shl 20) + 5)) }, ">1MB page")
        expectBad(container { z -> z.e("manifest.json", """{"version":1,"docs":0}"""); z.e("surprise.bin", "x") }, "unknown entry")
        expectBad(container { z -> z.e("manifest.json", """{"version":1,"docs":0}"""); z.putNextEntry(ZipEntry("dir/")); z.closeEntry() }, "directory entry")
    }

    @Test fun notABackupFileGetsAClearMessage() {
        val before = liveState()
        try { svc.restore(ByteArrayInputStream("hello world, not a backup".toByteArray()), pass); fail() } catch (e: PassphraseBackup.BadBackup) { assertTrue(e.message!!.isNotBlank()) }
        assertEquals(before, liveState())
    }

    @Test fun secondRestoreWhileOneRunsIsRefused() {
        fixture(); val bytes = backupBytes(); wipe()
        val gate = java.util.concurrent.CountDownLatch(1); val started = java.util.concurrent.CountDownLatch(1)
        val t = Thread { runCatching { svc.restore(ByteArrayInputStream(bytes), pass) { started.countDown(); gate.await(); false } } }
        t.start(); started.await()
        try { svc.restore(ByteArrayInputStream(bytes), pass); fail() } catch (e: IllegalStateException) { }
        gate.countDown(); t.join()
    }

    @Test fun backupDoesNotContainPlaintext() {
        fixture(); val s = String(backupBytes(), Charsets.ISO_8859_1)
        assertFalse(s.contains("আমার")); assertFalse(s.contains("Page one")); assertFalse(s.contains("sultan"))
    }
}
