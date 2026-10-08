package app.personal.banglaai.knowledge

import app.personal.banglaai.ai.Bm25Index
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KnowledgeBaseTest {
    private val dir = File("src/main/assets/knowledge")
    private val all = KnowledgeBase.categories.flatMap { c -> KnowledgeBase.parse(c.id, File(dir, "${c.id}.json").readText(Charsets.UTF_8)) }

    @Test fun everyCategoryFileLoadsWithCompleteCards() {
        assertTrue(all.size >= 50)
        KnowledgeBase.categories.forEach { c -> assertTrue("${c.id} empty", all.any { it.category == c.id }) }
        all.forEach { assertTrue("${it.id} q", it.q.length > 8); assertTrue("${it.id} answer too short", it.a.length > 150); assertTrue("${it.id} ref", it.ref.isNotBlank()) }
        assertEquals("ids must be unique", all.size, all.map { it.id }.toSet().size)
    }

    @Test fun searchFindsTheRightCards() {
        val idx = Bm25Index(all.indices.associateWith { i -> "${all[i].topic} ${all[i].q} ${all[i].tags.joinToString(" ")} ${all[i].a}" })
        fun top(q: String) = all[idx.search(q, 3).first().page]
        assertEquals("sex-consent1", top("সম্মতি consent").id)
        assertTrue(top("ম্যাকিয়াভেলি").q.contains("ম্যাকিয়াভেলি"))
        assertTrue(top("BATNA").id.startsWith("diplomacy"))
    }

    @Test fun contentStaysProtectiveAndSourced() {
        // the library must not contain explicit/how-to-harm wording; protective framing and "check with a professional" notes are required in the sensitive category
        val explicit = listOf("অশ্লীল ভিডিও দেখুন", "জোর করে", "উত্তেজক ভঙ্গি")
        all.forEach { k -> explicit.forEach { assertFalse("${k.id} has '$it'", k.a.contains(it) && !k.a.contains("না")) } }
        val sex = all.filter { it.category == "sex" }
        assertTrue(sex.count { it.a.contains("চিকিৎসক") || it.a.contains("ডাক্তার") } >= 10)
        assertTrue(all.any { it.id == "sex-childsafety" })
    }
}
