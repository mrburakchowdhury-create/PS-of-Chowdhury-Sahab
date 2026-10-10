package app.personal.banglaai.userdata

import org.json.JSONArray
import org.json.JSONObject

data class ChatMsg(val fromUser: Boolean, val text: String, val addressed: Boolean = false)
data class ChatSummary(val id: String, val mode: String, val title: String, val at: Long, val count: Int)

/**
 * Saved assistant conversations (encrypted in the vault like every other owner file). The owner can open an old chat, delete one chat, or delete all;
 * "save history" can also be switched off. Nothing is sent anywhere.
 */
class ChatHistory(private val store: SealedList) {
    @Synchronized fun list(): List<ChatSummary> = runCatching { store.read() }.getOrDefault(emptyList())
        .map { ChatSummary(it.getString("id"), it.optString("mode", "chat"), it.optString("title"), it.optLong("at"), it.optJSONArray("m")?.length() ?: 0) }.sortedByDescending { it.at }

    @Synchronized fun get(id: String): Pair<String, List<ChatMsg>>? = runCatching { store.read() }.getOrDefault(emptyList()).firstOrNull { it.optString("id") == id }?.let { o ->
        val a = o.optJSONArray("m") ?: JSONArray()
        o.optString("mode", "chat") to (0 until a.length()).map { i -> a.getJSONObject(i).let { ChatMsg(it.optBoolean("u"), it.optString("t"), it.optBoolean("a")) } }
    }

    @Synchronized fun save(id: String, mode: String, msgs: List<ChatMsg>, now: Long = System.currentTimeMillis()) {
        val real = msgs.filter { it.text.isNotBlank() }; if (real.none { it.fromUser }) return
        val title = real.first { it.fromUser }.text.replace(Regex("\\s+"), " ").trim().take(48)
        val o = JSONObject().put("id", id).put("mode", mode).put("title", title).put("at", now)
            .put("m", JSONArray(real.map { JSONObject().put("u", it.fromUser).put("t", it.text).put("a", it.addressed) }))
        val rest = runCatching { store.read() }.getOrDefault(emptyList()).filter { it.optString("id") != id }
        store.write((listOf(o) + rest).sortedByDescending { it.optLong("at") }.take(MAX_CHATS))
    }

    @Synchronized fun delete(id: String) = store.write(runCatching { store.read() }.getOrDefault(emptyList()).filter { it.optString("id") != id })
    @Synchronized fun clear() = store.write(emptyList())

    fun exportJson(): List<JSONObject> = runCatching { store.read() }.getOrDefault(emptyList())
    /** Backup restore: adds chats whose id is not here yet; existing chats win. */
    @Synchronized fun mergeJson(items: List<JSONObject>): Int {
        val have = list().map { it.id }.toMutableSet(); val add = items.filter { it.optString("id").isNotBlank() && it.optJSONArray("m") != null && have.add(it.optString("id")) }
        if (add.isNotEmpty()) store.write((runCatching { store.read() }.getOrDefault(emptyList()) + add).sortedByDescending { it.optLong("at") }.take(MAX_CHATS)); return add.size
    }
    companion object { const val MAX_CHATS = 300 }
}
