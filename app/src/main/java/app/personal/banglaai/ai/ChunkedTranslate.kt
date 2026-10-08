package app.personal.banglaai.ai

/**
 * C03: a long spoken/typed transcript is translated WHOLE, in order, in pieces that each fit the model's context — never cut off at the first limit.
 * [ask] translates one piece. [onProgress] gets (done, total). If a piece fails the pieces done so far are kept and the failure is reported,
 * so the screen can say exactly which part is missing instead of presenting a partial text as complete.
 */
object ChunkedTranslate {
    const val SINGLE_LIMIT = 1_500      // UTF-16 units that are sent in one request; longer text is split on paragraph/sentence boundaries
    data class Result(val text: String, val total: Int, val done: Int, val failure: String?) { val complete get() = failure == null && done == total }

    fun run(text: String, onProgress: (Int, Int) -> Unit = { _, _ -> }, ask: (String) -> String): Result {
        val parts = if (text.length <= SINGLE_LIMIT) listOf(text) else Chunker.split(text, SINGLE_LIMIT)
        val out = ArrayList<String>(); onProgress(0, parts.size)
        for ((i, p) in parts.withIndex()) {
            val r = try { ask(p).trim() } catch (e: java.util.concurrent.CancellationException) { throw e } catch (e: Exception) { return Result(out.joinToString("\n\n"), parts.size, out.size, "অংশ ${i + 1}/${parts.size}: ${e.message ?: e.javaClass.simpleName}") }
            if (r.isBlank()) return Result(out.joinToString("\n\n"), parts.size, out.size, "অংশ ${i + 1}/${parts.size}: অনুবাদ আসেনি")
            out += r; onProgress(i + 1, parts.size)
        }
        return Result(out.joinToString("\n\n"), parts.size, parts.size, null)
    }
}
