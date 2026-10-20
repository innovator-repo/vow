package com.vow.app.data.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Cold-state gating for planned exceptions. An exception can only be declared with a calm head:
 * before 08:00 it starts at once; at any other hour it starts after a 24h cooling period.
 * A planned exception never switches enforcement off; it only tells the ecosystem not to count the day as a slip.
 */
object ExceptionPolicy {
    const val COOLING_MS = 24 * 60 * 60 * 1000L
    const val COLD_BEFORE_HOUR = 8

    fun dayKey(millis: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

    fun isColdHour(hourOfDay: Int) = hourOfDay < COLD_BEFORE_HOUR

    /** When a declaration made at [now] starts counting. */
    fun effectiveAt(now: Long, hourOfDay: Int): Long = if (isColdHour(hourOfDay)) now else now + COOLING_MS

    fun hourOf(millis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)

    fun declare(r: Restriction, note: String, now: Long, hourOfDay: Int = hourOf(now)): Restriction {
        val at = effectiveAt(now, hourOfDay)
        return r.copy(
            exceptionState = ExceptionState.PLANNED,
            exceptionNote = note.trim(),
            exceptionEffectiveAt = at,
            exceptionDay = dayKey(at)
        )
    }

    fun clear(r: Restriction): Restriction = r.copy(
        exceptionState = ExceptionState.NONE, exceptionNote = "", exceptionEffectiveAt = null, exceptionDay = ""
    )

    /** PLANNED only once the cooling has passed and only for the day it covers. */
    fun activeState(r: Restriction, now: Long): ExceptionState {
        val at = r.exceptionEffectiveAt
        return if (r.exceptionState == ExceptionState.PLANNED && at != null && now >= at && dayKey(now) == r.exceptionDay)
            ExceptionState.PLANNED else ExceptionState.NONE
    }

    fun isPending(r: Restriction, now: Long): Boolean {
        val at = r.exceptionEffectiveAt
        return r.exceptionState == ExceptionState.PLANNED && at != null && now < at
    }
}
