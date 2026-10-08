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

    @Test fun everyBundledBookLoadsAndIsFindable() {
        Library.bundled.forEach { b ->
            val ps = load(b); assertTrue("${b.id} too small", ps.size > 40)
            assertTrue("${b.id} licence boilerplate indexed", ps.none { it.text.contains("START OF THE PROJECT GUTENBERG") || it.text.contains("END OF THE PROJECT GUTENBERG") })
            assertTrue("${b.id} passage size", ps.all { it.text.length <= 4000 })
        }
        val all = Library.bundled.flatMap { load(it) }; val idx = Library.Index(all)
        assertTrue(idx.search("ambassador negotiator secrecy of negotiation", 4).any { it.book.startsWith("On the Manner") })
        assertTrue(idx.search("crowds are credulous leaders prestige", 4).any { it.book.startsWith("The Crowd") })
        assertTrue(idx.search("a king should appoint ministers spies treasury taxation", 4).any { it.book.startsWith("Kautilya") })
        assertTrue(idx.search("never reveal the full extent of your knowledge keep people in suspense", 6).any { it.book.startsWith("The Art of Worldly") })
        assertTrue(idx.search("war is the continuation of policy by other means", 4).any { it.book.startsWith("On War") })
    }

    @Test fun smallInputChunking() {
        val p = Library.chunk("T", "intro text\n\nCHAPTER I. ONE\n\nalpha beta\n\nCHAPTER II.\n\ngamma")
        assertEquals(listOf("শুরুর অংশ", "CHAPTER I. ONE", "CHAPTER II"), p.map { it.label.substringBefore(" · ") })
    }

    @Test fun repeatedChapterNumbersKeepTheirBook() {
        val t = "BOOK I\n\nCHAPTER I. A\n\ntext one\n\nBOOK II\n\nCHAPTER I. B\n\ntext two"
        val labels = Library.chunk("X", t).map { it.label }
        assertTrue(labels.any { it.startsWith("BOOK II · CHAPTER I") })
        assertTrue(labels.any { it.startsWith("BOOK I · CHAPTER I") })
    }

    @Test fun headinglessBookGetsNumberedLabelsNotIntroduction() {
        val labels = Library.chunk("X", "alpha beta.\n\ngamma delta.").map { it.label }
        assertTrue(labels.all { it.startsWith("অনুচ্ছেদ-গুচ্ছ") })
    }

    @Test fun hyphenWithTrailingSpaceIsJoined() {
        assertTrue(Library.chunk("X", "the gov- \nernment fell").first().text.contains("government"))
    }
}
