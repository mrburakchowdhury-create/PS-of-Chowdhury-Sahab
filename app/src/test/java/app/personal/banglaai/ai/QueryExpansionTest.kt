package app.personal.banglaai.ai

import org.junit.Assert.*
import org.junit.Test

class QueryExpansionTest {
    @Test fun parsesListsAndBullets() {
        assertEquals(listOf("নামাজ", "salat", "صلاة"), QueryExpansion.parse("নামাজ, salat، صلاة"))
        assertEquals(listOf("zakat", "যাকাত"), QueryExpansion.parse("1. zakat\n- যাকাত\n- zakat"))
    }
    @Test fun dropsSentencesAndCapsCount() {
        assertTrue(QueryExpansion.parse("এটি একটি খুব লম্বা বাক্য যা শব্দ নয়").isEmpty())
        assertEquals(12, QueryExpansion.parse((1..30).joinToString(",") { "শব্দ$it" }).size)
    }
    @Test fun expandedQueryFindsOtherWording() {
        val idx = Bm25Index(mapOf(1 to "The prayer is obligatory five times a day", 2 to "Fasting in Ramadan"))
        assertTrue(idx.search("সালাত", 3).isEmpty())
        assertEquals(1, idx.search(QueryExpansion.expand("সালাত", listOf("prayer")), 3).first().page)
    }
}
