package app.personal.banglaai.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.personal.banglaai.ai.LocalLlm
import app.personal.banglaai.ai.Prompts
import app.personal.banglaai.doc.DocWorkflow
import app.personal.banglaai.knowledge.KItem
import app.personal.banglaai.knowledge.KnowledgeBase
import app.personal.banglaai.speech.ReadAloudController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * জ্ঞানভাণ্ডার: built-in offline question-and-answer folders (যৌনশিক্ষা, রাজনীতি, কূটনীতি, কলাকৌশল, লেখকের জন্য). Search works without the AI;
 * "AI-কে জিজ্ঞাসা" gives the best matching cards to the local model as reference text and says so. Cards can be read aloud or saved as TXT for the owner's book drafts.
 * These are general-information summaries written for this app — not medical, legal or religious rulings, and not copies of any book.
 */
@Composable
fun KnowledgeScreen(wf: DocWorkflow, cacheDir: File, onBack: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var cat by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }
    val items = remember(cat, query) { KnowledgeBase.search(ctx, query, cat, 60) }
    var ask by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(KnowledgeBase.exportText(items).toByteArray(Charsets.UTF_8)) }
    }

    fun askModel() {
        if (busy || ask.isBlank()) return
        val model = wf.usableModel() ?: run { err = "কোনো AI মডেল বসানো নেই — হোমে 'AI মডেল' থেকে বসান।"; return }
        busy = true; err = null; answer = null
        val refs = KnowledgeBase.search(ctx, ask, cat, 3)
        scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val m = LocalLlm(model, File(cacheDir, "llm"))
                val doc = refs.joinToString("\n\n") { "প্রশ্ন: ${Prompts.sanitize(it.q)}\nউত্তর: ${Prompts.sanitize(it.a)}" }
                m.ask(Prompts.ASSISTANT_SYSTEM, "<DOC>$doc</DOC>\nউপরের সহায়ক তথ্য (অ্যাপের জ্ঞানভাণ্ডার) দেখে প্রশ্নের উত্তর দাও। তথ্যে না থাকলে নিজের সাধারণ জ্ঞান থেকে বলো এবং সেটা স্পষ্ট জানাও; নিশ্চিত না হলে বলো। প্রশ্ন: ${Prompts.sanitize(ask.trim())}")
            } }.onSuccess { answer = it }.onFailure { err = it.message ?: it.javaClass.simpleName }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = onBack) { Text("← ফিরে যান") }; Text("জ্ঞানভাণ্ডার", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f)) }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = cat == null, onClick = { cat = null }, label = { Text("সব") })
            KnowledgeBase.categories.forEach { c -> FilterChip(selected = cat == c.id, onClick = { cat = c.id }, label = { Text(c.label) }) }
        }
        KnowledgeBase.categories.firstOrNull { it.id == cat }?.let { Text(it.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
            ?: Text("অফলাইন প্রশ্ন-উত্তর সংকলন: তথ্যমূলক সারকথা, চিকিৎসা/আইনি/ধর্মীয় সিদ্ধান্ত নয়। ${KnowledgeBase.all(ctx).size}টি কার্ড।", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(query, { query = it }, label = { Text("খুঁজুন (যেমন: সম্মতি, ম্যাকিয়াভেলি, দর-কষাকষি)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (items.isEmpty()) item { Text("কিছু পাওয়া যায়নি। অন্য শব্দ লিখুন, বা নিচে AI-কে জিজ্ঞাসা করুন।", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(items, key = { it.id }) { k -> KCard(k, open == k.id, { open = if (open == k.id) null else k.id }) { ReadAloudController.speakExplanation(ctx, "${k.q}। ${k.a}") } }
            item {
                Spacer(Modifier.height(6.dp))
                Text("AI-কে জিজ্ঞাসা (মডেল লাগে)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text("আপনার প্রশ্নের সবচেয়ে মিল-থাকা ৩টি কার্ড AI-কে সহায়ক তথ্য হিসেবে দেওয়া হয়। উত্তর যাচাই করে নিন; স্বাস্থ্য/আইন/ধর্মীয় বিষয়ে বিশেষজ্ঞ দেখান।", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(ask, { ask = it }, label = { Text("আপনার প্রশ্ন") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(enabled = !busy && ask.isNotBlank(), onClick = { askModel() }) { Text(if (busy) "ভাবছি…" else "জিজ্ঞাসা করুন") }
                    OutlinedButton(enabled = items.isNotEmpty(), onClick = { saver.launch("জ্ঞানভাণ্ডার.txt") }) { Text("এই তালিকা TXT-এ রাখুন") }
                }
                err?.let { Text("❌ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                answer?.let { a -> SelectionContainer { Text(a) }; TextButton(onClick = { ReadAloudController.speakExplanation(ctx, a) }) { Text("🔊 শুনুন") } }
            }
        }
    }
}

@Composable
private fun KCard(k: KItem, expanded: Boolean, toggle: () -> Unit, speak: () -> Unit) {
    Card(onClick = toggle, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (expanded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(k.topic, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(k.q, style = MaterialTheme.typography.titleSmall)
            if (expanded) {
                SelectionContainer { Text(k.a) }
                if (k.ref.isNotBlank()) Text(k.ref, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = speak) { Text("🔊 শুনুন") }
            }
        }
    }
}
