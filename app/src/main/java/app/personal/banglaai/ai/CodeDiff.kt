package app.personal.banglaai.ai

enum class DiffType { SAME, ADDED, REMOVED }
data class DiffLine(val type: DiffType, val text: String, val oldNo: Int?, val newNo: Int?)

/**
 * Line diff computed by the app (LCS), never trusted from the model, so the shown diff always matches the user's real source.
 * The model only proposes replacement code; applying it is always a manual user action (originals are never overwritten).
 */
object CodeDiff {
    fun diff(oldText: String, newText: String): List<DiffLine> {
        val a = oldText.lines(); val b = newText.lines()
        val dp = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.size - 1 downTo 0) for (j in b.size - 1 downTo 0)
            dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        val out = ArrayList<DiffLine>(); var i = 0; var j = 0
        while (i < a.size && j < b.size) when {
            a[i] == b[j] -> { out += DiffLine(DiffType.SAME, a[i], i + 1, j + 1); i++; j++ }
            dp[i + 1][j] >= dp[i][j + 1] -> { out += DiffLine(DiffType.REMOVED, a[i], i + 1, null); i++ }
            else -> { out += DiffLine(DiffType.ADDED, b[j], null, j + 1); j++ }
        }
        while (i < a.size) { out += DiffLine(DiffType.REMOVED, a[i], i + 1, null); i++ }
        while (j < b.size) { out += DiffLine(DiffType.ADDED, b[j], null, j + 1); j++ }
        return out
    }

    fun changed(d: List<DiffLine>) = d.any { it.type != DiffType.SAME }

    /** Unified-style text for display/copy. */
    fun render(d: List<DiffLine>) = d.joinToString("\n") { (if (it.type == DiffType.ADDED) "+ " else if (it.type == DiffType.REMOVED) "- " else "  ") + it.text }

    /** Last fenced ``` block in a model answer (the proposed full replacement), or null. Language tag on the fence is dropped. */
    fun lastFencedBlock(answer: String): String? =
        Regex("```[a-zA-Z0-9_+#.-]*\\n(.*?)```", RegexOption.DOT_MATCHES_ALL).findAll(answer).lastOrNull()?.groupValues?.get(1)?.trimEnd('\n')
}
