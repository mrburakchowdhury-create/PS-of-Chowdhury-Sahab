package app.personal.banglaai.ui

import android.os.Process
import android.os.SystemClock
import java.io.File

/**
 * Shows what in THIS app is using the processor (heat and battery come from CPU work). Reads /proc/self/task/<tid>/stat (our own threads only).
 * [sample] returns the app's total CPU use since the previous call, in "% of one core" (100 = one whole core busy; 400 = four cores), and the busiest threads.
 */
class CpuMeter {
    data class Result(val percentOfOneCore: Int, val top: List<Pair<String, Int>>)
    private var lastWall = SystemClock.elapsedRealtime(); private var lastCpu = Process.getElapsedCpuTime()
    private var lastThreads: Map<Int, Long> = emptyMap()
    private val tick = (runCatching { android.system.Os.sysconf(android.system.OsConstants._SC_CLK_TCK) }.getOrDefault(100L)).coerceAtLeast(1L)

    private fun threads(): Map<Int, Pair<String, Long>> = runCatching {
        File("/proc/self/task").listFiles().orEmpty().mapNotNull { d ->
            val tid = d.name.toIntOrNull() ?: return@mapNotNull null
            val s = runCatching { File(d, "stat").readText() }.getOrNull() ?: return@mapNotNull null
            val name = s.substringAfter('(').substringBeforeLast(')'); val rest = s.substringAfterLast(')').trim().split(' ')
            val ut = rest.getOrNull(11)?.toLongOrNull() ?: 0L; val st = rest.getOrNull(12)?.toLongOrNull() ?: 0L
            tid to (name to (ut + st))
        }.toMap()
    }.getOrDefault(emptyMap())

    fun sample(): Result {
        val wall = SystemClock.elapsedRealtime(); val cpu = Process.getElapsedCpuTime()
        val dw = (wall - lastWall).coerceAtLeast(1); val pct = (((cpu - lastCpu) * 100) / dw).toInt()
        val now = threads()
        val top = now.mapNotNull { (tid, v) ->
            val d = v.second - (lastThreads[tid] ?: v.second)   // ticks used since the last sample
            val p = (d * 1000L / tick * 100 / dw).toInt(); if (p > 0) v.first to p else null
        }.groupBy({ it.first }, { it.second }).map { (n, l) -> n to l.sum() }.sortedByDescending { it.second }.take(6)
        lastWall = wall; lastCpu = cpu; lastThreads = now.mapValues { it.value.second }
        return Result(pct, top)
    }
}
