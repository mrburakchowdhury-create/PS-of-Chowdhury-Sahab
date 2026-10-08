package app.personal.banglaai.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/**
 * Biometric unlock that really releases the vault secret: after a PIN unlock the 32-byte master secret is encrypted with the PUBLIC half of a Keystore RSA key
 * (no authentication needed to encrypt); the PRIVATE half needs a fresh STRONG biometric for every use, so only a matching biometric can decrypt it again.
 * The key is invalidated by Android when biometrics are added/removed — then only the PIN works until the owner unlocks with it once (which re-arms biometrics).
 * Status: compiles; behaviour on a real device is device-test pending. The PIN path never depends on this.
 */
object BiometricGate {
    private const val ALIAS = "banglaai_bio_rsa"
    private const val TRANSFORM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
    private val oaep = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
    private val ks get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun create() {
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply {
            initialize(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)   // every use needs a fresh strong biometric
                .setInvalidatedByBiometricEnrollment(true).build())
        }.generateKeyPair()
    }

    fun clear() { runCatching { ks.deleteEntry(ALIAS) } }

    /** Encrypts [master] for later biometric release; null if the phone cannot do it (then PIN is the only way, which is fine). */
    fun wrap(master: ByteArray): ByteArray? = runCatching {
        if (!ks.containsAlias(ALIAS)) create()
        val pub = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(ks.getCertificate(ALIAS).publicKey.encoded))
        Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, pub, oaep) }.doFinal(master)
    }.getOrNull()

    sealed interface Prep { data class Ready(val cipher: Cipher) : Prep; data object Invalidated : Prep; data object NotSet : Prep }

    /** Cipher for BiometricPrompt's CryptoObject. */
    fun prepare(): Prep {
        if (!ks.containsAlias(ALIAS)) return Prep.NotSet
        return try {
            Prep.Ready(Cipher.getInstance(TRANSFORM).apply { init(Cipher.DECRYPT_MODE, ks.getKey(ALIAS, null) as PrivateKey, oaep) })
        } catch (e: KeyPermanentlyInvalidatedException) { clear(); Prep.Invalidated }
    }

    /** Called with the cipher from the successful prompt; null means the biometric did not really release the secret. */
    fun release(cipher: Cipher, blob: ByteArray): ByteArray? = runCatching { cipher.doFinal(blob) }.getOrNull()
}
