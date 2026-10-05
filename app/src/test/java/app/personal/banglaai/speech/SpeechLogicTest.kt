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
    @Test fun numberWordsAndMissingNumbersAreAmbiguousNotGuessed() {
        assertTrue(VoiceCommands.parse("বিশ নম্বর পৃষ্ঠা পড়ো") is VoiceCommand.Ambiguous)
        assertTrue(VoiceCommands.parse("২ আর ৫ নম্বর পৃষ্ঠা") is VoiceCommand.Ambiguous)
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
}
