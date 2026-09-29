package org.chinaquest.core

import java.time.LocalDate

enum class Skill { RECOGNITION, PRONUNCIATION, MEANING, WRITING, WORD }
enum class EvidenceSource { SELF, QUIZ, PARENT }
enum class MasteryPhase { NEW, LEARNING, REVIEWING, MASTERED }

data class Mastery(
    val recognition: Int = 0,
    val pronunciation: Int = 0,
    val meaning: Int = 0,
    val writing: Int = 0,
    val word: Int = 0,
    val successfulDays: Int = 0,
    val lastSuccessDate: LocalDate? = null,
    val parentChecks: Int = 0,
    val totalAttempts: Int = 0,
) {
    init {
        require(listOf(recognition, pronunciation, meaning, writing, word).all { it in 0..100 })
        require(successfulDays >= 0 && parentChecks >= 0 && totalAttempts >= 0)
    }

    val phase: MasteryPhase
        get() = when {
            successfulDays >= 3 && parentChecks > 0 &&
                listOf(recognition, pronunciation, meaning, writing).all { it >= 70 } -> MasteryPhase.MASTERED
            successfulDays >= 2 && listOf(recognition, pronunciation, meaning, writing).any { it >= 40 } -> MasteryPhase.REVIEWING
            totalAttempts > 0 -> MasteryPhase.LEARNING
            else -> MasteryPhase.NEW
        }

    fun score(skill: Skill): Int = when (skill) {
        Skill.RECOGNITION -> recognition
        Skill.PRONUNCIATION -> pronunciation
        Skill.MEANING -> meaning
        Skill.WRITING -> writing
        Skill.WORD -> word
    }
}

object MasteryCalculator {
    private fun delta(outcome: ReviewOutcome, source: EvidenceSource): Int = when (outcome) {
        ReviewOutcome.CORRECT -> when (source) {
            EvidenceSource.SELF -> 6
            EvidenceSource.QUIZ -> 15
            EvidenceSource.PARENT -> 25
        }
        ReviewOutcome.ALMOST -> when (source) {
            EvidenceSource.SELF -> 2
            EvidenceSource.QUIZ -> 5
            EvidenceSource.PARENT -> 7
        }
        ReviewOutcome.WRONG -> when (source) {
            EvidenceSource.SELF -> -4
            EvidenceSource.QUIZ -> -10
            EvidenceSource.PARENT -> -12
        }
    }

    fun apply(
        mastery: Mastery,
        skill: Skill,
        outcome: ReviewOutcome,
        source: EvidenceSource,
        date: LocalDate,
    ): Mastery {
        require(mastery.lastSuccessDate == null || !date.isBefore(mastery.lastSuccessDate)) {
            "Evidence must be recorded in date order"
        }
        val updated = (mastery.score(skill) + delta(outcome, source)).coerceIn(0, 100)
        val successToday = outcome == ReviewOutcome.CORRECT && mastery.lastSuccessDate != date
        val base = mastery.copy(
            successfulDays = mastery.successfulDays + if (successToday) 1 else 0,
            lastSuccessDate = if (successToday) date else mastery.lastSuccessDate,
            parentChecks = mastery.parentChecks + if (source == EvidenceSource.PARENT && outcome == ReviewOutcome.CORRECT) 1 else 0,
            totalAttempts = mastery.totalAttempts + 1,
        )
        return when (skill) {
            Skill.RECOGNITION -> base.copy(recognition = updated)
            Skill.PRONUNCIATION -> base.copy(pronunciation = updated)
            Skill.MEANING -> base.copy(meaning = updated)
            Skill.WRITING -> base.copy(writing = updated)
            Skill.WORD -> base.copy(word = updated)
        }
    }
}
