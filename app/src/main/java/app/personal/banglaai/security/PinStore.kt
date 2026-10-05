package app.personal.banglaai.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** App PIN: salted PBKDF2 hash only (never the PIN), with escalating delay on repeated failures. Never wipes data. */
class PinStore(context: Context) {
    sealed interface Result {
        data object Ok : Result
        data class Wrong(val attemptsBeforeDelay: Int) : Result
        data class Locked(val seconds: Long) : Result
    }

    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    fun isSet() = prefs.contains(KEY_HASH)

    fun set(pin: CharArray) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, b64(salt))
            .putString(KEY_HASH, b64(hash(pin, salt)))
            .putInt(KEY_FAILS, 0).putLong(KEY_LOCK_UNTIL, 0)
            .apply()
    }

    fun lockedSeconds(): Long =
        ((prefs.getLong(KEY_LOCK_UNTIL, 0) - System.currentTimeMillis()) / 1000).coerceAtLeast(0)

    fun verify(pin: CharArray): Result {
        val wait = lockedSeconds()
        if (wait > 0) return Result.Locked(wait)
        val salt = Base64.decode(prefs.getString(KEY_SALT, null) ?: return Result.Wrong(0), Base64.NO_WRAP)
        val expected = Base64.decode(prefs.getString(KEY_HASH, null) ?: return Result.Wrong(0), Base64.NO_WRAP)
        if (MessageDigest.isEqual(expected, hash(pin, salt))) {
            prefs.edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCK_UNTIL, 0).apply()
            return Result.Ok
        }
        val fails = prefs.getInt(KEY_FAILS, 0) + 1
        val edit = prefs.edit().putInt(KEY_FAILS, fails)
        if (fails >= FREE_ATTEMPTS) {
            val delay = (BASE_DELAY_S shl (fails - FREE_ATTEMPTS).coerceAtMost(7)).coerceAtMost(MAX_DELAY_S)
            edit.putLong(KEY_LOCK_UNTIL, System.currentTimeMillis() + delay * 1000)
            edit.apply()
            return Result.Locked(delay)
        }
        edit.apply()
        return Result.Wrong(FREE_ATTEMPTS - fails)
    }

    private fun hash(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, ITERATIONS, 256)
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)

    private companion object {
        const val KEY_SALT = "salt"; const val KEY_HASH = "hash"; const val KEY_FAILS = "fails"; const val KEY_LOCK_UNTIL = "lock_until"
        const val ITERATIONS = 210_000
        const val FREE_ATTEMPTS = 3
        const val BASE_DELAY_S = 30L
        const val MAX_DELAY_S = 3600L
    }
}
