package app.personal.banglaai.library

import android.content.Context
import app.personal.banglaai.ai.Bm25Index

/** A searchable piece of a book: [book] title, [label] where in the book (chapter / page), and the text itself. */
data class Passage(val book: String, val label: String, val text: String)

data class BundledBook(val id: String, val title: String, val author: String, val file: String, val note: String)

/**
 * "লাইব্রেরি": the owner's reference books. Two kinds: (1) public-domain books bundled with the app, and (2) books the owner adds himself (PDF/EPUB/TXT, encrypted in the vault).
 * Questions are answered by local keyword search over the books' text followed by the local model, which may use ONLY the retrieved passages; every answer lists its sources.
 * This is retrieval, not training: the model does not "learn" the books, it re-reads the best-matching passages each time.
 */
object Library {
    val bundled = listOf(
        BundledBook("prince", "The Prince (দ্য প্রিন্স)", "নিকোলো ম্যাকিয়াভেলি", "library/the_prince.txt", "অনুবাদ: W. K. Marriott (১৯০৮), সর্বজনীন-ডোমেইন"),
        BundledBook("artofwar", "The Art of War (যুদ্ধের কৌশল)", "সান জু", "library/art_of_war.txt", "অনুবাদ: Lionel Giles (১৯১০), সর্বজনীন-ডোমেইন"),
        BundledBook("gracian", "The Art of Worldly Wisdom (বিচক্ষণ জীবন-কৌশল)", "বালতাসার গ্রাসিয়ান (১৬৪৭)", "library/gracian.txt", "অনুবাদ: Joseph Jacobs (১৮৯২), সর্বজনীন-ডোমেইন; স্ক্যান-OCR, কিছু বর্ণ-ভুল থাকতে পারে"),
        BundledBook("arthashastra", "Kautilya's Arthashastra (অর্থশাস্ত্র)", "কৌটিল্য (চাণক্য)", "library/arthashastra.txt", "অনুবাদ: R. Shamasastry (১৯১৫), সর্বজনীন-ডোমেইন; স্ক্যান-OCR, কিছু বর্ণ-ভুল থাকতে পারে"),
        BundledBook("callieres", "On the Manner of Negotiating with Princes (কূটনীতির ক্লাসিক)", "ফ্রাঁসোয়া দ্য ক্যালিয়ের (১৭১৬)", "library/callieres.txt", "অনুবাদ: A. F. Whyte (১৯১৯), সর্বজনীন-ডোমেইন"),
        BundledBook("discourses", "Discourses on Livy (লিভির আলোচনা)", "নিকোলো ম্যাকিয়াভেলি", "library/discourses.txt", "অনুবাদ: Ninian Hill Thomson (১৮৮৩), সর্বজনীন-ডোমেইন"),
        BundledBook("crowd", "The Crowd (জনতার মনোবিজ্ঞান)", "গুস্তাভ লে বঁ", "library/the_crowd.txt", "ইংরেজি অনুবাদ (১৮৯৬), সর্বজনীন-ডোমেইন"),
        BundledBook("onwar", "On War (যুদ্ধ ও রাষ্ট্রনীতি)", "কার্ল ফন ক্লাউজেভিৎস", "library/on_war.txt", "অনুবাদ: J. J. Graham (১৮৭৩), সর্বজনীন-ডোমেইন"),
    )

    private val heading = Regex("(?i)^\\s*(chapter|book)\\s+[ivxlc]+\\b.*$")

    /** Splits a plain-text book into passages of about [max] characters on paragraph borders; the label is the latest "CHAPTER …" heading seen. */
    fun chunk(book: String, raw: String, max: Int = 1400): List<Passage> {
        val out = ArrayList<Passage>(); var label = "ভূমিকা"; val cur = StringBuilder(); var part = 0
        fun flush() { if (cur.isNotBlank()) { part++; out += Passage(book, "$label · অংশ $part", cur.toString().trim()); cur.clear() } }
        for (para in raw.replace("\r", "").replace(Regex("(?<=\\p{L})-\\n(?=\\p{Ll})"), "").split(Regex("\n\\s*\n"))) {
            val p = para.trim(); if (p.isEmpty()) continue
            val h = p.lines().first().trim()
            if (heading.matches(h) && h.length < 140) { flush(); part = 0; label = h.trimEnd('.').take(60) }
            val pieces = if (p.length <= max) listOf(p) else splitLong(p, max)   // a very long paragraph is cut on sentence borders
            for (piece in pieces) { if (cur.length + piece.length > max && cur.isNotEmpty()) flush(); cur.append(piece).append("\n\n") }
        }
        flush(); return out
    }

    private fun splitLong(p: String, max: Int): List<String> {
        val out = ArrayList<String>(); val b = StringBuilder()
        for (sent in p.split(Regex("(?<=[.!?;:])\\s+"))) {
            if (b.isNotEmpty() && b.length + sent.length + 1 > max) { out += b.toString(); b.clear() }
            if (sent.length > max) { sent.chunked(max).forEach { out += it } } else { if (b.isNotEmpty()) b.append(' '); b.append(sent) }
        }
        if (b.isNotEmpty()) out += b.toString(); return out
    }

    fun bundledPassages(c: Context, b: BundledBook): List<Passage> =
        chunk(b.title.substringBefore(" ("), c.assets.open(b.file).bufferedReader(Charsets.UTF_8).use { it.readText() })

    /** In-memory keyword index over a list of passages. */
    class Index(val passages: List<Passage>) {
        private val bm = Bm25Index(passages.indices.associateWith { i -> passages[i].text })
        fun search(query: String, k: Int = 6): List<Passage> = bm.search(query, k).map { passages[it.page] }
    }
}
