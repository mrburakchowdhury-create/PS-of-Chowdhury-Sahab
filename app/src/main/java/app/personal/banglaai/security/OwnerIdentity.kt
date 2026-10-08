package app.personal.banglaai.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * §13.2 — owner identity and offline activation.
 *  • The constants below are the app's fixed owner profile. Nothing in settings, chat, voice commands, imported documents or restore writes to them.
 *  • Activation = an issuer-signed token that binds OWNER_ID + this package + this phone's non-exportable Keystore key. The APK carries only the issuer's
 *    PUBLIC verification key (assets/issuer_public_key.b64, X.509 EC P-256, base64). No private key lives in the app or the repository.
 *  • Activation NEVER deletes or locks data: a missing/invalid token only changes the status shown in About (and is meant for private operations to refuse).
 *    Honest limit: local checks and a name in the APK cannot stop a determined reverse-engineer; this raises the bar, it is not DRM.
 */
object OwnerIdentity {
    const val OWNER_NAME_BN = "মিস্টার ফাতেহি চৌধুরী নাইম স্যার"
    const val OWNER_ID = "PS-OWNER-FCN-0001"      // permanent project/owner ID (a label, not a secret or a key)
    const val PACKAGE = "app.personal.banglaai"
    private const val KEY_ALIAS = "ps_owner_device_key"
    private const val PREFS = "owner_activation"; private const val P_TOKEN = "token"

    enum class Status { NotConfigured, NotActivated, Active, Invalid }
    data class Result(val status: Status, val detail: String)

    /** What the owner sends to the issuer: owner ID, package and the phone's public key (no secret inside). */
    fun deviceRequest(devicePublicKeyB64: String) = "PSREQ1|$OWNER_ID|$PACKAGE|$devicePublicKeyB64"

    /** The exact text the issuer signs. */
    fun signedText(ownerId: String, pkg: String, devicePublicKeyB64: String, issuedAtIso: String) = "PSACT1|$ownerId|$pkg|$devicePublicKeyB64|$issuedAtIso"

    /** token = "PSACT1|ownerId|pkg|devicePub|issuedAt|signatureB64"; [issuerPublicKeyB64] null = this build has no issuer key yet. */
    fun verify(token: String?, issuerPublicKeyB64: String?, devicePublicKeyB64: String): Result {
        if (issuerPublicKeyB64.isNullOrBlank()) return Result(Status.NotConfigured, "এই বিল্ডে issuer-এর public key বসানো নেই — সক্রিয়করণ এখনো চালু হয়নি (আপনার ডেটা প্রভাবিত নয়)")
        if (token.isNullOrBlank()) return Result(Status.NotActivated, "এই ফোন এখনো সক্রিয় করা হয়নি")
        val p = token.trim().split("|")
        if (p.size != 6 || p[0] != "PSACT1") return Result(Status.Invalid, "টোকেনের ফরম্যাট ঠিক নয়")
        if (p[1] != OWNER_ID) return Result(Status.Invalid, "টোকেন অন্য owner ID-র")
        if (p[2] != PACKAGE) return Result(Status.Invalid, "টোকেন অন্য অ্যাপের")
        if (p[3] != devicePublicKeyB64) return Result(Status.Invalid, "টোকেন এই ফোনের জন্য নয়")
        val ok = runCatching {
            val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.decode(issuerPublicKeyB64.trim(), Base64.DEFAULT)))
            Signature.getInstance("SHA256withECDSA").run { initVerify(pub); update(signedText(p[1], p[2], p[3], p[4]).toByteArray(Charsets.UTF_8)); verify(Base64.decode(p[5], Base64.DEFAULT)) }
        }.getOrDefault(false)
        return if (ok) Result(Status.Active, "সক্রিয় (issued ${p[4]})") else Result(Status.Invalid, "স্বাক্ষর মেলেনি")
    }

    // ---- Android part (device key in the Keystore; not exportable) ----
    fun devicePublicKeyB64(): String {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(KEY_ALIAS)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").run {
                initialize(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN).setDigests(KeyProperties.DIGEST_SHA256).build()); generateKeyPair()
            }
        }
        return Base64.encodeToString(ks.getCertificate(KEY_ALIAS).publicKey.encoded, Base64.NO_WRAP)
    }

    fun issuerKey(ctx: Context): String? = runCatching { ctx.assets.open("issuer_public_key.b64").use { it.readBytes().toString(Charsets.UTF_8).trim() } }.getOrNull()
    fun savedToken(ctx: Context): String? = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(P_TOKEN, null)
    fun saveToken(ctx: Context, token: String) { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(P_TOKEN, token.trim()).apply() }
    fun status(ctx: Context): Result = runCatching { verify(savedToken(ctx), issuerKey(ctx), devicePublicKeyB64()) }.getOrElse { Result(Status.Invalid, "যাচাই করা যায়নি: ${it.javaClass.simpleName}") }
}
