package app.personal.banglaai.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

/**
 * Reads aloud one short segment at a time with the phone's TTS engine, using ONLY an installed voice that does not require
 * the network. If no such Bengali voice exists, nothing is spoken and the UI explains how to install one — there is no silent
 * fallback to a network voice or another language. Pause = stop speaking and remember the segment; resume replays that segment.
 * Status: compiles; whether the S24 Ultra has a usable offline Bengali voice and how it sounds is device-test pending.
 */
class TtsReader(private val context: Context, private val onState: (State) -> Unit, private val onSegment: (Segment?, ReadingPosition?) -> Unit) {
    enum class State { Initialising, NoEngine, NoOfflineBengali, Ready, Playing, Paused }
    var state = State.Initialising; private set
    /** True if a Bengali voice exists but needs the network (we refuse to use it). */
    var onlyNetworkBengaliExists = false; private set
    var voiceName: String? = null; private set

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var plan: ReadingPlan? = null
    private var pos: ReadingPosition? = null
    private var rate = 1.0f
    private var queued: Pair<ReadingPlan, ReadingPosition>? = null   // play() pressed while the voice engine was still starting
    private var token = 0   // invalidates callbacks from segments that were cancelled
    private var lastId: String? = null   // the segment is finished when its LAST language-run utterance finishes
    private var bnVoice: Voice? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            main.post {
                if (status != TextToSpeech.SUCCESS) return@post set(State.NoEngine)
                val bn = tts?.voices.orEmpty().filter { it.locale.language == "bn" && !it.features.contains("notInstalled") }
                val offline = tts?.let { BengaliVoices.choose(context, it) }
                onlyNetworkBengaliExists = offline == null && bn.isNotEmpty()
                if (offline == null) return@post set(State.NoOfflineBengali)
                if (tts!!.setVoice(offline) != TextToSpeech.SUCCESS || tts!!.voice?.name != offline.name) return@post set(State.NoOfflineBengali)   // C06: refused voice = not ready
                voiceName = offline.name; bnVoice = offline
                tts!!.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { main.post { if (id != null && id == lastId && state == State.Playing) advance() } }
                    @Deprecated("") override fun onError(id: String?) { main.post { if (id != null && id.startsWith("s$token.")) { set(State.Paused) } } }
                })
                set(State.Ready)
                queued?.let { (p, f) -> queued = null; play(p, f) }
            }
        }
    }

    private fun set(s: State) { state = s; onState(s) }

    fun setRate(r: Float) { rate = r.coerceIn(0.5f, 2.0f); tts?.setSpeechRate(rate) }

    fun play(plan: ReadingPlan, from: ReadingPosition) {
        if (state == State.Initialising) { queued = plan to from; return }
        if (state != State.Ready && state != State.Paused && state != State.Playing) return
        this.plan = plan; pos = from; set(State.Playing); speakCurrent()
    }

    fun resume() { if (state == State.Paused && plan != null && pos != null) { set(State.Playing); speakCurrent() } }
    fun pause() { token++; tts?.stop(); if (state == State.Playing) set(State.Paused) }   // the speech engine is stopped on EVERY press, whatever the screen thinks the state is
    fun stop() { token++; lastId = null; tts?.stop(); if (state == State.Playing || state == State.Paused) set(State.Ready); onSegment(null, pos) }
    fun next() { val p = pos ?: return; plan?.next(p)?.let { jump(it) } }
    fun previous() { val p = pos ?: return; plan?.previous(p)?.let { jump(it) } }
    fun position() = pos

    /** See [ReadingPlan.snapshot]: after this the reader no longer touches the vault. False if there is nothing to keep. */
    fun freezeForBackground(maxPages: Int, maxChars: Int): Boolean {
        val p = pos ?: return false; val pl = plan ?: return false
        plan = pl.snapshot(p.page, maxPages, maxChars); return true
    }

    private fun jump(p: ReadingPosition) { pos = p; if (state == State.Playing) speakCurrent() else onSegment(plan?.segmentAt(p), p) }

    private fun advance() { val n = pos?.let { plan?.next(it) }; if (n == null) { set(State.Ready); onSegment(null, pos) } else { pos = n; speakCurrent() } }

    private fun speakCurrent() {
        val seg = pos?.let { plan?.segmentAt(it) } ?: return advance()
        token++; onSegment(seg, pos)
        tts?.setSpeechRate(rate)
        val t = tts; val v = bnVoice
        val ids = if (t != null && v != null) BengaliVoices.speakRuns(t, v, SpeechText.clean(seg.text).ifBlank { "।" }, true, "s$token") else emptyList()
        lastId = ids.lastOrNull()
        if (ids.isEmpty()) set(State.Paused)   // the engine refused: stop honestly instead of showing "playing" in silence
    }

    fun shutdown() { queued = null; token++; tts?.stop(); tts?.shutdown(); tts = null }
}
