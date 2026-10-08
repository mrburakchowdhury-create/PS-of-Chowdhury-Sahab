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

    private fun finished() { if (pending > 0) pending--; if (pending == 0) onIdle() }

    fun speak(sentence: String) {
        val clean = SpeechText.clean(sentence)
        if (!ready || clean.isBlank()) return
        pending++
        tts?.speak(clean.take(TextToSpeech.getMaxSpeechInputLength() - 1), TextToSpeech.QUEUE_ADD, null, "c${++seq}")
    }

    fun setRate(r: Float) { tts?.setSpeechRate(r.coerceIn(0.5f, 2.0f)) }
    val isSpeaking get() = pending > 0
    fun stop() { gen++; pending = 0; tts?.stop() }
    fun shutdown() { gen++; pending = 0; tts?.stop(); tts?.shutdown(); tts = null }
}
