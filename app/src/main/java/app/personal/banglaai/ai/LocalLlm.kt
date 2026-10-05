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
        // CPU is the conservative default; GPU backend is a device-test item.
        engine = Engine(EngineConfig(modelPath = modelFile.path, backend = Backend.CPU(), cacheDir = cacheDir.path)).also { it.initialize() }
    }

    /** One independent request (fresh conversation, so chunks never leak into each other). Blocks; call off the main thread. */
    fun ask(system: String, prompt: String): String {
        load()
        val conv = engine!!.createConversation(ConversationConfig(systemInstruction = Contents.of(system)))
        current = conv
        try { return conv.sendMessage(prompt).toString() } finally { current = null; conv.close() }
    }

    /** Stops the running generation (used by pause/cancel). */
    fun cancel() { current?.cancelProcess() }

    @Synchronized override fun close() { engine?.close(); engine = null }
}
