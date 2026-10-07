package app.personal.banglaai.storage

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runs the (multi-minute, ≈3.6 GB) model copy in a process-wide scope, NOT in the home screen's scope: the screen locks itself when the display
 * turns off, which used to cancel the copy half-way and silently throw the partial file away. Progress/outcome are plain observable state.
 */
object ModelImporter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var copiedMb by mutableStateOf<Long?>(null); private set      // non-null while copying
    var lastError by mutableStateOf<String?>(null); private set
    var lastOk by mutableStateOf<String?>(null); private set
    val running get() = copiedMb != null

    fun start(store: ModelStore, uri: Uri) {
        if (running) return
        copiedMb = 0; lastError = null; lastOk = null
        scope.launch {
            runCatching { store.import(uri) { copiedMb = it / (1 shl 20) } }
                .onSuccess { lastOk = "${it.name} কপি হয়েছে (${it.size / (1 shl 20)} MB)" }
                .onFailure { lastError = it.message ?: it.javaClass.simpleName }
            copiedMb = null
        }
    }
}
