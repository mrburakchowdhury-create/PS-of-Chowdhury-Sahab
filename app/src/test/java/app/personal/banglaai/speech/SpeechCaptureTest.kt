package app.personal.banglaai.speech

import app.personal.banglaai.speech.EndpointDetector.Event
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioSystem
import org.junit.Assert.*
import org.junit.Test

class SpeechCaptureTest {
    private fun run(det: EndpointDetector, frames: List<Double>): List<Pair<Int, Event>> = frames.mapIndexedNotNull { i, r -> det.feed(r).takeIf { it != Event.NONE }?.let { i to it } }
    private fun quiet(n: Int) = List(n) { 40.0 }
    private fun loud(n: Int) = List(n) { 2500.0 }

    @Test fun wavFileIsAValid16kMono16BitPcm() {
        val pcm = ShortArray(1600) { (it % 100 - 50).toShort() }
        val b = Wav.encode(pcm)
        assertEquals(44 + 3200, b.size); assertEquals("RIFF", String(b, 0, 4)); assertEquals("WAVE", String(b, 8, 4)); assertEquals("data", String(b, 36, 4))
        val ais = AudioSystem.getAudioInputStream(ByteArrayInputStream(b))   // an independent reader accepts it
        assertEquals(16000f, ais.format.sampleRate); assertEquals(1, ais.format.channels); assertEquals(16, ais.format.sampleSizeInBits); assertEquals(1600L, ais.frameLength)
    }

    @Test fun silenceNeverReachesTheModel() {
        val ev = run(EndpointDetector(), quiet(500))
        assertEquals(listOf(Event.NO_SPEECH), ev.map { it.second }); assertTrue("after about 8 s", ev[0].first in 395..405)
    }

    @Test fun aSentenceThenAPauseEndsTheRecordingOnce() {
        val det = EndpointDetector(); val ev = run(det, quiet(30) + loud(100) + quiet(120))
        assertEquals(listOf(Event.SPEECH_STARTED, Event.SPEECH_ENDED), ev.map { it.second })
        assertEquals("speech starts right after the quiet lead-in", 30 + 2, ev[0].first)
        assertEquals("ends about 1.4 s (70 frames) after the last loud frame", 30 + 100 + 69, ev[1].first)
    }

    @Test fun aShortPauseInsideASentenceDoesNotCutIt() {
        val ev = run(EndpointDetector(), quiet(30) + loud(50) + quiet(40) + loud(50) + quiet(100))   // 0.8 s pause only
        assertEquals(listOf(Event.SPEECH_STARTED, Event.SPEECH_ENDED), ev.map { it.second }); assertTrue(ev[1].first > 30 + 50 + 40 + 50)
    }

    @Test fun someoneWhoSpeaksAtOnceIsStillHeard() {
        val ev = run(EndpointDetector(), loud(60) + quiet(100))
        assertEquals(Event.SPEECH_STARTED, ev.first().second); assertEquals(Event.SPEECH_ENDED, ev.last().second)
    }

    @Test fun aNoisyRoomRaisesTheBarButSpeechStillWins() {
        val ev = run(EndpointDetector(), List(30) { 200.0 } + List(80) { 2500.0 } + List(100) { 200.0 })
        assertEquals(listOf(Event.SPEECH_STARTED, Event.SPEECH_ENDED), ev.map { it.second })
        assertTrue("steady noise alone is never speech", run(EndpointDetector(), List(500) { 200.0 }).single().second == Event.NO_SPEECH)
    }

    @Test fun nonStopTalkingIsCutAtTheLimit() = assertEquals(Event.TOO_LONG, run(EndpointDetector(), quiet(20) + loud(2000)).last().second)

    @Test fun modelInstructionNamesTheLanguageAndCleanupRemovesWrappers() {
        assertTrue(ModelVoiceInput.instruction("bn-BD").contains("বাংলা")); assertTrue(ModelVoiceInput.instruction("ar-SA").contains("আরবি")); assertTrue(ModelVoiceInput.instruction("ur-PK").contains("উর্দু"))
        assertEquals("আপনি কেমন আছেন?", ModelVoiceInput.clean("  “আপনি কেমন আছেন?”\n"))
        assertEquals("কেমন আছেন", ModelVoiceInput.clean("ট্রান্সক্রিপশন: কেমন আছেন")); assertEquals("hello", ModelVoiceInput.clean("**hello**"))
    }
}
