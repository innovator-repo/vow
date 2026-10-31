package com.vow.app.focus

import android.content.Context
import com.vow.app.data.prefs.VowPreferences
import com.vow.app.service.blocking.UsageTracker
import kotlinx.coroutines.flow.first

/** Reads the focus state for the widget and the hero. Never throws: no data gives an empty snapshot. */
object FocusSnapshotLoader {
    suspend fun load(context: Context): FocusSnapshot = try {
        val prefs = VowPreferences(context)
        val profiles = prefs.getProfiles().first()
        val stats = prefs.getStats().first()
        val screen = try {
            val tracker = UsageTracker(context)
            if (tracker.hasData(0)) tracker.screenTimeMinutes(0) else null
        } catch (_: Exception) {
            null
        }
        val today = FocusLogic.reclaimedToday(stats.baselineDailyMinutes, screen)
        FocusSnapshot(
            focusOn = FocusLogic.focusOn(profiles),
            profileName = FocusLogic.profileName(profiles),
            reclaimedMinutes = today ?: stats.totalReclaimedMinutes,
            isToday = today != null
        )
    } catch (_: Exception) {
        FocusSnapshot()
    }
}
