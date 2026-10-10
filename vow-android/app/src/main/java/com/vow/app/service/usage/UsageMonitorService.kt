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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

class UsageMonitorService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var repo: VowRepository
    private lateinit var prefs: VowPreferences
    private var monitoring = false

    companion object {
        const val CHANNEL_ID = "vow_usage_channel"
        const val NOTIFICATION_ID = 2
    }

    override fun onCreate() {
        super.onCreate()
        repo = VowRepository(this)
        prefs = VowPreferences(this)
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
                try {
                    checkAppUsageLimits()
                    checkYouTubeUsage()
                    updateStats()
                } catch (_: Exception) {}
                delay(30_000) // Check every 30 seconds
            }
        }
    }

    private suspend fun checkAppUsageLimits() {
        val appRules = prefs.getAppRules().first()
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return

        val cal = Calendar.getInstance()
        val endTime = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        val startTime = cal.timeInMillis

        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)

        for (rule in appRules) {
            val limit = rule.dailyLimitMinutes ?: continue
            val usage = stats.find { it.packageName == rule.packageName }
            if (usage != null) {
                val usedMinutes = usage.totalTimeInForeground / 60_000
                if (usedMinutes >= limit) {
                    // App has exceeded limit - the accessibility service will handle blocking
                    // We just update the notification
                    updateNotification("${rule.appName}: limit reached (${usedMinutes}m / ${limit}m)")
                }
            }
        }
    }

    private suspend fun checkYouTubeUsage() {
        val restrictions = prefs.getRestrictions().first()
        val ytRestriction = restrictions.find { it.type == RestrictionType.YOUTUBE_LIMIT && it.enabled }
            ?: return

        val usedMinutes = com.vow.app.service.blocking.UsageTracker(this)
            .todayForegroundMinutes()["com.google.android.youtube"]
        if (usedMinutes != null) {
            val allowance = repo.computeYouTubeAllowance()

            val updated = allowance.copy(usedTodayMinutes = usedMinutes)
            prefs.saveYouTubeAllowance(updated)

            if (usedMinutes >= allowance.currentAllowanceMinutes) {
                // YouTube limit reached
                updateNotification("YouTube: daily limit reached")
            }
        }
    }

    private suspend fun updateStats() {
        val baselineDone = prefs.isBaselineDone.first()
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return

        val cal = Calendar.getInstance()
        val endTime = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        val startTime = cal.timeInMillis

        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        val totalToday = stats.sumOf { it.totalTimeInForeground } / 60_000

        val vowStats = prefs.getStats().first()
        val baseline = vowStats.baselineDailyMinutes

        if (baselineDone && baseline > 0 && totalToday < baseline) {
            val saved = baseline - totalToday
            prefs.saveStats(vowStats.copy(
                totalReclaimedMinutes = vowStats.totalReclaimedMinutes + saved,
                daysTracked = vowStats.daysTracked + 1
            ))
        }
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
