package org.chinaquest.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import java.io.File
import java.time.LocalDate
import org.chinaquest.core.ReviewOutcome
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Uses only synthetic children; native rendering does not certify BOOX latency or color. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], qualifiers="w744dp-h992dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CreativeActivityAcceptanceTest {
    private lateinit var context:Context
    private lateinit var controller:ActivityController<MainActivity>
    private lateinit var activity:MainActivity
    private lateinit var learning:QuestStore
    private lateinit var creative:CreativeStore
    private val day get()=LocalDate.now()
    @Before fun before() {
        context=RuntimeEnvironment.getApplication()
        context.deleteDatabase("china-quest.db");context.deleteDatabase("china-quest-creative.db")
        context.getSharedPreferences("parent_gate",0).edit().clear().commit()
        ParentGate(context).setPin("458726")
        learning=QuestStore(context)
        creative=CreativeStore(context,learning.children().map { it.id }.toSet(),learning.cards().map { it.id }.toSet(),
            learning.cards().mapNotNull { SceneCatalog.forCharacter(it.id)?.id }.toSet())
        controller=Robolectric.buildActivity(MainActivity::class.java).setup();activity=controller.get()
    }
    @After fun after() {
        controller.pause().stop().destroy();creative.close();learning.close()
        context.deleteDatabase("china-quest.db");context.deleteDatabase("china-quest-creative.db")
    }
    private fun view(tag:String):View=requireNotNull(activity.window.decorView.findViewWithTag<View>(tag)) { "Missing $tag" }
    private fun click(tag:String) { assertTrue(view(tag).performClick()) }
    private fun recreate() { controller.recreate();activity=controller.get() }
    private fun layout() {
        activity.window.decorView.apply {
            measure(View.MeasureSpec.makeMeasureSpec(744,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(992,View.MeasureSpec.EXACTLY))
            layout(0,0,744,992)
        }
    }
    private fun capture(name:String) {
        layout()
        val bitmap=Bitmap.createBitmap(744,992,Bitmap.Config.ARGB_8888)
        try {
            activity.window.decorView.draw(Canvas(bitmap))
            val file=File("build/screenshots/$name.png");file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        } finally { bitmap.recycle() }
    }
    private fun touch(view:View,action:Int,x:Float,y:Float) {
        val event=MotionEvent.obtain(0,10,action,x,y,0)
        try { view.onTouchEvent(event) } finally { event.recycle() }
    }
    private fun fillGlyph() {
        layout()
        val canvas=view("coloring-canvas") as ColoringCanvasView
        for(y in 10 until canvas.height step 10) for(x in 10 until canvas.width step 10) {
            touch(canvas,MotionEvent.ACTION_DOWN,x.toFloat(),y.toFloat())
            touch(canvas,MotionEvent.ACTION_UP,x.toFloat(),y.toFloat())
            if(!canvas.isEmpty) return
        }
        fail("No usable glyph hit area")
    }
    private fun screenText():String {
        fun text(v:View):String=when(v) {
            is TextView -> v.text.toString()
            is ViewGroup -> (0 until v.childCount).joinToString("\n") { text(v.getChildAt(it)) }
            else -> ""
        }
        return text(activity.window.decorView)
    }

    @Test fun coloringSurvivesRecreationAndChildSwitchWithoutChangingLessonEvidence() {
        click("child-a");click("start");click("learn-done")
        val before=learning.status("child-a",day)
        val card=learning.lesson("child-a",day).tasks[before.cursor].characterId
        val mastery=learning.mastery("child-a",card)
        val events=learning.eventCount("child-a")
        click("paint-this");click("color-2");fillGlyph()
        val saved=requireNotNull(creative.artwork("child-a",card))
        assertTrue((view("artwork-status") as TextView).text.contains("保存在"))
        recreate()
        assertEquals(saved,(view("coloring-canvas") as ColoringCanvasView).exportArtwork())
        click("art-clear");assertTrue((view("coloring-canvas") as ColoringCanvasView).isEmpty)
        click("art-undo");assertEquals(saved,(view("coloring-canvas") as ColoringCanvasView).exportArtwork())
        click("feedback-NO");click("art-done")
        assertNotNull(view("learn-done"))
        assertEquals(before,learning.status("child-a",day))
        assertEquals(mastery,learning.mastery("child-a",card))
        assertEquals(events,learning.eventCount("child-a"));assertEquals(0,learning.stampCount("child-a"))
        click("pause");click("switch-child");click("child-b");click("start");click("learn-done");click("paint-this")
        assertTrue((view("coloring-canvas") as ColoringCanvasView).isEmpty)
        assertNull(creative.artwork("child-b",card));assertTrue(creative.feedback("child-b").isEmpty())
        click("color-0");fillGlyph()
        assertEquals(saved,creative.artwork("child-a",card))
        assertNotEquals(saved,creative.artwork("child-b",card))
        click("art-back");click("pause");click("switch-child");click("parent")
        (view("parent-pin") as EditText).setText("458726");click("unlock-parent")
        assertTrue(screenText().contains("涂色试用反馈：不喜欢"))
        assertTrue(ParentGate(context).verify("458726"))
    }

    @Test fun scenesAreFinitePersistedOptionalAndSeparatePerChild() {
        click("child-a");val before=learning.status("child-a",day)
        click("workshop");click("scene-hanzi-5F00");click("scene-action")
        assertEquals(1,creative.sceneStep("child-a","open-door"))
        recreate();assertTrue((view("scene-prompt") as TextView).text.contains("再"))
        click("scene-action")
        assertEquals(2,creative.sceneStep("child-a","open-door"))
        assertEquals(View.GONE,view("scene-action").visibility)
        assertFalse(view("scene-canvas").isClickable)
        assertFalse(view("scene-canvas").performClick())
        assertEquals(2,creative.sceneStep("child-a","open-door"))
        click("feedback-CHANGE");click("scene-replay")
        assertEquals(0,creative.sceneStep("child-a","open-door"))
        click("scene-action");click("scene-done");click("workshop-back")
        assertEquals(before,learning.status("child-a",day))
        assertEquals(0,learning.pendingEventCount())
        click("switch-child");click("child-b");click("workshop");click("scene-hanzi-5F00")
        assertEquals(0,creative.sceneStep("child-b","open-door"))
        assertEquals(1,creative.sceneStep("child-a","open-door"))
        assertTrue(creative.feedback("child-b").isEmpty())
    }

    @Test fun previewOpensExistingVersionTwoLearningRecordsAndPinUnchanged() {
        val old=learning.lesson("child-a",day)
        old.tasks.forEachIndexed { index,task -> learning.answer("child-a",day,index,if(task.kind=="LEARN") null else ReviewOutcome.CORRECT) }
        learning.complete("child-a",day)
        val previous=learning.status("child-a",day)
        val mastery=learning.mastery("child-a",old.tasks.first().characterId)
        val events=learning.eventCount("child-a")
        assertEquals(2,learning.readableDatabase.version)
        recreate();click("child-a");click("workshop");click("scene-hanzi-53E3");click("scene-action")
        click("scene-done");click("workshop-back")
        assertEquals(previous,learning.status("child-a",day))
        assertEquals(mastery,learning.mastery("child-a",old.tasks.first().characterId))
        assertEquals(events,learning.eventCount("child-a"))
        assertEquals(1,learning.stampCount("child-a"));assertEquals(0,learning.stampCount("child-b"))
        assertEquals(2,learning.readableDatabase.version)
        assertTrue(ParentGate(context).verify("458726"))
    }

    @Test fun renderWorkshopColoringAndParticipatoryScenes() {
        click("child-a");click("workshop");capture("v3-01-workshop")
        click("choose-coloring");click("paint-hanzi-53E3");fillGlyph()
        click("tool-brush");click("color-2")
        val drawing=view("coloring-canvas")
        touch(drawing,MotionEvent.ACTION_DOWN,250f,155f)
        touch(drawing,MotionEvent.ACTION_MOVE,450f,155f)
        touch(drawing,MotionEvent.ACTION_UP,450f,155f)
        capture("v3-02-coloring")
        click("art-back");click("paint-picker-back")
        click("scene-hanzi-53E3");capture("v3-03-mouth-start")
        repeat(3) { click("scene-action") };capture("v3-04-mouth-end")
        click("scene-done");click("scene-hanzi-5F00");capture("v3-05-door-closed")
        repeat(2) { click("scene-action") };capture("v3-06-door-open")
        click("scene-done");click("scene-hanzi-559D");click("scene-action");capture("v3-07-drink")
        assertEquals(0,learning.eventCount("child-a"))
    }

    @Test fun failedSaveKeepsDrawingThroughBackAndRecreationUntilStorageRecovers() {
        click("child-a");click("workshop");click("choose-coloring");click("paint-hanzi-53E3")
        creative.writableDatabase.execSQL("CREATE TRIGGER simulate_unwritable_art BEFORE INSERT ON artworks BEGIN SELECT RAISE(ABORT,'simulated storage failure'); END")
        fillGlyph()
        val pending=(view("coloring-canvas") as ColoringCanvasView).exportArtwork()
        assertNull(creative.artwork("child-a","hanzi-53E3"))
        click("art-done");assertNotNull(view("coloring-canvas"))
        assertTrue((view("artwork-status") as TextView).text.contains("还没存好"))
        @Suppress("DEPRECATION")
        activity.onBackPressed()
        assertEquals(pending,(view("coloring-canvas") as ColoringCanvasView).exportArtwork())
        recreate()
        assertEquals(pending,(view("coloring-canvas") as ColoringCanvasView).exportArtwork())
        creative.writableDatabase.execSQL("DROP TRIGGER simulate_unwritable_art")
        click("art-done")
        assertNotNull(view("paint-hanzi-53E3"))
        assertEquals(pending,creative.artwork("child-a","hanzi-53E3"))
        assertNull(creative.artwork("child-b","hanzi-53E3"))
        assertEquals(0,learning.eventCount("child-a"))
    }
}
