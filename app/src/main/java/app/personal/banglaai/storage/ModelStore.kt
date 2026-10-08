package app.personal.banglaai.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.security.MessageDigest

data class ModelFile(val name: String, val size: Long, val sha256: String)

/**
 * Manual local import of AI model files (copied from the phone's storage; no download, no network).
 * The SHA-256 is computed while copying so it can be compared with the checksum published by the model's official source.
 * Models are public files, so they are not encrypted; they live in no-backup storage.
 */
class ModelStore(private val context: Context) {
    private val dir = File(context.noBackupFilesDir, "models").apply { mkdirs() }

    fun list(): List<ModelFile> = dir.listFiles { f -> f.isFile && !f.name.endsWith(".tmp") && !f.name.endsWith(".sha256") }.orEmpty()
        .sortedBy { it.name }.map { ModelFile(it.name, it.length(), File(dir, it.name + ".sha256").takeIf(File::exists)?.readText().orEmpty()) }

    fun freeBytes() = dir.usableSpace

    fun import(uri: Uri, onProgress: (Long) -> Unit, onSize: (Long) -> Unit = {}): ModelFile {
        var name = "model.bin"; var declared = -1L
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { declared = c.getLong(it) }
            }
        }
        if (declared > 0) onSize(declared)
        // A file name from a content provider is untrusted: keep only the last path part, drop control characters, and refuse "." / ".." style names.
        name = File(name.replace('\\', '/')).name.filter { it.code >= 32 && it != '/' }.trim().let { if (it.isBlank() || it.all { c -> c == '.' }) "model.bin" else it }
        require(declared < 0 || dir.usableSpace > declared + 500L * 1024 * 1024) { "ফোনে পর্যাপ্ত খালি জায়গা নেই" }
        val tmp = File(dir, "$name.tmp"); val out = File(dir, name)
        val md = MessageDigest.getInstance("SHA-256")
        try {
            var total = 0L
            (context.contentResolver.openInputStream(uri) ?: error("ফাইল খোলা যায়নি")).use { src ->
                tmp.outputStream().use { dst ->
                    val buf = ByteArray(1 shl 20)
                    while (true) { val n = src.read(buf); if (n < 0) break; dst.write(buf, 0, n); md.update(buf, 0, n); total += n; onProgress(total) }
                }
            }
            check(tmp.renameTo(out)) { "সংরক্ষণ ব্যর্থ" }
            val hex = md.digest().joinToString("") { "%02x".format(it) }
            File(dir, "$name.sha256").writeText(hex)
            return ModelFile(name, total, hex)
        } catch (e: Throwable) { tmp.delete(); throw e }
    }

    /** Computes the SHA-256 of an already-copied model file and records it (used when the sidecar is missing). */
    fun rehash(name: String, onProgress: (Long) -> Unit): ModelFile {
        val f = File(dir, File(name).name); require(f.isFile) { "ফাইল পাওয়া যায়নি" }
        val md = MessageDigest.getInstance("SHA-256"); var total = 0L
        f.inputStream().use { src -> val buf = ByteArray(1 shl 20); while (true) { val n = src.read(buf); if (n < 0) break; md.update(buf, 0, n); total += n; onProgress(total) } }
        val hex = md.digest().joinToString("") { "%02x".format(it) }
        File(dir, f.name + ".sha256").writeText(hex)
        return ModelFile(f.name, f.length(), hex)
    }

    fun delete(name: String) { File(dir, File(name).name).delete(); File(dir, File(name).name + ".sha256").delete() }
}
