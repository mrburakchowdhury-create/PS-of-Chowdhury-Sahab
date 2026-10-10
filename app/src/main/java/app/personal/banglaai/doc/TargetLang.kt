package app.personal.banglaai.doc

/** The language a document is translated INTO (the source language can be anything). One translation per document: changing the target starts a fresh translation. */
enum class TargetLang(val label: String, val promptName: String, val fileTag: String) {
    BN("বাংলা", "বাংলা", "bn"), EN("English", "ইংরেজি (English)", "en"), AR("العربية", "আরবি (Arabic)", "ar");

    /** Text that is already (mostly) in this language needs no translation and is copied as it is. */
    fun isAlready(text: String): Boolean {
        val letters = text.filter { it.isLetter() }; if (letters.length < 20) return false
        val n = when (this) {
            BN -> letters.count { it.code in 0x0980..0x09FF }
            EN -> letters.count { it in 'A'..'Z' || it in 'a'..'z' }
            AR -> letters.count { it.code in 0x0600..0x06FF || it.code in 0x0750..0x077F || it.code in 0xFB50..0xFDFF || it.code in 0xFE70..0xFEFF }
        }
        return n * 10 >= letters.length * 7
    }
}
