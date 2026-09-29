package org.chinaquest.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MasteryTest {
    private val day = LocalDate.of(2026, 9, 28)

    @Test fun oneSuccessNeverMastersAndSameDayCountsOnce() {
        val first = MasteryCalculator.apply(Mastery(), Skill.RECOGNITION, ReviewOutcome.CORRECT, EvidenceSource.PARENT, day)
        val repeated = MasteryCalculator.apply(first, Skill.MEANING, ReviewOutcome.CORRECT, EvidenceSource.PARENT, day)
        assertEquals(1, repeated.successfulDays)
        assertEquals(2, repeated.parentChecks)
        assertEquals(MasteryPhase.LEARNING, repeated.phase)
    }

    @Test fun masteryNeedsFourStrongSkillsThreeDaysAndParentCheck() {
        var state = Mastery()
        for (offset in 0L..2L) {
            for (skill in listOf(Skill.RECOGNITION, Skill.PRONUNCIATION, Skill.MEANING, Skill.WRITING)) {
                state = MasteryCalculator.apply(state, skill, ReviewOutcome.CORRECT, EvidenceSource.QUIZ, day.plusDays(offset))
            }
        }
        assertEquals(3, state.successfulDays)
        assertEquals(MasteryPhase.REVIEWING, state.phase)
        for (skill in listOf(Skill.RECOGNITION, Skill.PRONUNCIATION, Skill.MEANING, Skill.WRITING)) {
            state = MasteryCalculator.apply(state, skill, ReviewOutcome.CORRECT, EvidenceSource.PARENT, day.plusDays(3))
        }
        assertEquals(MasteryPhase.MASTERED, state.phase)
        assertTrue(state.word < 70) // Word is a separate fifth signal, not a hidden mastery gate.
    }

    @Test fun selfReportHasLessWeightAndFailurePreservesSuccessHistory() {
        val self = MasteryCalculator.apply(Mastery(), Skill.WRITING, ReviewOutcome.CORRECT, EvidenceSource.SELF, day)
        val quiz = MasteryCalculator.apply(Mastery(), Skill.WRITING, ReviewOutcome.CORRECT, EvidenceSource.QUIZ, day)
        assertTrue(self.writing < quiz.writing)
        val failed = MasteryCalculator.apply(self, Skill.WRITING, ReviewOutcome.WRONG, EvidenceSource.QUIZ, day.plusDays(1))
        assertEquals(1, failed.successfulDays)
        assertEquals(day, failed.lastSuccessDate)
        assertEquals(2, failed.totalAttempts)
        assertEquals(0, failed.writing)
    }

    @Test fun scoresAreClampedToBounds() {
        var state = Mastery()
        repeat(10) { state = MasteryCalculator.apply(state, Skill.WORD, ReviewOutcome.CORRECT, EvidenceSource.PARENT, day) }
        assertEquals(100, state.word)
        repeat(20) { state = MasteryCalculator.apply(state, Skill.WORD, ReviewOutcome.WRONG, EvidenceSource.PARENT, day) }
        assertEquals(0, state.word)
    }
}
