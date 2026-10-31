package com.vow.app.ipc

import com.vow.app.data.model.ExceptionPolicy
import com.vow.app.data.model.Restriction
import com.vow.app.data.model.RestrictionType
import org.junit.Assert.*
import org.junit.Test

class LumeBusTest {
    @Test fun duolingoNeeds150SecondsOncePerDay() {
        assertFalse(DuolingoRule.shouldLog(149_999, "", "2026-01-01"))
        assertTrue(DuolingoRule.shouldLog(150_000, "", "2026-01-01"))
        assertFalse(DuolingoRule.shouldLog(300_000, "2026-01-01", "2026-01-01"))
        assertTrue(DuolingoRule.shouldLog(300_000, "2025-12-31", "2026-01-01"))
    }

    @Test fun metricsPayloadShape() {
        assertEquals(
            "{\"reclaimedMinutes\":90,\"youtubeUsedMinutes\":12,\"shortVideoBlockAttempts\":4}",
            LumeBus.metricsPayload(90, 12, 4)
        )
    }

    @Test fun permissionAndActionNames() {
        assertEquals("app.lume.permission.ECOSYSTEM_SYNC", LumeBus.PERMISSION)
        assertEquals("app.kasane.action.LOG_CHECK", LumeBus.ACTION_LOG_CHECK)
        assertEquals("app.kasane.action.ECO_METRICS", LumeBus.ACTION_ECO_METRICS)
        assertEquals("app.lume.action.RULE_EVENT", LumeBus.ACTION_RULE_EVENT)
        assertEquals("exception", LumeBus.RuleKind.EXCEPTION.wire)
    }

    @Test fun statusRowCountsKeptRulesAndSleepLock() {
        val now = 1_700_000_000_000L
        val on = Restriction(type = RestrictionType.NIGHT_MODE)
        val off = Restriction(type = RestrictionType.DNS, enabled = false)
        val row = VowStatusProvider.statusRow(listOf(on, off), 120, now)
        assertEquals(listOf<Any>(120, 1, 2, 1), row.take(4))
        assertEquals(listOf<Any>(0, ""), row.drop(4))
        val planned = ExceptionPolicy.declare(off, "x", now, 6)
        assertEquals(2, VowStatusProvider.statusRow(listOf(on, planned), 0, now)[1])
    }

    @Test fun ruleEventParsing() {
        assertEquals("ren|slip|5", RuleEventParser.encode("ren", "slip", 5))
        assertNull(RuleEventParser.encode("ren", "bogus", 5))
        assertNull(RuleEventParser.encode(null, "kept", 5))
        assertNull(RuleEventParser.encode("a|b", "kept", 5))
    }

    @Test fun sleepSyncMergeValidatesRanges() {
        val cur = com.vow.app.data.model.NightModeConfig()
        val m = SleepSync.merge(cur, true, 22, 30, 7, 0)!!
        assertEquals(22, m.startHour); assertEquals(30, m.startMinute); assertEquals(7, m.endHour)
        assertEquals(cur.windDownMinutes, m.windDownMinutes)
        assertNull(SleepSync.merge(cur, true, 24, 0, 7, 0))
        assertNull(SleepSync.merge(cur, null, 22, 0, 7, 0))
        assertNull(SleepSync.merge(cur, true, 22, 60, 7, 0))
    }
}
