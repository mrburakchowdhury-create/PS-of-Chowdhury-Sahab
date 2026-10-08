package app.personal.banglaai.userdata

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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SavedQaAndFixTest {
    @get:Rule val tmp = TemporaryFolder()
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var wf: DocWorkflow; private lateinit var vault: Vault
    private val pepper = object : Pepper { override fun wrap(b: ByteArray) = b.reversedArray(); override fun unwrap(b: ByteArray) = b.reversedArray() }

    @Before fun setUp() {
        File(ctx.noBackupFilesDir, "vault").deleteRecursively(); File(ctx.noBackupFilesDir, "derived").deleteRecursively()
        ctx.getSharedPreferences("vault_key2", Context.MODE_PRIVATE).edit().clear().commit()
        val k = KeyVault(ctx, pepper, 1000); check(k.unlockWithPin("123456".toCharArray())); vault = Vault(ctx, k); wf = DocWorkflow(ctx, vault, ModelStore(ctx))
    }
    private fun doc() = tmp.newFile("a.txt").let { it.writeText("x"); vault.import(Uri.fromFile(it)) }

    @Test fun savedQaRoundTripKeepsKindSourcesAndSurvivesDocumentDeletion() {
        val d = doc(); val x = wf.savedQa.add("doc", " কী? ", " উত্তর ", listOf("a.txt — পৃষ্ঠা ৩"), d.id, 3)
        assertEquals(listOf(x.id), wf.savedQa.forDoc(d.id).map { it.id }); assertEquals("কী?", wf.savedQa.all().single().question); assertEquals(3, wf.savedQa.all().single().page)
        wf.savedQa.add("library", "প্রশ্ন", "উত্তর", listOf("The Prince — CHAPTER I"))
        assertEquals(2, wf.savedQa.all().size); wf.deleteDerived(d)
        assertEquals("deleting a file removes ITS saved answers only", 1, wf.savedQa.all().size)
        assertThrows(IllegalArgumentException::class.java) { wf.savedQa.add("doc", " ", "x", emptyList()) }
    }

    @Test fun correctionIsASeparateVersionUsedEverywhereElseAndStalesOnlyThatTranslation() {
        val d = doc()
        wf.texts.put(d.id, PageText(1, "Teh quick brown fox", "i", ocr = true)); wf.texts.put(d.id, PageText(2, "page two", null))
        wf.translations.put(d.id, PageText(1, "পুরোনো অনুবাদ ১", "i")); wf.translations.put(d.id, PageText(2, "অনুবাদ ২", null))
        val tr = JobState("${d.id}-translate", d.id, "translate", 2); tr.mark(1, PageStatus.DONE); tr.mark(2, PageStatus.DONE); tr.status = JobStatus.DONE; wf.jobs.save(tr)
        wf.saveFix(d, 1, "The quick brown fox")
        assertEquals("extracted text untouched", "Teh quick brown fox", wf.texts.get(d.id, 1)!!.text)
        assertEquals("The quick brown fox", wf.effective(d.id, 1)!!.text); assertEquals("i", wf.effective(d.id, 1)!!.printedLabel); assertFalse(wf.effective(d.id, 1)!!.ocr)
        assertEquals("page two", wf.effective(d.id, 2)!!.text)
        val j = wf.job(d, DocWorkflow.Kind.Translate)!!
        assertEquals(PageStatus.PENDING, j.pageStatus(1)); assertEquals(PageStatus.DONE, j.pageStatus(2)); assertEquals(JobStatus.PAUSED, j.status)
        assertNull(wf.translations.get(d.id, 1)); assertEquals("অনুবাদ ২", wf.translations.get(d.id, 2)!!.text)
        // the export uses the corrected text
        wf.jobs.save(app.personal.banglaai.jobs.JobState("${d.id}-extract", d.id, "extract", 2).also { it.mark(1, app.personal.banglaai.jobs.PageStatus.DONE); it.mark(2, app.personal.banglaai.jobs.PageStatus.DONE) })
        assertEquals("The quick brown fox", wf.exportPages(d).first { it.page == 1 }.original)
        wf.clearFix(d, 1); assertEquals("Teh quick brown fox", wf.effective(d.id, 1)!!.text)
    }
}
