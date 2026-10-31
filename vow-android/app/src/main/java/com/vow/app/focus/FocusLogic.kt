package com.vow.app.focus

import com.vow.app.data.model.Profile

/** Pure rules for the Focus switch: which profile is "the" profile, and what a toggle may do. */
object FocusLogic {

    /** The profile the app shows as active: the first one switched on. */
    fun activeProfile(profiles: List<Profile>): Profile? = profiles.firstOrNull { it.isActive }

    fun focusOn(profiles: List<Profile>): Boolean = activeProfile(profiles) != null

    /** Name shown for the profile: the active one, else the one a switch-on would start, else empty. */
    fun profileName(profiles: List<Profile>): String =
        (activeProfile(profiles) ?: profiles.firstOrNull())?.name.orEmpty()

    sealed class Toggle {
        /** Focus was off and is now on. Needs no unlock: it only tightens protection. */
        data class TurnedOn(val profiles: List<Profile>) : Toggle()

        /** Focus is on. Turning it off loosens protection, so it must go through the app's lock. */
        object NeedsUnlockToTurnOff : Toggle()

        /** There is no profile to switch on yet. */
        object NoProfile : Toggle()
    }

    /** What an external trigger (deep link, widget) may do. It can never switch focus off by itself. */
    fun externalToggle(profiles: List<Profile>): Toggle = when {
        focusOn(profiles) -> Toggle.NeedsUnlockToTurnOff
        profiles.isEmpty() -> Toggle.NoProfile
        else -> Toggle.TurnedOn(switchOn(profiles))
    }

    /** Switches on the first profile and leaves the rest as they are. */
    fun switchOn(profiles: List<Profile>): List<Profile> {
        val first = profiles.firstOrNull() ?: return profiles
        return profiles.map { if (it.id == first.id) it.copy(isActive = true) else it }
    }

    fun setProfile(profiles: List<Profile>, id: String, active: Boolean): List<Profile> =
        profiles.map { if (it.id == id) it.copy(isActive = active) else it }

    /** "3 h", "45 m", "3 h 20 m". */
    fun formatDuration(minutes: Long): String {
        val m = minutes.coerceAtLeast(0)
        val h = m / 60
        val r = m % 60
        return when {
            h == 0L -> "$r m"
            r == 0L -> "$h h"
            else -> "$h h $r m"
        }
    }

    /**
     * Time reclaimed so far today: the daily baseline minus today's screen time.
     * Null when there is no baseline or no usage data, so callers can fall back to the total.
     */
    fun reclaimedToday(baselineDailyMinutes: Long, screenTodayMinutes: Int?): Long? {
        if (baselineDailyMinutes <= 0 || screenTodayMinutes == null) return null
        return (baselineDailyMinutes - screenTodayMinutes).coerceAtLeast(0)
    }

    /** Ring fill: grows with reclaimed time and never reaches a full circle. */
    fun ringFraction(minutes: Long): Float =
        (minutes.toFloat() / (minutes + 60).toFloat()).coerceIn(0.02f, 0.95f)

    fun heroTitle(focusOn: Boolean, activeRules: Int): String =
        if (focusOn || activeRules > 0) "Focus is holding" else "Focus is resting"

    fun heroSubtitle(profileName: String, activeRules: Int): String {
        val rules = if (activeRules == 1) "1 protection" else "$activeRules protections"
        return if (profileName.isBlank()) rules else "$profileName · $rules"
    }
}

/** Everything the hero and the widget show, read once. */
data class FocusSnapshot(
    val focusOn: Boolean = false,
    val profileName: String = "",
    val reclaimedMinutes: Long = 0,
    /** True when [reclaimedMinutes] is today's figure, false when it is the running total. */
    val isToday: Boolean = false
)
