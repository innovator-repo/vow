package com.vow.app.service.blocking

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.telecom.TelecomManager
import com.vow.app.data.model.RestrictionType
import com.vow.app.data.repository.VowRepository
import kotlinx.coroutines.flow.first

data class BlockReason(val title: String, val message: String)

class BlockPolicy(private val context: Context, private val repo: VowRepository) {

    private val tracker = UsageTracker(context)
    private val pm = context.packageManager

    suspend fun check(packageName: String): BlockReason? {
        if (isAlwaysAllowed(packageName)) return null

        val prefs = repo.prefs
        val restrictions = prefs.getRestrictions().first()
        fun on(type: RestrictionType) = restrictions.any { it.type == type && it.enabled }

        val rules = prefs.getAppRules().first()
        val rule = rules.find { it.packageName == packageName }
        val appLimitsOff = restrictions.any { it.type == RestrictionType.APP_LIMIT && !it.enabled }
        val label = rule?.appName ?: appLabel(packageName)

        val profile = prefs.getProfiles().first().firstOrNull { it.isActive && packageName in it.blockedApps }
        if (profile != null) {
            return BlockReason(
                "$label is off during ${profile.name}",
                "Your ${profile.name} profile is on. Turn the profile off when you're done to use $label again."
            )
        }

        if (on(RestrictionType.NIGHT_MODE) && repo.isNightModeActive()) {
            val config = prefs.getNightConfig().first()
            val essential = packageName in config.essentialApps || rule?.allowedInNightMode == true
            if (!essential) {
                return BlockReason(
                    "$label is closed until morning",
                    "Night mode is on. Essentials still work — everything else opens again at " +
                        "%02d:%02d.".format(config.endHour, config.endMinute)
                )
            }
        }

        if (rule != null && !appLimitsOff) {
            if (rule.blocked) {
                return BlockReason("$label is blocked", "You chose to block this app. That choice still stands.")
            }
            val limit = rule.dailyLimitMinutes
            if (limit != null) {
                val used = tracker.todayForegroundMinutes()[packageName] ?: 0
                if (used >= limit) {
                    return BlockReason(
                        "Today's time for $label is used up",
                        "You set $limit minutes a day and used $used. It opens again tomorrow."
                    )
                }
            }
        }

        if (packageName == YOUTUBE && on(RestrictionType.YOUTUBE_LIMIT)) {
            val allowance = repo.computeYouTubeAllowance().currentAllowanceMinutes
            val used = tracker.todayForegroundMinutes()[YOUTUBE] ?: 0
            if (used >= allowance) {
                return BlockReason(
                    "YouTube is done for today",
                    "Today's allowance was $allowance minutes. It resets at midnight."
                )
            }
        }
        return null
    }

    private fun isAlwaysAllowed(pkg: String): Boolean {
        if (pkg == context.packageName || pkg in SYSTEM_ALLOWED) return true
        if (pkg == defaultInputMethod() || pkg == defaultDialer()) return true
        return pkg in launchers()
    }

    private fun launchers(): Set<String> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }.toSet()

    private fun defaultInputMethod(): String? =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')

    private fun defaultDialer(): String? =
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage

    private fun appLabel(pkg: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg
    }

    companion object {
        const val YOUTUBE = "com.google.android.youtube"
        private val SYSTEM_ALLOWED = setOf(
            "android", "com.android.systemui", "com.android.settings",
            "com.google.android.permissioncontroller", "com.android.permissioncontroller",
            "com.android.vpndialogs", "com.android.emergency", "com.google.android.apps.safetyhub",
            "com.android.phone", "com.android.server.telecom"
        )
    }
}
