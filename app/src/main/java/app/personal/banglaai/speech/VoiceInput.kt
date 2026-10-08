package app.personal.banglaai.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Press-to-talk, ONE utterance per press, using Android's ON-DEVICE recognizer only (API 31+). If the on-device recognizer or its
 * Bengali language pack is missing it says so; it never falls back to the default (possibly cloud) recognizer.
 * The transcript is returned for the user to see/correct; it is never executed here.
 *
 * Robustness (phones differ a lot): the language-pack check runs ONCE per instance (creating/destroying extra recognizers before every
 * press made the engine answer BUSY/CLIENT errors); transient engine errors are retried automatically (new recogniser, short pause);
 * live partial text is reported so the owner SEES that the phone hears; a partial result is used if the engine ends without a final one;
 * longer silence limits are requested so a short pause does not cut the sentence; a watchdog catches an engine that never answers.
 * Every error says its numeric code, so a failure can be reported precisely. Status: device-test pending.
 */
class VoiceInput(private val context: Context) : SpeechListener {
    override fun available() = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    private val main = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var epoch = 0   // bumped by every stop(), so a language-pack check or recogniser callback that finishes late cannot start/answer a listen that was already cancelled
    private var packChecked = false
    private var installedTags: List<String> = emptyList()
    private var watchdog: Runnable? = null

    /** What the owner should see while a press is in progress ("starting", "ready", "hearing", "processing"). */
    override fun listen(
        onText: (String) -> Unit, onError: (String) -> Unit, onLevel: ((Float) -> Unit)?, lang: String,
        onPartial: ((String) -> Unit)?, onStage: ((String) -> Unit)?,
    ) {
        if (!available()) return onError(UNAVAILABLE_MESSAGE)
        val e = ++epoch
        onStage?.invoke("starting")
        fun go() {
            if (e != epoch) return
            val tag = if (installedTags.isEmpty()) lang else pickInstalled(lang, installedTags)
            if (tag == null) return onError("এই ভাষার (${lang}) অফলাইন ভাষা-প্যাক ফোনে ইনস্টল নেই (আছে: ${installedTags.joinToString()})। নিচের ‘Download Bengali pack’ চাপুন বা ‘ফোন পরীক্ষা’ পর্দা দেখুন।")
            start(e, tag, 0, onText, onError, onLevel, onPartial, onStage)
        }
        if (Build.VERSION.SDK_INT >= 33 && !packChecked) {
            packStatus(lang) { installed, _, err ->
                if (e != epoch) return@packStatus
                packChecked = true; if (err == null) installedTags = installed
                main.postDelayed({ go() }, 150)   // let the checking recogniser be fully released before the real one starts
            }
        } else go()
    }

    /** Installed on-device language tags for the recogniser (Android 13+); [onResult] runs on the main thread. */
    fun packStatus(lang: String, onResult: (installed: List<String>, supported: List<String>, error: String?) -> Unit) {
        if (!available() || Build.VERSION.SDK_INT < 33) return onResult(emptyList(), emptyList(), "এই তথ্য Android 13+ ফোনে পাওয়া যায়")
        val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        r.checkRecognitionSupport(intent(lang), androidx.core.content.ContextCompat.getMainExecutor(context), object : android.speech.RecognitionSupportCallback {
            override fun onSupportResult(s: android.speech.RecognitionSupport) { onResult(s.installedOnDeviceLanguages, s.supportedOnDeviceLanguages, null); r.destroy() }
            override fun onError(error: Int) { onResult(emptyList(), emptyList(), "পরীক্ষা ব্যর্থ (কোড $error)"); r.destroy() }
        })
    }

    /** Asks the phone's own speech service (not this app, which has no internet permission) to download the language pack. Android 13+. */
    fun downloadPack(lang: String): Boolean {
        if (!available() || Build.VERSION.SDK_INT < 33) return false
        val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        r.triggerModelDownload(intent(lang))
        main.postDelayed({ r.destroy() }, 5_000)
        packChecked = false   // look again next time
        return true
    }

    private fun intent(lang: String) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)

    /** Exact tag if installed, else another installed variant of the same language (e.g. bn-IN when bn-BD was asked). */
    internal fun pickInstalled(lang: String, installed: List<String>): String? {
        val want = lang.replace('_', '-').lowercase()
        return installed.firstOrNull { it.replace('_', '-').lowercase() == want } ?: installed.firstOrNull { it.substringBefore('-', it).lowercase() == want.substringBefore('-') }
    }

    private fun start(
        e: Int, lang: String, attempt: Int, onText: (String) -> Unit, onError: (String) -> Unit, onLevel: ((Float) -> Unit)?,
        onPartial: ((String) -> Unit)?, onStage: ((String) -> Unit)?,
    ) {
        if (e != epoch) return
        releaseRecognizer()
        val r = try { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) } catch (t: Throwable) { return onError("ভয়েস চেনা চালু করা যায়নি (${t.javaClass.simpleName})") }
        rec = r
        var lastPartial = ""; var done = false
        fun finish(block: () -> Unit) { if (done || e != epoch || rec !== r) return; done = true; cancelWatchdog(); block(); if (rec === r) releaseRecognizer() }
        fun retryOrFail(code: Int) {
            if (attempt < MAX_RETRIES && e == epoch) {
                done = true; cancelWatchdog(); onStage?.invoke("retrying")
                main.postDelayed({ start(e, lang, attempt + 1, onText, onError, onLevel, onPartial, onStage) }, 450L * (attempt + 1))
            } else finish { onError(describe(code)) }
        }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() } ?: lastPartial.takeIf { it.isNotBlank() }
                finish { if (t == null) onError(NOTHING_HEARD) else onText(t) }
            }
            override fun onError(error: Int) {
                if (done) return
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        if (lastPartial.isNotBlank()) { val t = lastPartial; finish { onText(t) } } else finish { onError(NOTHING_HEARD) }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_TOO_MANY_REQUESTS, SpeechRecognizer.ERROR_AUDIO -> retryOrFail(error)
                    else -> finish { onError(describe(error)) }
                }
            }
            override fun onReadyForSpeech(p: Bundle?) { cancelWatchdog(); onStage?.invoke("ready") }
            override fun onBeginningOfSpeech() { onStage?.invoke("hearing") }
            override fun onEndOfSpeech() { onStage?.invoke("processing") }
            override fun onRmsChanged(v: Float) { onLevel?.invoke(((v + 2f) / 12f).coerceIn(0f, 1f)) }
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onPartialResults(b: Bundle?) {
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrBlank()) { lastPartial = t; onPartial?.invoke(t) }
            }
            override fun onEvent(t: Int, b: Bundle?) {}
        })
        // an engine that never even says "ready" would leave the screen waiting forever
        watchdog = Runnable { if (!done && e == epoch && rec === r) retryOrFail(CODE_NO_RESPONSE) }.also { main.postDelayed(it, START_WATCHDOG_MS) }
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang).putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true).putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L))
    }

    private fun cancelWatchdog() { watchdog?.let { main.removeCallbacks(it) }; watchdog = null }
    private fun releaseRecognizer() { rec?.let { runCatching { it.cancel() }; runCatching { it.destroy() } }; rec = null }

    /** Mic is released on every path: result, error, or leaving the screen. */
    override fun stop() { epoch++; cancelWatchdog(); releaseRecognizer() }

    companion object {
        const val NOTHING_HEARD = "কিছু শোনা যায়নি"
        const val UNAVAILABLE_MESSAGE = "এই ফোনে অফলাইন (on-device) বাংলা ভয়েস চেনা পাওয়া যায়নি। ইন্টারনেট-ভিত্তিক বিকল্পে নিজে থেকে যাওয়া হয় না। লিখে নির্দেশ দিন।"
        private const val MAX_RETRIES = 2
        private const val START_WATCHDOG_MS = 8_000L
        private const val CODE_NO_RESPONSE = -1

        /** One plain sentence per engine error, always with the number (so "code 5" can be reported exactly). */
        fun describe(code: Int): String = when (code) {
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                "এই ভাষার (বা বাংলার) অফলাইন ভাষা-প্যাক ফোনে নেই (কোড $code)। ফোনের সেটিংস → ভাষা/ভয়েস ইনপুট থেকে অফলাইন প্যাক নামান, অথবা লিখে দিন।"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "মাইক্রোফোনের অনুমতি দেওয়া হয়নি (কোড $code)"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ফোনের ভয়েস-সেবা ব্যস্ত ছিল (কোড $code) — কয়েকবার নিজে চেষ্টা করেও পারেনি; একটু পরে আবার চাপুন"
            SpeechRecognizer.ERROR_AUDIO -> "মাইক থেকে শব্দ পাওয়া যায়নি (কোড $code) — অন্য অ্যাপ মাইক ধরে রেখেছে কি না দেখুন"
            CODE_NO_RESPONSE -> "ফোনের ভয়েস-সেবা সাড়া দেয়নি (কোড $code) — কয়েকবার চেষ্টা করেও না; আবার চাপুন"
            else -> "ভয়েস চেনা ব্যর্থ (কোড $code) — আবার চাপুন; বারবার হলে এই কোডসহ জানান"
        }
    }
}
