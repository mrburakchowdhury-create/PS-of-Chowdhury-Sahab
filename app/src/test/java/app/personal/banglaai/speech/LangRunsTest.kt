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

class TargetLangTest {
    private val en = "This is a fairly long English sentence used for detection."
    private val bn = "এটি শনাক্তকরণের জন্য ব্যবহৃত একটি যথেষ্ট লম্বা বাংলা বাক্য।"
    private val ar = "هذه جملة عربية طويلة بما يكفي لاختبار الكشف عن اللغة."
    @org.junit.Test fun alreadyTargetIsDetectedPerLanguage() {
        val t = object { val BN = app.personal.banglaai.doc.TargetLang.BN; val EN = app.personal.banglaai.doc.TargetLang.EN; val AR = app.personal.banglaai.doc.TargetLang.AR }
        org.junit.Assert.assertTrue(t.BN.isAlready(bn)); org.junit.Assert.assertFalse(t.BN.isAlready(en))
        org.junit.Assert.assertTrue(t.EN.isAlready(en)); org.junit.Assert.assertFalse(t.EN.isAlready(ar))
        org.junit.Assert.assertTrue(t.AR.isAlready(ar)); org.junit.Assert.assertFalse(t.AR.isAlready(bn))
        org.junit.Assert.assertFalse(t.EN.isAlready("short"))
    }
    @org.junit.Test fun promptsNameTheChosenLanguage() {
        val p = app.personal.banglaai.ai.Prompts
        org.junit.Assert.assertTrue(p.translateSystem(app.personal.banglaai.doc.TargetLang.AR).contains("আরবি")); org.junit.Assert.assertEquals(p.TRANSLATE_SYSTEM, p.translateSystem(app.personal.banglaai.doc.TargetLang.BN))
        org.junit.Assert.assertTrue(p.translate("x", emptyList(), app.personal.banglaai.doc.TargetLang.EN).contains("English"))
    }
}
