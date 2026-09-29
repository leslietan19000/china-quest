package org.chinaquest.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPolicyTest {
    private val stage = LearningStage.STAGE_1_CHARACTER
    private fun items(prefix: String, count: Int) = (1..count).map { LearningItem("$prefix$it", stage) }

    @Test fun longTermSchemaHasTheSpecifiedStagesAndNineDimensions() {
        assertEquals(
            listOf("STAGE_1_CHARACTER", "STAGE_2_WORD", "STAGE_3_SENTENCE", "STAGE_4_READING", "STAGE_5_EXPRESSION", "STAGE_6_CREATION"),
            LearningStage.entries.map { it.name },
        )
        assertEquals(
            listOf("CHARACTER", "VOCABULARY", "READING", "WRITING", "PRONUNCIATION", "EXPRESSION", "REAL_WORLD_USAGE", "CREATOR", "BUILDER"),
            LearningDimension.entries.map { it.name },
        )
    }

    @Test fun configurableGoalIsNotFixedAtFive() {
        val plan = SessionPolicy.compose(stage, SessionGoal(8), items("review", 10), items("new", 10))
        assertEquals(8, plan.entries.size)
        assertEquals(5, plan.reviewCount)
        assertEquals(3, plan.newCount)
    }

    @Test fun reviewOnlyExcludesNewContentAndDoesNotDuplicateItems() {
        val item = LearningItem("same", stage)
        val plan = SessionPolicy.compose(stage, SessionGoal(4, reviewOnly = true), listOf(item, item), items("new", 4))
        assertEquals(1, plan.entries.size)
        assertEquals(0, plan.newCount)
    }

    @Test fun currentStageFiltersNewAndReviewsFillOpenPlaces() {
        val other = LearningItem("future", LearningStage.STAGE_2_WORD)
        val plan = SessionPolicy.compose(stage, SessionGoal(6, reviewPercentOverride = 50), items("review", 6), listOf(other, LearningItem("new1", stage)))
        assertEquals(6, plan.entries.size)
        assertEquals(5, plan.reviewCount)
        assertTrue(plan.entries.none { it.item.id == "future" })
    }
}
