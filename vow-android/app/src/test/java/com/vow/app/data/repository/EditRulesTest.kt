package com.vow.app.data.repository

import com.vow.app.data.model.AppRule
import com.vow.app.data.model.NightModeConfig
import com.vow.app.data.model.RestrictionType
import org.junit.Assert.*
import org.junit.Test

class EditRulesTest {
    private val night = NightModeConfig(startHour = 22, startMinute = 0, endHour = 6, endMinute = 0, windDownMinutes = 30)

    @Test fun nightTighteningIsInstant() {
        assertTrue(VowRepository.isTightening(night, night.copy(startHour = 21)))
        assertTrue(VowRepository.isTightening(night, night.copy(endHour = 7)))
        assertTrue(VowRepository.isTightening(night, night.copy(windDownMinutes = 60)))
        assertTrue(VowRepository.isTightening(night.copy(enabled = false), night.copy(startHour = 3)))
    }

    @Test fun nightLooseningIsDelayed() {
        assertFalse(VowRepository.isTightening(night, night.copy(enabled = false)))
        assertFalse(VowRepository.isTightening(night, night.copy(startHour = 23)))
        assertFalse(VowRepository.isTightening(night, night.copy(endHour = 5)))
        assertFalse(VowRepository.isTightening(night, night.copy(windDownMinutes = 15)))
        assertFalse(VowRepository.isTightening(night, night.copy(essentialApps = night.essentialApps + "com.instagram.android")))
        // Moving the whole window is a loosening even if it's the same length
        assertFalse(VowRepository.isTightening(night, night.copy(startHour = 23, endHour = 7)))
    }

    private val rule = AppRule("com.instagram.android", "Instagram", dailyLimitMinutes = 30)

    @Test fun appRuleTightening() {
        assertTrue(VowRepository.isTightening(null, rule))
        assertTrue(VowRepository.isTightening(rule, rule.copy(dailyLimitMinutes = 15)))
        assertTrue(VowRepository.isTightening(rule, rule.copy(blocked = true)))
        assertTrue(VowRepository.isTightening(rule.copy(dailyLimitMinutes = null), rule))
    }

    @Test fun appRuleLoosening() {
        assertFalse(VowRepository.isTightening(rule, null))
        assertFalse(VowRepository.isTightening(rule, rule.copy(dailyLimitMinutes = 45)))
        assertFalse(VowRepository.isTightening(rule, rule.copy(dailyLimitMinutes = null)))
        assertFalse(VowRepository.isTightening(rule.copy(blocked = true), rule))
        assertFalse(VowRepository.isTightening(rule, rule.copy(allowedInNightMode = true)))
    }

    @Test fun coolingHours() {
        assertEquals(72, VowRepository.coolingHours(RestrictionType.ADULT_SITES))
        assertEquals(48, VowRepository.coolingHours(RestrictionType.UNINSTALL))
        assertEquals(24, VowRepository.coolingHours(RestrictionType.SHORT_VIDEO))
    }

    @Test fun youTubeAllowanceStaysWithinAnHour() {
        val a = com.vow.app.data.model.YouTubeAllowance()
        assertEquals(60, com.vow.app.data.model.YouTubeAllowance.MAX_MINUTES)
        assertTrue(a.baselineMinutes <= 60 && a.currentAllowanceMinutes <= 60)
        assertTrue(a.floorMinutes >= 0)
    }
}
