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
}
