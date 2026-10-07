package app.personal.banglaai.call

import app.personal.banglaai.call.CallReaderSession.Outcome
import app.personal.banglaai.speech.VoiceCommands
import org.junit.Assert.*
import org.junit.Test

class CallReaderSessionTest {
    private val pages = mapOf(1 to "প্রথম পাতার লেখা। দ্বিতীয় বাক্য।", 2 to "", 3 to "তৃতীয় পাতা।")
    private fun session(files: List<String> = listOf("ইতিহাস বই.pdf", "ভূগোল.pdf"), total: Int = 3) = CallReaderSession(
        { files }, { if (it.startsWith("ইতিহাস")) total else 0 }, { _, p -> pages[p] }, { listOf("ভূমিকা" to 1, "শেষ কথা" to 3) })
    private fun say(s: CallReaderSession, t: String) = s.handle(VoiceCommands.parse(t))

    @Test fun opensFileThenReadsPageAndAdvances() {
        val s = session()
        assertTrue(say(s, "ইতিহাস ফাইলটা খোলো এবং এক নম্বর পৃষ্ঠা থেকে পড়ো") is Outcome.StartReading)
        assertEquals("ইতিহাস বই.pdf", s.doc); assertTrue(s.current()!!.text.startsWith("প্রথম পাতার"))
        while (s.advance()) {}   // reaches the end through page 3 (page 2 is empty and skipped)
        assertNull(s.current())
    }
    @Test fun explainNeedsSomethingRead() {
        val s = session()
        assertTrue(say(s, "বুঝিয়ে বলো") is Outcome.Say)
        say(s, "ইতিহাস ফাইলটা খোলো এবং তিন নম্বর পৃষ্ঠা থেকে পড়ো")
        val e = say(s, "বুঝিয়ে বলো") as Outcome.Explain
        assertEquals("তৃতীয় পাতা।", e.segment)
    }
    @Test fun honestMessagesInsteadOfGuessing() {
        val s = session()
        assertTrue((say(s, "দশ নম্বর পৃষ্ঠা থেকে পড়ো") as Outcome.Say).text.contains("আগে বলুন কোন ফাইল"))
        assertTrue((say(s, "ভূগোল ফাইলটা খোলো") as Outcome.Say).text.contains("লেখা এখনো বের করা হয়নি"))
        assertTrue((say(s, "রসায়ন ফাইলটা খোলো") as Outcome.Say).text.contains("কোনো ফাইল পাইনি"))
        say(s, "ইতিহাস ফাইলটা খোলো")
        assertTrue((say(s, "চার নম্বর পৃষ্ঠা থেকে পড়ো") as Outcome.Say).text.contains("পড়ার মতো কোনো লেখা নেই"))
        assertTrue((say(s, "পৃষ্ঠা পড়ো") as Outcome.Say).text.contains("বুঝতে পারিনি"))
        assertTrue((say(s, "আজ কত তারিখ") ) is Outcome.NotACommand)
    }
    @Test fun chaptersAndRate() {
        val s = session(); say(s, "ইতিহাস ফাইলটা খোলো")
        assertTrue(say(s, "দুই নম্বর অধ্যায় থেকে পড়ো") is Outcome.StartReading)
        assertEquals(3, s.pos!!.page)
        assertTrue((say(s, "পাঁচ নম্বর অধ্যায় থেকে পড়ো") as Outcome.Say).text.contains("অধ্যায় 5 নেই"))
        assertEquals(Outcome.Rate(false), say(s, "আস্তে পড়ো"))
    }
}
