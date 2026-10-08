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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import app.personal.banglaai.export.Exporter
import app.personal.banglaai.export.ExportOptions
import app.personal.banglaai.export.PdfExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One document: explicit-command buttons for extraction/translation, progress, pause/cancel/resume, coverage and side-by-side viewer. */
@Composable
fun DocScreen(doc: VaultDoc, wf: DocWorkflow, initialCommand: app.personal.banglaai.speech.VoiceCommand? = null, initialPage: Int? = null, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val running = JobRunner.label            // jobs live in the process, not in this screen (see JobRunner)
    val progress = JobRunner.progress
    val error = JobRunner.error
    val tick = JobRunner.tick
    var langs by remember { mutableStateOf(setOf(TesseractOcr.Lang.ENG)) }
    var editImage by remember { mutableStateOf(false) }
    var pageInput by remember { mutableStateOf(initialPage?.toString() ?: "1") }
    var fixTick by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val ext = remember(tick) { wf.job(doc, DocWorkflow.Kind.Extract) }
    val tr = remember(tick) { wf.job(doc, DocWorkflow.Kind.Translate) }

    fun start(label: String, block: () -> JobStatus) = JobRunner.start(label, block)

    var exportKind by remember { mutableStateOf("txt") }
    var rtlOriginal by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        start("এক্সপোর্ট") {
            val pages = wf.exportPages(doc); val o = ExportOptions(doc.name, includeOriginal = true, originalIsRtl = rtlOriginal)
            ctx.contentResolver.openOutputStream(uri)?.use { out ->
                when (exportKind) { "docx" -> Exporter.docx(pages, o, out); "pdf" -> PdfExport.write(pages, o, out); else -> out.write(Exporter.txt(pages, o).toByteArray(Charsets.UTF_8)) }
            } ?: error("ফাইল লেখা যায়নি")
            JobStatus.DONE
        }
    }

    if (editImage) { ImageEditScreen(doc, wf) { editImage = false }; return }
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row { TextButton(onClick = onBack) { Text("← ফিরে যান") }; Text(doc.name, Modifier.padding(top = 12.dp)) }
        if (!wf.isSupported(doc)) {
            Text("এই ফরম্যাট (${doc.name.substringAfterLast('.', "?")}) এখনো সমর্থিত নয়। এখন PDF, TXT, DOCX, PPTX, XLSX, EPUB, CSV এবং ছবি (JPG/PNG, OCR-সহ)-এর লেখা বের করা যায়।")
            return@Column
        }
        if (initialCommand != null && ext == null) Text("ভয়েস নির্দেশ ধরা হয়নি: এই ফাইলের লেখা এখনো বের করা হয়নি। আগে 'লেখা বের করুন' চালান, তারপর আবার বলুন।", color = MaterialTheme.colorScheme.tertiary)
        if (wf.isImage(doc)) OutlinedButton(onClick = { editImage = true }) { Text("🖼️ ছবি দেখুন / বড় করুন / কাটুন / ঘোরান") }
        error?.let { Text("❌ $it", color = MaterialTheme.colorScheme.error) }
        running?.let { Text("চলছে: $it — ${progress.first}/${progress.second}"); LinearProgressIndicator(progress = { if (progress.second == 0) 0f else progress.first / progress.second.toFloat() }, modifier = Modifier.fillMaxWidth()) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = running == null, onClick = { start("লেখা বের করা") { wf.extract(doc, false) { d, t -> JobRunner.progress = d to t } } }) {
                Text(if (ext?.status == JobStatus.PAUSED) "লেখা বের করা — চালিয়ে যান" else "লেখা বের করুন")
            }
            if (!wf.isCode(doc)) Button(enabled = running == null && ext != null, onClick = { start("বাংলা অনুবাদ") { wf.translate(doc, false) { d, t -> JobRunner.progress = d to t } } }) {
                Text(if (tr?.status == JobStatus.PAUSED) "অনুবাদ — চালিয়ে যান" else "বাংলায় অনুবাদ করুন")
            }
        }
        if (wf.isCode(doc)) Text("সোর্স-কোড ফাইল: অনুবাদ নেই (মূল কোড অক্ষত থাকে)। কোড বুঝতে হোম থেকে 'কোড ব্যাখ্যা' ব্যবহার করুন।", style = MaterialTheme.typography.bodySmall)
        wf.pauseReason?.let { Text("⏸ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        wf.textNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        val emptyPages = ext?.count(app.personal.banglaai.jobs.PageStatus.UNREADABLE) ?: 0
        if (emptyPages > 0 && (doc.name.lowercase().endsWith(".pdf") || wf.isImage(doc))) {
            Text("লেখা পাওয়া যায়নি এমন $emptyPages টি পৃষ্ঠার জন্য OCR (ভাষা বেছে নিন; একাধিক হলে ধীর ও কম নির্ভুল):", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TesseractOcr.Lang.entries.forEach { l -> FilterChip(selected = l in langs, onClick = { langs = if (l in langs) langs - l else langs + l }, label = { Text(l.label) }) }
            }
            Button(enabled = running == null && langs.isNotEmpty(), onClick = { start("OCR") { wf.ocr(doc, langs.toList()) { d, t -> JobRunner.progress = d to t } } }) { Text("OCR চালান") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = running != null, onClick = { wf.pause() }) { Text("থামান") }
            OutlinedButton(enabled = running != null, onClick = { wf.cancel() }) { Text("বাতিল") }
        }
        Text("অনুবাদ শুরু হবে শুধু আপনি বাটন চাপলেই। স্থানীয় AI মডেল ছাড়া অনুবাদ হয় না; এই অ্যাপের ইন্টারনেট অনুমতি নেই, তাই নিজে কিছু পাঠাতে পারে না।", style = MaterialTheme.typography.bodySmall)

        ext?.let { Text("লেখা বের করা (${it.status}): ${it.coverageReport()}", style = MaterialTheme.typography.bodySmall)
            if (it.status == JobStatus.DONE && it.count(app.personal.banglaai.jobs.PageStatus.UNREADABLE) > 0)
                Text("কিছু পৃষ্ঠায় লেখা পাওয়া যায়নি (সম্ভবত স্ক্যান করা ছবি)। নিচের OCR বাটন দিয়ে চেষ্টা করুন; না হলে সেগুলো অনুবাদ হবে না।", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        tr?.let { Text("অনুবাদ (${it.status}): ${it.coverageReport()}", style = MaterialTheme.typography.bodySmall) }

        if (tr != null) {
            HorizontalDivider()
            Text("এক্সপোর্ট (আপনার বেছে নেওয়া জায়গায়, ফোনের ভেতরেই; কোথাও আপলোড হয় না)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = rtlOriginal, onClick = { rtlOriginal = !rtlOriginal }, label = { Text("মূল ডান-থেকে-বাঁ (আরবি/উর্দু)") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("txt" to "TXT", "docx" to "DOCX", "pdf" to "PDF").forEach { (k, label) ->
                    OutlinedButton(enabled = running == null, onClick = { exportKind = k; saver.launch("${doc.name.substringBeforeLast('.')}-bn.$k") }) { Text(label) }
                }
            }
            Text("এক্সপোর্টে অনুবাদ-না-হওয়া পৃষ্ঠার তালিকা থাকে। পৃষ্ঠা-বিন্যাস মূলের সঙ্গে হুবহু মিলবে না।", style = MaterialTheme.typography.bodySmall)
        }
        if (ext != null) {
            ReaderSection(doc, wf, ext.total, initialCommand)
            QaSection(doc, wf, ext.total, ctx.cacheDir) { pageInput = it.toString() }
            SavedQaSection(wf, doc) { _, p -> pageInput = p.toString() }
            HorizontalDivider()
            OutlinedTextField(value = pageInput, onValueChange = { pageInput = it.filter(Char::isDigit).take(5) }, label = { Text("পৃষ্ঠা নম্বর (ফাইলের ভৌত পৃষ্ঠা)") }, singleLine = true)
            val page = pageInput.toIntOrNull()?.takeIf { it in 1..ext.total }
            if (page == null) Text("১ থেকে ${ext.total}-এর মধ্যে লিখুন") else {
                val src = remember(page, tick, fixTick) { wf.texts.get(doc.id, page) }
                val fix = remember(page, tick, fixTick) { wf.fixes.get(doc.id, page) }
                val bn = remember(page, tick) { wf.translations.get(doc.id, page) }
                if (doc.name.lowercase().endsWith(".pdf")) OriginalPageView(doc, wf, page)
                Text("মূল লেখা (পৃষ্ঠা $page${src?.printedLabel?.let { " · মূল অবস্থান: $it" } ?: ""})${if (src?.ocr == true) " — OCR থেকে, ভুল থাকতে পারে" else ""}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                SelectionContainer { Text(src?.text ?: "(এই পৃষ্ঠায় বের করা লেখা নেই)") }
                if (fix != null) {
                    Text("✏️ আপনার সংশোধিত লেখা — পড়া, অনুবাদ, প্রশ্নোত্তর ও এক্সপোর্ট এটি থেকেই হয়; উপরের নিষ্কাশিত লেখা অক্ষত আছে", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary)
                    SelectionContainer { Text(fix.text) }
                }
                if (!editing) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = running == null, onClick = { draft = fix?.text ?: src?.text.orEmpty(); editing = true }) { Text(if (fix == null) "✏️ লেখা সংশোধন করুন" else "✏️ সংশোধন বদলান") }
                    if (fix != null) OutlinedButton(enabled = running == null, onClick = { scope.launch { withContext(Dispatchers.IO) { wf.clearFix(doc, page) }; fixTick++ } }) { Text("সংশোধন বাতিল (নিষ্কাশিত লেখায় ফিরুন)") }
                } else {
                    OutlinedTextField(value = draft, onValueChange = { draft = it }, label = { Text("সংশোধিত লেখা (পৃষ্ঠা $page)") }, modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp))
                    Text("সংরক্ষণ করলে এই পৃষ্ঠার পুরোনো অনুবাদটি 'বাকি' হয়ে যায় (পুরোনো লেখার অনুবাদ ছিল); অনুবাদে 'চালিয়ে যান' চাপলে শুধু এই পৃষ্ঠাই আবার অনুবাদ হবে।", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = draft.isNotBlank(), onClick = { scope.launch { withContext(Dispatchers.IO) { wf.saveFix(doc, page, draft) }; editing = false; fixTick++ } }) { Text("সংরক্ষণ") }
                        OutlinedButton(onClick = { editing = false }) { Text("বাতিল") }
                    }
                }
                Text("বাংলা অনুবাদ", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                SelectionContainer { Text(bn?.text ?: "(এখনো অনুবাদ হয়নি)") }
            }
            pageInput.toIntOrNull()?.takeIf { it in 1..ext.total }?.let { SegmentExplainer(doc, wf, it, ext.total) }
            MarksSection(doc, wf, pageInput.toIntOrNull()?.takeIf { it in 1..ext.total }) { pageInput = it.toString() }
        }
    }
}
