package app.personal.banglaai.speech

/**
 * "আক্ষরিক পড়া" for source code: every symbol is spoken as a Bengali word, identifiers/keywords are left as written, and the indentation depth is announced.
 * This is deliberately separate from the Bengali line-by-line EXPLANATION (which is an AI answer): this one never interprets or paraphrases the code.
 * The generic [SpeechText.clean] would delete symbols like `(` `;` `_`, which is right for prose but wrong for code, so code is converted to words first.
 */
object CodeSpeech {
    private val two = linkedMapOf(
        "==" to "সমান সমান", "!=" to "সমান নয়", "<=" to "ছোট বা সমান", ">=" to "বড় বা সমান", "&&" to "অ্যান্ড অ্যান্ড", "||" to "পাইপ পাইপ",
        "++" to "প্লাস প্লাস", "--" to "মাইনাস মাইনাস", "+=" to "প্লাস সমান", "-=" to "মাইনাস সমান", "*=" to "গুণ সমান", "/=" to "ভাগ সমান", "->" to "তীর", "=>" to "সমান তীর",
        "::" to "ডবল কোলন", "//" to "ডবল স্ল্যাশ", "**" to "ডবল স্টার", "<<" to "বাঁ-শিফট", ">>" to "ডান-শিফট", ":=" to "কোলন সমান", "!=" to "সমান নয়", "..." to "তিন ডট",
    )
    private val one = mapOf(
        '(' to "বন্ধনী খুলুন", ')' to "বন্ধনী বন্ধ", '[' to "তৃতীয় বন্ধনী খুলুন", ']' to "তৃতীয় বন্ধনী বন্ধ", '{' to "কার্লি বন্ধনী খুলুন", '}' to "কার্লি বন্ধনী বন্ধ",
        ':' to "কোলন", ';' to "সেমিকোলন", ',' to "কমা", '.' to "ডট", '=' to "সমান", '<' to "ছোট", '>' to "বড়", '+' to "প্লাস", '-' to "মাইনাস", '*' to "স্টার", '/' to "স্ল্যাশ",
        '%' to "শতাংশ চিহ্ন", '&' to "অ্যাম্পারস্যান্ড", '|' to "পাইপ", '!' to "বিস্ময়চিহ্ন", '?' to "প্রশ্নচিহ্ন", '#' to "হ্যাশ", '@' to "অ্যাট", '$' to "ডলার", '^' to "ক্যারেট",
        '~' to "টিল্ড", '\\' to "ব্যাকস্ল্যাশ", '_' to "আন্ডারস্কোর", '"' to "ডবল উদ্ধৃতি", '\'' to "একক উদ্ধৃতি", '`' to "ব্যাকটিক",
    )

    /** One line of code as spoken words. Blank lines → "ফাঁকা লাইন". Keeps letters, digits and non-ASCII (identifiers/strings in any script) as they are. */
    fun literalLine(line: String, tabWidth: Int = 4): String {
        if (line.isBlank()) return "ফাঁকা লাইন"
        val lead = line.takeWhile { it == ' ' || it == '\t' }; val depth = lead.sumOf { if (it == '\t') tabWidth else 1 } / tabWidth
        val body = line.trim(); val out = ArrayList<String>()
        if (depth > 0) out += "ইন্ডেন্ট $depth"
        var i = 0
        while (i < body.length) {
            val t3 = if (i + 3 <= body.length) body.substring(i, i + 3) else ""; val t2 = if (i + 2 <= body.length) body.substring(i, i + 2) else ""
            when {
                t3 == "..." -> { out += two.getValue("..."); i += 3 }
                t2 in two -> { out += two.getValue(t2); i += 2 }
                body[i] in one -> { out += one.getValue(body[i]); i++ }
                body[i].isWhitespace() -> i++
                else -> { val s = i; while (i < body.length && !body[i].isWhitespace() && body[i] !in one) i++; out += body.substring(s, i) }
            }
        }
        return out.joinToString(" ")
    }

    /** Spoken text for each line, prefixed with its line number (the number of the user's own text box, not of the original picture). */
    fun literalLines(code: String): List<String> = code.split("\n").mapIndexed { i, l -> "লাইন ${i + 1}: ${literalLine(l.removeSuffix("\r"))}" }
}
