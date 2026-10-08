package app.personal.banglaai.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.personal.banglaai.storage.Vault
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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

    // ---- lock / session invalidation ----

    @Test fun lockWipesOurCopyAndKillsTheCachedCrypto() {
        val k = kv(); k.unlockWithPin("123456".toCharArray())
        val aead = k.streamingAead()
        val blob = Vault.sealWith(aead, "x".toByteArray(), aad)
        k.lock()
        assertFalse(k.isUnlocked)
        try { k.streamingAead(); fail("locked") } catch (_: VaultLockedException) {}
        try { Vault.sealWith(aead, "y".toByteArray(), aad); fail("old session must not encrypt") } catch (_: VaultLockedException) {}
        try { Vault.unsealWith(aead, blob, aad); fail("old session must not decrypt") } catch (_: VaultLockedException) {}
    }

    @Test fun masterArrayWeHoldIsZeroedOnLock() {
        val k = kv(); k.unlockWithPin("123456".toCharArray())
        val s = k.masterForWrapping()!!
        val k2 = kv(); assertTrue(k2.unlockWithMaster(s))
        assertTrue(s.any { it != 0.toByte() })
        k2.lock()
        assertTrue("our copy of the master secret must be zero after lock", s.all { it == 0.toByte() })
    }

    @Test fun streamOpenedBeforeLockFailsOnNextReadAfterLock() {
        val k = kv(); k.unlockWithPin("123456".toCharArray())
        val big = ByteArray(300_000) { (it % 251).toByte() }
        val blob = Vault.sealWith(k.streamingAead(), big, aad)
        val ins = k.streamingAead().newDecryptingStream(java.io.ByteArrayInputStream(blob), aad)
        assertTrue(ins.read(ByteArray(1000)) > 0)
        k.lock()
        try { ins.read(ByteArray(1000)); fail("a half-read file must not continue after lock") } catch (_: VaultLockedException) {}
    }

    @Test fun unlockingAgainStartsANewSessionAndOldStreamsStayDead() {
        val k = kv(); k.unlockWithPin("123456".toCharArray())
        val old = k.streamingAead(); val sid = k.sessionId()
        k.lock(); assertTrue(k.unlockWithPin("123456".toCharArray()))
        assertTrue(k.sessionId() > sid)
        try { Vault.sealWith(old, "z".toByteArray(), aad); fail("old session object stays dead") } catch (_: VaultLockedException) {}
        Vault.sealWith(k.streamingAead(), "z".toByteArray(), aad)   // the new session works
    }

    @Test fun appLockRunsHooksBeforeTheKeyIsWiped() {
        val k = kv(); k.unlockWithPin("123456".toCharArray())
        var unlockedDuringHook: Boolean? = null
        val hook = { unlockedDuringHook = k.isUnlocked }
        AppLock.addBeforeLock(hook)
        try { AppLock.lock(k) } finally { AppLock.removeBeforeLock(hook) }
        assertEquals(true, unlockedDuringHook); assertFalse(k.isUnlocked)
    }

    @Test fun lockMidTranslationPausesTheJobInsteadOfFailingPages() {
        val k = kv(); k.unlockWithPin("123456".toCharArray())
        val dir = java.io.File(ctx.cacheDir, "lockjob" + System.nanoTime()).apply { mkdirs() }
        val seal = { b: ByteArray, a: ByteArray -> Vault.sealWith(k.streamingAead(), b, a) }; val unseal = { b: ByteArray, a: ByteArray -> Vault.unsealWith(k.streamingAead(), b, a) }
        val src = app.personal.banglaai.doc.PageTextStore(java.io.File(dir, "s"), seal, unseal); val dst = app.personal.banglaai.doc.PageTextStore(java.io.File(dir, "t"), seal, unseal)
        val jobs = app.personal.banglaai.jobs.JobStore(java.io.File(dir, "j"), seal, unseal)
        for (p in 1..6) src.put("d", app.personal.banglaai.doc.PageText(p, "Page $p text.", null))
        val job = app.personal.banglaai.jobs.JobState("lj", "d", "translate", 6)
        var calls = 0
        val st = app.personal.banglaai.ai.TranslationJob(job, src, dst, jobs, { calls++; if (calls == 3) AppLock.lock(k); "BN:$it" }).run()
        assertEquals(app.personal.banglaai.jobs.JobStatus.PAUSED, st)
        assertEquals(0, job.count(app.personal.banglaai.jobs.PageStatus.FAILED))
        assertTrue(job.count(app.personal.banglaai.jobs.PageStatus.DONE) in 2..3)
        // after unlocking again the same job can be resumed from its checkpoint and finishes
        assertTrue(k.unlockWithPin("123456".toCharArray()))
        val st2 = app.personal.banglaai.ai.TranslationJob(job, src, dst, jobs, { "BN:$it" }).run()
        assertEquals(app.personal.banglaai.jobs.JobStatus.DONE, st2); assertEquals(6, job.count(app.personal.banglaai.jobs.PageStatus.DONE))
    }

    // ---- upgrade from an older install (Keystore-only keyset) ----

    @Test fun upgradeKeepsPinKeysAndOldFiles() {
        com.google.crypto.tink.streamingaead.StreamingAeadConfig.register()
        val oldHandle = com.google.crypto.tink.KeysetHandle.generateNew(com.google.crypto.tink.KeyTemplates.get("AES256_GCM_HKDF_4KB"))
        val oldAead = oldHandle.getPrimitive(com.google.crypto.tink.StreamingAead::class.java)
        val oldFile = Vault.sealWith(oldAead, "পুরনো গোপন ফাইল".toByteArray(), aad)          // written by the OLD version
        val pins = PinStore(ctx); pins.set("123456".toCharArray())                       // PIN set by the OLD version
        // new version starts: PIN still verifies, first unlock migrates the old key
        assertEquals(PinStore.Result.Ok, pins.verify("123456".toCharArray()))
        val k = KeyVault(ctx, pepper, 1000, legacyLoader = { oldHandle })
        assertTrue(k.unlockWithPin("123456".toCharArray()))
        assertArrayEquals("পুরনো গোপন ফাইল".toByteArray(), Vault.unsealWith(k.streamingAead(), oldFile, aad))
        // a later run (new process) opens with the PIN alone; the legacy loader is no longer needed
        val again = KeyVault(ctx, pepper, 1000, legacyLoader = { error("legacy must not be consulted again") })
        assertFalse(again.unlockWithPin("000000".toCharArray()))
        assertTrue(again.unlockWithPin("123456".toCharArray()))
        assertArrayEquals("পুরনো গোপন ফাইল".toByteArray(), Vault.unsealWith(again.streamingAead(), oldFile, aad))
        // and new files written now are readable after another restart
        val fresh = Vault.sealWith(again.streamingAead(), "নতুন".toByteArray(), aad)
        val third = KeyVault(ctx, pepper, 1000); assertTrue(third.unlockWithPin("123456".toCharArray()))
        assertArrayEquals("নতুন".toByteArray(), Vault.unsealWith(third.streamingAead(), fresh, aad))
    }

    @Test fun keystoreLayerFailureFallsBackToPinOnlyInsteadOfLockout() {
        val broken = object : Pepper { override fun wrap(b: ByteArray): ByteArray = error("keystore refused"); override fun unwrap(b: ByteArray): ByteArray = error("keystore refused") }
        val k = KeyVault(ctx, broken, 1000); assertTrue(k.unlockWithPin("123456".toCharArray()))
        val blob = Vault.sealWith(k.streamingAead(), "a".toByteArray(), aad)
        val k2 = KeyVault(ctx, broken, 1000); assertTrue(k2.unlockWithPin("123456".toCharArray()))
        assertArrayEquals("a".toByteArray(), Vault.unsealWith(k2.streamingAead(), blob, aad))
    }
}
