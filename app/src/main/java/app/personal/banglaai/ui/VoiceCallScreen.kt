package app.personal.banglaai.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.personal.banglaai.R
import app.personal.banglaai.ai.LocalLlm
import app.personal.banglaai.ai.Prompts
import app.personal.banglaai.call.AvatarStore
import app.personal.banglaai.call.SentenceBuffer
import app.personal.banglaai.call.VoiceCallMachine
import app.personal.banglaai.call.VoiceCallMachine.Action
import app.personal.banglaai.call.VoiceCallMachine.Phase
import app.personal.banglaai.doc.DocWorkflow
import app.personal.banglaai.speech.CallSpeaker
import app.personal.banglaai.speech.ReadAloudController
import app.personal.banglaai.speech.VoiceInput
import app.personal.banglaai.storage.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Hands-free voice conversation with the LOCAL model. Starts only when the owner taps "কল শুরু"; the mic is open only while the app is
 * listening (never while it speaks, so it cannot hear itself); the call ends by itself after repeated silence, on an end phrase ("কল শেষ", "বিদায়"),
 * on the end button, or when the app leaves the screen/background. Nothing is recorded or saved; the short context lives in memory for this call only.
 * Status: logic unit-tested; speech recognition, offline Bengali voice and model speed are device-test pending.
 */
@Composable
fun VoiceCallScreen(wf: DocWorkflow, vault: Vault, cacheDir: File, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { AvatarStore(ctx, vault) }
    var photo by remember { mutableStateOf(store.load()) }
    val machine = remember { VoiceCallMachine() }
    var phase by remember { mutableStateOf(Phase.Idle) }
    var level by remember { mutableFloatStateOf(0f) }
    var wordTick by remember { mutableIntStateOf(0) }
    var heard by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var typed by remember { mutableStateOf("") }
    var speakerReady by remember { mutableStateOf<Boolean?>(null) }

    val voice = remember { VoiceInput(ctx) }
    val llm = remember { arrayOfNulls<LocalLlm>(1) }
    val history = remember { ArrayList<Pair<String, String>>() }
    var turn by remember { mutableIntStateOf(0) }          // invalidates work of an interrupted turn
    var generating by remember { mutableStateOf(false) }
    var genDone by remember { mutableStateOf(true) }
    val speakerRef = remember { arrayOfNulls<CallSpeaker>(1) }
    val actRef = remember { arrayOfNulls<(Action) -> Unit>(1) }

    fun finishTurn() { if (machine.phase == Phase.Speaking || machine.phase == Phase.Thinking) actRef[0]?.invoke(machine.onSpeechFinished()) }

    val speaker = remember {
        CallSpeaker(ctx, { ok -> speakerReady = ok }, { wordTick++ }, { if (genDone) finishTurn() }).also { speakerRef[0] = it }
    }

    fun endCall(reason: String?) {
        voice.stop(); speaker.stop(); llm[0]?.cancel(); turn++
        phase = Phase.Ended; machine.onUserEnd(); level = 0f
        note = reason
    }

    fun think(text: String) {
        val my = ++turn; heard = text; answer = ""; generating = true; genDone = false; phase = Phase.Thinking
        val model = wf.usableModel()
        if (model == null) { endCall("কোনো AI মডেল বসানো নেই। হোমে গিয়ে মডেল বসিয়ে আবার কল করুন।"); return }
        scope.launch {
            val buf = SentenceBuffer(); val sb = StringBuilder()
            fun emit(s: String) { if (my != turn) return; if (machine.phase == Phase.Thinking) { machine.onAnswerStarted(); phase = machine.phase }; sb.append(s).append(' '); answer = sb.toString().trim(); speaker.speak(s) }
            val pieces = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.UNLIMITED)
            val consumer = launch { for (p in pieces) if (my == turn) buf.push(p).forEach(::emit) }   // main thread, in order
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    val m = llm[0] ?: LocalLlm(model, File(cacheDir, "llm")).also { llm[0] = it }
                    m.askStream(Prompts.CALL_SYSTEM, Prompts.callPrompt(history, text)) { piece -> pieces.trySend(piece) }
                }
            }
            pieces.close(); consumer.join()
            if (my != turn) return@launch   // interrupted or ended: someone else owns the state now
            ok.onFailure { generating = false; genDone = true; speaker.stop(); endCall("উত্তর বানাতে ব্যর্থ: ${it.javaClass.simpleName}"); return@launch }
            buf.flush()?.let { emit(it) }
            if (sb.isEmpty()) { answer = "(কোনো উত্তর আসেনি)" } else history += text to sb.toString().trim()
            generating = false; genDone = true
            if (!speaker.isSpeaking) finishTurn()   // nothing queued (no voice / empty): go back to listening
        }
    }

    fun listenNext() {
        if (machine.phase != Phase.Listening) return
        phase = Phase.Listening; level = 0f
        scope.launch {
            delay(350)   // let the recognizer/audio route settle between turns
            if (machine.phase != Phase.Listening) return@launch
            voice.listen({ t ->
                level = 0f
                if (machine.phase != Phase.Listening) return@listen
                if (VoiceCallMachineHelper.interrupt(t)) { actRef[0]?.invoke(Action.Listen); return@listen }
                actRef[0]?.invoke(machine.onHeard(t))
            }, { e ->
                level = 0f
                if (machine.phase != Phase.Listening) return@listen
                if (e == "কিছু শোনা যায়নি") actRef[0]?.invoke(machine.onSilence()) else endCall(e)
            }, { level = it })
        }
    }

    val act: (Action) -> Unit = { a ->
        when (a) {
            Action.Listen -> listenNext()
            is Action.Think -> think(a.userText)
            Action.StopSpeaking -> { speaker.stop(); llm[0]?.cancel(); turn++; generating = false; genDone = true; listenNext() }
            is Action.End -> endCall(a.reason)
            Action.None -> {}
        }
        phase = machine.phase
    }
    actRef[0] = act

    fun start() {
        ReadAloudController.stop()   // no background reading while the call is on
        note = null; heard = ""; answer = ""; history.clear()
        if (wf.usableModel() == null) { note = "কোনো AI মডেল বসানো নেই। হোমে ফিরে 'মডেল ফাইল বসান' চাপুন।"; return }
        if (!voice.available()) { note = "এই ফোনে অফলাইন (on-device) ভয়েস চেনা নেই, তাই কথা শোনা যাবে না; ইন্টারনেট-ভিত্তিক বিকল্পে যাওয়া হয় না। নিচের ঘরে লিখে কলের মতো চালাতে পারেন।" }
        act(machine.start())
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) start() else note = "মাইক্রোফোনের অনুমতি ছাড়া ভয়েস কল চলবে না।"
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { if (store.save(uri)) { photo = store.load(); note = null } else note = "ছবিটি পড়া যায়নি।" }
    }

    // Leaving the screen or sending the app to the background ends the call and releases the microphone.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP && machine.phase != Phase.Idle && machine.phase != Phase.Ended) endCall("অ্যাপ পর্দা থেকে সরে যাওয়ায় কল শেষ হয়েছে") }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); voice.stop(); speaker.shutdown(); llm[0]?.cancel(); llm[0]?.close(); turn++ }
    }

    val active = phase != Phase.Idle && phase != Phase.Ended
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (active) endCall(null); onBack() }) { Text("← ফিরে যান") }
            Text("ভয়েস কল", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        }
        AvatarOrb(photo?.asImageBitmap(), painterResource(R.drawable.logo_ps), phase, level, wordTick,
            Modifier.fillMaxWidth(0.85f).clickable(enabled = phase == Phase.Speaking || phase == Phase.Thinking) { act(machine.onInterrupt()) })
        Text(when (phase) {
            Phase.Idle -> "কল শুরু করুন"; Phase.Listening -> "🎤 শুনছি… বলুন"; Phase.Thinking -> "ভাবছি… (ছবিতে চাপলে থামে)"
            Phase.Speaking -> "🔊 বলছি… (ছবিতে চাপলে থামিয়ে আপনাকে শুনব)"; Phase.Ended -> "কল শেষ"
        }, style = MaterialTheme.typography.titleMedium, color = SkyBlue, textAlign = TextAlign.Center)
        if (active) Text("মাইক শুধু ‘শুনছি’ অবস্থায় চালু থাকে। কিছু রেকর্ড বা সংরক্ষণ হয় না।", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (heard.isNotBlank()) Text("আপনি: $heard", style = MaterialTheme.typography.bodyMedium)
        if (answer.isNotBlank()) Text("সহকারী: $answer", style = MaterialTheme.typography.bodyMedium)
        if (speakerReady == false && active) Text("⚠️ ফোনে অফলাইন বাংলা কণ্ঠ নেই, তাই উত্তর শুধু লেখায় দেখানো হবে (ফোনের সেটিংস → Text-to-speech থেকে বাংলা ভয়েস নামান)।", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        note?.let { Text(it, color = if (phase == Phase.Ended) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
        if (!active) {
            Button(onClick = { micPerm.launch(Manifest.permission.RECORD_AUDIO) }) { Text("📞 কল শুরু করুন") }
            OutlinedButton(onClick = { pickPhoto.launch("image/*") }) { Text(if (photo == null) "🖼️ আমার ছবি বসান" else "🖼️ ছবি বদলান") }
            if (photo != null) TextButton(onClick = { store.clear(); photo = null }) { Text("ছবি মুছুন") }
            Text("ছবি শুধু এই ফোনে, এনক্রিপ্টেড অবস্থায় থাকে — কোথাও পাঠানো হয় না। ছবির মুখ নড়ে না; ছবি শ্বাস নেওয়ার মতো দোলে, শোনার সময় মাইকের মাত্রায় আর বলার সময় প্রতিটি শব্দে স্পন্দিত হয়।", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        } else {
            Button(onClick = { endCall(null) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("📵 কল শেষ") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(typed, { typed = it }, label = { Text("মুখে না বলে লিখেও বলতে পারেন") }, modifier = Modifier.weight(1f), maxLines = 3)
                Button(enabled = typed.isNotBlank() && machine.phase == Phase.Listening, onClick = { val t = typed; typed = ""; voice.stop(); act(machine.onHeard(t)) }) { Text("পাঠান") }
            }
        }
    }
}

/** Tiny seam so the screen file stays readable. */
private object VoiceCallMachineHelper { fun interrupt(t: String) = VoiceCallMachine.isInterruptPhrase(t) }
