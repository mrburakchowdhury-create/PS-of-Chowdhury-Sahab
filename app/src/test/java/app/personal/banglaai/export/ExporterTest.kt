package app.personal.banglaai.export

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class ExporterTest {
    private val pages = listOf(
        ExportPage(1, "i", "العلم نور", "জ্ঞান আলো", false),
        ExportPage(2, null, "page <2> & \u0001more", null, true),
        ExportPage(3, null, null, "বাংলা তিন", false),
    )
    private val opt = ExportOptions("বই: টেস্ট & \"নমুনা\"", includeOriginal = true, originalIsRtl = true)

    @Test fun txtReportsMissingTranslationsAndOcrFlag() {
        val t = Exporter.txt(pages, opt)
        assertTrue(t.contains("(ছাপা পৃষ্ঠা i)")); assertTrue(t.contains("(অনুবাদ হয়নি)"))
        assertTrue(t.contains("OCR, ভুল থাকতে পারে")); assertTrue(t.contains("মোট 3 পৃষ্ঠার মধ্যে অনূদিত 2। অনূদিত নয়: 2।"))
    }

    @Test fun docxIsValidZipWithWellFormedUnicodeXmlAndRtl() {
        val bo = ByteArrayOutputStream(); Exporter.docx(pages, opt, bo)
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bo.toByteArray())).use { z -> while (true) { val e = z.nextEntry ?: break; entries[e.name] = z.readBytes() } }
        assertTrue(entries.keys.containsAll(listOf("[Content_Types].xml", "_rels/.rels", "word/document.xml")))
        entries.filterKeys { it.endsWith(".xml") || it.endsWith(".rels") }.values.forEach { // every part parses as XML
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(it)) }
        val doc = String(entries.getValue("word/document.xml"), Charsets.UTF_8)
        assertTrue(doc.contains("জ্ঞান আলো")); assertTrue(doc.contains("العلم نور"))
        assertTrue(doc.contains("<w:bidi/>")); assertTrue(doc.contains("<w:rtl/>"))      // Arabic paragraph direction
        assertTrue(doc.contains("page &lt;2&gt; &amp; more"))                            // escaped; control char stripped
        assertTrue(doc.contains("অনূদিত নয়: 2"))
    }

    @Test fun missingListIsExact() = assertEquals(listOf(2), Exporter.missing(pages))
}
