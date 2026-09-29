package org.chinaquest.app

import org.json.JSONObject
import java.time.LocalDate

data class Child(val id: String, val name: String, val ageGroup: String, val target: Int, val reviewOnly: Boolean)
data class CharacterCard(
    val id: String, val character: String, val pinyin: String, val radical: String,
    val strokes: Int, val meaningEn: String, val meaningZh: String?, val meaningEs: String?,
    val words: List<String>, val sentence: String?, val approvedTeaching: Boolean
)
data class Task(val kind: String, val characterId: String) {
    fun json() = JSONObject().put("kind", kind).put("character_id", characterId)
}
data class Lesson(val childId: String, val date: LocalDate, val tasks: List<Task>, val cursor: Int, val completed: Boolean) {
    val newCount get() = tasks.count { it.kind == "LEARN" }
    val reviewCount get() = tasks.count { it.kind == "REVIEW" }
}
data class DailyStatus(val child: Child, val completed: Boolean, val cursor: Int, val total: Int, val learned: Int, val reviewing: Int, val mastered: Int)
