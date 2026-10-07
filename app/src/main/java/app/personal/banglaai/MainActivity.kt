package app.personal.banglaai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
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
        override fun onReceive(c: Context?, i: Intent?) { unlocked = false }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Hide content from screenshots and the recent-apps preview.
        app.personal.banglaai.ai.LocalLlm.preferGpu = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("gpu", false)
        applyScreenshotPolicy()
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        // A crash or kill mid-extraction can leave a plaintext PDF working copy in the cache; remove any such leftovers at every start.
        cacheDir.listFiles { f -> f.name.startsWith("pdf-") || f.name == "photos" }?.forEach { it.deleteRecursively() }
        setContent { App(activity = this, unlocked = unlocked, onUnlocked = { unlocked = true }, onLock = { unlocked = false }) }
    }

    /** Screenshots/screen-recording and the recent-apps preview are allowed only if the owner turned that on in Settings (default: allowed, as requested). */
    fun applyScreenshotPolicy() {
        val allowed = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("allow_screenshots", true)
        if (allowed) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onStop() { super.onStop(); leftAt = SystemClock.elapsedRealtime() }

    override fun onStart() {
        super.onStart()
        if (unlocked && leftAt != 0L && SystemClock.elapsedRealtime() - leftAt > INACTIVITY_LOCK_MS) unlocked = false
    }

    override fun onDestroy() { unregisterReceiver(screenOff); super.onDestroy() }

    fun canUseStrongBiometric() =
        BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /** Returns a message to show the user if biometric cannot be used right now (e.g. key invalidated by new enrollment). */
    fun promptBiometric(title: String, negative: String, onSuccess: () -> Unit, onProblem: (String) -> Unit = {}) {
        val prepared = runCatching { BiometricGate.prepare() }
        prepared.exceptionOrNull()?.let { return onProblem("এই ফোনে বায়োমেট্রিক চালু করা যায়নি (${it.javaClass.simpleName}); PIN ব্যবহার করুন। ফোনে স্ক্রিন-লক ও আঙুলের ছাপ সেট করা আছে কি না দেখুন।") }
        val cipher = when (val prep = prepared.getOrThrow()) {
            is BiometricGate.Prep.Ready -> prep.cipher
            BiometricGate.Prep.Invalidated -> return onProblem("ফোনে আঙুলের ছাপ/ফেস বদলেছে, তাই বায়োমেট্রিক বন্ধ হয়েছে। PIN দিয়ে খুলুন; পরের বার আবার বায়োমেট্রিক চলবে।")
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                // Unlock only if the Keystore key actually worked; a UI-only "success" is not enough.
                if (result.cryptoObject?.cipher?.let(BiometricGate::confirm) == true) onSuccess() else onProblem("বায়োমেট্রিক যাচাই সম্পূর্ণ হয়নি — PIN ব্যবহার করুন")
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder().setTitle(title).setNegativeButtonText(negative)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).build(),
            BiometricPrompt.CryptoObject(cipher)
        )
    }

    private companion object { const val INACTIVITY_LOCK_MS = 30_000L }
}
