package app.personal.banglaai.security

import java.util.concurrent.CopyOnWriteArrayList

/**
 * ONE place that locks the app. Order matters: (1) "before-lock" hooks ask running work to stop at its next safe point (pause flags, cancel model calls,
 * stop reading unless the owner allowed limited background reading) and (2) THEN the vault key session is ended, so anything still running fails with
 * [VaultLockedException] instead of continuing on the old session. The UI flag (screen goes to the PIN page) is set by the caller.
 */
object AppLock {
    private val before = CopyOnWriteArrayList<() -> Unit>()
    fun addBeforeLock(h: () -> Unit) { before += h }
    fun removeBeforeLock(h: () -> Unit) { before -= h }

    fun lock(keys: KeyVault?) {
        before.forEach { runCatching(it) }
        keys?.lock()
    }
}
