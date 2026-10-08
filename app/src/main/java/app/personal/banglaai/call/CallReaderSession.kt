package app.personal.banglaai.call

import app.personal.banglaai.speech.*

/**
 * Document commands spoken during a voice call: "ইতিহাস ফাইলটা খোলো এবং পনেরো নম্বর পৃষ্ঠা থেকে পড়ো", "অধ্যায় ৩ থেকে পড়ো", "বুঝিয়ে বলো", "আবার পড়ো".
 * Pure logic over lambdas (so it is unit-tested without Android). The call screen speaks the returned text / segments and runs the model for [Outcome.Explain].
 * Nothing is guessed: no matching file, no text on that page, or an unclear number all produce a plain Bengali sentence saying so.
 */
class CallReaderSession(
    private val names: () -> List<String>,
    private val totalPages: (String) -> Int,                 // 0 when the file's text has not been extracted yet
    private val pageText: (String, Int) -> String?,          // translation if present, else extracted text
    private val chapters: (String) -> List<Pair<String, Int>>,
    private val rawText: (String, Int) -> String? = { _, _ -> null },   // extracted text whatever its language (to tell "not translated yet" from "nothing there")
    private val aliases: (String) -> String = { "" },   // other names a file is known by (a Bengali title for a bundled English book)
) {
    sealed interface Outcome {
        data object NotACommand : Outcome
        data class Say(val text: String) : Outcome
        data object StartReading : Outcome
        data class Explain(val segment: String, val pageContext: String?) : Outcome
        data class Rate(val faster: Boolean) : Outcome
        /** The page has text but only in another language: the screen translates it (local model) and then repeats the same command. */
        data class NeedsTranslation(val doc: String, val page: Int) : Outcome
    }

    var doc: String? = null; private set
    private var plan: ReadingPlan? = null
    private var total = 0
    private var textOf: ((Int) -> String?)? = null
    var pos: ReadingPosition? = null; private set

    fun current(): Segment? = pos?.let { plan?.segmentAt(it) }
    /** Moves to the next segment; false at the end of the document. */
    fun advance(): Boolean { val p = pos ?: return false; val n = plan?.next(p); pos = n; return n != null }

    fun handle(cmd: VoiceCommand): Outcome = when (cmd) {
        is VoiceCommand.OpenFile -> open(cmd)
        is VoiceCommand.ReadPage -> readPage(cmd.page)
        is VoiceCommand.ReadChapter -> readChapter(cmd.chapter)
        is VoiceCommand.ReadRange -> readRange(cmd.from, cmd.to)
        VoiceCommand.Resume, VoiceCommand.Repeat -> if (current() != null) Outcome.StartReading else Outcome.Say("এখনো কিছু পড়া শুরু হয়নি। বলুন: ফাইলের নাম, তারপর কত নম্বর পৃষ্ঠা থেকে পড়ব।")
        VoiceCommand.Explain -> current()?.let { Outcome.Explain(it.text, pageText(doc!!, it.page)) } ?: Outcome.Say("বোঝানোর মতো কিছু এখনো পড়া হয়নি।")
        VoiceCommand.Slower -> Outcome.Rate(false)
        VoiceCommand.Faster -> Outcome.Rate(true)
        is VoiceCommand.Ambiguous -> Outcome.Say("বুঝতে পারিনি: ${cmd.reason}")
        VoiceCommand.Stop, VoiceCommand.Pause, is VoiceCommand.Unknown -> Outcome.NotACommand
    }

    private fun open(c: VoiceCommand.OpenFile): Outcome {
        when (val r = FileMatcher.find(c.query, names(), aliases)) {
            FileMatcher.Result.None -> return Outcome.Say(if (names().isEmpty()) "এখনো কোনো ফাইল যোগ করা নেই।" else "এই নামে কোনো ফাইল পাইনি।")
            is FileMatcher.Result.Many -> return Outcome.Say("একাধিক ফাইল মিলেছে: ${r.names.take(4).joinToString("; ")}। আরও স্পষ্ট করে নাম বলুন।")
            is FileMatcher.Result.One -> {
                val total = totalPages(r.name)
                if (total <= 0) return Outcome.Say("“${r.name}” পেয়েছি, কিন্তু এর লেখা এখনো বের করা হয়নি। হোমে ফাইল খুলে আগে ‘লেখা বের করুন’ চালান।")
                doc = r.name; pos = null; this.total = total
                textOf = { p -> pageText(r.name, p) }; plan = ReadingPlan(textOf!!, total)
                return c.then?.let { handle(it) } ?: Outcome.Say("“${r.name}” খুলেছি, মোট $total পৃষ্ঠা। কোন পৃষ্ঠা বা অধ্যায় থেকে পড়ব?")
            }
        }
    }

    private fun untranslated(page: Int): Outcome? = doc?.takeIf { page in 1..total && !rawText(it, page).isNullOrBlank() }?.let { Outcome.NeedsTranslation(it, page) }

    private fun needDoc() = Outcome.Say("আগে বলুন কোন ফাইল খুলব — যেমন: ইতিহাস ফাইলটা খোলো।")

    private fun readRange(from: Int, to: Int): Outcome {
        val f = textOf ?: return needDoc()
        if (from > total) return Outcome.Say("ফাইলে মোট $total পৃষ্ঠা — $from নম্বর পৃষ্ঠা নেই।")
        plan = ReadingPlan(f, minOf(to, total))   // the plan stops at the last requested page
        val start = plan!!.startAt(from) ?: return untranslated(from) ?: Outcome.Say("পৃষ্ঠা $from থেকে $to-এর মধ্যে পড়ার মতো লেখা নেই (স্ক্যান হলে আগে OCR, অ-বাংলা হলে আগে অনুবাদ লাগবে)।")
        pos = start; return Outcome.StartReading
    }

    private fun readPage(page: Int): Outcome {
        textOf?.let { f -> if (plan?.pageCount != total) plan = ReadingPlan(f, total) }   // back to the whole document
        val pl = plan ?: return needDoc()
        val start = pl.startAt(page) ?: return untranslated(page) ?: Outcome.Say("পৃষ্ঠা $page থেকে পড়ার মতো কোনো লেখা নেই (স্ক্যান পৃষ্ঠা হলে আগে OCR লাগবে; আর মূল লেখা বাংলা না হলে আগে বাংলা অনুবাদ করতে হবে — বাংলা কণ্ঠ অন্য ভাষা পড়তে পারে না)।")
        pos = start; return Outcome.StartReading
    }

    private fun readChapter(n: Int): Outcome {
        val d = doc ?: return needDoc()
        val ch = chapters(d)
        if (ch.isEmpty()) return Outcome.Say("এই ফাইলে অধ্যায়-তালিকা পাওয়া যায়নি। পৃষ্ঠা নম্বর বলুন।")
        val c = ch.getOrNull(n - 1) ?: return Outcome.Say("অধ্যায় $n নেই — মোট ${ch.size}টি অধ্যায় আছে।")
        return readPage(c.second)
    }
}
