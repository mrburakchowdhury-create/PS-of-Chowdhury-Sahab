package app.personal.banglaai.ai

import android.os.Handler
import android.os.Looper
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File
import java.util.concurrent.Semaphore

/**
 * On-device text model (LiteRT-LM). ONE engine is shared by the whole app: several screens used to load their own multi-GB copy at the same time,
 * which exhausts the phone's RAM (slowdown, app/phone restart) and re-paid the 10–60 s load on every request. Now the engine is loaded once, requests
 * are run one at a time, the context length is capped (smaller KV-cache), and the engine is freed after [IDLE_MS] of no use, when the app goes to the
 * background, or when Android asks for memory. [close] therefore only marks this user as done. No network is used.
 * Status: compiles; speed/RAM on the phone are device-test pending. The same model file was verified on a desktop (docs/MODEL_EVAL.md).
 */
class LocalLlm(private val modelFile: File, private val cacheDir: File) : AutoCloseable {
    @Volatile private var current: com.google.ai.edge.litertlm.Conversation? = null

    /** Loads (or reuses) the shared engine. Call off the main thread. */
    fun load() { engine() }
    private fun engine(): Engine = Shared.engine(modelFile, cacheDir)

    /** One independent request (fresh conversation, so chunks never leak into each other). Blocks; call off the main thread. */
    fun ask(system: String, prompt: String): String {
        Shared.turn.acquire()
        try {
            val conv = engine().createConversation(ConversationConfig(systemInstruction = Contents.of(system)))
            current = conv
            try { return conv.sendMessage(prompt).toString() } finally { current = null; conv.close() }
        } finally { Shared.turn.release(); Shared.touch() }
    }

    /** Streaming answer: [onText] gets each new piece as the model writes it. Call from a background coroutine (loading the model can take many seconds). */
    suspend fun askStream(system: String, prompt: String, onText: (String) -> Unit) {
        Shared.turn.acquire()
        try {
            val conv = engine().createConversation(ConversationConfig(systemInstruction = Contents.of(system)))
            current = conv
            try { conv.sendMessageAsync(prompt).collect { onText(it.toString()) } } finally { current = null; conv.close() }
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
        @Volatile var lastGpuFailure: String? = null
        @Volatile var usedBackend: String = "-"
        const val IDLE_MS = 4 * 60_000L
        const val MAX_TOKENS = 4096

        /** Frees the model's RAM now (app in background, memory pressure, or the owner asked). */
        fun releaseAll() = Shared.releaseWhenIdle()
    }

    private object Shared {
        val turn = Semaphore(1)
        private var eng: Engine? = null
        private var path: String? = null
        private val main = Handler(Looper.getMainLooper())
        private val idle = Runnable { if (turn.tryAcquire()) try { release() } finally { turn.release() } else touch() }
        val loaded get() = eng != null

        @Synchronized fun engine(modelFile: File, cacheDir: File): Engine {
            eng?.let { if (path == modelFile.path) { touch(); return it } else { runCatching { it.close() }; eng = null } }
            require(modelFile.exists()) { "মডেল ফাইল নেই — আগে মডেল import করুন" }
            cacheDir.mkdirs()
            // CPU is the safe default. GPU (lower RAM) is opt-in via [preferGpu]; if it fails to start we fall back to CPU and remember why.
            if (preferGpu) {
                val gpu = runCatching { Engine(EngineConfig(modelPath = modelFile.path, backend = Backend.GPU(), maxNumTokens = MAX_TOKENS, cacheDir = cacheDir.path)).also { it.initialize() } }
                gpu.onSuccess { eng = it; path = modelFile.path; usedBackend = "GPU"; touch(); return it }
                lastGpuFailure = gpu.exceptionOrNull()?.let { "${it.javaClass.simpleName}: ${it.message}" }
            }
            val e = Engine(EngineConfig(modelPath = modelFile.path, backend = Backend.CPU(), maxNumTokens = MAX_TOKENS, cacheDir = cacheDir.path)).also { it.initialize() }
            eng = e; path = modelFile.path; usedBackend = "CPU"; touch(); return e
        }

        fun touch() { main.removeCallbacks(idle); if (eng != null) main.postDelayed(idle, IDLE_MS) }
        /** Never frees the engine while a request is running. */
        fun releaseWhenIdle() { if (turn.tryAcquire()) try { release() } finally { turn.release() } }
        @Synchronized fun release() { main.removeCallbacks(idle); runCatching { eng?.close() }; eng = null; path = null }
    }
}
