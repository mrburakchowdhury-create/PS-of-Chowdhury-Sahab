package app.personal.banglaai.knowledge

import android.content.Context
import org.json.JSONObject

/**
 * Offline English <-> Bengali word list (আভিধানিক অর্থ). Built from Wiktionary data (CC BY-SA 4.0; see docs/THIRD_PARTY.md and tools/build_dictionary.py), with a hand-picked
 * first-meaning list ("core", tools/core_en_bn.tsv) for the most common words of the bundled books; core meanings come first and are marked ★.
 * It is a WORD LIST, not a full dictionary: it can be incomplete and a meaning may not fit the sentence — the contextual meaning (পারিবেশিক অর্থ)
 * is a separate, AI-made answer that needs the local model and the sentence the word sits in.
 */
object Dictionary {
    enum class Lang { ENGLISH, BENGALI }
    data class Entry(val asked: String, val found: String, val lang: Lang, val meanings: List<String>, val pos: String?, val curated: Boolean = false)

    class Data(private val en: Map<String, List<String>>, private val bn: Map<String, Pair<String?, List<String>>>, private val core: Set<String> = emptySet()) {
        val size get() = en.size + bn.size
        fun lookup(raw: String): Entry? {
            val term = clean(raw); if (term.isEmpty()) return null
            return if (isBengali(term)) lookupBn(raw, term) else lookupEn(raw, term.lowercase())
        }
        private fun lookupEn(asked: String, t: String): Entry? {
            for (c in englishForms(t)) en[c]?.let { return Entry(asked.trim(), c, Lang.ENGLISH, it, null, c in core) }
            return null
        }
        private fun lookupBn(asked: String, t: String): Entry? {
            for (c in bengaliForms(t)) bn[c]?.let { (pos, g) -> return Entry(asked.trim(), c, Lang.BENGALI, g, pos) }
            return null
        }
        /** English words glossing a Bengali word (single words only), used as a fallback to widen a book search. */
        fun englishFor(bengali: String): List<String> = lookup(bengali)?.takeIf { it.lang == Lang.BENGALI }?.meanings.orEmpty()
            .flatMap { it.split(',', ';') }.map { it.trim().lowercase() }.filter { it.isNotEmpty() && it.all { c -> c in 'a'..'z' || c == '-' } }.distinct().take(6)

        /** Suggestions for the Bengali side of a glossary entry: the English word's Bengali meanings, best first. */
        fun bengaliFor(english: String): List<String> = lookup(english)?.takeIf { it.lang == Lang.ENGLISH }?.meanings.orEmpty()
    }

    fun parse(json: String): Data {
        val o = JSONObject(json); val e = o.getJSONObject("en"); val b = o.getJSONObject("bn")
        val en = HashMap<String, List<String>>(e.length() * 2)
        for (k in e.keys()) { val a = e.getJSONArray(k); en[k] = List(a.length()) { a.getString(it) } }
        val bn = HashMap<String, Pair<String?, List<String>>>(b.length() * 2)
        for (k in b.keys()) { val v = b.getJSONObject(k); val g = v.getJSONArray("g"); bn[k] = v.optString("p").takeIf { it.isNotBlank() && it != "null" } to List(g.length()) { g.getString(it) } }
        val core = o.optJSONArray("core")?.let { a -> HashSet<String>(a.length() * 2).also { h -> for (i in 0 until a.length()) h += a.getString(i) } } ?: emptySet()
        return Data(en, bn, core)
    }

    @Volatile private var cache: Data? = null
    fun get(c: Context): Data = cache ?: synchronized(this) { cache ?: parse(c.assets.open("dict/dictionary.json").bufferedReader(Charsets.UTF_8).use { it.readText() }).also { cache = it } }

    fun isBengali(s: String) = s.count { it in 'ঀ'..'৿' } > s.count { it.isLetter() } / 2

    internal fun clean(s: String) = s.trim().trim('.', ',', ';', ':', '!', '?', '"', '\'', '(', ')', '[', ']', '“', '”', '‘', '’', '।', '-', '—').trim()

    /** The word itself first, then plausible base forms (plural, past, -ing, -ly …). Order matters: the exact word wins. */
    internal fun englishForms(t: String): List<String> {
        val out = LinkedHashSet<String>(); out += t
        if (t.endsWith("'s")) out += t.dropLast(2)
        if (t.endsWith("ies") && t.length > 4) out += t.dropLast(3) + "y"
        if (t.endsWith("es") && t.length > 3) out += t.dropLast(2)
        if (t.endsWith("s") && t.length > 3) out += t.dropLast(1)
        if (t.endsWith("ied") && t.length > 4) out += t.dropLast(3) + "y"
        if (t.endsWith("ed") && t.length > 3) { out += t.dropLast(2); out += t.dropLast(1); if (t.length > 4 && t[t.length - 3] == t[t.length - 4]) out += t.dropLast(3) }
        if (t.endsWith("ing") && t.length > 4) { out += t.dropLast(3); out += t.dropLast(3) + "e"; if (t.length > 5 && t[t.length - 4] == t[t.length - 5]) out += t.dropLast(4) }
        if (t.endsWith("ly") && t.length > 4) { out += t.dropLast(2); if (t.endsWith("ily")) out += t.dropLast(3) + "y" }
        for (suf in listOf("ness", "ment", "ful", "less", "er", "est")) if (t.endsWith(suf) && t.length > suf.length + 2) out += t.dropLast(suf.length)
        return out.toList()
    }

    private val bnSuffixes = listOf("গুলোর", "গুলির", "গুলো", "গুলি", "দের", "েরা", "ের", "কে", "রা", "টির", "টার", "টি", "টা", "তে", "য়", "ে", "র", "কেই", "ই", "ও")
    internal fun bengaliForms(t: String): List<String> {
        val out = LinkedHashSet<String>(); out += t
        for (s in bnSuffixes) if (t.endsWith(s) && t.length > s.length + 1) out += t.dropLast(s.length)
        return out.toList()
    }
}
