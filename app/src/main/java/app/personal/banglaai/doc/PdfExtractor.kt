package app.personal.banglaai.doc

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.InputStream

/** Text of one physical PDF page. [printedLabel] is the page label printed in the book, when the PDF defines one. */
data class PageText(val page: Int, val text: String, val printedLabel: String?, val failed: Boolean = false, val ocr: Boolean = false)

/**
 * Streams text out of a PDF one page at a time (never the whole book in RAM).
 * The PDF is opened with a temp-file-backed buffer ([tmpDir]); callers must delete [tmpDir] contents afterwards
 * (see [PdfExtractor.use]). A page with no extractable text returns an empty string: it is NOT guessed and is a
 * candidate for OCR by the caller. A page that throws is reported as failed, not skipped silently.
 */
class PdfExtractor private constructor(private val doc: PDDocument) : AutoCloseable {
    val pageCount: Int get() = doc.numberOfPages
    val isEncrypted: Boolean get() = doc.isEncrypted

    fun page(n: Int): PageText {
        require(n in 1..pageCount) { "page $n out of range 1..$pageCount" }
        val label = runCatching { doc.documentCatalog.pageLabels?.labelsByPageIndices?.getOrNull(n - 1) }.getOrNull()
        return try {
            val stripper = PDFTextStripper().apply { startPage = n; endPage = n; sortByPosition = true }
            PageText(n, stripper.getText(doc).trim(), label)
        } catch (e: Throwable) {
            PageText(n, "", label, failed = true)
        }
    }

    /** Renders a page to a bitmap for OCR (in memory only; nothing is written to disk). */
    fun renderPage(n: Int, dpi: Float = 200f): android.graphics.Bitmap {
        require(n in 1..pageCount)
        return com.tom_roush.pdfbox.rendering.PDFRenderer(doc).renderImageWithDPI(n - 1, dpi, com.tom_roush.pdfbox.rendering.ImageType.GRAY)
    }

    override fun close() = doc.close()

    sealed interface OpenResult {
        data class Ok(val extractor: PdfExtractor) : OpenResult
        data object PasswordProtected : OpenResult
        data class Malformed(val reason: String) : OpenResult
    }

    companion object {
        fun init(context: Context) = PDFBoxResourceLoader.init(context)

        /** Opens a PDF from a stream. Scripts, JavaScript actions, embedded files and links are never executed. */
        fun open(input: InputStream, tmpDir: File): OpenResult {
            tmpDir.mkdirs()
            return try {
                val d = PDDocument.load(input, MemoryUsageSetting.setupTempFileOnly().setTempDir(tmpDir))
                if (d.isEncrypted) { d.close(); OpenResult.PasswordProtected } else OpenResult.Ok(PdfExtractor(d))
            } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
                OpenResult.PasswordProtected
            } catch (e: Throwable) {
                OpenResult.Malformed(e.javaClass.simpleName)
            }
        }
    }
}
