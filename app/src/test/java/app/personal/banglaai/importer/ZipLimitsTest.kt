package app.personal.banglaai.importer

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** The zip guard must count EVERY physical entry and EVERY expanded byte (also those only read to be skipped). Pure JVM; not an Android runtime test. */
class ZipLimitsTest {
    private fun zip(block: (ZipOutputStream) -> Unit) = ByteArrayOutputStream().also { ZipOutputStream(it).use(block) }.toByteArray()
    private fun read(b: ByteArray, maxEntries: Int = 2000, maxEntry: Long = 64L shl 20, maxTotal: Long = 256L shl 20, maxMillis: Long = 30_000, cancelled: () -> Boolean = { false }) =
        OfficeImporter.readZip(ByteArrayInputStream(b), maxEntries, maxEntry, maxTotal, maxMillis, cancelled)
    private fun refused(msg: String, f: () -> Unit) { try { f(); fail("must be refused: $msg") } catch (e: ImportRefused) { assertTrue(e.message!!.isNotBlank()) } }

    @Test fun reviewReproduction_2102SkippedEntriesAreRefused() {
        // before the fix only KEPT entries were counted, so 2,102 skipped (.bin) entries + one real part passed
        val z = zip { o -> for (i in 0 until 2102) { o.putNextEntry(ZipEntry("junk/$i.bin")); o.write(byteArrayOf(1)); o.closeEntry() }; o.putNextEntry(ZipEntry("word/document.xml")); o.write("<a/>".toByteArray()); o.closeEntry() }
        refused("2102 entries") { read(z) }
    }

    @Test fun directoriesCountToo() {
        val z = zip { o -> for (i in 0 until 2100) { o.putNextEntry(ZipEntry("d$i/")); o.closeEntry() }; o.putNextEntry(ZipEntry("a.xml")); o.write("<a/>".toByteArray()); o.closeEntry() }
        refused("directories") { read(z) }
    }

    @Test fun exactlyAtTheLimitIsAccepted() {
        val z = zip { o -> for (i in 0 until 9) { o.putNextEntry(ZipEntry("p$i.bin")); o.write(1); o.closeEntry() }; o.putNextEntry(ZipEntry("a.xml")); o.write("<a/>".toByteArray()); o.closeEntry() }
        assertEquals(setOf("a.xml"), read(z, maxEntries = 10).keys)
        refused("one over") { read(z, maxEntries = 9) }
    }

    @Test fun bytesOfSkippedEntriesCountTowardTheTotalBudget() {
        // a highly compressed ~6 MB skipped entry must not be drained "for free"
        val z = zip { o -> o.putNextEntry(ZipEntry("media/big.bin")); o.write(ByteArray(6 shl 20)); o.closeEntry(); o.putNextEntry(ZipEntry("a.xml")); o.write("<a/>".toByteArray()); o.closeEntry() }
        assertTrue("compressed form is tiny", z.size < 100_000)
        refused("skipped bytes") { read(z, maxTotal = 1L shl 20) }
        assertEquals(setOf("a.xml"), read(z, maxTotal = 8L shl 20).keys)
    }

    @Test fun perEntryLimitAppliesToSkippedEntriesToo() {
        val z = zip { o -> o.putNextEntry(ZipEntry("x.bin")); o.write(ByteArray(3 shl 20)); o.closeEntry(); o.putNextEntry(ZipEntry("a.xml")); o.write("<a/>".toByteArray()); o.closeEntry() }
        refused("per entry") { read(z, maxEntry = 1L shl 20) }
    }

    @Test fun duplicateNamesAreRefused() {
        val z = zip { o -> o.putNextEntry(ZipEntry("a.xml")); o.write("<a/>".toByteArray()); o.closeEntry(); o.putNextEntry(ZipEntry("b.xml")); o.write("<b/>".toByteArray()); o.closeEntry() }
        // rename b.xml -> a.xml in local headers AND central directory (same length, CRC unaffected): a hostile file with two entries of one name
        val s = String(z, Charsets.ISO_8859_1).replace("b.xml", "a.xml"); val dup = s.toByteArray(Charsets.ISO_8859_1)
        refused("duplicate") { read(dup) }
    }

    @Test fun cancelAndTimeoutStopEarly() {
        val z = zip { o -> o.putNextEntry(ZipEntry("x.xml")); o.write(ByteArray(4 shl 20)); o.closeEntry() }
        refused("cancel") { read(z, cancelled = { true }) }
        refused("timeout") { read(z, maxMillis = 0) }
    }
}
