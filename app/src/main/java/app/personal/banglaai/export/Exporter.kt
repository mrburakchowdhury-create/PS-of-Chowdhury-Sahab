package app.personal.banglaai.export

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One physical page for export. [translation] null = not translated (reported in the file, never silently skipped). */
data class ExportPage(val page: Int, val printedLabel: String?, val original: String?, val translation: String?, val originalIsOcr: Boolean = false, val footnotes: List<String> = emptyList())

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

    private class NoteCollector { val notes = ArrayList<String>(); fun add(t: String): Int { notes += t; return notes.size } }

    private fun run(text: String, rtl: Boolean, bold: Boolean, size: Int?): String {
        val rpr = buildString {
            if (bold) append("<w:b/><w:bCs/>"); if (rtl) append("<w:rtl/>")
            if (size != null) append("<w:sz w:val=\"$size\"/><w:szCs w:val=\"$size\"/>")
        }
        return "<w:r><w:rPr>$rpr</w:rPr><w:t xml:space=\"preserve\">${xml(text)}</w:t></w:r>"
    }

    private fun paraXml(text: String, rtl: Boolean, bold: Boolean, size: Int?, noteRefs: List<Int> = emptyList()): String {
        val ppr = if (rtl) "<w:pPr><w:bidi/><w:jc w:val=\"left\"/></w:pPr>" else ""
        return "<w:p>$ppr${run(text, rtl, bold, size)}" + noteRefs.joinToString("") { "<w:r><w:rPr><w:vertAlign w:val=\"superscript\"/></w:rPr><w:footnoteReference w:id=\"$it\"/></w:r>" } + "</w:p>"
    }

    /** Lines that contain tab-separated cells (the importer writes spreadsheet/table rows that way) become a real Word table; other lines become paragraphs. */
    private fun para(text: String, rtl: Boolean = false, bold: Boolean = false, size: Int? = null): String {
        val lines = text.split("\n"); val sb = StringBuilder(); var i = 0
        while (i < lines.size) {
            if (lines[i].contains('\t')) {
                val rows = ArrayList<List<String>>()
                while (i < lines.size && lines[i].contains('\t')) { rows += lines[i].split('\t'); i++ }
                val cols = rows.maxOf { it.size }
                sb.append("<w:tbl><w:tblPr><w:tblStyle w:val=\"TableGrid\"/><w:tblW w:w=\"0\" w:type=\"auto\"/><w:tblBorders>")
                for (b in listOf("top", "left", "bottom", "right", "insideH", "insideV")) sb.append("<w:$b w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"808080\"/>")
                sb.append("</w:tblBorders></w:tblPr><w:tblGrid>").append("<w:gridCol w:w=\"${9000 / cols}\"/>".repeat(cols)).append("</w:tblGrid>")
                for (r in rows) {
                    sb.append("<w:tr>")
                    for (c in 0 until cols) sb.append("<w:tc><w:tcPr><w:tcW w:w=\"${9000 / cols}\" w:type=\"dxa\"/></w:tcPr>").append(paraXml(r.getOrElse(c) { "" }, rtl, bold, size)).append("</w:tc>")
                    sb.append("</w:tr>")
                }
                sb.append("</w:tbl>")
                sb.append("<w:p/>")   // Word needs a paragraph after a table
            } else { sb.append(paraXml(lines[i], rtl, bold, size)); i++ }
        }
        return sb.toString()
    }

    private fun noteXml(tag: String, notes: List<String>): String {
        val ns = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\""
        val sep = "<w:$tag w:type=\"separator\" w:id=\"-1\"><w:p><w:r><w:separator/></w:r></w:p></w:$tag><w:$tag w:type=\"continuationSeparator\" w:id=\"0\"><w:p><w:r><w:continuationSeparator/></w:r></w:p></w:$tag>"
        val body = notes.mapIndexed { i, t -> "<w:$tag w:id=\"${i + 1}\"><w:p><w:r><w:t xml:space=\"preserve\">${xml(t)}</w:t></w:r></w:p></w:$tag>" }.joinToString("")
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:${tag}s $ns>$sep$body</w:${tag}s>"
    }

    fun docx(pages: List<ExportPage>, o: ExportOptions, out: OutputStream) {
        val notes = NoteCollector()
        val body = buildString {
            append(para(o.title, bold = true, size = 36))
            for (p in pages) {
                val refs = (p.footnotes + listOfNotNull("এই পৃষ্ঠার মূল লেখা OCR (ছবি থেকে পড়া) — ভুল থাকতে পারে।".takeIf { p.originalIsOcr && o.includeOriginal })).map { notes.add(it) }
                append(paraXml("পৃষ্ঠা ${p.page}${p.printedLabel?.let { " (মূল অবস্থান: $it)" } ?: ""}", false, true, 28, refs))
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
            put("[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/><Override PartName=\"/word/footnotes.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.footnotes+xml\"/><Override PartName=\"/word/endnotes.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.endnotes+xml\"/></Types>")
            put("_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>")
            put("word/_rels/document.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/footnotes\" Target=\"footnotes.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/endnotes\" Target=\"endnotes.xml\"/></Relationships>")
            put("word/footnotes.xml", noteXml("footnote", notes.notes))
            put("word/endnotes.xml", noteXml("endnote", emptyList()))
            put("word/document.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>$body</w:body></w:document>")
        }
    }
}
