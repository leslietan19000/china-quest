package org.chinaquest.app

import android.content.Context
import java.time.LocalDate
import org.chinaquest.core.ReviewOutcome
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QuestStoreAcceptanceTest {
    private lateinit var context: Context
    private lateinit var store: QuestStore
    private val day = LocalDate.of(2026, 9, 28)
    private val dbName = "acceptance.db"

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(dbName)
        store = QuestStore(context, dbName)
    }

    @After fun tearDown() { store.close(); context.deleteDatabase(dbName) }

    @Test fun plansCoverSevenDaysAndFollowEachChildsTarget() {
        store.updateChild("child-a", "Explorer A", 2, false, day)
        assertEquals(7, store.plannedDays("child-a", day))
        assertEquals(2, store.lesson("child-a", day).newCount)
        store.ensurePlans("child-b", day)
        assertEquals(7, store.plannedDays("child-b", day))
        assertEquals(5, store.lesson("child-b", day).newCount)
    }

    @Test fun goalChangeRebuildsUnstartedLessonButPreservesStartedLesson() {
        assertEquals(5, store.lesson("child-a", day).newCount)
        store.updateChild("child-a", "Explorer A", 2, false, day)
        val revised = store.lesson("child-a", day)
        assertEquals(2, revised.newCount)
        assertTrue(store.answer("child-a", day, 0, null))
        store.updateChild("child-a", "Explorer A", 4, false, day)
        val started = store.lesson("child-a", day)
        assertEquals(2, started.newCount)
        assertEquals(1, started.cursor)
    }

    @Test fun childrenHaveSeparateProgressAndItSurvivesDatabaseReopen() {
        val lesson = store.lesson("child-a", day)
        val firstId = lesson.tasks.first().characterId
        assertTrue(store.answer("child-a", day, 0, null))
        assertNotNull(store.mastery("child-a", firstId))
        assertNull(store.mastery("child-b", firstId))
        assertEquals(1, store.status("child-a", day).cursor)
        assertEquals(0, store.status("child-b", day).cursor)
        store.close()
        store = QuestStore(context, dbName)
        assertEquals(1, store.lesson("child-a", day).cursor)
        assertNotNull(store.mastery("child-a", firstId))
        assertNull(store.mastery("child-b", firstId))
        assertEquals(7, store.plannedDays("child-a", day))
        assertEquals(1, store.eventCount("child-a"))
    }

    @Test fun learningExposureIsDueTodayAndWrongShortensWithoutErasingProgress() {
        val lesson = store.lesson("child-a", day)
        val firstId = lesson.tasks.first().characterId
        assertTrue(store.answer("child-a", day, 0, null))
        assertEquals(day, store.review("child-a", firstId)!!.due)
        assertEquals(1, store.dueCount("child-a", day))
        for (cursor in 1 until lesson.tasks.size) {
            val task = lesson.tasks[cursor]
            val outcome = when {
                task.kind == "LEARN" -> null
                task.kind == "READ" && task.characterId == firstId -> ReviewOutcome.WRONG
                else -> ReviewOutcome.CORRECT
            }
            assertTrue(store.answer("child-a", day, cursor, outcome))
            if (task.kind == "FIND" && task.characterId == firstId) {
                assertEquals(day.plusDays(1), store.review("child-a", firstId)!!.due)
            }
            if (task.kind == "READ" && task.characterId == firstId) {
                assertEquals(day, store.review("child-a", firstId)!!.due)
                assertEquals(1, store.review("child-a", firstId)!!.lapses)
            }
        }
        val review = store.review("child-a", firstId)!!
        assertEquals(day.plusDays(1), review.due)
        assertEquals(1, review.lapses)
        assertNotNull(store.mastery("child-a", firstId))
        assertTrue(store.mastery("child-a", firstId)!!.totalAttempts >= 2)
    }

    @Test fun staleAnswerAndSecondCompletionCannotMintAnotherStampOrEvent() {
        val lesson = store.lesson("child-a", day)
        assertTrue(store.answer("child-a", day, 0, null))
        val eventsAfterFirst = store.eventCount("child-a")
        assertFalse(store.answer("child-a", day, 0, null))
        assertEquals(eventsAfterFirst, store.eventCount("child-a"))
        for (cursor in 1 until lesson.tasks.size) {
            val task = lesson.tasks[cursor]
            assertTrue(store.answer("child-a", day, cursor, if (task.kind == "LEARN") null else ReviewOutcome.CORRECT))
        }
        assertTrue(store.complete("child-a", day))
        val eventsAfterComplete = store.eventCount("child-a")
        assertFalse(store.complete("child-a", day))
        assertEquals(1, store.stampCount("child-a"))
        assertEquals(eventsAfterComplete, store.eventCount("child-a"))
        assertEquals(0, store.stampCount("child-b"))
    }

    @Test fun reviewOnlyOffersDueReviewWithoutNewCharacters() {
        val lesson = store.lesson("child-a", day)
        assertTrue(store.answer("child-a", day, 0, null))
        val first = lesson.tasks.first().characterId
        store.updateChild("child-a", "Explorer A", 5, true, day.plusDays(1))
        val next = store.lesson("child-a", day.plusDays(1))
        assertEquals(0, next.newCount)
        assertTrue(next.tasks.any { it.kind == "REVIEW" && it.characterId == first })
        assertEquals(7, store.plannedDays("child-a", day.plusDays(1)))
    }
}
