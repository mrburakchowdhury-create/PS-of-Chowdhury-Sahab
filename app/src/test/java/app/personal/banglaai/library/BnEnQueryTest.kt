package app.personal.banglaai.library

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class BnEnQueryTest {
    @Test fun bengaliConceptsBecomeEnglishSearchWords() {
        val t = BnEnQuery.englishTerms("ক্ষমতা সম্পর্কে ম্যাকিয়াভেলি কী বলেছেন")
        assertTrue(t.toString(), "power" in t && "machiavelli" in t)
        assertTrue("fear" in BnEnQuery.englishTerms("ভয়") && "love" in BnEnQuery.englishTerms("ভালোবাসা"))
        assertTrue("fear" in BnEnQuery.englishTerms("ভয়ের কারণ"))          // ending "-র" is ignored
        assertTrue("spy" in BnEnQuery.englishTerms("গুপ্তচরদের ব্যবহার"))
        assertEquals(listOf("treachery"), BnEnQuery.englishTerms("treachery"))   // Latin words are kept
        assertTrue(BnEnQuery.englishTerms("অমুকতমুক").isEmpty())
        assertEquals(listOf("hatred", "hate", "contempt"), BnEnQuery.englishTerms("ঘৃণা", { listOf("x") }))   // concept list wins; the dictionary fallback is used only for words the list does not know
        assertEquals(listOf("zzz"), BnEnQuery.englishTerms("অজানা", { listOf("zzz") }))
    }

    @Test fun aBengaliQuestionFindsTheRightPassageOfTheEnglishBookWithoutAnyModel() {
        val book = Library.bundled.first { it.id == "prince" }
        val passages = Library.chunk("The Prince", File("src/main/assets/${book.file}").readText())
        val index = Library.Index(passages)
        val q = "ভালোবাসা পাওয়ার চেয়ে ভয় পাওয়া কি ভালো?"
        val query = app.personal.banglaai.ai.QueryExpansion.expand(q, BnEnQuery.englishTerms(q))
        val hits = index.search(query, 6)
        assertTrue(hits.joinToString { it.label }, hits.any { it.text.contains("better to be feared than loved", true) || it.text.contains("feared than loved", true) })
    }

    @Test fun anArmyQuestionFindsArmyPassagesInTheArtOfWar() {
        val book = Library.bundled.first { it.id == "artofwar" }
        val index = Library.Index(Library.chunk("The Art of War", File("src/main/assets/${book.file}").readText()))
        val q = "গুপ্তচর কেন গুরুত্বপূর্ণ"
        val hits = index.search(app.personal.banglaai.ai.QueryExpansion.expand(q, BnEnQuery.englishTerms(q)), 6)
        assertTrue(hits.any { it.text.contains("spies", true) })
    }
}
