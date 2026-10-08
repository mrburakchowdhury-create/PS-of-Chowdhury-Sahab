package app.personal.banglaai.doc

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager

/** Long jobs (extraction, OCR, translation) pause themselves, with a plain reason, when the phone is too hot, nearly out of battery or nearly out of space. Nothing is lost: a paused job resumes. */
object ResourceGuard {
    const val MIN_FREE_BYTES = 150L * 1024 * 1024
    const val MIN_BATTERY_PCT = 10

    /** Pure decision (tested on the JVM). thermalStatus is [PowerManager] THERMAL_STATUS_*; null/negative = unknown. */
    fun decide(thermalStatus: Int?, batteryPct: Int?, charging: Boolean, freeBytes: Long): String? = when {
        freeBytes < MIN_FREE_BYTES -> "ফোনে খালি জায়গা কম — জায়গা খালি করে আবার চালু করুন"
        thermalStatus != null && thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE -> "ফোন খুব গরম হয়ে গেছে — ঠান্ডা হলে আবার চালু করুন"
        batteryPct != null && batteryPct in 0 until MIN_BATTERY_PCT && !charging -> "ব্যাটারি $batteryPct% — চার্জে দিয়ে আবার চালু করুন"
        else -> null
    }

    fun check(context: Context): String? {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val bat: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bat?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1; val scale = bat?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else null
        val status = bat?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return decide(pm?.currentThermalStatus, pct, charging, context.noBackupFilesDir.usableSpace)
    }
}
