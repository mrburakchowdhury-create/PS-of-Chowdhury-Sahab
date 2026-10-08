package app.personal.banglaai.library

import android.content.Context

/** Which of the owner's own files (vault document ids) belong to the library. Ids are random, not sensitive, so plain preferences are enough. */
class LibraryStore(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("library", Context.MODE_PRIVATE)
    fun ids(): Set<String> = p.getStringSet("docs", emptySet()).orEmpty()
    fun add(id: String) { p.edit().putStringSet("docs", ids() + id).apply() }
    fun remove(id: String) { p.edit().putStringSet("docs", ids() - id).apply() }
}
