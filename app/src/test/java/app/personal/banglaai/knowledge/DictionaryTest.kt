package app.personal.banglaai.knowledge

import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DictionaryTest {
    private val d = Dictionary.parse(File("src/main/assets/dict/dictionary.json").readText())

    @Test fun commonBookWordsGiveTheStandardBengaliFirst() {
        for ((w, bn) in mapOf("fear" to "ভয়", "power" to "ক্ষমতা", "enemy" to "শত্রু", "virtue" to "সদ্গুণ", "war" to "যুদ্ধ", "peace" to "শান্তি", "fortune" to "ভাগ্য", "cruelty" to "নিষ্ঠুরতা", "army" to "সেনাবাহিনী", "diplomacy" to "কূটনীতি")) {
            val e = d.lookup(w)!!; assertEquals(w, bn, e.meanings.first()); assertTrue("$w must be marked as hand-picked", e.curated)
        }
    }

    @Test fun inflectedAndPunctuatedFormsFindTheBaseWord() {
        assertEquals("enemy", d.lookup("enemies")!!.found); assertEquals("prince", d.lookup("Princes,")!!.found)
        assertEquals("fear", d.lookup("fearing")!!.found)
        assertEquals("fear", d.lookup("“Fear.”")!!.found)
    }

    @Test fun bengaliWordsGiveEnglishGlossesAndEndingsAreIgnored() {
        assertTrue(d.lookup("ভয়")!!.meanings.joinToString().contains("fear"))
        assertEquals("ভয়", d.lookup("ভয়ের")!!.found)
        assertTrue("fear" in d.englishFor("ভয়"))
    }

    @Test fun unknownWordsAreNotInventedAndTheGlossarySuggestionsComeFromTheSameList() {
        assertNull(d.lookup("zzzxqv")); assertNull(d.lookup("")); assertNull(d.lookup("অমুকতমুকখ"))
        assertEquals("ভাষা", d.bengaliFor("language").first { it == "ভাষা" })
        assertTrue(d.size > 15000)
    }
}
