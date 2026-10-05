package app.personal.banglaai.jobs

import org.json.JSONObject
import java.io.File

/**
 * Stores job checkpoints encrypted via the Vault's AEAD. Writes are atomic (tmp + rename), so a crash mid-write
 * leaves the previous checkpoint intact.
 */
class JobStore(private val dir: File, private val seal: (ByteArray, ByteArray) -> ByteArray, private val unseal: (ByteArray, ByteArray) -> ByteArray) {
    init { dir.mkdirs() }

    @Synchronized fun save(j: JobState) {
        val tmp = File(dir, "${j.id}.tmp"); val out = File(dir, "${j.id}.job")
        tmp.writeBytes(seal(j.toJson().toString().toByteArray(Charsets.UTF_8), j.id.toByteArray()))
        check(tmp.renameTo(out)) { "checkpoint সংরক্ষণ ব্যর্থ" }
    }

    @Synchronized fun load(id: String): JobState? {
        val f = File(dir, "$id.job"); if (!f.exists()) return null
        return JobState.fromJson(JSONObject(String(unseal(f.readBytes(), id.toByteArray()), Charsets.UTF_8))).also { it.afterRestart() }
    }

    @Synchronized fun list(): List<JobState> =
        dir.listFiles { x -> x.name.endsWith(".job") }.orEmpty().mapNotNull { load(it.name.removeSuffix(".job")) }

    @Synchronized fun delete(id: String) { File(dir, "$id.job").delete(); File(dir, "$id.tmp").delete() }
}
