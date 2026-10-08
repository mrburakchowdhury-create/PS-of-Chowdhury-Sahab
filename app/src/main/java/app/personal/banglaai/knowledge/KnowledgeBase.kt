package app.personal.banglaai.knowledge

import android.content.Context
import app.personal.banglaai.ai.Bm25Index
import org.json.JSONArray

/** One question-and-answer card of the built-in "জ্ঞানভাণ্ডার" (read-only reference written for the owner's own research and book writing). */
data class KItem(val id: String, val category: String, val topic: String, val q: String, val a: String, val tags: List<String>, val ref: String,
    val sources: List<KSource> = emptyList(), val uncertainty: String = "")

/** One specific, opened-and-checked source for a card: where, what page, the page's own date (if it shows one) and when WE opened it. */
data class KSource(val url: String, val publisher: String, val title: String, val pageDate: String?, val fetched: String) {
    fun line() = "$publisher — $title" + (pageDate?.let { " (পৃষ্ঠার তারিখ: $it)" } ?: " (পৃষ্ঠায় তারিখ দেখানো নেই)") + " · দেখা হয়েছে $fetched · $url"
}

data class KCategory(val id: String, val label: String, val note: String)

/**
 * Bundled, offline question/answer library (JSON files in assets/knowledge). Search is the same local keyword search as the document Q&A (no network, no AI needed);
 * the AI is only used when the owner presses "AI-কে জিজ্ঞাসা", and then it is given the best-matching cards as reference text.
 */
object KnowledgeBase {
    val categories = listOf(
        KCategory("sex", "যৌনশিক্ষা ও সম্পর্ক", "প্রাপ্তবয়স্ক ও অভিভাবকদের জন্য তথ্যমূলক ও সুরক্ষামূলক শিক্ষা (শিশু-কিশোর বিষয়ক কার্ডগুলো অভিভাবকের জন্য)। এটি চিকিৎসক, আইনজীবী বা ধর্মীয় আলেমের পরামর্শের বিকল্প নয়; সিদ্ধান্তের আগে যাচাই করুন।"),
        KCategory("politics", "রাজনীতি", "ক্ষমতা, রাষ্ট্র ও গণমাধ্যম বোঝার মৌলিক ধারণা। ধারণাগুলো যতটা সম্ভব নিরপেক্ষভাবে লেখার চেষ্টা করা হয়েছে, নিরপেক্ষতার নিশ্চয়তা নেই; উৎস মিলিয়ে নিন।"),
        KCategory("diplomacy", "কূটনীতি ও আলোচনা", "দর-কষাকষি, সংলাপ ও সংকট-মীমাংসার নীতি।"),
        KCategory("strategy", "কলাকৌশল ও মানব-আচরণ", "প্রভাব, কৌশল ও আচরণের ধারণা — ব্যবহার এবং প্রতারণা চেনার উপায় দুটোই।"),
        KCategory("writing", "লেখকের জন্য", "সংবেদনশীল বিষয়ে দায়িত্বশীল লেখা ও তথ্য-যাচাইয়ের নির্দেশিকা।"),
    )

    private var cache: List<KItem>? = null
    private var index: Bm25Index? = null

    @Synchronized fun all(c: Context): List<KItem> = cache ?: load(c).also { cache = it; index = Bm25Index(it.indices.associateWith { i -> text(it[i]) }) }

    fun parse(category: String, json: String): List<KItem> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            KItem("$category-${o.getString("id")}", category, o.getString("topic"), o.getString("q"), o.getString("a"),
                o.optJSONArray("tags")?.let { t -> (0 until t.length()).map(t::getString) }.orEmpty(), o.optString("ref"),
                o.optJSONArray("sources")?.let { a -> (0 until a.length()).map { j -> a.getJSONObject(j).let { x -> KSource(x.getString("url"), x.getString("publisher"), x.getString("title"), x.optString("pageDate").takeIf { it.isNotBlank() && it != "null" }, x.getString("fetched")) } } }.orEmpty(),
                o.optString("uncertainty"))
        }
    }

    private fun load(c: Context): List<KItem> = categories.flatMap { cat ->
        runCatching { parse(cat.id, c.assets.open("knowledge/${cat.id}.json").bufferedReader(Charsets.UTF_8).use { it.readText() }) }.getOrDefault(emptyList())
    }

    private fun text(i: KItem) = "${i.topic} ${i.q} ${i.tags.joinToString(" ")} ${i.a}"

    /** Best matches for [query] (keyword search), optionally inside one category. Empty query returns the category in file order. */
    fun search(c: Context, query: String, category: String?, limit: Int = 30): List<KItem> {
        val items = all(c).let { l -> if (category == null) l else l.filter { it.category == category } }
        if (query.isBlank()) return items.take(limit)
        val idx = index ?: return emptyList(); val all = all(c)
        return idx.search(query, limit * 3).mapNotNull { all.getOrNull(it.page) }.filter { category == null || it.category == category }.take(limit)
    }

    /** Plain-text export of cards (for saving into the owner's own notes/book draft). */
    fun exportText(items: List<KItem>): String = items.joinToString("\n\n") { "প্রশ্ন: ${it.q}\n${it.a}" + (if (it.sources.isNotEmpty()) it.sources.joinToString("") { s -> "\n[উৎস: ${s.line()}]" } else "\n[নির্দিষ্ট উৎস যাচাই হয়নি — ${it.ref}]") + (if (it.uncertainty.isNotBlank()) "\n[অনিশ্চয়তা: ${it.uncertainty}]" else "") }
}
