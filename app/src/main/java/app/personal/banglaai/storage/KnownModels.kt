package app.personal.banglaai.storage

/** Official model files whose size and SHA-256 were verified by downloading them (see docs/MODELS.md). */
data class KnownModel(val file: String, val size: Long, val sha256: String, val label: String)

object KnownModels {
    val all = listOf(
        KnownModel("gemma-4-E4B-it.litertlm", 3_659_530_240L, "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0", "Gemma 4 E4B (Apache-2.0)"),
        KnownModel("gemma-4-E2B-it.litertlm", 2_588_147_712L, "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c", "Gemma 4 E2B (Apache-2.0)"),
    )

    sealed interface Verdict {
        data class Official(val model: KnownModel) : Verdict
        data object Pending : Verdict          // no checksum recorded yet (e.g. the copy was interrupted right at the end): must be computed before use
        data object Unknown : Verdict          // not in our list: may be fine, but we cannot vouch for it
        data class Mismatch(val expected: KnownModel) : Verdict   // same name, different content: corrupted or tampered
    }

    fun verdict(file: String, size: Long, sha256: String): Verdict {
        if (sha256.isBlank()) return Verdict.Pending
        all.firstOrNull { it.sha256.equals(sha256, ignoreCase = true) && it.size == size }?.let { return Verdict.Official(it) }
        all.firstOrNull { it.file == file }?.let { return Verdict.Mismatch(it) }
        return Verdict.Unknown
    }

    /** Plain-Bengali reason for a Mismatch, so the owner can tell a cut-off download/copy from a damaged one. */
    fun explainMismatch(m: KnownModel, size: Long, sha256: String): String = when {
        size < m.size -> "ফাইল ছোট: আছে $size বাইট, হওয়া উচিত ${m.size} বাইট (${(m.size - size) / (1 shl 20)} MB কম) — ডাউনলোড বা কপি মাঝপথে থেমেছে। ফাইলটা মুছে আবার নামান/কপি করুন।"
        size > m.size -> "ফাইল বড়: আছে $size বাইট, হওয়া উচিত ${m.size} বাইট — এটি অন্য ফাইল বা ভুল সংস্করণ হতে পারে।"
        else -> "আকার ঠিক (${m.size} বাইট) কিন্তু ভেতরের বিট মেলেনি (checksum ${sha256.take(12)}… ≠ ${m.sha256.take(12)}…) — ডাউনলোড/কপির সময় নষ্ট হয়েছে। ফাইলটা মুছে আবার নামান।"
    }
}
