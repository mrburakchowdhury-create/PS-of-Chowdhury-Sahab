package app.personal.banglaai.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.personal.banglaai.doc.DocWorkflow
import app.personal.banglaai.speech.BengaliVoices
import app.personal.banglaai.speech.VoiceInput

/** One line of the checklist: what is needed, whether it is ready, and (if not) the one button that fixes it. */
private data class Need(val ok: Boolean?, val title: String, val why: String, val fixLabel: String?, val fix: (() -> Unit)?)

/**
 * "What is missing" panel for screens that need the AI model, the microphone, the phone's offline speech recogniser or an offline Bengali voice.
 * Without it a missing piece only showed up as a tiny grey sentence after the first tap, so a button seemed to "do nothing". Each missing
 * piece has its own fix button; nothing is downloaded by this app (it has no internet permission) — downloads are done by the phone's own services.
 */
@Composable
fun ReadinessPanel(wf: DocWorkflow, needModel: Boolean, needMic: Boolean, needHearing: Boolean, needSpeaking: Boolean, onGoModel: () -> Unit, onChanged: () -> Unit = {}) {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val hasModel = remember(refresh) { wf.usableModel() != null }
    var micOk by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> micOk = ok; refresh++; onChanged() }
    val voice = remember { VoiceInput(ctx) }
    DisposableEffect(Unit) { onDispose { voice.stop() } }
    val hearing by produceState<Pair<Boolean?, String>>(null to "", refresh) {
        value = when {
            !voice.available() -> false to "এই ফোনে অফলাইন (on-device) ভয়েস-চেনা সেবা নেই"
            android.os.Build.VERSION.SDK_INT < 33 -> true to "আছে (বাংলা প্যাক আছে কি না প্রথম ব্যবহারে বোঝা যাবে)"
            else -> { var r: Pair<Boolean?, String> = null to "দেখা হচ্ছে…"
                voice.packStatus("bn-BD") { inst, _, err -> r = if (err != null) (null to err) else if (inst.any { it.lowercase().startsWith("bn") }) (true to "বাংলা অফলাইন প্যাক ইনস্টল আছে (${inst.filter { it.lowercase().startsWith("bn") }.joinToString()})") else (false to "বাংলা অফলাইন প্যাক ইনস্টল নেই (আছে: ${inst.joinToString().ifBlank { "কিছু নেই" }})") }
                kotlinx.coroutines.delay(1500); r }
        }
    }
    val speaking by produceState<Pair<Boolean?, String>>(null to "দেখা হচ্ছে…", refresh) {
        var t: TextToSpeech? = null; var got = false
        t = TextToSpeech(ctx.applicationContext) { st ->
            val n = if (st == TextToSpeech.SUCCESS) t?.let { BengaliVoices.offline(it).size } ?: 0 else -1
            value = if (n < 0) (false to "ফোনে কোনো Text-to-speech ইঞ্জিন চালু নেই") else if (n == 0) (false to "অফলাইন বাংলা কণ্ঠ ইনস্টল নেই") else (true to "অফলাইন বাংলা কণ্ঠ আছে ($n টি)")
            got = true; t?.shutdown()
        }
        kotlinx.coroutines.delay(4000); if (!got) value = null to "সাড়া পাওয়া যায়নি"; t?.shutdown()
    }
    val needs = buildList {
        if (needModel) add(Need(hasModel, "AI model", if (hasModel) "বসানো আছে" else "বসানো নেই — এটা ছাড়া অনুবাদ, উত্তর, কল, লাইভ কিছুই কাজ করবে না (অ্যাপ আনইনস্টল করলে মডেলও মুছে যায়)", if (hasModel) null else "Open AI Model", if (hasModel) null else onGoModel))
        if (needMic) add(Need(micOk, "Microphone permission", if (micOk) "দেওয়া আছে" else "দেওয়া হয়নি", if (micOk) null else "Allow microphone") { micPerm.launch(Manifest.permission.RECORD_AUDIO) })
        if (needHearing) add(Need(hearing.first, "Speech recognition (offline Bengali)", hearing.second,
            if (hearing.first == true) null else "Download Bengali pack") {
            val ok = voice.downloadPack("bn-BD")
            if (!ok) ctx.startActivity(Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            refresh++
        })
        if (needSpeaking) add(Need(speaking.first, "Bengali voice (speaks answers)", speaking.second, if (speaking.first == true) null else "Install voice") {
            runCatching { ctx.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { runCatching { ctx.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        })
    }
    if (needs.all { it.ok == true }) return
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Before you start", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
            needs.forEach { n ->
                Text("${when (n.ok) { true -> "✅"; false -> "❌"; null -> "⏳" }} ${n.title} — ${n.why}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                if (n.ok != true && n.fix != null && n.fixLabel != null) Button(onClick = n.fix) { Text(n.fixLabel) }
            }
            OutlinedButton(onClick = { refresh++; onChanged() }) { Text("Check again") }
        }
    }
}
