package app.personal.banglaai.speech

import app.personal.banglaai.speech.LangRuns.L
import org.junit.Assert.*
import org.junit.Test

class LangRunsTest {
    @Test fun mixedBengaliEnglishArabicSplitsByScriptAndKeepsEveryWord() {
        val r = LangRuns.split("এই বইয়ের নাম The Prince এবং আরবি العلم نور শব্দ।")
        assertEquals(listOf(L.BN, L.EN, L.BN, L.AR, L.BN), r.map { it.lang })
        assertEquals("The Prince", r[1].text); assertEquals("العلم نور", r[3].text)
    }
    @Test fun digitsAndPunctuationStayWithTheirRun() {
        val r = LangRuns.split("Chapter 12, page 3. পৃষ্ঠা ১২।")
        assertEquals(listOf(L.EN, L.BN), r.map { it.lang }); assertTrue(r[0].text.contains("12"))
    }
    @Test fun pureTextsAreOneRunAndBlankIsEmpty() {
        assertEquals(1, LangRuns.split("শুধু বাংলা লেখা").size); assertEquals(1, LangRuns.split("Only English here").size); assertTrue(LangRuns.split("  ").isEmpty())
    }
}
