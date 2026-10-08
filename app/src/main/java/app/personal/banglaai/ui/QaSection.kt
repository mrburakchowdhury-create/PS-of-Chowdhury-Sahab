package app.personal.banglaai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.personal.banglaai.ai.Bm25Index
import app.personal.banglaai.ai.Hit
import app.personal.banglaai.ai.LocalLlm
import app.personal.banglaai.ai.Prompts
import app.personal.banglaai.doc.DocWorkflow
import app.personal.banglaai.storage.VaultDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Source-linked Q&A: local keyword retrieval, then the local model answers ONLY from the retrieved pages. Tap a source to open that page. */
@Composable
fun QaSection(doc: VaultDoc, wf: DocWorkflow, totalPages: Int, cacheDir: File, onOpenPage: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<Hit>>(emptyList()) }
    var answer by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var wide by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val llm = remember { arrayOfNulls<LocalLlm>(1) }
    DisposableEffect(Unit) { onDispose { llm[0]?.close() } }

    HorizontalDivider()
    Text("প্রশ্ন করুন (ডকুমেন্ট থেকে, উৎস-সহ)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    OutlinedTextField(value = q, onValueChange = { q = it }, label = { Text("প্রশ্ন") }, modifier = Modifier.fillMaxWidth())
    FilterChip(selected = wide, onClick = { wide = !wide }, label = { Text("আরও ব্যাপক খোঁজ (AI সমার্থক শব্দ যোগ করে — ধীর)") })
    Button(enabled = !busy && q.isNotBlank(), onClick = {
        busy = true; error = null; answer = null; hits = emptyList()
        var cutNote: String? = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val pages = (1..totalPages).mapNotNull { p -> wf.effective(doc.id, p)?.text?.takeIf { it.isNotBlank() }?.let { p to it } }.toMap()
                var query = q
                if (wide && pages.isNotEmpty()) {
                    val model0 = wf.usableModel() ?: error("কোনো AI মডেল বসানো নেই — আগে মডেল import করুন")
                    val m0 = llm[0] ?: LocalLlm(model0, File(cacheDir, "llm")).also { llm[0] = it }
                    query = app.personal.banglaai.ai.QueryExpansion.expand(q, app.personal.banglaai.ai.QueryExpansion.parse(m0.ask(app.personal.banglaai.ai.QueryExpansion.SYSTEM, app.personal.banglaai.ai.QueryExpansion.prompt(q))))
                }
                val found = Bm25Index(pages).search(query, 5)
                if (found.isEmpty()) return@withContext Triple(found, null as String?, pages.size)
                val model = wf.usableModel() ?: error("কোনো AI মডেল বসানো নেই — আগে মডেল import করুন")
                val m = llm[0] ?: LocalLlm(model, File(cacheDir, "llm")).also { llm[0] = it }
                val a = m.ask(Prompts.QA_SYSTEM, Prompts.answer(q, run { val (cut, trimmed) = app.personal.banglaai.ai.TokenBudget.share(found.map { pages.getValue(it.page) }, app.personal.banglaai.ai.TokenBudget.INPUT_TOKENS - 700)
                    if (trimmed) cutNote = app.personal.banglaai.ai.TokenBudget.NOTE_BN
                    found.indices.map { found[it].page to cut[it] } }))
                Triple(found, a, pages.size)
            } }.onSuccess { (f, a, n) ->
                hits = f; answer = a
                note = "অনুসন্ধান: $n পৃষ্ঠার মধ্যে কীওয়ার্ড-মিলে শীর্ষ ${f.size} পৃষ্ঠা ব্যবহার হয়েছে। এটি অর্থ-ভিত্তিক নয় — ভিন্ন শব্দে লেখা অংশ বাদ পড়তে পারে; তাই এটি পুরো বই যাচাইয়ের প্রমাণ নয়।" +
                    if (f.isEmpty()) " কোনো পৃষ্ঠায় মিল পাওয়া যায়নি; AI ডাকা হয়নি।" else "" + (cutNote?.let { " $it" } ?: "")
            }.onFailure { error = it.message ?: it.javaClass.simpleName }
            busy = false
        }
    }) { Text(if (busy) "খোঁজা ও উত্তর তৈরি হচ্ছে…" else "খুঁজে উত্তর দাও") }
    error?.let { Text("❌ $it", color = MaterialTheme.colorScheme.error) }
    note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    answer?.let { a -> Text("উত্তর (AI; উদ্ধৃতি ও অনুমান আলাদা — নিচের উৎস পৃষ্ঠায় মিলিয়ে নিন)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary); SelectionContainer { Text(a) } }
    SaveQaButton(wf, "doc", q, answer, hits.map { "${doc.name} — পৃষ্ঠা ${it.page}" }, doc.id, hits.firstOrNull()?.page ?: 0)
    if (hits.isNotEmpty()) {
        Text("উৎস পৃষ্ঠা (ট্যাপ করলে মূল লেখা খুলবে)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        hits.forEach { h -> OutlinedButton(onClick = { onOpenPage(h.page) }, modifier = Modifier.fillMaxWidth()) { Text("পৃষ্ঠা ${h.page}: ${h.snippet.replace('\n', ' ').take(80)}…") } }
    }
}
