package app.personal.banglaai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.personal.banglaai.ai.LocalLlm
import app.personal.banglaai.ai.Prompts
import app.personal.banglaai.doc.DocWorkflow
import app.personal.banglaai.knowledge.Dictionary
import app.personal.banglaai.speech.ReadAloudController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Look a word up: the OFFLINE word list answers at once (আভিধানিক অর্থ — no typing of the Bengali, no AI needed), and the sentence the word sits in
 * can be sent to the local model for the contextual meaning (পারিবেশিক অর্থ). The word is either typed here or arrives pre-filled from the page text
 * ([initialTerm]/[context]) so nobody has to type a word they are already looking at.
 */
@Composable
fun DictionaryPanel(wf: DocWorkflow, cacheDir: File, initialTerm: String = "", context: String = "", docId: String? = null, page: Int? = null, title: String? = "Dictionary") {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var term by remember(initialTerm) { mutableStateOf(initialTerm) }
    var sentence by remember(context) { mutableStateOf(context) }
    var ai by remember(initialTerm) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }; var err by remember { mutableStateOf<String?>(null) }; var saved by remember(initialTerm) { mutableStateOf(false) }
    val data by produceState<Dictionary.Data?>(null) { value = withContext(Dispatchers.IO) { runCatching { Dictionary.get(ctx) }.getOrNull() } }
    val entry = remember(term, data) { data?.lookup(term) }
    title?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
    OutlinedTextField(term, { term = it; ai = null; saved = false }, label = { Text("Word (English or Bengali)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    if (term.isNotBlank()) {
        Text("আভিধানিক অর্থ (অফলাইন শব্দতালিকা)", style = MaterialTheme.typography.titleSmall)
        when {
            data == null -> Text("তালিকা খোলা হচ্ছে…", style = MaterialTheme.typography.bodySmall)
            entry == null -> Text("এই শব্দ অফলাইন তালিকায় পাওয়া যায়নি (তালিকা ছোট — সব শব্দ নেই)। নিচে ‘Meaning in this sentence’ দিয়ে AI-র কাছে জানতে পারেন।", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                if (!entry.found.equals(entry.asked.trim(), true)) Text("(“${entry.asked.trim()}” নেই; “${entry.found}” দেখানো হলো)", style = MaterialTheme.typography.bodySmall)
                SelectionContainer { Text(if (entry.lang == Dictionary.Lang.ENGLISH) (if (entry.curated) "★ " else "") + entry.meanings.joinToString("  ·  ") else (entry.pos?.let { "[$it] " }.orEmpty() + entry.meanings.joinToString("; ")), style = MaterialTheme.typography.bodyLarge) }
            }
        }
        Text("উৎস: Wiktionary (CC BY-SA 4.0) + ★ চিহ্নিত শব্দে হাতে-বাছাই (AI-সহায়তায় তৈরি, ভুল থাকতে পারে) অর্থ। তালিকা অসম্পূর্ণ; অর্থ বাক্যের সঙ্গে না-ও মিলতে পারে।", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (context.isBlank()) OutlinedTextField(sentence, { sentence = it }, label = { Text("Sentence containing the word (optional)") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
        Button(enabled = !busy, onClick = {
            val model = wf.usableModel() ?: run { err = "AI model নেই — হোম → AI Model"; return@Button }
            ReadAloudController.pause(); busy = true; err = null; ai = null
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { LocalLlm(model, File(cacheDir, "llm")).use { it.ask(Prompts.WORD_SYSTEM, Prompts.wordMeaning(term, sentence)) } } }
                    .onSuccess { ai = it }.onFailure { err = it.message ?: it.javaClass.simpleName }
                busy = false
            }
        }) { Text(if (busy) "ভাবছে…" else "Meaning in this sentence (AI)") }
        err?.let { Text("❌ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        ai?.let { a ->
            Text("পারিবেশিক অর্থ (AI — ভুল হতে পারে)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary)
            SelectionContainer { Text(a) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { ReadAloudController.speakExplanation(ctx, a) }) { Text("🔊 Listen") }
                if (docId != null && page != null) OutlinedButton(enabled = !saved, onClick = { wf.vocab.add(term, a.take(1500), docId, page); saved = true }) { Text(if (saved) "✅ Saved" else "Save word") }
            }
        }
    }
}
