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
    /** True while the app speaks its own explanation (not the document); the saved reading position is then left untouched. */
    var adHoc by mutableStateOf(false); private set
    /** Last page a plan can reach (document reading only) — the "N / M" in the live progress line. */
    var endPage by mutableStateOf<Int?>(null); private set
    /** Set once when a document plan runs off its own end naturally (not on manual pause/stop); the UI shows a "done, pages A–B" line and clears it. */
    var finishedRange by mutableStateOf<Pair<Int, Int>?>(null)
    private var reader: TtsReader? = null
    private var onPosition: ((ReadingPosition) -> Unit)? = null
    /** Called whenever state changes; the foreground service uses it to refresh the notification. */
    @Volatile var listener: (() -> Unit)? = null

    fun get(context: Context): TtsReader = reader ?: TtsReader(context.applicationContext,
        { state = it; listener?.invoke() },
        { seg, pos -> current = seg; if (!adHoc) pos?.let { p -> onPosition?.invoke(p) }; listener?.invoke() },
        { if (!adHoc) finishedRange = (startPage ?: 1) to (endPage ?: current?.page ?: 1) }).also { reader = it }

    private var startPage: Int? = null
    fun play(context: Context, plan: ReadingPlan, from: ReadingPosition) {
        adHoc = false; startPage = from.page; endPage = plan.pageCount; finishedRange = null
        get(context).play(plan, from)
    }

    /** Plays a plan that is NOT a document (e.g. literal reading of code the user pasted): never saved as a reading position. [from] = first page (= line number) to speak. */
    fun speakPlan(context: Context, plan: ReadingPlan, from: Int = 1) { adHoc = true; get(context).play(plan, plan.startAt(from) ?: return) }

    /** Speaks the app's own explanation text (the user asked for it); never saved as a reading position. */
    fun speakExplanation(context: Context, text: String) { adHoc = true; get(context).play(ReadingPlan({ SpeechText.clean(text) }, 1), ReadingPosition(1, 0)) }

    /**
     * Called by [app.personal.banglaai.security.AppLock] just BEFORE the vault key session ends. Default: reading stops. Only if the owner switched on
     * "keep reading when the screen locks" in Settings, a bounded copy of the pages still to be read is kept in memory for playback (the vault itself stays locked).
     */
    fun onVaultLock(context: Context) {
        val r = reader ?: return
        if (state != TtsReader.State.Playing && state != TtsReader.State.Paused) return
        if (adHoc) return   // the app's own explanation: its text is already in memory, nothing in the vault is involved
        val allow = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean(BG_READ_KEY, false)
        if (allow && r.freezeForBackground(BG_MAX_PAGES, BG_MAX_CHARS)) return
        r.stop()
    }
    const val BG_READ_KEY = "bg_read_ok"; const val BG_MAX_PAGES = 40; const val BG_MAX_CHARS = 400_000

    fun setPositionSaver(f: ((ReadingPosition) -> Unit)?) { onPosition = f }

    fun pause() { reader?.pause() }
    fun resume() { reader?.resume() }
    fun stop() { reader?.stop(); finishedRange = null }   // a manual stop is not "finished reading" — only running off the end naturally shows that line
    fun next() { reader?.next() }
    fun previous() { reader?.previous() }
    val isPlaying get() = state == TtsReader.State.Playing

    fun release() { reader?.shutdown(); reader = null; state = TtsReader.State.Initialising; current = null }
}
