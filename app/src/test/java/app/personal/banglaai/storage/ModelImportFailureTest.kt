package app.personal.banglaai.storage

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

/** Offline model import with a wrong, cut-off, oversized-claim, hostile-name or failing source: the store must stay clean and the verdict must say what is wrong. */
class FakeModelProvider : ContentProvider() {
    companion object { @Volatile var name = "m.litertlm"; @Volatile var declared = -1L; @Volatile var file: File? = null; @Volatile var failAfter = -1L }
    override fun onCreate() = true
    override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor =
        MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply { addRow(arrayOf<Any?>(name, if (declared >= 0) declared else null)) }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val f = file ?: throw FileNotFoundException()
        if (failAfter < 0) return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
        // a source that dies half-way (unplugged card, revoked permission): serve only the first bytes through a pipe, then close it with an error
        val pipe = ParcelFileDescriptor.createReliablePipe()
        Thread { ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { o -> f.inputStream().use { i -> val b = ByteArray(failAfter.toInt()); val n = i.read(b); o.write(b, 0, n) } }; pipe[1].closeWithError("source failed") }.start()
        return pipe[0]
    }
    override fun getType(u: Uri): String? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, a: Array<String>?) = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModelImportFailureTest {
    @get:Rule val tmp = TemporaryFolder()
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val uri = Uri.parse("content://app.test.models/m")
    private lateinit var store: ModelStore
    private val dir get() = File(ctx.noBackupFilesDir, "models")

    @Before fun setUp() {
        Robolectric.buildContentProvider(FakeModelProvider::class.java).create("app.test.models")
        dir.deleteRecursively(); store = ModelStore(ctx)
        FakeModelProvider.name = "m.litertlm"; FakeModelProvider.declared = -1; FakeModelProvider.failAfter = -1
    }
    private fun source(bytes: Int): File = tmp.newFile().also { it.writeBytes(ByteArray(bytes) { (it % 7).toByte() }); FakeModelProvider.file = it }

    @Test fun cutOffCopyOfAnOfficialNameIsFlaggedAsMismatchAndNotUsable() {
        FakeModelProvider.name = "gemma-4-E4B-it.litertlm"; source(5_000_000)
        val m = store.import(uri, { })
        val v = KnownModels.verdict(m.name, m.size, m.sha256)
        assertTrue(v is KnownModels.Verdict.Mismatch)
        assertTrue(KnownModels.explainMismatch((v as KnownModels.Verdict.Mismatch).expected, m.size, m.sha256).contains("ফাইল ছোট"))
    }

    @Test fun claimedSizeLargerThanFreeSpaceIsRefusedBeforeCopyingAndLeavesNothing() {
        source(1000); FakeModelProvider.declared = 50L shl 40   // 50 TB
        try { store.import(uri, { }); fail() } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("খালি জায়গা")) }
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test fun sourceThatFailsHalfWayLeavesNoPartialFileAndNoModelEntry() {
        source(4_000_000); FakeModelProvider.failAfter = 1_500_000; FakeModelProvider.declared = 4_000_000   // the provider announces the real size, then dies half-way
        try { store.import(uri, { }); fail("must fail") } catch (e: Exception) { assertTrue(e.message!!.contains("অসম্পূর্ণ") || e is java.io.IOException) }
        assertTrue("no .tmp left", dir.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
        assertTrue(store.list().isEmpty())
    }

    @Test fun hostileDisplayNameCannotEscapeTheModelFolder() {
        FakeModelProvider.name = "../../../evil.litertlm"; source(100)
        val m = store.import(uri, { })
        assertEquals("evil.litertlm", m.name); assertTrue(File(dir, "evil.litertlm").isFile)
        assertFalse(File(ctx.noBackupFilesDir, "evil.litertlm").exists())
    }

    @Test fun modelWithoutRecordedChecksumIsNotUsableUntilVerified() {
        File(dir.apply { mkdirs() }, "gemma-4-E4B-it.litertlm").writeBytes(ByteArray(10))   // sidecar missing (copy interrupted right at the end)
        val m = store.list().single(); assertTrue(KnownModels.verdict(m.name, m.size, m.sha256) is KnownModels.Verdict.Pending)
    }
}
