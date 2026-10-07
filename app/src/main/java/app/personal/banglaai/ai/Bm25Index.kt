package app.personal.banglaai.ai

import kotlin.math.ln

data class Hit(val page: Int, val score: Double, val snippet: String)

/**
 * Local keyword (BM25) search over page texts. It is lexical, NOT semantic: it can miss a passage that uses different words.
 * Bengali/Arabic/Urdu/Hindi are handled by Unicode-aware tokenising (letters+marks+digits), lowercasing and removing
 * Arabic diacritics/tatweel; there is no stemming, so inflected forms may not match. Callers must say so in the UI.
 */
class Bm25Index(pages: Map<Int, String>, private val k1: Double = 1.5, private val b: Double = 0.75) {
    private val docs = pages.mapValues { tokens(it.value) }
    private val texts = pages
    private val avgLen = docs.values.map { it.size }.average().let { if (it.isNaN() || it == 0.0) 1.0 else it }
    private val df = HashMap<String, Int>().also { m -> docs.values.forEach { d -> d.toSet().forEach { m.merge(it, 1, Int::plus) } } }
    val pageCount get() = docs.size

    fun search(query: String, topK: Int = 5): List<Hit> {
        val q = tokens(query).distinct(); if (q.isEmpty()) return emptyList()
        val n = docs.size.toDouble()
        return docs.mapNotNull { (page, d) ->
            val tf = d.groupingBy { it }.eachCount(); var s = 0.0
            for (t in q) { val f = tf[t] ?: continue; val idf = ln(1 + (n - (df[t] ?: 0) + 0.5) / ((df[t] ?: 0) + 0.5))
                s += idf * f * (k1 + 1) / (f + k1 * (1 - b + b * d.size / avgLen)) }
            if (s > 0) Hit(page, s, snippet(texts.getValue(page), q)) else null
        }.sortedByDescending { it.score }.take(topK)
    }

    private fun snippet(text: String, q: List<String>): String {
        val low = normalize(text); val i = q.map { low.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: 0
        return text.drop((i - 60).coerceAtLeast(0)).take(240).trim()
    }

    companion object {
        private val marks = Regex("[\\u064B-\\u065F\\u0670\\u0640\\u06D6-\\u06ED]")   // Arabic vowel marks, tatweel, annotation marks
        /** Lowercase, strip Arabic vowel/annotation marks, and unify letter variants that readers treat as the same (alef forms; Urdu/Persian yeh and kaf). Applied to both text and query. */
        fun normalize(s: String) = marks.replace(s.lowercase(), "")
            .replace('\u0671', '\u0627').replace('\u0622', '\u0627').replace('\u0623', '\u0627').replace('\u0625', '\u0627')
            .replace('\u06CC', '\u064A').replace('\u0649', '\u064A').replace('\u06A9', '\u0643')
        fun tokens(s: String): List<String> = Regex("[\\p{L}\\p{M}\\p{N}]+").findAll(normalize(s)).map { it.value }.filter { it.length > 1 || it[0].isDigit() }.toList()
    }
}
