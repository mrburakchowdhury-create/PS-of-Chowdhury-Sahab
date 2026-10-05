package app.personal.banglaai.ocr

import kotlin.math.abs
import kotlin.math.roundToInt

data class OcrWord(val text: String, val left: Int, val width: Int, val conf: Float, val line: Int)
data class Doubt(val line: Int, val word: String, val reason: String)
data class CodeResult(val text: String, val doubts: List<Doubt>, val indentUnit: Int)

/**
 * Tesseract returns code lines without leading indentation. This rebuilds indentation and inter-word gaps from word
 * bounding boxes, assuming a monospaced screenshot. It is a heuristic: it NEVER silently "fixes" characters; anything
 * uncertain (low confidence, look-alike characters such as 0/O and l/1/I, spacing inside merged tokens) is returned as
 * a [Doubt] for the user to confirm. Proportional-font screenshots give unreliable indentation and are flagged.
 */
object CodeReconstructor {
    private val lookAlike = Regex("[0Oo1lI|]")

    fun rebuild(words: List<OcrWord>, lowConf: Float = 75f): CodeResult {
        if (words.isEmpty()) return CodeResult("", emptyList(), 0)
        val doubts = ArrayList<Doubt>()
        // monospaced character width = median of width/length over longer words
        val ratios = words.filter { it.text.length >= 3 && it.width > 0 }.map { it.width.toDouble() / it.text.length }.sorted()
        val lines = words.groupBy { it.line }.toSortedMap()
        if (ratios.isEmpty()) return CodeResult(lines.values.joinToString("\n") { l -> l.sortedBy { it.left }.joinToString(" ") { it.text } },
            listOf(Doubt(0, "", "অক্ষরের প্রস্থ বোঝা যায়নি — indentation অনিশ্চিত")), 0)
        val cw = ratios[ratios.size / 2]
        // proportional font check: char widths should be near-constant for monospaced code
        val spread = (ratios[(ratios.size * 9 / 10).coerceAtMost(ratios.size - 1)] - ratios[ratios.size / 10]) / cw
        val base = words.minOf { it.left }
        val cols = lines.mapValues { (_, l) -> ((l.minOf { it.left } - base) / cw).roundToInt() }
        // indent unit = the candidate leaving the smallest rounding residual; on a tie prefer the larger unit
        val unit = listOf(8, 4, 3, 2).minByOrNull { u -> cols.values.sumOf { c -> if (c == 0) 0 else abs(c - u * (c.toDouble() / u).roundToInt()) } }
            .takeIf { cols.values.any { it > 0 } } ?: 0
        if (spread > 0.35) doubts += Doubt(0, "", "অক্ষরের প্রস্থ অসমান — সম্ভবত proportional ফন্ট; indentation নির্ভরযোগ্য নয়")
        val out = StringBuilder()
        for ((ln, ws) in lines) {
            val sorted = ws.sortedBy { it.left }
            val col = cols.getValue(ln).let { if (unit > 0) unit * (it.toDouble() / unit).roundToInt() else it }
            out.append(" ".repeat(col))
            sorted.forEachIndexed { i, w ->
                if (i > 0) {
                    val gap = w.left - (sorted[i - 1].left + sorted[i - 1].width)
                    out.append(" ".repeat((gap / cw).roundToInt().coerceAtLeast(1)))
                }
                out.append(w.text)
                if (w.conf < lowConf) doubts += Doubt(ln, w.text, "কম নিশ্চিততা (${w.conf.roundToInt()}%)")
                if (lookAlike.containsMatchIn(w.text) && w.text.any { it.isLetter() } && w.text.any { it.isDigit() || it in "Ol|I" })
                    doubts += Doubt(ln, w.text, "০/O, 1/l/I জাতীয় সদৃশ অক্ষর থাকতে পারে — নিশ্চিত করুন")
                if (w.text.length >= 3 && w.width / w.text.length.toDouble() < cw * 0.7)
                    doubts += Doubt(ln, w.text, "শব্দের ভেতরের ফাঁকা/অক্ষর সংখ্যা অনিশ্চিত")
            }
            out.append('\n')
        }
        return CodeResult(out.toString().trimEnd('\n'), doubts.distinct(), unit)
    }
}
