package app.personal.banglaai.speech

import android.content.Context
import app.personal.banglaai.doc.DocWorkflow

/**
 * Which engine turns speech into text: the AI model's own audio understanding, or the phone's on-device recogniser. "Auto" (default) uses the AI model when one is installed
 * (it does not depend on Google's language packs, which were unreliable on the owner's phone) and the phone's recogniser otherwise. The owner can force either one in Settings.
 */
object VoiceEngine {
    enum class Mode(val key: String) { AUTO("auto"), PHONE("phone"), MODEL("model") }
    private const val PREF = "voice_engine"

    fun mode(context: Context): Mode = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString(PREF, null).let { k -> Mode.entries.firstOrNull { it.key == k } ?: Mode.AUTO }
    fun setMode(context: Context, m: Mode) { context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString(PREF, m.key).apply() }

    fun create(context: Context, wf: DocWorkflow): SpeechListener {
        val hasModel = wf.usableModel() != null
        return when (mode(context)) {
            Mode.PHONE -> VoiceInput(context)
            Mode.MODEL -> if (hasModel) ModelVoiceInput(context) { wf.usableModel() } else VoiceInput(context)
            Mode.AUTO -> if (hasModel) ModelVoiceInput(context) { wf.usableModel() } else VoiceInput(context)
        }
    }

    fun usesModel(context: Context, wf: DocWorkflow) = wf.usableModel() != null && mode(context) != Mode.PHONE
}
