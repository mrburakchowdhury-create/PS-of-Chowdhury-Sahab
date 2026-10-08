package app.personal.banglaai.ocr

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** The five OCR language packs MUST be inside the APK sources (an ignore rule once silently left them out of every build). Size + SHA-256 are the values the app verifies at first use. */
class TessdataAssetsTest {
    private val dir = File("src/main/assets/tessdata")
    @Test fun everyLanguagePackIsPresentWithTheExpectedSizeAndHash() {
        for (l in TesseractOcr.Lang.entries) {
            val f = File(dir, "${l.code}.traineddata"); assertTrue("missing ${f.path}", f.isFile)
            assertEquals("size of ${l.code}", l.size, f.length())
            val hex = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
            assertEquals("sha256 of ${l.code}", l.sha256, hex)
        }
        assertTrue("licence text must ship with the data", File(dir, "LICENSE.txt").readText().contains("Apache License"))
        assertEquals(5, TesseractOcr.Lang.entries.size)
    }
}
