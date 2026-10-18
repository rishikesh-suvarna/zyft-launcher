package dev.rishikeshsuvarna.zyft

import android.os.Process
import android.os.SystemClock
import android.os.Trace
import android.util.Log

/**
 * Startup instrumentation that costs nothing in normal use:
 * - trace sections show up in system traces (Perfetto / atrace)
 * - timing logs are off unless enabled on the device:
 *     adb shell setprop log.tag.ZyftPerf DEBUG
 *     adb logcat -s ZyftPerf
 */
object Perf {
    private const val TAG = "ZyftPerf"

    val enabled: Boolean get() = Log.isLoggable(TAG, Log.DEBUG)

    inline fun <T> section(name: String, block: () -> T): T {
        Trace.beginSection(name)
        val start = if (enabled) SystemClock.elapsedRealtimeNanos() else 0L
        try {
            return block()
        } finally {
            Trace.endSection()
            if (start != 0L) log("$name: ${(SystemClock.elapsedRealtimeNanos() - start) / NANOS_PER_MS} ms")
        }
    }

    fun log(message: String) {
        if (enabled) Log.d(TAG, message)
    }

    /** Milestone relative to process start, e.g. "onCreate +312 ms" (startup timeline). */
    fun milestone(name: String) {
        if (enabled) Log.d(TAG, "@$name +${SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()} ms")
    }

    const val NANOS_PER_MS = 1_000_000L
}
