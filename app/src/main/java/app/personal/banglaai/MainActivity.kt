package app.personal.banglaai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import java.io.File
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.personal.banglaai.security.AppLock
import app.personal.banglaai.security.BiometricGate
import app.personal.banglaai.ui.App

class MainActivity : FragmentActivity() {
    var unlocked by mutableStateOf(false)
        private set
    private var leftAt = 0L
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val lockLater = Runnable { if (!isChangingConfigurations && !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) lockNow() }

    /** The ONE lock path (screen off, background timeout, lock button, closing the screen): UI back to the PIN page AND the vault key session ended (see AppLock). */
    fun lockNow() {
        unlocked = false
        app.personal.banglaai.ai.LocalLlm.cancelInteractive()
        AppLock.lock(app.personal.banglaai.ui.AppGraph.keysIfCreated())
    }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { lockNow() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Screenshots/recents: allowed only if the owner left that on in Settings; while the app is LOCKED the screen is always protected (see applyScreenshotPolicy).
        app.personal.banglaai.ai.LocalLlm.preferGpu = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("gpu", false)
        applyScreenshotPolicy()
        app.personal.banglaai.ui.CrashLog.install(this)
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        if (!hooked) { hooked = true   // once per process, before any lock can happen
            val appCtx = applicationContext
            AppLock.addBeforeLock { File(appCtx.cacheDir, "photos").deleteRecursively() }   // a not-yet-imported document photo is plaintext: it does not survive a lock
            AppLock.addBeforeLock { app.personal.banglaai.backup.BackupService.requestCancel() }   // a restore in progress stops; its staged copy is discarded, the vault is untouched
            AppLock.addBeforeLock { app.personal.banglaai.ui.AppGraph.workflowIfCreated()?.pause() }   // jobs stop at their next safe point, model calls are cancelled
            AppLock.addBeforeLock { app.personal.banglaai.speech.ReadAloudController.onVaultLock(appCtx) }   // reading stops unless the owner allowed limited background reading
        }
        // A crash or kill mid-extraction can leave a plaintext PDF working copy in the cache; remove any such leftovers at every start.
        if (!cleaned) { cleaned = true; cacheDir.listFiles { f -> f.name.startsWith("pdf-") || f.name == "photos" }?.forEach { it.deleteRecursively() }
            // interrupted imports/copies leave half-written ".tmp" files (up to GBs): nothing is running at process start, so they are garbage
            listOf("vault", "models", "derived").forEach { d -> File(noBackupFilesDir, d).walkTopDown().filter { it.isFile && it.name.endsWith(".tmp") }.forEach { it.delete() } } }   // once per process: an Activity re-creation must not delete a running job's working copy
        setContent { androidx.compose.runtime.LaunchedEffect(unlocked) { applyScreenshotPolicy() }; App(activity = this, unlocked = unlocked, onUnlocked = { unlocked = true }, onLock = { lockNow() }) }
    }

    /** The lock/PIN screen is always hidden from screenshots and recents. After unlocking, screenshots/screen-recording and the recents preview are allowed only if the owner turned that on in Settings (default: NOT allowed — private vault). */
    fun applyScreenshotPolicy() {
        val allowed = unlocked && getSharedPreferences("settings", MODE_PRIVATE).getBoolean("allow_screenshots", false)
        if (allowed) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onStop() {
        super.onStop(); leftAt = SystemClock.elapsedRealtime()
        handler.removeCallbacks(lockLater); if (!isChangingConfigurations) handler.postDelayed(lockLater, INACTIVITY_LOCK_MS)   // crypto lock too, not only the screen
        handler.postDelayed({ if (!isChangingConfigurations && !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) app.personal.banglaai.ai.LocalLlm.releaseAll() }, 60_000L)
    }

    /** Android is short of memory: give the model's RAM back first (it is by far the biggest thing we hold). */
    override fun onTrimMemory(level: Int) { super.onTrimMemory(level); if (level in android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW..android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL || level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) app.personal.banglaai.ai.LocalLlm.releaseAll() }   // not UI_HIDDEN (20): that fires on every Home press / file picker

    override fun onStart() {
        super.onStart()
        handler.removeCallbacks(lockLater)
        if (leftAt != 0L && SystemClock.elapsedRealtime() - leftAt > INACTIVITY_LOCK_MS) lockNow()
    }

    override fun onDestroy() {
        unregisterReceiver(screenOff); handler.removeCallbacks(lockLater)
        if (isFinishing) lockNow()   // the screen is being closed for good: do not leave the key session open behind no UI
        super.onDestroy()
    }

    fun canUseStrongBiometric() =
        BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /** Biometric unlock: the secret is released only if the Keystore key really worked. Returns a message via [onProblem] if it cannot be used right now. */
    fun promptBiometric(title: String, negative: String, blob: ByteArray, onSecret: (ByteArray) -> Unit, onProblem: (String) -> Unit = {}) {
        val prepared = runCatching { BiometricGate.prepare() }
        prepared.exceptionOrNull()?.let { return onProblem("এই ফোনে বায়োমেট্রিক চালু করা যায়নি (${it.javaClass.simpleName}); PIN ব্যবহার করুন।") }
        val cipher = when (val prep = prepared.getOrThrow()) {
            is BiometricGate.Prep.Ready -> prep.cipher
            BiometricGate.Prep.Invalidated -> return onProblem("ফোনে আঙুলের ছাপ/ফেস বদলেছে, তাই বায়োমেট্রিক বন্ধ হয়েছে। PIN দিয়ে খুলুন; তারপর আবার বায়োমেট্রিক চালু হবে।")
            BiometricGate.Prep.NotSet -> return onProblem("বায়োমেট্রিক এখনো চালু নয় — একবার PIN দিয়ে খুলুন।")
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val s = result.cryptoObject?.cipher?.let { BiometricGate.release(it, blob) }
                if (s != null) onSecret(s) else onProblem("বায়োমেট্রিক যাচাই সম্পূর্ণ হয়নি — PIN ব্যবহার করুন")
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder().setTitle(title).setNegativeButtonText(negative)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).build(),
            BiometricPrompt.CryptoObject(cipher)
        )
    }

    private companion object {
        @Volatile private var cleaned = false; @Volatile private var hooked = false; // photo/file pickers and permission dialogs are other screens; a short limit locked the app while they were open
        const val INACTIVITY_LOCK_MS = 90_000L }
}
