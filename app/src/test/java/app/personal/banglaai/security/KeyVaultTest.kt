package app.personal.banglaai.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.personal.banglaai.storage.Vault
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyVaultTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val pepper = object : Pepper { override fun wrap(b: ByteArray) = b.reversedArray(); override fun unwrap(b: ByteArray) = b.reversedArray() }
    private fun kv() = KeyVault(ctx, pepper, iterations = 1000)
    private val aad = "x".toByteArray()

    @Test fun lockedVaultRefusesAndWrongPinFails() {
        val k = kv(); assertTrue(k.unlockWithPin("123456".toCharArray()))
        val blob = Vault.sealWith(k.streamingAead(), "গোপন".toByteArray(), aad)
        val fresh = kv()   // simulates a new process: nothing in memory
        try { fresh.streamingAead(); fail("must be locked") } catch (_: VaultLockedException) {}
        assertFalse(fresh.unlockWithPin("000000".toCharArray()))
        try { fresh.streamingAead(); fail("still locked") } catch (_: VaultLockedException) {}
        assertTrue(fresh.unlockWithPin("123456".toCharArray()))
        assertArrayEquals("গোপন".toByteArray(), Vault.unsealWith(fresh.streamingAead(), blob, aad))
    }

    @Test fun masterReleasedByBiometricBlobOpensTheSameKeys() {
        val k = kv(); k.unlockWithPin("123456".toCharArray())
        val blob = Vault.sealWith(k.streamingAead(), "abc".toByteArray(), aad)
        val s = k.masterForWrapping()!!
        val other = kv(); assertTrue(other.unlockWithMaster(s))
        assertArrayEquals("abc".toByteArray(), Vault.unsealWith(other.streamingAead(), blob, aad))
        assertFalse(kv().unlockWithMaster(ByteArray(32)))
    }

    @Test fun changePinKeepsDataAndRetiresOldPin() {
        val k = kv(); k.unlockWithPin("123456".toCharArray())
        val blob = Vault.sealWith(k.streamingAead(), "d".toByteArray(), aad)
        k.changePin("654321".toCharArray())
        val n = kv()
        assertFalse(n.unlockWithPin("123456".toCharArray()))
        assertTrue(n.unlockWithPin("654321".toCharArray()))
        assertArrayEquals("d".toByteArray(), Vault.unsealWith(n.streamingAead(), blob, aad))
    }

    @Test fun nothingInPrefsRevealsThePinOrKey() {
        kv().unlockWithPin("123456".toCharArray())
        val all = ctx.getSharedPreferences("vault_key2", Context.MODE_PRIVATE).all.values.joinToString()
        assertFalse(all.contains("123456"))
    }
}
