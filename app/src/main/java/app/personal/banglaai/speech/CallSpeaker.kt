package app.personal.banglaai.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener

/**
 * Speaker for the voice call: queues sentences as the model streams them, reports each spoken word (for the avatar's mouth-pulse) and tells when
 * everything queued has been spoken. Same rule as [TtsReader]: only an installed offline Bengali voice; otherwise [ready] is false and nothing is spoken.
 * Status: compiles; device-test pending.
 */
class CallSpeaker(context: Context, private val onReady: (Boolean) -> Unit, private val onWord: () -> Unit, private val onIdle: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var pending = 0
    private var seq = 0
    private var gen = 0   // bumped by stop(), so late callbacks of cancelled sentences are ignored
    var ready = false; private set
    private companion object { const val STALL_MS = 45_000L }
    var onlyNetworkBengaliExists = false; private set

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            main.post {
                val t = tts ?: return@post
                if (status != TextToSpeech.SUCCESS) return@post onReady(false)
                val bn = t.voices.orEmpty().filter { it.locale.language == "bn" && !it.features.contains("notInstalled") }
                val offline = bn.firstOrNull { !it.isNetworkConnectionRequired }
                onlyNetworkBengaliExists = offline == null && bn.isNotEmpty()
                if (offline == null) return@post onReady(false)
                t.voice = offline
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) { val g = gen; main.post { if (g == gen) onWord() } }
                    override fun onDone(id: String?) { val g = gen; main.post { if (g == gen) finished() } }
                    @Deprecated("") override fun onError(id: String?) { val g = gen; main.post { if (g == gen) finished() } }
                })
                ready = true; onReady(true)
            }
        }
    }

    private var lastEvent = 0L
    private val watchdog = object : Runnable {
        override fun run() {
            if (pending <= 0) return
            if (android.os.SystemClock.elapsedRealtime() - lastEvent > STALL_MS) { gen++; pending = 0; tts?.stop(); onIdle() }   // the voice engine went silent: do not leave the call stuck in "speaking"
            else main.postDelayed(this, 5_000)
        }
    }
    private fun finished() { lastEvent = android.os.SystemClock.elapsedRealtime(); if (pending > 0) pending--; if (pending == 0) { main.removeCallbacks(watchdog); onIdle() } }

    fun speak(sentence: String) {
        val clean = SpeechText.clean(sentence)
        if (!ready || clean.isBlank()) return
        pending++; lastEvent = android.os.SystemClock.elapsedRealtime()
        val r = tts?.speak(clean.take(TextToSpeech.getMaxSpeechInputLength() - 1), TextToSpeech.QUEUE_ADD, null, "c${++seq}") ?: TextToSpeech.ERROR
        if (r != TextToSpeech.SUCCESS) { main.post { finished() }; return }   // refused: count it as spoken so the call moves on
        main.removeCallbacks(watchdog); main.postDelayed(watchdog, 5_000)
    }

    fun setRate(r: Float) { tts?.setSpeechRate(r.coerceIn(0.5f, 2.0f)) }
    val isSpeaking get() = pending > 0
    fun stop() { gen++; pending = 0; main.removeCallbacks(watchdog); tts?.stop() }
    fun shutdown() { gen++; pending = 0; main.removeCallbacks(watchdog); tts?.stop(); tts?.shutdown(); tts = null }
}
