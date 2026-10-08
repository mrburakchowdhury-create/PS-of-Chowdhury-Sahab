package app.personal.banglaai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.personal.banglaai.ai.LocalLlm
import app.personal.banglaai.ai.Prompts
import app.personal.banglaai.doc.DocWorkflow
import app.personal.banglaai.speech.ReadAloudController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class Msg(val fromUser: Boolean, val text: String)

/**
 * Direct assistant: type (or press-to-talk) a question, or switch to "অনুবাদ" and paste any text to get Bengali. Runs ONLY on the imported local model:
 * if none is imported it says so and shows how. The conversation lives in memory only (nothing saved, nothing sent anywhere).
 * Each message is independent (no hidden long memory); answers stream in as the model writes them.
 */
@Composable
fun ChatScreen(wf: DocWorkflow, cacheDir: File, startWith: String?, onGoModel: () -> Unit = {}, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val msgs = remember { mutableStateListOf<Msg>() }
    var input by remember { mutableStateOf("") }
    var translate by remember { mutableStateOf(false) }
    var advisor by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val llm = remember { arrayOfNulls<LocalLlm>(1) }
    var stoppedByUser by remember { mutableStateOf(false) }
    // microphone: one press = one sentence, written into the question box (so it can be corrected before sending)
    val voice = remember { app.personal.banglaai.speech.VoiceEngine.create(ctx, wf) }
    var listening by remember { mutableStateOf(false) }; var micMsg by remember { mutableStateOf<String?>(null) }; var heardPartial by remember { mutableStateOf("") }
    DisposableEffect(Unit) { onDispose { voice.stop() } }
    fun listen() {
        ReadAloudController.pause(); listening = true; micMsg = null; heardPartial = ""
        voice.listen({ t -> listening = false; heardPartial = ""; input = t }, { e -> listening = false; heardPartial = ""; micMsg = if (e == app.personal.banglaai.speech.VoiceInput.NOTHING_HEARD) "কিছু শোনা যায়নি — আবার চেপে বলুন" else e },
            null, "bn-BD", { heardPartial = it; input = it }, null)
    }
    val micPerm = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok -> if (ok) listen() else micMsg = "মাইক্রোফোনের অনুমতি দেওয়া হয়নি — লিখে প্রশ্ন করুন" }
    var checkTick by remember { mutableIntStateOf(0) }
    val list = rememberLazyListState()
    DisposableEffect(Unit) { onDispose { llm[0]?.cancel(); llm[0]?.close(); } }   // free the model's RAM when leaving

    fun send(text: String) {
        if (busy != null || text.isBlank()) return
        val model = wf.usableModel() ?: run { error = "কোনো AI মডেল বসানো নেই। হোমে ফিরে 'মডেল ফাইল বসান' চাপুন (E4B ≈ 3.66 GB, E2B ≈ 2.59 GB, docs/MODELS.md)। মডেল ছাড়া উত্তর/অনুবাদ হয় না, আর ইন্টারনেটে কিছু পাঠানো হয় না।"; return }
        error = null; msgs += Msg(true, text.trim()); msgs += Msg(false, ""); input = ""
        val idx = msgs.lastIndex
        busy = if (llm[0]?.isLoaded == true) "লিখছে…" else "মডেল লোড হচ্ছে (প্রথমবার কিছু সময় লাগে, ফোনভেদে)…"
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val m = llm[0] ?: LocalLlm(model, File(cacheDir, "llm")).also { llm[0] = it }
                    val sys = if (translate) Prompts.TRANSLATE_SYSTEM else if (advisor) Prompts.ADVISOR_SYSTEM else Prompts.ASSISTANT_SYSTEM
                    val prompt = if (translate) Prompts.translateFree(text) else if (advisor) Prompts.advisor(text, app.personal.banglaai.knowledge.KnowledgeBase.search(ctx, text, null, 3).map { "প্রশ্ন: ${it.q}\nউত্তর: ${it.a}" }) else Prompts.ask(text)
                    var first = true
                    m.askStream(sys, prompt) { piece -> if (first) { first = false; busy = "লিখছে…" }; msgs[idx] = Msg(false, msgs[idx].text + piece) }
                }
            }.onFailure { error = if (stoppedByUser) null else "ব্যর্থ: ${it.javaClass.simpleName}: ${it.message ?: ""}".take(400) }
            stoppedByUser = false
            if (msgs.getOrNull(idx)?.text.isNullOrBlank() && error == null) msgs[idx] = Msg(false, "(কোনো উত্তর আসেনি)")
            busy = null
        }
    }
    LaunchedEffect(Unit) { if (!startWith.isNullOrBlank()) send(startWith) }
    LaunchedEffect(msgs.size, msgs.lastOrNull()?.text?.length) { if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.lastIndex) }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("Assistant", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        }
        if (wf.usableModel() == null || checkTick < 0) ReadinessPanel(wf, needModel = true, needMic = false, needHearing = false, needSpeaking = false, onGoModel = onGoModel, onChanged = { checkTick++ })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !translate && !advisor, onClick = { translate = false; advisor = false }, label = { Text("প্রশ্ন / কথা") })
            FilterChip(selected = advisor, onClick = { translate = false; advisor = true }, label = { Text("পরামর্শদাতা") })
            FilterChip(selected = translate, onClick = { translate = true; advisor = false }, label = { Text("অনুবাদ → বাংলা") })
        }
        Text(if (translate) "যেকোনো ভাষার লেখা পেস্ট করুন; শুধু বাংলা অনুবাদ আসবে।" else "যা জানতে চান লিখুন। AI ভুল করতে পারে; ধর্মীয়/আইনি/চিকিৎসা বিষয়ে নিজে যাচাই করুন।",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (msgs.isEmpty()) item { Text("এখানে লিখে পাঠান। মডেল বসানো না থাকলে অ্যাপ জানিয়ে দেবে।", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(msgs.size) { i ->
                val m = msgs[i]
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (m.fromUser) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(if (m.fromUser) "আপনি" else "সহকারী", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        SelectionContainer { Text(m.text.ifBlank { "…" }) }
                        if (!m.fromUser && m.text.isNotBlank() && busy == null) TextButton(onClick = { ReadAloudController.speakExplanation(ctx, m.text) }) { Text("🔊 Listen") }
                    }
                }
            }
        }
        busy?.let { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text(it, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); OutlinedButton(onClick = { stoppedByUser = true; llm[0]?.cancel() }) { Text("Stop") } } }
        error?.let { Text("❌ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, label = { Text(if (translate) "অনুবাদের লেখা" else "প্রশ্ন") }, modifier = Modifier.weight(1f), maxLines = 5)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(enabled = busy == null && input.isNotBlank(), onClick = { send(input) }) { Text("Send") }
                OutlinedButton(enabled = busy == null, onClick = { if (listening) { voice.stop(); listening = false } else if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) listen() else micPerm.launch(android.Manifest.permission.RECORD_AUDIO) }) { Text(if (listening) "⏹ Stop" else "🎤 Speak") }
            }
        }
        if (listening) Text(if (heardPartial.isBlank()) "🎤 শুনছি… বলুন" else "🎤 $heardPartial", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        micMsg?.let { Text("⚠️ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}
