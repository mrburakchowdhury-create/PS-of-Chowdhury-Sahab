package app.personal.banglaai.userdata

import org.json.JSONObject

/** Where the owner stopped reading each document ("page:segment"), kept in the encrypted store (it reveals what he reads) and removed with the document. */
class ReadingPosition(private val store: SealedList) {
    fun get(docId: String): Pair<Int, Int>? = runCatching { store.read().firstOrNull { it.getString("d") == docId }?.let { it.getInt("p") to it.getInt("s") } }.getOrNull()
    @Synchronized fun put(docId: String, page: Int, segment: Int) {
        runCatching { store.write(store.read().filterNot { it.getString("d") == docId } + JSONObject().put("d", docId).put("p", page).put("s", segment)) }
    }
    @Synchronized fun delete(docId: String) { runCatching { store.write(store.read().filterNot { it.getString("d") == docId }) } }
}
