package org.chinaquest.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReviewSchedulerTest {
    private val day = LocalDate.of(2026, 9, 28)

    @Test fun exposureAndSpacingLadder() {
        var state = ReviewScheduler.afterExposure(day)
        assertEquals(day, state.due)
        val expected = listOf(1L, 3, 7, 14, 30, 60, 120)
        expected.forEachIndexed { index, interval ->
            val reviewed = state.due
            state = ReviewScheduler.schedule(state, ReviewOutcome.CORRECT, reviewed)
            assertEquals(index + 1, state.step)
            assertEquals(reviewed.plusDays(interval), state.due)
        }
        val lastDue = state.due
        state = ReviewScheduler.schedule(state, ReviewOutcome.CORRECT, lastDue)
        assertEquals(7, state.step)
        assertEquals(lastDue.plusDays(120), state.due)
    }

    @Test fun repeatedAndEarlyCorrectDoNotAccelerateSchedule() {
        val first = ReviewScheduler.schedule(ReviewScheduler.afterExposure(day), ReviewOutcome.CORRECT, day)
        val repeated = ReviewScheduler.schedule(first, ReviewOutcome.CORRECT, day)
        assertEquals(first.step, repeated.step)
        assertEquals(first.due, repeated.due)
        assertEquals(2, repeated.history.size)
        val early = ReviewScheduler.schedule(repeated, ReviewOutcome.CORRECT, day)
        assertEquals(first.due, early.due)
    }

    @Test fun lapseShortensIntervalButKeepsHistoryAndCorrectionCannotDoubleAdvance() {
        val first = ReviewScheduler.schedule(ReviewScheduler.afterExposure(day), ReviewOutcome.CORRECT, day)
        val second = ReviewScheduler.schedule(first, ReviewOutcome.CORRECT, first.due)
        val wrongDay = second.due
        val wrong = ReviewScheduler.schedule(second, ReviewOutcome.WRONG, wrongDay)
        assertEquals(1, wrong.step)
        assertEquals(wrongDay, wrong.due)
        assertEquals(1, wrong.lapses)
        assertEquals(3, wrong.history.size)
        val corrected = ReviewScheduler.schedule(wrong, ReviewOutcome.CORRECT, wrongDay)
        assertEquals(1, corrected.step)
        assertEquals(wrongDay.plusDays(1), corrected.due)
        assertEquals(4, corrected.history.size)
    }

    @Test fun datesUseTheChildsLocalCalendarAcrossMidnightAndDst() {
        val instant = Instant.parse("2026-09-06T03:30:00Z")
        val santiago = instant.atZone(ZoneId.of("America/Santiago")).toLocalDate()
        val tokyo = instant.atZone(ZoneId.of("Asia/Tokyo")).toLocalDate()
        assertEquals(LocalDate.of(2026, 9, 5), santiago)
        assertEquals(LocalDate.of(2026, 9, 6), tokyo)
        assertEquals(santiago.plusDays(1), ReviewScheduler.schedule(ReviewScheduler.afterExposure(santiago), ReviewOutcome.CORRECT, santiago).due)
    }

    @Test fun oldDateIsRejectedRatherThanRewritingHistory() {
        val first = ReviewScheduler.schedule(ReviewScheduler.afterExposure(day), ReviewOutcome.CORRECT, day)
        assertThrows(IllegalArgumentException::class.java) {
            ReviewScheduler.schedule(first, ReviewOutcome.WRONG, day.minusDays(1))
        }
    }
}
