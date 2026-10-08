package app.personal.banglaai.ui

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.personal.banglaai.doc.DocWorkflow
import app.personal.banglaai.speech.*
import app.personal.banglaai.ai.LocalLlm
import app.personal.banglaai.ai.Prompts
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.personal.banglaai.storage.VaultDoc

/**
 * Read-aloud + press-to-talk. Reading starts only on a button/confirmed command. TTS audio is never fed to the recognizer:
 * the mic is opened only by a press and speech is stopped before listening, so the app cannot command itself.
 * Status: compiles; behaviour on the S24 Ultra (offline Bengali voice present? quality? mic) is device-test pending.
 */
@Composable
fun ReaderSection(doc: VaultDoc, wf: DocWorkflow, totalPages: Int, initialCommand: VoiceCommand? = null) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { ctx.getSharedPreferences("reading", Context.MODE_PRIVATE).edit().clear().apply() }   // older versions kept the position unencrypted
    var rate by remember { mutableFloatStateOf(1.0f) }
    var useTranslation by remember { mutableStateOf(true) }
    var pageInput by remember { mutableStateOf("1") }
    var endInput by remember { mutableStateOf("") }   // optional "read up to this page"
    var transcript by remember { mutableStateOf("") }
    var cmd by remember { mutableStateOf<VoiceCommand?>(null) }
    var voiceMsg by remember { mutableStateOf<String?>(null) }
    var listening by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var explanation by remember { mutableStateOf<String?>(null) }
    var explaining by remember { mutableStateOf(false) }

    val reader = remember { ReadAloudController.get(ctx) }
    val state = ReadAloudController.state
    val current = ReadAloudController.current
    LaunchedEffect(doc.id) { ReadAloudController.setPositionSaver { wf.reading.put(doc.id, it.page, it.segment) } }
    val voice = remember { VoiceEngine.create(ctx, wf) }
    // Reading deliberately continues in the background (foreground service); the mic never does.
    DisposableEffect(Unit) { onDispose { voice.stop() } }
    var initialDone by remember { mutableStateOf(false) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun plan() = ReadingPlan({ p -> (if (useTranslation) wf.translations.get(doc.id, p) else wf.effective(doc.id, p))?.text }, endInput.toIntOrNull()?.coerceIn(1, totalPages) ?: totalPages)
    var translating by remember { mutableStateOf<Int?>(null) }
    val playRef = remember { arrayOfNulls<(Int) -> Unit>(1) }
    /** An English (or other-language) page has no Bengali yet: translate JUST this page with the local model, keep it as the page's translation, then read it. */
    fun translateThenPlay(page: Int) {
        val model = wf.usableModel() ?: run { voiceMsg = "এই পৃষ্ঠার বাংলা অনুবাদ নেই, আর AI model বসানো নেই। Home → AI Model থেকে model বসান, তারপর আবার চাপুন।"; return }
        val src = wf.effective(doc.id, page)
        if (src == null || src.text.isBlank()) { voiceMsg = "পৃষ্ঠা $page-এ বের-করা লেখা নেই (স্ক্যান হলে আগে OCR চালান)।"; return }
        translating = page; voiceMsg = null
        scope.launch {
            runCatching { withContext(kotlinx.coroutines.Dispatchers.IO) { LocalLlm(model, java.io.File(ctx.cacheDir, "llm")).use { it.ask(Prompts.TRANSLATE_SYSTEM, Prompts.translate(src.text, wf.glossary.relevantTo(src.text).map { g -> g.source to g.bengali })) } } }
                .onSuccess { out -> wf.translations.put(doc.id, app.personal.banglaai.doc.PageText(page, out.trim(), src.printedLabel)); translating = null; useTranslation = true; playRef[0]?.invoke(page) }
                .onFailure { translating = null; voiceMsg = "অনুবাদ ব্যর্থ: ${it.message ?: it.javaClass.simpleName}" }
        }
    }
    fun playNowImpl(page: Int, seg: Int) {
        val pl = plan(); val start = if (seg == 0) pl.startAt(page) else ReadingPosition(page, seg)
        val sample = start?.let { pl.segmentAt(it)?.text }
        if (!useTranslation && sample != null && !app.personal.banglaai.speech.SpeechText.isMostlyBengali(sample)) { voiceMsg = "এই মূল লেখা বাংলা নয় — বাংলা কণ্ঠ একে ঠিকমতো পড়তে পারে না। আগে 'বাংলায় অনুবাদ করুন' চালিয়ে ‘বাংলা অনুবাদ’ বেছে পড়ুন।"; return }
        if (start == null && useTranslation && seg == 0 && wf.effective(doc.id, page)?.text?.isNotBlank() == true) { translateThenPlay(page); return }
        if (start == null) voiceMsg = if (useTranslation) "এই পৃষ্ঠা থেকে পড়ার মতো লেখা নেই (স্ক্যান পৃষ্ঠা হলে আগে OCR চালান)।" else "এই পৃষ্ঠা থেকে পড়ার মতো বের-করা লেখা নেই (স্ক্যান পৃষ্ঠা হলে আগে OCR চালান)।" else {
            voiceMsg = null; ReadAloudController.play(ctx, pl, start)
            if (android.os.Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)   // only affects whether the controls are visible
            ReadAloudService.start(ctx)
        }
    }
    fun play(page: Int, seg: Int = 0) = playNowImpl(page, seg)
    playRef[0] = { p -> playNowImpl(p, 0) }
    fun explainNow() {
        val seg = ReadAloudController.current?.takeIf { !ReadAloudController.adHoc }?.text ?: plan().segments(pageInput.toIntOrNull() ?: 1).firstOrNull()?.text
        if (seg == null) { voiceMsg = "বোঝানোর মতো কোনো অংশ নেই — আগে পড়া শুরু করুন বা পৃষ্ঠা বাছুন"; return }
        val model = wf.usableModel() ?: run { voiceMsg = "কোনো AI মডেল বসানো নেই — আগে মডেল import করুন"; return }
        val pg = ReadAloudController.current?.takeIf { !ReadAloudController.adHoc }?.page ?: (pageInput.toIntOrNull() ?: 1)
        val pageCtx = (if (useTranslation) wf.translations.get(doc.id, pg) else wf.effective(doc.id, pg))?.text
        ReadAloudController.pause(); explaining = true; voiceMsg = null; explanation = null
        scope.launch {
            runCatching { withContext(kotlinx.coroutines.Dispatchers.IO) { LocalLlm(model, java.io.File(ctx.cacheDir, "llm")).use { it.ask(Prompts.EXPLAIN_SYSTEM, Prompts.explain(seg, pageCtx)) } } }
                .onSuccess { explanation = it; ReadAloudController.speakExplanation(ctx, it) }.onFailure { voiceMsg = it.message ?: it.javaClass.simpleName }
            explaining = false
        }
    }
    fun run(c: VoiceCommand) {
        when (c) {
            is VoiceCommand.ReadPage -> { pageInput = c.page.toString(); endInput = ""; play(c.page) }
            is VoiceCommand.ReadRange -> { pageInput = c.from.toString(); endInput = c.to.toString(); play(c.from) }
            VoiceCommand.Resume -> if (state == TtsReader.State.Paused) reader.resume() else {
                wf.reading.get(doc.id)?.let { play(it.first, it.second) } ?: play(pageInput.toIntOrNull() ?: 1) }
            VoiceCommand.Pause -> reader.pause(); VoiceCommand.Stop -> reader.stop()
            VoiceCommand.Slower -> { rate = (rate - 0.15f).coerceAtLeast(0.5f); reader.setRate(rate) }
            VoiceCommand.Faster -> { rate = (rate + 0.15f).coerceAtMost(2f); reader.setRate(rate) }
            VoiceCommand.Repeat -> reader.position()?.let { play(it.page, it.segment) }
            is VoiceCommand.ReadChapter -> {
                val ch = wf.chapters(doc)
                if (ch.isEmpty()) voiceMsg = "এই ফাইলে অধ্যায়-তালিকা (bookmark) পাওয়া যায়নি। পৃষ্ঠা নম্বর বলুন।"
                else ch.getOrNull(c.chapter - 1)?.let { pageInput = it.page.toString(); play(it.page) } ?: run { voiceMsg = "অধ্যায় ${c.chapter} নেই — মোট ${ch.size}টি অধ্যায় আছে" }
            }
            VoiceCommand.Explain -> explainNow()
            is VoiceCommand.OpenFile -> voiceMsg = "ফাইল খুলতে হোম পর্দায় ফিরে সেখানকার 🎤 ব্যবহার করুন।"
            else -> {}
        }
        cmd = null
    }
    fun startListening() {
        reader.pause()   // never listen while speaking: prevents the app hearing (and obeying) its own voice
        listening = true; voiceMsg = null
        voice.listen({ t -> listening = false; transcript = t; val c = VoiceCommands.parse(t); if (VoiceCommands.safeWithoutConfirm(c)) run(c) else cmd = c }, { e -> listening = false; voiceMsg = e })
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) startListening() else voiceMsg = "মাইক্রোফোনের অনুমতি ছাড়া ভয়েস কমান্ড চলবে না। বাটন/লেখা দিয়ে কাজ চালান।" }

    LaunchedEffect(initialCommand, state) {
        if (initialCommand != null && !initialDone && state != TtsReader.State.Initialising) { initialDone = true; run(initialCommand) }
    }
    explaining.let { if (it) Text("AI বুঝিয়ে বলার জন্য ভাবছে…", color = MaterialTheme.colorScheme.tertiary) }
    explanation?.let { e ->
        Text("সহজ বাংলায় ব্যাখ্যা (AI; 'সাধারণ ব্যাখ্যা' অংশ ডকুমেন্টের বক্তব্য নয়)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        androidx.compose.foundation.text.selection.SelectionContainer { Text(e) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { ReadAloudController.speakExplanation(ctx, e) }) { Text("🔊 Listen again") }
            OutlinedButton(onClick = { explanation = null; reader.stop() }) { Text("Close") }
        }
    }
    HorizontalDivider()
    Text("পড়ে শোনানো (অফলাইন বাংলা voice)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    when (state) {
        TtsReader.State.Initialising -> Text("ভয়েস ইঞ্জিন চালু হচ্ছে…")
        TtsReader.State.NoEngine -> Text("এই ফোনে কোনো text-to-speech ইঞ্জিন চালু নেই। সেটিংস → ভাষা → Text-to-speech।", color = MaterialTheme.colorScheme.error)
        TtsReader.State.NoOfflineBengali -> Text(
            (if (reader.onlyNetworkBengaliExists) "বাংলা voice আছে কিন্তু ইন্টারনেট লাগে — আপনার নিয়মে তা ব্যবহার করা হয়নি। " else "ফোনে বাংলা voice নেই। ") +
                "অফলাইন বাংলা voice নামান: সেটিংস → ভাষা → Text-to-speech → (Google) ইঞ্জিনের সেটিংস → ভয়েস ডেটা ইনস্টল → বাংলা। তারপর অ্যাপে ফিরে আসুন।", color = MaterialTheme.colorScheme.error)
        else -> {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = useTranslation, onClick = { useTranslation = true }, label = { Text("বাংলা অনুবাদ") })
                FilterChip(selected = !useTranslation, onClick = { useTranslation = false }, label = { Text("বের করা মূল লেখা") })
            }
            translating?.let { Text("⏳ পৃষ্ঠা $it বাংলায় অনুবাদ হচ্ছে… (মডেল প্রথমবার লোড হয়; কয়েক মিনিট লাগতে পারে — শেষ হলে নিজে পড়া শুরু হবে)", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(value = pageInput, onValueChange = { pageInput = it.filter(Char::isDigit).take(5) }, label = { Text("পৃষ্ঠা") }, singleLine = true, modifier = Modifier.width(110.dp))
                OutlinedTextField(value = endInput, onValueChange = { endInput = it.filter(Char::isDigit).take(5) }, label = { Text("পর্যন্ত") }, singleLine = true, modifier = Modifier.width(110.dp))
                Button(enabled = translating == null, onClick = { play(pageInput.toIntOrNull() ?: 1) }) { Text("▶ Read") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { reader.previous() }) { Text("⏮") }
                OutlinedButton(enabled = state == TtsReader.State.Playing, onClick = { reader.pause() }) { Text("⏸ Pause") }
                OutlinedButton(enabled = state == TtsReader.State.Paused, onClick = { reader.resume() }) { Text("▶ Resume") }
                OutlinedButton(onClick = { reader.next() }) { Text("⏭") }
                OutlinedButton(onClick = { reader.stop() }) { Text("⏹") }
            }
            Text("গতি: ${"%.2f".format(rate)}×", style = MaterialTheme.typography.bodySmall)
            Slider(value = rate, onValueChange = { rate = it; reader.setRate(it) }, valueRange = 0.5f..2f)
            current?.let { Text((if (ReadAloudController.adHoc) "ব্যাখ্যা শোনানো হচ্ছে:" else "এখন পড়া হচ্ছে — পৃষ্ঠা ${it.page}:"), style = MaterialTheme.typography.bodySmall); Surface(color = MaterialTheme.colorScheme.secondaryContainer) { Text(it.text, Modifier.padding(8.dp)) } }
            wf.reading.get(doc.id)?.let { Text("শেষ পড়ার অবস্থান: পৃষ্ঠা ${it.first}", style = MaterialTheme.typography.bodySmall) }
        }
    }
    voiceMsg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

    WordQuestion(doc, wf, current?.page ?: pageInput.toIntOrNull(), current?.text, ctx.cacheDir)

    Text("ভয়েস কমান্ড (চেপে বলুন; শুধু ফোনের ভেতরে চেনা হয়)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Button(enabled = !listening, onClick = { micPerm.launch(Manifest.permission.RECORD_AUDIO) }) { Text(if (listening) "🎤 শুনছি…" else "🎤 চেপে বলুন") }
        if (listening) OutlinedButton(onClick = { voice.stop(); listening = false }) { Text("মাইক বন্ধ") }
    }
    OutlinedTextField(value = transcript, onValueChange = { transcript = it; cmd = VoiceCommands.parse(it) }, label = { Text("কমান্ড (লিখেও দিতে পারেন / শোনা লেখা ঠিক করুন)") }, modifier = Modifier.fillMaxWidth())
    Button(enabled = transcript.isNotBlank(), onClick = { cmd = VoiceCommands.parse(transcript) }) { Text("কমান্ড বুঝে নিন") }
    cmd?.let { c ->
        val label = describe(c)
        Text("বোঝা গেছে: $label", style = MaterialTheme.typography.bodySmall)
        if (c !is VoiceCommand.Ambiguous && c !is VoiceCommand.Unknown && c !is VoiceCommand.OpenFile) Button(onClick = { run(c) }) { Text("Confirm — করুন") }
    }
}
