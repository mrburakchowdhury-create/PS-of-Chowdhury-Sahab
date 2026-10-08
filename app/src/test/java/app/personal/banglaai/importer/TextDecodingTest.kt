package app.personal.banglaai.importer

import org.junit.Assert.*
import org.junit.Test

class TextDecodingTest {
    @Test fun utf8Bengali() { val d = TextDecoding.decode("আমি বাংলায় গান গাই".toByteArray()); assertFalse(d.guessed); assertEquals("আমি বাংলায় গান গাই", d.text) }
    @Test fun bomIsStripped() { val d = TextDecoding.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "abc".toByteArray()); assertEquals("abc", d.text) }
    @Test fun utf16WithBom() { val d = TextDecoding.decode(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "hi".toByteArray(Charsets.UTF_16LE)); assertEquals("hi", d.text) }
    @Test fun arabicInWindows1256IsDetectedAsGuess() {
        val d = TextDecoding.decode("السلام عليكم".toByteArray(charset("windows-1256")))
        assertTrue(d.guessed); assertEquals("windows-1256", d.charset); assertEquals("السلام عليكم", d.text)
    }
    @Test fun latinAccentsInWindows1252() {
        val d = TextDecoding.decode("café déjà vu".toByteArray(charset("windows-1252")))
        assertTrue(d.guessed); assertEquals("windows-1252", d.charset); assertEquals("café déjà vu", d.text)
    }
}
