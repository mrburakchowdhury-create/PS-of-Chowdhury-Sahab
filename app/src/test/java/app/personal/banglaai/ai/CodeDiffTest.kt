package app.personal.banglaai.ai

import org.junit.Assert.*
import org.junit.Test

class CodeDiffTest {
    private val old = "def avg(xs):\n    total = 0\n    for x in xs:\n        total += x\n    return total / len(xs)"
    private val new = "def avg(xs):\n    if not xs:\n        return 0\n    total = 0\n    for x in xs:\n        total += x\n    return total / len(xs)"

    @Test fun diffShowsOnlyTheInsertionWithRealLineNumbers() {
        val d = CodeDiff.diff(old, new)
        val added = d.filter { it.type == DiffType.ADDED }
        assertEquals(listOf("    if not xs:", "        return 0"), added.map { it.text })
        assertEquals(listOf(2, 3), added.map { it.newNo })
        assertTrue(d.none { it.type == DiffType.REMOVED })
        assertEquals(5, d.count { it.type == DiffType.SAME })
    }

    @Test fun replayingDiffReproducesBothSides() {
        val d = CodeDiff.diff(old, new)
        assertEquals(old, d.filter { it.type != DiffType.ADDED }.joinToString("\n") { it.text })
        assertEquals(new, d.filter { it.type != DiffType.REMOVED }.joinToString("\n") { it.text })
    }

    @Test fun identicalCodeHasNoChange() = assertFalse(CodeDiff.changed(CodeDiff.diff(old, old)))

    @Test fun fenceExtractionTakesLastBlockAndKeepsIndentation() {
        val ans = "সমস্যা...\n```python\nold()\n```\nসংশোধিত:\n```python\ndef f():\n    return 1\n```\nশেষ।"
        assertEquals("def f():\n    return 1", CodeDiff.lastFencedBlock(ans))
        assertNull(CodeDiff.lastFencedBlock("কোনো কোড নেই"))
    }

    @Test fun whitespaceOnlyChangeIsDetected() = assertTrue(CodeDiff.changed(CodeDiff.diff("a\n  b", "a\n    b")))
}
