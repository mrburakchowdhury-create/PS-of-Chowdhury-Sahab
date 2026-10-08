package app.personal.banglaai.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import app.personal.banglaai.ai.LocalLlm
import java.io.File
import kotlinx.coroutines.CancellationException

/**
 * Speech -> text WITHOUT the phone's speech service: the microphone is recorded by this app ([PcmRecorder], one sentence, ends after a pause) and the imported AI model
 * (Gemma 4, which understands audio) writes down what was said. Slower than a phone recogniser (the model must load once, then a few seconds to tens of seconds per sentence
 * depending on the phone) but it does not depend on Google's offline language packs. Nothing is recorded to disk or sent anywhere.
 * Verified on a desktop CPU with synthetic Bengali clips (docs/STT_EVAL.md); speed and accuracy on the phone and with real voices/noise: device-test pending.
 */
class ModelVoiceInput(private val context: Context, private val model: () -> File?) : SpeechListener {
    private val main = Handler(Looper.getMainLooper())
    private var epoch = 0
    private var recorder: PcmRecorder? = null
    @Volatile private var llm: LocalLlm? = null

    override fun available() = model()?.exists() == true

    override fun listen(onText: (String) -> Unit, onError: (String) -> Unit, onLevel: ((Float) -> Unit)?, lang: String, onPartial: ((String) -> Unit)?, onStage: ((String) -> Unit)?) {
        val m = model()?.takeIf { it.exists() } ?: return onError("AI model বসানো নেই — Setup → AI Model থেকে বসান (মডেল দিয়েই কথা শোনা হয়)।")
        val e = ++epoch
        onStage?.invoke("starting")
        recorder?.stop()
        val r = PcmRecorder(context); recorder = r
        r.start(onLevel, { st -> if (e == epoch) onStage?.invoke(st) }) { end ->
            if (e != epoch) return@start
            when (end) {
                PcmRecorder.End.NoSpeech -> onError(VoiceInput.NOTHING_HEARD)
                is PcmRecorder.End.Failed -> onError("মাইক: ${end.reason}")
                is PcmRecorder.End.Speech -> {
                    val wav = Wav.encode(end.pcm)
                    Thread {
                        val engine = LocalLlm(m, File(context.cacheDir, "llm")); llm = engine
                        val result = runCatching { clean(engine.transcribe(wav, instruction(lang))) }
                        llm = null
                        main.post {
                            if (e != epoch) return@post
                            result.onSuccess { t -> if (t.isBlank()) onError(VoiceInput.NOTHING_HEARD) else onText(t) }
                                .onFailure { t -> if (t !is CancellationException) onError("AI model দিয়ে শোনা ব্যর্থ: ${t.message ?: t.javaClass.simpleName} — Setup → Settings থেকে ‘Voice recognition’ বদলে ফোনের সেবা বেছে দেখুন।") }
                        }
                    }.also { it.isDaemon = true }.start()
                }
            }
        }
    }

    override fun stop() { epoch++; recorder?.stop(); recorder = null; llm?.cancel() }

    companion object {
        private val names = mapOf("bn" to "বাংলা", "en" to "ইংরেজি", "ar" to "আরবি", "ur" to "উর্দু", "hi" to "হিন্দি")
        /** The model is told to write down exactly what was said, in that language, and nothing else. */
        fun instruction(lang: String): String { val n = names[lang.substringBefore('-').lowercase()] ?: "বাংলা"; return "এই অডিওতে যা বলা হয়েছে হুবহু $n ভাষায় লিখে দাও। শুধু বলা কথাটুকু লেখো, অন্য কিছু (ব্যাখ্যা, অনুবাদ, উদ্ধৃতি-চিহ্ন) নয়। কিছু বোঝা না গেলে কিছুই লিখো না।" }
        /** Removes quotes/markdown the model sometimes wraps around the sentence. */
        fun clean(raw: String): String = raw.trim().trim('"', '“', '”', '\'', '`').replace("**", "").replace(Regex("^(transcription|লেখা|ট্রান্সক্রিপশন)\\s*[:：]\\s*", RegexOption.IGNORE_CASE), "").trim()
    }
}
