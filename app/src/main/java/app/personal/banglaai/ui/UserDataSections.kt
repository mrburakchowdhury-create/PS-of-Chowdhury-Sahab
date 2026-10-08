package app.personal.banglaai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.personal.banglaai.doc.DocWorkflow
import app.personal.banglaai.storage.VaultDoc
import app.personal.banglaai.userdata.GlossaryEntry
import app.personal.banglaai.userdata.Mark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Personal terminology list used by every translation: only entries whose source word appears in the text being translated are sent to the model. */
@Composable
fun GlossarySection(wf: DocWorkflow) {
    var items by remember { mutableStateOf<List<GlossaryEntry>>(emptyList()) }
    var src by remember { mutableStateOf("") }; var bn by remember { mutableStateOf("") }; var dom by remember { mutableStateOf("") }; var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { items = runCatching { wf.glossary.all() }.getOrDefault(emptyList()) }
    Text("Glossary — অনুবাদে একই শব্দের একই বাংলা", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    val dictCtx = androidx.compose.ui.platform.LocalContext.current
    val dict by produceState<app.personal.banglaai.knowledge.Dictionary.Data?>(null) { value = withContext(Dispatchers.IO) { runCatching { app.personal.banglaai.knowledge.Dictionary.get(dictCtx) }.getOrNull() } }
    val suggestions = remember(src, dict) { if (src.isBlank()) emptyList() else dict?.bengaliFor(src).orEmpty().take(6) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(src, { src = it }, label = { Text("মূল শব্দ") }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(bn, { bn = it }, label = { Text("বাংলা") }, singleLine = true, modifier = Modifier.weight(1f))
    }
    OutlinedTextField(dom, { dom = it }, label = { Text("কোন প্রসঙ্গে? (ঐচ্ছিক — যেমন: ব্যাংক, নদী)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    if (suggestions.isNotEmpty()) {
        Text("Tap a suggestion to fill the Bengali:", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { suggestions.forEach { sgt -> AssistChip(onClick = { bn = sgt }, label = { Text(sgt) }) } }
    }
    Button(onClick = { runCatching { wf.glossary.add(src, bn, dom) }.onSuccess { src = ""; bn = ""; dom = ""; err = null; items = wf.glossary.all() }.onFailure { err = it.message } }) { Text("Add") }
    err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    items.forEach { e -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text("${e.source} → ${e.bengali}" + (if (e.domain.isBlank()) "" else " [${e.domain}]"), Modifier.weight(1f)); TextButton(onClick = { wf.glossary.remove(e.source, e.domain); items = wf.glossary.all() }) { Text("Delete") } } }
}

/** Bookmarks and personal notes for the page chosen in the viewer. Notes are yours, kept separate from the document text. */
@Composable
fun MarksSection(doc: VaultDoc, wf: DocWorkflow, page: Int?, onOpenPage: (Int) -> Unit) {
    var items by remember { mutableStateOf<List<Mark>>(emptyList()) }
    var note by remember { mutableStateOf("") }
    LaunchedEffect(doc.id) { items = runCatching { wf.marks.forDoc(doc.id) }.getOrDefault(emptyList()) }
    HorizontalDivider()
    Text("বুকমার্ক ও নোট", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    if (page == null) Text("উপরে একটি বৈধ পৃষ্ঠা নম্বর দিন", style = MaterialTheme.typography.bodySmall) else {
        OutlinedTextField(note, { note = it }, label = { Text("পৃষ্ঠা $page-এর জন্য নোট") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { wf.marks.add(doc.id, page, Mark.Kind.BOOKMARK, ""); items = wf.marks.forDoc(doc.id) }) { Text("🔖 বুকমার্ক") }
            Button(enabled = note.isNotBlank(), onClick = { wf.marks.add(doc.id, page, Mark.Kind.NOTE, note); note = ""; items = wf.marks.forDoc(doc.id) }) { Text("নোট রাখুন") }
        }
    }
    items.forEach { m -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        TextButton(onClick = { onOpenPage(m.page) }) { Text("পৃষ্ঠা ${m.page}") }
        Text(if (m.kind == Mark.Kind.BOOKMARK) "🔖" else "📝 ${m.text}", Modifier.weight(1f))
        TextButton(onClick = { wf.marks.delete(m.id); items = wf.marks.forDoc(doc.id) }) { Text("Delete") } } }
}
