package app.personal.banglaai.library

import app.personal.banglaai.ai.Prompts
import app.personal.banglaai.ai.QueryExpansion
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Not an assertion test: writes the EXACT prompts the app would send (retrieval + Prompts builders) to build/eval/library_prompts.json so tools/eval_library.py
 * can run them through the real local model on the desktop and the answers can be read by a person. Model answers are never asserted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryEvalExportTest {
    private fun index(id: String) = Library.bundled.first { it.id == id }.let { b -> b to Library.Index(Library.chunk(b.title.substringBefore(" ("), File("src/main/assets/${b.file}").readText())) }

    @Test fun exportPrompts() {
        val out = JSONArray()
        val all = Library.bundled.flatMap { b -> Library.chunk(b.title.substringBefore(" ("), File("src/main/assets/${b.file}").readText()) }
        val idx = Library.Index(all)
        for (q in listOf("ভালোবাসা পাওয়ার চেয়ে ভয় পাওয়া কি ভালো?", "গুপ্তচর কেন গুরুত্বপূর্ণ?", "কূটনীতিক বা দূতের কী কী গুণ থাকা উচিত?", "জনতা কীভাবে প্রভাবিত হয়?", "রাজার কি দয়ালু হওয়া উচিত নাকি নিষ্ঠুর?")) {
            val found = idx.search(QueryExpansion.expand(q, BnEnQuery.englishTerms(q)), 6)
            out.put(JSONObject().put("kind", "qa").put("q", q).put("system", Prompts.LIB_SYSTEM).put("prompt", Prompts.libraryAnswer(q, found.map { "${it.book} — ${it.label}" to it.text.take(1400) })).put("sources", JSONArray(found.map { "${it.book} — ${it.label}" })))
        }
        val (_, prince) = index("prince")
        val page = prince.passages.first { it.text.replace(Regex("\\s+"), " ").contains("feared than loved") }
        out.put(JSONObject().put("kind", "translate").put("system", Prompts.TRANSLATE_SYSTEM).put("prompt", Prompts.translate(page.text)).put("sources", JSONArray(listOf(page.label))))
        out.put(JSONObject().put("kind", "word").put("system", Prompts.WORD_SYSTEM).put("prompt", Prompts.wordMeaning("virtue", "A prince must not mind incurring the charge of cruelty for the purpose of keeping his subjects united; his virtue lies in prudence.")).put("sources", JSONArray()))
        File("build/eval").apply { mkdirs() }.resolve("library_prompts.json").writeText(out.toString(1))
    }
}
