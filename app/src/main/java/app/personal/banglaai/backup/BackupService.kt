package app.personal.banglaai.backup

import app.personal.banglaai.doc.DocWorkflow
import app.personal.banglaai.doc.PageText
import app.personal.banglaai.jobs.JobState
import app.personal.banglaai.jobs.JobStatus
import app.personal.banglaai.storage.Vault
import org.json.JSONArray
import org.json.JSONObject
import app.personal.banglaai.importer.readUpTo
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Local encrypted backup of originals + extracted text + translations + job coverage, written wherever the user chooses
 * (nothing is uploaded). Restore adds documents back into the vault; documents already present are left untouched.
 * Status: crypto container is unit-tested; the full create/restore flow runs only on a device (Keystore) — device-test pending.
 */
class BackupService(private val vault: Vault, private val wf: DocWorkflow) {
    data class Report(val restored: Int, val skipped: Int)

    fun create(out: OutputStream, pass: CharArray) {
        ZipOutputStream(PassphraseBackup.encryptTo(out, pass)).use { z ->
            val docs = vault.list()
            z.putNextEntry(ZipEntry("manifest.json")); z.write(JSONObject().put("version", 1).put("docs", docs.size).toString().toByteArray()); z.closeEntry()
            for (d in docs) {
                val ext = wf.job(d, DocWorkflow.Kind.Extract); val tr = wf.job(d, DocWorkflow.Kind.Translate)
                val meta = JSONObject().put("id", d.id).put("name", d.name).put("added", d.added)
                    .put("extract", ext?.pages?.toList()?.let(::JSONArray) ?: JSONObject.NULL).put("translate", tr?.pages?.toList()?.let(::JSONArray) ?: JSONObject.NULL)
                z.putNextEntry(ZipEntry("d/${d.id}/meta.json")); z.write(meta.toString().toByteArray(Charsets.UTF_8)); z.closeEntry()
                z.putNextEntry(ZipEntry("d/${d.id}/orig")); vault.open(d.id).use { it.copyTo(z) }; z.closeEntry()
                for (p in 1..(ext?.total ?: 0)) {
                    wf.texts.get(d.id, p)?.let { put(z, "d/${d.id}/t/$p.json", it) }
                    wf.translations.get(d.id, p)?.let { put(z, "d/${d.id}/b/$p.json", it) }
                }
            }
        }
    }

    private fun put(z: ZipOutputStream, name: String, p: PageText) {
        z.putNextEntry(ZipEntry(name))
        z.write(JSONObject().put("t", p.text).put("l", p.printedLabel ?: JSONObject.NULL).put("o", p.ocr).toString().toByteArray(Charsets.UTF_8)); z.closeEntry()
    }

    /** Throws [PassphraseBackup.BadBackup] / crypto exception on wrong passphrase or damaged file. */
    fun restore(input: InputStream, pass: CharArray): Report {
        var restored = 0; var skipped = 0
        val metas = HashMap<String, JSONObject>(); val doneIds = HashSet<String>()
        ZipInputStream(PassphraseBackup.decryptFrom(input, pass)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break; val parts = e.name.split("/")
                if (parts.size < 3 || parts[0] != "d" || ".." in parts) continue   // ignore anything unexpected; never trust entry names as paths
                val id = parts[1]
                when {
                    parts[2] == "meta.json" -> metas[id] = JSONObject(String(readLimited(z), Charsets.UTF_8))
                    parts[2] == "orig" -> { val m = metas[id] ?: continue
                        if (vault.restore(id, m.getString("name"), m.getLong("added"), NoCloseStream(z)) != null) { doneIds += id; restored++ } else skipped++ }
                    parts[2] == "t" && id in doneIds -> readPage(z)?.let { wf.texts.put(id, it.copy(page = parts[3].removeSuffix(".json").toInt())) }
                    parts[2] == "b" && id in doneIds -> readPage(z)?.let { wf.translations.put(id, it.copy(page = parts[3].removeSuffix(".json").toInt())) }
                }
            }
        }
        for (id in doneIds) metas[id]?.let { m ->
            for ((key, kind) in listOf("extract" to DocWorkflow.Kind.Extract, "translate" to DocWorkflow.Kind.Translate)) {
                if (m.isNull(key)) continue
                val a = m.getJSONArray(key)
                wf.jobs.save(JobState("$id-${kind.key}", id, kind.key, IntArray(a.length()) { a.getInt(it) }, JobStatus.PAUSED))
            }
        }
        return Report(restored, skipped)
    }

    private fun readLimited(z: InputStream) = z.readUpTo(1L shl 20)
    private fun readPage(z: InputStream): PageText? = runCatching {
        val o = JSONObject(String(readLimited(z), Charsets.UTF_8))
        PageText(0, o.getString("t"), if (o.isNull("l")) null else o.getString("l"), ocr = o.optBoolean("o"))
    }.getOrNull()

    private class NoCloseStream(private val s: InputStream) : InputStream() {
        override fun read() = s.read(); override fun read(b: ByteArray, o: Int, l: Int) = s.read(b, o, l); override fun close() {}
    }
}
