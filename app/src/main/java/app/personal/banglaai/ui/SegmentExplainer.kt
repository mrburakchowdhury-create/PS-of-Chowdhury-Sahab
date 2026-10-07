package app.personal.banglaai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.personal.banglaai.ai.LocalLlm
import app.personal.banglaai.ai.Prompts
import app.personal.banglaai.doc.DocWorkflow
import app.personal.banglaai.speech.ReadAloudController
import app.personal.banglaai.speech.ReadingPlan
import app.personal.banglaai.storage.VaultDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * "Tap a passage to ask": the page text is shown in short passages; each has "বোঝান" (simple Bengali explanation) and "প্রশ্ন" (your own question about
 * just that passage). Runs only when a button is pressed, on the local model only; the passage is passed as data (<DOC>), never as instructions.
 */
@Composable
fun SegmentExplainer(doc: VaultDoc, wf: DocWorkflow, page: Int, total: Int) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var useTr by remember { mutableStateOf(false) }
    val segs = remember(page, useTr) { ReadingPlan({ p -> (if (useTr) wf.translations.get(doc.id, p) else wf.texts.get(doc.id, p))?.text }, total).segments(page) }
    var sel by remember(page, useTr) { mutableIntStateOf(-1) }
    var q by remember(page, useTr) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var answer by remember(page, useTr) { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    fun run(system: String, prompt: String) {
        if (busy) return
        val model = wf.usableModel() ?: run { err = "কোনো AI মডেল বসানো নেই"; return }
        busy = true; err = null; answer = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { LocalLlm(model, File(ctx.cacheDir, "llm")).use { it.ask(system, prompt) } } }
                .onSuccess { answer = it }.onFailure { err = it.message ?: it.javaClass.simpleName }
            busy = false
        }
    }

    HorizontalDivider()
    Text("অংশ ধরে বুঝুন ও প্রশ্ন করুন", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = !useTr, onClick = { useTr = false }, label = { Text("মূল লেখা") })
        FilterChip(selected = useTr, onClick = { useTr = true }, label = { Text("বাংলা অনুবাদ") })
    }
    if (segs.isEmpty()) Text("এই পৃষ্ঠায় এই লেখা নেই।", style = MaterialTheme.typography.bodySmall)
    segs.forEachIndexed { i, s ->
        Card(onClick = { sel = if (sel == i) -1 else i; answer = null }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (sel == i) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.text)
                if (sel == i) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(enabled = !busy, onClick = { run(Prompts.EXPLAIN_SYSTEM, Prompts.explain(s.text, null)) }) { Text("💡 বোঝান") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(q, { q = it }, label = { Text("এই অংশ নিয়ে প্রশ্ন") }, modifier = Modifier.weight(1f), maxLines = 3)
                        Button(enabled = !busy && q.isNotBlank(), onClick = { run(Prompts.ASSISTANT_SYSTEM, "<DOC>${Prompts.sanitize(s.text)}</DOC>\nউপরের অংশ সম্পর্কে প্রশ্ন: ${q.trim()}") }) { Text("প্রশ্ন") }
                    }
                    if (busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text("ভাবছি…", style = MaterialTheme.typography.bodySmall) }
                    err?.let { Text("❌ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    answer?.let { a -> SelectionContainer { Text(a) }; TextButton(onClick = { ReadAloudController.speakExplanation(ctx, a) }) { Text("🔊 শুনুন") } }
                }
            }
        }
    }
}
