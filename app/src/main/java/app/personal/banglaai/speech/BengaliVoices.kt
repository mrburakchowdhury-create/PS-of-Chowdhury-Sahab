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

    /** Offline voice for [lang] (English/Arabic runs inside a Bengali text); null if the phone has none installed. Never a network voice. */
    fun offlineFor(tts: TextToSpeech, lang: LangRuns.L): Voice? {
        if (lang == LangRuns.L.BN) return null
        return tts.voices.orEmpty().filter { it.locale.language == lang.tag && !it.features.contains("notInstalled") && !it.isNetworkConnectionRequired }
            .sortedWith(compareBy({ it.locale.country != (if (lang == LangRuns.L.EN) "US" else "SA") }, { it.name })).firstOrNull()
    }

    /**
     * Queues [text] as one utterance per language run, each with its own offline voice (Bengali voice = [bn]); the first one flushes the queue when [flush].
     * Returns the utterance ids in order (the caller treats the LAST id's completion as "text finished"), or an empty list if nothing was accepted.
     */
    fun speakRuns(tts: TextToSpeech, bn: Voice, text: String, flush: Boolean, idPrefix: String, maxLen: Int = TextToSpeech.getMaxSpeechInputLength() - 1): List<String> {
        val runs = LangRuns.split(text).ifEmpty { listOf(LangRuns.Run(text, LangRuns.L.BN)) }
        val ids = ArrayList<String>(); val cache = HashMap<LangRuns.L, Voice?>()
        for ((i, r) in runs.withIndex()) {
            val v = if (r.lang == LangRuns.L.BN) bn else cache.getOrPut(r.lang) { offlineFor(tts, r.lang) } ?: bn   // no offline voice for that language: the Bengali one still reads the letters
            if (tts.voice?.name != v.name) tts.setVoice(v)
            val id = "$idPrefix.$i"
            val mode = if (flush && i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            if (tts.speak(r.text.take(maxLen), mode, null, id) != TextToSpeech.SUCCESS) return ids.also { if (it.isEmpty()) it.clear() }
            ids += id
        }
        return ids
    }
}
