package app.personal.banglaai.ai

/**
 * Wider search WITHOUT embeddings: the local model lists related words / the same words in the document's language(s); the app appends them to the
 * question and runs the normal keyword (BM25) search. It is still keyword matching — better recall for other wording, not true meaning search.
 */
object QueryExpansion {
    const val SYSTEM = "তুমি একটি অনুসন্ধান-সহায়ক। প্রশ্নের মূল শব্দগুলোর সমার্থক/সম্পর্কিত শব্দ এবং ইংরেজি, আরবি ও বাংলা প্রতিশব্দ দাও। শুধু শব্দের তালিকা, কমা দিয়ে আলাদা; কোনো ব্যাখ্যা বা বাক্য নয়।"
    fun prompt(question: String) = "প্রশ্ন: ${Prompts.sanitize(question).take(300)}\nসর্বোচ্চ ১২টি শব্দ দাও।"

    /** Turns the model's reply into clean single words/short phrases: strips bullets/numbering, de-duplicates, caps the count. */
    fun parse(reply: String, max: Int = 12): List<String> =
        reply.split(',', '،', '\n', ';', '、')
            .map { it.trim().replace(Regex("^[\\-•*\\d.)\\s]+"), "").trim(' ', '"', '\'', '।', '.') }
            .filter { it.length in 2..30 && it.count { c -> c.isWhitespace() } <= 2 }
            .distinct().take(max)

    fun expand(question: String, extra: List<String>) = (listOf(question) + extra).joinToString(" ")
}
