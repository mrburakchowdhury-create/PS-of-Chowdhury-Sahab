package app.personal.banglaai.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.personal.banglaai.MainActivity
import app.personal.banglaai.security.PinStore
import app.personal.banglaai.doc.DocWorkflow
import app.personal.banglaai.storage.KnownModels
import app.personal.banglaai.storage.ModelStore
import app.personal.banglaai.storage.Vault
import app.personal.banglaai.storage.VaultDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun App(activity: MainActivity, unlocked: Boolean, onUnlocked: () -> Unit, onLock: () -> Unit) {
    MaterialTheme(colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            val pins = remember { PinStore(activity) }
            var pinSet by remember { mutableStateOf(pins.isSet()) }
            when {
                !pinSet -> SetupScreen(pins) { pinSet = true; onUnlocked() }
                !unlocked -> UnlockScreen(activity, pins, onUnlocked)
                else -> HomeScreen(activity, onLock)
            }
        }
    }
}

@Composable
private fun SetupScreen(pins: PinStore, done: () -> Unit) {
    var a by remember { mutableStateOf("") }; var b by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(24.dp).systemBarsPadding(), verticalArrangement = Arrangement.Center) {
        Text("প্রথম সেটআপ", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("অ্যাপের জন্য একটি PIN ঠিক করুন (কমপক্ষে ৬ সংখ্যা)। এই PIN কোথাও পাঠানো হয় না এবং ভুলে গেলে ফিরে পাওয়ার কোনো গোপন পথ নেই। আপনার তথ্য মোছা হবে না, কিন্তু PIN ছাড়া অ্যাপ খুলবে না।",
            style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        PinField("নতুন PIN", a) { a = it }
        Spacer(Modifier.height(8.dp))
        PinField("আবার PIN লিখুন", b) { b = it }
        err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            when {
                a.length < 6 -> err = "PIN কমপক্ষে ৬ সংখ্যার হতে হবে"
                a != b -> err = "দুটি PIN মেলেনি"
                else -> { pins.set(a.toCharArray()); done() }
            }
        }, Modifier.fillMaxWidth()) { Text("PIN সেট করুন") }
    }
}

@Composable
private fun PinField(label: String, value: String, onChange: (String) -> Unit) = OutlinedTextField(
    value = value, onValueChange = { onChange(it.filter(Char::isDigit).take(12)) }, label = { Text(label) },
    singleLine = true, visualTransformation = PasswordVisualTransformation(),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth(),
)

@Composable
private fun UnlockScreen(activity: MainActivity, pins: PinStore, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var wait by remember { mutableStateOf(pins.lockedSeconds()) }
    LaunchedEffect(wait) { if (wait > 0) { delay(1000); wait = pins.lockedSeconds() } }
    val bio = remember { activity.canUseStrongBiometric() }
    Column(Modifier.fillMaxSize().padding(24.dp).systemBarsPadding(), verticalArrangement = Arrangement.Center) {
        Text("🔒 অ্যাপ বন্ধ আছে", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        PinField("PIN", pin) { pin = it }
        msg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (wait > 0) Text("$wait সেকেন্ড পরে আবার চেষ্টা করুন", color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Button(enabled = wait == 0L && pin.isNotEmpty(), modifier = Modifier.fillMaxWidth(), onClick = {
            when (val r = pins.verify(pin.toCharArray())) {
                PinStore.Result.Ok -> onUnlocked()
                is PinStore.Result.Wrong -> msg = "ভুল PIN (আর ${r.attemptsBeforeDelay}বার পর অপেক্ষা শুরু হবে)"
                is PinStore.Result.Locked -> { wait = r.seconds; msg = null }
            }
            pin = ""
        }) { Text("খুলুন") }
        if (bio) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                activity.promptBiometric("আঙুলের ছাপ / ফেস দিয়ে খুলুন", "PIN ব্যবহার করুন", onUnlocked) { msg = it }
            }) { Text("বায়োমেট্রিক দিয়ে খুলুন") }
            Text("মনে রাখবেন: ফোনে অন্য কারও আঙুলের ছাপ/ফেস নথিভুক্ত থাকলে সেও খুলতে পারবে। তা থাকলে শুধু PIN ব্যবহার করুন।",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HomeScreen(activity: MainActivity, onLock: () -> Unit) {
    val scope = rememberCoroutineScope()
    val vault = remember { Vault(activity) }; val models = remember { ModelStore(activity) }
    var docs by remember { mutableStateOf<List<VaultDoc>>(emptyList()) }
    var modelList by remember { mutableStateOf(models.list()) }
    val workflow = remember { DocWorkflow(activity, vault, models) }
    var openDoc by remember { mutableStateOf<VaultDoc?>(null) }
    var codeOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val refresh: suspend () -> Unit = { docs = withContext(Dispatchers.IO) { runCatching { vault.list() }.getOrDefault(emptyList()) }; modelList = withContext(Dispatchers.IO) { models.list() } }
    LaunchedEffect(Unit) { refresh() }

    val pickDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            busy = "ফাইল এনক্রিপ্ট করে রাখা হচ্ছে…"; error = null
            runCatching { withContext(Dispatchers.IO) { vault.import(uri) } }.onFailure { error = it.message ?: "ব্যর্থ" }
            busy = null; refresh()
        }
    }
    val pickModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            error = null
            runCatching { withContext(Dispatchers.IO) { models.import(uri) { busy = "মডেল কপি হচ্ছে… ${it / (1 shl 20)} MB" } } }.onFailure { error = it.message ?: "ব্যর্থ" }
            busy = null; refresh()
        }
    }

    openDoc?.let { d -> DocScreen(d, workflow) { openDoc = null }; return }
    if (codeOpen) { CodeScreen(workflow, activity.cacheDir, activity.noBackupFilesDir) { codeOpen = false }; return }
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("বাংলা সহকারী", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onLock) { Text("🔒 বন্ধ করুন") }
        }
        busy?.let { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(it) }
        error?.let { Text("❌ $it", color = MaterialTheme.colorScheme.error) }

        Button(onClick = { codeOpen = true }) { Text("কোড বিশ্লেষণ / ছবি থেকে কোড") }
        SectionTitle("আমার ফাইল (এনক্রিপ্টেড)")
        Button(onClick = { pickDoc.launch(arrayOf("*/*")) }, enabled = busy == null) { Text("ফাইল যোগ করুন") }
        if (docs.isEmpty()) Text("এখনো কোনো ফাইল নেই।")
        docs.forEach { d ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(d.name); Text(fmtSize(d.size), style = MaterialTheme.typography.bodySmall) }
                    TextButton(onClick = { openDoc = d }) { Text("খুলুন") }
                    TextButton(onClick = { scope.launch { withContext(Dispatchers.IO) { vault.delete(d.id); workflow.deleteDerived(d) }; refresh() } }) { Text("মুছুন") }
                }
            }
        }
        Text("যোগ করার পর নিজে থেকে কোনো অনুবাদ বা AI কাজ শুরু হয় না।", style = MaterialTheme.typography.bodySmall)

        SectionTitle("AI মডেল (ম্যানুয়ালি বসান)")
        Button(onClick = { pickModel.launch(arrayOf("*/*")) }, enabled = busy == null) { Text("মডেল ফাইল বসান") }
        Text("খালি জায়গা: ${fmtSize(models.freeBytes())}", style = MaterialTheme.typography.bodySmall)
        if (modelList.isEmpty()) Text("কোনো মডেল বসানো নেই — অনুবাদ/প্রশ্নোত্তর এখনো কাজ করবে না।")
        modelList.forEach { m ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name); Text(fmtSize(m.size), style = MaterialTheme.typography.bodySmall)
                        Text("SHA-256: ${m.sha256}", style = MaterialTheme.typography.bodySmall)
                        Text(when (val v = KnownModels.verdict(m.name, m.size, m.sha256)) {
                            is KnownModels.Verdict.Official -> "✅ অফিসিয়াল ফাইলের সঙ্গে মিলেছে: ${v.model.label}"
                            is KnownModels.Verdict.Mismatch -> "❌ নামের সঙ্গে মিললেও ফাইলের কনটেন্ট মেলেনি — নষ্ট বা বদলানো হতে পারে; ব্যবহার করবেন না"
                            KnownModels.Verdict.Unknown -> "⚠️ আমাদের যাচাই-করা তালিকায় নেই; অফিসিয়াল checksum নিজে মিলিয়ে নিন"
                        }, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { scope.launch { withContext(Dispatchers.IO) { models.delete(m.name) }; refresh() } }) { Text("মুছুন") }
                }
            }
        }
        Text("SHA-256 মিলিয়ে নিন মডেলের অফিসিয়াল পাতায় দেওয়া checksum-এর সঙ্গে।", style = MaterialTheme.typography.bodySmall)

        SectionTitle("ফিচারের অবস্থা (সৎ তালিকা)")
        listOf(
            "PIN + বায়োমেট্রিক লক, এনক্রিপ্টেড ফাইল সংরক্ষণ" to "তৈরি; ডিভাইসে পরীক্ষা বাকি",
            "PDF/TXT লেখা বের করা, পৃষ্ঠা-ম্যাপ, থামা/চালু" to "তৈরি; synthetic টেস্টে যাচাইকৃত, ফোনে পরীক্ষা বাকি",
            "স্ক্যান PDF-এর OCR (Tesseract: ইংরেজি/বাংলা/আরবি/উর্দু/হিন্দি)" to "তৈরি; ডেস্কটপে synthetic-এ যাচাইকৃত, ফোনে বা বাস্তব স্ক্যানে চালানো হয়নি",
            "বাংলা অনুবাদ (স্থানীয় Gemma 4)" to "তৈরি; ডেস্কটপে মডেল যাচাইকৃত, ফোনে চালানো হয়নি (ডিভাইস-টেস্ট বাকি)",
            "কোড-স্ক্রিনশট OCR, ব্যাখ্যা, ভুল খোঁজা, diff" to "তৈরি; diff/ইন্ডেন্টেশন-পুনর্গঠনের লজিক টেস্টকৃত, ফোনে বা বাস্তব স্ক্রিনশটে চালানো হয়নি",
            "বাংলা পড়ে শোনানো (ফোনের অফলাইন voice), পজ/রিজিউম/গতি/অবস্থান" to "তৈরি; কার্সর-লজিক টেস্টকৃত; ফোনে অফলাইন বাংলা voice আছে কি না ও মান — ডিভাইস-টেস্ট বাকি",
            "ভয়েস কমান্ড (চেপে বলুন, on-device চেনা)" to "তৈরি; কমান্ড-পার্সার টেস্টকৃত; ফোনে বাংলা ভাষা-প্যাক/মাইক — ডিভাইস-টেস্ট বাকি",
            "ব্যাকগ্রাউন্ড/স্ক্রিন-অফ প্লেব্যাক, headphone কন্ট্রোল" to "এখনো তৈরি হয়নি",
            "প্রশ্নোত্তর (কীওয়ার্ড অনুসন্ধান + AI, উৎস পৃষ্ঠা-লিঙ্ক)" to "তৈরি; অনুসন্ধান টেস্টকৃত; AI-উত্তরের মান ফোনে/বাস্তব বইয়ে যাচাই বাকি",
            "পড়ার মাঝে প্রশ্ন (নির্বাচিত শব্দ/লাইন), নোট, বুকমার্ক, শব্দভান্ডার, glossary" to "এখনো তৈরি হয়নি",
        ).forEach { (k, v) -> Text("• $k — $v", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable private fun SectionTitle(t: String) = Text(t, style = MaterialTheme.typography.titleMedium)

private fun fmtSize(b: Long) = when {
    b >= 1L shl 30 -> "%.2f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.1f MB".format(b / (1L shl 20).toDouble())
    else -> "${b / 1024} KB"
}
