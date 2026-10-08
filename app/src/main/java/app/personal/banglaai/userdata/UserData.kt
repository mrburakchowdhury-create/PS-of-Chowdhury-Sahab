package app.personal.banglaai.userdata

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Encrypted small JSON-array file (atomic write). Used for glossary, bookmarks and notes: all private, never exported unless the user asks. */
class SealedList(private val file: File, private val seal: (ByteArray, ByteArray) -> ByteArray, private val unseal: (ByteArray, ByteArray) -> ByteArray) {
    private val aad = file.name.toByteArray()
    @Synchronized fun read(): List<JSONObject> {
        if (!file.exists()) return emptyList()
        val a = JSONArray(String(unseal(file.readBytes(), aad), Charsets.UTF_8)); return (0 until a.length()).map { a.getJSONObject(it) }
    }
    @Synchronized fun write(items: List<JSONObject>) {
        file.parentFile?.mkdirs(); val tmp = File(file.path + ".tmp")
        tmp.writeBytes(seal(JSONArray(items).toString().toByteArray(Charsets.UTF_8), aad)); check(tmp.renameTo(file))
    }
}

data class GlossaryEntry(val source: String, val bengali: String)

/**
 * User's own terminology list. During translation only the entries whose source term occurs in the current chunk are sent
 * to the model (short prompt, consistent wording across a long book). Matching is case-insensitive for Latin text and
 * diacritic-insensitive for Arabic; it is substring-based, so very short terms can over-match — the user controls the list.
 */
class Glossary(private val store: SealedList) {
    fun all(): List<GlossaryEntry> = store.read().map { GlossaryEntry(it.getString("s"), it.getString("b")) }
    fun add(source: String, bengali: String) {
        require(source.isNotBlank() && bengali.isNotBlank()) { "দুটি ঘরই পূরণ করুন" }
        val rest = all().filterNot { it.source.equals(source.trim(), true) }
        store.write((rest + GlossaryEntry(source.trim(), bengali.trim())).map { JSONObject().put("s", it.source).put("b", it.bengali) })
    }
    /** Backup support: the raw items, and a merge that keeps what is already here (existing entries win) and ignores malformed items. Returns how many were added. */
    fun exportJson(): List<JSONObject> = store.read()
    @Synchronized fun mergeJson(items: List<JSONObject>): Int {
        val have = all().map { it.source.lowercase() }.toMutableSet(); val add = ArrayList<JSONObject>()
        for (o in items) { val s = o.optString("s").trim(); val b = o.optString("b").trim(); if (s.isNotEmpty() && b.isNotEmpty() && have.add(s.lowercase())) add += JSONObject().put("s", s).put("b", b) }
        if (add.isNotEmpty()) store.write(store.read() + add); return add.size
    }
    fun remove(source: String) = store.write(all().filterNot { it.source == source }.map { JSONObject().put("s", it.source).put("b", it.bengali) })
    fun relevantTo(chunk: String, limit: Int = 40): List<GlossaryEntry> {
        val n = app.personal.banglaai.ai.Bm25Index.normalize(chunk)
        return all().filter { n.contains(app.personal.banglaai.ai.Bm25Index.normalize(it.source)) }.sortedByDescending { it.source.length }.take(limit)
    }
}

data class Mark(val id: String, val docId: String, val page: Int, val kind: Kind, val text: String, val at: Long) { enum class Kind { BOOKMARK, NOTE } }

/** Bookmarks and personal notes per document. Notes are the user's own words and are never mixed into the document's text. */
class Marks(private val store: SealedList) {
    fun forDoc(docId: String): List<Mark> = store.read().map {
        Mark(it.getString("id"), it.getString("d"), it.getInt("p"), Mark.Kind.valueOf(it.getString("k")), it.getString("t"), it.getLong("a"))
    }.filter { it.docId == docId }.sortedWith(compareBy({ it.page }, { it.at }))
    fun add(docId: String, page: Int, kind: Mark.Kind, text: String, now: Long = System.currentTimeMillis()): Mark {
        require(page >= 1); require(kind == Mark.Kind.BOOKMARK || text.isNotBlank()) { "নোট খালি রাখা যায় না" }
        val m = Mark(java.util.UUID.randomUUID().toString(), docId, page, kind, text.trim(), now)
        store.write(store.read() + toJson(m)); return m
    }
    fun exportJson(): List<JSONObject> = store.read()
    /** Backup restore: adds marks whose id is new and whose document is in [validDocs]; malformed items are ignored. Returns how many were added. */
    @Synchronized fun mergeJson(items: List<JSONObject>, validDocs: Set<String>): Int {
        val have = store.read().map { it.getString("id") }.toMutableSet(); val add = ArrayList<JSONObject>()
        for (o in items) runCatching {
            val id = o.getString("id"); val d = o.getString("d"); val p = o.getInt("p"); val k = Mark.Kind.valueOf(o.getString("k")); val t = o.getString("t"); val a = o.getLong("a")
            if (d in validDocs && p >= 1 && id.isNotBlank() && have.add(id)) add += JSONObject().put("id", id).put("d", d).put("p", p).put("k", k.name).put("t", t).put("a", a)
        }
        if (add.isNotEmpty()) store.write(store.read() + add); return add.size
    }
    fun delete(id: String) = store.write(store.read().filterNot { it.getString("id") == id })
    fun deleteDoc(docId: String) = store.write(store.read().filterNot { it.getString("d") == docId })
    private fun toJson(m: Mark) = JSONObject().put("id", m.id).put("d", m.docId).put("p", m.page).put("k", m.kind.name).put("t", m.text).put("a", m.at)
}

data class VocabEntry(val term: String, val meaning: String, val docId: String, val page: Int, val at: Long)

/** Words the user chose to keep (with the AI's explanation they accepted). Practice questions are generated only from these, on request. */
class Vocab(private val store: SealedList) {
    fun all(): List<VocabEntry> = store.read().map { VocabEntry(it.getString("t"), it.getString("m"), it.getString("d"), it.getInt("p"), it.getLong("a")) }
    fun add(term: String, meaning: String, docId: String, page: Int, now: Long = System.currentTimeMillis()) {
        require(term.isNotBlank() && meaning.isNotBlank()) { "শব্দ ও অর্থ দুটিই লাগবে" }
        val rest = store.read().filterNot { it.getString("t").equals(term.trim(), true) && it.getString("d") == docId }
        store.write(rest + JSONObject().put("t", term.trim()).put("m", meaning.trim()).put("d", docId).put("p", page).put("a", now))
    }
    fun exportJson(): List<JSONObject> = store.read()
    @Synchronized fun mergeJson(items: List<JSONObject>, validDocs: Set<String>): Int {
        val have = store.read().map { it.getString("t").lowercase() to it.getString("d") }.toMutableSet(); val add = ArrayList<JSONObject>()
        for (o in items) runCatching {
            val t = o.getString("t").trim(); val m = o.getString("m").trim(); val d = o.getString("d"); val p = o.getInt("p"); val a = o.getLong("a")
            if (d in validDocs && t.isNotEmpty() && m.isNotEmpty() && have.add(t.lowercase() to d)) add += JSONObject().put("t", t).put("m", m).put("d", d).put("p", p).put("a", a)
        }
        if (add.isNotEmpty()) store.write(store.read() + add); return add.size
    }
    fun remove(term: String, docId: String) = store.write(store.read().filterNot { it.getString("t") == term && it.getString("d") == docId })
    fun deleteDoc(docId: String) = store.write(store.read().filterNot { it.getString("d") == docId })
}
