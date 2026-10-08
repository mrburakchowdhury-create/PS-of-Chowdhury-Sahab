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
        assertTrue(t.contains("(মূল অবস্থান: i)")); assertTrue(t.contains("(অনুবাদ হয়নি)"))
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

    @Test fun docxHasRealTablesFootnotesAndRelationships() {
        val pg = listOf(ExportPage(1, null, "নাম\tমান\nএক\t১", "অনুবাদ", originalIsOcr = true, footnotes = listOf("সম্পাদকের টীকা")))
        val bo = ByteArrayOutputStream(); Exporter.docx(pg, ExportOptions("T", true, false), bo)
        val parts = HashMap<String, String>(); java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bo.toByteArray())).use { z -> while (true) { val e = z.nextEntry ?: break; parts[e.name] = z.readBytes().toString(Charsets.UTF_8) } }
        val doc = parts["word/document.xml"]!!
        assertTrue(doc.contains("<w:tbl>")); assertEquals(2, Regex("<w:tr>").findAll(doc).count()); assertEquals(4, Regex("<w:tc>").findAll(doc).count())
        assertEquals(2, Regex("<w:footnoteReference").findAll(doc).count())
        assertTrue(parts["word/footnotes.xml"]!!.contains("সম্পাদকের টীকা")); assertTrue(parts["word/footnotes.xml"]!!.contains("OCR"))
        assertTrue(parts.containsKey("word/endnotes.xml"))
        val rels = parts["word/_rels/document.xml.rels"]!!; assertTrue(rels.contains("footnotes.xml")); assertTrue(rels.contains("endnotes.xml"))
        val ct = parts["[Content_Types].xml"]!!; assertTrue(ct.contains("/word/footnotes.xml")); assertTrue(ct.contains("/word/endnotes.xml"))
        for ((n, x) in parts) if (n.endsWith(".xml") || n.endsWith(".rels")) javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(java.io.ByteArrayInputStream(x.toByteArray()))
    }

    @Test fun missingListIsExact() = assertEquals(listOf(2), Exporter.missing(pages))
}

class ExporterLongDocumentTest {
    /** 400 pages of Bengali + Arabic + Urdu text with XML-hostile characters: nothing may be lost or break the files. */
    private val pages = (1..400).map { p ->
        ExportPage(p, if (p % 10 == 0) "ix-$p" else null, "العلم نور والجهل ظلام — صفحة $p & <tag> \u0001 ‫ علم حاصل کرنا $p", "এটি পৃষ্ঠা $p-এর বাংলা অনুবাদ। জ্ঞান অর্জন করা সবার জন্য জরুরি। " + "দীর্ঘ বাক্য ".repeat(40), p % 50 == 0)
    }
    private val opt = ExportOptions("দীর্ঘ বই — العلم", includeOriginal = true, originalIsRtl = true)

    @Test fun txtKeepsEveryPageInOrder() {
        val t = Exporter.txt(pages, opt)
        for (p in listOf(1, 137, 400)) assertTrue(t.contains("পৃষ্ঠা $p")); assertTrue(t.indexOf("পৃষ্ঠা 2") < t.indexOf("পৃষ্ঠা 399"))
        assertTrue(t.contains("মোট 400 পৃষ্ঠার মধ্যে অনূদিত 400।"))
        assertTrue(t.contains("আসল পাঠ") || t.contains("العلم نور"))
    }

    @Test fun docxStaysWellFormedAndHasEveryPage() {
        val bo = ByteArrayOutputStream(); Exporter.docx(pages, opt, bo)
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bo.toByteArray())).use { z -> while (true) { val e = z.nextEntry ?: break; entries[e.name] = z.readBytes() } }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(entries.getValue("word/document.xml")))
        val text = doc.documentElement.textContent
        assertTrue(text.contains("পৃষ্ঠা 400")); assertTrue(text.contains("এটি পৃষ্ঠা 250-এর বাংলা অনুবাদ")); assertTrue(text.contains("صفحة 399"))
        assertFalse("control characters must be stripped", text.contains('\u0001'))
    }
}
