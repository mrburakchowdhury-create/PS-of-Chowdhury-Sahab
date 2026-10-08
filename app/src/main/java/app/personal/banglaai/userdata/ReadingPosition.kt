package app.personal.banglaai.userdata

import org.json.JSONObject

/** Where the owner stopped reading each document ("page:segment"), kept in the encrypted store (it reveals what he reads) and removed with the document. */
class ReadingPosition(private val store: SealedList) {
    fun get(docId: String): Pair<Int, Int>? = runCatching { store.read().firstOrNull { it.getString("d") == docId }?.let { it.getInt("p") to it.getInt("s") } }.getOrNull()
    @Synchronized fun put(docId: String, page: Int, segment: Int) {
        runCatching { store.write(store.read().filterNot { it.getString("d") == docId } + JSONObject().put("d", docId).put("p", page).put("s", segment)) }
    }
    fun exportJson(): List<JSONObject> = runCatching { store.read() }.getOrDefault(emptyList())
    /** Backup restore: positions for documents in [validDocs] that have none yet (existing wins). Returns how many were added. */
    @Synchronized fun mergeJson(items: List<JSONObject>, validDocs: Set<String>): Int {
        val cur = runCatching { store.read() }.getOrDefault(emptyList()); val have = cur.map { it.getString("d") }.toMutableSet(); val add = ArrayList<JSONObject>()
        for (o in items) runCatching { val d = o.getString("d"); val p = o.getInt("p"); val s = o.getInt("s"); if (d in validDocs && p >= 1 && s >= 0 && have.add(d)) add += JSONObject().put("d", d).put("p", p).put("s", s) }
        if (add.isNotEmpty()) store.write(cur + add); return add.size
    }
    @Synchronized fun delete(docId: String) { runCatching { store.write(store.read().filterNot { it.getString("d") == docId }) } }
}
