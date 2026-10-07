package app.personal.banglaai.storage

import org.junit.Assert.*
import org.junit.Test

class KnownModelsTest {
    private val m = KnownModels.all.first { it.file == "gemma-4-E4B-it.litertlm" }
    @Test fun officialFileIsRecognised() = assertTrue(KnownModels.verdict(m.file, m.size, m.sha256) is KnownModels.Verdict.Official)
    @Test fun sameNameOtherContentIsMismatch() = assertTrue(KnownModels.verdict(m.file, m.size, "00".repeat(32)) is KnownModels.Verdict.Mismatch)
    @Test fun mismatchExplanationsTellCutOffFromDamaged() {
        assertTrue(KnownModels.explainMismatch(m, m.size - 300L * 1024 * 1024, "ab").contains("মাঝপথে থেমেছে"))
        assertTrue(KnownModels.explainMismatch(m, m.size, "ab".repeat(32)).contains("ভেতরের বিট মেলেনি"))
        assertTrue(KnownModels.explainMismatch(m, m.size + 5, "ab").contains("বড়"))
    }
}
