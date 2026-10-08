package app.personal.banglaai.speech

import org.junit.Assert.*
import org.junit.Test

class SpeechLogicTest {
    @Test fun readPageWithBengaliDigits() = assertEquals(VoiceCommand.ReadPage(20), VoiceCommands.parse("এই PDF-এর ২০ নম্বর পৃষ্ঠা বাংলায় পড়ে শোনাও"))
    @Test fun readPageWithAsciiDigits() = assertEquals(VoiceCommand.ReadPage(7), VoiceCommands.parse("page 7 পড়ো"))
    @Test fun stopAndPauseAndSlow() {
        assertEquals(VoiceCommand.Stop, VoiceCommands.parse("থামো"))
        assertEquals(VoiceCommand.Slower, VoiceCommands.parse("আস্তে পড়ো"))
        assertEquals(VoiceCommand.Resume, VoiceCommands.parse("আগের জায়গা থেকে আবার পড়ো"))
    }
    @Test fun numbersNextToAnchorWordsAreUnderstoodButUnknownWordsAreNotGuessed() {
        assertEquals(VoiceCommand.ReadPage(20), VoiceCommands.parse("বিশ নম্বর পৃষ্ঠা পড়ো"))
        assertEquals(VoiceCommand.ReadPage(15), VoiceCommands.parse("পনেরো নম্বর পৃষ্ঠা থেকে পড়া শুরু করো"))
        assertEquals(VoiceCommand.ReadPage(37), VoiceCommands.parse("পৃষ্ঠা সাঁইত্রিশ পড়ো"))
        assertTrue(VoiceCommands.parse("২ আর ৫ নম্বর পৃষ্ঠা") is VoiceCommand.Ambiguous)
        assertTrue(VoiceCommands.parse("পৃষ্ঠা পড়ো") is VoiceCommand.Ambiguous)                // no number
        assertTrue(VoiceCommands.parse("হাজার নম্বর পৃষ্ঠা পড়ো") is VoiceCommand.Ambiguous)   // unlisted word -> not guessed
    }

    @Test fun casualNumberWordElsewhereIsNotTreatedAsNumber() =
        assertEquals(VoiceCommand.ReadPage(15), VoiceCommands.parse("এক জায়গা থেকে পনেরো নম্বর পৃষ্ঠা পড়ো"))   // "এক" is not next to an anchor

    @Test fun chaptersAndExplain() {
        assertEquals(VoiceCommand.ReadChapter(3), VoiceCommands.parse("তিন নম্বর অধ্যায় থেকে পড়া শুরু করো"))
        assertEquals(VoiceCommand.ReadChapter(5), VoiceCommands.parse("৫ নম্বর চ্যাপ্টার পড়ো"))
        assertEquals(VoiceCommand.Explain, VoiceCommands.parse("এখানে আসলে এটার কী বুঝাইছে আমাকে একটু বুঝিয়ে বলো"))
        assertEquals(VoiceCommand.Explain, VoiceCommands.parse("এটার মানে কী"))
    }

    @Test fun openFileWithNameAndThenReading() {
        val c = VoiceCommands.parse("ইসলামিক ইতিহাস ফাইলটা ওপেন করো এবং পনেরো নম্বর পৃষ্ঠা থেকে পড়া শুরু করো") as VoiceCommand.OpenFile
        assertEquals("ইসলামিক ইতিহাস", c.query); assertEquals(VoiceCommand.ReadPage(15), c.then)
        val d = VoiceCommands.parse("ফাইলটা খোলো") as VoiceCommand.OpenFile
        assertEquals("", d.query); assertNull(d.then)
        assertFalse(VoiceCommands.safeWithoutConfirm(c))
    }

    @Test fun fileMatcherNeverPicksSilentlyAmongSeveral() {
        val names = listOf("ইসলামিক ইতিহাস.pdf", "ইসলামিক আইন.docx", "report-2019.xlsx")
        assertEquals(FileMatcher.Result.One("ইসলামিক ইতিহাস.pdf"), FileMatcher.find("ইসলামিক ইতিহাস", names))
        assertTrue(FileMatcher.find("ইসলামিক", names) is FileMatcher.Result.Many)
        assertEquals(FileMatcher.Result.One("report-2019.xlsx"), FileMatcher.find("report 2019", names))
        assertEquals(FileMatcher.Result.None, FileMatcher.find("অন্য কিছু", names))
        assertTrue(FileMatcher.find("", names) is FileMatcher.Result.Many)
    }

    @Test fun numberWordTableSpotChecks() {
        assertEquals(15, BengaliNumbers.parse("পনেরো")); assertEquals(25, BengaliNumbers.parse("পঁচিশ")); assertEquals(50, BengaliNumbers.parse("পঞ্চাশ"))
        assertEquals(99, BengaliNumbers.parse("নিরানব্বই")); assertEquals(100, BengaliNumbers.parse("একশো")); assertNull(BengaliNumbers.parse("হাজার"))
    }
    @Test fun unknownStaysUnknown() = assertTrue(VoiceCommands.parse("আজকের আবহাওয়া কেমন") is VoiceCommand.Unknown)
    @Test fun onlyStopAndPauseNeedNoConfirmation() {
        assertTrue(VoiceCommands.safeWithoutConfirm(VoiceCommand.Stop)); assertTrue(VoiceCommands.safeWithoutConfirm(VoiceCommand.Pause))
        assertFalse(VoiceCommands.safeWithoutConfirm(VoiceCommand.ReadPage(1)))
    }

    private val pages = mapOf(1 to "Alpha one. Alpha two.", 2 to null, 3 to "Gamma. ".repeat(100), 4 to "Delta.")
    private val plan = ReadingPlan({ pages[it] }, 4, maxChars = 100)

    @Test fun cursorWalksSegmentsAndSkipsEmptyPages() {
        var p = plan.startAt(1)!!; val seen = ArrayList<Int>()
        while (true) { seen += p.page; p = plan.next(p) ?: break }
        assertTrue(2 !in seen); assertEquals(1, seen.first()); assertEquals(4, seen.last())
        assertTrue(seen.count { it == 3 } > 1)   // long page = many short segments, never one giant utterance
    }
    @Test fun startAtSkipsToNextPageWithText() = assertEquals(ReadingPosition(3, 0), plan.startAt(2))
    @Test fun previousCrossesPagesBackwards() {
        val first4 = ReadingPosition(4, 0)
        val prev = plan.previous(first4)!!; assertEquals(3, prev.page); assertEquals(plan.segments(3).size - 1, prev.segment)
        assertNull(plan.previous(ReadingPosition(1, 0)))
    }
    @Test fun nothingReadableAfterLastPage() = assertNull(plan.startAt(5))

    @Test fun pageRangeCommand() {
        assertEquals(VoiceCommand.ReadRange(10, 20), VoiceCommands.parse("১০ থেকে ২০ নম্বর পৃষ্ঠা পড়ো"))
        assertEquals(VoiceCommand.ReadRange(5, 9), VoiceCommands.parse("নয় নম্বর পৃষ্ঠা থেকে পাঁচ পর্যন্ত পড়ো"))   // order is normalised
        assertTrue(VoiceCommands.parse("৩ ও ৪ পৃষ্ঠা পড়ো") is VoiceCommand.Ambiguous)                       // no "from/to" word: never guessed
    }
}
