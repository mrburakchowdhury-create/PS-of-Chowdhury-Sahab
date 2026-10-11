package app.personal.banglaai.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** The phone's own heat level (Android thermal status 0 none … 6 shutdown). At SEVERE (3) or worse the app stops its heavy AI/translation work and says so; it never deletes anything. */
object ThermalGuard {
    var status by mutableIntStateOf(0)
    const val WARN = 2      // THERMAL_STATUS_MODERATE
    const val STOP = 3      // THERMAL_STATUS_SEVERE
    fun label(s: Int) = when (s) { 2 -> "গরম"; 3 -> "বেশ গরম"; 4 -> "খুব গরম"; 5, 6 -> "বিপজ্জনক গরম"; else -> "স্বাভাবিক" }

    /**
     * Automatic cooling: the owner picks how many CPU cores the AI is ALLOWED to use (`LocalLlm.preferredThreads`, in Phone check); this quietly uses
     * fewer than that — never more — once the phone itself reports it is warming up, and gives the full amount back once it reports normal again. Nothing
     * stops working either way, it only answers a bit slower while capped. Call after [status] changes (the thermal listener) and after the owner changes
     * their preference, so the two stay in sync without the owner having to notice or act.
     */
    fun applyThreads() {
        val pref = app.personal.banglaai.ai.LocalLlm.preferredThreads
        val effective = if (status >= WARN) minOf(pref, 2) else pref
        if (app.personal.banglaai.ai.LocalLlm.threads != effective) {
            app.personal.banglaai.ai.LocalLlm.threads = effective
            app.personal.banglaai.ai.LocalLlm.releaseAll()   // only takes effect once idle; never interrupts an answer in progress
        }
    }
}
