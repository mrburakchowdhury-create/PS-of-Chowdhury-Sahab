package app.personal.banglaai.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.crypto.tink.StreamingAead
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

data class VaultDoc(val id: String, val name: String, val size: Long, val added: Long)

/**
 * Encrypted store for imported originals. Files are streamed through AES-256-GCM (Tink streaming AEAD);
 * the keyset is sealed by a secret that only the owner's PIN (or a strong biometric) can release — see KeyVault. Lives in no-backup storage. Originals are never modified.
 * The document index (names) is encrypted as well.
 */
class Vault(private val context: Context, private val keys: app.personal.banglaai.security.KeyVault) {
    private val dir = File(context.noBackupFilesDir, "vault").apply { mkdirs() }
    private val indexFile = File(dir, "index.enc")

    private val aead: StreamingAead get() = keys.streamingAead()

    @Synchronized fun list(): List<VaultDoc> = readIndex().sortedByDescending { it.added }

    /** Copies the user-chosen file into the vault, encrypted. Throws on failure and leaves no plaintext/partial file. */
    @Synchronized fun import(uri: Uri): VaultDoc {
        val (name, declared) = queryMeta(uri)
        require(declared < 0 || dir.usableSpace > declared + MIN_FREE) { "ফোনে পর্যাপ্ত খালি জায়গা নেই" }
        val id = UUID.randomUUID().toString()
        val tmp = File(dir, "$id.tmp"); val out = File(dir, "$id.enc")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("ফাইল খোলা যায়নি")
            var total = 0L
            input.use { src ->
                aead.newEncryptingStream(FileOutputStream(tmp), id.toByteArray()).use { dst ->
                    val buf = ByteArray(64 * 1024)
                    while (true) { val n = src.read(buf); if (n < 0) break; dst.write(buf, 0, n); total += n }
                }
            }
            check(tmp.renameTo(out)) { "সংরক্ষণ ব্যর্থ" }
            val doc = VaultDoc(id, name, total, System.currentTimeMillis())
            writeIndex(readIndex() + doc)
            return doc
        } catch (e: Throwable) {
            tmp.delete(); out.delete(); throw e
        }
    }

    // ---- staged restore (backup): nothing is visible in the vault until [publishStaged] ----

    /** A fresh private folder inside the vault area for one restore attempt. Delete it with [deleteStaging] whatever happens. */
    fun newStaging(): File = File(dir, "staging-" + UUID.randomUUID()).apply { mkdirs() }
    fun deleteStaging(d: File) { if (d.parentFile == dir && d.name.startsWith("staging-")) d.deleteRecursively() }
    fun usableSpace(): Long = dir.usableSpace
    /** Parent of the restore journal (inside the vault area, so it is not part of any backup and not visible to the app's file pickers). */
    fun journalDir(): File = dir
    @Synchronized fun contains(id: String): Boolean = readIndex().any { it.id == id }

    /** Encrypts [input] into [target] exactly like a normal import (same key, same associated data = [id]) so the file can later be renamed into place. Returns the plaintext size. */
    fun encryptInto(target: File, id: String, input: java.io.InputStream, limit: Long, cancelled: () -> Boolean = { false }): Long {
        var total = 0L
        aead.newEncryptingStream(FileOutputStream(target), id.toByteArray()).use { dst ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                if (cancelled()) throw java.util.concurrent.CancellationException("বাতিল")
                val n = input.read(buf); if (n < 0) break
                total += n; require(total <= limit) { "ব্যাকআপের একটি ফাইল অনুমোদিত সীমার চেয়ে বড়" }
                dst.write(buf, 0, n)
            }
        }
        return total
    }

    /**
     * Makes all staged documents visible at once: every file is renamed into place, then the index is written ONCE (that single atomic write is the commit point).
     * If anything fails before it, the renames are undone and the vault is exactly as before. Ids that already exist are refused (never overwritten).
     */
    @Synchronized fun publishStaged(docs: List<Pair<VaultDoc, File>>) {
        val have = readIndex().map { it.id }.toSet()
        require(docs.none { it.first.id in have }) { "একই আইডির ডকুমেন্ট আগে থেকেই আছে" }
        val moved = ArrayList<File>()
        try {
            for ((d, f) in docs) { val out = File(dir, "${d.id}.enc"); check(!out.exists() && f.renameTo(out)) { "সংরক্ষণ ব্যর্থ" }; moved += out }
            writeIndex(readIndex() + docs.map { it.first })
        } catch (e: Throwable) { moved.forEach { it.delete() }; throw e }
    }

    /** Encrypted originals that no index entry points to (left by a crash between "file written" and "index written"). Returns how many were removed. */
    @Synchronized fun removeOrphans(): Int {
        val known = readIndex().map { "${it.id}.enc" }.toSet() + "index.enc"
        val orphans = dir.listFiles { f -> f.isFile && (f.name.endsWith(".enc") && f.name !in known) }.orEmpty()
        orphans.forEach { it.delete() }
        return orphans.size
    }

    /** Leftover staging folders of a restore that was killed. Only call while no restore is running. */
    fun removeStaleStaging() { dir.listFiles { f -> f.isDirectory && f.name.startsWith("staging-") }?.forEach { it.deleteRecursively() } }

    /** Small-blob encryption for derived data (job checkpoints, page text). [aad] binds the blob to its owner id. */
    fun seal(plain: ByteArray, aad: ByteArray): ByteArray = sealWith(aead, plain, aad)
    fun unseal(blob: ByteArray, aad: ByteArray): ByteArray = unsealWith(aead, blob, aad)

    /** Decrypting stream for later stages (extraction, viewer). Caller must close it. */
    fun open(id: String) = aead.newDecryptingStream(FileInputStream(File(dir, "$id.enc")), id.toByteArray())

    @Synchronized fun delete(id: String) {
        File(dir, "$id.enc").delete(); File(dir, "$id.tmp").delete()
        writeIndex(readIndex().filterNot { it.id == id })
    }

    /** Display name and size from the provider; falls back to the last path segment (e.g. file:// URIs) so the extension — which decides the format — is kept. */
    private fun queryMeta(uri: Uri): Pair<String, Long> {
        val fallback = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "ফাইল"
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); val s = c.getColumnIndex(OpenableColumns.SIZE)
                    return (if (n >= 0) c.getString(n) else null).orEmpty().ifBlank { fallback } to (if (s >= 0 && !c.isNull(s)) c.getLong(s) else -1L)
                }
            }
        }
        return fallback to -1L
    }

    private fun readIndex(): List<VaultDoc> {
        if (!indexFile.exists()) return emptyList()
        val text = aead.newDecryptingStream(FileInputStream(indexFile), "index".toByteArray()).use { String(it.readBytes(), Charsets.UTF_8) }
        val arr = JSONArray(text)
        return (0 until arr.length()).map { arr.getJSONObject(it).let { o -> VaultDoc(o.getString("id"), o.getString("name"), o.getLong("size"), o.getLong("added")) } }
    }

    private fun writeIndex(docs: List<VaultDoc>) {
        val arr = JSONArray(); docs.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("size", it.size).put("added", it.added)) }
        val tmp = File(dir, "index.tmp")
        aead.newEncryptingStream(FileOutputStream(tmp), "index".toByteArray()).use { it.write(arr.toString().toByteArray(Charsets.UTF_8)) }
        check(tmp.renameTo(indexFile)) { "ইনডেক্স সংরক্ষণ ব্যর্থ" }
    }

    companion object {
        fun sealWith(a: StreamingAead, plain: ByteArray, aad: ByteArray): ByteArray {
            val bo = java.io.ByteArrayOutputStream()
            a.newEncryptingStream(bo, aad).use { it.write(plain) }
            return bo.toByteArray()
        }
        fun unsealWith(a: StreamingAead, blob: ByteArray, aad: ByteArray): ByteArray =
            a.newDecryptingStream(java.io.ByteArrayInputStream(blob), aad).use { it.readBytes() }
        private const val MIN_FREE = 200L * 1024 * 1024
    }
}
