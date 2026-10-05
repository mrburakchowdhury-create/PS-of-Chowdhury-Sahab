package app.personal.banglaai.ai

/** Splits page text into model-sized pieces on paragraph, then sentence boundaries; never drops or reorders text. */
object Chunker {
    fun split(text: String, maxChars: Int = 1500): List<String> {
        require(maxChars >= 50)
        val out = ArrayList<String>(); val cur = StringBuilder()
        fun flush() { if (cur.isNotEmpty()) { out += cur.toString(); cur.setLength(0) } }
        for (para in text.split(Regex("\n\\s*\n"))) {
            if (para.isBlank()) continue
            for (piece in pieces(para.trim(), maxChars)) {
                if (cur.isNotEmpty() && cur.length + piece.length + 2 > maxChars) flush()
                if (cur.isNotEmpty()) cur.append("\n\n")
                cur.append(piece)
            }
        }
        flush(); return out
    }

    private fun pieces(para: String, max: Int): List<String> {
        if (para.length <= max) return listOf(para)
        val res = ArrayList<String>(); val cur = StringBuilder()
        for (s in para.split(Regex("(?<=[.!?।؟۔])\\s+"))) {
            var rest = s
            while (rest.length > max) { if (cur.isNotEmpty()) { res += cur.toString(); cur.setLength(0) }; res += rest.take(max); rest = rest.drop(max) }
            if (cur.isNotEmpty() && cur.length + rest.length + 1 > max) { res += cur.toString(); cur.setLength(0) }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(rest)
        }
        if (cur.isNotEmpty()) res += cur.toString()
        return res
    }
}
