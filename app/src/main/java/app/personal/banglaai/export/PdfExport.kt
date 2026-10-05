package app.personal.banglaai.export

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.OutputStream

/**
 * PDF export using the system text engine (shapes Bengali conjuncts and Arabic/Urdu joining with the phone's fonts).
 * Pagination is NOT the original's. Status: compiles; rendering quality on the phone is device-test pending
 * (it cannot be checked on the build machine because it needs Android's text stack).
 */
object PdfExport {
    fun write(pages: List<ExportPage>, o: ExportOptions, out: OutputStream) {
        val w = 595; val h = 842; val margin = 40
        val doc = PdfDocument()
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; typeface = Typeface.DEFAULT }
        val head = TextPaint(body).apply { textSize = 15f; typeface = Typeface.DEFAULT_BOLD }
        var pageNo = 0; var page: PdfDocument.Page? = null; var y = 0f
        fun newPage() { page?.let { doc.finishPage(it) }; pageNo++; page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create()); y = margin.toFloat() }
        fun draw(text: String, paint: TextPaint, rtl: Boolean = false) {
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, w - 2 * margin)
                .setAlignment(if (rtl) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(if (rtl) android.text.TextDirectionHeuristics.RTL else android.text.TextDirectionHeuristics.FIRSTSTRONG_LTR).build()
            // break long text across pages line by line
            var line = 0
            while (line < layout.lineCount) {
                if (page == null || y + layout.getLineBottom(line) - layout.getLineTop(line) > h - margin) newPage()
                val top = layout.getLineTop(line); var last = line
                while (last + 1 < layout.lineCount && y + layout.getLineBottom(last + 1) - top <= h - margin) last++
                val c = page!!.canvas; c.save(); c.translate(margin.toFloat(), y - top); c.clipRect(0f, top.toFloat(), (w - 2 * margin).toFloat(), layout.getLineBottom(last).toFloat())
                layout.draw(c); c.restore(); y += layout.getLineBottom(last) - top + 4; line = last + 1
            }
        }
        newPage(); draw(o.title, head)
        for (p in pages) {
            draw("পৃষ্ঠা ${p.page}${p.printedLabel?.let { " (ছাপা পৃষ্ঠা $it)" } ?: ""}", head)
            if (o.includeOriginal) { draw("মূল${if (p.originalIsOcr) " — OCR, ভুল থাকতে পারে" else ""}", head); draw(p.original ?: "(লেখা নেই)", body, o.originalIsRtl); draw("বাংলা অনুবাদ", head) }
            draw(p.translation?.takeIf { it.isNotBlank() } ?: "(অনুবাদ হয়নি)", body)
        }
        val m = Exporter.missing(pages)
        draw("কভারেজ: মোট ${pages.size} পৃষ্ঠার মধ্যে অনূদিত ${pages.size - m.size}" + if (m.isEmpty()) "।" else "; অনূদিত নয়: ${m.joinToString(", ")}।", body)
        page?.let { doc.finishPage(it) }
        doc.writeTo(out); doc.close()
    }
}
