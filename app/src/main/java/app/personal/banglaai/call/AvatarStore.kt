package app.personal.banglaai.call

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import app.personal.banglaai.storage.Vault
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * The owner's own photo for the call screen. Chosen inside the app (never needs to be sent or committed anywhere), shrunk to a centred square
 * (max 512 px), and stored encrypted with the same vault key as the documents. If none is set the app logo is shown.
 */
class AvatarStore(private val context: Context, private val vault: Vault) {
    private val file = File(context.filesDir, "avatar.enc")
    private val aad = "avatar".toByteArray()

    fun load(): Bitmap? = runCatching {
        if (!file.exists()) return null
        val b = vault.unseal(file.readBytes(), aad); BitmapFactory.decodeByteArray(b, 0, b.size)
    }.getOrNull()

    fun save(uri: Uri): Boolean = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val bmp = squareScaled(bytes, 512) ?: return false
        val out = ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
        file.writeBytes(vault.seal(out.toByteArray(), aad)); true
    }.getOrDefault(false)

    fun clear() { file.delete() }

    companion object {
        /** Decode with sub-sampling (no huge bitmaps in RAM), then centre-crop to a square of [size]. */
        fun squareScaled(bytes: ByteArray, size: Int): Bitmap? {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            if (o.outWidth <= 0 || o.outHeight <= 0) return null
            var s = 1; while (minOf(o.outWidth, o.outHeight) / (s * 2) >= size) s *= 2
            val b = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
            val side = minOf(b.width, b.height)
            val sq = Bitmap.createBitmap(b, (b.width - side) / 2, (b.height - side) / 2, side, side)
            return if (side > size) Bitmap.createScaledBitmap(sq, size, size, true) else sq
        }
    }
}
