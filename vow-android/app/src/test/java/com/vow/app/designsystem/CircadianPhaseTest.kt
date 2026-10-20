package com.vow.app.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class CircadianPhaseTest {
    @Test fun phasesFollowTheSpec() {
        assertEquals(CircadianPhase.MORNING, CircadianPhase.at(6))
        assertEquals(CircadianPhase.MORNING, CircadianPhase.at(11))
        assertEquals(CircadianPhase.DAY, CircadianPhase.at(12))
        assertEquals(CircadianPhase.DAY, CircadianPhase.at(19))
        assertEquals(CircadianPhase.EVENING, CircadianPhase.at(20))
        assertEquals(CircadianPhase.EVENING, CircadianPhase.at(21))
        assertEquals(CircadianPhase.SLEEP, CircadianPhase.at(22))
        assertEquals(CircadianPhase.SLEEP, CircadianPhase.at(3))
    }

    @Test fun sleepSurfaceIsAlwaysObsidian() {
        assertEquals(LumeSurfaces.Obsidian, LumeSurfaces.forPhase(CircadianPhase.SLEEP, false))
        assertEquals(LumeSurfaces.WarmGraphite, LumeSurfaces.forPhase(CircadianPhase.EVENING, false))
    }
}
