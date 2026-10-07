package app.personal.banglaai.userdata

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UserDataTest {
    @get:Rule val tmp = TemporaryFolder()
    private val xor = { b: ByteArray, _: ByteArray -> ByteArray(b.size) { i -> (b[i].toInt() xor 0x5A).toByte() } }   // stand-in cipher so we can check the file is not plaintext
    private fun list(n: String) = SealedList(File(tmp.root, n), xor, xor)

    @Test fun glossarySendsOnlyTermsPresentInChunkAndReplacesDuplicates() {
        val g = Glossary(list("g.enc"))
        g.add("committee", "কমিটি"); g.add("Budget", "বাজেট"); g.add("budget", "বাজেট/ব্যয়-পরিকল্পনা"); g.add("ٱلْعِلْمُ", "জ্ঞান")
        assertEquals(3, g.all().size)   // "Budget" and "budget" are one term; later wins
        assertEquals(listOf("কমিটি"), g.relevantTo("The committee met").map { it.bengali })
        assertEquals(listOf("বাজেট/ব্যয়-পরিকল্পনা"), g.relevantTo("A BUDGET was approved").map { it.bengali })
        assertEquals(listOf("জ্ঞান"), g.relevantTo("العلم نور").map { it.bengali })   // diacritics ignored
        assertTrue(g.relevantTo("nothing here").isEmpty())
    }

    @Test fun glossaryRejectsEmptyAndRemoves() {
        val g = Glossary(list("g2.enc")); g.add("a1", "x")
        assertThrows(IllegalArgumentException::class.java) { g.add("  ", "y") }
        g.remove("a1"); assertTrue(g.all().isEmpty())
    }

    @Test fun marksArePerDocOrderedByPageAndStoredEncrypted() {
        val m = Marks(list("m.enc"))
        m.add("d1", 9, Mark.Kind.NOTE, "SECRETNOTE গোপন", 2); m.add("d1", 3, Mark.Kind.BOOKMARK, "", 1); m.add("d2", 1, Mark.Kind.NOTE, "other", 3)
        assertEquals(listOf(3, 9), m.forDoc("d1").map { it.page })
        assertFalse(String(File(tmp.root, "m.enc").readBytes(), Charsets.ISO_8859_1).contains("SECRETNOTE"))
        m.deleteDoc("d1"); assertTrue(m.forDoc("d1").isEmpty()); assertEquals(1, m.forDoc("d2").size)
    }

    @Test fun emptyNoteIsRejectedButBookmarkNeedsNoText() {
        val m = Marks(list("m2.enc"))
        assertThrows(IllegalArgumentException::class.java) { m.add("d", 1, Mark.Kind.NOTE, " ") }
        m.add("d", 1, Mark.Kind.BOOKMARK, ""); assertEquals(1, m.forDoc("d").size)
    }
}

class GlossaryPromptTest {
    @Test fun glossaryTermsAppearInPromptOutsideDocTags() {
        val p = app.personal.banglaai.ai.Prompts.translate("The committee met", listOf("committee" to "কমিটি"))
        assertTrue(p.contains("committee → কমিটি")); assertTrue(p.indexOf("</DOC>") < p.indexOf("committee → কমিটি"))
        assertFalse(app.personal.banglaai.ai.Prompts.translate("x").contains("পরিভাষা"))
    }
}
