package app.personal.banglaai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.personal.banglaai.ai.LocalLlm
import app.personal.banglaai.ai.Prompts
import app.personal.banglaai.doc.DocWorkflow
import app.personal.banglaai.speech.ReadAloudController
import app.personal.banglaai.storage.VaultDoc
import app.personal.banglaai.userdata.VocabEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Ask about a word/phrase while reading. Reading is paused first; the question is answered in the context of the segment being read (or the page you picked). */
@Composable
fun WordQuestion(doc: VaultDoc, wf: DocWorkflow, page: Int?, context: String?, cacheDir: File) {
    val scope = rememberCoroutineScope()
    var term by remember { mutableStateOf("") }; var answer by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }; var err by remember { mutableStateOf<String?>(null) }; var saved by remember { mutableStateOf(false) }
    HorizontalDivider()
    Text("শব্দ/বাক্যের অর্থ জানুন (পড়ার প্রসঙ্গে)", style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(term, { term = it; saved = false }, label = { Text("কোন শব্দ বা বাক্য? (লিখুন বা কপি-পেস্ট)") }, modifier = Modifier.fillMaxWidth())
    Text(if (context.isNullOrBlank()) "প্রসঙ্গ: কোনো অংশ পড়া হচ্ছে না — প্রসঙ্গ ছাড়া সাধারণ অর্থ দেওয়া হবে।" else "প্রসঙ্গ: এখন পড়া অংশ (পৃষ্ঠা ${page ?: "?"})।", style = MaterialTheme.typography.bodySmall)
    Button(enabled = !busy && term.isNotBlank(), onClick = {
        val model = wf.usableModel() ?: run { err = "কোনো AI মডেল বসানো নেই"; return@Button }
        ReadAloudController.pause(); busy = true; err = null; answer = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { LocalLlm(model, File(cacheDir, "llm")).use { it.ask(Prompts.WORD_SYSTEM, Prompts.wordMeaning(term, context.orEmpty())) } } }
                .onSuccess { answer = it }.onFailure { err = it.message ?: it.javaClass.simpleName }
            busy = false
        }
    }) { Text(if (busy) "ভাবছে…" else "অর্থ জানুন") }
    err?.let { Text("❌ $it", color = MaterialTheme.colorScheme.error) }
    answer?.let { a ->
        Text("AI-র উত্তর (সাধারণ ব্যাখ্যা অংশ ডকুমেন্টের বক্তব্য নয়; ভুল হতে পারে)", style = MaterialTheme.typography.bodySmall)
        SelectionContainer { Text(a) }
        OutlinedButton(enabled = !saved && page != null, onClick = { wf.vocab.add(term, a.take(1500), doc.id, page ?: 1); saved = true }) { Text(if (saved) "✅ শব্দভান্ডারে রাখা হয়েছে" else "শব্দভান্ডারে রাখুন") }
    }
}

/** Saved words and on-request practice questions. Practice text is AI-made study material, kept apart from every document. */
@Composable
fun VocabPanel(wf: DocWorkflow, cacheDir: File) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<VocabEntry>>(emptyList()) }
    var out by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }; var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { items = runCatching { wf.vocab.all() }.getOrDefault(emptyList()) }
    Text("শব্দভান্ডার ও অনুশীলনী", style = MaterialTheme.typography.titleMedium)
    if (items.isEmpty()) Text("এখনো কোনো শব্দ রাখা হয়নি। ডকুমেন্টে 'শব্দ/বাক্যের অর্থ' থেকে রাখুন।", style = MaterialTheme.typography.bodySmall)
    items.forEach { e -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text("${e.term} — ${e.meaning.lines().firstOrNull { it.isNotBlank() }.orEmpty().take(70)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { wf.vocab.remove(e.term, e.docId); items = wf.vocab.all() }) { Text("মুছুন") } } }
    Button(enabled = items.isNotEmpty() && !busy, onClick = {
        val model = wf.usableModel() ?: run { err = "কোনো AI মডেল বসানো নেই"; return@Button }
        busy = true; err = null; out = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { LocalLlm(model, File(cacheDir, "llm")).use { it.ask(Prompts.PRACTICE_SYSTEM, Prompts.practice(items.take(30).map { v -> v.term to v.meaning.take(300) })) } } }
                .onSuccess { out = it }.onFailure { err = it.message ?: it.javaClass.simpleName }
            busy = false
        }
    }) { Text(if (busy) "তৈরি হচ্ছে…" else "অনুশীলনী প্রশ্ন তৈরি করুন") }
    err?.let { Text("❌ $it", color = MaterialTheme.colorScheme.error) }
    out?.let { SelectionContainer { Text(it) } }
}
