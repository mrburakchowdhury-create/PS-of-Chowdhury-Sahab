package app.personal.banglaai.ocr

/**
 * Pictures of code often show a line-number gutter ("12  return x"). OCR copies it into the text, so the text box then has TWO numbering systems: the picture's
 * and the text box's. This finds such a gutter (only when it is clearly there), and offers a stripped copy together with each line's picture number, so both
 * numbers can be shown side by side and an explanation can say "text-box line 5 (line 7 in the picture)". The original OCR text is never overwritten.
 */
object LineNumbers {
    data class Stripped(val code: String, val imageNo: List<Int?>, val firstNo: Int, val offset: Int)

    private val gutter = Regex("^\\s{0,3}(\\d{1,5})(?:[.):|]?[ \\t]|$)(.*)$")   // number, optional . ) : |, ONE space; what follows (incl. indentation) is code

    /** null when no clear gutter: at least 60 % of the non-blank lines carry a number and the numbers mostly count upwards. */
    fun detect(code: String): Stripped? {
        val lines = code.split("\n").map { it.removeSuffix("\r") }
        val nonBlank = lines.count { it.isNotBlank() }; if (nonBlank < 3) return null
        val nums = lines.map { l -> gutter.matchEntire(l)?.groupValues?.get(1)?.toIntOrNull() }
        val withNo = nums.filterNotNull(); if (withNo.size < nonBlank * 0.6) return null
        val ups = withNo.zipWithNext().count { (a, b) -> b == a + 1 }
        if (ups < (withNo.size - 1) * 0.6) return null
        // numbers in a gutter are usually right-aligned ("␣7" / "10"): a 1-digit number leaves one more space before the code than a 2-digit one
        val maxDigits = withNo.maxOf { it.toString().length }
        val rest = lines.mapIndexed { i, l ->
            if (nums[i] == null) l else {
                val r = gutter.matchEntire(l)!!.groupValues[2]; val extra = maxDigits - nums[i]!!.toString().length
                r.drop(minOf(extra, r.takeWhile { it == ' ' }.length))
            }
        }
        // what is still common to all numbered code lines is gutter padding, not code indentation; relative indentation is kept
        val common = rest.filterIndexed { i, l -> nums[i] != null && l.isNotBlank() }.minOfOrNull { l -> l.takeWhile { it == ' ' }.length } ?: 0
        val stripped = rest.mapIndexed { i, l -> if (nums[i] != null) l.drop(minOf(common, l.takeWhile { it == ' ' }.length)) else l }
        val first = withNo.first()
        return Stripped(stripped.joinToString("\n"), nums, first, first - 1)
    }
}
