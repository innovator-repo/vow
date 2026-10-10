package com.vow.app.service.accessibility

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserManager

/**
 * Accessibility events also arrive from work-profile apps, without saying which profile they
 * came from. Usage stats are per-profile, so they tell us whether the personal copy is in front.
 * When we cannot tell, we treat the app as work and leave it alone.
 */
class ProfileGuard(private val context: Context) {

    private val usm = context.getSystemService(UsageStatsManager::class.java)
    private var foreground: String? = null
    private var lastQueryAt = System.currentTimeMillis() - 24 * 60 * 60 * 1000L

    fun isPersonalForeground(packageName: String): Boolean {
        if (!isInstalledInPersonalProfile(packageName)) return false
        if (!hasWorkProfile()) return true
        if (!hasUsageAccess()) return false
        return currentForeground() == packageName
    }

    private fun isInstalledInPersonalProfile(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun hasWorkProfile(): Boolean =
        context.getSystemService(UserManager::class.java).userProfiles.size > 1

    private fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    @Suppress("DEPRECATION")
    private fun currentForeground(): String? {
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(lastQueryAt, now)
        lastQueryAt = now
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> foreground = e.packageName
                UsageEvents.Event.MOVE_TO_BACKGROUND ->
                    if (foreground == e.packageName) foreground = null
            }
        }
        return foreground
    }
}
