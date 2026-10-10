package com.vow.app.service.usage

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vow.app.MainActivity
import com.vow.app.R
import com.vow.app.data.model.RestrictionType
import com.vow.app.data.prefs.VowPreferences
import com.vow.app.data.repository.VowRepository
import com.vow.app.service.admin.VowDeviceAdminReceiver
import com.vow.app.service.blocking.UsageTracker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

class UsageMonitorService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var repo: VowRepository
    private lateinit var prefs: VowPreferences
    private var monitoring = false
    private lateinit var tracker: UsageTracker

    companion object {
        const val CHANNEL_ID = "vow_usage_channel"
        const val NOTIFICATION_ID = 2
        private const val WIND_DOWN_CHANNEL = "vow_wind_down"
        private const val WIND_DOWN_ID = 3
    }

    override fun onCreate() {
        super.onCreate()
        repo = VowRepository(this)
        prefs = VowPreferences(this)
        tracker = UsageTracker(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            androidx.core.app.ServiceCompat.startForeground(
                this, NOTIFICATION_ID, buildNotification("Monitoring usage"),
                if (android.os.Build.VERSION.SDK_INT >= 34)
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            )
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!monitoring) {
            monitoring = true
            startMonitoring()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        scope.launch {
            while (isActive) {
                for (step in listOf<suspend () -> Unit>(
                    { repo.applyMaturedChanges() },
                    { syncDeviceOwner() },
                    { checkAppUsageLimits() },
                    { checkYouTubeUsage() },
                    { updateStats() },
                    { checkWindDown() }
                )) {
                    try { step() } catch (_: Exception) {}
                }
                delay(30_000)
            }
        }
    }

    private suspend fun syncDeviceOwner() {
        val isOwner = VowDeviceAdminReceiver.isDeviceOwner(this)
        if (prefs.isDeviceOwner.first() != isOwner) prefs.setDeviceOwner(isOwner)
        if (isOwner) VowDeviceAdminReceiver.syncDeviceOwnerPolicies(this, prefs.getRestrictions().first())
    }

    private suspend fun checkAppUsageLimits() {
        val used = tracker.todayForegroundMinutes()
        val over = prefs.getAppRules().first().firstOrNull { rule ->
            val limit = rule.dailyLimitMinutes ?: return@firstOrNull false
            (used[rule.packageName] ?: 0) >= limit
        }
        updateNotification(if (over != null) "${over.appName}: today's limit is used up" else "Protecting your commitments")
    }

    private suspend fun checkYouTubeUsage() {
        val on = prefs.getRestrictions().first().any { it.type == RestrictionType.YOUTUBE_LIMIT && it.enabled }
        if (!on) return
        val allowance = repo.computeYouTubeAllowance()
        val usedMinutes = tracker.todayForegroundMinutes()["com.google.android.youtube"] ?: 0
        val today = repo.todayKey()
        if (allowance.usedTodayMinutes != usedMinutes || allowance.usedDate != today) {
            prefs.saveYouTubeAllowance(allowance.copy(usedTodayMinutes = usedMinutes, usedDate = today))
        }
    }

    /**
     * Baseline = average daily screen time over the last 7 days with data. After that, each
     * finished day adds (baseline - that day's screen time) once, keyed by date.
     */
    private suspend fun updateStats() {
        val today = repo.todayKey()
        var stats = prefs.getStats().first()
        if (stats.lastProcessedDate == today) return

        if (stats.baselineDailyMinutes <= 0) {
            val days = (1..7).filter { tracker.hasData(it) }.map { tracker.screenTimeMinutes(it) }
            if (days.isEmpty()) return
            stats = stats.copy(
                baselineDailyMinutes = days.average().toLong(),
                measurementStartDate = today,
                lastProcessedDate = today
            )
            prefs.saveStats(stats)
            prefs.setBaselineDone(true)
            return
        }

        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        var reclaimed = stats.totalReclaimedMinutes
        var tracked = stats.daysTracked
        for (daysAgo in 7 downTo 1) {
            val key = fmt.format(Date(UsageTracker.startOfDay(daysAgo)))
            if (key <= stats.lastProcessedDate || key < stats.measurementStartDate) continue
            if (!tracker.hasData(daysAgo)) continue
            reclaimed += (stats.baselineDailyMinutes - tracker.screenTimeMinutes(daysAgo)).coerceAtLeast(0)
            tracked++
        }
        prefs.saveStats(stats.copy(totalReclaimedMinutes = reclaimed, daysTracked = tracked, lastProcessedDate = today))
    }

    private var windDownNotifiedOn = ""

    private suspend fun checkWindDown() {
        val on = prefs.getRestrictions().first().any { it.type == RestrictionType.NIGHT_MODE && it.enabled }
        val today = repo.todayKey()
        if (!on || windDownNotifiedOn == today || !repo.isInWindDown()) return
        windDownNotifiedOn = today
        val config = prefs.getNightConfig().first()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(WIND_DOWN_CHANNEL, "Wind-down reminders", NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.notify(
            WIND_DOWN_ID,
            NotificationCompat.Builder(this, WIND_DOWN_CHANNEL)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle("Time to wind down")
                .setContentText("Night mode starts at %02d:%02d. Finish what you're doing.".format(config.startHour, config.startMinute))
                .setAutoCancel(true)
                .build()
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Usage Monitoring",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Screen time monitoring"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vow")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
