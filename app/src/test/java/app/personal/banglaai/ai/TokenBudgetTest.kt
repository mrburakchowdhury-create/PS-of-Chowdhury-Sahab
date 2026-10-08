package app.personal.banglaai.ai

import org.junit.Assert.*
import org.junit.Test

class TokenBudgetTest {
    @Test fun bengaliCostsMoreThanLatinPerCharacter() { assertTrue(TokenBudget.estimate("আমি".repeat(100)) > TokenBudget.estimate("abc".repeat(100))) }
    @Test fun headStaysWithinBudget() {
        val t = "বাংলা লেখা ".repeat(2000); val h = TokenBudget.head(t, 500)
        assertTrue(TokenBudget.estimate(h) <= 500); assertTrue(h.length > 100); assertTrue(t.startsWith(h))
    }
    @Test fun shareKeepsShortOnesWholeAndCutsLongOnes() {
        val (out, cut) = TokenBudget.share(listOf("short", "x ".repeat(5000)), 600)
        assertTrue(cut); assertEquals("short", out[0]); assertTrue(TokenBudget.estimate(out.sum()) <= 600)
    }
    @Test fun shareLeavesSmallInputAlone() { val (out, cut) = TokenBudget.share(listOf("a", "b"), 600); assertFalse(cut); assertEquals(listOf("a", "b"), out) }
    @Test fun guardKeepsStartAndQuestionAtEnd() {
        val p = "START " + "মধ্য ".repeat(5000) + " প্রশ্ন: কেন?"
        val g = TokenBudget.guard("sys", p)
        assertTrue(g.startsWith("START")); assertTrue(g.endsWith("কেন?")); assertTrue(TokenBudget.estimate(g) <= TokenBudget.INPUT_TOKENS)
    }
    private fun List<String>.sum() = joinToString("")
}
