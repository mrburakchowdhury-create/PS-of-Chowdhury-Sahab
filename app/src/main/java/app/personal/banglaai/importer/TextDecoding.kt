package app.personal.banglaai.importer

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Plain-text files come in many encodings. Order: BOM (UTF-8/16) → strict UTF-8 → a LEGACY guess (Windows-1256 if most high bytes look like Arabic/Urdu letters, else Windows-1252).
 * The legacy step is a heuristic, so [Decoded.guessed] is true and the screen says so; the original file is never changed.
 */
object TextDecoding {
    data class Decoded(val text: String, val charset: String, val guessed: Boolean)

    fun decode(b: ByteArray): Decoded {
        if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) return Decoded(String(b, 3, b.size - 3, Charsets.UTF_8), "UTF-8", false)
        if (b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) return Decoded(String(b, 2, b.size - 2, Charsets.UTF_16LE), "UTF-16LE", false)
        if (b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()) return Decoded(String(b, 2, b.size - 2, Charsets.UTF_16BE), "UTF-16BE", false)
        try {
            val t = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString()
            return Decoded(t, "UTF-8", false)
        } catch (_: CharacterCodingException) { }
        val high = b.count { it < 0 }
        val arabicLike = b.count { val v = it.toInt() and 0xFF; v in 0xC1..0xD6 || v in 0xD8..0xDF || v == 0xE1 || v in 0xE3..0xE6 || v == 0xED }
        val name = if (high > 0 && arabicLike * 2 > high) "windows-1256" else "windows-1252"
        return Decoded(String(b, Charset.forName(name)), name, true)
    }
}
