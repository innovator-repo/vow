package com.vow.app.focus

import com.vow.app.data.model.Profile
import com.vow.app.data.model.Restriction
import com.vow.app.data.model.RestrictionType
import com.vow.app.ipc.VowStatusProvider
import org.junit.Assert.*
import org.junit.Test

class FocusLogicTest {
    private val work = Profile(id = "w", name = "Work")
    private val study = Profile(id = "s", name = "Study")

    @Test fun noProfilesMeansOffAndNoName() {
        assertFalse(FocusLogic.focusOn(emptyList()))
        assertEquals("", FocusLogic.profileName(emptyList()))
        assertEquals(FocusLogic.Toggle.NoProfile, FocusLogic.externalToggle(emptyList()))
    }

    @Test fun activeProfileIsTheFirstSwitchedOn() {
        val list = listOf(work, study.copy(isActive = true))
        assertTrue(FocusLogic.focusOn(list))
        assertEquals("Study", FocusLogic.profileName(list))
    }

    @Test fun nameFallsBackToTheProfileThatWouldStart() {
        assertEquals("Work", FocusLogic.profileName(listOf(work, study)))
    }

    @Test fun externalToggleTurnsOnWithoutUnlock() {
        val plan = FocusLogic.externalToggle(listOf(work, study))
        assertTrue(plan is FocusLogic.Toggle.TurnedOn)
        val after = (plan as FocusLogic.Toggle.TurnedOn).profiles
        assertTrue(after.first { it.id == "w" }.isActive)
        assertFalse(after.first { it.id == "s" }.isActive)
    }

    @Test fun externalToggleNeverTurnsOff() {
        val plan = FocusLogic.externalToggle(listOf(work.copy(isActive = true), study))
        assertEquals(FocusLogic.Toggle.NeedsUnlockToTurnOff, plan)
    }

    @Test fun setProfileChangesOnlyThatProfile() {
        val after = FocusLogic.setProfile(listOf(work, study), "s", true)
        assertFalse(after[0].isActive)
        assertTrue(after[1].isActive)
    }

    @Test fun durationFormat() {
        assertEquals("0 m", FocusLogic.formatDuration(-5))
        assertEquals("45 m", FocusLogic.formatDuration(45))
        assertEquals("3 h", FocusLogic.formatDuration(180))
        assertEquals("3 h 20 m", FocusLogic.formatDuration(200))
    }

    @Test fun reclaimedTodayNeedsBaselineAndData() {
        assertNull(FocusLogic.reclaimedToday(0, 30))
        assertNull(FocusLogic.reclaimedToday(120, null))
        assertEquals(90L, FocusLogic.reclaimedToday(120, 30))
        assertEquals(0L, FocusLogic.reclaimedToday(120, 200))
    }

    @Test fun ringFractionStaysInsideBounds() {
        assertEquals(0.02f, FocusLogic.ringFraction(0), 0.0001f)
        assertEquals(0.5f, FocusLogic.ringFraction(60), 0.0001f)
        assertEquals(0.95f, FocusLogic.ringFraction(100_000), 0.0001f)
    }

    @Test fun heroText() {
        assertEquals("Focus is holding", FocusLogic.heroTitle(true, 0))
        assertEquals("Focus is holding", FocusLogic.heroTitle(false, 2))
        assertEquals("Focus is resting", FocusLogic.heroTitle(false, 0))
        assertEquals("Deep work · 3 protections", FocusLogic.heroSubtitle("Deep work", 3))
        assertEquals("1 protection", FocusLogic.heroSubtitle("", 1))
    }

    @Test fun statusRowKeepsOldColumnsAndAddsFocus() {
        assertEquals(
            listOf("reclaimedMinutes", "rulesKept", "rulesTotal", "sleepLock", "focusOn", "profileName"),
            VowStatusProvider.COLUMNS.toList()
        )
        val rules = listOf(Restriction(type = RestrictionType.DNS))
        val on = VowStatusProvider.statusRow(rules, 90, 0L, listOf(work.copy(isActive = true)))
        assertEquals(listOf<Any>(90, 1, 1, 0, 1, "Work"), on)
        val none = VowStatusProvider.statusRow(rules, 90, 0L)
        assertEquals(listOf<Any>(90, 1, 1, 0, 0, ""), none)
    }
}
