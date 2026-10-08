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
    var totalMb by mutableStateOf<Long?>(null); private set       // size announced by the file provider, if any
    var lastError by mutableStateOf<String?>(null); private set
    var lastOk by mutableStateOf<String?>(null); private set
    val running get() = copiedMb != null

    fun start(store: ModelStore, uri: Uri) {
        if (running) return
        copiedMb = 0; totalMb = null; lastError = null; lastOk = null
        scope.launch {
            runCatching { store.import(uri, { copiedMb = it / 1_000_000L }, { totalMb = it / 1_000_000L }) }
                .onSuccess { lastOk = "${it.name} কপি হয়েছে (${it.size / 1_000_000L} MB)" }
                .onFailure { lastError = it.message ?: it.javaClass.simpleName }
            copiedMb = null
        }
    }

    /** Computes the missing checksum of a model file that is already on the phone (no copy needed). */
    fun startVerify(store: ModelStore, name: String) {
        if (running) return
        copiedMb = 0; lastError = null; lastOk = null
        scope.launch {
            runCatching { store.rehash(name) { copiedMb = it / 1_000_000L } }
                .onSuccess { lastOk = "${it.name} যাচাই শেষ" }
                .onFailure { lastError = it.message ?: it.javaClass.simpleName }
            copiedMb = null
        }
    }
}
