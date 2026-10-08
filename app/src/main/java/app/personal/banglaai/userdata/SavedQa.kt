package app.personal.banglaai.userdata

import org.json.JSONArray
import org.json.JSONObject

/** One saved question + answer. [sources] are the human-readable source labels shown with the answer ("ফাইল — পৃষ্ঠা ১২"); [docId]/[page] let a tap reopen the original. */
data class SavedQaItem(val id: String, val kind: String, val question: String, val answer: String, val sources: List<String>, val docId: String, val page: Int, val at: Long)

/**
 * Saved Q&A (the owner presses "সংরক্ষণ"; nothing is saved automatically). The answer is stored exactly as shown, together with its source labels, and is marked
 * with [kind] (document / library / knowledge / code / assistant) so a saved AI answer is never mistaken for a quotation from the document.
 */
class SavedQa(private val store: SealedList) {
    fun all(): List<SavedQaItem> = store.read().mapNotNull { runCatching { fromJson(it) }.getOrNull() }.sortedByDescending { it.at }
    fun forDoc(docId: String) = all().filter { it.docId == docId }
    @Synchronized fun add(kind: String, question: String, answer: String, sources: List<String>, docId: String = "", page: Int = 0, now: Long = System.currentTimeMillis()): SavedQaItem {
        require(question.isNotBlank() && answer.isNotBlank()) { "প্রশ্ন ও উত্তর দুটিই লাগবে" }
        val it = SavedQaItem(java.util.UUID.randomUUID().toString(), kind, question.trim(), answer.trim(), sources.take(20), docId, page, now)
        store.write(store.read() + toJson(it)); return it
    }
    @Synchronized fun delete(id: String) = store.write(store.read().filterNot { it.optString("id") == id })
    @Synchronized fun deleteDoc(docId: String) = store.write(store.read().filterNot { it.optString("d") == docId })
    fun exportJson(): List<JSONObject> = store.read()
    /** Backup restore: items with a new id whose document (if any) is in [validDocs]. Returns how many were added. */
    @Synchronized fun mergeJson(items: List<JSONObject>, validDocs: Set<String>): Int {
        val have = store.read().map { it.optString("id") }.toMutableSet(); val add = ArrayList<JSONObject>()
        for (o in items) runCatching { val x = fromJson(o); if ((x.docId.isEmpty() || x.docId in validDocs) && have.add(x.id)) add += toJson(x) }
        if (add.isNotEmpty()) store.write(store.read() + add); return add.size
    }
    private fun toJson(x: SavedQaItem) = JSONObject().put("id", x.id).put("k", x.kind).put("q", x.question).put("a", x.answer).put("s", JSONArray(x.sources)).put("d", x.docId).put("p", x.page).put("t", x.at)
    private fun fromJson(o: JSONObject): SavedQaItem {
        val s = o.getJSONArray("s")
        return SavedQaItem(o.getString("id").also { require(it.isNotBlank()) }, o.getString("k"), o.getString("q"), o.getString("a"), (0 until s.length()).map { s.getString(it) }, o.optString("d"), o.optInt("p"), o.getLong("t"))
    }
}
