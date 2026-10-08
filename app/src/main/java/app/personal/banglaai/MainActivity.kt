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
import app.personal.banglaai.security.BiometricGate
import app.personal.banglaai.ui.App

class MainActivity : FragmentActivity() {
    var unlocked by mutableStateOf(false)
        private set
    private var leftAt = 0L

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { unlocked = false; app.personal.banglaai.ai.LocalLlm.cancelInteractive() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Screenshots/recents: allowed only if the owner left that on in Settings; while the app is LOCKED the screen is always protected (see applyScreenshotPolicy).
        app.personal.banglaai.ai.LocalLlm.preferGpu = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("gpu", false)
        applyScreenshotPolicy()
        app.personal.banglaai.ui.CrashLog.install(this)
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        // A crash or kill mid-extraction can leave a plaintext PDF working copy in the cache; remove any such leftovers at every start.
        if (!cleaned) { cleaned = true; cacheDir.listFiles { f -> f.name.startsWith("pdf-") || f.name == "photos" }?.forEach { it.deleteRecursively() }
            // interrupted imports/copies leave half-written ".tmp" files (up to GBs): nothing is running at process start, so they are garbage
            listOf("vault", "models", "derived").forEach { d -> File(noBackupFilesDir, d).walkTopDown().filter { it.isFile && it.name.endsWith(".tmp") }.forEach { it.delete() } } }   // once per process: an Activity re-creation must not delete a running job's working copy
        setContent { androidx.compose.runtime.LaunchedEffect(unlocked) { applyScreenshotPolicy() }; App(activity = this, unlocked = unlocked, onUnlocked = { unlocked = true }, onLock = { unlocked = false }) }
    }

    /** The lock/PIN screen is always hidden from screenshots and recents. After unlocking, screenshots/screen-recording and the recents preview are allowed only if the owner turned that on in Settings (default: allowed, as requested). */
    fun applyScreenshotPolicy() {
        val allowed = unlocked && getSharedPreferences("settings", MODE_PRIVATE).getBoolean("allow_screenshots", true)
        if (allowed) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onStop() { super.onStop(); leftAt = SystemClock.elapsedRealtime(); window.decorView.postDelayed({ if (!isChangingConfigurations && !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) app.personal.banglaai.ai.LocalLlm.releaseAll() }, 60_000L) }

    /** Android is short of memory: give the model's RAM back first (it is by far the biggest thing we hold). */
    override fun onTrimMemory(level: Int) { super.onTrimMemory(level); if (level in android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW..android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL || level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) app.personal.banglaai.ai.LocalLlm.releaseAll() }   // not UI_HIDDEN (20): that fires on every Home press / file picker

    override fun onStart() {
        super.onStart()
        if (unlocked && leftAt != 0L && SystemClock.elapsedRealtime() - leftAt > INACTIVITY_LOCK_MS) unlocked = false
    }

    override fun onDestroy() { unregisterReceiver(screenOff); super.onDestroy() }

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
        @Volatile private var cleaned = false; const val INACTIVITY_LOCK_MS = 30_000L }
}
