package app.personal.banglaai.library

import app.personal.banglaai.speech.FileMatcher
import app.personal.banglaai.speech.VoiceCommand
import app.personal.banglaai.speech.VoiceCommands
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class BundledBooksTest {
    private val names = Library.bundled.map { BundledBooks.docName(it) } + listOf("ইতিহাস নোট.pdf", "report 2019.xlsx")
    private val alias = { n: String -> BundledBooks.aliasesFor(n) }
    private fun find(q: String) = FileMatcher.find(q, names, alias)
    private fun say(t: String) = VoiceCommands.withNamedDocument(t, VoiceCommands.parse(t), names, alias)

    @Test fun aBundledBookIsFoundByItsBengaliOrEnglishNameDespiteArticlesAndEndings() {
        for (q in listOf("দ্য প্রিন্স", "দি প্রিন্স", "প্রিন্স", "প্রিন্সের", "the prince", "The Prince বইটা")) assertEquals(q, FileMatcher.Result.One("The Prince.txt"), find(q))
        assertEquals(FileMatcher.Result.One("Arthashastra.txt".let { n -> names.first { it.startsWith("Kautilya") } }), find("অর্থশাস্ত্র"))
        assertEquals(FileMatcher.Result.One("On War.txt"), find("অন ওয়ার"))
        assertEquals(FileMatcher.Result.One("The Art of War.txt"), find("যুদ্ধের কৌশল"))
        assertEquals(FileMatcher.Result.None, find("সম্পূর্ণ অন্য কিছু"))
    }

    @Test fun anAuthorsNameCanBeAmbiguousAndIsThenListedNotGuessed() {
        val r = find("ম্যাকিয়াভেলি")
        assertTrue(r.toString(), r is FileMatcher.Result.Many && r.names.size == 2)
    }

    @Test fun readingASpokenBookNameOpensThatBookThenReads() {
        assertEquals(VoiceCommand.OpenFile("The Prince", VoiceCommand.ReadPage(10)), say("দ্য প্রিন্স থেকে দশ নম্বর পৃষ্ঠা পড়ো"))
        assertEquals(VoiceCommand.OpenFile("The Prince", VoiceCommand.ReadPage(1)), say("দ্য প্রিন্স পড়ো"))
        assertEquals(VoiceCommand.OpenFile("The Prince", VoiceCommand.ReadChapter(3)), say("প্রিন্স বইয়ের ৩ নম্বর অধ্যায় পড়ো"))
    }

    @Test fun sentencesWithoutADocumentNameAreLeftAlone() {
        assertEquals(VoiceCommand.ReadPage(15), say("পনেরো নম্বর পৃষ্ঠা পড়ো"))
        assertEquals(VoiceCommand.ReadPage(20), say("এই PDF-এর ২০ নম্বর পৃষ্ঠা বাংলায় পড়ে শোনাও"))
        assertTrue(say("প্রিন্স কী বলতে চেয়েছেন?") is VoiceCommand.Unknown)   // a question, not a reading request: goes to the assistant
        assertEquals(VoiceCommand.Slower, say("আস্তে পড়ো"))
    }

    @Test fun everyBundledBookPaginatesIntoChapterBoundPagesWithAChapterList() {
        for (b in Library.bundled) {
            val raw = File("src/main/assets/${b.file}").readText()
            val (pages, chapters) = BundledBooks.paginate(b, raw)
            assertTrue("${b.id}: pages=${pages.size}", pages.size > 20)
            assertTrue(pages.indices.all { pages[it].page == it + 1 && pages[it].text.isNotBlank() && !pages[it].printedLabel.isNullOrBlank() })
            assertTrue("${b.id}: a page must stay close to the page size", pages.all { it.text.length <= BundledBooks.PAGE_CHARS + 400 })
            assertTrue(chapters.all { it.page in 1..pages.size })
        }
        val prince = Library.bundled.first { it.id == "prince" }
        val (_, ch) = BundledBooks.paginate(prince, File("src/main/assets/${prince.file}").readText())
        assertTrue("The Prince has 26 chapters: ${ch.size}", ch.size >= 26)
    }
}
