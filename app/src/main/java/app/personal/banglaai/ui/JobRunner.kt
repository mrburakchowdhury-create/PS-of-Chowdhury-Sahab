package app.personal.banglaai.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.personal.banglaai.jobs.JobStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runs the owner-started long jobs (extract, OCR, translate, export) in a process-wide scope instead of the screen's scope. The app locks itself when the
 * display turns off, which tears the screen down; a job tied to that screen was cancelled mid-way. Progress and the outcome are plain observable state, so the
 * document screen simply re-attaches to a job that kept running. Jobs still checkpoint every page and can be paused/cancelled/resumed by the owner.
 */
object JobRunner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var label by mutableStateOf<String?>(null); private set
    var progress by mutableStateOf(0 to 0)
    var error by mutableStateOf<String?>(null); private set
    var tick by mutableIntStateOf(0); private set

    fun start(name: String, block: () -> JobStatus) {
        if (label != null) return
        label = name; error = null; progress = 0 to 0
        scope.launch {
            runCatching { block() }.onFailure { error = if (it is app.personal.banglaai.security.VaultLockedException) "ভল্ট বন্ধ হওয়ায় কাজ থেমেছে — আবার খুলে ‘চালিয়ে যান’ চাপুন; শেষ চেকপয়েন্ট থেকে চলবে।" else it.message ?: it.javaClass.simpleName }
            label = null; tick++
        }
    }
}
