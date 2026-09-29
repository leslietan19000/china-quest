package org.chinaquest.core

import java.time.LocalDate

enum class ReviewOutcome { CORRECT, ALMOST, WRONG }

data class ReviewEvent(val date: LocalDate, val outcome: ReviewOutcome)

/** A step is the number of earned spacing intervals, capped at the last interval. */
data class ReviewState(
    val step: Int = 0,
    val due: LocalDate,
    val lastReviewed: LocalDate? = null,
    val lapses: Int = 0,
    val history: List<ReviewEvent> = emptyList(),
) {
    init {
        require(step in 0..ReviewScheduler.intervalsDays.size)
        require(lapses >= 0)
    }
}

object ReviewScheduler {
    val intervalsDays: List<Long> = listOf(1, 3, 7, 14, 30, 60, 120)

    /** An item just shown in learning is eligible again today. */
    fun afterExposure(today: LocalDate): ReviewState = ReviewState(due = today)

    fun schedule(state: ReviewState, outcome: ReviewOutcome, today: LocalDate): ReviewState {
        require(state.lastReviewed == null || !today.isBefore(state.lastReviewed)) {
            "Reviews must be recorded in date order"
        }
        val event = ReviewEvent(today, outcome)
        val sameDay = state.lastReviewed == today
        val (nextStep, nextDue) = when (outcome) {
            ReviewOutcome.CORRECT -> when {
                // Repetition or early practice cannot advance the spacing ladder.
                state.due.isAfter(today) -> state.step to state.due
                // A correction after a lapse may leave today, but earns no second step.
                sameDay -> maxOf(1, state.step) to today.plusDays(1)
                else -> {
                    val step = minOf(state.step + 1, intervalsDays.size)
                    step to today.plusDays(intervalsDays[step - 1])
                }
            }
            ReviewOutcome.ALMOST -> state.step to if (state.due.isAfter(today)) state.due else today.plusDays(1)
            ReviewOutcome.WRONG -> maxOf(0, state.step - 1) to today
        }
        return state.copy(
            step = nextStep,
            due = nextDue,
            lastReviewed = today,
            lapses = state.lapses + if (outcome == ReviewOutcome.WRONG) 1 else 0,
            history = state.history + event,
        )
    }
}
