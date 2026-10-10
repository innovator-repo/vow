package com.vow.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.vow.app.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vow_prefs")

class VowPreferences(private val context: Context) {

    companion object {
        private val KEY_RESTRICTIONS = stringPreferencesKey("restrictions")
        private val KEY_KEYWORDS = stringPreferencesKey("blocked_keywords")
        private val KEY_PROFILES = stringPreferencesKey("profiles")
        private val KEY_APP_RULES = stringPreferencesKey("app_rules")
        private val KEY_COOLING_OFF = stringPreferencesKey("cooling_off_requests")
        private val KEY_NIGHT_CONFIG = stringPreferencesKey("night_mode_config")
        private val KEY_YOUTUBE = stringPreferencesKey("youtube_allowance")
        private val KEY_STATS = stringPreferencesKey("vow_stats")
        private val KEY_PASSWORD = stringPreferencesKey("password_hash")
        private val KEY_PASSWORD_SET = booleanPreferencesKey("password_set")
        private val KEY_SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
        private val KEY_BASELINE_DONE = booleanPreferencesKey("baseline_done")
        private val KEY_FIRST_LAUNCH = longPreferencesKey("first_launch")
        private val KEY_ADULT_DOMAINS = stringPreferencesKey("adult_domains_extra")
        private val KEY_DEVICE_OWNER = booleanPreferencesKey("is_device_owner")
        private val KEY_PENDING_CHANGES = stringPreferencesKey("pending_changes")

        private inline fun <T> safe(fallback: T, block: () -> T): T =
            try { block() } catch (_: Exception) { fallback }
    }

    // Pending (delayed) loosening edits
    fun getPendingChanges(): Flow<List<PendingChange>> = context.dataStore.data.map { prefs ->
        safe(emptyList()) {
            val arr = JSONArray(prefs[KEY_PENDING_CHANGES] ?: "[]")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PendingChange(
                    id = o.getString("id"),
                    kind = ChangeKind.valueOf(o.getString("kind")),
                    key = o.getString("key"),
                    description = o.getString("description"),
                    baseJson = o.optString("baseJson").takeIf { o.has("baseJson") && !o.isNull("baseJson") },
                    newJson = o.optString("newJson").takeIf { o.has("newJson") && !o.isNull("newJson") },
                    requestedAt = o.getLong("requestedAt"),
                    maturesAt = o.getLong("maturesAt"),
                    applied = o.getBoolean("applied"),
                    cancelled = o.getBoolean("cancelled")
                )
            }
        }
    }

    suspend fun savePendingChanges(list: List<PendingChange>) {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id); put("kind", c.kind.name); put("key", c.key)
                put("description", c.description)
                put("baseJson", c.baseJson ?: JSONObject.NULL)
                put("newJson", c.newJson ?: JSONObject.NULL)
                put("requestedAt", c.requestedAt); put("maturesAt", c.maturesAt)
                put("applied", c.applied); put("cancelled", c.cancelled)
            })
        }
        context.dataStore.edit { it[KEY_PENDING_CHANGES] = arr.toString() }
    }

    // Single-item codecs used for pending-change snapshots
    fun encodeAppRule(r: AppRule): String = JSONArray(appRulesToJson(listOf(r))).getJSONObject(0).toString()
    fun decodeAppRule(json: String): AppRule = parseAppRules("[$json]").first()
    fun encodeKeyword(k: BlockedKeyword): String = JSONArray(keywordsToJson(listOf(k))).getJSONObject(0).toString()
    fun decodeKeyword(json: String): BlockedKeyword = parseKeywords("[$json]").first()
    fun encodeNightConfig(c: NightModeConfig): String = nightConfigToJson(c)
    fun decodeNightConfig(json: String): NightModeConfig = parseNightConfig(json)

    val isSetupComplete: Flow<Boolean> = context.dataStore.data.map { it[KEY_SETUP_COMPLETE] ?: false }
    val isBaselineDone: Flow<Boolean> = context.dataStore.data.map { it[KEY_BASELINE_DONE] ?: false }
    val isDeviceOwner: Flow<Boolean> = context.dataStore.data.map { it[KEY_DEVICE_OWNER] ?: false }

    suspend fun setSetupComplete(done: Boolean) {
        context.dataStore.edit { it[KEY_SETUP_COMPLETE] = done }
    }

    suspend fun setBaselineDone(done: Boolean) {
        context.dataStore.edit { it[KEY_BASELINE_DONE] = done }
    }

    suspend fun setDeviceOwner(isOwner: Boolean) {
        context.dataStore.edit { it[KEY_DEVICE_OWNER] = isOwner }
    }

    // Password
    suspend fun setPassword(hash: String) {
        context.dataStore.edit {
            it[KEY_PASSWORD] = hash
            it[KEY_PASSWORD_SET] = true
        }
    }

    suspend fun getPasswordHash(): String {
        return context.dataStore.data.first()[KEY_PASSWORD] ?: ""
    }

    val isPasswordSet: Flow<Boolean> = context.dataStore.data.map { it[KEY_PASSWORD_SET] ?: false }

    // Restrictions
    fun getRestrictions(): Flow<List<Restriction>> = context.dataStore.data.map { prefs ->
        safe(emptyList()) { parseRestrictions(prefs[KEY_RESTRICTIONS] ?: "[]") }
    }

    suspend fun saveRestrictions(list: List<Restriction>) {
        context.dataStore.edit { it[KEY_RESTRICTIONS] = restrictionsToJson(list) }
    }

    // Blocked Keywords
    fun getBlockedKeywords(): Flow<List<BlockedKeyword>> = context.dataStore.data.map { prefs ->
        safe(emptyList()) { parseKeywords(prefs[KEY_KEYWORDS] ?: "[]") }
    }

    suspend fun saveBlockedKeywords(list: List<BlockedKeyword>) {
        context.dataStore.edit { it[KEY_KEYWORDS] = keywordsToJson(list) }
    }

    // Profiles
    fun getProfiles(): Flow<List<Profile>> = context.dataStore.data.map { prefs ->
        safe(emptyList()) { parseProfiles(prefs[KEY_PROFILES] ?: "[]") }
    }

    suspend fun saveProfiles(list: List<Profile>) {
        context.dataStore.edit { it[KEY_PROFILES] = profilesToJson(list) }
    }

    // App Rules
    fun getAppRules(): Flow<List<AppRule>> = context.dataStore.data.map { prefs ->
        safe(emptyList()) { parseAppRules(prefs[KEY_APP_RULES] ?: "[]") }
    }

    suspend fun saveAppRules(list: List<AppRule>) {
        context.dataStore.edit { it[KEY_APP_RULES] = appRulesToJson(list) }
    }

    // Cooling Off
    fun getCoolingOffRequests(): Flow<List<CoolingOffRequest>> = context.dataStore.data.map { prefs ->
        safe(emptyList()) { parseCoolingOff(prefs[KEY_COOLING_OFF] ?: "[]") }
    }

    suspend fun saveCoolingOffRequests(list: List<CoolingOffRequest>) {
        context.dataStore.edit { it[KEY_COOLING_OFF] = coolingOffToJson(list) }
    }

    // Night config
    fun getNightConfig(): Flow<NightModeConfig> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_NIGHT_CONFIG]
        if (json != null) safe(NightModeConfig()) { parseNightConfig(json) } else NightModeConfig()
    }

    suspend fun saveNightConfig(config: NightModeConfig) {
        context.dataStore.edit { it[KEY_NIGHT_CONFIG] = nightConfigToJson(config) }
    }

    // YouTube Allowance
    fun getYouTubeAllowance(): Flow<YouTubeAllowance> = getStoredYouTubeAllowance().map { it ?: YouTubeAllowance() }

    /** Null until an allowance has been saved, so the decay start can be fixed on first use. */
    fun getStoredYouTubeAllowance(): Flow<YouTubeAllowance?> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_YOUTUBE]
        if (json != null) safe(YouTubeAllowance()) { parseYouTubeAllowance(json) } else null
    }

    suspend fun saveYouTubeAllowance(allowance: YouTubeAllowance) {
        context.dataStore.edit { it[KEY_YOUTUBE] = youtubeToJson(allowance) }
    }

    // Stats
    fun getStats(): Flow<VowStats> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_STATS]
        if (json != null) safe(VowStats()) { parseStats(json) } else VowStats()
    }

    suspend fun saveStats(stats: VowStats) {
        context.dataStore.edit { it[KEY_STATS] = statsToJson(stats) }
    }

    // JSON serialization helpers
    private fun restrictionsToJson(list: List<Restriction>): String {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("type", r.type.name)
                put("enabled", r.enabled)
                put("sealState", r.sealState.name)
                put("sealRequestedAt", r.sealRequestedAt ?: -1L)
                put("sealConfirmedAt", r.sealConfirmedAt ?: -1L)
                put("reason", r.reason)
                put("ifThenPlan", r.ifThenPlan)
                put("createdAt", r.createdAt)
            })
        }
        return arr.toString()
    }

    private fun parseRestrictions(json: String): List<Restriction> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Restriction(
                id = o.getString("id"),
                type = RestrictionType.valueOf(o.getString("type")),
                enabled = o.getBoolean("enabled"),
                sealState = SealState.valueOf(o.getString("sealState")),
                sealRequestedAt = o.getLong("sealRequestedAt").takeIf { it >= 0 },
                sealConfirmedAt = o.getLong("sealConfirmedAt").takeIf { it >= 0 },
                reason = o.optString("reason", ""),
                ifThenPlan = o.optString("ifThenPlan", ""),
                createdAt = o.getLong("createdAt")
            )
        }
    }

    private fun keywordsToJson(list: List<BlockedKeyword>): String {
        val arr = JSONArray()
        list.forEach { k ->
            arr.put(JSONObject().apply {
                put("id", k.id)
                put("keyword", k.keyword)
                put("enabled", k.enabled)
                put("sealed", k.sealed)
            })
        }
        return arr.toString()
    }

    private fun parseKeywords(json: String): List<BlockedKeyword> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            BlockedKeyword(
                id = o.getString("id"),
                keyword = o.getString("keyword"),
                enabled = o.getBoolean("enabled"),
                sealed = o.optBoolean("sealed", false)
            )
        }
    }

    private fun profilesToJson(list: List<Profile>): String {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("type", p.type.name)
                put("blockedApps", JSONArray(p.blockedApps))
                put("blockedKeywords", JSONArray(p.blockedKeywords))
                put("blockShortVideos", p.blockShortVideos)
                put("blockAdultSites", p.blockAdultSites)
                put("isActive", p.isActive)
            })
        }
        return arr.toString()
    }

    private fun parseProfiles(json: String): List<Profile> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val apps = o.getJSONArray("blockedApps")
            val kws = o.getJSONArray("blockedKeywords")
            Profile(
                id = o.getString("id"),
                name = o.getString("name"),
                type = ProfileType.valueOf(o.getString("type")),
                blockedApps = (0 until apps.length()).map { apps.getString(it) },
                blockedKeywords = (0 until kws.length()).map { kws.getString(it) },
                blockShortVideos = o.getBoolean("blockShortVideos"),
                blockAdultSites = o.getBoolean("blockAdultSites"),
                isActive = o.getBoolean("isActive")
            )
        }
    }

    private fun appRulesToJson(list: List<AppRule>): String {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(JSONObject().apply {
                put("packageName", a.packageName)
                put("appName", a.appName)
                put("dailyLimitMinutes", a.dailyLimitMinutes ?: -1)
                put("blocked", a.blocked)
                put("allowedInNightMode", a.allowedInNightMode)
                put("allowedInProfiles", JSONArray(a.allowedInProfiles))
            })
        }
        return arr.toString()
    }

    private fun parseAppRules(json: String): List<AppRule> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val profs = o.getJSONArray("allowedInProfiles")
            AppRule(
                packageName = o.getString("packageName"),
                appName = o.getString("appName"),
                dailyLimitMinutes = o.getInt("dailyLimitMinutes").takeIf { it >= 0 },
                blocked = o.getBoolean("blocked"),
                allowedInNightMode = o.getBoolean("allowedInNightMode"),
                allowedInProfiles = (0 until profs.length()).map { profs.getString(it) }
            )
        }
    }

    private fun coolingOffToJson(list: List<CoolingOffRequest>): String {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("restrictionId", c.restrictionId)
                put("restrictionType", c.restrictionType.name)
                put("requestedAt", c.requestedAt)
                put("maturesAt", c.maturesAt)
                put("completed", c.completed)
                put("cancelled", c.cancelled)
            })
        }
        return arr.toString()
    }

    private fun parseCoolingOff(json: String): List<CoolingOffRequest> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CoolingOffRequest(
                id = o.getString("id"),
                restrictionId = o.getString("restrictionId"),
                restrictionType = RestrictionType.valueOf(o.getString("restrictionType")),
                requestedAt = o.getLong("requestedAt"),
                maturesAt = o.getLong("maturesAt"),
                completed = o.getBoolean("completed"),
                cancelled = o.getBoolean("cancelled")
            )
        }
    }

    private fun nightConfigToJson(c: NightModeConfig): String = JSONObject().apply {
        put("enabled", c.enabled)
        put("startHour", c.startHour)
        put("startMinute", c.startMinute)
        put("endHour", c.endHour)
        put("endMinute", c.endMinute)
        put("windDownMinutes", c.windDownMinutes)
        put("essentialApps", JSONArray(c.essentialApps))
    }.toString()

    private fun parseNightConfig(json: String): NightModeConfig {
        val o = JSONObject(json)
        val apps = o.getJSONArray("essentialApps")
        return NightModeConfig(
            enabled = o.getBoolean("enabled"),
            startHour = o.getInt("startHour"),
            startMinute = o.getInt("startMinute"),
            endHour = o.getInt("endHour"),
            endMinute = o.getInt("endMinute"),
            windDownMinutes = o.getInt("windDownMinutes"),
            essentialApps = (0 until apps.length()).map { apps.getString(it) }
        )
    }

    private fun youtubeToJson(y: YouTubeAllowance): String = JSONObject().apply {
        put("baselineMinutes", y.baselineMinutes)
        put("currentAllowanceMinutes", y.currentAllowanceMinutes)
        put("usedTodayMinutes", y.usedTodayMinutes)
        put("decayStartedAt", y.decayStartedAt)
        put("floorMinutes", y.floorMinutes)
        put("usedDate", y.usedDate)
    }.toString()

    private fun parseYouTubeAllowance(json: String): YouTubeAllowance {
        val o = JSONObject(json)
        return YouTubeAllowance(
            baselineMinutes = o.getInt("baselineMinutes"),
            currentAllowanceMinutes = o.getInt("currentAllowanceMinutes"),
            usedTodayMinutes = o.getInt("usedTodayMinutes"),
            decayStartedAt = o.getLong("decayStartedAt"),
            floorMinutes = o.getInt("floorMinutes"),
            usedDate = o.optString("usedDate", "")
        )
    }

    private fun statsToJson(s: VowStats): String = JSONObject().apply {
        put("totalReclaimedMinutes", s.totalReclaimedMinutes)
        put("baselineDailyMinutes", s.baselineDailyMinutes)
        put("measurementStartDate", s.measurementStartDate)
        put("daysTracked", s.daysTracked)
        put("lastProcessedDate", s.lastProcessedDate)
    }.toString()

    private fun parseStats(json: String): VowStats {
        val o = JSONObject(json)
        return VowStats(
            totalReclaimedMinutes = o.getLong("totalReclaimedMinutes"),
            baselineDailyMinutes = o.getLong("baselineDailyMinutes"),
            measurementStartDate = o.getString("measurementStartDate"),
            daysTracked = o.getInt("daysTracked"),
            lastProcessedDate = o.optString("lastProcessedDate", "")
        )
    }
}
