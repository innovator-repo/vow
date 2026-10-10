package com.vow.app.data.repository

import android.content.Context
import android.provider.Settings
import com.vow.app.data.model.*
import com.vow.app.data.prefs.VowPreferences
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

sealed class EditResult {
    object Applied : EditResult()
    data class Delayed(val maturesAt: Long) : EditResult()
    data class Refused(val reason: String) : EditResult()
}

enum class LooseningResult { DONE, NOT_YET, OUTSIDE_HOURS, EXPIRED, CLOCK_UNTRUSTED, NOT_FOUND }

class VowRepository(context: Context) {

    private val appContext = context.applicationContext
    val prefs = VowPreferences(context)

    // Password: PBKDF2 with a random salt, stored as "pbkdf2$iterations$salt$hash".
    fun hashPassword(password: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return "pbkdf2\$$PBKDF2_ITERATIONS\$${salt.hex()}\$${pbkdf2(password, salt, PBKDF2_ITERATIONS).hex()}"
    }

    suspend fun verifyPassword(input: String): Boolean {
        val stored = prefs.getPasswordHash()
        if (stored.isEmpty()) return true
        val parts = stored.split('$')
        if (parts.size == 4 && parts[0] == "pbkdf2") {
            val expected = parts[3].unhex()
            val actual = pbkdf2(input, parts[2].unhex(), parts[1].toInt())
            return MessageDigest.isEqual(expected, actual)
        }
        // Legacy unsalted SHA-256
        val legacy = MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).hex()
        return MessageDigest.isEqual(legacy.toByteArray(), stored.toByteArray())
    }

    private fun pbkdf2(password: String, salt: ByteArray, iterations: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, iterations, 256)).encoded

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
    private fun String.unhex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    /** Waits only count when the phone sets its own clock; otherwise moving the clock skips them. */
    fun isClockTrusted(): Boolean =
        Settings.Global.getInt(appContext.contentResolver, Settings.Global.AUTO_TIME, 0) == 1

    // Restriction management
    suspend fun getRestriction(type: RestrictionType): Restriction? {
        return prefs.getRestrictions().first().find { it.type == type }
    }

    suspend fun enableRestriction(type: RestrictionType, reason: String, ifThenPlan: String) {
        val list = prefs.getRestrictions().first().toMutableList()
        val existing = list.indexOfFirst { it.type == type }
        val restriction = Restriction(
            type = type,
            enabled = true,
            reason = reason,
            ifThenPlan = ifThenPlan
        )
        if (existing >= 0) {
            val old = list[existing]
            if (old.sealState == SealState.SEALED) return // Cannot modify sealed
            list[existing] = restriction.copy(id = old.id)
        } else {
            list.add(restriction)
        }
        prefs.saveRestrictions(list)
    }

    suspend fun requestLoosening(restrictionId: String): CoolingOffRequest? {
        val restrictions = prefs.getRestrictions().first()
        val restriction = restrictions.find { it.id == restrictionId } ?: return null
        if (restriction.sealState == SealState.SEALED) return null
        if (!restriction.enabled) return null

        val existing = prefs.getCoolingOffRequests().first()
            .find { it.restrictionId == restrictionId && !it.completed && !it.cancelled && !isExpired(it) }
        if (existing != null) return existing

        val coolingHours = coolingHours(restriction.type)

        val request = CoolingOffRequest(
            restrictionId = restrictionId,
            restrictionType = restriction.type,
            maturesAt = System.currentTimeMillis() + coolingHours * 60 * 60 * 1000L
        )

        val requests = prefs.getCoolingOffRequests().first().toMutableList()
        requests.add(request)
        prefs.saveCoolingOffRequests(requests)
        return request
    }

    /** A matured request must be used within a day, so it can't be banked as an instant unlock. */
    fun isExpired(request: CoolingOffRequest): Boolean =
        System.currentTimeMillis() > request.maturesAt + GRACE_MS

    suspend fun completeLoosening(requestId: String): LooseningResult {
        val requests = prefs.getCoolingOffRequests().first().toMutableList()
        val idx = requests.indexOfFirst { it.id == requestId }
        if (idx < 0) return LooseningResult.NOT_FOUND

        val request = requests[idx]
        if (request.cancelled || request.completed) return LooseningResult.NOT_FOUND
        if (!isClockTrusted()) return LooseningResult.CLOCK_UNTRUSTED
        val now = System.currentTimeMillis()
        if (now < request.maturesAt || now < request.requestedAt) return LooseningResult.NOT_YET
        if (isExpired(request)) return LooseningResult.EXPIRED

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour < 8 || hour >= 20) return LooseningResult.OUTSIDE_HOURS

        for (i in requests.indices) {
            val r = requests[i]
            if (r.restrictionId == request.restrictionId && !r.completed && !r.cancelled) {
                requests[i] = if (i == idx) r.copy(completed = true) else r.copy(cancelled = true)
            }
        }
        prefs.saveCoolingOffRequests(requests)

        // Disable the restriction
        val restrictions = prefs.getRestrictions().first().toMutableList()
        val rIdx = restrictions.indexOfFirst { it.id == request.restrictionId }
        if (rIdx >= 0) {
            if (restrictions[rIdx].sealState == SealState.SEALED) return LooseningResult.NOT_FOUND
            restrictions[rIdx] = restrictions[rIdx].copy(enabled = false)
            prefs.saveRestrictions(restrictions)
        }
        return LooseningResult.DONE
    }

    suspend fun cancelLoosening(requestId: String) {
        val requests = prefs.getCoolingOffRequests().first().toMutableList()
        val idx = requests.indexOfFirst { it.id == requestId }
        if (idx >= 0) {
            requests[idx] = requests[idx].copy(cancelled = true)
            prefs.saveCoolingOffRequests(requests)
        }
    }

    // Lifetime seal: step 1 - request
    suspend fun requestSeal(restrictionId: String): Boolean {
        val restrictions = prefs.getRestrictions().first().toMutableList()
        val idx = restrictions.indexOfFirst { it.id == restrictionId }
        if (idx < 0) return false
        val r = restrictions[idx]
        if (r.sealState != SealState.UNSEALED) return false

        restrictions[idx] = r.copy(
            sealState = SealState.PENDING_SEAL,
            sealRequestedAt = System.currentTimeMillis()
        )
        prefs.saveRestrictions(restrictions)
        return true
    }

    // Lifetime seal: step 2 - confirm after 24h with typed sentence
    suspend fun confirmSeal(restrictionId: String): Boolean {
        val restrictions = prefs.getRestrictions().first().toMutableList()
        val idx = restrictions.indexOfFirst { it.id == restrictionId }
        if (idx < 0) return false
        val r = restrictions[idx]
        if (r.sealState != SealState.PENDING_SEAL) return false

        if (!isClockTrusted()) return false
        val elapsed = System.currentTimeMillis() - (r.sealRequestedAt ?: return false)
        if (elapsed < DAY_MS) return false

        restrictions[idx] = r.copy(
            sealState = SealState.SEALED,
            sealConfirmedAt = System.currentTimeMillis(),
            enabled = true
        )
        prefs.saveRestrictions(restrictions)
        return true
    }

    fun isRestrictionSealed(restriction: Restriction): Boolean {
        return restriction.sealState == SealState.SEALED
    }

    // YouTube allowance auto-decay
    suspend fun computeYouTubeAllowance(): YouTubeAllowance {
        val current = prefs.getStoredYouTubeAllowance().first()
            ?: YouTubeAllowance().also { prefs.saveYouTubeAllowance(it) }
        val daysSinceStart = ((System.currentTimeMillis() - current.decayStartedAt) / (24 * 60 * 60 * 1000L)).toInt()
        val decayFactor = Math.pow(0.97, (daysSinceStart / 7).toDouble()) // 3% per week
        val newAllowance = (current.baselineMinutes * decayFactor).toInt()
            .coerceAtLeast(current.floorMinutes)

        // Lower allowance after overuse or in evening
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val eveningPenalty = if (hour >= 20) 0.7 else if (hour >= 18) 0.85 else 1.0

        val finalAllowance = (newAllowance * eveningPenalty).toInt()
            .coerceAtLeast(current.floorMinutes)

        return current.copy(currentAllowanceMinutes = finalAllowance)
    }

    // Night mode check
    suspend fun isNightModeActive(): Boolean {
        val config = prefs.getNightConfig().first()
        if (!config.enabled) return false
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val currentMinutes = hour * 60 + minute
        val startMinutes = config.startHour * 60 + config.startMinute
        val endMinutes = config.endHour * 60 + config.endMinute

        return if (startMinutes > endMinutes) {
            // Crosses midnight (e.g. 22:00 - 06:00)
            currentMinutes >= startMinutes || currentMinutes < endMinutes
        } else {
            currentMinutes in startMinutes until endMinutes
        }
    }

    suspend fun isInWindDown(): Boolean {
        val config = prefs.getNightConfig().first()
        if (!config.enabled) return false
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val currentMinutes = hour * 60 + minute
        val startMinutes = config.startHour * 60 + config.startMinute
        val minutesUntilStart = (startMinutes - currentMinutes + 1440) % 1440
        return minutesUntilStart in 1..config.windDownMinutes
    }

    // Stats
    suspend fun updateReclaimedTime(savedMinutes: Long) {
        val stats = prefs.getStats().first()
        prefs.saveStats(stats.copy(
            totalReclaimedMinutes = stats.totalReclaimedMinutes + savedMinutes
        ))
    }

    // ---- Guarded edits: tighten instantly, loosen after a delay, never when sealed ----

    private suspend fun sealed(type: RestrictionType) =
        prefs.getRestrictions().first().any { it.type == type && it.sealState == SealState.SEALED }

    suspend fun updateNightConfig(new: NightModeConfig): EditResult {
        val old = prefs.getNightConfig().first()
        if (isTightening(old, new)) {
            prefs.saveNightConfig(new)
            return EditResult.Applied
        }
        if (sealed(RestrictionType.NIGHT_MODE)) return EditResult.Refused("Night mode is sealed.")
        return queue(
            ChangeKind.NIGHT_CONFIG, "night",
            if (!new.enabled) "Turn off night mode" else "Change night schedule to %02d:%02d\u2013%02d:%02d"
                .format(new.startHour, new.startMinute, new.endHour, new.endMinute),
            prefs.encodeNightConfig(old), prefs.encodeNightConfig(new)
        )
    }

    /** [new] = null removes the rule. */
    suspend fun updateAppRule(packageName: String, new: AppRule?): EditResult {
        val rules = prefs.getAppRules().first()
        val old = rules.find { it.packageName == packageName }
        if (isTightening(old, new)) {
            prefs.saveAppRules(rules.filter { it.packageName != packageName } + listOfNotNull(new))
            return EditResult.Applied
        }
        if (sealed(RestrictionType.APP_LIMIT)) return EditResult.Refused("App limits are sealed.")
        if (new?.allowedInNightMode == true && old?.allowedInNightMode != true && sealed(RestrictionType.NIGHT_MODE)) {
            return EditResult.Refused("Night mode is sealed.")
        }
        val name = (new ?: old)?.appName ?: packageName
        val what = when {
            new == null -> "Remove the rule for $name"
            old?.blocked == true && !new.blocked -> "Unblock $name"
            new.allowedInNightMode && old?.allowedInNightMode != true -> "Allow $name at night"
            else -> "Raise $name's limit to ${new.dailyLimitMinutes?.let { "$it min" } ?: "no limit"}"
        }
        return queue(ChangeKind.APP_RULE, packageName, what, old?.let(prefs::encodeAppRule), new?.let(prefs::encodeAppRule))
    }

    /** [new] = null deletes the keyword. */
    suspend fun updateKeyword(id: String, new: BlockedKeyword?): EditResult {
        val list = prefs.getBlockedKeywords().first()
        val old = list.find { it.id == id }
        val tightening = old == null ||
            (new != null && new.keyword == old.keyword && (new.enabled || !old.enabled))
        if (tightening) {
            prefs.saveBlockedKeywords(
                if (old == null) list + listOfNotNull(new) else list.map { if (it.id == id) new!! else it }
            )
            return EditResult.Applied
        }
        if (sealed(RestrictionType.KEYWORD_BLOCK) || old?.sealed == true) {
            return EditResult.Refused("Keyword blocking is sealed.")
        }
        val what = if (new == null) "Remove \u201C${old!!.keyword}\u201D" else "Turn off \u201C${old!!.keyword}\u201D"
        return queue(ChangeKind.KEYWORD, id, what, prefs.encodeKeyword(old), new?.let(prefs::encodeKeyword))
    }

    private suspend fun queue(kind: ChangeKind, key: String, description: String, base: String?, new: String?): EditResult {
        val now = System.currentTimeMillis()
        val change = PendingChange(
            kind = kind, key = key, description = description,
            baseJson = base, newJson = new, requestedAt = now, maturesAt = now + DAY_MS
        )
        val others = prefs.getPendingChanges().first().map {
            if (it.kind == kind && it.key == key && !it.applied && !it.cancelled) it.copy(cancelled = true) else it
        }
        prefs.savePendingChanges(others + change)
        return EditResult.Delayed(change.maturesAt)
    }

    suspend fun cancelPendingChange(id: String) {
        prefs.savePendingChanges(prefs.getPendingChanges().first().map { if (it.id == id) it.copy(cancelled = true) else it })
    }

    /** Applies matured changes whose target hasn't been edited since; drops stale ones. */
    suspend fun applyMaturedChanges() {
        if (!isClockTrusted()) return
        val now = System.currentTimeMillis()
        val changes = prefs.getPendingChanges().first()
        if (changes.none { !it.applied && !it.cancelled && now >= it.maturesAt }) return

        val updated = changes.map { c ->
            if (c.applied || c.cancelled || now < c.maturesAt || now < c.requestedAt) return@map c
            when (c.kind) {
                ChangeKind.NIGHT_CONFIG -> {
                    val current = prefs.encodeNightConfig(prefs.getNightConfig().first())
                    if (sealed(RestrictionType.NIGHT_MODE) || current != c.baseJson) return@map c.copy(cancelled = true)
                    prefs.saveNightConfig(prefs.decodeNightConfig(c.newJson!!))
                }
                ChangeKind.APP_RULE -> {
                    val rules = prefs.getAppRules().first()
                    val current = rules.find { it.packageName == c.key }?.let(prefs::encodeAppRule)
                    if (sealed(RestrictionType.APP_LIMIT) || current != c.baseJson) return@map c.copy(cancelled = true)
                    prefs.saveAppRules(rules.filter { it.packageName != c.key } + listOfNotNull(c.newJson?.let(prefs::decodeAppRule)))
                }
                ChangeKind.KEYWORD -> {
                    val list = prefs.getBlockedKeywords().first()
                    val current = list.find { it.id == c.key }?.let(prefs::encodeKeyword)
                    if (sealed(RestrictionType.KEYWORD_BLOCK) || current != c.baseJson) return@map c.copy(cancelled = true)
                    val replacement = c.newJson?.let(prefs::decodeKeyword)
                    prefs.saveBlockedKeywords(list.mapNotNull { if (it.id == c.key) replacement else it })
                }
            }
            c.copy(applied = true)
        }
        prefs.savePendingChanges(updated.filter { now - it.requestedAt < 30 * DAY_MS || (!it.applied && !it.cancelled) })
    }

    fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val GRACE_MS = DAY_MS
        private const val PBKDF2_ITERATIONS = 120_000

        fun coolingHours(type: RestrictionType): Int = when (type) {
            RestrictionType.DNS, RestrictionType.UNINSTALL -> 48
            RestrictionType.ADULT_SITES -> 72
            else -> 24
        }

        fun isTightening(old: NightModeConfig, new: NightModeConfig): Boolean {
            if (!old.enabled) return true
            if (!new.enabled) return false
            val oldCover = coverage(old)
            val newCover = coverage(new)
            if ((0 until 1440).any { oldCover[it] && !newCover[it] }) return false
            if (!old.essentialApps.containsAll(new.essentialApps)) return false
            return new.windDownMinutes >= old.windDownMinutes
        }

        private fun coverage(c: NightModeConfig): BooleanArray {
            val start = c.startHour * 60 + c.startMinute
            val end = c.endHour * 60 + c.endMinute
            return BooleanArray(1440) { m -> if (start > end) m >= start || m < end else m in start until end }
        }

        fun isTightening(old: AppRule?, new: AppRule?): Boolean {
            if (old == null) return true
            if (new == null) return false
            if (old.blocked && !new.blocked) return false
            if (new.allowedInNightMode && !old.allowedInNightMode) return false
            if (new.blocked) return true
            val o = old.dailyLimitMinutes ?: return true
            val n = new.dailyLimitMinutes ?: return false
            return n <= o
        }
    }
}
