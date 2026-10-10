package com.vow.app.service.blocking

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

/** Today's foreground time per app, computed from raw events (daily buckets are unreliable). */
class UsageTracker(context: Context) {

    private val usm = context.getSystemService(UsageStatsManager::class.java)

    @Suppress("DEPRECATION")
    fun todayForegroundMinutes(): Map<String, Int> {
        val now = System.currentTimeMillis()
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val totals = HashMap<String, Long>()
        val openedAt = HashMap<String, Long>()
        val events = usm.queryEvents(midnight, now)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> openedAt[e.packageName] = e.timeStamp
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val start = openedAt.remove(e.packageName) ?: midnight
                    totals[e.packageName] = (totals[e.packageName] ?: 0L) + (e.timeStamp - start)
                }
            }
        }
        for ((pkg, start) in openedAt) totals[pkg] = (totals[pkg] ?: 0L) + (now - start)
        return totals.mapValues { (it.value / 60_000L).toInt() }
    }
}
