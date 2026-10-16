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
    var askText by remember { mutableStateOf("") }
    var commentText by remember { mutableStateOf("") }
    var commentSaved by remember { mutableStateOf(false) }
    var summaryEveryN by remember { mutableStateOf<Int?>(null) }   // null = off
    var summaryAtChapterEnd by remember { mutableStateOf(false) }
    var summaryFromPage by remember { mutableStateOf<Int?>(null) }   // first page not yet covered by a summary
    var summaryText by remember { mutableStateOf<String?>(null) }
    var summarizing by remember { mutableStateOf(false) }

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
            runCatching { withContext(kotlinx.coroutines.Dispatchers.IO) { LocalLlm(model, java.io.File(ctx.cacheDir, "llm")).use { val tg = wf.translationTarget(doc); it.ask(Prompts.translateSystem(tg), Prompts.translate(src.text, wf.glossary.promptPairs(src.text), tg)) } } }
                .onSuccess { out -> wf.translations.put(doc.id, app.personal.banglaai.doc.PageText(page, out.trim(), src.printedLabel)); translating = null; useTranslation = true; playRef[0]?.invoke(page) }
                .onFailure { translating = null; voiceMsg = "অনুবাদ ব্যর্থ: ${it.message ?: it.javaClass.simpleName}" }
        }
    }
    fun playNowImpl(page: Int, seg: Int) {
        val pl = plan(); val start = if (seg == 0) pl.startAt(page) else ReadingPosition(page, seg)
        val sample = start?.let { pl.segmentAt(it)?.text }
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
                .onSuccess { val t = app.personal.banglaai.OwnerAddress.present(it); explanation = t; ReadAloudController.speakExplanation(ctx, t) }.onFailure { voiceMsg = it.message ?: it.javaClass.simpleName }
            explaining = false
        }
    }
    fun askNow() {
        val q = askText.trim(); if (q.isEmpty()) return
        val seg = ReadAloudController.current?.takeIf { !ReadAloudController.adHoc }?.text ?: plan().segments(pageInput.toIntOrNull() ?: 1).firstOrNull()?.text ?: ""
        val model = wf.usableModel() ?: run { voiceMsg = "এই কাজের জন্য AI মডেল প্রয়োজন। মডেল যোগ করুন।"; return }
        val pg = ReadAloudController.current?.takeIf { !ReadAloudController.adHoc }?.page ?: (pageInput.toIntOrNull() ?: 1)
        val pageCtx = (if (useTranslation) wf.translations.get(doc.id, pg) else wf.effective(doc.id, pg))?.text
        ReadAloudController.pause(); explaining = true; voiceMsg = null; explanation = null
        scope.launch {
            runCatching { withContext(kotlinx.coroutines.Dispatchers.IO) { LocalLlm(model, java.io.File(ctx.cacheDir, "llm")).use { it.ask(Prompts.ASK_ABOUT_SYSTEM, Prompts.askAbout(seg, pageCtx, q)) } } }
                .onSuccess { val t = app.personal.banglaai.OwnerAddress.present(it); explanation = t; askText = ""; ReadAloudController.speakExplanation(ctx, t) }.onFailure { voiceMsg = it.message ?: it.javaClass.simpleName }
            explaining = false
        }
    }
    fun gatherText(from: Int, to: Int): String {
        val sb = StringBuilder()
        for (p in from..to) ((if (useTranslation) wf.translations.get(doc.id, p) else wf.effective(doc.id, p))?.text)?.let { if (it.isNotBlank()) sb.append(it).append("\n") }
        return sb.toString()
    }
    fun runAutoSummary(from: Int, to: Int, chapterEnd: Boolean) {
        val model = wf.usableModel() ?: return   // no model installed: skip quietly, reading is not interrupted for this
        val text = gatherText(from, to).take(6000)
        summaryFromPage = to + 1
        if (text.isBlank()) return
        summarizing = true; voiceMsg = null
        reader.pause()
        scope.launch {
            runCatching { withContext(kotlinx.coroutines.Dispatchers.IO) { LocalLlm(model, java.io.File(ctx.cacheDir, "llm")).use { it.ask(Prompts.SUMMARY_SYSTEM, Prompts.summary(text, chapterEnd)) } } }
                .onSuccess { val t = app.personal.banglaai.OwnerAddress.present(it); summaryText = t; ReadAloudController.speakExplanation(ctx, t) }
                .onFailure { voiceMsg = "সারাংশ ব্যর্থ: ${it.message ?: it.javaClass.simpleName}" }
            summarizing = false
        }
    }
    var lastSeenPage by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(current?.page, current?.index) {
        val c = current
        if (c != null && !ReadAloudController.adHoc && c.index == 0) {
            val prev = lastSeenPage; lastSeenPage = c.page
            if (summaryFromPage == null) summaryFromPage = c.page
            if (prev != null && prev != c.page) {
                val from = summaryFromPage ?: prev
                val chapterBoundary = summaryAtChapterEnd && wf.chapters(doc).any { it.page == c.page }
                val everyNHit = summaryEveryN?.let { n -> n > 0 && (c.page - from) >= n } ?: false
                if (chapterBoundary || everyNHit) runAutoSummary(from, prev, chapterBoundary)
            }
        }
    }
    /**
     * "Paused" (TTS engine literally paused mid-document) resumes in place; any other state (Ready, e.g. right after an
     * Explain/Ask/auto-summary interruption replaced the engine's in-memory position) falls back to the last SAVED
     * document reading position, never a no-op. Shared by the voice command and the on-screen button so both behave alike.
     */
    fun resumeReading() {
        if (state == TtsReader.State.Paused) reader.resume()
        else wf.reading.get(doc.id)?.let { play(it.first, it.second) } ?: play(pageInput.toIntOrNull() ?: 1)
    }
    fun run(c: VoiceCommand) {
        when (c) {
            is VoiceCommand.ReadPage -> { pageInput = c.page.toString(); endInput = ""; play(c.page) }
            is VoiceCommand.ReadRange -> { pageInput = c.from.toString(); endInput = c.to.toString(); play(c.from) }
            VoiceCommand.Resume -> resumeReading()
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
    summarizing.let { if (it) Text("পড়া অংশের সারাংশ বানাচ্ছে…", color = MaterialTheme.colorScheme.tertiary) }
    summaryText?.let { s ->
        Text("স্বয়ংক্রিয় সারাংশ (যা পড়া হয়েছে তার ভিত্তিতে)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        androidx.compose.foundation.text.selection.SelectionContainer { Text(s) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { ReadAloudController.speakExplanation(ctx, s) }) { Text("🔊 Listen again") }
            OutlinedButton(onClick = { summaryText = null }) { Text("Close") }
        }
    }
    explaining.let { if (it) Text("AI বুঝিয়ে বলার জন্য ভাবছে…", color = MaterialTheme.colorScheme.tertiary) }
    explanation?.let { e ->
        Text("AI-র ব্যাখ্যা/উত্তর ('সাধারণ ব্যাখ্যা' অংশ ডকুমেন্টের বক্তব্য নয়)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
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
                FilterChip(selected = useTranslation, onClick = { useTranslation = true }, label = { Text("${wf.translationTarget(doc).label} অনুবাদ") })
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
                OutlinedButton(onClick = { reader.pause() }) { Text("⏸ Pause") }
                OutlinedButton(onClick = { resumeReading() }) { Text("▶ Resume") }
                OutlinedButton(onClick = { reader.next() }) { Text("⏭") }
                OutlinedButton(onClick = { reader.stop() }) { Text("⏹") }
            }
            Text("গতি: ${"%.2f".format(rate)}×", style = MaterialTheme.typography.bodySmall)
            Slider(value = rate, onValueChange = { rate = it; reader.setRate(it) }, valueRange = 0.5f..2f)
            Text("স্বয়ংক্রিয় সারাংশ (ঐচ্ছিক)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                FilterChip(selected = summaryAtChapterEnd, onClick = { summaryAtChapterEnd = !summaryAtChapterEnd }, label = { Text("অধ্যায় শেষে সামারি") })
                listOf(5, 10, 20).forEach { n -> FilterChip(selected = summaryEveryN == n, onClick = { summaryEveryN = if (summaryEveryN == n) null else n }, label = { Text("প্রতি $n পৃষ্ঠা") }) }
            }
            // --- Right here while reading: explain, ask, comment (reading pauses by itself) ---
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(enabled = !explaining, onClick = { explainNow() }) { Text(if (explaining) "ভাবছে…" else "💡 Explain this") }
            }
            OutlinedTextField(value = askText, onValueChange = { askText = it }, label = { Text("এই অংশ নিয়ে প্রশ্ন করুন (পড়া থেমে যাবে)") }, modifier = Modifier.fillMaxWidth())
            Button(enabled = !explaining && askText.isNotBlank(), onClick = { askNow() }) { Text("Ask") }
            OutlinedTextField(value = commentText, onValueChange = { commentText = it; commentSaved = false }, label = { Text("কমেন্ট / নোট (এই পৃষ্ঠার সঙ্গে সংরক্ষিত হবে)") }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(enabled = commentText.isNotBlank() && !commentSaved, onClick = {
                val pg = ReadAloudController.current?.takeIf { !ReadAloudController.adHoc }?.page ?: (pageInput.toIntOrNull() ?: 1)
                runCatching { wf.marks.add(doc.id, pg, app.personal.banglaai.userdata.Mark.Kind.NOTE, commentText.trim()) }.onSuccess { commentSaved = true; commentText = ""; voiceMsg = "কমেন্ট পৃষ্ঠা $pg-এ রাখা হয়েছে (নিচে ‘বুকমার্ক ও নোট’-এ দেখুন)" }.onFailure { voiceMsg = it.message }
            }) { Text(if (commentSaved) "✅ Saved" else "Save comment") }
            current?.let {
                val endPg = ReadAloudController.endPage
                Text((if (ReadAloudController.adHoc) "ব্যাখ্যা শোনানো হচ্ছে:" else if (endPg != null && endPg > it.page) "পড়া হচ্ছে — পৃষ্ঠা ${it.page} / $endPg…" else "এখন পড়া হচ্ছে — পৃষ্ঠা ${it.page}:"), style = MaterialTheme.typography.bodySmall)
                Surface(color = MaterialTheme.colorScheme.secondaryContainer) { Text(it.text, Modifier.padding(8.dp)) }
            }
            ReadAloudController.finishedRange?.let { (a, b) ->
                Text(if (a == b) "✅ পড়া শেষ — পৃষ্ঠা $a" else "✅ পড়া শেষ হয়েছে — পৃষ্ঠা $a–$b", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            }
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
