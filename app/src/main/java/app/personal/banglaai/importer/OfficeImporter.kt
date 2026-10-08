package app.personal.banglaai.importer

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/** One logical page of a non-PDF document: a slide, a sheet, a chapter file, a block of lines. [title] keeps its original location. */
data class Section(val title: String, val text: String)

class ImportRefused(msg: String) : Exception(msg)

/**
 * Text extraction for DOCX, PPTX, XLSX, EPUB and CSV. Safety rules (all enforced in code and tests):
 *  - nothing is executed: macros (vbaProject.bin), embedded objects, external links and scripts are ignored; formulas are never
 *    evaluated — only the value cached in the file is shown;
 *  - zip entry names are never used as file paths (everything stays in memory);
 *  - limits: ≤ 2000 entries (ALL physical entries, incl. directories/skipped/duplicates; duplicates refused), ≤ 64 MB per entry and ≤ 256 MB total after decompression
    (every expanded byte counts, also bytes read only to be skipped), ≤ 30 s, cancellable (zip-bomb guard); larger -> [ImportRefused];
 *  - XML is read with a pull parser that does not resolve external entities.
 * Empty results are reported as empty, never invented. Layout (tables, footnotes) is flattened to text; this is stated in the UI.
 */
object OfficeImporter {
    private const val MAX_ENTRIES = 2000; private const val MAX_ENTRY = 64L shl 20; private const val MAX_TOTAL = 256L shl 20; private const val MAX_MILLIS = 30_000L
    val supported = setOf("docx", "pptx", "xlsx", "epub", "csv")
    /** Plain-text source files: shown as-is, split by lines so indentation is never altered. Never executed. */
    val codeExtensions = setOf("py", "js", "ts", "jsx", "tsx", "java", "kt", "kts", "c", "h", "cpp", "cc", "hpp", "cs", "go", "rs", "php", "sql", "html", "htm", "css", "sh", "bash", "swift", "rb", "json", "xml", "yml", "yaml", "md", "ini", "toml", "log")
    fun splitCode(text: String, linesPerSection: Int = 120): List<Section> =
        text.lines().chunked(linesPerSection).mapIndexed { i, c -> Section("লাইন ${i * linesPerSection + 1}–${i * linesPerSection + c.size}", c.joinToString("\n")) }

    fun extension(name: String) = name.substringAfterLast('.', "").lowercase()

    fun extract(name: String, input: InputStream, cancelled: () -> Boolean = { false }): List<Section> = when (extension(name)) {
        "docx" -> docx(readZip(input, cancelled = cancelled))
        "pptx" -> pptx(readZip(input, cancelled = cancelled))
        "xlsx" -> xlsx(readZip(input, cancelled = cancelled))
        "epub" -> epub(readZip(input, cancelled = cancelled))
        "csv" -> csv(input.readUpTo(MAX_ENTRY).toString(Charsets.UTF_8))
        else -> throw ImportRefused("এই ফরম্যাট সমর্থিত নয়")
    }

    /**
     * Reads the XML-like parts of an OOXML/EPUB zip into memory. EVERY physical entry counts (directories, skipped binaries, duplicates), EVERY expanded byte
     * counts (also bytes that are read only to be thrown away), and a time limit and a cancel hook stop slow or hostile files early with a plain Bengali message.
     */
    internal fun readZip(
        input: InputStream, maxEntries: Int = MAX_ENTRIES, maxEntry: Long = MAX_ENTRY, maxTotal: Long = MAX_TOTAL,
        maxMillis: Long = MAX_MILLIS, cancelled: () -> Boolean = { false },
    ): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>(); val seen = HashSet<String>(); var total = 0L; var entries = 0
        val deadline = System.nanoTime() + maxMillis * 1_000_000L
        val b = ByteArray(16384)
        ZipInputStream(input).use { z ->
            while (true) {
                if (cancelled()) throw ImportRefused("কাজ বাতিল করা হয়েছে")
                val e = z.nextEntry ?: break
                if (++entries > maxEntries) throw ImportRefused("ফাইলে অস্বাভাবিক বেশি অংশ আছে (সীমা $maxEntries) — প্রত্যাখ্যাত")
                val n = e.name
                if (!seen.add(n)) throw ImportRefused("ফাইলে একই নামের অংশ দুবার আছে — সন্দেহজনক, প্রত্যাখ্যাত")
                if (e.isDirectory) continue
                val keep = n.endsWith(".xml") || n.endsWith(".rels") || n.endsWith(".xhtml") || n.endsWith(".html") || n.endsWith(".htm") || n.endsWith(".opf")   // binaries/macros are not kept
                val buf = if (keep) java.io.ByteArrayOutputStream() else null; var size = 0L
                while (true) {
                    val r = z.read(b); if (r < 0) break
                    size += r; total += r
                    if (size > maxEntry || total > maxTotal) throw ImportRefused("ফাইলটি খুলে অতিরিক্ত বড় হয় (সম্ভাব্য zip-bomb) — প্রত্যাখ্যাত")
                    if (System.nanoTime() > deadline) throw ImportRefused("ফাইল খুলতে অতিরিক্ত সময় লাগছে — প্রত্যাখ্যাত")
                    if (cancelled()) throw ImportRefused("কাজ বাতিল করা হয়েছে")
                    buf?.write(b, 0, r)   // skipped entries are still READ (counted above), never trusted to be small
                }
                if (buf != null) out[n] = buf.toByteArray()
            }
        }
        if (out.isEmpty()) throw ImportRefused("ফাইল নষ্ট বা পাসওয়ার্ড-সুরক্ষিত (পড়া যায়নি)")
        return out
    }

    private fun parser(bytes: ByteArray) = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(ByteArrayInputStream(bytes), "UTF-8")
    }

    /** Collects text of elements named [textTag], inserting a newline at each [paraTag] end. */
    private fun textOf(bytes: ByteArray, textTag: String, paraTag: String, brTag: String? = null): String {
        val p = try { parser(bytes) } catch (e: Exception) { throw ImportRefused("XML পড়া যায়নি") }
        val sb = StringBuilder(); var inText = false
        try {
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> { if (p.name == textTag) inText = true else if (p.name == brTag) sb.append('\n') else if (p.name == "w:tab") sb.append('\t') }
                    XmlPullParser.TEXT -> if (inText) sb.append(p.text)
                    XmlPullParser.END_TAG -> { if (p.name == textTag) inText = false else if (p.name == paraTag) sb.append('\n') }
                }
                ev = p.next()
            }
        } catch (e: Exception) { throw ImportRefused("XML নষ্ট: ${e.javaClass.simpleName}") }
        return sb.toString().trim()
    }

    private fun docx(z: Map<String, ByteArray>): List<Section> {
        val body = z["word/document.xml"] ?: throw ImportRefused("এটি বৈধ DOCX নয় (document.xml নেই)")
        val text = textOf(body, "w:t", "w:p", "w:br")
        return if (text.isBlank()) emptyList() else Chunking.byParagraphs(text, "DOCX").mapIndexed { i, t -> Section("অংশ ${i + 1}", t) }
    }

    private fun pptx(z: Map<String, ByteArray>): List<Section> {
        val slides = z.keys.filter { Regex("ppt/slides/slide\\d+\\.xml").matches(it) }.sortedBy { Regex("\\d+").find(it.substringAfterLast('/'))!!.value.toInt() }
        if (slides.isEmpty()) throw ImportRefused("এটি বৈধ PPTX নয় (স্লাইড নেই)")
        return slides.mapIndexed { i, n -> Section("স্লাইড ${i + 1}", textOf(z.getValue(n), "a:t", "a:p")) }
    }

    private fun xlsx(z: Map<String, ByteArray>): List<Section> {
        val shared = z["xl/sharedStrings.xml"]?.let { sharedStrings(it) } ?: emptyList()
        val sheets = z.keys.filter { Regex("xl/worksheets/sheet\\d+\\.xml").matches(it) }.sortedBy { Regex("\\d+").find(it.substringAfterLast('/'))!!.value.toInt() }
        if (sheets.isEmpty()) throw ImportRefused("এটি বৈধ XLSX নয় (শিট নেই)")
        return sheets.mapIndexed { i, n -> Section("শিট ${i + 1}", sheetText(z.getValue(n), shared)) }
    }

    private fun sharedStrings(b: ByteArray): List<String> {
        val p = parser(b); val out = ArrayList<String>(); var cur: StringBuilder? = null; var inT = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> { if (p.name == "si") cur = StringBuilder() else if (p.name == "t") inT = true }
                XmlPullParser.TEXT -> if (inT) cur?.append(p.text)
                XmlPullParser.END_TAG -> { if (p.name == "t") inT = false else if (p.name == "si") { out += cur.toString(); cur = null } }
            }
            ev = p.next()
        }
        return out
    }

    /** Cell values only (cached results). A formula cell shows its cached value; the formula text is not evaluated or shown as authoritative. */
    private fun sheetText(b: ByteArray, shared: List<String>): String {
        val p = parser(b); val rows = ArrayList<String>(); var row = ArrayList<String>(); var type = ""; var inV = false; var inIs = false; var v = StringBuilder()
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> row = ArrayList()
                    "c" -> { type = p.getAttributeValue(null, "t") ?: ""; v = StringBuilder() }
                    "v" -> inV = true; "is" -> inIs = true
                    "t" -> if (inIs) inV = true
                }
                XmlPullParser.TEXT -> if (inV) v.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v", "t" -> inV = false; "is" -> inIs = false
                    "c" -> row += if (type == "s") shared.getOrElse(v.toString().trim().toIntOrNull() ?: -1) { "" } else v.toString()
                    "row" -> { if (row.any { it.isNotEmpty() }) rows += row.joinToString("\t"); }
                }
            }
            ev = p.next()
        }
        return rows.joinToString("\n")
    }

    private fun epub(z: Map<String, ByteArray>): List<Section> {
        val container = z["META-INF/container.xml"] ?: throw ImportRefused("এটি বৈধ EPUB নয়")
        val opfPath = Regex("full-path=\"([^\"]+)\"").find(String(container, Charsets.UTF_8))?.groupValues?.get(1) ?: throw ImportRefused("EPUB-এর OPF পাওয়া যায়নি")
        val opf = String(z[opfPath] ?: throw ImportRefused("EPUB-এর OPF নেই"), Charsets.UTF_8)
        val dir = opfPath.substringBeforeLast('/', "")
        val manifest = Regex("<item\\b[^>]*>").findAll(opf).associate { m ->
            val t = m.value; (Regex("id=\"([^\"]+)\"").find(t)?.groupValues?.get(1) ?: "") to (Regex("href=\"([^\"]+)\"").find(t)?.groupValues?.get(1) ?: "") }
        val spine = Regex("<itemref\\b[^>]*idref=\"([^\"]+)\"").findAll(opf).map { it.groupValues[1] }.toList()
        val out = ArrayList<Section>()
        for ((i, id) in spine.withIndex()) {
            val href = manifest[id] ?: continue
            val path = (if (dir.isEmpty()) href else "$dir/$href").substringBefore('#')
            val html = z[path]?.toString(Charsets.UTF_8) ?: continue
            val text = htmlToText(html); if (text.isNotBlank()) out += Section("অধ্যায়-ফাইল ${i + 1}", text)
        }
        return out
    }

    internal fun htmlToText(html: String): String =
        html.replace(Regex("(?is)<(script|style)\\b.*?</\\1>"), " ").replace(Regex("(?i)</(p|div|h[1-6]|li|tr|br)\\s*>|<br\\s*/?>"), "\n")
            .replace(Regex("<[^>]+>"), "").replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&")
            .lines().joinToString("\n") { it.trim() }.replace(Regex("\n{3,}"), "\n\n").trim()

    private fun csv(text: String): List<Section> {
        val lines = text.lines().filter { it.isNotBlank() }; if (lines.isEmpty()) return emptyList()
        return lines.chunked(200).mapIndexed { i, c -> Section("সারি ${i * 200 + 1}–${i * 200 + c.size}", c.joinToString("\n")) }
    }
}

object Chunking {
    /** Splits long text into ~3000-char sections on paragraph boundaries without losing text. */
    fun byParagraphs(text: String, @Suppress("UNUSED_PARAMETER") label: String, max: Int = 3000): List<String> =
        app.personal.banglaai.ai.Chunker.split(text, max)
}

/** Reads at most [limit] bytes (API 31-safe replacement for InputStream.readNBytes, which needs API 33). */
internal fun java.io.InputStream.readUpTo(limit: Long): ByteArray {
    val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(16 * 1024); var total = 0L
    while (total < limit) { val n = read(buf, 0, minOf(buf.size.toLong(), limit - total).toInt()); if (n < 0) break; out.write(buf, 0, n); total += n }
    return out.toByteArray()
}
