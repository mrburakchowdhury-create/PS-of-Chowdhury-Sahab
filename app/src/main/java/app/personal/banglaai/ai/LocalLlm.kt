package app.personal.banglaai.ai

import android.os.Handler
import android.os.Looper
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.Semaphore

/**
 * On-device text model (LiteRT-LM). ONE engine is shared by the whole app: several screens used to load their own multi-GB copy at the same time,
 * which exhausts the phone's RAM (slowdown, app/phone restart) and re-paid the 10–60 s load on every request. Now the engine is loaded once, requests
 * are run one at a time, the context length is capped (smaller KV-cache), and the engine is freed after [IDLE_MS] of no use, when the app goes to the
 * background, or when Android asks for memory. [close] therefore only marks this user as done. Interactive requests (screens) are cancelled when the screen goes away or the app locks ([cancelInteractive]); background jobs ([background] = true, e.g. a long translation) keep running. No network is used.
 * Status: compiles; speed/RAM on the phone are device-test pending. The same model file was verified on a desktop (docs/MODEL_EVAL.md).
 */
class LocalLlm(private val modelFile: File, private val cacheDir: File, private val background: Boolean = false) : AutoCloseable {
    @Volatile private var current: com.google.ai.edge.litertlm.Conversation? = null

    private fun stillWanted(gen: Int) { if (!background && gen != Shared.interactiveGen) throw CancellationException("screen closed or app locked") }
    private fun engine(audio: Boolean = false, vision: Boolean = false): Engine = Shared.engine(modelFile, cacheDir, audio, vision)

    /** Loads (or reuses) the shared engine, taking its turn like a request so a memory-trim cannot free it half-way. Call off the main thread. */
    fun load() { Shared.turn.acquire(); try { engine() } finally { Shared.turn.release(); Shared.touch() } }

    /** One independent request (fresh conversation, so chunks never leak into each other). Blocks; call off the main thread. */
    fun ask(system: String, prompt: String): String {
        val gen = Shared.interactiveGen
        Shared.turn.acquire()
        try {
            stillWanted(gen)
            val eng = engine(); stillWanted(gen)
            val conv = eng.createConversation(ConversationConfig(systemInstruction = Contents.of(system)))
            current = conv; if (!background) Shared.activeInteractive = conv
            try { return conv.sendMessage(TokenBudget.guard(system, prompt)).toString() } finally { current = null; Shared.activeInteractive = null; conv.close() }
        } finally { Shared.turn.release(); Shared.touch() }
    }

    /** Streaming answer: [onText] gets each new piece as the model writes it. Call from a background coroutine (loading the model can take many seconds). */
    suspend fun askStream(system: String, prompt: String, onText: (String) -> Unit) {
        val gen = Shared.interactiveGen
        Shared.turn.acquire()
        try {
            currentCoroutineContext().ensureActive(); stillWanted(gen)
            val eng = engine(); currentCoroutineContext().ensureActive(); stillWanted(gen)
            val conv = eng.createConversation(ConversationConfig(systemInstruction = Contents.of(system)))
            current = conv; if (!background) Shared.activeInteractive = conv
            try { conv.sendMessageAsync(TokenBudget.guard(system, prompt)).collect { onText(it.toString()) } } finally { current = null; Shared.activeInteractive = null; conv.close() }
        } finally { Shared.turn.release(); Shared.touch() }
    }

    /**
     * Speech -> text with the model's own audio understanding (Gemma 4): [wav] is a complete 16 kHz mono 16-bit WAV file. Independent of the phone's speech service.
     * The engine is (re)loaded with the audio encoder on first use (a little more RAM than text only). Blocks; call off the main thread. Cancelled by [cancel].
     */
    fun transcribe(wav: ByteArray, instruction: String): String {
        val gen = Shared.interactiveGen
        Shared.turn.acquire()
        try {
            stillWanted(gen)
            val eng = engine(audio = true); stillWanted(gen)
            val conv = eng.createConversation(ConversationConfig())
            current = conv; if (!background) Shared.activeInteractive = conv
            try { return conv.sendMessage(Contents.of(Content.AudioBytes(wav), Content.Text(instruction))).toString() } finally { current = null; Shared.activeInteractive = null; conv.close() }
        } finally { Shared.turn.release(); Shared.touch() }
    }

    /**
     * One question about a picture (e.g. a screenshot the owner sent): [imageBytes] is a JPEG/PNG. [onText] streams the answer as it is written.
     * The engine is (re)loaded with the vision encoder on first use. Independent request (no shared history). Call from a background coroutine.
     */
    suspend fun askStreamWithImage(system: String, imageBytes: ByteArray, prompt: String, onText: (String) -> Unit) {
        val gen = Shared.interactiveGen
        Shared.turn.acquire()
        try {
            currentCoroutineContext().ensureActive(); stillWanted(gen)
            val eng = engine(vision = true); currentCoroutineContext().ensureActive(); stillWanted(gen)
            val conv = eng.createConversation(ConversationConfig(systemInstruction = Contents.of(system)))
            current = conv; if (!background) Shared.activeInteractive = conv
            try { conv.sendMessageAsync(Contents.of(Content.ImageBytes(imageBytes), Content.Text(TokenBudget.guard(system, prompt)))).collect { onText(it.toString()) } }
            finally { current = null; Shared.activeInteractive = null; conv.close() }
        } finally { Shared.turn.release(); Shared.touch() }
    }

    val isLoaded get() = Shared.loaded

    /** Stops the running generation (used by pause/cancel). */
    fun cancel() { current?.cancelProcess() }

    /** The shared engine stays loaded for reuse; it is freed by the idle timer / [releaseAll]. */
    override fun close() { Shared.touch() }

    companion object {
        /** Set from the owner's setting at app start. Default false = CPU. */
        @Volatile var preferGpu = false
        /** CPU threads the model may use. Fewer = cooler phone and less battery, slightly slower; all cores at 100% is what heated the phone. Set from Settings (default 4). */
        @Volatile var threads = 2   // default is the cool/low-battery setting; the owner can pick মাঝারি(4)/দ্রুত(6) in Phone check for faster but warmer answers
        @Volatile var lastGpuFailure: String? = null
        @Volatile var usedBackend: String = "-"
        const val IDLE_MS = 90_000L   // give the model's RAM back quickly: it is idle most of the time
        const val MAX_TOKENS = 4096

        /** Frees the model's RAM (app in background, memory pressure, or the owner asked) — only when no request is running; done off the main thread. */
        fun releaseAll() = Shared.releaseWhenIdle()

        /** Stops every screen-owned request now (running or still waiting for its turn). Called when a screen closes or the app locks. Background jobs are not affected. */
        fun cancelInteractive() { Shared.interactiveGen++; Shared.activeInteractive?.cancelProcess() }
    }

    private object Shared {
        val turn = Semaphore(1, true)
        @Volatile var interactiveGen = 0
        @Volatile var activeInteractive: com.google.ai.edge.litertlm.Conversation? = null
        private var eng: Engine? = null
        private var path: String? = null
        private var withAudio = false
        private var withVision = false
        private val main = Handler(Looper.getMainLooper())
        private val idle = Runnable { releaseWhenIdle(retryLater = true) }
        val loaded get() = eng != null

        @Synchronized fun engine(modelFile: File, cacheDir: File, audio: Boolean = false, vision: Boolean = false): Engine {
            // an engine that already has the audio/vision encoder serves plain-text requests too; one missing a needed encoder is replaced when first needed
            eng?.let { if (path == modelFile.path && (withAudio || !audio) && (withVision || !vision)) { touch(); return it } else { runCatching { it.close() }; eng = null } }
            require(modelFile.exists()) { "মডেল ফাইল নেই — আগে মডেল import করুন" }
            cacheDir.mkdirs()
            // CPU is the safe default. GPU (lower RAM) is opt-in via [preferGpu]; if it fails to start we fall back to CPU and remember why.
            if (preferGpu) {
                val gpu = runCatching { Engine(EngineConfig(modelPath = modelFile.path, backend = Backend.GPU(), audioBackend = if (audio) Backend.CPU(threadCount = threads) else null, visionBackend = if (vision) Backend.CPU(threadCount = threads) else null, maxNumTokens = MAX_TOKENS, cacheDir = cacheDir.path)).also { it.initialize() } }
                gpu.onSuccess { eng = it; path = modelFile.path; withAudio = audio; withVision = vision; usedBackend = "GPU"; touch(); return it }
                lastGpuFailure = gpu.exceptionOrNull()?.let { "${it.javaClass.simpleName}: ${it.message}" }
            }
            val e = Engine(EngineConfig(modelPath = modelFile.path, backend = Backend.CPU(threadCount = threads), audioBackend = if (audio) Backend.CPU(threadCount = threads) else null, visionBackend = if (vision) Backend.CPU(threadCount = threads) else null, maxNumTokens = MAX_TOKENS, cacheDir = cacheDir.path)).also { it.initialize() }
            eng = e; path = modelFile.path; withAudio = audio; withVision = vision; usedBackend = "CPU"; touch(); return e
        }

        fun touch() { main.removeCallbacks(idle); if (eng != null) main.postDelayed(idle, IDLE_MS) }

        /** Never frees the engine while a request or a load is running, and never on the calling (possibly main) thread: closing a 3.4 GB engine takes time. */
        fun releaseWhenIdle(retryLater: Boolean = false) {
            if (turn.tryAcquire()) Thread { try { release() } finally { turn.release() } }.start() else if (retryLater) touch()
        }
        @Synchronized fun release() { main.removeCallbacks(idle); runCatching { eng?.close() }; eng = null; path = null; withAudio = false; withVision = false }
    }
}
