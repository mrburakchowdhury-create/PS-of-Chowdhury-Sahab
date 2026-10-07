package app.personal.banglaai.call

import app.personal.banglaai.speech.VoiceCommand
import app.personal.banglaai.speech.VoiceCommands

/**
 * Turn-taking for the hands-free "voice call" with the local assistant. Pure logic (tested on the JVM); the screen does the I/O.
 * Privacy rules built in: a call exists only after the owner taps "start"; the mic is open ONLY in [Phase.Listening] (never while the assistant
 * speaks, so it cannot hear and obey itself); after [maxSilences] empty listens in a row the call ends by itself; "call end" words end it.
 */
class VoiceCallMachine(private val maxSilences: Int = 3) {
    enum class Phase { Idle, Listening, Thinking, Speaking, Ended }
    sealed interface Action {
        data object Listen : Action
        data class Think(val userText: String) : Action
        data object StopSpeaking : Action
        data class End(val reason: String) : Action
        data object None : Action
    }

    var phase = Phase.Idle; private set
    private var silences = 0

    fun start(): Action { phase = Phase.Listening; silences = 0; return Action.Listen }

    fun onHeard(text: String): Action {
        if (phase != Phase.Listening) return Action.None
        val t = text.trim()
        if (t.isEmpty()) return onSilence()
        if (isEndPhrase(t)) return end("কল শেষ করা হয়েছে")
        silences = 0; phase = Phase.Thinking; return Action.Think(t)
    }

    fun onSilence(): Action {
        if (phase != Phase.Listening) return Action.None
        silences++
        return if (silences >= maxSilences) end("অনেকক্ষণ কোনো কথা শোনা যায়নি, তাই কল শেষ হয়েছে") else Action.Listen
    }

    fun onAnswerStarted() { if (phase == Phase.Thinking) phase = Phase.Speaking }

    /** Everything the assistant had to say has been spoken (or there was nothing to speak): back to listening. */
    fun onSpeechFinished(): Action = if (phase == Phase.Speaking || phase == Phase.Thinking) { phase = Phase.Listening; Action.Listen } else Action.None

    /** The owner taps the orb or says "থামো" while the assistant is thinking/speaking. */
    fun onInterrupt(): Action = if (phase == Phase.Speaking || phase == Phase.Thinking) { phase = Phase.Listening; Action.StopSpeaking } else Action.None

    fun onUserEnd(): Action = end("কল শেষ করা হয়েছে")
    fun onFailure(reason: String): Action = end(reason)

    private fun end(reason: String): Action { phase = Phase.Ended; return Action.End(reason) }

    companion object {
        private val endWords = listOf("কল শেষ", "কল বন্ধ", "বিদায়", "আল্লাহ হাফেজ", "আল্লাহ হাফিজ", "খোদা হাফেজ", "শেষ করো", "শেষ করুন", "বাই বাই")
        fun isEndPhrase(t: String) = endWords.any { t.contains(it) }
        /** "থামো"-type words while speaking mean "stop talking", not "end the call". */
        fun isInterruptPhrase(t: String) = VoiceCommands.parse(t).let { it == VoiceCommand.Stop || it == VoiceCommand.Pause }
    }
}

/** Turns streamed model text into speakable sentences and strips formatting that would be read out loud ("star star", "hash"). */
class SentenceBuffer {
    private val buf = StringBuilder()
    /** Feed a piece; returns complete sentences ready to speak (each already cleaned). */
    fun push(piece: String): List<String> {
        buf.append(piece); val out = ArrayList<String>()
        while (true) {
            val i = cutIndex() ?: break
            val s = clean(buf.substring(0, i + 1)); buf.delete(0, i + 1)
            if (s.isNotBlank()) out += s
        }
        return out
    }

    /** Index of the next sentence end, at least a short run in; a '.' between digits ("1.5") is not an end. */
    private fun cutIndex(): Int? {
        for (i in minOf(12, buf.length) until buf.length) {
            val c = buf[i]
            if (c !in ENDS) continue
            if (c == '.' && i > 0 && i + 1 < buf.length && buf[i - 1].isDigit() && buf[i + 1].isDigit()) continue
            if (c == '.' && i + 1 == buf.length) return null   // might still turn out to be a decimal point
            return i
        }
        return null
    }
    fun flush(): String? = clean(buf.toString()).also { buf.clear() }.ifBlank { null }

    companion object {
        private const val ENDS = "।.!?؟۔\n"
        fun clean(s: String) = s.replace(Regex("```.*?```", RegexOption.DOT_MATCHES_ALL), " কোড দেখুন পর্দায় ")
            .replace(Regex("https?://\\S+"), " ").replace(Regex("[*_#`>|~\\\\]+"), " ")
            .replace(Regex("^\\s*[-•]\\s+", RegexOption.MULTILINE), "").replace(Regex("\\s+"), " ").trim()
    }
}
