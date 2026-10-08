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
fun LibraryScreen(wf: DocWorkflow, vault: Vault, cacheDir: File, onBack: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val store = remember { LibraryStore(ctx) }
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

    fun ready(d: VaultDoc): Int? = wf.job(d, DocWorkflow.Kind.Extract)?.let { j -> if (j.count(PageStatus.DONE) > 0) j.count(PageStatus.DONE) else null }

    fun ask() {
        if (busy != null || q.isBlank()) return
        busy = "বই খোঁজা হচ্ছে…"; answer = null; err = null; sources = emptyList(); note = null; openSrc = -1
        scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val all = ArrayList<Passage>()
                Library.bundled.forEach { all += Library.bundledPassages(ctx, it) }
                userDocs.forEach { d -> val total = wf.job(d, DocWorkflow.Kind.Extract)?.total ?: 0
                    for (p in 1..total) wf.texts.get(d.id, p)?.text?.takeIf { it.isNotBlank() }?.let { all += Passage(d.name, "পৃষ্ঠা $p", it.take(2500)) } }
                val index = Library.Index(all)
                val model = wf.usableModel()
                var query = q
                if (model != null) {
                    withContext(Dispatchers.Main) { busy = "ইংরেজি/বাংলা কীওয়ার্ড বানানো হচ্ছে (প্রথমবার মডেল লোড হয়)…" }
                    val m = LocalLlm(model, File(cacheDir, "llm"))
                    query = QueryExpansion.expand(q, QueryExpansion.parse(m.ask(QueryExpansion.SYSTEM, QueryExpansion.prompt(q))))
                }
                val found = index.search(query, 6)
                var ans: String? = null
                if (model != null && found.isNotEmpty()) {
                    withContext(Dispatchers.Main) { busy = "উত্তর লেখা হচ্ছে…" }
                    ans = LocalLlm(model, File(cacheDir, "llm")).ask(Prompts.LIB_SYSTEM, Prompts.libraryAnswer(q, found.map { "${it.book} — ${it.label}" to it.text.take(1800) }))
                }
                Triple(found, ans, model != null)
            } }.onSuccess { (found, ans, hadModel) ->
                sources = found; answer = ans
                note = when { found.isEmpty() -> "কোনো বইয়ে মিল পাওয়া যায়নি।" + if (!hadModel) " (মডেল না থাকায় শুধু সরাসরি শব্দ-মিল খোঁজা হয়েছে; বাংলা প্রশ্নে ইংরেজি বই পেতে মডেল লাগে।)" else ""
                    !hadModel -> "মডেল বসানো নেই — নিচে শুধু মিল-থাকা অংশ দেখানো হলো, AI-র উত্তর নেই।" else -> null }
            }.onFailure { err = it.message ?: it.javaClass.simpleName }
            busy = null
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = onBack) { Text("← ফিরে যান") }; Text("লাইব্রেরি", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f)) }
        Text("বই থেকে প্রশ্ন করুন; উত্তরে বই ও অংশের উৎস থাকবে। এটি বই 'মুখস্থ' করা নয় — প্রতিবার সবচেয়ে মিল-থাকা অংশ খুঁজে পড়ে উত্তর দেয়, তাই ভুল বা অসম্পূর্ণ হতে পারে; গুরুত্বপূর্ণ বিষয়ে নিচের উৎস-অংশ নিজে মিলিয়ে নিন।", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        JobRunner.label?.let { Text("চলছে: $it ${JobRunner.progress.first}/${JobRunner.progress.second}", color = MaterialTheme.colorScheme.tertiary); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        JobRunner.error?.let { Text("❌ $it", color = MaterialTheme.colorScheme.error) }

        SectionTitle("প্রশ্ন")
        OutlinedTextField(q, { q = it }, label = { Text("আপনার প্রশ্ন (বাংলা বা ইংরেজি)") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
        Button(enabled = busy == null && q.isNotBlank(), onClick = { ask() }) { Text(busy ?: "বইয়ে খুঁজে উত্তর দাও") }
        err?.let { Text("❌ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
        answer?.let { a ->
            Text("উত্তর (AI; শুধু নিচের উৎস-অংশ থেকে)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            SelectionContainer { Text(a) }
            TextButton(onClick = { ReadAloudController.speakExplanation(ctx, a) }) { Text("🔊 শুনুন") }
        }
        if (sources.isNotEmpty()) {
            Text("উৎস (ট্যাপ করলে মূল অংশ দেখা যাবে)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            sources.forEachIndexed { i, p ->
                OutlinedButton(onClick = { openSrc = if (openSrc == i) -1 else i }, modifier = Modifier.fillMaxWidth()) { Text("[উৎস ${i + 1}] ${p.book} — ${p.label}") }
                if (openSrc == i) SelectionContainer { Text(p.text, style = MaterialTheme.typography.bodySmall) }
            }
        }

        SectionTitle("আমার বইগুলো")
        Library.bundled.forEach { b ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text("✅ ${b.title}", style = MaterialTheme.typography.titleSmall); Text("${b.author} · ${b.note}", style = MaterialTheme.typography.bodySmall)
                Text("অ্যাপের সঙ্গে দেওয়া; প্রস্তুত।", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            } }
        }
        if (userDocs.isEmpty()) Text("এখনো নিজের কোনো বই যোগ করা হয়নি।", style = MaterialTheme.typography.bodySmall)
        userDocs.forEach { d ->
            val r = ready(d)
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text((if (r != null) "✅ " else "⏳ ") + d.name, style = MaterialTheme.typography.titleSmall)
                Text(if (r != null) "প্রস্তুত — $r পৃষ্ঠার লেখা খোঁজা যায়।" else "লেখা বের করা বাকি — নিচের বাটন চাপুন (স্ক্যান PDF হলে আগে ফাইল-পর্দা থেকে OCR লাগবে)।", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (r == null) Button(enabled = JobRunner.label == null, onClick = { JobRunner.start("লেখা বের করা: ${d.name.take(30)}") { wf.extract(d, false) { x, t -> JobRunner.progress = x to t } } }) { Text("লেখা বের করুন") }
                    TextButton(onClick = { store.remove(d.id); userDocs = userDocs - d }) { Text("লাইব্রেরি থেকে সরান") }
                }
            } }
        }
        Button(enabled = JobRunner.label == null, onClick = { add.launch(arrayOf("*/*")) }) { Text("➕ নিজের বই যোগ করুন (PDF / EPUB / TXT)") }
        Text("বই ফোনে এনক্রিপ্ট হয়ে থাকে, কোথাও যায় না। কপিরাইটযুক্ত বই শুধু আপনার বৈধ কপি যোগ করুন। যোগ করলে নিজে থেকে কোনো AI কাজ শুরু হয় না।", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionTitle("কোন বই আছে, কোনটা আপনাকে দিতে হবে")
        Text("অ্যাপে আছে (সর্বজনীন-ডোমেইন, বৈধ): The Prince, Discourses on Livy, The Art of War, On War, On the Manner of Negotiating with Princes (কূটনীতি), The Crowd (জনতার মনোবিজ্ঞান)।\n\nআপনাকে নিজে যোগ করতে হবে (কপিরাইটযুক্ত): 48 Laws of Power, Influence (Cialdini), How to Win Friends and Influence People, Kissinger-এর Diplomacy ইত্যাদি। বৈধ কপি কিনে বা লাইব্রেরি থেকে নিয়ে PDF/EPUB যোগ করুন।", style = MaterialTheme.typography.bodySmall)
    }
}
