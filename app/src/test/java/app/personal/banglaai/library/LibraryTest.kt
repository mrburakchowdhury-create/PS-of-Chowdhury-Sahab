package app.personal.banglaai.library

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LibraryTest {
    private fun load(b: BundledBook) = Library.chunk(b.title.substringBefore(" ("), File("src/main/assets/${b.file}").readText(Charsets.UTF_8))
    private val prince = load(Library.bundled[0])
    private val war = load(Library.bundled[1])

    @Test fun chunkingKeepsChaptersAndSizes() {
        assertTrue(prince.size > 100); assertTrue(war.size > 100)
        assertTrue(prince.all { it.text.length in 20..4000 })
        assertTrue(prince.any { it.label.startsWith("CHAPTER XVII") })
        assertTrue(war.any { it.label.contains("Laying plans", ignoreCase = true) })
        assertTrue("Gutenberg licence boilerplate must not be indexed", prince.none { it.text.contains("START OF THE PROJECT GUTENBERG") || it.text.contains("END OF THE PROJECT GUTENBERG") })
    }

    @Test fun englishQuestionsFindTheRightBookAndChapter() {
        val idx = Library.Index(prince + war)
        val a = idx.search("is it better to be loved than feared cruelty clemency", 3)
        assertTrue(a.first().book.startsWith("The Prince")); assertTrue(a.any { it.label.startsWith("CHAPTER XVII") })
        val b = idx.search("supreme excellence is to subdue the enemy without fighting", 3)
        assertTrue(b.any { it.book.startsWith("The Art of War") })
        val c = idx.search("use of spies foreknowledge", 3)
        assertTrue(c.any { it.book.startsWith("The Art of War") && it.text.contains("spies", ignoreCase = true) })
    }

    @Test fun smallInputChunking() {
        val p = Library.chunk("T", "intro text\n\nCHAPTER I. ONE\n\nalpha beta\n\nCHAPTER II.\n\ngamma")
        assertEquals(listOf("ভূমিকা", "CHAPTER I. ONE", "CHAPTER II"), p.map { it.label.substringBefore(" · ") })
    }
}
