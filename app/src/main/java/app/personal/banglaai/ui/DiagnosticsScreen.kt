package app.personal.banglaai.ui

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.personal.banglaai.ai.LocalLlm
import app.personal.banglaai.ai.Prompts
import app.personal.banglaai.doc.DocWorkflow
import app.personal.banglaai.speech.ReadAloudController
import app.personal.banglaai.speech.TtsReader
import app.personal.banglaai.speech.VoiceInput
import app.personal.banglaai.storage.KnownModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class Fact(val ok: Boolean?, val title: String, val detail: String)

/**
 * "What actually works on THIS phone" — real checks, no assumptions. The model test really loads the imported model and generates a sentence,
 * measuring load/generation time and process memory; any error text is shown verbatim so it can be copied back to the developer.
 */
@Composable
fun DiagnosticsScreen(wf: DocWorkflow, cacheDir: File, onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var modelTest by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    // This screen shows no private data, so screenshots are allowed here (so it can be sent for diagnosis); the secure flag returns on leaving.
    DisposableEffect(Unit) {
        val w = (ctx as? android.app.Activity)?.window
        // the screen shows no private data, so screenshots are allowed here — but never against the owner's explicit 'no screenshots' setting
        if (ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("allow_screenshots", false)) w?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { (ctx as? app.personal.banglaai.MainActivity)?.applyScreenshotPolicy() }
    }
    ReadAloudController.get(ctx)   // starts the TTS engine so its state below is real
    val ttsState = ReadAloudController.state
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1500); tick++ } }

    val facts = remember(tick, ttsState) {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val model = wf.usableModel()
        val modelFiles = app.personal.banglaai.storage.ModelStore(ctx).list()
        val verdict = modelFiles.firstOrNull { it.name.endsWith(".litertlm") }?.let { KnownModels.verdict(it.name, it.size, it.sha256) }
        listOf(
            Fact(null, "ফোন", "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.SUPPORTED_ABIS.firstOrNull()}"),
            Fact(null, "RAM (এই মুহূর্তে)", "মোট ${mi.totalMem / (1 shl 20)} MB · খালি ${mi.availMem / (1 shl 20)} MB" + if (mi.lowMemory) " · ⚠️ কম" else ""),
            Fact(null, "স্টোরেজ (খালি)", "${ctx.noBackupFilesDir.usableSpace / (1 shl 20)} MB"),
            Fact(model != null, "AI মডেল", when { model == null -> (app.personal.banglaai.storage.ModelImporter.lastError?.let { "শেষ চেষ্টায় ব্যর্থ: $it। " } ?: if (app.personal.banglaai.storage.ModelImporter.running) "কপি চলছে, শেষ হলে আবার দেখুন। " else "") + "বসানো নেই — হোমে 'মডেল ফাইল বসান' (E4B ≈ 3.66 GB, E2B ≈ 2.59 GB — দশমিক GB)। মডেল ছাড়া অনুবাদ/উত্তর/ব্যাখ্যা চলবে না।"
                verdict is KnownModels.Verdict.Official -> "${verdict.model.label} — অফিসিয়াল ফাইলের সঙ্গে মিলেছে"
                verdict is KnownModels.Verdict.Pending -> "ফাইল আছে, checksum যাচাই বাকি/চলছে (হোমের AI মডেল পর্দা দেখুন)"
                verdict is KnownModels.Verdict.Mismatch -> "ফাইল আছে কিন্তু মেলেনি: " + modelFiles.firstOrNull { it.name.endsWith(".litertlm") }!!.let { KnownModels.explainMismatch(verdict.expected, it.size, it.sha256) }
                else -> "ফাইল আছে (আমাদের তালিকার বাইরের)" }),
            Fact(when (ttsState) { TtsReader.State.Ready, TtsReader.State.Playing, TtsReader.State.Paused -> true; TtsReader.State.Initialising -> null; else -> false },
                "পড়ে শোনানো (অফলাইন বাংলা voice)", when (ttsState) {
                    TtsReader.State.Initialising -> "পরীক্ষা চলছে…"
                    TtsReader.State.NoEngine -> "কোনো text-to-speech ইঞ্জিন চালু নেই"
                    TtsReader.State.NoOfflineBengali -> if (ReadAloudController.get(ctx).onlyNetworkBengaliExists) "বাংলা voice আছে কিন্তু ইন্টারনেট লাগে — ব্যবহার করা হয় না। অফলাইন বাংলা voice নামান।" else "বাংলা voice নেই। সেটিংস → ভাষা → Text-to-speech → ইঞ্জিনের সেটিংস → ভয়েস ডেটা → বাংলা"
                    else -> "আছে: ${ReadAloudController.get(ctx).voiceName}" }),
            Fact(VoiceInput(ctx).available(), "ভয়েস চেনা (ফোনের ভেতরে, on-device)", if (VoiceInput(ctx).available()) "আছে (বাংলা ভাষা-প্যাক আছে কি না প্রথম ব্যবহারে বোঝা যাবে)" else "এই ফোনে on-device recognizer পাওয়া যায়নি — 🎤 কাজ করবে না; লিখে নির্দেশ দিন"),
            Fact(ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED, "মাইক্রোফোন অনুমতি", "🎤 প্রথম চাপলে অনুমতি চাইবে"),
        )
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = onBack) { Text("← ফিরে যান") }
        Text("ফোনের অবস্থা পরীক্ষা", style = MaterialTheme.typography.headlineSmall)
        Text("এটি আসল পরীক্ষা, অনুমান নয়। কিছু না চললে এই পর্দার স্ক্রিনশট পাঠান।", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        facts.forEach { f ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text((when (f.ok) { true -> "✅ "; false -> "❌ "; null -> "ℹ️ " }) + f.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(f.detail, style = MaterialTheme.typography.bodyMedium)
            } }
        }
        SectionTitle("অ্যাপ কেন বন্ধ হয়েছিল (শুধু এই ফোনে জমা, কোথাও যায় না)")
        var crash by remember { mutableStateOf(CrashLog.last(ctx)) }
        val exits = remember { CrashLog.exitReasons(ctx) }
        if (exits.isEmpty() && crash == null) Text("কোনো তথ্য নেই।", style = MaterialTheme.typography.bodySmall)
        exits.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        crash?.let { Text(it.take(900), style = MaterialTheme.typography.bodySmall.merge(app.personal.banglaai.ui.Ps.Mono)); TextButton(onClick = { CrashLog.clear(ctx); crash = null }) { Text("মুছে ফেলুন") } }
        SectionTitle("ভয়েস-চেনার ভাষা-প্যাক (কথা শুনে লেখা)")
        var packText by remember { mutableStateOf<String?>(null) }
        val vi = remember { VoiceInput(ctx) }
        Text("মাইকে বললে অ্যাপ ফোনের ভেতরের অফলাইন ভাষা-প্যাক ব্যবহার করে। বাংলা প্যাক ইনস্টল না থাকলে ‘বাংলা অফলাইন নামান’ বার্তা আসে — সেটা কীবোর্ড বা কণ্ঠস্বর (TTS) থেকে আলাদা জিনিস।", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { packText = "দেখা হচ্ছে…"; vi.packStatus("bn-BD") { inst, sup, err -> packText = err ?: ("ইনস্টল করা: " + (inst.joinToString().ifBlank { "কিছু নেই" }) + "\nসমর্থিত (বাংলা): " + sup.filter { it.startsWith("bn") }.joinToString().ifBlank { "জানা যায়নি" }) } }) { Text("প্যাকের অবস্থা দেখুন") }
            Button(onClick = { packText = if (vi.downloadPack("bn-BD")) "ফোনের নিজস্ব স্পিচ-সার্ভিসকে বাংলা প্যাক নামাতে বলা হয়েছে (এই অ্যাপের ইন্টারনেট অনুমতি নেই, নামায় ফোনের সিস্টেম)। কিছুক্ষণ পরে ‘অবস্থা দেখুন’ চাপুন।" else "এই ফোনে (Android 13+ লাগে) এটা করা যায়নি। ফোনের Settings → Google → সব সেবা → ভয়েস → অফলাইন স্পিচ রিকগনিশন → বাংলা দেখুন।" }) { Text("বাংলা প্যাক নামান") }
        }
        packText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
        SectionTitle("AI চালানোর উপায়: CPU নাকি GPU")
        var gpu by remember { mutableStateOf(LocalLlm.preferGpu) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !gpu, onClick = { gpu = false; LocalLlm.preferGpu = false; ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("gpu", false).apply() }, label = { Text("CPU (নিরাপদ, ডিফল্ট)") })
            FilterChip(selected = gpu, onClick = { gpu = true; LocalLlm.preferGpu = true; ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("gpu", true).apply() }, label = { Text("GPU (কম RAM, পরীক্ষামূলক)") })
        }
        Text("GPU বেছে 'মডেল পরীক্ষা' চালান: ঠিকমতো চললে ও দ্রুত হলে GPU-ই রাখুন। শুরু না হলে অ্যাপ নিজে CPU-তে ফিরে যাবে ও কারণ লিখবে (তবে GPU-ড্রাইভার ক্র্যাশ করলে অ্যাপ বন্ধ হতে পারে — তখন আবার খুলে CPU বেছে নিন)।", style = MaterialTheme.typography.bodySmall)
        SectionTitle("মডেল পরীক্ষা (লোড + একটি বাক্য)")
        Text("মডেল সত্যিই লোড হয়ে বাংলা বলতে পারে কি না, কত সময় লাগে ও কত মেমরি নেয় — এখানে মাপা হবে। প্রথমবার এক মিনিটের মতো লাগতে পারে; অ্যাপ বন্ধ করবেন না।", style = MaterialTheme.typography.bodySmall)
        Button(enabled = !testing && wf.usableModel() != null, onClick = {
            testing = true; modelTest = null
            scope.launch {
                val model = wf.usableModel()!!
                val out = withContext(Dispatchers.IO) {
                    val sb = StringBuilder()
                    try {
                        val t0 = System.nanoTime(); val llm = LocalLlm(model, File(cacheDir, "llm"))
                        llm.load(); val tLoad = (System.nanoTime() - t0) / 1e9
                        var firstAt = -1.0; val t1 = System.nanoTime(); var chars = 0; val text = StringBuilder()
                        llm.askStream(Prompts.ASSISTANT_SYSTEM, "এক বাক্যে বাংলায় নিজের পরিচয় দাও।") { p -> if (firstAt < 0) firstAt = (System.nanoTime() - t1) / 1e9; chars += p.length; text.append(p) }
                        val tGen = (System.nanoTime() - t1) / 1e9
                        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                        val pss = am.getProcessMemoryInfo(intArrayOf(android.os.Process.myPid())).firstOrNull()?.totalPss?.div(1024) ?: -1
                        llm.close()
                        sb.append("✅ সফল (চালানো হয়েছে: ${LocalLlm.usedBackend}${LocalLlm.lastGpuFailure?.let { f -> " — GPU ব্যর্থ হয়ে CPU-তে ফিরেছে: $f" } ?: ""})\nমডেল লোড: %.1f সে.\nপ্রথম লেখা আসতে: %.1f সে.\nমোট উত্তর: %.1f সে. (%d অক্ষর)\nঅ্যাপের মেমরি (উত্তরের পর, PSS): %d MB\nনেটিভ হিপ: %d MB\n\nউত্তর: %s".format(
                            tLoad, firstAt, tGen, chars, pss, Debug.getNativeHeapAllocatedSize() / (1 shl 20), text))
                    } catch (e: Throwable) {
                        sb.append("❌ ব্যর্থ\n${e.javaClass.name}\n${e.message}\n\n${e.stackTrace.take(8).joinToString("\n")}")
                    }
                    sb.toString()
                }
                modelTest = out; testing = false
            }
        }) { Text(if (testing) "পরীক্ষা চলছে…" else "মডেল পরীক্ষা চালান") }
        if (wf.usableModel() == null) Text("আগে মডেল বসান।", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        modelTest?.let { SelectionContainer { Text(it, style = MaterialTheme.typography.bodySmall.merge(Ps.Mono)) } }
    }
}
