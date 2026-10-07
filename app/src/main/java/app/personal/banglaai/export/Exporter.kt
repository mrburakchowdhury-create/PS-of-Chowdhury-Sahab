package app.personal.banglaai.export

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One physical page for export. [translation] null = not translated (reported in the file, never silently skipped). */
data class ExportPage(val page: Int, val printedLabel: String?, val original: String?, val translation: String?, val originalIsOcr: Boolean = false)

data class ExportOptions(val title: String, val includeOriginal: Boolean, val originalIsRtl: Boolean)

/**
 * Pure builders for TXT and DOCX (testable on the JVM). Every export ends with a coverage section listing pages that are
 * missing or untranslated, so a partial translation can never be mistaken for a full one. Page mapping is kept via
 * "পৃষ্ঠা N" headings; exact original pagination is not promised.
 */
object Exporter {
    private val invalidXml = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\uFFFE\\uFFFF]")
    fun xml(s: String) = invalidXml.replace(s, "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun missing(pages: List<ExportPage>) = pages.filter { it.translation.isNullOrBlank() }.map { it.page }

    private fun coverageLine(pages: List<ExportPage>): String {
        val m = missing(pages)
        return "মোট ${pages.size} পৃষ্ঠার মধ্যে অনূদিত ${pages.size - m.size}" + if (m.isEmpty()) "।" else "। অনূদিত নয়: ${m.joinToString(", ")}।"
    }

    fun txt(pages: List<ExportPage>, o: ExportOptions): String = buildString {
        append(o.title).append("\n").append("=".repeat(o.title.length.coerceAtMost(60))).append("\n\n")
        for (p in pages) {
            append("--- পৃষ্ঠা ${p.page}${p.printedLabel?.let { " (মূল অবস্থান: $it)" } ?: ""} ---\n")
            if (o.includeOriginal) append("[মূল${if (p.originalIsOcr) " — OCR, ভুল থাকতে পারে" else ""}]\n").append(p.original ?: "(লেখা নেই)").append("\n\n[বাংলা]\n")
            append(p.translation?.takeIf { it.isNotBlank() } ?: "(অনুবাদ হয়নি)").append("\n\n")
        }
        append("--- কভারেজ ---\n").append(coverageLine(pages)).append("\n")
    }

    private fun para(text: String, rtl: Boolean = false, bold: Boolean = false, size: Int? = null): String {
        val rpr = buildString {
            if (bold) append("<w:b/><w:bCs/>"); if (rtl) append("<w:rtl/>")
            if (size != null) append("<w:sz w:val=\"$size\"/><w:szCs w:val=\"$size\"/>")
        }
        val ppr = if (rtl) "<w:pPr><w:bidi/><w:jc w:val=\"left\"/></w:pPr>" else ""
        return text.split("\n").joinToString("") { line ->
            "<w:p>$ppr<w:r><w:rPr>$rpr</w:rPr><w:t xml:space=\"preserve\">${xml(line)}</w:t></w:r></w:p>"
        }
    }

    fun docx(pages: List<ExportPage>, o: ExportOptions, out: OutputStream) {
        val body = buildString {
            append(para(o.title, bold = true, size = 36))
            for (p in pages) {
                append(para("পৃষ্ঠা ${p.page}${p.printedLabel?.let { " (মূল অবস্থান: $it)" } ?: ""}", bold = true, size = 28))
                if (o.includeOriginal) {
                    append(para("মূল${if (p.originalIsOcr) " — OCR, ভুল থাকতে পারে" else ""}", bold = true))
                    append(para(p.original ?: "(লেখা নেই)", rtl = o.originalIsRtl))
                    append(para("বাংলা অনুবাদ", bold = true))
                }
                append(para(p.translation?.takeIf { it.isNotBlank() } ?: "(অনুবাদ হয়নি)"))
            }
            append(para("কভারেজ", bold = true, size = 28)); append(para(coverageLine(pages)))
        }
        ZipOutputStream(out).use { z ->
            fun put(name: String, c: String) { z.putNextEntry(ZipEntry(name)); z.write(c.toByteArray(Charsets.UTF_8)); z.closeEntry() }
            put("[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>")
            put("_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>")
            put("word/document.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>$body</w:body></w:document>")
        }
    }
}
