package app.personal.banglaai.speech

/** What the user probably meant. The UI always shows the transcript and this reading, and asks for a tap before anything but Pause/Stop. */
sealed interface VoiceCommand {
    data class OpenFile(val query: String, val then: VoiceCommand?) : VoiceCommand
    data class ReadPage(val page: Int) : VoiceCommand
    data class ReadChapter(val chapter: Int) : VoiceCommand
    /** "১০ থেকে ২০ পৃষ্ঠা পড়ো": read only these pages, then stop. */
    data class ReadRange(val from: Int, val to: Int) : VoiceCommand
    data object Explain : VoiceCommand
    data object Resume : VoiceCommand
    data object Pause : VoiceCommand
    data object Stop : VoiceCommand
    data object Slower : VoiceCommand
    data object Faster : VoiceCommand
    data object Repeat : VoiceCommand
    data class Ambiguous(val reason: String) : VoiceCommand
    data class Unknown(val transcript: String) : VoiceCommand
}

/** Bengali number words 1–100. Only exact listed spellings (plus a few common variants) are accepted; anything else stays a word and is NOT guessed. */
object BengaliNumbers {
    private val words: Map<String, Int> = buildMap {
        val l = listOf("এক","দুই","তিন","চার","পাঁচ","ছয়","সাত","আট","নয়","দশ","এগারো","বারো","তেরো","চৌদ্দ","পনেরো","ষোল","সতেরো","আঠারো","উনিশ","বিশ",
            "একুশ","বাইশ","তেইশ","চব্বিশ","পঁচিশ","ছাব্বিশ","সাতাশ","আটাশ","ঊনত্রিশ","ত্রিশ","একত্রিশ","বত্রিশ","তেত্রিশ","চৌত্রিশ","পঁয়ত্রিশ","ছত্রিশ","সাঁইত্রিশ","আটত্রিশ","ঊনচল্লিশ","চল্লিশ",
            "একচল্লিশ","বিয়াল্লিশ","তেতাল্লিশ","চুয়াল্লিশ","পঁয়তাল্লিশ","ছেচল্লিশ","সাতচল্লিশ","আটচল্লিশ","ঊনপঞ্চাশ","পঞ্চাশ","একান্ন","বায়ান্ন","তিপ্পান্ন","চুয়ান্ন","পঞ্চান্ন","ছাপ্পান্ন","সাতান্ন","আটান্ন","ঊনষাট","ষাট",
            "একষট্টি","বাষট্টি","তেষট্টি","চৌষট্টি","পঁয়ষট্টি","ছেষট্টি","সাতষট্টি","আটষট্টি","ঊনসত্তর","সত্তর","একাত্তর","বাহাত্তর","তিয়াত্তর","চুয়াত্তর","পঁচাত্তর","ছিয়াত্তর","সাতাত্তর","আটাত্তর","ঊনআশি","আশি",
            "একাশি","বিরাশি","তিরাশি","চুরাশি","পঁচাশি","ছিয়াশি","সাতাশি","আটাশি","ঊননব্বই","নব্বই","একানব্বই","বিরানব্বই","তিরানব্বই","চুরানব্বই","পঁচানব্বই","ছিয়ানব্বই","সাতানব্বই","আটানব্বই","নিরানব্বই","একশো")
        l.forEachIndexed { i, w -> put(w, i + 1) }
        put("একশ", 100); put("উনত্রিশ", 29); put("উনচল্লিশ", 39); put("উনপঞ্চাশ", 49); put("উনষাট", 59); put("উনসত্তর", 69); put("উনআশি", 79); put("উননব্বই", 89)
        put("পনের", 15); put("এগার", 11); put("বার", 12); put("তের", 13); put("সতের", 17); put("আঠার", 18)
    }
    private val ordinals = mapOf("প্রথম" to 1, "দ্বিতীয়" to 2, "তৃতীয়" to 3, "চতুর্থ" to 4, "পঞ্চম" to 5, "ষষ্ঠ" to 6, "সপ্তম" to 7, "অষ্টম" to 8, "নবম" to 9, "দশম" to 10)
    fun parse(token: String): Int? = words[token] ?: ordinals[token]
}

/**
 * Matches a spoken file name to the imported documents. Never picks silently when several fit.
 * Speech recognition rarely writes a name exactly as stored, so matching is forgiving in the ways that are safe: article words ("দ্য", "the", "বইটা") are ignored,
 * a spoken word may carry a Bengali ending ("প্রিন্সের", "প্রিন্সটা") and [extra] gives other names a file is known by (a Bengali title for an English book).
 */
object FileMatcher {
    sealed interface Result { data object None : Result; data class One(val name: String) : Result; data class Many(val names: List<String>) : Result }
    private val ignorable = setOf("দ্য", "দি", "দা", "the", "বই", "বইটা", "বইটি", "বইয়ের", "বইটার", "ফাইল", "book", "file", "এর", "টা", "টি")
    private fun toks(s: String) = app.personal.banglaai.ai.Bm25Index.tokens(s)
    private fun stem(t: String): String { for (e in listOf("গুলোর", "গুলো", "দের", "ের", "টির", "টার", "টা", "টি", "কে", "র", "য়", "ে")) if (t.endsWith(e) && t.length > e.length + 2) return t.dropLast(e.length); return t }

    fun find(query: String, names: List<String>, extra: (String) -> String = { "" }): Result {
        val q0 = toks(query); val q = q0.filter { it !in ignorable }.ifEmpty { q0 }
        if (q.isEmpty()) return resultOf(names)
        data class Hit(val name: String, val exactAll: Boolean, val matched: Int)
        val hits = names.map { n ->
            val cand = toks(n.substringBeforeLast('.', n)).toSet() + toks(extra(n)).toSet()
            val candStems = cand.map(::stem).toSet()
            var exact = 0; var loose = 0
            for (t in q) {
                if (t in cand || stem(t) in candStems) exact++
                else if (cand.any { c -> (c.startsWith(t) && t.length >= 3) || (t.startsWith(c) && c.length >= 3) || c.contains(t) && t.length >= 4 }) loose++
            }
            Hit(n, exact == q.size, exact + loose)
        }
        val full = hits.filter { it.matched == q.size }
        val exactOnes = full.filter { it.exactAll }
        if (exactOnes.size == 1) return Result.One(exactOnes[0].name)
        if (full.isNotEmpty()) return resultOf((if (exactOnes.isNotEmpty()) exactOnes else full).map { it.name })
        // nothing matched every word: accept a clear best partial match (at least half the words, and strictly better than the rest)
        val best = hits.maxByOrNull { it.matched }?.takeIf { it.matched * 2 >= q.size && it.matched > 0 } ?: return Result.None
        val top = hits.filter { it.matched == best.matched }
        return resultOf(top.map { it.name })
    }
    /**
     * Finds a document that is NAMED inside a longer spoken sentence ("দ্য প্রিন্স থেকে দশ নম্বর পৃষ্ঠা পড়ো"). Only distinctive words count (3+ letters, not articles or
     * reading words), and a sentence with no such word matches nothing — so "পনেরো নম্বর পৃষ্ঠা পড়ো" never opens a file by accident.
     */
    fun findIn(sentence: String, names: List<String>, ignore: Set<String>, extra: (String) -> String = { "" }): Result {
        val words = toks(sentence).filter { it !in ignorable && it !in ignore && !it.all(Char::isDigit) && it.length >= 3 }
        if (words.isEmpty()) return Result.None
        val scored = names.map { n ->
            val cand = toks(n.substringBeforeLast('.', n)).toSet() + toks(extra(n)).toSet(); val stems = cand.map(::stem).toSet()
            n to words.count { t -> t in cand || stem(t) in stems }
        }.filter { it.second > 0 }
        if (scored.isEmpty()) return Result.None
        val top = scored.maxOf { it.second }
        return resultOf(scored.filter { it.second == top }.map { it.first })
    }

    private fun resultOf(l: List<String>) = when (l.size) { 0 -> Result.None; 1 -> Result.One(l[0]); else -> Result.Many(l) }
}

/**
 * Rule-based Bengali command reader. Deliberately small: it understands only phrasings listed in tests. Anything else is
 * Unknown — never guessed. Bengali digits and ASCII digits are accepted anywhere; Bengali number WORDS (পনেরো, বিশ…) are accepted only right next to
 * পৃষ্ঠা/অধ্যায়/নম্বর so a casual "এক" in a sentence is not mistaken for 1.
 */
object VoiceCommands {
    private val digitMap = "০১২৩৪৫৬৭৮৯".withIndex().associate { it.value to it.index.toString()[0] }
    fun normalizeDigits(s: String) = s.map { digitMap[it] ?: it }.joinToString("")
    private val anchors = setOf("নম্বর", "নং", "পৃষ্ঠা", "পাতা", "অধ্যায়", "চ্যাপ্টার", "page", "chapter", "পৃষ্ঠার", "অধ্যায়ের", "চ্যাপ্টারের", "নম্বরের", "থেকে", "পর্যন্ত", "হতে")
    private val filler = setOf("এই", "ওই", "সেই", "ফাইল", "ফাইলটা", "ফাইলটি", "ফাইলটাকে", "খোলো", "খুলো", "খুলুন", "ওপেন", "খুলে", "করো", "করুন", "করে", "এবং", "আর", "তারপর", "তারপরে", "থেকে", "পড়া", "পড়ো", "পড়ে", "পড়", "শুরু", "নম্বর", "নং", "পৃষ্ঠা", "পাতা", "অধ্যায়", "চ্যাপ্টার", "page", "chapter", "open", "দাও", "শোনাও", "আমাকে", "আমার", "একটু", "অ্যাপ", "হও", "ওপেন", "ও")

    /** Words that can sit in a reading command without being part of a file name. */
    private val nameStop = setOf("পড়ো", "পড়", "পড়ে", "পড়ুন", "শোনাও", "শোনান", "দ্য", "দি", "বই", "বইটা", "বইটি", "বইয়ের", "এখান", "সেখান", "এখানে", "এটা", "সেটা", "ওটা", "আগের", "পরের",
        "আবার", "আস্তে", "জোরে", "দ্রুত", "ধীরে", "পুরো", "সম্পূর্ণ", "শেষ", "থেকেই", "পর্যন্ত", "হতে", "to", "from", "read", "the", "book", "ধরে", "নিয়ে", "দেখাও", "বলো", "বল")

    /** Tokens with number words converted to digits where they sit next to an anchor word. */
    internal fun tokens(transcript: String): List<String> {
        val raw = normalizeDigits(transcript.lowercase()).replace(Regex("[,.;:!?।\"'()\\[\\]]"), " ").split(Regex("\\s+")).filter { it.isNotBlank() }
        return raw.mapIndexed { i, t ->
            val n = BengaliNumbers.parse(t)
            if (n != null && ((i > 0 && raw[i - 1] in anchors) || (i + 1 < raw.size && raw[i + 1] in anchors))) n.toString() else t
        }
    }

    fun parse(transcript: String): VoiceCommand {
        if (transcript.isBlank()) return VoiceCommand.Unknown(transcript)
        val toks = tokens(transcript); val t = toks.joinToString(" ")
        val numbers = toks.filter { it.all(Char::isDigit) }
        fun has(vararg k: String) = k.any { t.contains(it) }

        if (has("থামো", "থামুন", "বন্ধ করো", "বন্ধ করুন", "স্টপ", "stop")) return VoiceCommand.Stop
        if (has("পজ", "একটু থামো", "pause")) return VoiceCommand.Pause

        val openVerb = has("খোলো", "খুলো", "খুলুন", "ওপেন", "খুলে", "open")
        val readsFrom = has("পৃষ্ঠা", "পাতা", "page", "অধ্যায়", "চ্যাপ্টার", "chapter")
        if (openVerb && (has("ফাইল") || toks.any { it !in filler && !it.all(Char::isDigit) })) {
            val query = toks.filter { it !in filler && !it.all(Char::isDigit) }.joinToString(" ")
            val rest = if (readsFrom) parseRead(toks, numbers) else null
            return VoiceCommand.OpenFile(query, rest)
        }
        if (has("বুঝিয়ে", "বুঝাও", "বোঝাও", "বুঝাই", "বোঝান", "ব্যাখ্যা", "মানে কী", "মানে কি", "কী বলছে", "কি বলছে", "বুঝিয়েছে", "explain")) return VoiceCommand.Explain
        if (readsFrom) return parseRead(toks, numbers)

        if (has("আগের জায়গা", "চালিয়ে যাও", "resume") || (has("আবার পড়ো") && numbers.isEmpty())) return VoiceCommand.Resume
        if (has("আস্তে")) return VoiceCommand.Slower
        if (has("জোরে পড়", "দ্রুত")) return VoiceCommand.Faster
        if (has("আবার পড়ো", "repeat")) return VoiceCommand.Repeat
        return VoiceCommand.Unknown(transcript)
    }

    private fun parseRead(toks: List<String>, numbers: List<String>): VoiceCommand {
        val t = toks.joinToString(" ")
        val chapter = t.contains("অধ্যায়") || t.contains("চ্যাপ্টার") || t.contains("chapter")
        return when {
            numbers.size == 1 -> numbers[0].toIntOrNull()?.takeIf { it in 1..99999 }?.let { if (chapter) VoiceCommand.ReadChapter(it) else VoiceCommand.ReadPage(it) }
                ?: VoiceCommand.Ambiguous("সংখ্যা বোঝা যায়নি")
            numbers.size == 2 && !chapter && listOf("থেকে", "হতে", "পর্যন্ত", "to").any { t.contains(it) } ->
                numbers.map { it.toIntOrNull() }.let { n -> if (n.any { it == null || it !in 1..99999 }) VoiceCommand.Ambiguous("পৃষ্ঠার সংখ্যা বোঝা যায়নি") else VoiceCommand.ReadRange(minOf(n[0]!!, n[1]!!), maxOf(n[0]!!, n[1]!!)) }
            numbers.size > 1 -> VoiceCommand.Ambiguous("একাধিক সংখ্যা শোনা গেছে: ${numbers.joinToString(", ")}")
            else -> VoiceCommand.Ambiguous("${if (chapter) "অধ্যায়ের" else "পৃষ্ঠার"} নম্বর বোঝা যায়নি (অঙ্কে বা পরিচিত বাংলা সংখ্যা-শব্দে বলুন)")
        }
    }

    /** Reading words and anchors that, together with a document name, mean "open that document and read" even without "খোলো". */
    private val readish = Regex("পড়ো|পড়ুন|পড়|শোনাও|পৃষ্ঠা|পাতা|অধ্যায়|চ্যাপ্টার|খোলো|খুলো|খুলুন|ওপেন|\\bread\\b|\\bpage\\b|\\bchapter\\b|\\bopen\\b")

    /**
     * When no document is open yet and the sentence names one (a bundled book by its Bengali or English name, or any file), turns a bare read request into
     * "open that document, then read". Other sentences are returned unchanged, so ordinary questions still go to the assistant.
     */
    fun withNamedDocument(transcript: String, parsed: VoiceCommand, names: List<String>, extra: (String) -> String = { "" }): VoiceCommand {
        if (parsed is VoiceCommand.OpenFile || parsed is VoiceCommand.Stop || parsed is VoiceCommand.Pause || parsed is VoiceCommand.Slower || parsed is VoiceCommand.Faster) return parsed
        if (!readish.containsMatchIn(transcript.lowercase())) return parsed
        val ignore = filler + nameStop + anchors
        return when (val r = FileMatcher.findIn(transcript, names, ignore, extra)) {
            FileMatcher.Result.None -> parsed
            is FileMatcher.Result.One -> VoiceCommand.OpenFile(r.name.substringBeforeLast('.'), then(parsed))
            is FileMatcher.Result.Many -> VoiceCommand.OpenFile(tokens(transcript).filter { it !in ignore && !it.all(Char::isDigit) && it.length >= 3 }.joinToString(" "), then(parsed))
        }
    }
    private fun then(p: VoiceCommand): VoiceCommand = when (p) { is VoiceCommand.ReadPage, is VoiceCommand.ReadChapter, is VoiceCommand.ReadRange -> p; else -> VoiceCommand.ReadPage(1) }

    /** Only these may run without a confirming tap (they reduce activity). */
    fun safeWithoutConfirm(c: VoiceCommand) = c is VoiceCommand.Stop || c is VoiceCommand.Pause
}

/** Plain-Bengali description of what a command will do, shown before the user confirms. */
fun describe(c: VoiceCommand): String = when (c) {
    is VoiceCommand.OpenFile -> "ফাইল খোলা" + (if (c.query.isNotBlank()) " (নাম: ${c.query})" else "") + (c.then?.let { " এবং তারপর: ${describe(it)}" } ?: "")
    is VoiceCommand.ReadPage -> "পৃষ্ঠা ${c.page} থেকে পড়া"
    is VoiceCommand.ReadChapter -> "অধ্যায় ${c.chapter} থেকে পড়া"
    is VoiceCommand.ReadRange -> "পৃষ্ঠা ${c.from} থেকে ${c.to} পর্যন্ত পড়া"
    VoiceCommand.Explain -> "এখন পড়া/বাছা অংশ সহজ বাংলায় বুঝিয়ে বলা (AI, মডেল লাগবে)"
    VoiceCommand.Resume -> "আগের জায়গা থেকে পড়া"; VoiceCommand.Pause -> "থামানো"; VoiceCommand.Stop -> "বন্ধ করা"
    VoiceCommand.Slower -> "আস্তে পড়া"; VoiceCommand.Faster -> "দ্রুত পড়া"; VoiceCommand.Repeat -> "আবার পড়া"
    is VoiceCommand.Ambiguous -> "অস্পষ্ট: ${c.reason}"; is VoiceCommand.Unknown -> "বুঝতে পারিনি"
}
