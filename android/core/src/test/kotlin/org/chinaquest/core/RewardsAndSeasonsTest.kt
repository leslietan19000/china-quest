package org.chinaquest.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardsAndSeasonsTest {
    private val monday = LocalDate.of(2026, 9, 28)

    @Test fun oneStampPerChildPerDateAndOwnWeeklyTargets() {
        var ledger = RewardLedger()
        ledger = RewardEngine.awardDailyStamp(ledger, "a", monday, true)
        ledger = RewardEngine.awardDailyStamp(ledger, "a", monday, true)
        ledger = RewardEngine.awardDailyStamp(ledger, "b", monday, true)
        ledger = RewardEngine.awardDailyStamp(ledger, "b", monday.plusDays(1), false)
        assertEquals(2, ledger.stamps.size)
        assertTrue(RewardEngine.weeklyChoiceUnlocked(ledger, WeeklyChoiceGoal("a", monday, 1)))
        assertFalse(RewardEngine.weeklyChoiceUnlocked(ledger, WeeklyChoiceGoal("b", monday, 2)))
    }

    @Test fun familyRewardRequiresBothChildrensOwnTargets() {
        var ledger = RewardLedger()
        for (offset in 0L..2L) ledger = RewardEngine.awardDailyStamp(ledger, "a", monday.plusDays(offset), true)
        ledger = RewardEngine.awardDailyStamp(ledger, "b", monday, true)
        val goal = FamilyGoal(monday, mapOf("a" to 3, "b" to 2))
        assertFalse(RewardEngine.familyUnlocked(ledger, goal))
        ledger = RewardEngine.awardDailyStamp(ledger, "b", monday.plusDays(1), true)
        assertTrue(RewardEngine.familyUnlocked(ledger, goal))
        ledger = RewardEngine.awardDailyStamp(ledger, "b", monday.plusDays(7), true)
        assertTrue(RewardEngine.familyUnlocked(ledger, goal))
        assertFalse(RewardEngine.weeklyChoiceUnlocked(ledger, WeeklyChoiceGoal("b", monday, 3)))
    }

    @Test fun seasonCompletionPreservesLongTermAbilityAndStartsNextAdventure() {
        val mastery = Mastery(recognition = 80, pronunciation = 80, meaning = 80, writing = 80, successfulDays = 3, parentChecks = 1)
        val profile = LearningProfile("a", LearningStage.STAGE_3_SENTENCE).withMastery("character-1", mastery)
        val season = SeasonEngine.addProgress(SeasonState("mountain", 4, 5), 1)
        val transition = SeasonEngine.complete(season, "The family reaches the summit", "river", profile)
        assertTrue(transition.completedSeason.isComplete)
        assertEquals("river", transition.nextSeason.id)
        assertEquals(0, transition.nextSeason.progressPoints)
        assertEquals(profile, transition.profile)
        assertEquals(MasteryPhase.MASTERED, transition.profile.masteryByItem.getValue("character-1").phase)
        assertEquals(9, transition.profile.dimensions.size)
    }
}
