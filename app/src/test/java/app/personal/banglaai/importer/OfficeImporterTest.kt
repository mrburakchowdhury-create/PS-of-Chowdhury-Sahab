package app.personal.banglaai.importer

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Synthetic files built here (no real documents). Word/PowerPoint/Excel themselves were NOT used to open them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfficeImporterTest {
    private fun zip(vararg e: Pair<String, String>): ByteArray {
        val bo = ByteArrayOutputStream(); ZipOutputStream(bo).use { z -> e.forEach { (n, c) -> z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() } }; return bo.toByteArray()
    }
    private fun run(name: String, b: ByteArray) = OfficeImporter.extract(name, ByteArrayInputStream(b))
    private val W = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\""

    @Test fun docxKeepsParagraphsBengaliArabicAndIgnoresMacros() {
        val doc = "<w:document $W><w:body><w:p><w:r><w:t>প্রথম অনুচ্ছেদ</w:t></w:r></w:p><w:p><w:r><w:t>العلم نور</w:t></w:r></w:p></w:body></w:document>"
        val s = run("a.docx", zip("word/document.xml" to doc, "word/vbaProject.bin" to "MACRO-DO-NOT-RUN"))
        val all = s.joinToString("\n") { it.text }
        assertTrue(all.contains("প্রথম অনুচ্ছেদ")); assertTrue(all.contains("العلم نور")); assertFalse(all.contains("MACRO"))
    }

    @Test fun pptxSlidesInNumericOrderNotAlphabetical() {
        fun slide(t: String) = "<p:sld xmlns:a=\"a\" xmlns:p=\"p\"><a:p><a:r><a:t>$t</a:t></a:r></a:p></p:sld>"
        val s = run("a.pptx", zip("ppt/slides/slide10.xml" to slide("দশ"), "ppt/slides/slide2.xml" to slide("দুই"), "ppt/slides/slide1.xml" to slide("এক")))
        assertEquals(listOf("এক", "দুই", "দশ"), s.map { it.text })
    }

    @Test fun xlsxShowsCachedValuesAndNeverEvaluatesFormulas() {
        val ss = "<sst><si><t>নাম</t></si><si><t>মান</t></si></sst>"
        val sheet = "<worksheet><sheetData><row><c t=\"s\"><v>0</v></c><c t=\"s\"><v>1</v></c></row><row><c><v>5</v></c><c><f>HYPERLINK(\"http://x\")</f><v>7</v></c></row></sheetData></worksheet>"
        val s = run("a.xlsx", zip("xl/sharedStrings.xml" to ss, "xl/worksheets/sheet1.xml" to sheet))
        assertEquals("নাম\tমান\n5\t7", s[0].text)
        assertFalse(s[0].text.contains("HYPERLINK"))
    }

    @Test fun epubFollowsSpineOrderAndDropsScripts() {
        val opf = "<package><manifest><item id=\"b\" href=\"b.xhtml\"/><item id=\"a\" href=\"a.xhtml\"/></manifest><spine><itemref idref=\"a\"/><itemref idref=\"b\"/></spine></package>"
        val s = run("a.epub", zip("META-INF/container.xml" to "<container><rootfiles><rootfile full-path=\"OEBPS/c.opf\"/></rootfiles></container>",
            "OEBPS/c.opf" to opf, "OEBPS/a.xhtml" to "<html><body><h1>One</h1><p>Hello &amp; welcome</p><script>alert(1)</script></body></html>", "OEBPS/b.xhtml" to "<p>Two</p>"))
        assertEquals(2, s.size); assertTrue(s[0].text.contains("Hello & welcome")); assertFalse(s[0].text.contains("alert")); assertEquals("Two", s[1].text)
    }

    @Test fun zipBombIsRefused() {
        val big = "<w:document $W>" + "<w:p><w:r><w:t>x</w:t></w:r></w:p>".repeat(10) + "</w:document>"
        val bo = ByteArrayOutputStream(); ZipOutputStream(bo).use { z -> z.putNextEntry(ZipEntry("word/document.xml")); z.write(big.toByteArray())
            val chunk = ByteArray(1 shl 20); repeat(70) { z.write(chunk) }; z.closeEntry() }
        val e = assertThrows(ImportRefused::class.java) { run("a.docx", bo.toByteArray()) }
        assertTrue(e.message!!.contains("zip-bomb"))
    }

    @Test fun garbageAndWrongStructureAreRefusedClearly() {
        assertThrows(ImportRefused::class.java) { run("a.docx", "not a zip".toByteArray()) }
        assertThrows(ImportRefused::class.java) { run("a.docx", zip("other.xml" to "<a/>")) }
        assertThrows(ImportRefused::class.java) { run("a.exe", ByteArray(3)) }
    }

    @Test fun csvIsChunkedByLinesWithoutLoss() {
        val csv = (1..450).joinToString("\n") { "r$it,${it * 2}" }
        val s = run("a.csv", csv.toByteArray()); assertEquals(3, s.size)
        assertEquals(csv, s.joinToString("\n") { it.text })
    }

    @Test fun codeFilesKeepIndentationAndEveryLine() {
        val code = (1..300).joinToString("\n") { if (it % 2 == 0) "    indented line $it" else "line $it" }
        val parts = OfficeImporter.splitCode(code)
        assertEquals(3, parts.size)
        assertEquals(code, parts.joinToString("\n") { it.text })   // lossless, leading spaces intact
        assertTrue(parts[0].text.lines()[1].startsWith("    indented"))
        assertTrue("py" in OfficeImporter.codeExtensions && "exe" !in OfficeImporter.codeExtensions)
    }

    @Test fun readUpToIsBoundedAndComplete() {
        assertEquals(10, ByteArrayInputStream(ByteArray(100)).readUpTo(10).size)
        assertEquals(5, ByteArrayInputStream(ByteArray(5)).readUpTo(10).size)
    }

    @Test fun xxeStyleEntityIsNotResolved() {
        val doc = "<!DOCTYPE d [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><w:document $W><w:p><w:r><w:t>&x;</w:t></w:r></w:p></w:document>"
        val r = runCatching { run("a.docx", zip("word/document.xml" to doc)) }
        val txt = r.getOrNull()?.joinToString { it.text } ?: ""
        assertFalse(txt.contains("root:"))   // never reads local files (either refused or left unresolved)
    }
}
