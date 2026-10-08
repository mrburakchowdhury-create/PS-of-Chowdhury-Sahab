package app.personal.banglaai.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.BinaryKeysetReader
import com.google.crypto.tink.BinaryKeysetWriter
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Extra outer layer around the PIN-wrapped key, held by the phone's Keystore, so a stolen copy of the app's files cannot be brute-forced off the phone. */
interface Pepper { fun wrap(b: ByteArray): ByteArray; fun unwrap(b: ByteArray): ByteArray }

class KeystorePepper : Pepper {
    private val ks get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun key(): SecretKey = (ks.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
        init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
    }.generateKey()
    // The Keystore insists on choosing the IV itself (a caller-made IV is refused), so: init without IV, read the IV it made, store it in front.
    override fun wrap(b: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()); updateAAD(ALIAS.toByteArray()) }
        return c.iv + c.doFinal(b)
    }
    override fun unwrap(b: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b.copyOfRange(0, 12))); updateAAD(ALIAS.toByteArray()) }
        return c.doFinal(b, 12, b.size - 12)
    }
    private companion object { const val ALIAS = "banglaai_pin_pepper2" }
}

internal fun gcmSeal(key: SecretKey, plain: ByteArray, aad: ByteArray): ByteArray {
    val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
    val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)); updateAAD(aad) }
    return iv + c.doFinal(plain)
}
internal fun gcmOpen(key: SecretKey, blob: ByteArray, aad: ByteArray): ByteArray {
    require(blob.size > 12 + 16) { "bad blob" }
    val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob.copyOfRange(0, 12))); updateAAD(aad) }
    return c.doFinal(blob, 12, blob.size - 12)
}
private class GcmAead(private val k: SecretKey) : Aead {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray) = gcmSeal(k, plaintext, associatedData)
    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray) = gcmOpen(k, ciphertext, associatedData)
}

class VaultLockedException : IllegalStateException("ভল্ট বন্ধ — আগে PIN দিয়ে অ্যাপ খুলুন")

/**
 * The vault's data key is no longer protected by a Keystore key alone: a random 32-byte master secret S encrypts the Tink keyset, and S is stored
 * only (a) wrapped by a key derived from the owner's PIN (PBKDF2) inside a Keystore-held outer layer, and (b) optionally wrapped for biometric unlock
 * by a Keystore RSA key that needs a fresh STRONG biometric to use. Without the PIN (or a matching biometric on this phone) the files stay sealed.
 * S lives in process memory after unlock so background jobs keep running while the screen is locked; it is gone when the process dies.
 */
class KeyVault(
    private val ctx: Context, private val pepper: Pepper = KeystorePepper(), private val iterations: Int = 210_000,
    /** Finds the keyset of an OLDER app version (Keystore-only protection) or null if there is none. Replaceable so the upgrade path can be tested without a real Keystore. */
    private val legacyLoader: (Context) -> KeysetHandle? = ::loadLegacyKeyset,
) {
    private val prefs = ctx.getSharedPreferences("vault_key2", Context.MODE_PRIVATE)
    @Volatile private var master: ByteArray? = null
    @Volatile private var cached: StreamingAead? = null
    private val session = java.util.concurrent.atomic.AtomicInteger(0)   // bumped by every unlock and every lock: a stream made in an older session is dead

    val isUnlocked get() = master != null
    fun sessionId() = session.get()

    /** Ends the unlock session: wipes OUR copy of the master secret, drops the cached crypto object and kills every stream made in this session. Safe to call twice. */
    @Synchronized fun lock() {
        session.incrementAndGet()
        master?.fill(0); master = null; cached = null
    }
    fun hasBio() = prefs.contains(P_BIO)

    /** Unlocks with the PIN; creates (fresh install) or migrates (older install) the key on first use. False = wrong PIN. */
    @Synchronized fun unlockWithPin(pin: CharArray): Boolean {
        if (!prefs.contains(P_KEYSET)) { if (!prefs.contains(P_PIN)) { createOrMigrate(pin); return true } }
        val s = runCatching {
            val salt = d(prefs.getString(P_SALT, null)!!)
            gcmOpen(kek(pin, salt), outerOpen(d(prefs.getString(P_PIN, null)!!)), AAD_PIN)
        }.getOrNull() ?: return false
        adopt(s); return true
    }

    /** Biometric path: [wrapped] is the blob decrypted by the Keystore RSA key (done by the prompt's cipher). */
    @Synchronized fun unlockWithMaster(s: ByteArray): Boolean = runCatching { adopt(s); true }.getOrElse { false }

    fun bioBlob(): ByteArray? = prefs.getString(P_BIO, null)?.let(::d)
    fun setBioBlob(b: ByteArray?) { prefs.edit().apply { if (b == null) remove(P_BIO) else putString(P_BIO, e(b)) }.apply() }
    fun masterForWrapping(): ByteArray? = master?.copyOf()

    @Synchronized fun changePin(newPin: CharArray) { val s = master ?: throw VaultLockedException(); storePin(s, newPin) }

    @Synchronized fun streamingAead(): StreamingAead {
        cached?.let { return it }
        val s = master ?: throw VaultLockedException()
        StreamingAeadConfig.register()
        val h = KeysetHandle.read(BinaryKeysetReader.withBytes(d(prefs.getString(P_KEYSET, null)!!)), GcmAead(SecretKeySpec(s, "AES")))
        val sid = session.get()
        return GuardedStreamingAead(h.getPrimitive(StreamingAead::class.java)) { session.get() == sid && master != null }.also { cached = it }
    }

    private fun outerOpen(b: ByteArray) = if (prefs.getBoolean(P_NOPEPPER, false)) b else pepper.unwrap(b)

    private fun adopt(s: ByteArray) {
        require(s.size == 32)
        session.incrementAndGet(); master = s; cached = null
        try { streamingAead() }   // proves S really opens the keyset; throws otherwise
        catch (t: Throwable) { session.incrementAndGet(); master = null; cached = null; throw t }   // a wrong secret must not stay installed (the caller's array is theirs to wipe)
    }

    private fun createOrMigrate(pin: CharArray) {
        StreamingAeadConfig.register()
        val s = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val old = legacyLoader(ctx); val legacy = old != null   // an older install's keyset is carried over (same key => every existing file stays readable)
        val handle = old ?: KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM_HKDF_4KB"))
        val out = ByteArrayOutputStream(); handle.write(BinaryKeysetWriter.withOutputStream(out), GcmAead(SecretKeySpec(s, "AES")))
        val keyset = out.toByteArray()
        storePin(s, pin)
        prefs.edit().putString(P_KEYSET, e(keyset)).apply()
        try { adopt(s) } catch (t: Throwable) { prefs.edit().clear().apply(); master = null; cached = null; throw t }
        if (legacy) {   // only after the new copy is proven to open: drop the old Keystore-only copy
            runCatching { ctx.deleteSharedPreferences(LEGACY_PREFS) }
            runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("banglaai_vault_master") }
        }
    }

    private fun storePin(s: ByteArray, pin: CharArray) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val inner = gcmSeal(kek(pin, salt), s, AAD_PIN)
        // If this phone's Keystore cannot do the outer layer, fall back to PIN-only protection (still PIN-bound) instead of locking the owner out; the choice is remembered.
        val outer = runCatching { pepper.wrap(inner) }.getOrNull()
        prefs.edit().putString(P_SALT, e(salt)).putString(P_PIN, e(outer ?: inner)).putBoolean(P_NOPEPPER, outer == null).apply()
    }

    private fun kek(pin: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(pin, salt, iterations, 256)
        try { return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES") } finally { spec.clearPassword() }
    }

    private fun e(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun d(s: String) = Base64.decode(s, Base64.NO_WRAP)

    private companion object {
        const val P_KEYSET = "keyset"; const val P_SALT = "salt"; const val P_PIN = "pinwrap"; const val P_BIO = "biowrap"; const val P_NOPEPPER = "nopepper"
        const val LEGACY_NAME = "vault_keyset"; const val LEGACY_PREFS = "vault_keyset_prefs"
        fun loadLegacyKeyset(ctx: Context): KeysetHandle? {
            if (!ctx.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE).contains(LEGACY_NAME)) return null
            StreamingAeadConfig.register()
            return AndroidKeysetManager.Builder().withSharedPref(ctx, LEGACY_NAME, LEGACY_PREFS)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM_HKDF_4KB")).withMasterKeyUri("android-keystore://banglaai_vault_master").build().keysetHandle
        }
        val AAD_PIN = "banglaai-pin-wrap".toByteArray()
    }
}
