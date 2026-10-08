package app.personal.banglaai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.personal.banglaai.doc.DocWorkflow
import app.personal.banglaai.speech.ReadAloudController
import app.personal.banglaai.userdata.SavedQaItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun kindBn(k: String) = when (k) { "doc" -> "ফাইলের উত্তর"; "library" -> "লাইব্রেরির উত্তর"; "knowledge" -> "জ্ঞানভাণ্ডার"; "code" -> "কোড-ব্যাখ্যা"; else -> "সহকারী" }

/** Saved question/answer pairs (only what the owner chose to save). [doc] = show only that file's items; null = all. [onOpen] reopens the original source page. */
@Composable
fun SavedQaSection(wf: DocWorkflow, doc: app.personal.banglaai.storage.VaultDoc?, onOpen: (docId: String, page: Int) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var items by remember { mutableStateOf<List<SavedQaItem>>(emptyList()) }
    fun reload() { items = runCatching { if (doc != null) wf.savedQa.forDoc(doc.id) else wf.savedQa.all() }.getOrDefault(emptyList()) }
    LaunchedEffect(doc?.id) { reload() }
    HorizontalDivider()
    Text("সংরক্ষিত প্রশ্নোত্তর${if (items.isEmpty()) "" else " (${items.size}টি)"}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    if (items.isEmpty()) Text("এখনো কিছু সংরক্ষণ করা হয়নি। উত্তরের নিচে '💾 প্রশ্নোত্তর সংরক্ষণ' চাপলে এখানে আসবে।", style = MaterialTheme.typography.bodySmall)
    items.take(50).forEach { x ->
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${kindBn(x.kind)} · ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(x.at))}", style = MaterialTheme.typography.labelMedium)
            Text("প্রশ্ন: ${x.question}", style = MaterialTheme.typography.titleSmall)
            SelectionContainer { Text(x.answer, style = MaterialTheme.typography.bodySmall) }
            if (x.sources.isNotEmpty()) Text("উৎস: " + x.sources.joinToString("; "), style = MaterialTheme.typography.bodySmall)
            Text("এটি সংরক্ষিত AI-উত্তর (অনুমান থাকতে পারে) — উদ্ধৃতি হিসেবে নিতে হলে উৎসে মিলিয়ে নিন।", style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (x.docId.isNotBlank() && x.page > 0) TextButton(onClick = { onOpen(x.docId, x.page) }) { Text("উৎস খুলুন (পৃষ্ঠা ${x.page})") }
                TextButton(onClick = { ReadAloudController.speakExplanation(ctx, x.answer) }) { Text("🔊 শুনুন") }
                TextButton(onClick = { wf.savedQa.delete(x.id); reload() }) { Text("মুছুন") }
            }
        } }
    }
}

/** The "save" button shown under an answer. */
@Composable
fun SaveQaButton(wf: DocWorkflow, kind: String, question: String, answer: String?, sources: List<String>, docId: String = "", page: Int = 0) {
    if (answer.isNullOrBlank() || question.isBlank()) return
    var saved by remember(answer) { mutableStateOf(false) }
    OutlinedButton(enabled = !saved, onClick = { runCatching { wf.savedQa.add(kind, question, answer, sources, docId, page) }.onSuccess { saved = true } }) { Text(if (saved) "✅ সংরক্ষিত" else "💾 প্রশ্নোত্তর সংরক্ষণ") }
}
