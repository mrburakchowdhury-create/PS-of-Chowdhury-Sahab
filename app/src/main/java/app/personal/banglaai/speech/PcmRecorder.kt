package app.personal.banglaai.speech

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import kotlin.math.sqrt

/**
 * Records ONE spoken sentence from the microphone (16 kHz mono 16-bit), ending by itself after a pause, and hands back only the part with speech (plus a short lead-in/out).
 * The audio stays in memory: nothing is written to disk and nothing is kept after [onDone]. The microphone is released on every path.
 */
class PcmRecorder(private val context: Context) {
    sealed interface End { data class Speech(val pcm: ShortArray) : End; data object NoSpeech : End; data class Failed(val reason: String) : End }

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var stopped = false
    private var thread: Thread? = null

    @SuppressLint("MissingPermission")   // the caller asks for RECORD_AUDIO first; a missing permission is reported as Failed
    fun start(onLevel: ((Float) -> Unit)?, onStage: (String) -> Unit, onDone: (End) -> Unit) {
        stopped = false
        thread = Thread {
            var rec: AudioRecord? = null
            var end: End = End.Failed("মাইক চালু করা যায়নি")
            try {
                val rate = 16000; val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                if (min <= 0) throw IllegalStateException("এই ফোনে মাইক-বাফার পাওয়া যায়নি")
                rec = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                    .setBufferSizeInBytes(maxOf(min, rate * 2)).build()
                if (rec.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("মাইক খোলা যায়নি (অনুমতি নেই, বা অন্য অ্যাপ মাইক ধরে আছে)")
                rec.startRecording()
                if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) throw IllegalStateException("মাইক শুরু হয়নি — অন্য অ্যাপ (যেমন কল/রেকর্ডার) মাইক ধরে রেখেছে কি না দেখুন")
                main.post { if (!stopped) onStage("ready") }
                val det = EndpointDetector(); val frame = ShortArray(320); val all = ArrayList<ShortArray>(); var lastLevelAt = 0L
                loop@ while (!stopped) {
                    var got = 0
                    while (got < frame.size && !stopped) { val n = rec.read(frame, got, frame.size - got); if (n <= 0) throw IllegalStateException("মাইক থেকে শব্দ আসা বন্ধ হয়েছে"); got += n }
                    if (stopped) break
                    all += frame.copyOf()
                    var sum = 0.0; for (s in frame) sum += s.toDouble() * s; val rms = sqrt(sum / frame.size)
                    val now = System.currentTimeMillis(); if (onLevel != null && now - lastLevelAt > 60) { lastLevelAt = now; val lv = (rms / 4000.0).toFloat().coerceIn(0f, 1f); main.post { if (!stopped) onLevel(lv) } }
                    when (det.feed(rms)) {
                        EndpointDetector.Event.SPEECH_STARTED -> main.post { if (!stopped) onStage("hearing") }
                        EndpointDetector.Event.SPEECH_ENDED, EndpointDetector.Event.TOO_LONG -> { end = End.Speech(trim(all, det.speechStartFrame)); break@loop }
                        EndpointDetector.Event.NO_SPEECH -> { end = End.NoSpeech; break@loop }
                        EndpointDetector.Event.NONE -> {}
                    }
                }
                if (stopped) return@Thread
            } catch (t: Throwable) { end = End.Failed(t.message ?: t.javaClass.simpleName) }
            finally { runCatching { rec?.stop() }; runCatching { rec?.release() } }
            val result = end
            main.post { if (!stopped) { onStage("processing"); onDone(result) } }
        }.also { it.isDaemon = true; it.start() }
    }

    /** From 300 ms before the speech began to the end (the trailing pause is cut to 300 ms). */
    private fun trim(frames: List<ShortArray>, startFrame: Int): ShortArray {
        val from = (startFrame - 15).coerceAtLeast(0)
        val total = frames.size
        var last = total - 1
        // drop the trailing silence beyond ~300 ms: scan back while the frame is quiet
        val keepTail = 15
        val body = frames.subList(from, total)
        val quietTail = body.asReversed().takeWhile { f -> var s = 0.0; for (x in f) s += x.toDouble() * x; sqrt(s / f.size) < 300 }.size
        last = total - 1 - (quietTail - keepTail).coerceAtLeast(0)
        val kept = frames.subList(from, last + 1)
        val out = ShortArray(kept.size * 320); kept.forEachIndexed { i, f -> f.copyInto(out, i * 320) }
        return out
    }

    fun stop() { stopped = true; thread?.interrupt() }
}
