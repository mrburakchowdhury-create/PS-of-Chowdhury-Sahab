package app.personal.banglaai.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Press-to-talk, ONE utterance per press, using Android's ON-DEVICE recognizer only (API 31+). If the on-device recognizer or its
 * Bengali language pack is missing it says so; it never falls back to the default (possibly cloud) recognizer.
 * The transcript is returned for the user to see/correct; it is never executed here. Status: compiles; device-test pending.
 */
class VoiceInput(private val context: Context) {
    fun available() = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    private var rec: SpeechRecognizer? = null
    private var epoch = 0   // bumped by every stop(), so a language-pack check or recogniser callback that finishes late cannot start/answer a listen that was already cancelled

    fun listen(onText: (String) -> Unit, onError: (String) -> Unit, onLevel: ((Float) -> Unit)? = null, lang: String = "bn-BD") {
        if (!available()) return onError("এই ফোনে অফলাইন (on-device) বাংলা ভয়েস চেনা পাওয়া যায়নি। ইন্টারনেট-ভিত্তিক বিকল্পে নিজে থেকে যাওয়া হয় না। লিখে নির্দেশ দিন।")
        val e = ++epoch
        if (Build.VERSION.SDK_INT >= 33) {
            packStatus(lang) { installed, _, err ->
                if (e != epoch) return@packStatus
                val tag = pickInstalled(lang, installed)
                if (err == null && installed.isNotEmpty() && tag == null)
                    onError("এই ভাষার (${lang}) অফলাইন ভাষা-প্যাক ফোনে ইনস্টল নেই (আছে: ${installed.joinToString()})। ‘ফোন পরীক্ষা’ পর্দায় ‘ভয়েস-চেনার ভাষা-প্যাক’ অংশ দেখুন।")
                else start(tag ?: lang, onText, onError, onLevel)
            }
        } else start(lang, onText, onError, onLevel)
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
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ r.destroy() }, 5_000)
        return true
    }

    private fun intent(lang: String) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)

    /** Exact tag if installed, else another installed variant of the same language (e.g. bn-IN when bn-BD was asked). */
    internal fun pickInstalled(lang: String, installed: List<String>): String? {
        val want = lang.replace('_', '-').lowercase()
        return installed.firstOrNull { it.replace('_', '-').lowercase() == want } ?: installed.firstOrNull { it.substringBefore('-', it).lowercase() == want.substringBefore('-') }
    }

    private fun start(lang: String, onText: (String) -> Unit, onError: (String) -> Unit, onLevel: ((Float) -> Unit)?) {
        val keep = epoch; stop(); epoch = keep
        val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(context); rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                if (rec !== r) return
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (t.isNullOrBlank()) onError("কিছু শোনা যায়নি") else onText(t); stop()
            }
            override fun onError(error: Int) {
                if (rec !== r) return
                onError(when (error) {
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                        "এই ভাষার (বা বাংলার) অফলাইন ভাষা-প্যাক ফোনে নেই। ফোনের সেটিংস → ভাষা/ভয়েস ইনপুট থেকে অফলাইন প্যাক নামান, অথবা লিখে দিন।"
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "কিছু শোনা যায়নি"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "মাইক্রোফোনের অনুমতি দেওয়া হয়নি"
                    else -> "ভয়েস চেনা ব্যর্থ (কোড $error)"
                }); stop()
            }
            override fun onReadyForSpeech(p: Bundle?) {}; override fun onBeginningOfSpeech() {}; override fun onRmsChanged(v: Float) { onLevel?.invoke(((v + 2f) / 12f).coerceIn(0f, 1f)) }
            override fun onBufferReceived(b: ByteArray?) {}; override fun onEndOfSpeech() {}
            override fun onPartialResults(b: Bundle?) {}; override fun onEvent(t: Int, b: Bundle?) {}
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang).putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false))
    }

    /** Mic is released on every path: result, error, or leaving the screen. */
    fun stop() { epoch++; rec?.cancel(); rec?.destroy(); rec = null }
}
