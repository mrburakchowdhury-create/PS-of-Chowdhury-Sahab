package app.personal.banglaai.speech

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Process-wide owner of the reader, so reading continues when the screen is off or the user leaves the document screen,
 * and the notification / headset buttons can control it. Only the app (a user press or a confirmed command) starts reading;
 * the notification offers pause/resume/stop only — it can never start new work. Status: compiles; device-test pending.
 */
object ReadAloudController {
    var state by mutableStateOf(TtsReader.State.Initialising); private set
    var current by mutableStateOf<Segment?>(null); private set
    private var reader: TtsReader? = null
    private var onPosition: ((ReadingPosition) -> Unit)? = null
    /** Called whenever state changes; the foreground service uses it to refresh the notification. */
    @Volatile var listener: (() -> Unit)? = null

    fun get(context: Context): TtsReader = reader ?: TtsReader(context.applicationContext,
        { state = it; listener?.invoke() },
        { seg, pos -> current = seg; pos?.let { p -> onPosition?.invoke(p) }; listener?.invoke() }).also { reader = it }

    fun setPositionSaver(f: ((ReadingPosition) -> Unit)?) { onPosition = f }

    fun pause() { reader?.pause() }
    fun resume() { reader?.resume() }
    fun stop() { reader?.stop() }
    fun next() { reader?.next() }
    fun previous() { reader?.previous() }
    val isPlaying get() = state == TtsReader.State.Playing

    fun release() { reader?.shutdown(); reader = null; state = TtsReader.State.Initialising; current = null }
}
