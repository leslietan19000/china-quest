package org.chinaquest.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import java.time.Instant

enum class CreativeFeedback { KEEP, CHANGE, NO }

data class CreativeOpinion(val featureId:String, val decision:CreativeFeedback)

/** Private creative work is separate from learning evidence and can never mint mastery or stamps. */
class CreativeStore(
    context:Context,
    private val childIds:Set<String>,
    private val characterIds:Set<String>,
    private val sceneIds:Set<String>,
    name:String="china-quest-creative.db",
) : SQLiteOpenHelper(context,name,null,1) {
    companion object {
        const val MAX_ART_BYTES=262_144
        const val PROTOTYPE_VERSION="0.3.0-preview"
        val FEATURE_IDS=setOf("coloring","scenes")
    }
    override fun onCreate(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE artworks(child_id TEXT NOT NULL,character_id TEXT NOT NULL,artwork TEXT NOT NULL,updated_at TEXT NOT NULL,PRIMARY KEY(child_id,character_id))")
        db.execSQL("CREATE TABLE scene_progress(child_id TEXT NOT NULL,scene_id TEXT NOT NULL,step INTEGER NOT NULL CHECK(step BETWEEN 0 AND 8),updated_at TEXT NOT NULL,PRIMARY KEY(child_id,scene_id))")
        db.execSQL("CREATE TABLE creative_feedback(child_id TEXT NOT NULL,feature_id TEXT NOT NULL,version TEXT NOT NULL,decision TEXT NOT NULL CHECK(decision IN ('KEEP','CHANGE','NO')),updated_at TEXT NOT NULL,PRIMARY KEY(child_id,feature_id,version))")
    }
    override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int) {
        error("Creative data needs an additive migration: $oldVersion -> $newVersion")
    }
    private fun child(id:String) { require(id in childIds) { "Unknown child" } }
    private fun artworkKey(childId:String,characterId:String) { child(childId);require(characterId in characterIds) { "Unknown character" } }
    private fun sceneKey(childId:String,sceneId:String) { child(childId);require(sceneId in sceneIds) { "Unknown scene" } }

    fun artwork(childId:String,characterId:String):String? {
        artworkKey(childId,characterId)
        return readableDatabase.rawQuery("SELECT artwork FROM artworks WHERE child_id=? AND character_id=?",arrayOf(childId,characterId))
            .use { if(it.moveToFirst()) it.getString(0) else null }
    }
    fun saveArtwork(childId:String,characterId:String,artwork:String) {
        artworkKey(childId,characterId)
        require(artwork.toByteArray(Charsets.UTF_8).size<=MAX_ART_BYTES) { "Artwork exceeds device budget" }
        try { JSONObject(artwork) } catch(error:Exception) { throw IllegalArgumentException("Artwork must be a JSON object",error) }
        writableDatabase.execSQL("INSERT OR REPLACE INTO artworks(child_id,character_id,artwork,updated_at) VALUES (?,?,?,?)",
            arrayOf(childId,characterId,artwork,Instant.now().toString()))
    }
    fun artworkCount(childId:String):Int {
        child(childId)
        return readableDatabase.rawQuery("SELECT COUNT(*) FROM artworks WHERE child_id=?",arrayOf(childId)).use { it.moveToFirst();it.getInt(0) }
    }
    fun sceneStep(childId:String,sceneId:String):Int {
        sceneKey(childId,sceneId)
        return readableDatabase.rawQuery("SELECT step FROM scene_progress WHERE child_id=? AND scene_id=?",arrayOf(childId,sceneId))
            .use { if(it.moveToFirst()) it.getInt(0) else 0 }
    }
    fun saveSceneStep(childId:String,sceneId:String,step:Int) {
        sceneKey(childId,sceneId);require(step in 0..8)
        writableDatabase.execSQL("INSERT OR REPLACE INTO scene_progress(child_id,scene_id,step,updated_at) VALUES (?,?,?,?)",
            arrayOf(childId,sceneId,step,Instant.now().toString()))
    }
    fun saveFeedback(childId:String,featureId:String,decision:CreativeFeedback) {
        child(childId);require(featureId in FEATURE_IDS)
        writableDatabase.execSQL("INSERT OR REPLACE INTO creative_feedback(child_id,feature_id,version,decision,updated_at) VALUES (?,?,?,?,?)",
            arrayOf(childId,featureId,PROTOTYPE_VERSION,decision.name,Instant.now().toString()))
    }
    fun feedback(childId:String):List<CreativeOpinion> {
        child(childId)
        return readableDatabase.rawQuery("SELECT feature_id,decision FROM creative_feedback WHERE child_id=? AND version=? ORDER BY feature_id",
            arrayOf(childId,PROTOTYPE_VERSION)).use { cursor -> buildList {
                while(cursor.moveToNext()) add(CreativeOpinion(cursor.getString(0),CreativeFeedback.valueOf(cursor.getString(1))))
            } }
    }
}
