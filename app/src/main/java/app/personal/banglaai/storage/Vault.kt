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

    /** Restores a document from a backup stream under its ORIGINAL id (so derived data still maps). Never overwrites an existing id. */
    @Synchronized fun restore(id: String, name: String, added: Long, input: java.io.InputStream): VaultDoc? {
        if (File(dir, "$id.enc").exists()) return null
        val tmp = File(dir, "$id.tmp"); val out = File(dir, "$id.enc")
        try {
            var total = 0L
            aead.newEncryptingStream(FileOutputStream(tmp), id.toByteArray()).use { dst ->
                val buf = ByteArray(64 * 1024); while (true) { val n = input.read(buf); if (n < 0) break; dst.write(buf, 0, n); total += n }
            }
            check(tmp.renameTo(out)) { "সংরক্ষণ ব্যর্থ" }
            val doc = VaultDoc(id, name, total, added); writeIndex(readIndex() + doc); return doc
        } catch (e: Throwable) { tmp.delete(); out.delete(); throw e }
    }

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
