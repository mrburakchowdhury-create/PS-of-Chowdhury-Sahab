package app.personal.banglaai.speech

/**
 * Splits text into runs by script so each run can be spoken by a voice of its own language. A Bengali voice garbles English words (they came out unclear or
 * skipped), so Latin runs go to an English voice and Arabic-script runs to an Arabic voice. Digits and punctuation stay with the run they follow.
 */
object LangRuns {
    enum class L(val tag: String) { BN("bn"), EN("en"), AR("ar") }
    data class Run(val text: String, val lang: L)

    private fun langOf(c: Char): L? = when {
        c.code in 0x0980..0x09FF -> L.BN
        c.code in 0x0600..0x06FF || c.code in 0x0750..0x077F || c.code in 0xFB50..0xFDFF || c.code in 0xFE70..0xFEFF -> L.AR
        c in 'A'..'Z' || c in 'a'..'z' || c.code in 0x00C0..0x024F -> L.EN
        else -> null
    }

    fun split(text: String, minRun: Int = 1): List<Run> {
        if (text.isBlank()) return emptyList()
        val out = ArrayList<Run>(); var cur: L? = null; val sb = StringBuilder()
        fun flush() { if (sb.isNotBlank()) out += Run(sb.toString().trim(), cur ?: L.BN); sb.setLength(0) }
        for (c in text) {
            val l = langOf(c)
            if (l != null && cur != null && l != cur) flush()
            if (l != null) cur = l
            sb.append(c)
        }
        flush()
        // a lone neutral-only chunk (e.g. "12") joined into the previous run; very short Latin runs inside Bengali (a single letter) stay as they are
        val merged = ArrayList<Run>()
        for (r in out) if (merged.isNotEmpty() && merged.last().lang == r.lang) merged[merged.lastIndex] = Run(merged.last().text + " " + r.text, r.lang) else merged += r
        return merged.filter { it.text.length >= minRun }
    }
}
