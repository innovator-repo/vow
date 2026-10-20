package com.vow.app.service.blocking

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import java.util.Calendar

/** Foreground time per app, computed from raw events (daily buckets are unreliable). */
class UsageTracker(private val context: Context) {

    private val usm = context.getSystemService(UsageStatsManager::class.java)

    fun todayForegroundMinutes(): Map<String, Int> =
        foregroundMinutes(startOfDay(0), System.currentTimeMillis())

    /** Total screen time for the day [daysAgo] days back, excluding launchers and Vow itself. */
    fun screenTimeMinutes(daysAgo: Int): Int {
        val launchers = context.packageManager
            .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }.toSet()
        val end = if (daysAgo == 0) System.currentTimeMillis() else startOfDay(daysAgo - 1)
        return foregroundMinutes(startOfDay(daysAgo), end)
            .filterKeys { it !in launchers && it != context.packageName && it != "com.android.systemui" }
            .values.sum()
    }

    fun foregroundMinutes(from: Long, to: Long): Map<String, Int> =
        foregroundMillis(from, to).mapValues { (it.value / 60_000L).toInt() }

    fun todayForegroundMillis(): Map<String, Long> = foregroundMillis(startOfDay(0), System.currentTimeMillis())

    @Suppress("DEPRECATION")
    fun foregroundMillis(from: Long, to: Long): Map<String, Long> {
        val totals = HashMap<String, Long>()
        val openedAt = HashMap<String, Long>()
        val events = usm.queryEvents(from, to)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> openedAt[e.packageName] = e.timeStamp
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val start = openedAt.remove(e.packageName) ?: from
                    totals[e.packageName] = (totals[e.packageName] ?: 0L) + (e.timeStamp - start)
                }
            }
        }
        for ((pkg, start) in openedAt) totals[pkg] = (totals[pkg] ?: 0L) + (to - start)
        return totals
    }

    /** Has the system recorded anything for that day? (Distinguishes "no use" from "no data".) */
    fun hasData(daysAgo: Int): Boolean {
        val end = if (daysAgo == 0) System.currentTimeMillis() else startOfDay(daysAgo - 1)
        return usm.queryEvents(startOfDay(daysAgo), end).hasNextEvent()
    }

    companion object {
        fun startOfDay(daysAgo: Int): Long = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -daysAgo)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
