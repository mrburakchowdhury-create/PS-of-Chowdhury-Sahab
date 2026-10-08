package app.personal.banglaai.speech

/**
 * Decides, from the loudness (RMS of 16-bit samples) of consecutive 20 ms frames, when a spoken sentence starts and ends — so one tap records exactly one sentence and
 * silence alone never reaches the model (a model asked to transcribe silence invents words). Pure logic, unit-tested.
 * The first 300 ms calibrate the room's noise level; speech must be clearly above it (3×, and never below [minThreshold]).
 */
class EndpointDetector(
    private val frameMs: Int = 20, private val waitForSpeechMs: Int = 8_000, private val trailingSilenceMs: Int = 1_400,
    private val maxMs: Int = 28_000, private val minSpeechMs: Int = 240, private val minThreshold: Double = 300.0,
) {
    enum class Event { NONE, SPEECH_STARTED, SPEECH_ENDED, NO_SPEECH, TOO_LONG }

    private var frames = 0; private val calibration = ArrayList<Double>()
    private var threshold = minThreshold
    private var run = 0; private var speaking = false
    private var speechFrames = 0; private var silentRun = 0
    val elapsedMs get() = frames * frameMs
    var speechStartFrame = -1; private set

    private var finished = false

    /** One terminal event (ended / no speech / too long) is reported once; after that the detector stays quiet. */
    fun feed(rms: Double): Event = step(rms).also { if (it == Event.SPEECH_ENDED || it == Event.NO_SPEECH || it == Event.TOO_LONG) finished = true }

    private fun step(rms: Double): Event {
        if (finished) return Event.NONE
        frames++
        if (calibration.size < CALIBRATION_FRAMES && !speaking) {
            calibration += rms
            if (calibration.size == CALIBRATION_FRAMES) threshold = (calibration.sorted()[CALIBRATION_FRAMES / 2] * 3.0).coerceIn(minThreshold, MAX_THRESHOLD)   // capped: someone who starts speaking at once must not make the room "loud"
            else return limits()
            // the calibration window itself may already hold speech (the user spoke at once): treat the loud frames normally below
            if (rms <= threshold) return limits()
        }
        val loud = rms > threshold
        if (!speaking) {
            run = if (loud) run + 1 else 0
            if (run >= START_FRAMES) { speaking = true; speechStartFrame = frames - START_FRAMES; speechFrames = START_FRAMES; silentRun = 0; return Event.SPEECH_STARTED }
            return limits()
        }
        if (loud) { speechFrames++; silentRun = 0 } else silentRun++
        if (silentRun * frameMs >= trailingSilenceMs && speechFrames * frameMs >= minSpeechMs) return Event.SPEECH_ENDED
        if (elapsedMs >= maxMs) return Event.TOO_LONG
        return Event.NONE
    }

    private fun limits(): Event = if (!speaking && elapsedMs >= waitForSpeechMs) Event.NO_SPEECH else Event.NONE

    companion object { const val CALIBRATION_FRAMES = 15; const val START_FRAMES = 3; const val MAX_THRESHOLD = 900.0 }
}
