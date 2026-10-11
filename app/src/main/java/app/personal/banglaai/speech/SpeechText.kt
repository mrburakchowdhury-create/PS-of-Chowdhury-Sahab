package app.personal.banglaai.speech

/**
 * Text → what is actually worth speaking. Model answers and documents contain markdown (**bold**, # headings, 1. lists, `code`, links, emoji, table bars) that a
 * text-to-speech voice reads out literally ("তারা চিহ্ন", "এক ডট"). This removes such symbols and turns list items into short pauses; the words stay unchanged.
 */
object SpeechText {
    private val fence = Regex("```.*?```", RegexOption.DOT_MATCHES_ALL)
    private val url = Regex("https?://\\S+|www\\.\\S+")
    private val listMarker = Regex("(?m)^\\s*(?:[-–•▪◦*+]|\\d{1,3}[.)]|[০-৯]{1,3}[.)])\\s+")
    private val heading = Regex("(?m)^\\s{0,3}#{1,6}\\s*")
    private val symbols = Regex("[*_#`~^|<>\\[\\]{}\\\\=]+")
    private val emoji = Regex("[\\p{So}\\p{Cs}\\uFE0F\\u200D✅❌⚠️✔✖▶►◀■□●○★☆→←↑↓]+")
    private val dashes = Regex("\\s*[—–]\\s*")

    /**
     * Acronyms that English speakers say as ONE WORD (not spelled letter by letter, e.g. "OSINT" = "oh-sint"). The phone's Bengali voice has no idea of this
     * and, lacking an installed English voice to hand such words to, reads any Latin word it doesn't recognise by spelling out each letter — so these are
     * rewritten here to a Bengali spelling of how they actually sound. Acronyms people DO spell out in English ("API", "DNS", "PKI"…) are left alone: spelling
     * them out is the correct pronunciation, in English or Bengali. Word boundaries are Latin-letter boundaries, so "OSINT-এর" still matches "OSINT".
     */
    private val wordAcronyms = linkedMapOf(
        "OSINT" to "ওসিন্ট", "JWT" to "জট", "CSRF" to "সি-সার্ফ", "SSRF" to "এস-সার্ফ", "IDOR" to "আই-ডোর",
        "MITRE" to "মাইটার", "SAST" to "স্যাস্ট", "DAST" to "ড্যাস্ট", "IAM" to "আই-অ্যাম", "RADIUS" to "রেডিয়াস",
    )
    private val acronymPattern = Regex(wordAcronyms.keys.joinToString("|") { Regex.escape(it) }.let { "(?<![A-Za-z])(?:$it)(?![A-Za-z])" }, RegexOption.IGNORE_CASE)

    fun clean(raw: String): String {
        var s = acronymPattern.replace(raw) { m -> wordAcronyms[m.value.uppercase()] ?: m.value }
        s = s.replace(fence, " কোড দেখুন পর্দায়। ").replace(url, " ")
        s = s.replace(heading, "").replace(listMarker, "")
        s = s.replace(Regex("\\.{2,}|[…·]+|(?<=\\s)\\.(?=\\s)"), ", ").replace(emoji, " ").replace(symbols, " ").replace(dashes, ", ")
        s = s.replace(Regex("\\(\\s*\\)"), " ").replace(Regex("\\s*\\n\\s*"), "। ").replace(Regex("[।]\\s*[।]+"), "।")
        s = s.replace(Regex("\\s{2,}"), " ").replace(Regex("\\s+([,।.!?;:])"), "$1").replace(Regex("([,;:])\\s*[,;:]+"), "$1")
        return s.trim().trim(',', ';', ':').trim()
    }

    /** True when most letters are Bengali. The phone's Bengali voice would garble English/Arabic/Urdu/Hindi text, so such text is never sent to it. */
    fun isMostlyBengali(text: String): Boolean {
        val letters = text.filter { it.isLetter() }; if (letters.length < 3) return true
        return letters.count { it.code in 0x0980..0x09FF } * 2 >= letters.length
    }
}
