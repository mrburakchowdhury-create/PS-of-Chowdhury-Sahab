package app.personal.banglaai.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice

/** Offline Bengali voice choice. Only installed voices that do NOT need the network are ever offered or used; the owner's pick is stored by voice name. */
object BengaliVoices {
    const val PREF = "tts_voice"
    fun offline(tts: TextToSpeech): List<Voice> =
        tts.voices.orEmpty().filter { it.locale.language == "bn" && !it.features.contains("notInstalled") && !it.isNetworkConnectionRequired }.sortedBy { it.name }

    /** The owner's chosen voice if it is still installed and offline, else the first offline Bengali voice, else null (nothing is spoken). */
    fun choose(context: Context, tts: TextToSpeech): Voice? {
        val list = offline(tts); val want = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString(PREF, null)
        return list.firstOrNull { it.name == want } ?: list.firstOrNull()
    }
}
