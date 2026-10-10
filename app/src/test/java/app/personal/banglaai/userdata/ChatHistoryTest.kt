package app.personal.banglaai.userdata

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class ChatHistoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private val xor = { b: ByteArray, _: ByteArray -> ByteArray(b.size) { i -> (b[i].toInt() xor 0x5A).toByte() } }
    private fun h(n: String = "c.enc") = ChatHistory(SealedList(File(tmp.root, n), xor, xor))

    @Test fun savedChatSurvivesRestartAndCanBeReopenedDeletedAndCleared() {
        val a = h(); a.save("1", "advisor", listOf(ChatMsg(true, "কূটনৈতিক পরামর্শ চাই"), ChatMsg(false, "উত্তর", true)), now = 10)
        a.save("2", "chat", listOf(ChatMsg(true, "দ্বিতীয়")), now = 20)
        val b = h()   // new instance = app restarted
        assertEquals(listOf("2", "1"), b.list().map { it.id }); assertEquals("কূটনৈতিক পরামর্শ চাই", b.list().last().title)
        val (mode, msgs) = b.get("1")!!; assertEquals("advisor", mode); assertEquals(2, msgs.size); assertTrue(msgs[1].addressed)
        b.save("1", "advisor", msgs + ChatMsg(true, "আরেকটি"), now = 30); assertEquals("1", b.list().first().id); assertEquals(3, b.get("1")!!.second.size)
        b.delete("1"); assertNull(b.get("1")); b.clear(); assertTrue(b.list().isEmpty())
    }

    @Test fun emptyOrAssistantOnlyChatIsNotSavedAndBackupMergeKeepsExisting() {
        val a = h(); a.save("x", "chat", emptyList()); a.save("y", "chat", listOf(ChatMsg(false, "hi"))); assertTrue(a.list().isEmpty())
        a.save("1", "chat", listOf(ChatMsg(true, "মূল")), now = 1)
        val other = h("d.enc"); other.save("1", "chat", listOf(ChatMsg(true, "অন্য")), now = 2); other.save("3", "chat", listOf(ChatMsg(true, "নতুন")), now = 3)
        assertEquals(1, a.mergeJson(other.exportJson())); assertEquals("মূল", a.get("1")!!.second[0].text); assertEquals(2, a.list().size)
    }
}
