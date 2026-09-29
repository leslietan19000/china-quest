package org.chinaquest.app

import org.junit.Assert.*
import org.junit.Test

class QuizChoicesTest {
    private fun card(id:String,sound:String,strokes:Int=3,radical:String="口") = CharacterCard(id,id,sound,radical,strokes,"",null,null,emptyList(),null,false)
    @Test fun alternativesAvoidAmbiguousReadingsAndPreferCurrentLesson() {
        val target=card("甲","jiǎ")
        val sameSound=card("假","jiǎ")
        val multiReading=card("多","jiǎ jià")
        val near=card("乙","yǐ")
        val another=card("丙","bǐng")
        val extra=card("丁","dīng")
        val remote=card("远","yuǎn",18,"金")
        val all=listOf(target,sameSound,multiReading,near,another,extra,remote)
        val choices=QuizChoices.forCard(target,all,setOf("乙","丙","丁"),"day:1",4)
        assertEquals(setOf("甲","乙","丙","丁"),choices.map { it.id }.toSet())
        assertEquals(choices,QuizChoices.forCard(target,all,setOf("乙","丙","丁"),"day:1",4))
        assertEquals(3,QuizChoices.forCard(target,all,emptySet(),"day:2",3).size)
        assertTrue((0..8).map { QuizChoices.forCard(target,all,emptySet(),"day:$it",4).indexOf(target) }.toSet().size>1)
    }
}
