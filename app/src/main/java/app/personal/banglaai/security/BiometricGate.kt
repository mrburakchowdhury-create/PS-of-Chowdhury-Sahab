package app.personal.banglaai.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Proof-of-biometric via a hardware-backed Keystore key that can only be used right after a STRONG biometric match
 * (per-use authentication, no time window). The key is invalidated by Android when biometrics are added/removed, so
 * someone enrolling their own fingerprint on the phone cannot reuse it; the owner must then unlock with the app PIN.
 * Android decides what "strong" is; the app cannot tell whose finger it was beyond what the OS reports.
 * Status: compiles; behaviour on a real device is device-test pending.
 */
object BiometricGate {
    private const val ALIAS = "banglaai_bio_gate"
    private val ks get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun createKey(): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
        init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)   // every use needs a fresh strong biometric
            .setInvalidatedByBiometricEnrollment(true).build())
    }.generateKey()

    sealed interface Prep { data class Ready(val cipher: Cipher) : Prep; data object Invalidated : Prep }

    /** Cipher to hand to BiometricPrompt as a CryptoObject. If the old key was invalidated, a new one is created and [Invalidated] is returned once. */
    fun prepare(): Prep {
        val existing = ks.getKey(ALIAS, null) as? SecretKey
        if (existing == null) createKey()
        return try {
            Prep.Ready(Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, ks.getKey(ALIAS, null) as SecretKey) })
        } catch (e: KeyPermanentlyInvalidatedException) {
            ks.deleteEntry(ALIAS); createKey(); Prep.Invalidated
        }
    }

    /** Called with the cipher from the successful prompt: using it is the cryptographic proof that a strong biometric just matched. */
    fun confirm(cipher: Cipher): Boolean = runCatching { cipher.doFinal(ByteArray(16)); true }.getOrDefault(false)
}
