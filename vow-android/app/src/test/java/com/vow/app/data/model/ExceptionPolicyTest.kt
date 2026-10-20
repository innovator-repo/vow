package com.vow.app.data.model

import org.junit.Assert.*
import org.junit.Test

class ExceptionPolicyTest {
    private val rule = Restriction(type = RestrictionType.SHORT_VIDEO)
    private val hour = 3_600_000L

    @Test fun coldHoursAreBeforeEight() {
        assertTrue(ExceptionPolicy.isColdHour(7))
        assertFalse(ExceptionPolicy.isColdHour(8))
        assertFalse(ExceptionPolicy.isColdHour(23))
    }

    @Test fun beforeEightStartsImmediately() {
        val now = 1_700_000_000_000L
        assertEquals(now, ExceptionPolicy.effectiveAt(now, 6))
        val r = ExceptionPolicy.declare(rule, " family dinner ", now, 6)
        assertEquals(ExceptionState.PLANNED, r.exceptionState)
        assertEquals("family dinner", r.exceptionNote)
        assertEquals(ExceptionState.PLANNED, ExceptionPolicy.activeState(r, now))
    }

    @Test fun otherHoursNeedTwentyFourHoursCooling() {
        val now = 1_700_000_000_000L
        val r = ExceptionPolicy.declare(rule, "trip", now, 23)
        assertEquals(now + 24 * hour, r.exceptionEffectiveAt)
        assertTrue(ExceptionPolicy.isPending(r, now))
        assertEquals(ExceptionState.NONE, ExceptionPolicy.activeState(r, now + hour))
        assertEquals(ExceptionState.PLANNED, ExceptionPolicy.activeState(r, now + 24 * hour))
    }

    @Test fun exceptionCoversOnlyItsOwnDay() {
        val now = 1_700_000_000_000L
        val r = ExceptionPolicy.declare(rule, "x", now, 6)
        assertEquals(ExceptionState.NONE, ExceptionPolicy.activeState(r, now + 48 * hour))
    }

    @Test fun clearRemovesException() {
        val r = ExceptionPolicy.clear(ExceptionPolicy.declare(rule, "x", 1L, 6))
        assertEquals(ExceptionState.NONE, r.exceptionState)
        assertNull(r.exceptionEffectiveAt)
    }
}
