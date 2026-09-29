package org.chinaquest.core

import java.time.LocalDate

data class DailyStamp(val childId: String, val date: LocalDate)
data class RewardLedger(val stamps: Set<DailyStamp> = emptySet())
data class WeeklyChoiceGoal(val childId: String, val weekStart: LocalDate, val targetDays: Int) {
    init { require(targetDays > 0) }
}
data class FamilyGoal(val weekStart: LocalDate, val targetsByChild: Map<String, Int>) {
    init {
        require(targetsByChild.size >= 2)
        require(targetsByChild.keys.all { it.isNotBlank() })
        require(targetsByChild.values.all { it > 0 })
    }
}

object RewardEngine {
    fun awardDailyStamp(ledger: RewardLedger, childId: String, date: LocalDate, earned: Boolean): RewardLedger {
        require(childId.isNotBlank())
        return if (earned) ledger.copy(stamps = ledger.stamps + DailyStamp(childId, date)) else ledger
    }

    fun weeklyChoiceUnlocked(ledger: RewardLedger, goal: WeeklyChoiceGoal): Boolean =
        countDays(ledger, goal.childId, goal.weekStart) >= goal.targetDays

    fun familyUnlocked(ledger: RewardLedger, goal: FamilyGoal): Boolean =
        goal.targetsByChild.all { (childId, target) -> countDays(ledger, childId, goal.weekStart) >= target }

    private fun countDays(ledger: RewardLedger, childId: String, weekStart: LocalDate): Int {
        val end = weekStart.plusDays(7)
        return ledger.stamps.count { it.childId == childId && !it.date.isBefore(weekStart) && it.date.isBefore(end) }
    }
}

data class SeasonState(
    val id: String,
    val progressPoints: Int,
    val targetPoints: Int,
    val ending: String? = null,
    val nextAdventureId: String? = null,
) {
    init {
        require(id.isNotBlank() && targetPoints > 0 && progressPoints >= 0)
    }
    val isComplete: Boolean get() = progressPoints >= targetPoints
}

data class SeasonTransition(val completedSeason: SeasonState, val nextSeason: SeasonState, val profile: LearningProfile)

object SeasonEngine {
    fun addProgress(season: SeasonState, points: Int): SeasonState {
        require(points >= 0)
        return season.copy(progressPoints = (season.progressPoints.toLong() + points).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }

    fun complete(season: SeasonState, ending: String, nextAdventureId: String, profile: LearningProfile): SeasonTransition {
        require(season.isComplete) { "The season target has not been reached" }
        require(ending.isNotBlank() && nextAdventureId.isNotBlank())
        val completed = season.copy(ending = ending, nextAdventureId = nextAdventureId)
        return SeasonTransition(completed, SeasonState(nextAdventureId, 0, season.targetPoints), profile)
    }
}
