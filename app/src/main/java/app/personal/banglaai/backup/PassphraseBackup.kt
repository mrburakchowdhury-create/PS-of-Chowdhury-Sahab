package app.personal.banglaai.backup

import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.streamingaead.AesGcmHkdfStreamingKey
import com.google.crypto.tink.streamingaead.AesGcmHkdfStreamingParameters
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import com.google.crypto.tink.util.SecretBytes
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Offline recovery container. The key comes ONLY from the owner's passphrase (PBKDF2-HMAC-SHA256, random salt) — there is no
 * master key, no escrow, no backdoor: lose the passphrase and the backup is unreadable, by design. Encryption is Tink
 * AES-256-GCM-HKDF streaming (1 MB segments); the header (magic, iterations, salt) is authenticated as associated data, and a
 * wrong passphrase or any tampering fails with [BadBackup] instead of returning garbage.
 */
object PassphraseBackup {
    class BadBackup(msg: String) : Exception(msg)
    private val MAGIC = byteArrayOf('B'.code.toByte(), 'A'.code.toByte(), 'I'.code.toByte(), '1'.code.toByte())
    const val DEFAULT_ITERATIONS = 600_000
    const val MIN_PASSPHRASE = 10

    private fun aead(pass: CharArray, salt: ByteArray, iterations: Int): StreamingAead {
        StreamingAeadConfig.register()
        val spec = PBEKeySpec(pass, salt, iterations, 256)
        val raw = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
        val params = AesGcmHkdfStreamingParameters.builder().setKeySizeBytes(32).setDerivedAesGcmKeySizeBytes(32)
            .setHkdfHashType(AesGcmHkdfStreamingParameters.HashType.SHA256).setCiphertextSegmentSizeBytes(1 shl 20).build()
        val key = AesGcmHkdfStreamingKey.create(params, SecretBytes.copyFrom(raw, InsecureSecretKeyAccess.get()))
        raw.fill(0)
        return KeysetHandle.newBuilder().addEntry(KeysetHandle.importKey(key).withFixedId(1).makePrimary()).build().getPrimitive(StreamingAead::class.java)
    }

    private fun header(salt: ByteArray, iterations: Int) = java.io.ByteArrayOutputStream().also {
        DataOutputStream(it).apply { write(MAGIC); writeInt(iterations); write(salt) }
    }.toByteArray()

    /** Returns the stream to write plaintext into; close it to finish. */
    fun encryptTo(out: OutputStream, pass: CharArray, iterations: Int = DEFAULT_ITERATIONS): OutputStream {
        require(pass.size >= MIN_PASSPHRASE) { "পাসফ্রেজ কমপক্ষে $MIN_PASSPHRASE অক্ষরের হতে হবে" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val h = header(salt, iterations); out.write(h)
        return aead(pass, salt, iterations).newEncryptingStream(out, h)
    }

    /** Returns a decrypting stream; reading it throws if the passphrase is wrong or data was altered. */
    fun decryptFrom(input: InputStream, pass: CharArray): InputStream {
        val d = DataInputStream(input)
        val magic = ByteArray(4); try { d.readFully(magic) } catch (e: Exception) { throw BadBackup("ফাইলটি ব্যাকআপ নয়") }
        if (!magic.contentEquals(MAGIC)) throw BadBackup("ফাইলটি এই অ্যাপের ব্যাকআপ নয়")
        val iterations = d.readInt(); val salt = ByteArray(16).also { d.readFully(it) }
        if (iterations !in 100_000..5_000_000) throw BadBackup("ব্যাকআপের হেডার অগ্রহণযোগ্য")
        return aead(pass, salt, iterations).newDecryptingStream(input, header(salt, iterations))
    }
}
