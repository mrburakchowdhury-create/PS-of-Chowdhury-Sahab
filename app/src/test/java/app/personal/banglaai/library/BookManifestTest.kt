package app.personal.banglaai.library

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BookManifestTest {
    private fun file(b: BundledBook) = File("src/main/assets/${b.file}")

    @Test fun everyBundledBookHasExactlyOneRecordAndNoRecordIsOrphaned() {
        assertEquals(Library.bundled.map { it.id }.sorted(), BookManifest.all.map { it.id }.sorted())
        assertEquals(8, BookManifest.all.size)
    }

    @Test fun shippedBytesMatchThePinnedHashAndStartAndEndWhereTheyShould() {
        for (b in Library.bundled) {
            val r = BookManifest.of(b.id); val bytes = file(b).readBytes()
            assertEquals("${b.id}: file changed without updating BookManifest", r.sha256, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
            val text = bytes.toString(Charsets.UTF_8)
            assertTrue("${b.id}: start marker missing", text.contains(r.startMarker))
            assertTrue("${b.id}: end marker missing (file truncated?)", text.replace(Regex("\\s+"), " ").trimEnd().let { it.contains(r.endMarker) && it.length - it.lastIndexOf(r.endMarker) < 400 })
            assertTrue("${b.id}: expected at least ${r.minHeadings} headings", Regex(r.headingRegex).findAll(text).count() >= r.minHeadings)
            assertTrue("${b.id}: licence basis must be stated", r.licenseBasis.isNotBlank()); assertTrue(r.limitation.isNotBlank())
        }
    }

    @Test fun nothingIsClaimedAsCheckedAgainstTheOriginalUntilSomeoneReallyDoesIt() {
        assertTrue(BookManifest.all.none { it.checkedAgainstOriginal })
    }

    @Test fun chunkingKeepsEveryCharacterOfEveryBookReachableAndLabelsPassages() {
        for (b in Library.bundled) {
            val raw = file(b).readText(); val ps = Library.chunk(b.title.substringBefore(" ("), raw)
            assertTrue("${b.id}: no passages", ps.size > 20)
            val inChunks = ps.sumOf { p -> p.text.count { !it.isWhitespace() } }; val inRaw = raw.count { !it.isWhitespace() }
            assertTrue("${b.id}: lost ${inRaw - inChunks} characters in chunking", inRaw - inChunks < inRaw / 50)   // only hyphen line-breaks are removed
            assertTrue(ps.all { it.label.isNotBlank() })
        }
    }
}
