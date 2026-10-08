package app.personal.banglaai.security

import android.util.Base64
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator
import java.security.Signature

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OwnerIdentityTest {
    private fun kp() = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun token(issuer: java.security.KeyPair, dev: String, owner: String = OwnerIdentity.OWNER_ID, pkg: String = OwnerIdentity.PACKAGE): String {
        val text = OwnerIdentity.signedText(owner, pkg, dev, "2026-10-08")
        val sig = Signature.getInstance("SHA256withECDSA").run { initSign(issuer.private); update(text.toByteArray()); sign() }
        return "$text|${b64(sig)}"
    }

    @Test fun validTokenForThisDeviceIsActive() {
        val issuer = kp(); val dev = b64(kp().public.encoded)
        assertEquals(OwnerIdentity.Status.Active, OwnerIdentity.verify(token(issuer, dev), b64(issuer.public.encoded), dev).status)
    }

    @Test fun copiedTokenOnAnotherPhoneOrFromAnotherIssuerOrForAnotherOwnerIsInvalid() {
        val issuer = kp(); val dev = b64(kp().public.encoded); val other = b64(kp().public.encoded); val pub = b64(issuer.public.encoded)
        val t = token(issuer, dev)
        assertEquals(OwnerIdentity.Status.Invalid, OwnerIdentity.verify(t, pub, other).status)                         // other phone
        assertEquals(OwnerIdentity.Status.Invalid, OwnerIdentity.verify(token(kp(), dev), pub, dev).status)           // signed by someone else
        assertEquals(OwnerIdentity.Status.Invalid, OwnerIdentity.verify(token(issuer, dev, owner = "SOMEONE-ELSE"), pub, dev).status)
        assertEquals(OwnerIdentity.Status.Invalid, OwnerIdentity.verify(t.replace("2026-10-08", "2030-01-01"), pub, dev).status)   // tampered
    }

    @Test fun missingPiecesNeverLockAnythingAndAreReportedHonestly() {
        val dev = b64(kp().public.encoded)
        assertEquals(OwnerIdentity.Status.NotConfigured, OwnerIdentity.verify(null, null, dev).status)
        assertEquals(OwnerIdentity.Status.NotActivated, OwnerIdentity.verify(null, b64(kp().public.encoded), dev).status)
    }

    @Test fun ownerNameIsExact() = assertEquals("মিস্টার ফাতেহি চৌধুরী নাইম স্যার", OwnerIdentity.OWNER_NAME_BN)
}
