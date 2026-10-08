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
class TtsReader(context: Context, private val onState: (State) -> Unit, private val onSegment: (Segment?, ReadingPosition?) -> Unit) {
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
    private var token = 0   // invalidates callbacks from segments that were cancelled

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            main.post {
                if (status != TextToSpeech.SUCCESS) return@post set(State.NoEngine)
                val bn = tts?.voices.orEmpty().filter { it.locale.language == "bn" && !it.features.contains("notInstalled") }
                val offline = bn.firstOrNull { !it.isNetworkConnectionRequired }
                onlyNetworkBengaliExists = offline == null && bn.isNotEmpty()
                if (offline == null) return@post set(State.NoOfflineBengali)
                tts!!.voice = offline; voiceName = offline.name
                tts!!.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { main.post { if (id == "s$token" && state == State.Playing) advance() } }
                    @Deprecated("") override fun onError(id: String?) { main.post { if (id == "s$token") { set(State.Paused) } } }
                })
                set(State.Ready)
            }
        }
    }

    private fun set(s: State) { state = s; onState(s) }

    fun setRate(r: Float) { rate = r.coerceIn(0.5f, 2.0f); tts?.setSpeechRate(rate) }

    fun play(plan: ReadingPlan, from: ReadingPosition) {
        if (state != State.Ready && state != State.Paused && state != State.Playing) return
        this.plan = plan; pos = from; set(State.Playing); speakCurrent()
    }

    fun resume() { if (state == State.Paused && plan != null && pos != null) { set(State.Playing); speakCurrent() } }
    fun pause() { if (state == State.Playing) { token++; tts?.stop(); set(State.Paused) } }
    fun stop() { token++; tts?.stop(); if (state == State.Playing || state == State.Paused) set(State.Ready); onSegment(null, pos) }
    fun next() { val p = pos ?: return; plan?.next(p)?.let { jump(it) } }
    fun previous() { val p = pos ?: return; plan?.previous(p)?.let { jump(it) } }
    fun position() = pos

    private fun jump(p: ReadingPosition) { pos = p; if (state == State.Playing) speakCurrent() else onSegment(plan?.segmentAt(p), p) }

    private fun advance() { val n = pos?.let { plan?.next(it) }; if (n == null) { set(State.Ready); onSegment(null, pos) } else { pos = n; speakCurrent() } }

    private fun speakCurrent() {
        val seg = pos?.let { plan?.segmentAt(it) } ?: return advance()
        token++; onSegment(seg, pos)
        tts?.setSpeechRate(rate)
        tts?.speak(SpeechText.clean(seg.text).ifBlank { "।" }, TextToSpeech.QUEUE_FLUSH, null, "s$token")
    }

    fun shutdown() { token++; tts?.stop(); tts?.shutdown(); tts = null }
}
