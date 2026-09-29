package org.chinaquest.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w320dp-h480dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SceneCatalogTest {
    @Test fun verifiedCharactersHaveFiniteScenesAndNoUnknownCharacterDoes() {
        val ids = listOf("hanzi-53E3", "hanzi-5403", "hanzi-5F00", "hanzi-5173", "hanzi-6C34", "hanzi-559D")
        ids.forEach { id ->
            val definition = requireNotNull(SceneCatalog.forCharacter(id))
            assertEquals(id, definition.characterId)
            var state = SceneCatalog.initial(definition.id)
            val seen = mutableListOf<String>()
            repeat(SceneCatalog.maxStep(definition.id)) {
                val stage = SceneCatalog.stage(state)
                assertFalse(stage.complete)
                assertNotNull(stage.actionLabel)
                assertNotNull(stage.hotspotDescription)
                seen += stage.prompt
                state = SceneCatalog.advance(state)
            }
            assertEquals(SceneCatalog.maxStep(definition.id), state.step)
            assertTrue(SceneCatalog.stage(state).complete)
            assertNull(SceneCatalog.stage(state).actionLabel)
            assertEquals(state, SceneCatalog.advance(state))
            assertEquals(0, SceneCatalog.replay(state).step)
            assertEquals(seen.size, seen.distinct().size)
        }
        assertNull(SceneCatalog.forCharacter("hanzi-4E00"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidSavedStepIsRejectedForCallerRecovery() {
        SceneCatalog.stage(SceneState("open-door", 99))
    }

    @Test fun staticCanvasDrawsEveryStageAndTapAdvancesOnlyOnHotspot() {
        val context = RuntimeEnvironment.getApplication()
        val view = CharacterSceneView(context)
        val width = 320
        val height = 310
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            listOf("mouth-eats", "eat-apple", "open-door", "close-door", "water-cup", "drink-water").forEach { id ->
                var advances = 0
                (0..SceneCatalog.maxStep(id)).forEach { step ->
                    val state = SceneState(id, step)
                    view.render(state) { advances++ }
                    view.draw(Canvas(bitmap))
                    assertEquals(step < SceneCatalog.maxStep(id), view.isClickable)
                    assertNotNull(view.contentDescription)
                }
                val state = SceneCatalog.initial(id)
                view.render(state) { advances++ }
                val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 310f, 300f, 0)
                val up = MotionEvent.obtain(0, 1, MotionEvent.ACTION_UP, 310f, 300f, 0)
                try { view.onTouchEvent(down); view.onTouchEvent(up) } finally { down.recycle(); up.recycle() }
                assertEquals(0, advances)
                assertTrue(view.performClick()) // accessibility and the separate button use the same action
                assertEquals(1, advances)
            }
        } finally { bitmap.recycle() }
    }

    @Test fun touchNeedsMatchingDownAndUpAndCancelsOnInterruption() {
        val view = CharacterSceneView(RuntimeEnvironment.getApplication())
        view.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(310, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 320, 310)
        var advances = 0
        view.render(SceneCatalog.initial("open-door")) { advances++ }
        val bitmap = Bitmap.createBitmap(320, 310, Bitmap.Config.ARGB_8888)
        try { view.draw(Canvas(bitmap)) } finally { bitmap.recycle() } // sets the measured art transform
        fun send(action: Int, vararg pointers: Triple<Int, Float, Float>): Boolean {
            val properties = Array(pointers.size) { index -> MotionEvent.PointerProperties().apply {
                id = pointers[index].first
                toolType = MotionEvent.TOOL_TYPE_FINGER
            } }
            val coordinates = Array(pointers.size) { index -> MotionEvent.PointerCoords().apply {
                x = pointers[index].second
                y = pointers[index].third
                pressure = 1f
                size = 1f
            } }
            val event = MotionEvent.obtain(0L, 1L, action, pointers.size, properties, coordinates, 0, 0, 1f, 1f, 0, 0, 0, 0)
            return try { view.onTouchEvent(event) } finally { event.recycle() }
        }
        val inside = Triple(3, 200f, 153f)
        val outside = Triple(3, 10f, 10f)
        assertFalse(send(MotionEvent.ACTION_UP, inside)) // no preceding DOWN
        assertFalse(send(MotionEvent.ACTION_DOWN, outside))
        assertFalse(send(MotionEvent.ACTION_UP, inside))
        assertEquals(0, advances)

        assertTrue(send(MotionEvent.ACTION_DOWN, inside))
        assertTrue(send(MotionEvent.ACTION_CANCEL, inside))
        assertFalse(send(MotionEvent.ACTION_UP, inside))
        assertEquals(0, advances)

        assertTrue(send(MotionEvent.ACTION_DOWN, inside))
        assertFalse(send(MotionEvent.ACTION_UP, Triple(8, 200f, 153f))) // wrong pointer ID
        assertEquals(0, advances)

        assertTrue(send(MotionEvent.ACTION_DOWN, inside))
        assertFalse(send(MotionEvent.ACTION_MOVE, outside))
        assertFalse(send(MotionEvent.ACTION_UP, inside)) // leaving the hotspot disarms the gesture
        assertEquals(0, advances)

        assertTrue(send(MotionEvent.ACTION_DOWN, inside))
        val other = Triple(4, 230f, 153f)
        assertTrue(send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), inside, other))
        assertFalse(send(MotionEvent.ACTION_POINTER_UP, inside, other)) // active pointer lifted
        assertFalse(send(MotionEvent.ACTION_UP, other))
        assertEquals(0, advances)

        assertTrue(send(MotionEvent.ACTION_DOWN, inside))
        assertTrue(send(MotionEvent.ACTION_POINTER_UP, inside, other))
        assertFalse(send(MotionEvent.ACTION_UP, other))
        assertEquals(0, advances)

        assertTrue(send(MotionEvent.ACTION_DOWN, inside))
        assertTrue(send(MotionEvent.ACTION_UP, inside))
        assertEquals(1, advances)
        assertTrue(view.performClick()) // accessibility click remains available
        assertEquals(2, advances)
    }
}
