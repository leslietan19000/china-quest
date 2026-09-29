package org.chinaquest.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.chinaquest.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.Instant
import java.util.UUID

/** Local source of truth. All public learning operations are explicitly child-scoped. */
class QuestStore(private val context: Context, name: String = "china-quest.db") : SQLiteOpenHelper(context, name, null, 1), SyncOutbox {
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE children (id TEXT PRIMARY KEY, family_id TEXT NOT NULL DEFAULT 'local-family', display_name TEXT NOT NULL, age_group TEXT NOT NULL, avatar TEXT NOT NULL DEFAULT 'explorer', learning_stage TEXT NOT NULL DEFAULT 'STAGE_1_CHARACTER', daily_target INTEGER NOT NULL CHECK(daily_target BETWEEN 0 AND 10), review_only INTEGER NOT NULL DEFAULT 0, preferences TEXT NOT NULL DEFAULT '{}', current_season TEXT NOT NULL DEFAULT 'preparing-china', current_stage INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL)")
        db.execSQL("CREATE TABLE characters (id TEXT PRIMARY KEY, ordinal INTEGER NOT NULL, data TEXT NOT NULL)")
        db.execSQL("CREATE TABLE mastery (child_id TEXT NOT NULL REFERENCES children(id), character_id TEXT NOT NULL REFERENCES characters(id), data TEXT NOT NULL, review TEXT NOT NULL, due TEXT NOT NULL, introduced TEXT NOT NULL, PRIMARY KEY(child_id,character_id))")
        db.execSQL("CREATE INDEX mastery_due ON mastery(child_id,due)")
        db.execSQL("CREATE TABLE plans (child_id TEXT NOT NULL REFERENCES children(id), day TEXT NOT NULL, new_ids TEXT NOT NULL, PRIMARY KEY(child_id,day))")
        db.execSQL("CREATE TABLE sessions (child_id TEXT NOT NULL REFERENCES children(id), day TEXT NOT NULL, tasks TEXT NOT NULL, cursor INTEGER NOT NULL DEFAULT 0, completed INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(child_id,day))")
        db.execSQL("CREATE TABLE events (id TEXT PRIMARY KEY, child_id TEXT NOT NULL REFERENCES children(id), day TEXT NOT NULL, kind TEXT NOT NULL, payload TEXT NOT NULL, created_at TEXT NOT NULL)")
        db.execSQL("CREATE TABLE outbox (event_id TEXT PRIMARY KEY REFERENCES events(id), payload TEXT NOT NULL, attempts INTEGER NOT NULL DEFAULT 0, acknowledged_at TEXT)")
        db.execSQL("CREATE TABLE daily_stamps (child_id TEXT NOT NULL REFERENCES children(id), day TEXT NOT NULL, PRIMARY KEY(child_id,day))")
        db.execSQL("CREATE TABLE child_learning_profiles (child_id TEXT PRIMARY KEY REFERENCES children(id), character_level INTEGER NOT NULL DEFAULT 0, vocabulary_level INTEGER NOT NULL DEFAULT 0, reading_level INTEGER NOT NULL DEFAULT 0, writing_level INTEGER NOT NULL DEFAULT 0, pronunciation_level INTEGER NOT NULL DEFAULT 0, expression_level INTEGER NOT NULL DEFAULT 0, real_world_usage_level INTEGER NOT NULL DEFAULT 0, creator_level INTEGER NOT NULL DEFAULT 0, builder_level INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("INSERT INTO children(id,display_name,age_group,daily_target,created_at) VALUES ('child-a','Child A','AGE_8_10',5,?),('child-b','Child B','AGE_UNDER_6',5,?)", arrayOf(Instant.now().toString(), Instant.now().toString()))
        db.execSQL("INSERT INTO child_learning_profiles(child_id) VALUES ('child-a'),('child-b')")
        val seed = JSONArray(context.assets.open("characters.json").bufferedReader().use { it.readText() })
        for (i in 0 until seed.length()) {
            val item = seed.getJSONObject(i)
            db.execSQL("INSERT INTO characters(id,ordinal,data) VALUES (?,?,?)", arrayOf(item.getString("id"), i, item.toString()))
        }
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("A non-destructive migration is required: $oldVersion -> $newVersion")
    }
    fun children(): List<Child> = readableDatabase.rawQuery("SELECT id,display_name,age_group,daily_target,review_only FROM children ORDER BY id", null).use { c ->
        buildList { while(c.moveToNext()) add(Child(c.getString(0),c.getString(1),c.getString(2),c.getInt(3),c.getInt(4)==1)) }
    }
    fun child(id: String) = children().first { it.id == id }
    fun updateChild(id: String, name: String, target: Int, reviewOnly: Boolean, today: LocalDate) {
        require(name.trim().length in 1..24 && target in 0..10)
        child(id)
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE children SET display_name=?,daily_target=?,review_only=? WHERE id=?", arrayOf(name.trim(),target,if(reviewOnly) 1 else 0,id))
            // Never rewrite an in-progress lesson. Changed goals affect the next unstarted plan.
            db.execSQL("DELETE FROM sessions WHERE child_id=? AND day>=? AND cursor=0 AND completed=0",arrayOf(id,today.toString()))
            db.execSQL("DELETE FROM plans WHERE child_id=? AND day>=? AND day NOT IN (SELECT day FROM sessions WHERE child_id=?)", arrayOf(id,today.toString(),id))
            record(db,id,today,"CHILD_SETTINGS",JSONObject().put("daily_target",target).put("review_only",reviewOnly))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        ensurePlans(id,today)
    }
    fun cards(): List<CharacterCard> = readableDatabase.rawQuery("SELECT data FROM characters ORDER BY ordinal",null).use { c ->
        buildList { while(c.moveToNext()) add(parseCard(JSONObject(c.getString(0)))) }
    }
    fun card(id: String): CharacterCard = readableDatabase.rawQuery("SELECT data FROM characters WHERE id=?",arrayOf(id)).use { c ->
        require(c.moveToFirst()) { "Unknown content ID" }; parseCard(JSONObject(c.getString(0)))
    }
    private fun parseCard(j: JSONObject): CharacterCard {
        fun optional(key: String) = if(j.isNull(key)) null else j.optString(key).takeIf { it.isNotBlank() }
        val words = j.optJSONArray("common_words") ?: JSONArray()
        return CharacterCard(j.getString("id"),j.getString("character"),j.getString("pinyin"),j.optString("radical",""),j.optInt("stroke_count",0),j.optString("meaning_en",""),optional("meaning_zh"),optional("meaning_es_optional"),List(words.length()) { words.getString(it) },optional("example_sentence"),j.optString("review_status")=="APPROVED")
    }
    fun dueCount(id: String, today: LocalDate): Int = scalar("SELECT COUNT(*) FROM mastery WHERE child_id=? AND due<=?", arrayOf(id,today.toString()))
    fun plannedDays(id: String, today: LocalDate): Int = scalar("SELECT COUNT(*) FROM plans WHERE child_id=? AND day>=? AND day<=?",arrayOf(id,today.toString(),today.plusDays(6).toString()))
    fun ensurePlans(id: String, today: LocalDate) {
        val profile = child(id)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val used = mutableSetOf<String>()
            db.rawQuery("SELECT character_id FROM mastery WHERE child_id=?",arrayOf(id)).use { c -> while(c.moveToNext()) used.add(c.getString(0)) }
            db.rawQuery("SELECT new_ids FROM plans WHERE child_id=? AND day>=?",arrayOf(id,today.toString())).use { c -> while(c.moveToNext()) { val a=JSONArray(c.getString(0)); for(i in 0 until a.length()) used.add(a.getString(i)) } }
            val available = cards().map { it.id }.filterNot { it in used }.iterator()
            for (offset in 0..6) {
                val date = today.plusDays(offset.toLong()).toString()
                if (scalar("SELECT COUNT(*) FROM plans WHERE child_id=? AND day=?",arrayOf(id,date))>0) continue
                val ids=JSONArray()
                repeat(if(profile.reviewOnly) 0 else profile.target) { if(available.hasNext()) ids.put(available.next()) }
                db.execSQL("INSERT INTO plans(child_id,day,new_ids) VALUES (?,?,?)",arrayOf(id,date,ids.toString()))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun lesson(id: String, today: LocalDate): Lesson {
        ensurePlans(id,today)
        readLesson(id,today)?.let { return it }
        val db=writableDatabase
        db.beginTransaction()
        try {
            val planned=db.rawQuery("SELECT new_ids FROM plans WHERE child_id=? AND day=?",arrayOf(id,today.toString())).use { c -> c.moveToFirst();JSONArray(c.getString(0)) }
            val newIds=List(planned.length()) { planned.getString(it) }.filter { mastery(id,it)==null }
            val due=db.rawQuery("SELECT character_id FROM mastery WHERE child_id=? AND due<=? ORDER BY due,character_id LIMIT 6",arrayOf(id,today.toString())).use { c -> buildList { while(c.moveToNext()) add(c.getString(0)) } }
            val tasks=buildList {
                newIds.forEach { add(Task("LEARN",it)) }
                due.forEach { add(Task("REVIEW",it)) }
                newIds.forEach { add(Task("FIND",it)) }
                (newIds+due).firstOrNull()?.let { add(Task("READ",it)); add(Task("WRITE",it)) }
            }
            db.execSQL("INSERT OR IGNORE INTO sessions(child_id,day,tasks) VALUES (?,?,?)",arrayOf(id,today.toString(),JSONArray(tasks.map { it.json() }).toString()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return readLesson(id,today)!!
    }
    private fun readLesson(id:String,day:LocalDate):Lesson? = readableDatabase.rawQuery("SELECT tasks,cursor,completed FROM sessions WHERE child_id=? AND day=?",arrayOf(id,day.toString())).use { c ->
        if(!c.moveToFirst()) null else { val a=JSONArray(c.getString(0)); Lesson(id,day,List(a.length()) { val t=a.getJSONObject(it);Task(t.getString("kind"),t.getString("character_id")) },c.getInt(1),c.getInt(2)==1) }
    }
    /** Compare-and-advance protects against double taps, stale screens and resumed callbacks. */
    fun answer(id:String,day:LocalDate,expectedCursor:Int,outcome:ReviewOutcome?):Boolean {
        val db=writableDatabase
        db.beginTransaction()
        try {
            val session=readLesson(id,day) ?: return false
            if(session.completed || session.cursor!=expectedCursor || expectedCursor !in session.tasks.indices) return false
            val task=session.tasks[expectedCursor]
            val prior=mastery(id,task.characterId) ?: Mastery()
            val priorReview=review(id,task.characterId) ?: ReviewScheduler.afterExposure(day)
            val skill=when(task.kind) { "READ" -> Skill.PRONUNCIATION; "WRITE" -> Skill.WRITING; else -> Skill.RECOGNITION }
            val source=if(task.kind in listOf("FIND","REVIEW")) EvidenceSource.QUIZ else EvidenceSource.SELF
            require((task.kind=="LEARN") == (outcome==null)) { "Exposure and assessed evidence are different events" }
            val effectiveDate=listOfNotNull(day,prior.lastSuccessDate,priorReview.lastReviewed).maxOrNull()!!
            val next=if(outcome==null) prior else MasteryCalculator.apply(prior,skill,outcome,source,effectiveDate)
            val nextReview=if(outcome==null) priorReview else ReviewScheduler.schedule(priorReview,outcome,effectiveDate)
            db.execSQL("INSERT OR IGNORE INTO mastery(child_id,character_id,data,review,due,introduced) VALUES (?,?,?,?,?,?)",arrayOf(id,task.characterId,masteryJson(next).toString(),reviewJson(nextReview).toString(),nextReview.due.toString(),day.toString()))
            db.execSQL("UPDATE mastery SET data=?,review=?,due=? WHERE child_id=? AND character_id=?",arrayOf(masteryJson(next).toString(),reviewJson(nextReview).toString(),nextReview.due.toString(),id,task.characterId))
            record(db,id,day,"LEARNING_EVIDENCE",task.json().put("cursor",expectedCursor).put("outcome",outcome?.name ?: "EXPOSURE").put("skill",skill.name).put("source",source.name).put("effective_date",effectiveDate.toString()).put("session_date",day.toString()).put("algorithm_version",1))
            db.execSQL("UPDATE sessions SET cursor=cursor+1 WHERE child_id=? AND day=?",arrayOf(id,day.toString()))
            db.setTransactionSuccessful()
            return true
        } finally { db.endTransaction() }
    }
    fun complete(id:String,day:LocalDate):Boolean {
        val db=writableDatabase; db.beginTransaction()
        try {
            val s=readLesson(id,day) ?: return false
            if(s.completed || s.tasks.isEmpty() || s.cursor<s.tasks.size) return false
            db.execSQL("UPDATE sessions SET completed=1 WHERE child_id=? AND day=?",arrayOf(id,day.toString()))
            db.execSQL("INSERT OR IGNORE INTO daily_stamps(child_id,day) VALUES (?,?)",arrayOf(id,day.toString()))
            record(db,id,day,"DAILY_COMPLETE",JSONObject().put("task_count",s.tasks.size))
            db.setTransactionSuccessful(); return true
        } finally { db.endTransaction() }
    }
    fun mastery(id:String,charId:String):Mastery?=readableDatabase.rawQuery("SELECT data FROM mastery WHERE child_id=? AND character_id=?",arrayOf(id,charId)).use { c -> if(c.moveToFirst()) parseMastery(JSONObject(c.getString(0))) else null }
    fun review(id:String,charId:String):ReviewState?=readableDatabase.rawQuery("SELECT review FROM mastery WHERE child_id=? AND character_id=?",arrayOf(id,charId)).use { c -> if(c.moveToFirst()) parseReview(JSONObject(c.getString(0))) else null }
    fun status(id:String,day:LocalDate):DailyStatus {
        val s=readLesson(id,day)
        val ms=readableDatabase.rawQuery("SELECT data FROM mastery WHERE child_id=?",arrayOf(id)).use { c -> buildList { while(c.moveToNext()) add(parseMastery(JSONObject(c.getString(0)))) } }
        return DailyStatus(child(id),s?.completed==true,s?.cursor ?: 0,s?.tasks?.size ?: 0,ms.size,ms.count { it.phase==MasteryPhase.LEARNING || it.phase==MasteryPhase.REVIEWING },ms.count { it.phase==MasteryPhase.MASTERED })
    }
    fun stampCount(id:String)=scalar("SELECT COUNT(*) FROM daily_stamps WHERE child_id=?",arrayOf(id))
    fun eventCount(id:String)=scalar("SELECT COUNT(*) FROM events WHERE child_id=?",arrayOf(id))
    fun pendingEventCount()=scalar("SELECT COUNT(*) FROM outbox WHERE acknowledged_at IS NULL",emptyArray())
    override fun pending(limit:Int):List<PendingSyncEvent> {
        require(limit in 1..50)
        return readableDatabase.rawQuery("SELECT o.event_id,e.child_id,o.payload,o.attempts FROM outbox o JOIN events e ON e.id=o.event_id WHERE acknowledged_at IS NULL AND e.kind!='CHILD_SETTINGS' ORDER BY e.created_at,o.event_id LIMIT ?",arrayOf(limit.toString())).use { c ->
            buildList { while(c.moveToNext()) add(PendingSyncEvent(c.getString(0),c.getString(1),c.getString(2),c.getInt(3))) }
        }
    }
    override fun recordAttempt(ids:Set<String>) = updateOutbox(ids,false)
    override fun acknowledge(ids:Set<String>) = updateOutbox(ids,true)
    private fun updateOutbox(ids:Set<String>,ack:Boolean) {
        require(ids.size<=50)
        val db=writableDatabase;db.beginTransaction()
        try {
            ids.forEach { id ->
                if(ack) db.execSQL("UPDATE outbox SET acknowledged_at=? WHERE event_id=? AND acknowledged_at IS NULL",arrayOf(Instant.now().toString(),id))
                else db.execSQL("UPDATE outbox SET attempts=attempts+1 WHERE event_id=? AND acknowledged_at IS NULL",arrayOf(id))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    /** Explicit parent export only. No PIN, credentials, raw voice, media or hidden budget. */
    fun parentSnapshot(day:LocalDate):JSONObject {
        val items=children().map { child ->
            val s=status(child.id,day)
            val week=JSONArray((6 downTo 0).map { offset ->
                val date=day.minusDays(offset.toLong());val session=readLesson(child.id,date)
                val introduced=scalar("SELECT COUNT(*) FROM mastery WHERE child_id=? AND introduced=?",arrayOf(child.id,date.toString()))
                JSONObject().put("date",date.toString()).put("completed",session?.completed==true).put("new_count",introduced).put("cursor",session?.cursor ?: 0)
            })
            JSONObject().put("id",child.id).put("display_name",child.name).put("age_group",child.ageGroup)
                .put("daily_target",child.target).put("review_only",child.reviewOnly)
                .put("completed_today",s.completed).put("completed_steps",s.cursor).put("total_steps",s.total)
                .put("introduced",s.learned).put("reviewing",s.reviewing).put("mastered",s.mastered)
                .put("due_reviews",dueCount(child.id,day)).put("daily_stamps",stampCount(child.id)).put("week",week)
        }
        return JSONObject().put("product","China Quest").put("schema_version",1).put("snapshot_id",UUID.randomUUID().toString())
            .put("exported_at",Instant.now().toString()).put("day",day.toString()).put("source","ANDROID_PARENT_EXPORT")
            .put("children",JSONArray(items))
    }
    private fun scalar(sql:String,args:Array<String>)=readableDatabase.rawQuery(sql,args).use { it.moveToFirst();it.getInt(0) }
    private fun record(db:SQLiteDatabase,id:String,day:LocalDate,kind:String,payload:JSONObject) {
        val eventId=UUID.randomUUID().toString();val at=Instant.now().toString()
        db.execSQL("INSERT INTO events VALUES (?,?,?,?,?,?)",arrayOf(eventId,id,day.toString(),kind,payload.toString(),at))
        val envelope=JSONObject().put("id",eventId).put("child_id",id).put("kind",kind).put("day",day.toString()).put("payload",payload).put("created_at",at).put("schema_version",1)
        // Parent configuration requires a separate authenticated command path, not child sync.
        if(kind!="CHILD_SETTINGS") db.execSQL("INSERT INTO outbox(event_id,payload) VALUES (?,?)",arrayOf(eventId,envelope.toString()))
    }
    private fun masteryJson(m:Mastery)=JSONObject().put("recognition",m.recognition).put("pronunciation",m.pronunciation).put("meaning",m.meaning).put("writing",m.writing).put("word",m.word).put("successfulDays",m.successfulDays).put("lastSuccessDate",m.lastSuccessDate?.toString() ?: JSONObject.NULL).put("parentChecks",m.parentChecks).put("totalAttempts",m.totalAttempts)
    private fun parseMastery(j:JSONObject)=Mastery(j.getInt("recognition"),j.getInt("pronunciation"),j.getInt("meaning"),j.getInt("writing"),j.getInt("word"),j.getInt("successfulDays"),if(j.isNull("lastSuccessDate")) null else LocalDate.parse(j.getString("lastSuccessDate")),j.getInt("parentChecks"),j.getInt("totalAttempts"))
    private fun reviewJson(r:ReviewState)=JSONObject().put("step",r.step).put("due",r.due.toString()).put("lastReviewed",r.lastReviewed?.toString() ?: JSONObject.NULL).put("lapses",r.lapses)
    private fun parseReview(j:JSONObject)=ReviewState(j.getInt("step"),LocalDate.parse(j.getString("due")),if(j.isNull("lastReviewed")) null else LocalDate.parse(j.getString("lastReviewed")),j.getInt("lapses"))
}
