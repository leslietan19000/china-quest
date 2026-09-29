package org.chinaquest.core

enum class LearningStage {
    STAGE_1_CHARACTER,
    STAGE_2_WORD,
    STAGE_3_SENTENCE,
    STAGE_4_READING,
    STAGE_5_EXPRESSION,
    STAGE_6_CREATION,
}

enum class LearningDimension {
    CHARACTER,
    VOCABULARY,
    READING,
    WRITING,
    PRONUNCIATION,
    EXPRESSION,
    REAL_WORLD_USAGE,
    CREATOR,
    BUILDER,
}

data class LearningProfile(
    val childId: String,
    val stage: LearningStage = LearningStage.STAGE_1_CHARACTER,
    val dimensions: Map<LearningDimension, Int> = LearningDimension.entries.associateWith { 0 },
    val masteryByItem: Map<String, Mastery> = emptyMap(),
) {
    init {
        require(childId.isNotBlank())
        require(dimensions.keys.containsAll(LearningDimension.entries))
        require(dimensions.values.all { it in 0..100 })
    }

    fun withMastery(itemId: String, mastery: Mastery): LearningProfile {
        require(itemId.isNotBlank())
        return copy(masteryByItem = masteryByItem + (itemId to mastery))
    }
}

data class LearningItem(val id: String, val stage: LearningStage) {
    init { require(id.isNotBlank()) }
}

data class SessionGoal(
    val targetCount: Int,
    val reviewOnly: Boolean = false,
    val reviewPercentOverride: Int? = null,
) {
    init {
        require(targetCount > 0)
        require(reviewPercentOverride == null || reviewPercentOverride in 0..100)
    }
}

enum class SessionEntryType { REVIEW, NEW }
data class SessionEntry(val item: LearningItem, val type: SessionEntryType)
data class SessionPlan(val entries: List<SessionEntry>) {
    val reviewCount: Int get() = entries.count { it.type == SessionEntryType.REVIEW }
    val newCount: Int get() = entries.count { it.type == SessionEntryType.NEW }
}

object SessionPolicy {
    private val stageReviewPercent = mapOf(
        LearningStage.STAGE_1_CHARACTER to 60,
        LearningStage.STAGE_2_WORD to 55,
        LearningStage.STAGE_3_SENTENCE to 50,
        LearningStage.STAGE_4_READING to 45,
        LearningStage.STAGE_5_EXPRESSION to 40,
        LearningStage.STAGE_6_CREATION to 35,
    )

    /** Inputs are already filtered and ordered by the caller (for example due date or lesson order). */
    fun compose(
        stage: LearningStage,
        goal: SessionGoal,
        dueReviews: List<LearningItem>,
        newItems: List<LearningItem>,
    ): SessionPlan {
        val uniqueReviews = dueReviews.distinctBy { it.id }
        if (goal.reviewOnly) {
            return SessionPlan(uniqueReviews.take(goal.targetCount).map { SessionEntry(it, SessionEntryType.REVIEW) })
        }
        val uniqueNew = newItems.filter { it.stage == stage }
            .distinctBy { it.id }
            .filterNot { candidate -> uniqueReviews.any { it.id == candidate.id } }
        val percent = goal.reviewPercentOverride ?: stageReviewPercent.getValue(stage)
        val preferredReviews = (goal.targetCount * percent + 99) / 100
        val reviews = uniqueReviews.take(preferredReviews)
        val new = uniqueNew.take(goal.targetCount - reviews.size)
        // Fill remaining room with due reviews if the current stage has too few new items.
        val extraReviews = uniqueReviews.drop(reviews.size).take(goal.targetCount - reviews.size - new.size)
        return SessionPlan(
            reviews.map { SessionEntry(it, SessionEntryType.REVIEW) } +
                new.map { SessionEntry(it, SessionEntryType.NEW) } +
                extraReviews.map { SessionEntry(it, SessionEntryType.REVIEW) },
        )
    }
}
