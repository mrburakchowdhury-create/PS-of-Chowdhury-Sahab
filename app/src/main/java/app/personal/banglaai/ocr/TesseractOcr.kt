package app.personal.banglaai.ocr

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.security.MessageDigest

data class OcrPage(val text: String, val words: List<OcrWord>, val meanConf: Float)

/**
 * Local OCR (Tesseract 5 via Tesseract4Android, Apache-2.0). Language data (tessdata_fast, Apache-2.0) is bundled in the APK
 * and copied once to no-backup storage after its SHA-256 is verified against the values recorded in docs/OCR_EVAL.md.
 * No network. Status: compiles; Tesseract itself verified on desktop with the same traineddata; on-phone run is device-test pending.
 */
class TesseractOcr(private val context: Context) : AutoCloseable {
    private var api: TessBaseAPI? = null
    private var langs = ""
    private val root = File(context.noBackupFilesDir, "tess")

    enum class Lang(val code: String, val label: String, val size: Long, val sha256: String) {
        ENG("eng", "English / কোড", 4_113_088, "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2"),
        BEN("ben", "বাংলা", 855_841, "31163084c279aaebd376216f0c3d5c17ad4b5fee8db49dae79c20000b5de5964"),
        ARA("ara", "العربية", 1_432_056, "e3206d3dc87fd50c24a0fb9f01838615911d25168f4e64415244b67d2bb3e729"),
        URD("urd", "اردو", 1_398_718, "62e8250ce2a994106e313a82e26a516a39e2cf159d0ce3c5b5008387fd0d555f"),
        HIN("hin", "हिन्दी", 1_122_751, "4c73ffc59d497c186b19d1e90f5d721d678ea6b2e277b719bee4e2af12271825"),
    }

    companion object {
        /** Languages whose bundled data file is missing from this APK (empty = all present). Checked by the screens before starting OCR. */
        fun missingPacks(context: Context): List<Lang> {
            val have = runCatching { context.assets.list("tessdata")?.toSet() }.getOrNull().orEmpty()
            return Lang.entries.filter { "${it.code}.traineddata" !in have }
        }
    }

    private fun ensureData(l: Lang) {
        val dir = File(root, "tessdata").apply { mkdirs() }; val f = File(dir, "${l.code}.traineddata")
        if (f.exists() && f.length() == l.size) return   // size AND (when copied) SHA-256 were checked at copy time; a wrong-size leftover is re-copied below
        val tmp = File(dir, "${l.code}.tmp"); val md = MessageDigest.getInstance("SHA-256")
        try {
            context.assets.open("tessdata/${l.code}.traineddata").use { i -> tmp.outputStream().use { o ->
                val b = ByteArray(1 shl 16); while (true) { val n = i.read(b); if (n < 0) break; o.write(b, 0, n); md.update(b, 0, n) } } }
        } catch (e: java.io.IOException) {
            tmp.delete(); error("${l.label} ভাষার OCR ডেটা অ্যাপে পাওয়া যায়নি — অ্যাপের এই সংস্করণ ঠিকমতো তৈরি হয়নি। সঠিক সংস্করণ ইনস্টল করুন; ততক্ষণ এই ভাষায় OCR চলবে না।")
        }
        val hex = md.digest().joinToString("") { "%02x".format(it) }
        if (!hex.equals(l.sha256, true)) { tmp.delete(); error("OCR ডেটা (${l.code}) নষ্ট — checksum মেলেনি") }
        check(tmp.renameTo(f))
    }

    /** psm: 6 = single uniform block (good for code/screenshots), 3 = automatic page layout (books). */
    fun recognize(bitmap: Bitmap, languages: List<Lang>, psm: Int = TessBaseAPI.PageSegMode.PSM_AUTO): OcrPage {
        require(languages.isNotEmpty())
        languages.forEach(::ensureData)
        val spec = languages.joinToString("+") { it.code }
        if (api == null || langs != spec) {
            api?.recycle(); api = null
            val t = TessBaseAPI()
            check(t.init(root.path, spec)) { "OCR চালু করা যায়নি" }
            api = t; langs = spec
        }
        val t = api!!
        t.setPageSegMode(psm)
        t.setVariable("preserve_interword_spaces", "1")
        t.setImage(bitmap)
        val text = t.getUTF8Text().orEmpty()
        val words = ArrayList<OcrWord>(); var line = 0
        t.resultIterator?.let { it0 ->
            it0.begin()
            do {
                if (it0.isAtBeginningOf(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)) line++
                val w = it0.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD).orEmpty()
                if (w.isNotBlank()) {
                    val r = it0.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                    words += OcrWord(w.trim(), r.left, r.width(), it0.confidence(TessBaseAPI.PageIteratorLevel.RIL_WORD), line)
                }
            } while (it0.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
            it0.delete()
        }
        return OcrPage(text.trim(), words, if (words.isEmpty()) 0f else words.map { it.conf }.average().toFloat())
    }

    fun stop() { api?.stop() }
    override fun close() { api?.recycle(); api = null }
}
