package app.personal.banglaai.ocr

import org.junit.Assert.*
import org.junit.Test

/** Fixture = REAL Tesseract 5.3.4 word boxes (tessdata_fast eng) for a rendered monospaced Python snippet (see tools/ocr_eval.py). */
class CodeReconstructorTest {
    private fun w(line: Int, left: Int, width: Int, conf: Float, t: String) = OcrWord(t, left, width, conf, line)
    private val real = listOf(
        w(1, 32, 57, 93f, "def"), w(1, 114, 256, 92f, "total(items):"),
        w(2, 115, 97, 83f, "s=0"),
        w(3, 115, 57, 95f, "for"), w(3, 197, 15, 93f, "i"), w(3, 238, 34, 93f, "in"), w(3, 302, 354, 90f, "range(len(items)"), w(3, 620, 38, 93f, "):"),
        w(4, 197, 14, 93f, "s"), w(4, 236, 38, 91f, "+="), w(4, 299, 153, 90f, "items[i]"), w(4, 483, 15, 90f, "*"), w(4, 523, 57, 91f, "0.5"),
        w(4, 624, 119, 61f, "#0vs"), w(4, 769, 31, 96f, "0,"), w(4, 871, 36, 47f, "Lvs"), w(4, 935, 14, 82f, "1"),
        w(5, 118, 113, 93f, "return"), w(5, 258, 14, 92f, "s"), w(5, 299, 36, 93f, "if"), w(5, 361, 14, 93f, "s"),
        w(5, 407, 31, 93f, "!="), w(5, 462, 16, 71f, "0"), w(5, 503, 78, 96f, "else"), w(5, 605, 78, 96f, "None"),
    )

    @Test fun recoversIndentationLevelsFromRealTesseractBoxes() {
        val r = CodeReconstructor.rebuild(real)
        val lines = r.text.lines()
        assertEquals(5, lines.size)
        assertEquals(0, lines[0].takeWhile { it == ' ' }.length)   // def
        assertEquals(4, lines[1].takeWhile { it == ' ' }.length)   // s = 0   (4 spaces)
        assertEquals(4, lines[2].takeWhile { it == ' ' }.length)   // for
        assertEquals(8, lines[3].takeWhile { it == ' ' }.length)   // s += ... (8 spaces)
        assertEquals(4, lines[4].takeWhile { it == ' ' }.length)   // return
        assertEquals(4, r.indentUnit)
    }

    @Test fun lookAlikeAndLowConfidenceWordsAreFlaggedNotFixed() {
        val r = CodeReconstructor.rebuild(real)
        assertTrue("Lvs must be flagged", r.doubts.any { it.word == "Lvs" })
        assertTrue("#0vs must be flagged", r.doubts.any { it.word == "#0vs" })
        assertTrue(r.text.contains("#0vs"))          // original OCR text kept verbatim, not "corrected"
        assertTrue(r.doubts.any { it.word == "s=0" }) // merged token: inner spacing uncertain
    }

    @Test fun emptyInputIsEmpty() = assertEquals("", CodeReconstructor.rebuild(emptyList()).text)
}
