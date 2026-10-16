package app.personal.banglaai.doc

import app.personal.banglaai.export.ExportPage

/**
 * Heuristic post-translation checks beyond "which pages are done" (JobState.coverageReport already covers missing/failed
 * pages): a translation suspiciously shorter than its original, two pages whose translation text is identical (a
 * duplication/chunking-bug symptom), and a glossary term used inconsistently across pages. These never block anything —
 * only point the owner at specific pages worth a look; they do not judge translation quality/meaning, only structure.
 */
object TranslationQualityCheck {
    data class Issue(val page: Int?, val message: String)

    fun run(pages: List<ExportPage>, glossary: List<Pair<String, String>> = emptyList()): List<Issue> {
        val issues = mutableListOf<Issue>()
        var prevTranslation: String? = null
        var prevPage: Int? = null
        for (p in pages) {
            val orig = p.original?.trim().orEmpty()
            val tr = p.translation?.trim()
            if (!tr.isNullOrBlank() && orig.length > 120 && tr.length < orig.length / 5)
                issues += Issue(p.page, "পৃষ্ঠা ${p.page}: অনুবাদ মূলের তুলনায় অস্বাভাবিক ছোট (${tr.length} বর্ণ বনাম মূল ${orig.length} বর্ণ) — অনুবাদ অসম্পূর্ণ হতে পারে")
            if (!tr.isNullOrBlank() && tr == prevTranslation && orig.isNotBlank())
                issues += Issue(p.page, "পৃষ্ঠা ${prevPage} ও ${p.page}-এর অনুবাদ হুবহু একই — একটির বদলে অন্যটি কপি হয়ে থাকতে পারে")
            prevTranslation = tr; prevPage = p.page
        }
        for ((src, bn) in glossary) {
            if (src.isBlank() || bn.isBlank()) continue
            val withSrc = pages.filter { it.original?.contains(src, ignoreCase = true) == true }
            if (withSrc.size < 2) continue
            val withBn = withSrc.count { it.translation?.contains(bn) == true }
            if (withBn in 1 until withSrc.size)
                issues += Issue(null, "পরিভাষা \"$src\" → \"$bn\" সব পৃষ্ঠায় সমানভাবে ব্যবহার হয়নি (${withBn}/${withSrc.size} পৃষ্ঠায় পাওয়া গেছে) — একই শব্দের অনুবাদ পৃষ্ঠাভেদে আলাদা হতে পারে")
        }
        return issues
    }

    fun summarize(issues: List<Issue>): String =
        if (issues.isEmpty()) "স্বয়ংক্রিয় যাচাইয়ে কোনো সমস্যা পাওয়া যায়নি (এটি ভাষার মান/অর্থ যাচাই করে না — শুধু স্পষ্ট গঠনগত সমস্যা দেখে)"
        else "স্বয়ংক্রিয় যাচাইয়ে ${issues.size}টি জায়গায় নজর দেওয়ার মতো বিষয় পাওয়া গেছে:\n" + issues.joinToString("\n") { "• ${it.message}" }
}
