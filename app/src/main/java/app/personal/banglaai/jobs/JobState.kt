package app.personal.banglaai.jobs

import org.json.JSONArray
import org.json.JSONObject

enum class JobStatus { PENDING, RUNNING, PAUSED, CANCELLED, DONE }
enum class PageStatus { PENDING, DONE, FAILED, UNREADABLE }

/**
 * Persistent checkpoint of one long task (e.g. "extract text of all pages", later "translate all pages").
 * Per-page status lets a restart resume without redoing finished pages and lets us show a coverage report.
 * RUNNING is never trusted after a restart: [afterRestart] turns it into PAUSED so nothing resumes by itself.
 */
class JobState(
    val id: String,
    val docId: String,
    val kind: String,
    val pages: IntArray,   // ordinal of PageStatus per physical page, index 0 = page 1
    var status: JobStatus = JobStatus.PENDING,
) {
    constructor(id: String, docId: String, kind: String, pageCount: Int) :
        this(id, docId, kind, IntArray(pageCount) { PageStatus.PENDING.ordinal })

    val total get() = pages.size
    fun count(s: PageStatus) = pages.count { it == s.ordinal }
    fun pageStatus(page: Int) = PageStatus.entries[pages[page - 1]]
    fun mark(page: Int, s: PageStatus) { pages[page - 1] = s.ordinal }
    /** Pages still to do, in order; DONE pages are never returned again. FAILED pages are retried only on explicit resume. */
    fun remaining(includeFailed: Boolean = false) =
        (1..total).filter { pageStatus(it) == PageStatus.PENDING || (includeFailed && pageStatus(it) == PageStatus.FAILED) }

    fun afterRestart() { if (status == JobStatus.RUNNING) status = JobStatus.PAUSED }

    fun coverageReport(): String = buildString {
        append("মোট ").append(total).append(" · সম্পন্ন ").append(count(PageStatus.DONE))
        append(" · বাকি ").append(count(PageStatus.PENDING))
        append(" · ব্যর্থ ").append(count(PageStatus.FAILED))
        append(" · পড়া যায়নি ").append(count(PageStatus.UNREADABLE))
        val bad = (1..total).filter { pageStatus(it) == PageStatus.FAILED || pageStatus(it) == PageStatus.UNREADABLE }
        if (bad.isNotEmpty()) append("\nসমস্যাযুক্ত পৃষ্ঠা: ").append(bad.joinToString(", "))
    }

    fun toJson() = JSONObject().put("id", id).put("docId", docId).put("kind", kind)
        .put("status", status.name).put("pages", JSONArray(pages.toList()))

    companion object {
        fun fromJson(o: JSONObject): JobState {
            val a = o.getJSONArray("pages")
            return JobState(o.getString("id"), o.getString("docId"), o.getString("kind"),
                IntArray(a.length()) { a.getInt(it) }, JobStatus.valueOf(o.getString("status")))
        }
    }
}
