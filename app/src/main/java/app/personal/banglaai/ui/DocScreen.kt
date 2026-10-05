package app.personal.banglaai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.personal.banglaai.doc.DocWorkflow
import app.personal.banglaai.jobs.JobStatus
import app.personal.banglaai.ocr.TesseractOcr
import app.personal.banglaai.storage.VaultDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One document: explicit-command buttons for extraction/translation, progress, pause/cancel/resume, coverage and side-by-side viewer. */
@Composable
fun DocScreen(doc: VaultDoc, wf: DocWorkflow, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0 to 0) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var langs by remember { mutableStateOf(setOf(TesseractOcr.Lang.ENG)) }
    var pageInput by remember { mutableStateOf("1") }
    val ext = remember(tick) { wf.job(doc, DocWorkflow.Kind.Extract) }
    val tr = remember(tick) { wf.job(doc, DocWorkflow.Kind.Translate) }

    fun start(label: String, block: () -> JobStatus) {
        if (running != null) return
        running = label; error = null; progress = 0 to 0
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { block() } }.onFailure { error = it.message ?: it.javaClass.simpleName }
            running = null; tick++
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row { TextButton(onClick = onBack) { Text("← ফিরে যান") }; Text(doc.name, Modifier.padding(top = 12.dp)) }
        if (!wf.isSupported(doc)) {
            Text("এই ফরম্যাট (${doc.name.substringAfterLast('.', "?")}) এখনো সমর্থিত নয়। এখন শুধু PDF ও TXT-এর লেখা বের করা যায়।")
            return@Column
        }
        error?.let { Text("❌ $it", color = MaterialTheme.colorScheme.error) }
        running?.let { Text("চলছে: $it — ${progress.first}/${progress.second}"); LinearProgressIndicator(progress = { if (progress.second == 0) 0f else progress.first / progress.second.toFloat() }, modifier = Modifier.fillMaxWidth()) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = running == null, onClick = { start("লেখা বের করা") { wf.extract(doc, false) { d, t -> progress = d to t } } }) {
                Text(if (ext?.status == JobStatus.PAUSED) "লেখা বের করা — চালিয়ে যান" else "লেখা বের করুন")
            }
            Button(enabled = running == null && ext != null, onClick = { start("বাংলা অনুবাদ") { wf.translate(doc, false) { d, t -> progress = d to t } } }) {
                Text(if (tr?.status == JobStatus.PAUSED) "অনুবাদ — চালিয়ে যান" else "বাংলায় অনুবাদ করুন")
            }
        }
        val emptyPages = ext?.count(app.personal.banglaai.jobs.PageStatus.UNREADABLE) ?: 0
        if (emptyPages > 0 && doc.name.lowercase().endsWith(".pdf")) {
            Text("লেখা পাওয়া যায়নি এমন $emptyPages টি পৃষ্ঠার জন্য OCR (ভাষা বেছে নিন; একাধিক হলে ধীর ও কম নির্ভুল):", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TesseractOcr.Lang.entries.forEach { l -> FilterChip(selected = l in langs, onClick = { langs = if (l in langs) langs - l else langs + l }, label = { Text(l.label) }) }
            }
            Button(enabled = running == null && langs.isNotEmpty(), onClick = { start("OCR") { wf.ocr(doc, langs.toList()) { d, t -> progress = d to t } } }) { Text("OCR চালান") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = running != null, onClick = { wf.pause() }) { Text("থামান") }
            OutlinedButton(enabled = running != null, onClick = { wf.cancel() }) { Text("বাতিল") }
        }
        Text("অনুবাদ শুরু হবে শুধু আপনি বাটন চাপলেই। স্থানীয় AI মডেল ছাড়া অনুবাদ হয় না; কখনো ইন্টারনেটে পাঠানো হয় না।", style = MaterialTheme.typography.bodySmall)

        ext?.let { Text("লেখা বের করা (${it.status}): ${it.coverageReport()}", style = MaterialTheme.typography.bodySmall)
            if (it.status == JobStatus.DONE && it.count(app.personal.banglaai.jobs.PageStatus.UNREADABLE) > 0)
                Text("কিছু পৃষ্ঠায় লেখা পাওয়া যায়নি (সম্ভবত স্ক্যান করা ছবি)। নিচের OCR বাটন দিয়ে চেষ্টা করুন; না হলে সেগুলো অনুবাদ হবে না।", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        tr?.let { Text("অনুবাদ (${it.status}): ${it.coverageReport()}", style = MaterialTheme.typography.bodySmall) }

        if (ext != null) {
            HorizontalDivider()
            OutlinedTextField(value = pageInput, onValueChange = { pageInput = it.filter(Char::isDigit).take(5) }, label = { Text("পৃষ্ঠা নম্বর (ফাইলের ভৌত পৃষ্ঠা)") }, singleLine = true)
            val page = pageInput.toIntOrNull()?.takeIf { it in 1..ext.total }
            if (page == null) Text("১ থেকে ${ext.total}-এর মধ্যে লিখুন") else {
                val src = remember(page, tick) { wf.texts.get(doc.id, page) }
                val bn = remember(page, tick) { wf.translations.get(doc.id, page) }
                Text("মূল লেখা (পৃষ্ঠা $page${src?.printedLabel?.let { " · ছাপা পৃষ্ঠা $it" } ?: ""})${if (src?.ocr == true) " — OCR থেকে, ভুল থাকতে পারে" else ""}", style = MaterialTheme.typography.titleSmall)
                SelectionContainer { Text(src?.text ?: "(এই পৃষ্ঠায় বের করা লেখা নেই)") }
                Text("বাংলা অনুবাদ", style = MaterialTheme.typography.titleSmall)
                SelectionContainer { Text(bn?.text ?: "(এখনো অনুবাদ হয়নি)") }
            }
        }
    }
}
