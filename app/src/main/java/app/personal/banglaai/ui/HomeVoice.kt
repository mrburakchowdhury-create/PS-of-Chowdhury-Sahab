package app.personal.banglaai.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.personal.banglaai.speech.*
import app.personal.banglaai.storage.VaultDoc

/**
 * Home-screen voice/typed commands: "ফাইল খোলো …" then reading/explaining happens inside the document screen.
 * One press = one utterance, on-device recognizer only. The transcript and the interpreted action are always shown; the file opens only after
 * the owner taps confirm (or taps one of several matching names). The app cannot be opened or unlocked by voice from outside — see the user guide (Bixby).
 */
@Composable
fun HomeVoice(docs: List<VaultDoc>, onOpen: (VaultDoc, VoiceCommand?) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val voice = remember { VoiceInput(ctx) }
    DisposableEffect(Unit) { onDispose { voice.stop() } }
    var transcript by remember { mutableStateOf("") }
    var cmd by remember { mutableStateOf<VoiceCommand?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var listening by remember { mutableStateOf(false) }

    fun interpret(text: String) {
        val c = VoiceCommands.parse(text); cmd = c; msg = null
        if (VoiceCommands.safeWithoutConfirm(c)) { if (c == VoiceCommand.Stop) ReadAloudController.stop() else ReadAloudController.pause(); cmd = null }
    }
    fun listen() {
        ReadAloudController.pause()   // never listen while the app is speaking: it must not hear and obey its own voice
        listening = true; msg = null
        voice.listen({ t -> listening = false; transcript = t; interpret(t) }, { e -> listening = false; msg = e })
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) listen() else msg = "মাইক্রোফোনের অনুমতি ছাড়া ভয়েস চলবে না। নিচের ঘরে লিখে নির্দেশ দিন।" }

    SectionTitle("ভয়েস নির্দেশ")
    Text("যেমন: “ইতিহাস ফাইলটা খোলো এবং পনেরো নম্বর পৃষ্ঠা থেকে পড়া শুরু করো”। বলার পর লেখা দেখে নিশ্চিত করতে হয়।", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Button(enabled = !listening, onClick = { micPerm.launch(Manifest.permission.RECORD_AUDIO) }) { Text(if (listening) "🎤 শুনছি…" else "🎤 চেপে বলুন") }
        if (listening) OutlinedButton(onClick = { voice.stop(); listening = false }) { Text("মাইক বন্ধ") }
    }
    OutlinedTextField(transcript, { transcript = it }, label = { Text("নির্দেশ (লিখেও দিতে পারেন / শোনা লেখা ঠিক করুন)") }, modifier = Modifier.fillMaxWidth())
    Button(enabled = transcript.isNotBlank(), onClick = { interpret(transcript) }) { Text("নির্দেশ বুঝে নিন") }
    msg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    cmd?.let { c ->
        Text("বোঝা গেছে: ${describe(c)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        when (c) {
            is VoiceCommand.OpenFile -> when (val r = FileMatcher.find(c.query, docs.map { it.name })) {
                FileMatcher.Result.None -> Text(if (docs.isEmpty()) "এখনো কোনো ফাইল যোগ করা নেই।" else "এই নামে কোনো ফাইল পাওয়া যায়নি।", color = MaterialTheme.colorScheme.error)
                is FileMatcher.Result.One -> Button(onClick = { onOpen(docs.first { it.name == r.name }, c.then); cmd = null }) { Text("নিশ্চিত — “${r.name}” খুলুন") }
                is FileMatcher.Result.Many -> { Text("একাধিক ফাইল মিলেছে — কোনটি?"); r.names.forEach { n -> OutlinedButton(onClick = { onOpen(docs.first { it.name == n }, c.then); cmd = null }, modifier = Modifier.fillMaxWidth()) { Text(n) } } }
            }
            is VoiceCommand.Ambiguous, is VoiceCommand.Unknown -> {}
            VoiceCommand.Resume, VoiceCommand.Slower, VoiceCommand.Faster, VoiceCommand.Repeat, is VoiceCommand.ReadPage, is VoiceCommand.ReadChapter, VoiceCommand.Explain ->
                Text("এই নির্দেশের জন্য আগে ফাইল খুলতে হবে। বলুন: “… ফাইলটা খোলো”।", style = MaterialTheme.typography.bodySmall)
            else -> {}
        }
    }
}
