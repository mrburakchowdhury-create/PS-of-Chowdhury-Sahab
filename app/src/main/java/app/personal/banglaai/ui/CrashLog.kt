package app.personal.banglaai.ui

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import java.io.File

/**
 * Local-only crash note so a restart can be explained on the phone-check screen. It records the time, the thread and the exception TYPE with stack frames,
 * never the exception message (messages can contain private text). Nothing is sent anywhere. [exitReasons] adds Android's own record of why earlier
 * processes of this app ended (crash, killed for memory, …), which also covers kills that no Java handler can see.
 */
object CrashLog {
    private fun file(c: Context) = File(c.filesDir, "last_crash.txt")

    fun install(c: Context) {
        val app = c.applicationContext; val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val sb = StringBuilder("সময়: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}\nথ্রেড: ${t.name}\n")
                var x: Throwable? = e; var depth = 0
                while (x != null && depth++ < 4) { sb.append(x.javaClass.name).append('\n'); x.stackTrace.take(12).forEach { sb.append("  at ").append(it.className).append('.').append(it.methodName).append(':').append(it.lineNumber).append('\n') }; x = x.cause }
                file(app).writeText(sb.toString())
            }
            old?.uncaughtException(t, e)
        }
    }

    fun last(c: Context): String? = file(c).takeIf { it.exists() }?.readText()
    fun clear(c: Context) { file(c).delete() }

    fun exitReasons(c: Context): List<String> {
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return runCatching { am.getHistoricalProcessExitReasons(c.packageName, 0, 6) }.getOrDefault(emptyList()).map { i ->
            val why = when (i.reason) {
                ApplicationExitInfo.REASON_LOW_MEMORY -> "ফোনের মেমরি ফুরিয়ে সিস্টেম অ্যাপ বন্ধ করেছে"
                ApplicationExitInfo.REASON_CRASH -> "অ্যাপ ক্র্যাশ করেছে"
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "নেটিভ (C++) ক্র্যাশ — সম্ভবত মডেল/OCR লাইব্রেরি"
                ApplicationExitInfo.REASON_ANR -> "অ্যাপ সাড়া দেয়নি (ANR)"
                ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "বেশি রিসোর্স ব্যবহারের কারণে বন্ধ"
                ApplicationExitInfo.REASON_USER_REQUESTED, ApplicationExitInfo.REASON_USER_STOPPED -> "আপনি বা সিস্টেম-সেটিংস বন্ধ করেছে"
                ApplicationExitInfo.REASON_SIGNALED -> "সিগন্যালে বন্ধ (সম্ভবত মেমরি-কিলার)"
                ApplicationExitInfo.REASON_EXIT_SELF -> "অ্যাপ নিজে বন্ধ হয়েছে"
                ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "নির্ভরশীল সেবা বন্ধ হওয়ায়"
                else -> "অন্য কারণ (কোড ${i.reason})"
            }
            "${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(i.timestamp))} — $why"
        }
    }
}
