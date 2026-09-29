package org.chinaquest.app

import android.content.Context
import org.chinaquest.core.ReviewOutcome
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class PlacementAndUpgradeTest {
    private lateinit var context:Context
    private lateinit var store:QuestStore
    private val name="placement-upgrade.db"
    private val day=LocalDate.of(2026,9,29)
    @Before fun before() {
        context=RuntimeEnvironment.getApplication();context.deleteDatabase(name)
        store=QuestStore(context,name)
    }
    @After fun after() { store.close();context.deleteDatabase(name) }

    @Test fun placementReplansSevenDaysWithoutInventingMasteryRewardsOrSiblingChanges() {
        store.lesson("child-a",day)
        val selected=store.cards().take(40).map { it.id }.toSet()
        store.setPriorKnowledge("child-a",selected,day)
        val next=store.lesson("child-a",day)
        assertEquals(5,next.newCount)
        assertTrue(next.tasks.none { it.characterId in selected })
        assertEquals(store.cards()[40].id,next.tasks.first().characterId)
        assertEquals(7,store.plannedDays("child-a",day))
        assertEquals(0,store.status("child-a",day).learned)
        assertEquals(0,store.status("child-a",day).mastered)
        assertEquals(0,store.stampCount("child-a"))
        assertEquals(0,store.pendingEventCount())
        assertEquals(store.cards().first().id,store.lesson("child-b",day).tasks.first().characterId)
        assertTrue(store.knownCharacterIds("child-b").isEmpty())
        store.close();store=QuestStore(context,name)
        assertEquals(selected,store.knownCharacterIds("child-a"))
    }

    @Test fun changingPlacementKeepsActiveLessonAndHistoryButChangesTomorrow() {
        val old=store.lesson("child-a",day)
        store.answer("child-a",day,0,null)
        val first=old.tasks.first().characterId
        val before=store.mastery("child-a",first)
        val selected=store.cards().take(30).map { it.id }.toSet()
        store.setPriorKnowledge("child-a",selected,day)
        assertEquals(old.tasks,store.lesson("child-a",day).tasks)
        assertEquals(1,store.lesson("child-a",day).cursor)
        assertEquals(before,store.mastery("child-a",first))
        assertEquals(1,store.pendingEventCount())
        assertTrue(store.lesson("child-a",day.plusDays(1)).tasks.none { it.kind=="LEARN" && it.characterId in selected })
        for(cursor in 1 until old.tasks.size) {
            store.answer("child-a",day,cursor,if(old.tasks[cursor].kind=="LEARN") null else ReviewOutcome.CORRECT)
        }
        assertTrue(store.complete("child-a",day))
        store.setPriorKnowledge("child-a",selected+store.cards()[31].id,day)
        assertTrue(store.lesson("child-a",day).completed)
        assertEquals(1,store.stampCount("child-a"))
    }

    @Test fun priorKnowledgeIsCheckedLaterAndWrongAnswerReturnsToReviewWithoutReset() {
        val first=store.cards().first().id
        store.setPriorKnowledge("child-a",setOf(first),day)
        store.updateChild("child-a","A",0,true,day)
        assertTrue(store.lesson("child-a",day).tasks.isEmpty())
        assertEquals(0,store.dueCount("child-a",day.plusDays(6)))
        val checkDay=day.plusDays(7)
        assertEquals(1,store.dueCount("child-a",checkDay))
        val check=store.lesson("child-a",checkDay)
        assertEquals(Task("REVIEW",first),check.tasks.first())
        assertTrue(store.answer("child-a",checkDay,0,ReviewOutcome.WRONG))
        assertEquals(checkDay,store.review("child-a",first)!!.due)
        assertEquals(1,store.review("child-a",first)!!.lapses)
        assertEquals(0,store.status("child-a",checkDay).mastered)
        assertEquals(1,store.dueCount("child-a",checkDay))
    }

    @Test fun wordPracticeIsBoundedByAgeAndCanBeTurnedOff() {
        assertEquals(2,store.lesson("child-a",day).tasks.count { it.kind=="WORD" })
        assertEquals(1,store.lesson("child-b",day).tasks.count { it.kind=="WORD" })
        assertTrue(store.cards().all { store.words(it.id).isNotEmpty() })
        store.updateChild("child-a","A",3,false,day,wordPractice=false)
        assertTrue(store.lesson("child-a",day).tasks.none { it.kind=="WORD" })
        assertEquals(3,store.lesson("child-a",day).newCount)
    }

    @Test fun unknownPlacementIdIsRejectedBeforeAnyChange() {
        try { store.setPriorKnowledge("child-a",setOf("not-a-character"),day);fail("Invalid ID accepted") }
        catch(_:IllegalArgumentException) { }
        assertTrue(store.knownCharacterIds("child-a").isEmpty())
        assertEquals(0,store.eventCount("child-a"))
    }

    @Test fun shippedVersionOneDatabaseUpgradesWithoutLosingPinSessionEventsOrStamps() {
        store.close();context.deleteDatabase(name)
        val db=context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null)
        val schema=javaClass.classLoader!!.getResourceAsStream("legacy-v1-schema.sql")!!.bufferedReader().use { it.readText() }
            .lineSequence().filterNot { it.startsWith("--") }.joinToString("\n")
        schema.split(';').filter { it.isNotBlank() }.forEach { db.execSQL(it.trim()) }
        db.execSQL("INSERT INTO children(id,display_name,age_group,daily_target,created_at) VALUES ('child-a','Old A','AGE_8_10',3,'2026-09-28'),('child-b','Old B','AGE_UNDER_6',5,'2026-09-28')")
        db.execSQL("INSERT INTO child_learning_profiles(child_id,character_level) VALUES ('child-a',2),('child-b',0)")
        val chars=JSONArray(context.assets.open("characters.json").bufferedReader().use { it.readText() })
        for(i in 0 until chars.length()) {
            val item=chars.getJSONObject(i)
            db.execSQL("INSERT INTO characters VALUES (?,?,?)",arrayOf(item.getString("id"),i,item.toString()))
        }
        val charId=chars.getJSONObject(0).getString("id")
        val tasks=JSONArray(listOf(Task("LEARN",charId).json(),Task("FIND",charId).json())).toString()
        db.execSQL("INSERT INTO plans VALUES (?,?,?)",arrayOf("child-a",day.toString(),JSONArray(listOf(charId)).toString()))
        db.execSQL("INSERT INTO sessions VALUES (?,?,?,1,0)",arrayOf("child-a",day.toString(),tasks))
        db.execSQL("INSERT INTO sessions VALUES (?,?,?,2,1)",arrayOf("child-b",day.toString(),tasks))
        val mastery="""{"recognition":15,"pronunciation":6,"meaning":0,"writing":0,"word":0,"successfulDays":1,"lastSuccessDate":"2026-09-28","parentChecks":0,"totalAttempts":2}"""
        val review="""{"step":1,"due":"2026-09-29","lastReviewed":"2026-09-28","lapses":0}"""
        db.execSQL("INSERT INTO mastery VALUES (?,?,?,?,?,?)",arrayOf("child-a",charId,mastery,review,day.toString(),"2026-09-28"))
        db.execSQL("INSERT INTO events VALUES ('legacy-event','child-a',?,'LEARNING_EVIDENCE','{}','2026-09-28T12:00:00Z')",arrayOf(day.toString()))
        db.execSQL("INSERT INTO outbox(event_id,payload) VALUES ('legacy-event','preserved-envelope')")
        db.execSQL("INSERT INTO daily_stamps VALUES ('child-b',?)",arrayOf(day.toString()))
        db.version=1;db.close()
        val prefs=context.getSharedPreferences("parent_gate",Context.MODE_PRIVATE)
        prefs.edit().clear().commit();ParentGate(context).setPin("458726")
        store=QuestStore(context,name)
        assertEquals(2,store.readableDatabase.version)
        assertEquals("Old A",store.child("child-a").name)
        assertEquals(3,store.child("child-a").target)
        assertTrue(store.child("child-a").wordPractice)
        assertEquals(1,store.lesson("child-a",day).cursor)
        assertEquals(2,store.lesson("child-a",day).tasks.size)
        assertTrue(store.lesson("child-b",day).completed)
        assertEquals(15,store.mastery("child-a",charId)!!.recognition)
        assertEquals(1,store.review("child-a",charId)!!.step)
        assertEquals(1,store.eventCount("child-a"))
        assertEquals("preserved-envelope",store.pending(5).single().envelope)
        assertEquals(1,store.stampCount("child-b"))
        assertTrue(ParentGate(context).verify("458726"))
        assertTrue(store.knownCharacterIds("child-a").isEmpty())
        store.close();store=QuestStore(context,name)
        assertEquals(2,store.readableDatabase.version)
        assertEquals(1,store.eventCount("child-a"))
    }
}
