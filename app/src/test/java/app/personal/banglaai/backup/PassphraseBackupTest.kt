package app.personal.banglaai.backup

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class PassphraseBackupTest {
    private val iters = 100_000   // fast iterations for tests; production default is 600k
    private fun enc(data: ByteArray, pass: String): ByteArray {
        val bo = ByteArrayOutputStream(); PassphraseBackup.encryptTo(bo, pass.toCharArray(), iters).use { o -> o.write(data) }; return bo.toByteArray()
    }
    private fun dec(blob: ByteArray, pass: String) = PassphraseBackup.decryptFrom(ByteArrayInputStream(blob), pass.toCharArray()).use { s -> s.readBytes() }

    @Test fun roundTripIncludingMultiSegmentData() {
        val data = ByteArray(3_500_000) { (it * 31).toByte() } + "বাংলা ✓".toByteArray()
        assertArrayEquals(data, dec(enc(data, "correct horse battery"), "correct horse battery"))
    }
    @Test fun wrongPassphraseFailsAndDoesNotReturnData() {
        val blob = enc("secret".toByteArray(), "correct horse battery")
        assertThrows(Exception::class.java) { dec(blob, "wrong passphrase!!") }
    }
    @Test fun anyTamperingIsDetected() {
        val blob = enc(ByteArray(5000) { 7 }, "correct horse battery")
        for (pos in listOf(5, 30, blob.size / 2, blob.size - 1)) {   // iteration field, ciphertext start, middle, tag
            val bad = blob.clone().also { b -> b[pos] = (b[pos] + 1).toByte() }
            assertThrows("byte $pos", Exception::class.java) { dec(bad, "correct horse battery") }
        }
    }
    @Test fun plaintextIsNotVisibleInFile() = assertFalse(String(enc("TOPSECRETWORD".repeat(50).toByteArray(), "correct horse battery"), Charsets.ISO_8859_1).contains("TOPSECRET"))
    @Test fun sameDataTwiceGivesDifferentFiles() = assertFalse(enc("x".toByteArray(), "correct horse battery").contentEquals(enc("x".toByteArray(), "correct horse battery")))
    @Test fun shortPassphraseRejected() { assertThrows(IllegalArgumentException::class.java) { enc("x".toByteArray(), "short") } }
    @Test fun nonBackupFileRejectedClearly() {
        val e = assertThrows(PassphraseBackup.BadBackup::class.java) { dec("this is not a backup file at all".toByteArray(), "correct horse battery") }
        assertTrue(e.message!!.contains("ব্যাকআপ"))
    }
}
