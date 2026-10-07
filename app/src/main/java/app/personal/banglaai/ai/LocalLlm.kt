package app.personal.banglaai.ai

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File

/**
 * On-device text model (LiteRT-LM). Loaded on demand and released with [close] so it never sits in RAM together with
 * other models. No network is used; the model file must have been imported manually first.
 * Status: compiles; NOT yet run on a phone (device-test pending). The same model file was verified on a desktop (docs/MODEL_EVAL.md).
 */
class LocalLlm(private val modelFile: File, private val cacheDir: File) : AutoCloseable {
    private var engine: Engine? = null
    @Volatile private var current: com.google.ai.edge.litertlm.Conversation? = null

    @Synchronized fun load() {
        if (engine != null) return
        require(modelFile.exists()) { "মডেল ফাইল নেই — আগে মডেল import করুন" }
        // CPU is the safe default. GPU (much lower RAM and a little faster per the model card, measured on another phone) is opt-in via [preferGpu];
        // if the GPU engine fails to start we fall back to CPU and remember why in [lastGpuFailure] (shown on the phone-check screen).
        if (preferGpu) {
            val gpu = runCatching { Engine(EngineConfig(modelPath = modelFile.path, backend = Backend.GPU(), cacheDir = cacheDir.path)).also { it.initialize() } }
            gpu.onSuccess { engine = it; usedBackend = "GPU"; return }
            lastGpuFailure = gpu.exceptionOrNull()?.let { "${it.javaClass.simpleName}: ${it.message}" }
        }
        engine = Engine(EngineConfig(modelPath = modelFile.path, backend = Backend.CPU(), cacheDir = cacheDir.path)).also { it.initialize() }
        usedBackend = "CPU"
    }

    /** One independent request (fresh conversation, so chunks never leak into each other). Blocks; call off the main thread. */
    fun ask(system: String, prompt: String): String {
        load()
        val conv = engine!!.createConversation(ConversationConfig(systemInstruction = Contents.of(system)))
        current = conv
        try { return conv.sendMessage(prompt).toString() } finally { current = null; conv.close() }
    }

    /** Streaming answer: [onText] gets each new piece as the model writes it. Call from a background coroutine (loading the model can take many seconds). */
    suspend fun askStream(system: String, prompt: String, onText: (String) -> Unit) {
        load()
        val conv = engine!!.createConversation(ConversationConfig(systemInstruction = Contents.of(system)))
        current = conv
        try { conv.sendMessageAsync(prompt).collect { onText(it.toString()) } } finally { current = null; conv.close() }
    }

    val isLoaded get() = engine != null

    /** Stops the running generation (used by pause/cancel). */
    fun cancel() { current?.cancelProcess() }

    companion object {
        /** Set from the owner's setting at app start. Default false = CPU. */
        @Volatile var preferGpu = false
        @Volatile var lastGpuFailure: String? = null
        @Volatile var usedBackend: String = "-"
    }

    @Synchronized override fun close() { engine?.close(); engine = null }
}
