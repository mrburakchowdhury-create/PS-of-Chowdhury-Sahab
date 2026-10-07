package app.personal.banglaai.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModelStoreTest {
    @Test fun checksumSidecarFilesAreNotListedAsModels() {
        val ctx = ApplicationProvider.getApplicationContext<Context>(); val store = ModelStore(ctx)
        val dir = File(ctx.noBackupFilesDir, "models").apply { mkdirs() }
        File(dir, "m.litertlm").writeBytes(ByteArray(10)); File(dir, "m.litertlm.sha256").writeText("abc"); File(dir, "half.tmp").writeBytes(ByteArray(1))
        assertEquals(listOf("m.litertlm"), store.list().map { it.name })
        assertEquals("abc", store.list().single().sha256)
        store.delete("m.litertlm"); assertTrue(store.list().isEmpty()); assertFalse(File(dir, "m.litertlm.sha256").exists())
    }

    @Test fun missingChecksumCanBeComputedWithoutCopying() {
        val ctx = ApplicationProvider.getApplicationContext<Context>(); val store = ModelStore(ctx)
        val dir = File(ctx.noBackupFilesDir, "models").apply { mkdirs() }
        File(dir, "n.litertlm").writeBytes("hello".toByteArray())
        assertEquals("", store.list().single { it.name == "n.litertlm" }.sha256)
        val r = store.rehash("n.litertlm") { }
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", r.sha256)   // SHA-256("hello")
        assertEquals(r.sha256, store.list().single { it.name == "n.litertlm" }.sha256)
        store.delete("n.litertlm")
    }
}
