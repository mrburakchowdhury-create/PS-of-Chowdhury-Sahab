package app.personal.banglaai.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.personal.banglaai.ai.LocalLlm
import app.personal.banglaai.ai.Prompts
import app.personal.banglaai.doc.DocWorkflow
import app.personal.banglaai.speech.CallSpeaker
import app.personal.banglaai.speech.ReadAloudController
import app.personal.banglaai.speech.VoiceEngine
import app.personal.banglaai.speech.VoiceInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Languages offered for live interpreting (the on-device recogniser needs that language's offline pack installed on the phone). */
enum class LiveLang(val label: String, val tag: String, val promptName: String) {
    EN("ইংরেজি", "en-US", "ইংরেজি"), AR("আরবি", "ar-SA", "আরবি"), UR("উর্দু", "ur-PK", "উর্দু"), HI("হিন্দি", "hi-IN", "হিন্দি"), BN("বাংলা", "bn-BD", "বাংলা")
}

private data class Line(val original: String, var translated: String)

/**
 * Live (spoken) translation: tap start, speak; each sentence is recognised on-device, translated by the LOCAL model and shown (and read aloud when the
 * target is Bengali, using the phone's offline Bengali voice). The mic is open only while listening; the session ends after repeated silence, on the
 * stop button, or when the app leaves the screen. Nothing is recorded or sent anywhere. Status: device-test pending (recogniser language packs, speed).
 */
@Composable
fun LiveTranslateScreen(wf: DocWorkflow, cacheDir: File, onGoModel: () -> Unit = {}, onBack: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var lang by remember { mutableStateOf(LiveLang.EN) }
    var toBengali by remember { mutableStateOf(true) }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("শুরু করতে নিচের বাটন চাপুন") }
    var note by remember { mutableStateOf<String?>(null) }
    var typed by remember { mutableStateOf("") }
    var level by remember { mutableFloatStateOf(0f) }
    var voiceErr by remember { mutableStateOf<String?>(null) }
    var partial by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf("") }
    var checkTick by remember { mutableIntStateOf(0) }
    val lines = remember { mutableStateListOf<Line>() }
    val voice = remember { VoiceEngine.create(ctx, wf) }
    val llm = remember { arrayOfNulls<LocalLlm>(1) }
    var gen by remember { mutableIntStateOf(0) }
    var silences by remember { mutableIntStateOf(0) }
    var speaking by remember { mutableStateOf(false) }
    var typedOnly by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val nextRef = remember { arrayOfNulls<() -> Unit>(1) }
    val speaker = remember { CallSpeaker(ctx, { }, { }, { if (speaking) { speaking = false; nextRef[0]?.invoke() } }) }

    fun stopAll(msg: String?) { running = false; gen++; voice.stop(); speaker.stop(); llm[0]?.cancel(); level = 0f; status = "থেমে আছে"; note = msg }

    fun translate(text: String) {
        val my = ++gen; status = "অনুবাদ হচ্ছে…"
        val model = wf.usableModel() ?: run { stopAll("AI model নেই — উপরের লাল বাক্সে ‘Open AI Model’ চাপুন।"); return }
        lines += Line(text, ""); val idx = lines.lastIndex
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) {
                val m = llm[0] ?: LocalLlm(model, File(cacheDir, "llm")).also { llm[0] = it }
                val target = if (toBengali) "বাংলা" else lang.promptName
                m.ask(Prompts.INTERPRET_SYSTEM, Prompts.interpret(text, target)).trim()
            } }
            if (my != gen || !running) return@launch
            r.onFailure { stopAll("অনুবাদ ব্যর্থ: ${it.javaClass.simpleName}"); return@launch }
            val out = r.getOrDefault("").ifBlank { "(অনুবাদ আসেনি)" }
            lines[idx] = Line(text, out)
            if (typedOnly) { typedOnly = false; running = false; status = "থেমে আছে" }   // a typed line does not start the microphone
            if (toBengali && speaker.ready) { if (running) status = "🔊 বলছি…"; speaking = true; speaker.speak(out) } else nextRef[0]?.invoke()
        }
    }

    fun listenNext() {
        if (!running) return
        status = "🎤 শুনছি (${if (toBengali) lang.label else "বাংলা"})…"; voiceErr = null; partial = ""; stage = "starting"
        scope.launch {
            delay(350)
            if (!running) return@launch
            voice.listen({ t -> level = 0f; partial = ""; stage = ""; if (running) { silences = 0; translate(t) } }, { e ->
                level = 0f; partial = ""; stage = ""
                if (!running) return@listen
                if (e == VoiceInput.NOTHING_HEARD) { silences++; if (silences >= 4) stopAll("অনেকক্ষণ কথা শোনা যায়নি, তাই থেমেছে।") else listenNext() }
                else { voiceErr = e; status = "🎤 শোনা যাচ্ছে না — নিচে দেখুন" }   // stays open: the owner taps "Speak again" or types
            }, { level = it }, if (toBengali) lang.tag else "bn-BD", { partial = it }, { stage = it })
        }
    }
    nextRef[0] = { listenNext() }

    fun start() {
        if (wf.usableModel() == null) { note = "AI model নেই — উপরের লাল বাক্সে ‘Open AI Model’ চাপুন।"; checkTick++; return }   // checked BEFORE the mic opens: speaking a sentence only to be told "no model" is the worst order
        ReadAloudController.stop(); note = null; silences = 0; running = true
        if (!voice.available()) note = "এই ফোনে অফলাইন ভয়েস চেনা নেই — কথা শোনা যাবে না; নিচের ঘরে লিখে অনুবাদ করতে পারেন।"
        listenNext()
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> checkTick++; if (ok) start() else note = "মাইক্রোফোনের অনুমতি ছাড়া লাইভ অনুবাদ চলবে না — উপরের বাক্সে ‘Allow microphone’ চাপুন।" }
    val hasModel = remember(checkTick, running) { wf.usableModel() != null }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val w = (ctx as? android.app.Activity)?.window
        KeepAwake.acquire(ctx as? android.app.Activity)
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP && running) stopAll("অ্যাপ পর্দা থেকে সরে যাওয়ায় থেমেছে") }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); KeepAwake.release(ctx as? android.app.Activity); running = false; gen++; voice.stop(); speaker.shutdown(); llm[0]?.cancel(); llm[0]?.close() }
    }
    LaunchedEffect(lines.size, lines.lastOrNull()?.translated) { if (lines.isNotEmpty()) list.animateScrollToItem(lines.lastIndex) }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (running) stopAll(null); onBack() }) { Text("← Back") }
            Text("Live Translate", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        }
        if (!running) ReadinessPanel(wf, needModel = true, needMic = true, needHearing = true, needSpeaking = true, onGoModel = onGoModel, onChanged = { checkTick++ })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = toBengali, enabled = !running, onClick = { toBengali = true }, label = { Text("অন্য ভাষা → বাংলা") })
            FilterChip(selected = !toBengali, enabled = !running, onClick = { toBengali = false }, label = { Text("বাংলা → অন্য ভাষা") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LiveLang.entries.filter { it != LiveLang.BN }.forEach { l -> FilterChip(selected = lang == l, enabled = !running, onClick = { lang = l }, label = { Text(l.label) }) }
        }
        Text(status, style = MaterialTheme.typography.titleMedium, color = SkyBlue)
        if (running && level > 0f) LinearProgressIndicator(progress = { level }, Modifier.fillMaxWidth())
        if (partial.isNotBlank()) Text("শুনছি: $partial", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        note?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        voiceErr?.let { e -> Text("⚠️ $e", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium); if (running) Button(onClick = { listenNext() }) { Text("🎤 Speak again") } }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (lines.isEmpty()) item { Text("বক্তার কথা এখানে মূল ভাষায় ও অনুবাদে পাশাপাশি আসবে। অনুবাদ AI-এর, ভুল হতে পারে; গুরুত্বপূর্ণ বিষয়ে মূল লেখা মিলিয়ে নিন।", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(lines) { l ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    SelectionContainer { Text(l.original, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    SelectionContainer { Text(l.translated.ifBlank { "…" }, style = MaterialTheme.typography.titleMedium) }
                } }
            }
        }
        if (!running) Button(enabled = hasModel, modifier = Modifier.fillMaxWidth(), onClick = { if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) start() else micPerm.launch(Manifest.permission.RECORD_AUDIO) }) { Text("🎙️ Start live translation") }
        else Button(modifier = Modifier.fillMaxWidth(), onClick = { stopAll(null) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("⏹ Stop") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(typed, { typed = it }, label = { Text("Type instead of speaking") }, modifier = Modifier.weight(1f), maxLines = 3)
            Button(enabled = typed.isNotBlank(), onClick = { val t = typed; typed = ""; voice.stop(); if (!running) { running = true; typedOnly = true; silences = 0 }; translate(t) }) { Text("Translate") }
        }
    }
}
