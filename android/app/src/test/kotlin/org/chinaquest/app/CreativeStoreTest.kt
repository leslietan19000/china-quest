package org.chinaquest.app

import android.content.Context
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class CreativeStoreTest {
    private lateinit var context:Context
    private lateinit var store:CreativeStore
    private val name="creative-store-test.db"
    private fun open()=CreativeStore(context,setOf("a","b"),setOf("mouth","water"),setOf("mouth-eats"),name)
    @Before fun before() { context=RuntimeEnvironment.getApplication();context.deleteDatabase(name);store=open() }
    @After fun after() { store.close();context.deleteDatabase(name) }

    @Test fun workAndFeedbackRemainIndependentAfterReopen() {
        store.saveArtwork("a","mouth","{\"color\":\"red\"}")
        store.saveArtwork("b","mouth","{\"color\":\"blue\"}")
        store.saveSceneStep("a","mouth-eats",2)
        store.saveFeedback("a","coloring",CreativeFeedback.NO)
        store.saveFeedback("b","coloring",CreativeFeedback.KEEP)
        store.close();store=open()
        assertEquals("{\"color\":\"red\"}",store.artwork("a","mouth"))
        assertEquals("{\"color\":\"blue\"}",store.artwork("b","mouth"))
        assertNull(store.artwork("a","water"))
        assertEquals(1,store.artworkCount("a"))
        assertEquals(2,store.sceneStep("a","mouth-eats"))
        assertEquals(0,store.sceneStep("b","mouth-eats"))
        assertEquals(CreativeFeedback.NO,store.feedback("a").single().decision)
        assertEquals(CreativeFeedback.KEEP,store.feedback("b").single().decision)
        store.saveFeedback("a","coloring",CreativeFeedback.CHANGE)
        assertEquals(1,store.feedback("a").size)
        assertEquals(CreativeFeedback.CHANGE,store.feedback("a").single().decision)
        assertEquals(CreativeFeedback.KEEP,store.feedback("b").single().decision)
    }

    private fun rejected(action:()->Unit) {
        try { action();fail("Invalid creative data accepted") } catch(_:IllegalArgumentException) { }
    }
    @Test fun invalidScopePayloadAndBoundsCannotOverwriteWork() {
        val safe="{\"v\":1}"
        store.saveArtwork("a","mouth",safe)
        rejected { store.saveArtwork("stranger","mouth",safe) }
        rejected { store.artwork("stranger","mouth") }
        rejected { store.saveArtwork("a","unknown",safe) }
        rejected { store.saveArtwork("a","mouth","not JSON") }
        rejected { store.saveArtwork("a","mouth","{\"text\":\""+"字".repeat(CreativeStore.MAX_ART_BYTES/2)+"\"}") }
        rejected { store.saveSceneStep("a","unknown",1) }
        rejected { store.saveSceneStep("a","mouth-eats",9) }
        rejected { store.saveSceneStep("a","mouth-eats",-1) }
        rejected { store.saveFeedback("a","production-approval",CreativeFeedback.KEEP) }
        rejected { store.feedback("stranger") }
        assertEquals(safe,store.artwork("a","mouth"))
        assertEquals(0,store.sceneStep("a","mouth-eats"))
        assertTrue(store.feedback("a").isEmpty())
    }

    @Test fun earlierVersionOpinionsAreKeptWithoutBecomingCurrentApproval() {
        store.writableDatabase.execSQL("INSERT INTO creative_feedback VALUES ('a','coloring','older-preview','KEEP','2026-09-28')")
        assertTrue(store.feedback("a").isEmpty())
        store.saveFeedback("a","coloring",CreativeFeedback.NO)
        store.readableDatabase.rawQuery("SELECT COUNT(*) FROM creative_feedback WHERE child_id='a'",null).use {
            assertTrue(it.moveToFirst());assertEquals(2,it.getInt(0))
        }
        assertEquals(CreativeFeedback.NO,store.feedback("a").single().decision)
    }
}
