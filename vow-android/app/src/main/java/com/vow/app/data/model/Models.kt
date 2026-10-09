package com.vow.app.data.model

import java.util.UUID

enum class RestrictionType {
    DNS, DATE_TIME, UNINSTALL, SHORT_VIDEO, ADULT_SITES, KEYWORD_BLOCK, NIGHT_MODE, APP_LIMIT, YOUTUBE_LIMIT
}

enum class SealState {
    UNSEALED, PENDING_SEAL, SEALED
}

enum class ProfileType {
    WORK, STUDY, BEDTIME, CUSTOM
}

data class Restriction(
    val id: String = UUID.randomUUID().toString(),
    val type: RestrictionType,
    val enabled: Boolean = true,
    val sealState: SealState = SealState.UNSEALED,
    val sealRequestedAt: Long? = null,
    val sealConfirmedAt: Long? = null,
    val reason: String = "",
    val ifThenPlan: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class BlockedKeyword(
    val id: String = UUID.randomUUID().toString(),
    val keyword: String,
    val enabled: Boolean = true,
    val sealed: Boolean = false
)

data class AppRule(
    val packageName: String,
    val appName: String,
    val dailyLimitMinutes: Int? = null,
    val blocked: Boolean = false,
    val allowedInNightMode: Boolean = false,
    val allowedInProfiles: List<String> = emptyList()
)

data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: ProfileType = ProfileType.CUSTOM,
    val blockedApps: List<String> = emptyList(),
    val blockedKeywords: List<String> = emptyList(),
    val blockShortVideos: Boolean = false,
    val blockAdultSites: Boolean = false,
    val isActive: Boolean = false,
    val triggers: List<ProfileTrigger> = emptyList()
)

data class ProfileTrigger(
    val id: String = UUID.randomUUID().toString(),
    val type: TriggerType,
    val timeStart: String? = null,    // "HH:mm"
    val timeEnd: String? = null,
    val daysOfWeek: List<Int> = emptyList(), // Calendar.MONDAY etc
    val wifiSsid: String? = null,
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val locationRadius: Float = 200f
)

enum class TriggerType {
    TIME_OF_DAY, DAY_OF_WEEK, WIFI_NETWORK, LOCATION
}

data class CoolingOffRequest(
    val id: String = UUID.randomUUID().toString(),
    val restrictionId: String,
    val restrictionType: RestrictionType,
    val requestedAt: Long = System.currentTimeMillis(),
    val maturesAt: Long = System.currentTimeMillis() + 24 * 60 * 60 * 1000,
    val completed: Boolean = false,
    val cancelled: Boolean = false
)

data class UsageRecord(
    val packageName: String,
    val date: String, // yyyy-MM-dd
    val totalMinutes: Long
)

data class YouTubeAllowance(
    val baselineMinutes: Int = 120,
    val currentAllowanceMinutes: Int = 120,
    val usedTodayMinutes: Int = 0,
    val decayStartedAt: Long = System.currentTimeMillis(),
    val floorMinutes: Int = 30
)

data class NightModeConfig(
    val enabled: Boolean = true,
    val startHour: Int = 22,
    val startMinute: Int = 0,
    val endHour: Int = 6,
    val endMinute: Int = 0,
    val windDownMinutes: Int = 30,
    val essentialApps: List<String> = listOf(
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.messaging",
        "com.google.android.apps.messaging",
        "com.google.android.apps.maps",
        "com.android.deskclock",
        "com.google.android.deskclock"
    )
)

data class VowStats(
    val totalReclaimedMinutes: Long = 0,
    val baselineDailyMinutes: Long = 0,
    val measurementStartDate: String = "",
    val daysTracked: Int = 0
)

data class PasswordConfig(
    val passwordHash: String = "",
    val isSet: Boolean = false
)
