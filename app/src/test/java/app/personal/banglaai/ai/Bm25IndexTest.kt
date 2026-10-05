package app.personal.banglaai.ai

import org.junit.Assert.*
import org.junit.Test

class Bm25IndexTest {
    private val pages = mapOf(
        1 to "The library opens at nine and closes at five.",
        2 to "প্রতি রবিবার গ্রন্থাগারে শিশুদের জন্য গল্প বলার অনুষ্ঠান হয়।",
        3 to "ٱلْعِلْمُ نُورٌ وَٱلْجَهْلُ ظَلَامٌ",
        4 to "Budget approved: 4,500 dollars for the new reading room.",
    )
    private val idx = Bm25Index(pages)

    @Test fun englishQueryFindsRightPageFirst() = assertEquals(4, idx.search("budget for reading room").first().page)
    @Test fun bengaliQueryWorks() = assertEquals(2, idx.search("গ্রন্থাগারে গল্প").first().page)
    @Test fun arabicMatchesWithOrWithoutDiacritics() = assertEquals(3, idx.search("العلم نور").first().page)
    @Test fun noMatchGivesEmptyNotInvention() = assertTrue(idx.search("quantum chromodynamics").isEmpty())
    @Test fun emptyQueryIsEmpty() = assertTrue(idx.search("  ").isEmpty())
    @Test fun inflectedFormsAreNotMatched_documentedLimit() = assertTrue(idx.search("গ্রন্থাগার").none { it.page == 2 })
}
