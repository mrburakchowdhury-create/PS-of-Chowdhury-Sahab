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
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        setContent { App(activity = this, unlocked = unlocked, onUnlocked = { unlocked = true }, onLock = { unlocked = false }) }
    }

    override fun onStop() { super.onStop(); leftAt = SystemClock.elapsedRealtime() }

    override fun onStart() {
        super.onStart()
        if (unlocked && leftAt != 0L && SystemClock.elapsedRealtime() - leftAt > INACTIVITY_LOCK_MS) unlocked = false
    }

    override fun onDestroy() { unregisterReceiver(screenOff); super.onDestroy() }

    fun canUseStrongBiometric() =
        BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    fun promptBiometric(title: String, negative: String, onSuccess: () -> Unit) {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder().setTitle(title).setNegativeButtonText(negative)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).build()
        )
    }

    private companion object { const val INACTIVITY_LOCK_MS = 30_000L }
}
