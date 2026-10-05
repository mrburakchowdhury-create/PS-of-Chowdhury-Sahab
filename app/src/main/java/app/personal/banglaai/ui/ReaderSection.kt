package app.personal.banglaai.ui

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.personal.banglaai.doc.DocWorkflow
import app.personal.banglaai.speech.*
import app.personal.banglaai.storage.VaultDoc

/**
 * Read-aloud + press-to-talk. Reading starts only on a button/confirmed command. TTS audio is never fed to the recognizer:
 * the mic is opened only by a press and speech is stopped before listening, so the app cannot command itself.
 * Status: compiles; behaviour on the S24 Ultra (offline Bengali voice present? quality? mic) is device-test pending.
 */
@Composable
fun ReaderSection(doc: VaultDoc, wf: DocWorkflow, totalPages: Int) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("reading", Context.MODE_PRIVATE) }
    var state by remember { mutableStateOf(TtsReader.State.Initialising) }
    var current by remember { mutableStateOf<Segment?>(null) }
    var rate by remember { mutableFloatStateOf(1.0f) }
    var useTranslation by remember { mutableStateOf(true) }
    var pageInput by remember { mutableStateOf("1") }
    var transcript by remember { mutableStateOf("") }
    var cmd by remember { mutableStateOf<VoiceCommand?>(null) }
    var voiceMsg by remember { mutableStateOf<String?>(null) }
    var listening by remember { mutableStateOf(false) }

    val reader = remember { TtsReader(ctx, { state = it }, { seg, pos -> current = seg; pos?.let { prefs.edit().putString(doc.id, "${it.page}:${it.segment}").apply() } }) }
    val voice = remember { VoiceInput(ctx) }
    DisposableEffect(Unit) { onDispose { reader.shutdown(); voice.stop() } }   // speech and mic are released when leaving

    fun plan() = ReadingPlan({ p -> (if (useTranslation) wf.translations.get(doc.id, p) else wf.texts.get(doc.id, p))?.text }, totalPages)
    fun play(page: Int, seg: Int = 0) {
        val pl = plan(); val start = if (seg == 0) pl.startAt(page) else ReadingPosition(page, seg)
        if (start == null) voiceMsg = "এই পৃষ্ঠা থেকে পড়ার মতো ${if (useTranslation) "অনুবাদ" else "লেখা"} নেই" else { voiceMsg = null; reader.play(pl, start) }
    }
    fun run(c: VoiceCommand) {
        when (c) {
            is VoiceCommand.ReadPage -> { pageInput = c.page.toString(); play(c.page) }
            VoiceCommand.Resume -> if (state == TtsReader.State.Paused) reader.resume() else {
                prefs.getString(doc.id, null)?.split(":")?.let { play(it[0].toInt(), it[1].toInt()) } ?: play(pageInput.toIntOrNull() ?: 1) }
            VoiceCommand.Pause -> reader.pause(); VoiceCommand.Stop -> reader.stop()
            VoiceCommand.Slower -> { rate = (rate - 0.15f).coerceAtLeast(0.5f); reader.setRate(rate) }
            VoiceCommand.Faster -> { rate = (rate + 0.15f).coerceAtMost(2f); reader.setRate(rate) }
            VoiceCommand.Repeat -> reader.position()?.let { play(it.page, it.segment) }
            else -> {}
        }
        cmd = null
    }
    fun startListening() {
        reader.pause()   // never listen while speaking: prevents the app hearing (and obeying) its own voice
        listening = true; voiceMsg = null
        voice.listen({ t -> listening = false; transcript = t; val c = VoiceCommands.parse(t); if (VoiceCommands.safeWithoutConfirm(c)) run(c) else cmd = c }, { e -> listening = false; voiceMsg = e })
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) startListening() else voiceMsg = "মাইক্রোফোনের অনুমতি ছাড়া ভয়েস কমান্ড চলবে না। বাটন/লেখা দিয়ে কাজ চালান।" }

    HorizontalDivider()
    Text("পড়ে শোনানো (অফলাইন বাংলা voice)", style = MaterialTheme.typography.titleSmall)
    when (state) {
        TtsReader.State.Initialising -> Text("ভয়েস ইঞ্জিন চালু হচ্ছে…")
        TtsReader.State.NoEngine -> Text("এই ফোনে কোনো text-to-speech ইঞ্জিন চালু নেই। সেটিংস → ভাষা → Text-to-speech।", color = MaterialTheme.colorScheme.error)
        TtsReader.State.NoOfflineBengali -> Text(
            (if (reader.onlyNetworkBengaliExists) "বাংলা voice আছে কিন্তু ইন্টারনেট লাগে — আপনার নিয়মে তা ব্যবহার করা হয়নি। " else "ফোনে বাংলা voice নেই। ") +
                "অফলাইন বাংলা voice নামান: সেটিংস → ভাষা → Text-to-speech → (Google) ইঞ্জিনের সেটিংস → ভয়েস ডেটা ইনস্টল → বাংলা। তারপর অ্যাপে ফিরে আসুন।", color = MaterialTheme.colorScheme.error)
        else -> {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = useTranslation, onClick = { useTranslation = true }, label = { Text("বাংলা অনুবাদ") })
                FilterChip(selected = !useTranslation, onClick = { useTranslation = false }, label = { Text("বের করা মূল লেখা") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(value = pageInput, onValueChange = { pageInput = it.filter(Char::isDigit).take(5) }, label = { Text("পৃষ্ঠা") }, singleLine = true, modifier = Modifier.width(110.dp))
                Button(onClick = { play(pageInput.toIntOrNull() ?: 1) }) { Text("▶ পড়ুন") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { reader.previous() }) { Text("⏮") }
                OutlinedButton(enabled = state == TtsReader.State.Playing, onClick = { reader.pause() }) { Text("⏸ থামান") }
                OutlinedButton(enabled = state == TtsReader.State.Paused, onClick = { reader.resume() }) { Text("▶ চালু") }
                OutlinedButton(onClick = { reader.next() }) { Text("⏭") }
                OutlinedButton(onClick = { reader.stop() }) { Text("⏹") }
            }
            Text("গতি: ${"%.2f".format(rate)}×", style = MaterialTheme.typography.bodySmall)
            Slider(value = rate, onValueChange = { rate = it; reader.setRate(it) }, valueRange = 0.5f..2f)
            current?.let { Text("এখন পড়া হচ্ছে — পৃষ্ঠা ${it.page}:", style = MaterialTheme.typography.bodySmall); Surface(color = MaterialTheme.colorScheme.secondaryContainer) { Text(it.text, Modifier.padding(8.dp)) } }
            prefs.getString(doc.id, null)?.let { Text("শেষ পড়ার অবস্থান: পৃষ্ঠা ${it.substringBefore(':')}", style = MaterialTheme.typography.bodySmall) }
        }
    }
    voiceMsg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

    Text("ভয়েস কমান্ড (চেপে বলুন; শুধু ফোনের ভেতরে চেনা হয়)", style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Button(enabled = !listening, onClick = { micPerm.launch(Manifest.permission.RECORD_AUDIO) }) { Text(if (listening) "🎤 শুনছি…" else "🎤 চেপে বলুন") }
        if (listening) OutlinedButton(onClick = { voice.stop(); listening = false }) { Text("মাইক বন্ধ") }
    }
    OutlinedTextField(value = transcript, onValueChange = { transcript = it; cmd = VoiceCommands.parse(it) }, label = { Text("কমান্ড (লিখেও দিতে পারেন / শোনা লেখা ঠিক করুন)") }, modifier = Modifier.fillMaxWidth())
    Button(enabled = transcript.isNotBlank(), onClick = { cmd = VoiceCommands.parse(transcript) }) { Text("কমান্ড বুঝে নিন") }
    cmd?.let { c ->
        val label = when (c) {
            is VoiceCommand.ReadPage -> "পৃষ্ঠা ${c.page} পড়া"; VoiceCommand.Resume -> "আগের জায়গা থেকে পড়া"; VoiceCommand.Pause -> "থামানো"; VoiceCommand.Stop -> "বন্ধ করা"
            VoiceCommand.Slower -> "আস্তে পড়া"; VoiceCommand.Faster -> "দ্রুত পড়া"; VoiceCommand.Repeat -> "আবার পড়া"
            is VoiceCommand.Ambiguous -> "অস্পষ্ট: ${c.reason}"; is VoiceCommand.Unknown -> "বুঝতে পারিনি"
        }
        Text("বোঝা গেছে: $label", style = MaterialTheme.typography.bodySmall)
        if (c !is VoiceCommand.Ambiguous && c !is VoiceCommand.Unknown) Button(onClick = { run(c) }) { Text("নিশ্চিত — করুন") }
    }
}
