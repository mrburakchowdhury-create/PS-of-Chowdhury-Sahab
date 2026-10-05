package app.personal.banglaai.speech

/** What the user probably meant. The UI always shows the transcript and this reading, and asks for a tap before anything but Pause/Stop. */
sealed interface VoiceCommand {
    data class ReadPage(val page: Int) : VoiceCommand
    data object Resume : VoiceCommand
    data object Pause : VoiceCommand
    data object Stop : VoiceCommand
    data object Slower : VoiceCommand
    data object Faster : VoiceCommand
    data object Repeat : VoiceCommand
    data class Ambiguous(val reason: String) : VoiceCommand
    data class Unknown(val transcript: String) : VoiceCommand
}

/**
 * Rule-based Bengali command reader. Deliberately small: it understands only phrasings listed in tests. Anything else is
 * Unknown — never guessed. Bengali digits (০-৯) and ASCII digits are accepted; number *words* (যেমন "বিশ") are NOT supported yet,
 * so they produce Ambiguous rather than a wrong page.
 */
object VoiceCommands {
    private val digitMap = "০১২৩৪৫৬৭৮৯".withIndex().associate { it.value to it.index.toString()[0] }
    fun normalizeDigits(s: String) = s.map { digitMap[it] ?: it }.joinToString("")

    fun parse(transcript: String): VoiceCommand {
        val t = normalizeDigits(transcript.trim().lowercase())
        if (t.isBlank()) return VoiceCommand.Unknown(transcript)
        val numbers = Regex("\\d+").findAll(t).map { it.value }.toList()
        return when {
            listOf("থামো", "থামুন", "বন্ধ করো", "বন্ধ করুন", "স্টপ", "stop").any { t.contains(it) } -> VoiceCommand.Stop
            listOf("পজ", "একটু থামো", "pause").any { t.contains(it) } -> VoiceCommand.Pause
            (t.contains("আগের জায়গা") || t.contains("আবার পড়ো") && numbers.isEmpty() || t.contains("চালিয়ে যাও") || t.contains("resume")) -> VoiceCommand.Resume
            t.contains("আস্তে") -> VoiceCommand.Slower
            t.contains("জোরে পড়") || t.contains("দ্রুত") -> VoiceCommand.Faster
            t.contains("আবার পড়ো") || t.contains("repeat") -> VoiceCommand.Repeat
            t.contains("পৃষ্ঠা") || t.contains("পাতা") || t.contains("page") -> when {
                numbers.size == 1 -> VoiceCommand.ReadPage(numbers[0].toIntOrNull()?.takeIf { it in 1..99999 } ?: return VoiceCommand.Ambiguous("পৃষ্ঠা নম্বর বোঝা যায়নি"))
                numbers.size > 1 -> VoiceCommand.Ambiguous("একাধিক সংখ্যা শোনা গেছে: ${numbers.joinToString(", ")}")
                else -> VoiceCommand.Ambiguous("পৃষ্ঠার নম্বর বলা হয়নি (সংখ্যা অঙ্কে বলুন/লিখুন)")
            }
            else -> VoiceCommand.Unknown(transcript)
        }
    }

    /** Only these may run without a confirming tap (they reduce activity). */
    fun safeWithoutConfirm(c: VoiceCommand) = c is VoiceCommand.Stop || c is VoiceCommand.Pause
}
