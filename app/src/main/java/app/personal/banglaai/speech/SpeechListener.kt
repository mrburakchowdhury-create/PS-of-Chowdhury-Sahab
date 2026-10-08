package app.personal.banglaai.speech

/** One spoken sentence per call, turned into text. Two engines implement it: the phone's own on-device recogniser ([VoiceInput]) and the AI model's audio understanding ([ModelVoiceInput]). */
interface SpeechListener {
    fun available(): Boolean
    /** [onStage] gets "starting", "ready", "hearing", "processing" (and "retrying"); [onPartial] gets live text when the engine can give it. Everything is delivered on the main thread. */
    fun listen(
        onText: (String) -> Unit, onError: (String) -> Unit, onLevel: ((Float) -> Unit)? = null, lang: String = "bn-BD",
        onPartial: ((String) -> Unit)? = null, onStage: ((String) -> Unit)? = null,
    )
    /** Releases the microphone and cancels any work; no callback is delivered afterwards. */
    fun stop()
}
