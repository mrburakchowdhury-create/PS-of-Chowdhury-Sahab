package app.personal.banglaai.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.personal.banglaai.ai.CodeDiff
import app.personal.banglaai.ai.DiffType
import app.personal.banglaai.ai.LocalLlm
import app.personal.banglaai.ai.Prompts
import app.personal.banglaai.doc.DocWorkflow
import app.personal.banglaai.importer.readUpTo
import app.personal.banglaai.ocr.CodeReconstructor
import app.personal.banglaai.ocr.Doubt
import app.personal.banglaai.ocr.TesseractOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Code understanding. Code is NEVER executed. OCR output is shown for correction before analysis; doubtful characters are flagged,
 * not silently fixed. The diff is computed by the app from the user's code and the model's proposed replacement.
 */
@Composable
fun CodeScreen(wf: DocWorkflow, cacheDir: File, noBackupDir: File, onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("এই কোড বাংলায় বোঝাও।") }
    var doubts by remember { mutableStateOf<List<Doubt>>(emptyList()) }
    var answer by remember { mutableStateOf<String?>(null) }
    var proposed by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val llm = remember { arrayOfNulls<LocalLlm>(1) }
    DisposableEffect(Unit) { onDispose { llm[0]?.close() } }   // free the model's RAM when leaving this screen

    /** Runs OCR on a bitmap produced by [load] (decoded on a background thread; large pictures are downsampled to bound memory). */
    fun ocr(load: () -> android.graphics.Bitmap) {
        scope.launch {
            busy = "ছবি থেকে লেখা পড়া হচ্ছে (OCR)…"; error = null
            runCatching {
                withContext(Dispatchers.IO) {
                    val bmp = load()
                    TesseractOcr(ctx).use { t ->
                        val r = t.recognize(bmp, listOf(TesseractOcr.Lang.ENG), psm = 6)
                        bmp.recycle(); CodeReconstructor.rebuild(r.words)
                    }
                }
            }.onSuccess { code = it.text; doubts = it.doubts; answer = null; proposed = null }.onFailure { error = it.message ?: it.javaClass.simpleName }
            busy = null
        }
    }
    fun decode(open: () -> java.io.InputStream?): android.graphics.Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1; while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 4000) sample *= 2
        return open()!!.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: error("ছবি পড়া যায়নি")
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        ocr { decode { ctx.contentResolver.openInputStream(uri) } }
    }
    val takePhoto = rememberPhotoCapture { f -> ocr { try { decode { f.inputStream() } } finally { f.delete() } } }
    val pickCodeFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.use { it.readUpTo(1L shl 20).toString(Charsets.UTF_8) } } }
                .onSuccess { code = it; doubts = emptyList(); answer = null; proposed = null; error = if (it.length >= (1 shl 20)) "ফাইল বড় — প্রথম ১ MB নেওয়া হয়েছে" else null }
                .onFailure { error = it.message ?: it.javaClass.simpleName }
        }
    }

    fun ask(q: String) {
        if (busy != null || code.isBlank()) return
        val model = wf.usableModel() ?: run { error = "কোনো AI মডেল বসানো নেই — আগে মডেল import করুন"; return }
        busy = "AI ভাবছে… (প্রথমবার মডেল লোড হতে সময় লাগে)"; error = null; answer = null; proposed = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val m = llm[0] ?: LocalLlm(model, File(cacheDir, "llm")).also { llm[0] = it }
                val fitted = app.personal.banglaai.ai.TokenBudget.head(code, 2000)
                if (fitted.length < code.length) withContext(Dispatchers.Main) { error = "কোড বড়, তাই প্রথম ${fitted.lines().size} লাইন নিয়ে ব্যাখ্যা করা হচ্ছে (মডেলের সীমা)।" }
                m.ask(Prompts.CODE_SYSTEM, Prompts.explainCode(fitted, q))
            } }.onSuccess { answer = it; proposed = CodeDiff.lastFencedBlock(it) }.onFailure { error = it.message ?: it.javaClass.simpleName }
            busy = null
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = onBack) { Text("← ফিরে যান") }
        Text("কোড বিশ্লেষণ", style = MaterialTheme.typography.headlineSmall)
        Text("কোড কখনো চালানো হয় না; শুধু পড়ে ব্যাখ্যা করা হয়। AI ভুল করতে পারে — বিশেষ করে জটিল কোডে। নিশ্চিত নয় এমন অংশ ‘অনুমান’ লিখতে মডেলকে বলা হয়, কিন্তু সে সবসময় মানবে — এমন নিশ্চয়তা নেই। বড় কোডের শুরুর অংশই নেওয়া হয়।", style = MaterialTheme.typography.bodySmall)
        busy?.let { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(it) }
        error?.let { Text("❌ $it", color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = busy == null, onClick = { pickImage.launch(arrayOf("image/*")) }) { Text("🖼 ছবি (OCR)") }
            OutlinedButton(enabled = busy == null, onClick = { takePhoto() }) { Text("📷 ছবি তুলুন") }
            OutlinedButton(enabled = busy == null, onClick = { pickCodeFile.launch(arrayOf("text/*", "application/json", "application/xml", "application/x-sh", "*/*")) }) { Text("📄 কোড-ফাইল") }
        }
        OutlinedTextField(value = code, onValueChange = { code = it; doubts = emptyList() }, label = { Text("কোড (লিখুন, পেস্ট করুন, বা OCR-এর পর সংশোধন করুন)") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp), textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        if (code.isNotEmpty()) Text("লাইন সংখ্যা: ${code.lines().size} (এই নম্বর আপনার টেক্সট বক্সের; ছবির লাইন নম্বর থেকে আলাদা হতে পারে)", style = MaterialTheme.typography.bodySmall)
        if (doubts.isNotEmpty()) {
            Text("⚠️ OCR-এর সন্দেহজনক অংশ — কোডে মিলিয়ে নিন (নিজে থেকে ঠিক করা হয়নি):", color = MaterialTheme.colorScheme.error)
            doubts.take(30).forEach { Text("• লাইন ${it.line}: `${it.word}` — ${it.reason}", style = MaterialTheme.typography.bodySmall) }
        }
        OutlinedTextField(value = question, onValueChange = { question = it }, label = { Text("প্রশ্ন") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = busy == null && code.isNotBlank(), onClick = { ask(question) }) { Text("ব্যাখ্যা করো") }
            Button(enabled = busy == null && code.isNotBlank(), onClick = {
                ask("এই কোডে কোথায় ভুল বা ঝুঁকি আছে? প্রতিটির জন্য লাইন, কারণ, সম্ভাব্য প্রভাব, কতটা নিশ্চিত, এবং কী লিখতে হবে বলো। সংশোধনের পর সম্পূর্ণ সংশোধিত কোড একটি ``` ব্লকে দাও। syntax, logic, security ও অনুপস্থিত তথ্য আলাদা করো।")
            }) { Text("ভুল খোঁজো") }
        }
        answer?.let { a ->
            HorizontalDivider(); Text("AI-র উত্তর (সাধারণ ব্যাখ্যা/অনুমান — যাচাইকৃত নয়)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            SelectionContainer { Text(a) }
        }
        proposed?.let { p ->
            val d = CodeDiff.diff(code, p)
            HorizontalDivider(); Text("প্রস্তাবিত পরিবর্তন (diff আপনার কোডের সঙ্গে অ্যাপ নিজে হিসাব করেছে; কিছু নিজে থেকে বদলানো হয়নি)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            if (!CodeDiff.changed(d)) Text("প্রস্তাবিত কোড মূল কোডের হুবহু একই।") else d.forEach { l ->
                val (bg, mark) = when (l.type) { DiffType.ADDED -> Color(0x3328A745) to "+" ; DiffType.REMOVED -> Color(0x33DC3545) to "−"; else -> Color.Transparent to " " }
                Box(Modifier.fillMaxWidth().background(bg)) {
                    Text("${l.oldNo?.toString().orEmpty().padStart(3)} ${l.newNo?.toString().orEmpty().padStart(3)} $mark ${l.text}",
                        fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, softWrap = false)
                }
            }
            Text("এই পরিবর্তন চালানো/পরীক্ষা করা হয়নি। প্রয়োগের আগে নিজে যাচাই করুন।", style = MaterialTheme.typography.bodySmall)
        }
    }
}
