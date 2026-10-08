package app.personal.banglaai.ui

import android.app.Activity
import android.view.WindowManager

/** Reference-counted "keep the screen on": a copy, a call and a live-translation session can overlap, and one finishing must not switch the flag off for the others. */
object KeepAwake {
    private var n = 0
    @Synchronized fun acquire(a: Activity?) { n++; a?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    @Synchronized fun release(a: Activity?) { n = (n - 1).coerceAtLeast(0); if (n == 0) a?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
}
