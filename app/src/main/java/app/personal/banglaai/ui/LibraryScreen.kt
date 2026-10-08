package app.personal.banglaai.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.personal.banglaai.ai.LocalLlm
import app.personal.banglaai.ai.Prompts
import app.personal.banglaai.ai.QueryExpansion
import app.personal.banglaai.doc.DocWorkflow
import app.personal.banglaai.jobs.JobStatus
import app.personal.banglaai.jobs.PageStatus
import app.personal.banglaai.library.Library
import app.personal.banglaai.library.LibraryStore
import app.personal.banglaai.library.Passage
import app.personal.banglaai.speech.ReadAloudController
import app.personal.banglaai.storage.Vault
import app.personal.banglaai.storage.VaultDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * লাইব্রেরি: reference books (bundled public-domain ones + the owner's own PDFs/EPUBs/TXT). Ask a question in Bengali or English; the app finds the best-matching passages
 * locally (the local model first adds English/Bengali keywords, since most books are English), then the model answers ONLY from those passages and every answer lists its sources.
 * Adding a book never starts any AI work by itself; "লেখা বের করুন" is a button the owner presses.
 */
@Composable
fun LibraryScreen(wf: DocWorkflow, vault: Vault, cacheDir: File, onOpenDoc: (VaultDoc, Int?) -> Unit = { _, _ -> }, onOpenQuick: () -> Unit = {}, onBack: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val store = remember { LibraryStore(ctx) }
    val books = remember { app.personal.banglaai.library.BundledBooks(ctx, vault, wf) }
    var installing by remember { mutableStateOf<String?>(null) }
    var installErr by remember { mutableStateOf<String?>(null) }
    var bookTick by remember { mutableIntStateOf(0) }
    // the bundled books become ordinary encrypted documents (once), so they can be opened, read page by page, asked, translated and noted like any file
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { runCatching { books.ensureAll { d, t, title -> installing = if (d < t) "বই প্রস্তুত হচ্ছে ($d/$t): $title" else null } } }
            .onFailure { installErr = "বই প্রস্তুত করা যায়নি: ${it.message ?: it.javaClass.simpleName}" }
        installing = null; bookTick++
    }
    var userDocs by remember { mutableStateOf<List<VaultDoc>>(emptyList()) }
    val tick = JobRunner.tick
    LaunchedEffect(tick) { userDocs = withContext(Dispatchers.IO) { runCatching { vault.list() }.getOrDefault(emptyList()).filter { it.id in store.ids() } } }
    val add = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        JobRunner.start("বই যোগ হচ্ছে (এনক্রিপ্ট করে রাখা)") { val d = vault.import(uri); store.add(d.id); JobStatus.DONE }
    }
    var q by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var answer by remember { mutableStateOf<String?>(null) }
    var sources by remember { mutableStateOf<List<Passage>>(emptyList()) }
    var openSrc by remember { mutableStateOf(-1) }
    var err by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    val passageBn = remember { mutableStateMapOf<Int, String>() }   // per-passage Bengali translation (AI), kept apart from the original text
    var transBusy by remember { mutableStateOf(-1) }

    fun translatePassage(i: Int) {
        if (transBusy >= 0) return
        val model = wf.usableModel() ?: run { err = "অনুবাদের জন্য মডেল বসানো নেই"; return }
        transBusy = i; err = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { LocalLlm(model, File(cacheDir, "llm")).ask(Prompts.TRANSLATE_SYSTEM, Prompts.translate(sources[i].text.take(3500))) } }
                .onSuccess { passageBn[i] = it }.onFailure { err = it.message ?: it.javaClass.simpleName }
            transBusy = -1
        }
    }

    fun ready(d: VaultDoc): Int? = wf.job(d, DocWorkflow.Kind.Extract)?.let { j -> if (j.count(PageStatus.DONE) > 0) j.count(PageStatus.DONE) else null }

    fun ask() {
        if (busy != null || q.isBlank()) return
        passageBn.clear(); busy = "বই খোঁজা হচ্ছে…"; answer = null; err = null; sources = emptyList(); note = null; openSrc = -1
        var cutNote: String? = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val all = ArrayList<Passage>()
                Library.bundled.forEach { all += Library.bundledPassages(ctx, it) }
                userDocs.forEach { d -> val total = wf.job(d, DocWorkflow.Kind.Extract)?.total ?: 0
                    for (p in 1..total) wf.effective(d.id, p)?.text?.takeIf { it.isNotBlank() }?.let { t -> all += Library.chunk(d.name, t).map { c -> c.copy(label = "পৃষ্ঠা $p") } } }
                val index = Library.Index(all)
                val model = wf.usableModel()
                // Bengali question -> English search words WITHOUT the model (concept list + dictionary glosses); the model's own expansion is added on top
                val dict = runCatching { app.personal.banglaai.knowledge.Dictionary.get(ctx) }.getOrNull()
                val lexicon = app.personal.banglaai.library.BnEnQuery.englishTerms(q) { w -> dict?.englishFor(w).orEmpty() }
                var query = QueryExpansion.expand(q, lexicon)
                if (model != null) {
                    withContext(Dispatchers.Main) { busy = "ইংরেজি/বাংলা কীওয়ার্ড বানানো হচ্ছে (প্রথমবার মডেল লোড হয়)…" }
                    val m = LocalLlm(model, File(cacheDir, "llm"))
                    query = QueryExpansion.expand(query, QueryExpansion.parse(m.ask(QueryExpansion.SYSTEM, QueryExpansion.prompt(q))))
                }
                val found = index.search(query, 6)
                var ans: String? = null
                if (model != null && found.isNotEmpty()) {
                    withContext(Dispatchers.Main) { busy = "উত্তর লেখা হচ্ছে…" }
                    ans = LocalLlm(model, File(cacheDir, "llm")).ask(Prompts.LIB_SYSTEM, Prompts.libraryAnswer(q, run { val (cut, trimmed) = app.personal.banglaai.ai.TokenBudget.share(found.map { it.text }, app.personal.banglaai.ai.TokenBudget.INPUT_TOKENS - 800)
                        if (trimmed) cutNote = app.personal.banglaai.ai.TokenBudget.NOTE_BN
                        found.indices.map { "${found[it].book} — ${found[it].label}" to cut[it] } }))
                }
                Triple(found, ans, model != null)
            } }.onSuccess { (found, ans, hadModel) ->
                sources = found; answer = ans
                note = when { found.isEmpty() -> "কোনো বইয়ে মিল পাওয়া যায়নি।" + if (!hadModel) " (AI model ছাড়া শুধু শব্দ-মিল খোঁজা হয়েছে; অন্য শব্দে প্রশ্ন করে দেখুন, অথবা ইংরেজি শব্দ ব্যবহার করুন।)" else ""
                    !hadModel -> "AI model বসানো নেই — তাই বাংলা উত্তর নেই; নিচে পুরো বই থেকে সবচেয়ে মিল-থাকা মূল অংশ (ইংরেজি) দেখানো হলো। প্রতিটি অংশ ‘Open in reader’ দিয়ে বইয়ে খুলে পড়তে পারেন।" else -> cutNote }
            }.onFailure { err = it.message ?: it.javaClass.simpleName }
            busy = null
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = onBack) { Text("← Back") }; Text("Library", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f)) }
        Text("বই থেকে প্রশ্ন করুন; উত্তরে বই ও অংশের উৎস থাকবে। এটি বই 'মুখস্থ' করা নয় — প্রতিবার সবচেয়ে মিল-থাকা অংশ খুঁজে পড়ে উত্তর দেয়, তাই ভুল বা অসম্পূর্ণ হতে পারে; গুরুত্বপূর্ণ বিষয়ে নিচের উৎস-অংশ নিজে মিলিয়ে নিন।", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        JobRunner.label?.let { Text("চলছে: $it ${JobRunner.progress.first}/${JobRunner.progress.second}", color = MaterialTheme.colorScheme.tertiary); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        JobRunner.error?.let { Text("❌ $it", color = MaterialTheme.colorScheme.error) }

        SectionTitle("প্রশ্ন")
        OutlinedTextField(q, { q = it }, label = { Text("আপনার প্রশ্ন (বাংলা বা ইংরেজি)") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
        Button(enabled = busy == null && q.isNotBlank(), onClick = { ask() }) { Text(busy ?: "Ask the books") }
        err?.let { Text("❌ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
        answer?.let { a ->
            Text("উত্তর (AI; মূলত নিচের উৎস-অংশের ভিত্তিতে — ‘আমার মন্তব্য/অনুমান’ অংশ বইয়ের কথা নয়; উৎসের সঙ্গে মিলিয়ে নিন)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            SelectionContainer { Text(a) }
            TextButton(onClick = { ReadAloudController.speakExplanation(ctx, a) }) { Text("🔊 Listen") }
            SaveQaButton(wf, "library", q, a, sources.map { "${it.book} — ${it.label}" })
        }
        if (sources.isNotEmpty()) {
            Text("উৎস (ট্যাপ করলে মূল অংশ দেখা যাবে)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            sources.forEachIndexed { i, p ->
                OutlinedButton(onClick = { openSrc = if (openSrc == i) -1 else i }, modifier = Modifier.fillMaxWidth()) { Text("[উৎস ${i + 1}] ${p.book} — ${p.label}") }
                if (openSrc == i) {
                    Text("মূল লেখা (${p.book} — ${p.label})", style = MaterialTheme.typography.titleSmall)
                    SelectionContainer { Text(p.text, style = MaterialTheme.typography.bodySmall) }
                    OutlinedButton(onClick = {
                        scope.launch {
                            val target = withContext(Dispatchers.IO) { runCatching {
                                val book = Library.bundled.firstOrNull { b -> b.title.substringBefore(" (") == p.book }
                                val doc = book?.let { b -> books.installedDoc(b) } ?: userDocs.firstOrNull { it.name == p.book }
                                doc?.let { d -> d to (books.pageContaining(d, p.text) ?: p.label.removePrefix("পৃষ্ঠা ").toIntOrNull()) }
                            }.getOrNull() }
                            if (target != null) onOpenDoc(target.first, target.second) else err = "এই অংশের বইটি খুঁজে পাওয়া যায়নি"
                        }
                    }) { Text("📖 Open in reader") }
                    OutlinedButton(enabled = transBusy < 0, onClick = { translatePassage(i) }) { Text(if (transBusy == i) "অনুবাদ হচ্ছে…" else "Translate to Bengali (AI)") }
                    passageBn[i]?.let { Text("বাংলা অনুবাদ (AI — মূলের সঙ্গে মিলিয়ে নিন; মূল লেখা অক্ষত)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary); SelectionContainer { Text(it) } }
                }
            }
        }

        installing?.let { Text(it, color = MaterialTheme.colorScheme.tertiary); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        installErr?.let { Text("❌ $it", color = MaterialTheme.colorScheme.error) }
        SectionTitle("আমার বইগুলো")
        Library.bundled.forEach { b ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text("✅ ${b.title}", style = MaterialTheme.typography.titleSmall); Text(b.author, style = MaterialTheme.typography.bodySmall)
                var details by remember(b.id) { mutableStateOf(false) }
                TextButton(onClick = { details = !details }) { Text(if (details) "▲ Hide details" else "ℹ Edition, source, licence") }
                if (details) {
                    val rec = app.personal.banglaai.library.BookManifest.of(b.id)
                    Text("সংস্করণ: ${rec.edition} · উৎস: ${rec.source}", style = MaterialTheme.typography.bodySmall)
                    Text("স্বত্ব-ভিত্তি: ${rec.licenseBasis}", style = MaterialTheme.typography.bodySmall)
                    Text("সীমা: ${rec.limitation}। মূল প্রকাশকের ফাইলের সঙ্গে শব্দে শব্দে মেলানো হয়নি। SHA-256: ${rec.sha256.take(16)}…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val doc = remember(bookTick) { books.installedDoc(b) }
                Text(if (doc != null) "অ্যাপের সঙ্গে দেওয়া; প্রস্তুত — পৃষ্ঠা/অধ্যায় ধরে পড়া যায়।" else "প্রস্তুত করা হচ্ছে…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                if (doc != null) Button(onClick = { onOpenDoc(doc, 1) }) { Text("📖 Open / Read") }
            } }
        }
        if (userDocs.isEmpty()) Text("এখনো নিজের কোনো বই যোগ করা হয়নি।", style = MaterialTheme.typography.bodySmall)
        userDocs.forEach { d ->
            val r = ready(d)
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text((if (r != null) "✅ " else "⏳ ") + d.name, style = MaterialTheme.typography.titleSmall)
                Text(if (r != null) "প্রস্তুত — $r পৃষ্ঠার লেখা খোঁজা যায়।" else "লেখা বের করা বাকি — নিচের বাটন চাপুন (স্ক্যান PDF হলে আগে ফাইল-পর্দা থেকে OCR লাগবে)।", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (r == null) Button(enabled = JobRunner.label == null, onClick = { JobRunner.start("লেখা বের করা: ${d.name.take(30)}") { wf.extract(d, false) { x, t -> JobRunner.progress = x to t } } }) { Text("Extract text") }
                    TextButton(onClick = { store.remove(d.id); userDocs = userDocs - d }) { Text("Remove from library") }
                }
            } }
        }
        Button(enabled = JobRunner.label == null, onClick = { add.launch(arrayOf("*/*")) }) { Text("➕ Add my own book (PDF / EPUB / TXT)") }
        Text("বই ফোনে এনক্রিপ্ট হয়ে থাকে, কোথাও যায় না। কপিরাইটযুক্ত বই শুধু আপনার বৈধ কপি যোগ করুন। যোগ করলে নিজে থেকে কোনো AI কাজ শুরু হয় না।", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onOpenQuick) { Text("📚 Quick answers (short topic cards)") }
        SectionTitle("কোন বই আছে, কোনটা আপনাকে দিতে হবে")
        Text("অ্যাপে আছে (পুরোনো অনুবাদ, সর্বজনীন-ডোমেইন বলে ধরা হয়েছে — আর্থশাস্ত্রের ১৯৫১-র সংস্করণের ভূমিকা-নোটের স্বত্ব যাচাই বাকি): The Prince, Discourses on Livy, The Art of War, On War, On the Manner of Negotiating with Princes (কূটনীতি), The Crowd (জনতার মনোবিজ্ঞান), The Art of Worldly Wisdom (গ্রাসিয়ান), Kautilya’s Arthashastra (অর্থশাস্ত্র)।\n\nআপনাকে নিজে যোগ করতে হবে (কপিরাইটযুক্ত): 48 Laws of Power, Influence (Cialdini), How to Win Friends and Influence People, Kissinger-এর Diplomacy ইত্যাদি। বৈধ কপি কিনে বা লাইব্রেরি থেকে নিয়ে PDF/EPUB যোগ করুন।", style = MaterialTheme.typography.bodySmall)
    }
}
