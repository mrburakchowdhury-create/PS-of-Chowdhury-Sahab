package app.personal.banglaai.storage

import android.content.Context
import java.io.File

data class StorageBreakdown(val originals: Long, val derived: Long, val models: Long, val ocrData: Long, val cache: Long, val free: Long)

/** Sizes shown to the user per category. Free space is read live each time (never assumed). */
object StorageStats {
    private fun size(f: File): Long = if (!f.exists()) 0 else if (f.isFile) f.length() else f.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun read(c: Context) = StorageBreakdown(
        originals = size(File(c.noBackupFilesDir, "vault")), derived = size(File(c.noBackupFilesDir, "derived")),
        models = size(File(c.noBackupFilesDir, "models")), ocrData = size(File(c.noBackupFilesDir, "tess")),
        cache = size(c.cacheDir), free = c.noBackupFilesDir.usableSpace)

    /** Clears only re-creatable cache (model load cache, temp PDF buffers). Never touches originals, derived text, models. */
    fun clearCache(c: Context) { c.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
}
