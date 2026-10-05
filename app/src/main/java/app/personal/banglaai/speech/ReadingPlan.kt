package app.personal.banglaai.speech

import app.personal.banglaai.ai.Chunker

data class Segment(val page: Int, val index: Int, val text: String)
data class ReadingPosition(val page: Int, val segment: Int)

/**
 * Splits pages into short speakable segments (never the whole book at once) and moves a cursor over them.
 * Position = (physical page, segment index), so reading resumes at the same sentence group after an interruption.
 */
class ReadingPlan(private val pageText: (Int) -> String?, val pageCount: Int, private val maxChars: Int = 300) {
    private val cache = HashMap<Int, List<Segment>>()
    fun segments(page: Int): List<Segment> = cache.getOrPut(page) {
        Chunker.split(pageText(page).orEmpty(), maxChars).mapIndexed { i, t -> Segment(page, i, t) }
    }.also { if (cache.size > 8) cache.keys.firstOrNull { it != page }?.let(cache::remove) }

    /** First readable position at or after [page]; null if nothing from there on has text. */
    fun startAt(page: Int): ReadingPosition? = (page.coerceAtLeast(1)..pageCount).firstOrNull { segments(it).isNotEmpty() }?.let { ReadingPosition(it, 0) }

    fun next(p: ReadingPosition): ReadingPosition? {
        if (p.segment + 1 < segments(p.page).size) return ReadingPosition(p.page, p.segment + 1)
        return startAt(p.page + 1)
    }

    fun previous(p: ReadingPosition): ReadingPosition? {
        if (p.segment > 0) return ReadingPosition(p.page, p.segment - 1)
        for (pg in p.page - 1 downTo 1) segments(pg).let { if (it.isNotEmpty()) return ReadingPosition(pg, it.size - 1) }
        return null
    }

    fun segmentAt(p: ReadingPosition): Segment? = segments(p.page).getOrNull(p.segment)
}
