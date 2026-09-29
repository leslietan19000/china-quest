package org.chinaquest.app

import java.text.Normalizer
import kotlin.math.abs
import kotlin.random.Random

/** Finite, reproducible choices. Similar shapes and current lesson items replace fixed easy decoys. */
object QuizChoices {
    fun forCard(target: CharacterCard, all: List<CharacterCard>, lessonIds: Set<String>, salt: String, count: Int): List<CharacterCard> {
        require(count in 2..4)
        val targetReadings=target.pinyin.split(' ').filter { it.isNotBlank() }.toSet()
        fun base(value:String)=Normalizer.normalize(value,Normalizer.Form.NFD).replace(Regex("\\p{M}"),"")
        fun score(card:CharacterCard):Int =
            (if(card.id in lessonIds) 8 else 0) +
            (if(card.radical==target.radical) 4 else 0) +
            (4-abs(card.strokes-target.strokes)).coerceAtLeast(0) +
            (if(base(card.pinyin)==base(target.pinyin)) 6 else 0)
        val alternatives=all.filter { it.id!=target.id && it.pinyin.split(' ').none(targetReadings::contains) }
            .distinctBy { it.id }.shuffled(Random(salt.hashCode())).sortedByDescending(::score)
            .take(count-1)
        return (alternatives+target).shuffled(Random((target.id+salt).hashCode()))
    }
}
