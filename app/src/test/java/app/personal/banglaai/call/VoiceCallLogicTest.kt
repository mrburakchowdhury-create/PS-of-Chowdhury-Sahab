package app.personal.banglaai.call

import app.personal.banglaai.call.VoiceCallMachine.Action
import app.personal.banglaai.call.VoiceCallMachine.Phase
import org.junit.Assert.*
import org.junit.Test

class VoiceCallLogicTest {
    @Test fun normalTurnCycle() {
        val m = VoiceCallMachine()
        assertEquals(Action.Listen, m.start()); assertEquals(Phase.Listening, m.phase)
        assertEquals(Action.Think("জ্ঞানের গুরুত্ব কী?"), m.onHeard("  জ্ঞানের গুরুত্ব কী? ")); assertEquals(Phase.Thinking, m.phase)
        m.onAnswerStarted(); assertEquals(Phase.Speaking, m.phase)
        assertEquals(Action.Listen, m.onSpeechFinished()); assertEquals(Phase.Listening, m.phase)
    }

    @Test fun micIsNeverOpenWhileThinkingOrSpeaking() {
        val m = VoiceCallMachine(); m.start(); m.onHeard("প্রশ্ন")
        assertEquals(Action.None, m.onHeard("নিজের কণ্ঠ শোনা গেল"))   // heard text while not Listening is ignored (echo guard)
        m.onAnswerStarted(); assertEquals(Action.None, m.onHeard("আবার")); assertEquals(Action.None, m.onSilence())
    }

    @Test fun threeSilencesEndTheCallButSpeechResetsTheCount() {
        val m = VoiceCallMachine(3); m.start()
        assertEquals(Action.Listen, m.onSilence()); assertEquals(Action.Listen, m.onSilence())
        m.onHeard("হ্যালো"); m.onSpeechFinished()                       // spoke -> counter reset
        assertEquals(Action.Listen, m.onSilence()); assertEquals(Action.Listen, m.onSilence())
        assertTrue(m.onSilence() is Action.End); assertEquals(Phase.Ended, m.phase)
    }

    @Test fun endPhrasesEndTheCall() {
        listOf("কল শেষ করো", "আল্লাহ হাফেজ", "ধন্যবাদ বিদায়").forEach { w ->
            val m = VoiceCallMachine(); m.start(); assertTrue(w, m.onHeard(w) is Action.End)
        }
        val m2 = VoiceCallMachine(); m2.start(); assertTrue(m2.onHeard("শেষ বিচারের দিন কখন?") is Action.Think)   // "শেষ" alone is not an end word
    }

    @Test fun interruptReturnsToListening() {
        val m = VoiceCallMachine(); m.start(); m.onHeard("লম্বা প্রশ্ন"); m.onAnswerStarted()
        assertEquals(Action.StopSpeaking, m.onInterrupt()); assertEquals(Phase.Listening, m.phase)
        assertEquals(Action.None, m.onInterrupt())   // nothing to interrupt now
        assertTrue(VoiceCallMachine.isInterruptPhrase("থামো")); assertFalse(VoiceCallMachine.isInterruptPhrase("আজ কী বার"))
    }

    @Test fun sentenceBufferSplitsStreamedTextAndStripsFormatting() {
        val b = SentenceBuffer(); val out = ArrayList<String>()
        listOf("**জ্ঞান অর্জন", " করা খুবই জরুরি।** ", "এটি মানুষকে ", "এগিয়ে নেয়। ", "আরও জানতে ## চাইলে বলুন").forEach { out += b.push(it) }
        b.flush()?.let { out += it }
        assertEquals(listOf("জ্ঞান অর্জন করা খুবই জরুরি।", "এটি মানুষকে এগিয়ে নেয়।", "আরও জানতে চাইলে বলুন"), out)
        assertEquals("কোড দেখুন পর্দায়", SentenceBuffer.clean("```py\nprint(1)\n```"))
        assertFalse(SentenceBuffer.clean("দেখুন https://example.com/x এখানে").contains("http"))
    }

    @Test fun decimalsAreNotCutInTheMiddle() {
        val b = SentenceBuffer(); val out = b.push("মূল্য ছিল 1.5 ডলার মাত্র।") + listOfNotNull(b.flush())
        assertEquals(listOf("মূল্য ছিল 1.5 ডলার মাত্র।"), out)
    }
}
