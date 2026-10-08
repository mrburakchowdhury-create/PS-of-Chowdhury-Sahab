package app.personal.banglaai.ai

/**
 * The model's context is [LocalLlm.MAX_TOKENS] tokens for system text + question + answer together. We have no tokenizer on the JVM side, so this is a
 * deliberately PESSIMISTIC estimate (Latin ≈ 3.2 chars/token, everything else — Bengali, Arabic, Devanagari — ≈ 1.2 chars/token); real counts are usually lower.
 * Over-long input is shortened visibly (callers show a note), never silently dropped and never sent to fail inside the engine.
 */
object TokenBudget {
    const val INPUT_TOKENS = 2800   // leaves room for the answer inside the 4096-token window

    fun estimate(s: String): Int {
        var latin = 0; var other = 0
        for (c in s) if (c.code < 0x250 || c.isWhitespace()) latin++ else other++
        return (latin / 3.2 + other / 1.2).toInt() + 1
    }

    /** Longest prefix of [s] within [maxTokens] (cut on a line/space border when possible). */
    fun head(s: String, maxTokens: Int): String {
        if (estimate(s) <= maxTokens) return s
        var lo = 0; var hi = s.length
        while (lo < hi) { val mid = (lo + hi + 1) / 2; if (estimate(s.substring(0, mid)) <= maxTokens) lo = mid else hi = mid - 1 }
        val cut = s.substring(0, lo); val b = maxOf(cut.lastIndexOf('\n'), cut.lastIndexOf(' '), cut.lastIndexOf('।'), cut.lastIndexOf('.'))
        return if (b > lo * 0.6) cut.substring(0, b + 1) else cut
    }

    /** Shortens each of [texts] so that together they fit [totalTokens] (equal share, unused share is handed to longer ones). Second = true if anything was cut. */
    fun share(texts: List<String>, totalTokens: Int): Pair<List<String>, Boolean> {
        if (texts.isEmpty()) return texts to false
        val sizes = texts.map { estimate(it) }
        if (sizes.sum() <= totalTokens) return texts to false
        var left = totalTokens; var open = texts.indices.sortedBy { sizes[it] }.toMutableList(); val limit = IntArray(texts.size)
        while (open.isNotEmpty()) { val i = open.removeAt(0); val fair = left / (open.size + 1); limit[i] = minOf(sizes[i], fair); left -= limit[i] }
        return texts.indices.map { head(texts[it], limit[it]) } to true
    }

    /** Last line of defence inside [LocalLlm]: keeps the start and the end of an over-long prompt (the question is usually at the end). */
    fun guard(system: String, prompt: String): String {
        val room = INPUT_TOKENS - estimate(system)
        if (estimate(prompt) <= room || room < 200) return prompt
        val tail = prompt.takeLast(minOf(prompt.length, 600)).let { t -> head(t.reversed(), 150).reversed() }
        return head(prompt, room - estimate(tail) - 40) + "\n[…মাঝের অংশ ছাঁটা হয়েছে…]\n" + tail
    }

    const val NOTE_BN = "লেখা মডেলের সীমার (৪০৯৬ টোকেন) চেয়ে বড় ছিল, তাই কিছু অংশ ছাঁটা হয়েছে — উত্তর অসম্পূর্ণ হতে পারে।"
}
