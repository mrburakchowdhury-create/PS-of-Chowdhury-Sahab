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
}
