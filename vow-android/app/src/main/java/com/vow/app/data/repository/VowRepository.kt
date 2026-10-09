package com.vow.app.data.repository

import android.content.Context
import com.vow.app.data.model.*
import com.vow.app.data.prefs.VowPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.util.Calendar

class VowRepository(context: Context) {

    val prefs = VowPreferences(context)

    // Password management
    fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun verifyPassword(input: String): Boolean {
        val stored = prefs.getPasswordHash()
        if (stored.isEmpty()) return true
        return hashPassword(input) == stored
    }

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

        val coolingHours = when (restriction.type) {
            RestrictionType.DNS, RestrictionType.UNINSTALL -> 48
            RestrictionType.ADULT_SITES -> 72
            else -> 24
        }

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

    suspend fun completeLoosening(requestId: String): Boolean {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        // Only allow between 8am and 8pm
        if (hour < 8 || hour >= 20) return false

        val requests = prefs.getCoolingOffRequests().first().toMutableList()
        val idx = requests.indexOfFirst { it.id == requestId }
        if (idx < 0) return false

        val request = requests[idx]
        if (request.cancelled || request.completed) return false
        if (System.currentTimeMillis() < request.maturesAt) return false

        requests[idx] = request.copy(completed = true)
        prefs.saveCoolingOffRequests(requests)

        // Disable the restriction
        val restrictions = prefs.getRestrictions().first().toMutableList()
        val rIdx = restrictions.indexOfFirst { it.id == request.restrictionId }
        if (rIdx >= 0) {
            restrictions[rIdx] = restrictions[rIdx].copy(enabled = false)
            prefs.saveRestrictions(restrictions)
        }
        return true
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

        val elapsed = System.currentTimeMillis() - (r.sealRequestedAt ?: return false)
        if (elapsed < 24 * 60 * 60 * 1000L) return false // Must wait 24h

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
        val current = prefs.getYouTubeAllowance().first()
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
        val windDownStart = startMinutes - config.windDownMinutes

        return currentMinutes in windDownStart until startMinutes
    }

    // Stats
    suspend fun updateReclaimedTime(savedMinutes: Long) {
        val stats = prefs.getStats().first()
        prefs.saveStats(stats.copy(
            totalReclaimedMinutes = stats.totalReclaimedMinutes + savedMinutes
        ))
    }
}
