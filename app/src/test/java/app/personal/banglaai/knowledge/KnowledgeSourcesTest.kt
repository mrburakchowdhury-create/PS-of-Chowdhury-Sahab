package app.personal.banglaai.knowledge

import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KnowledgeSourcesTest {
    private val items = KnowledgeBase.categories.flatMap { c -> KnowledgeBase.parse(c.id, File("src/main/assets/knowledge/${c.id}.json").readText()) }

    @Test fun everySourceIsSpecificDatedByFetchAndHttps() {
        for (i in items) for (s in i.sources) {
            assertTrue("${i.id}: ${s.url}", s.url.startsWith("https://") && s.url.count { it == '/' } >= 3)
            assertTrue(s.publisher.isNotBlank() && s.title.isNotBlank()); assertEquals("2026-10-08", s.fetched)
        }
    }

    @Test fun healthCardsWithASourceStateTheirLimitsAndTheOthersAreFlagged() {
        val sex = items.filter { it.category == "sex" }
        assertTrue("sources present on most health cards", sex.count { it.sources.isNotEmpty() } >= 20)
        assertTrue(sex.all { it.uncertainty.isNotBlank() })
    }

    @Test fun exportShowsSourcesOrAnHonestWarning() {
        val t = KnowledgeBase.exportText(items)
        assertTrue(t.contains("[উৎস: ")); assertTrue(t.contains("নির্দিষ্ট উৎস যাচাই হয়নি")); assertTrue(t.contains("[অনিশ্চয়তা: "))
    }
}
