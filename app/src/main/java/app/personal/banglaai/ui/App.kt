package app.personal.banglaai.ui

import android.net.Uri
import androidx.compose.material.icons.rounded.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.personal.banglaai.MainActivity
import app.personal.banglaai.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import app.personal.banglaai.security.PinStore
import app.personal.banglaai.backup.BackupService
import app.personal.banglaai.backup.PassphraseBackup
import app.personal.banglaai.doc.DocWorkflow
import app.personal.banglaai.storage.KnownModels
import app.personal.banglaai.storage.ModelStore
import app.personal.banglaai.storage.StorageStats
import app.personal.banglaai.storage.Vault
import app.personal.banglaai.storage.VaultDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun App(activity: MainActivity, unlocked: Boolean, onUnlocked: () -> Unit, onLock: () -> Unit) {
    val settings = remember { activity.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    var fontScale by remember { mutableFloatStateOf(settings.getFloat("font_scale", 1.0f).coerceIn(0.85f, 1.6f)) }
    PsTheme(fontScale) {
        Surface(Modifier.fillMaxSize(), color = Color.Transparent, contentColor = Ps.Ice) {
            val pins = remember { PinStore(activity) }
            var pinSet by remember { mutableStateOf(pins.isSet()) }
            var splashDone by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) { if (!splashDone) { delay(1400); splashDone = true } }
            when {
                !splashDone -> Image(painterResource(R.drawable.splash_art), contentDescription = "চৌধুরী সাহেবের পিএস", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                !pinSet -> SetupScreen(pins) { pinSet = true; onUnlocked() }
                !unlocked -> UnlockScreen(activity, pins, onUnlocked)
                else -> HomeScreen(activity, onLock, fontScale) { v -> fontScale = v.coerceIn(0.85f, 1.6f); settings.edit().putFloat("font_scale", fontScale).apply() }
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
        Image(painterResource(R.drawable.logo_ps), contentDescription = null, modifier = Modifier.size(104.dp).clip(androidx.compose.foundation.shape.CircleShape).align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(12.dp))
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

/** First thing on Home: says plainly what is missing (usually the AI model) and gives one-tap access to the assistant and the phone check. */
private enum class HomeTab { Menu, Files, Model, Voice, Terms, Settings, Status }

@Composable
private fun HomeScreen(activity: MainActivity, onLock: () -> Unit, fontScale: Float, onFontScale: (Float) -> Unit) {
    val scope = rememberCoroutineScope()
    val vault = remember { AppGraph.vault(activity) }; val models = remember { AppGraph.models(activity) }
    var docs by remember { mutableStateOf<List<VaultDoc>>(emptyList()) }
    var modelList by remember { mutableStateOf(models.list()) }
    val workflow = remember { AppGraph.workflow(activity) }
    var openDoc by remember { mutableStateOf<VaultDoc?>(null) }
    var codeOpen by remember { mutableStateOf(false) }
    var chatOpen by remember { mutableStateOf(false) }
    var callOpen by remember { mutableStateOf(false) }
    var liveOpen by remember { mutableStateOf(false) }
    var kbOpen by remember { mutableStateOf(false) }
    var chatStart by remember { mutableStateOf<String?>(null) }
    var diagOpen by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(HomeTab.Menu) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val refresh: suspend () -> Unit = { docs = withContext(Dispatchers.IO) { runCatching { vault.list() }.getOrDefault(emptyList()) }; modelList = withContext(Dispatchers.IO) { models.list() } }
    LaunchedEffect(Unit) { refresh() }

    var backupPass by remember { mutableStateOf("") }
    val backup = remember { BackupService(vault, workflow) }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            busy = "এনক্রিপ্টেড ব্যাকআপ তৈরি হচ্ছে…"; error = null
            runCatching { withContext(Dispatchers.IO) { activity.contentResolver.openOutputStream(uri)!!.use { backup.create(it, backupPass.toCharArray()) } } }
                .onSuccess { error = "✅ ব্যাকআপ লেখা হয়েছে। পাসফ্রেজ হারালে এটি কেউ খুলতে পারবে না — আপনিও না।" }.onFailure { error = it.message ?: "ব্যর্থ" }
            busy = null
        }
    }
    val restoreBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            busy = "ব্যাকআপ থেকে ফেরত আনা হচ্ছে…"; error = null
            runCatching { withContext(Dispatchers.IO) { activity.contentResolver.openInputStream(uri)!!.use { backup.restore(it, backupPass.toCharArray()) } } }
                .onSuccess { error = "✅ ফেরত এসেছে: ${it.restored}টি ফাইল (আগে থেকে থাকায় বাদ: ${it.skipped}টি)" }
                .onFailure { error = if (it is PassphraseBackup.BadBackup) it.message else "পাসফ্রেজ ভুল অথবা ফাইল নষ্ট" }
            busy = null; refresh()
        }
    }

    val pickDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            busy = "ফাইল এনক্রিপ্ট করে রাখা হচ্ছে…"; error = null
            runCatching { withContext(Dispatchers.IO) { vault.import(uri) } }.onFailure { error = it.message ?: "ব্যর্থ" }
            busy = null; refresh()
        }
    }
    val takeDocPhoto = rememberPhotoCapture { f ->
        scope.launch {
            busy = "ছবি এনক্রিপ্ট করে রাখা হচ্ছে…"; error = null
            runCatching { withContext(Dispatchers.IO) { try { vault.import(Uri.fromFile(f)) } finally { f.delete() } } }.onFailure { error = it.message ?: "ব্যর্থ" }
            busy = null; refresh()
        }
    }
    val pickModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        app.personal.banglaai.storage.ModelImporter.start(models, uri)
    }
    // Keep the display on while the big copy runs, and refresh the model list when it ends.
    val importing = app.personal.banglaai.storage.ModelImporter.running
    val jobBusy = JobRunner.label != null
    DisposableEffect(importing || jobBusy) {
        if (importing || jobBusy) activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    LaunchedEffect(importing) { if (!importing) refresh() }
    // A model file without a recorded checksum is verified automatically (reads the file once; no copy).
    LaunchedEffect(modelList, importing) { if (!importing) modelList.firstOrNull { it.sha256.isBlank() && it.name.endsWith(".litertlm") }?.let { app.personal.banglaai.storage.ModelImporter.startVerify(models, it.name) } }

    androidx.activity.compose.BackHandler(enabled = tab != HomeTab.Menu && openDoc == null && !callOpen && !kbOpen && !chatOpen && !diagOpen && !codeOpen) { tab = HomeTab.Menu }
    var pendingCmd by remember { mutableStateOf<app.personal.banglaai.speech.VoiceCommand?>(null) }
    openDoc?.let { d -> DocScreen(d, workflow, pendingCmd) { openDoc = null; pendingCmd = null }; return }
    if (kbOpen) { KnowledgeScreen(workflow, activity.cacheDir) { kbOpen = false }; return }
    if (liveOpen) { LiveTranslateScreen(workflow, activity.cacheDir) { liveOpen = false }; return }
    if (callOpen) { VoiceCallScreen(workflow, vault, activity.cacheDir) { callOpen = false }; return }
    if (chatOpen) { ChatScreen(workflow, activity.cacheDir, chatStart) { chatOpen = false; chatStart = null }; return }
    if (diagOpen) { DiagnosticsScreen(workflow, activity.cacheDir) { diagOpen = false }; return }
    if (codeOpen) { CodeScreen(workflow, activity.cacheDir, activity.noBackupFilesDir) { codeOpen = false }; return }
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (tab == HomeTab.Menu) Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
            // the logo art has its own dark backdrop: fade its edges out so it melts into the app background
            Image(painterResource(R.drawable.home_logo), contentDescription = "চৌধুরী সাহেবের পিএস",
                modifier = Modifier.fillMaxHeight().graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                    .drawWithContent { drawContent(); drawRect(androidx.compose.ui.graphics.Brush.radialGradient(0f to Color.Black, 0.62f to Color.Black, 1f to Color.Transparent, center = center, radius = size.maxDimension * 0.55f), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn) },
                contentScale = ContentScale.Fit)
            OutlinedButton(onClick = onLock, modifier = Modifier.align(Alignment.TopEnd)) { Text("🔒 বন্ধ করুন") }
        } else Image(painterResource(R.drawable.home_banner), contentDescription = "চৌধুরী সাহেবের পিএস", modifier = Modifier.fillMaxWidth().height(56.dp).clip(MaterialTheme.shapes.large), contentScale = ContentScale.Crop)
        if (tab != HomeTab.Menu) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("চৌধুরী সাহেবের পিএস", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { tab = HomeTab.Menu }) { Text("← হোম") }
        }
        app.personal.banglaai.storage.ModelImporter.copiedMb?.let { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("মডেল কপি হচ্ছে… $it MB / প্রায় ৩৪৯০ MB — স্ক্রিন জাগিয়ে রাখা হয়েছে, অ্যাপ বন্ধ করবেন না") }
        app.personal.banglaai.storage.ModelImporter.lastOk?.let { Text("✅ $it", color = MaterialTheme.colorScheme.tertiary) }
        app.personal.banglaai.storage.ModelImporter.lastError?.let { Text("❌ মডেল বসানো ব্যর্থ: $it", color = MaterialTheme.colorScheme.error) }
        busy?.let { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(it) }
        error?.let { Text("❌ $it", color = MaterialTheme.colorScheme.error) }

        when (tab) {
            HomeTab.Menu -> {
        val hasModel = workflow.usableModel() != null
        Text(if (hasModel) "✅ AI মডেল বসানো আছে" else "❌ AI মডেল বসানো নেই — নিচে '🤖 AI মডেল' চেপে বসান", color = if (hasModel) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        val ic = androidx.compose.material.icons.Icons.Rounded; val st = TileStyle
        TileGrid(listOf(
            Tile("ভয়েস কল", ic.Call, st.call.first, st.call.second) { callOpen = true },
            Tile("লাইভ অনুবাদ", ic.Translate, st.live.first, st.live.second) { liveOpen = true },
            Tile("সহকারী", ic.Forum, st.chat.first, st.chat.second) { chatStart = null; chatOpen = true },
            Tile("ফাইল যোগ (PDF)", ic.UploadFile, st.add.first, st.add.second, enabled = busy == null) { pickDoc.launch(arrayOf("*/*")) },
            Tile("আমার ফাইল", ic.FolderOpen, st.files.first, st.files.second) { tab = HomeTab.Files },
            Tile("ছবি তুলুন", ic.PhotoCamera, st.camera.first, st.camera.second, enabled = busy == null) { takeDocPhoto() },
            Tile("ভয়েস নির্দেশ", ic.Mic, st.voice.first, st.voice.second) { tab = HomeTab.Voice },
            Tile("কোড বিশ্লেষণ", ic.Code, st.code.first, st.code.second) { codeOpen = true },
            Tile("AI মডেল", ic.SmartToy, st.model.first, st.model.second) { tab = HomeTab.Model },
            Tile("জ্ঞানভাণ্ডার", ic.AutoStories, st.kb.first, st.kb.second) { kbOpen = true },
            Tile("পরিভাষা ও শব্দ", ic.MenuBook, st.terms.first, st.terms.second) { tab = HomeTab.Terms },
            Tile("সেটিংস", ic.Settings, st.settings.first, st.settings.second) { tab = HomeTab.Settings },
            Tile("ফোন পরীক্ষা", ic.MonitorHeart, st.diag.first, st.diag.second) { diagOpen = true },
            Tile("ফিচারের অবস্থা", ic.Info, st.status.first, st.status.second) { tab = HomeTab.Status },
        ))
        Text("যোগ করার পর নিজে থেকে কোনো অনুবাদ বা AI কাজ শুরু হয় না।", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HomeTab.Files -> {
        SectionTitle("আমার ফাইল (এনক্রিপ্টেড)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { pickDoc.launch(arrayOf("*/*")) }, enabled = busy == null) { Text("ফাইল যোগ করুন") }
            OutlinedButton(onClick = { takeDocPhoto() }, enabled = busy == null) { Text("📷 ছবি তুলুন") }
        }
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

            }
            HomeTab.Model -> {
        SectionTitle("AI মডেল (ম্যানুয়ালি বসান)")
        Button(onClick = { pickModel.launch(arrayOf("*/*")) }, enabled = busy == null && !importing) { Text("মডেল ফাইল বসান") }
        Text("খালি জায়গা: ${fmtSize(models.freeBytes())}", style = MaterialTheme.typography.bodySmall)
        if (modelList.isEmpty()) Text("কোনো মডেল বসানো নেই — অনুবাদ/প্রশ্নোত্তর এখনো কাজ করবে না।")
        modelList.forEach { m ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name); Text(fmtSize(m.size), style = MaterialTheme.typography.bodySmall)
                        Text("SHA-256: ${m.sha256}", style = MaterialTheme.typography.bodySmall.merge(Ps.Mono))
                        Text(when (val v = KnownModels.verdict(m.name, m.size, m.sha256)) {
                            is KnownModels.Verdict.Official -> "✅ অফিসিয়াল ফাইলের সঙ্গে মিলেছে: ${v.model.label}"
                            is KnownModels.Verdict.Mismatch -> "❌ নামের সঙ্গে মিললেও ফাইলের কনটেন্ট মেলেনি; ব্যবহার করবেন না। " + KnownModels.explainMismatch(v.expected, m.size, m.sha256)
                            KnownModels.Verdict.Pending -> "⏳ checksum এখনো হিসাব হয়নি — যাচাই শেষ না হওয়া পর্যন্ত ব্যবহার করা হবে না (নিচের বাটন)"
                            KnownModels.Verdict.Unknown -> "⚠️ আমাদের যাচাই-করা তালিকায় নেই; অফিসিয়াল checksum নিজে মিলিয়ে নিন"
                        }, style = MaterialTheme.typography.bodySmall)
                    }
                    if (workflow.isUsable(m)) {
                        val active = workflow.usableModel()?.name == m.name
                        if (active) Text("✔ ব্যবহার হচ্ছে", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
                        else TextButton(onClick = { workflow.chosenModel = m.name; scope.launch { refresh() } }) { Text("এটি ব্যবহার করুন") }
                    }
                    if (m.sha256.isBlank()) TextButton(enabled = !importing, onClick = { app.personal.banglaai.storage.ModelImporter.startVerify(models, m.name) }) { Text("যাচাই করুন") }
                    TextButton(onClick = { scope.launch { withContext(Dispatchers.IO) { models.delete(m.name) }; refresh() } }) { Text("মুছুন") }
                }
            }
        }
        if (modelList.size > 1) Text("দুটো মডেল রাখা যায়, কিন্তু একসঙ্গে একটাই চলে (RAM বাঁচাতে)। বদলাতে চাইলে ‘এটি ব্যবহার করুন’ চাপুন; প্রথম প্রশ্নে নতুন মডেল লোড হতে সময় লাগবে।", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        Text("SHA-256 মিলিয়ে নিন মডেলের অফিসিয়াল পাতায় দেওয়া checksum-এর সঙ্গে।", style = MaterialTheme.typography.bodySmall)

            }
            HomeTab.Voice -> {
        HomeVoice(docs, { d, then -> pendingCmd = then; openDoc = d }) { q -> chatStart = q; chatOpen = true }

            }
            HomeTab.Terms -> {
        GlossarySection(workflow)

        VocabPanel(workflow, activity.cacheDir)

            }
            HomeTab.Settings -> {
        SectionTitle("সেটিংস")
        var shots by remember { mutableStateOf(activity.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getBoolean("allow_screenshots", true)) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = shots, onCheckedChange = { shots = it; activity.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).edit().putBoolean("allow_screenshots", it).apply(); activity.applyScreenshotPolicy() })
            Column(Modifier.weight(1f)) {
                Text("স্ক্রিনশট / স্ক্রিন-রেকর্ডিং অনুমতি")
                Text(if (shots) "চালু: স্ক্রিনশট নেওয়া যাবে। সাবধান: রিসেন্ট-অ্যাপ প্রিভিউতেও ভেতরের লেখা দেখা যেতে পারে।" else "বন্ধ: স্ক্রিনশট/রেকর্ডিং ও রিসেন্ট-অ্যাপ প্রিভিউ অন্ধকার থাকবে (বেশি গোপনীয়)।", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("লেখার আকার", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { onFontScale(fontScale - 0.1f) }, enabled = fontScale > 0.86f) { Text("A−") }
            Text("${(fontScale * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { onFontScale(fontScale + 0.1f) }, enabled = fontScale < 1.59f) { Text("A+") }
        }
        SectionTitle("স্টোরেজ ব্যবহার")
        var stats by remember { mutableStateOf(StorageStats.read(activity)) }
        LaunchedEffect(docs, modelList, busy) { stats = withContext(Dispatchers.IO) { StorageStats.read(activity) } }
        Text("মূল ফাইল ${fmtSize(stats.originals)} · বের করা লেখা/অনুবাদ ${fmtSize(stats.derived)} · মডেল ${fmtSize(stats.models)} · OCR ডেটা ${fmtSize(stats.ocrData)} · ক্যাশ ${fmtSize(stats.cache)}\nখালি জায়গা এখন: ${fmtSize(stats.free)} (এটি নির্দিষ্ট ধরে নেওয়া হয় না; প্রতিটি কাজের আগে যাচাই হয়)", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { StorageStats.clearCache(activity); stats = StorageStats.read(activity) }) { Text("ক্যাশ মুছুন (ফাইল/অনুবাদ মোছে না)") }

        SectionTitle("এনক্রিপ্টেড ব্যাকআপ ও রিকভারি")
        Text("পাসফ্রেজ থেকে চাবি তৈরি হয়; কোনো গোপন master key নেই। ফোন রিসেট/অ্যাপ মুছলে অ্যাপের ভেতরের ডেটা চলে যায় — ব্যাকআপ ও পাসফ্রেজ আপনার কাছে থাকলে ফেরানো যায়। পাসফ্রেজ হারালে ব্যাকআপ চিরতরে অপাঠ্য।", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = backupPass, onValueChange = { backupPass = it }, label = { Text("ব্যাকআপ পাসফ্রেজ (কমপক্ষে ${PassphraseBackup.MIN_PASSPHRASE} অক্ষর)") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = busy == null && backupPass.length >= PassphraseBackup.MIN_PASSPHRASE, onClick = { saveBackup.launch("banglaai-backup.bai") }) { Text("ব্যাকআপ তৈরি") }
            OutlinedButton(enabled = busy == null && backupPass.isNotEmpty(), onClick = { restoreBackup.launch(arrayOf("*/*")) }) { Text("ব্যাকআপ থেকে ফেরান") }
        }

            }
            HomeTab.Status -> {
        SectionTitle("ফিচারের অবস্থা (সৎ তালিকা)")
        listOf(
            "PIN + বায়োমেট্রিক লক, এনক্রিপ্টেড ফাইল সংরক্ষণ" to "তৈরি; ডিভাইসে পরীক্ষা বাকি",
            "PDF/TXT লেখা বের করা, পৃষ্ঠা-ম্যাপ, থামা/চালু" to "তৈরি; synthetic টেস্টে যাচাইকৃত, ফোনে পরীক্ষা বাকি",
            "স্ক্যান PDF-এর OCR (Tesseract: ইংরেজি/বাংলা/আরবি/উর্দু/হিন্দি)" to "তৈরি; ডেস্কটপে synthetic-এ যাচাইকৃত, ফোনে বা বাস্তব স্ক্যানে চালানো হয়নি",
            "বাংলা অনুবাদ (স্থানীয় Gemma 4)" to "তৈরি; ডেস্কটপে মডেল যাচাইকৃত, ফোনে চালানো হয়নি (ডিভাইস-টেস্ট বাকি)",
            "কোড-স্ক্রিনশট OCR, ব্যাখ্যা, ভুল খোঁজা, diff" to "তৈরি; diff/ইন্ডেন্টেশন-পুনর্গঠনের লজিক টেস্টকৃত, ফোনে বা বাস্তব স্ক্রিনশটে চালানো হয়নি",
            "বাংলা পড়ে শোনানো (ফোনের অফলাইন voice), পজ/রিজিউম/গতি/অবস্থান" to "তৈরি; কার্সর-লজিক টেস্টকৃত; ফোনে অফলাইন বাংলা voice আছে কি না ও মান — ডিভাইস-টেস্ট বাকি",
            "ভয়েস কমান্ড (চেপে বলুন, on-device চেনা)" to "তৈরি; কমান্ড-পার্সার টেস্টকৃত; ফোনে বাংলা ভাষা-প্যাক/মাইক — ডিভাইস-টেস্ট বাকি",
            "পাসফ্রেজ-এনক্রিপ্টেড ব্যাকআপ/রিকভারি" to "তৈরি; এনক্রিপশন কন্টেইনার টেস্টকৃত (ভুল পাসফ্রেজ/বদল ধরা পড়ে); পূর্ণ create→restore ফোনে চালানো হয়নি",
            "ব্যাকগ্রাউন্ড/স্ক্রিন-অফ প্লেব্যাক, headphone কন্ট্রোল" to "এখনো তৈরি হয়নি",
            "প্রশ্নোত্তর (কীওয়ার্ড অনুসন্ধান + AI, উৎস পৃষ্ঠা-লিঙ্ক)" to "তৈরি; অনুসন্ধান টেস্টকৃত; AI-উত্তরের মান ফোনে/বাস্তব বইয়ে যাচাই বাকি",
            "পরিভাষা (glossary), বুকমার্ক, ব্যক্তিগত নোট" to "তৈরি; লজিক টেস্টকৃত, ফোনে চালানো হয়নি",
            "শব্দ/বাক্য ধরে প্রশ্ন (লিখে/পেস্ট করে; পড়ার প্রসঙ্গে), শব্দভান্ডার, অনুশীলনী প্রশ্ন" to "তৈরি; লজিক টেস্টকৃত; AI-উত্তরের মান ফোনে যাচাই বাকি",
            "ছবি-ফাইল (JPG/PNG) আলাদা ডকুমেন্ট হিসেবে import + OCR" to "তৈরি; ফোনে চালানো হয়নি",
        ).forEach { (k, v) -> Text("• $k — $v", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

/** Section heading in "terminal prompt" style: cyan chevron + bold title + thin cyan rule. */
@Composable fun SectionTitle(t: String) = Column {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("❯ ", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
        Text(t, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), modifier = Modifier.padding(top = 4.dp))
}

private fun fmtSize(b: Long) = when {
    b >= 1L shl 30 -> "%.2f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.1f MB".format(b / (1L shl 20).toDouble())
    else -> "${b / 1024} KB"
}
