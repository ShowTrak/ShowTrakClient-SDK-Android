package io.showtrak.sdk

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import java.io.RandomAccessFile
import java.util.Locale
import org.json.JSONObject

/**
 * Collects real device metrics for the heartbeat. The SDK never fabricates
 * values: any metric the platform cannot provide is simply omitted, and the
 * server normalizes the shape so rendering stays safe.
 */
internal class MetricsCollector(context: Context) {

    private val appContext = context.applicationContext
    private val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    // CPU is derived from a delta of the app process's CPU time vs wall time.
    private var lastCpuTicks = -1L
    private var lastWallMs = -1L

    fun vitals(): JSONObject {
        val vitals = JSONObject()
        cpuUsagePercent()?.let { vitals.put("CPU", JSONObject().put("UsagePercentage", it)) }
        ramUsage()?.let { vitals.put("Ram", it) }
        vitals.put(
            "Uptime",
            JSONObject().put("Formatted", formatUptime(SystemClock.elapsedRealtime() / 1000))
        )
        return vitals
    }

    private fun ramUsage(): JSONObject? = try {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val total = info.totalMem
        val used = info.totalMem - info.availMem
        val percent = if (total > 0) used.toDouble() / total.toDouble() * 100.0 else 0.0
        // Field names and types are fixed by RamVitals in the ShowTrak wire
        // protocol: bytes as numbers, and the percentage as a two-decimal
        // STRING. Sending a number here fails the server's heartbeat
        // validation outright, which silently leaves the device offline.
        JSONObject()
            .put("Total", total)
            .put("Used", used)
            .put("UsagePercentage", String.format(Locale.US, "%.2f", percent))
    } catch (_: Exception) {
        null
    }

    /** Uptime as `HH:mm:ss`, matching UptimeVitals in the wire protocol. */
    private fun formatUptime(totalSeconds: Long): String {
        val safe = if (totalSeconds > 0) totalSeconds else 0
        return String.format(
            Locale.US,
            "%02d:%02d:%02d",
            safe / 3600,
            (safe % 3600) / 60,
            safe % 60
        )
    }

    private fun cpuUsagePercent(): Long? {
        return try {
            val ticks = readProcessCpuTicks() ?: return null
            val now = SystemClock.elapsedRealtime()
            val result: Long? =
                if (lastCpuTicks >= 0 && lastWallMs >= 0 && now > lastWallMs) {
                    val ticksPerSecond = 100.0 // typical USER_HZ on Android
                    val cpuDeltaSeconds = (ticks - lastCpuTicks) / ticksPerSecond
                    val wallDeltaSeconds = (now - lastWallMs) / 1000.0
                    val percent = (cpuDeltaSeconds / (wallDeltaSeconds * cores)) * 100.0
                    Math.round(percent.coerceIn(0.0, 100.0))
                } else {
                    null // first sample establishes a baseline
                }
            lastCpuTicks = ticks
            lastWallMs = now
            result
        } catch (_: Exception) {
            null
        }
    }

    // utime (field 14) + stime (field 15) from /proc/self/stat, in clock ticks.
    // The comm field (2) may contain spaces, so we parse after the final ')'.
    private fun readProcessCpuTicks(): Long? {
        return try {
            RandomAccessFile("/proc/self/stat", "r").use { raf ->
                val line = raf.readLine() ?: return null
                val rparen = line.lastIndexOf(')')
                if (rparen < 0 || rparen + 2 >= line.length) return null
                val rest = line.substring(rparen + 2).trim().split(Regex("\\s+"))
                // rest[0] is field 3 (state); utime = field 14 -> index 11; stime -> index 12.
                if (rest.size <= 12) return null
                rest[11].toLong() + rest[12].toLong()
            }
        } catch (_: Exception) {
            null
        }
    }
}
