package org.chinaquest.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Region
import android.view.MotionEvent
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ColoringCanvasViewTest {
    private fun view(size: Int = 400) = ColoringCanvasView(RuntimeEnvironment.getApplication()).apply {
        character = "口"
        measure(viewMeasure(size), viewMeasure(size))
        layout(0, 0, size, size)
    }

    private fun viewMeasure(size: Int) = android.view.View.MeasureSpec.makeMeasureSpec(size, android.view.View.MeasureSpec.EXACTLY)

    private fun touch(view: ColoringCanvasView, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0L, 10L, action, x, y, 0)
        try { view.onTouchEvent(event) } finally { event.recycle() }
    }

    private fun stroke(view: ColoringCanvasView, x: Float, y: Float) {
        touch(view, MotionEvent.ACTION_DOWN, x, y)
        touch(view, MotionEvent.ACTION_MOVE, x + 20f, y + 20f)
        touch(view, MotionEvent.ACTION_UP, x + 20f, y + 20f)
    }

    private fun render(view: ColoringCanvasView, size: Int): Bitmap =
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }

    private fun isRed(color: Int) = Color.red(color) > 170 && Color.green(color) < 110 && Color.blue(color) < 110
    private fun isBlue(color: Int) = Color.blue(color) > 170 && Color.red(color) < 110 && Color.green(color) < 110
    private fun isDark(color: Int) = Color.alpha(color) > 250 && Color.red(color) < 40 && Color.green(color) < 40 && Color.blue(color) < 40

    @Test fun clearCanBeUndoneAndChangesAreSentOnlyAfterGestures() {
        val view = view()
        var notifications = 0
        view.onArtworkChanged = { notifications++ }
        touch(view, MotionEvent.ACTION_DOWN, 100f, 100f)
        touch(view, MotionEvent.ACTION_MOVE, 120f, 120f)
        assertEquals(0, notifications)
        touch(view, MotionEvent.ACTION_UP, 120f, 120f)
        assertEquals(1, notifications)
        assertFalse(view.isEmpty)
        assertTrue(view.clear())
        assertTrue(view.isEmpty)
        assertEquals(2, notifications)
        assertTrue(view.undo())
        assertFalse(view.isEmpty)
        assertEquals(3, notifications)
        assertTrue(view.undo())
        assertTrue(view.isEmpty)
        assertFalse(view.undo())
    }

    @Test fun canceledStrokeDoesNotChangeArtwork() {
        val view = view()
        val blank = view.exportArtwork()
        var notifications = 0
        view.onArtworkChanged = { notifications++ }
        touch(view, MotionEvent.ACTION_DOWN, 100f, 100f)
        touch(view, MotionEvent.ACTION_MOVE, 130f, 130f)
        touch(view, MotionEvent.ACTION_CANCEL, 130f, 130f)
        assertEquals(blank, view.exportArtwork())
        assertEquals(0, notifications)
    }

    @Test fun liftingDrawingPointerAndDetachingDiscardPreview() {
        val view = view()
        val blank = view.exportArtwork()
        touch(view, MotionEvent.ACTION_DOWN, 100f, 100f)
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0 }, MotionEvent.PointerProperties().apply { id = 1 })
        val coordinates = arrayOf(
            MotionEvent.PointerCoords().apply { x = 120f; y = 120f; pressure = 1f; size = 1f },
            MotionEvent.PointerCoords().apply { x = 200f; y = 200f; pressure = 1f; size = 1f },
        )
        val lifted = MotionEvent.obtain(0L, 10L, MotionEvent.ACTION_POINTER_UP, 2, properties, coordinates, 0, 0, 1f, 1f, 0, 0, 0, 0)
        try { assertTrue(view.onTouchEvent(lifted)) } finally { lifted.recycle() }
        touch(view, MotionEvent.ACTION_UP, 200f, 200f)
        assertEquals(blank, view.exportArtwork())

        touch(view, MotionEvent.ACTION_DOWN, 100f, 100f)
        val detach = ColoringCanvasView::class.java.getDeclaredMethod("onDetachedFromWindow")
        detach.isAccessible = true
        detach.invoke(view)
        touch(view, MotionEvent.ACTION_UP, 120f, 120f)
        assertEquals(blank, view.exportArtwork())
    }

    @Test fun artworkRoundTripsWithNormalizedCoordinatesAcrossResize() {
        val first = view(400)
        first.selectedColor = Color.BLUE
        stroke(first, 100f, 100f)
        val json = first.exportArtwork()
        val points = JSONObject(json).getJSONArray("actions").getJSONObject(0).getJSONArray("points")
        assertTrue(points.getJSONArray(0).getInt(0) in 0..1000)
        assertTrue(points.getJSONArray(0).getInt(1) in 0..1000)
        val second = view(620)
        second.loadArtwork(json)
        assertEquals(json, second.exportArtwork())
        assertFalse(second.isEmpty)
        second.character = "中"
        assertTrue(second.isEmpty)
        second.loadArtwork(json)
        assertTrue(second.isEmpty)
    }

    @Test fun rejectsMalformedAndOversizedArtworkWithoutPartialState() {
        val view = view()
        stroke(view, 100f, 100f)
        val badPoint = JSONObject().put("type", "stroke").put("color", Color.BLUE)
            .put("points", JSONArray().put(JSONArray().put(1001).put(0)))
        val bad = JSONObject().put("v", 1).put("character", "口")
            .put("actions", JSONArray().put(JSONObject().put("type", "fill").put("color", Color.RED)).put(badPoint))
        view.loadArtwork(bad.toString())
        assertTrue(view.isEmpty)
        val tooMany = JSONObject().put("v", 1).put("character", "口")
            .put("actions", JSONArray().apply { repeat(129) { put(JSONObject().put("type", "fill").put("color", Color.RED)) } })
        view.loadArtwork(tooMany.toString())
        assertTrue(view.isEmpty)
    }

    @Test fun fillAndClearAreSerializedAsUndoableActions() {
        val view = view()
        view.tool = ColoringTool.FILL
        outer@ for (y in 40..360 step 20) {
            for (x in 40..360 step 20) {
                touch(view, MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat())
                touch(view, MotionEvent.ACTION_UP, x.toFloat(), y.toFloat())
                if (!view.isEmpty) break@outer
            }
        }
        assertFalse(view.isEmpty)
        assertEquals("fill", JSONObject(view.exportArtwork()).getJSONArray("actions").getJSONObject(0).getString("type"))
        view.clear()
        assertEquals("clear", JSONObject(view.exportArtwork()).getJSONArray("actions").getJSONObject(1).getString("type"))
    }

    @Test fun artworkLimitDoesNotSilentlyDiscardEarlierColor() {
        val view = view()
        val actions = JSONArray().apply {
            repeat(128) { put(JSONObject().put("type", "fill").put("color", Color.RED)) }
        }
        view.loadArtwork(JSONObject().put("v", 1).put("character", "口").put("actions", actions).toString())
        val before = view.exportArtwork()
        var status = ""
        view.onStatusMessage = { status = it }
        stroke(view, 100f, 100f)
        assertEquals(before, view.exportArtwork())
        assertTrue(status.contains("画布已满"))
        assertTrue(view.undo())
        assertTrue(view.clear())
    }

    @Test fun nativeRenderingClipsRealColorToGlyphAndKeepsOutlineAfterResize() {
        val view = view()
        val before = render(view, 400)
        val originalDark = (0 until 400).sumOf { y -> (0 until 400).count { x -> isDark(before.getPixel(x, y)) } }
        assertTrue("real glyph outline should render", originalDark > 500)
        assertEquals("the hole in 口 should be blank", Color.WHITE, before.getPixel(200, 200))

        view.tool = ColoringTool.FILL
        view.selectedColor = Color.RED
        outer@ for (y in 40..360 step 20) for (x in 40..360 step 20) {
            touch(view, MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat())
            touch(view, MotionEvent.ACTION_UP, x.toFloat(), y.toFloat())
            if (!view.isEmpty) break@outer
        }
        assertFalse("fill requires a real hittable glyph path", view.isEmpty)
        view.tool = ColoringTool.BRUSH
        view.selectedColor = Color.BLUE
        touch(view, MotionEvent.ACTION_DOWN, 40f, 200f)
        touch(view, MotionEvent.ACTION_MOVE, 360f, 200f)
        touch(view, MotionEvent.ACTION_UP, 360f, 200f)
        val saved = view.exportArtwork()

        fun verify(bitmap: Bitmap, size: Int, expectedDark: Int) {
            val hit = ColoringCanvasView::class.java.getDeclaredField("glyphHit").apply { isAccessible = true }.get(view) as Region
            val square = ColoringCanvasView::class.java.getDeclaredField("square").apply { isAccessible = true }.get(view) as RectF
            var red = 0
            var blue = 0
            var dark = 0
            for (y in 0 until size) for (x in 0 until size) {
                val pixel = bitmap.getPixel(x, y)
                if (isDark(pixel)) dark++
                if (isRed(pixel) || isBlue(pixel)) {
                    val gx = ((x - square.left) / square.width() * 1000).toInt()
                    val gy = ((y - square.top) / square.height() * 1000).toInt()
                    assertTrue("color escaped glyph at $x,$y", hit.contains(gx, gy))
                    if (isRed(pixel)) red++ else blue++
                }
            }
            assertTrue("fill pigment is visible", red > 100)
            assertTrue("brush pigment is visible", blue > 10)
            assertTrue("dark outline remains visible", dark >= expectedDark * 9 / 10)
            assertEquals("hole stays unpainted", Color.WHITE, bitmap.getPixel(size / 2, size / 2))
        }

        val painted = render(view, 400)
        verify(painted, 400, originalDark)
        view.measure(viewMeasure(620), viewMeasure(620))
        view.layout(0, 0, 620, 620)
        val resizedBefore = this.view(620)
        val resizedDark = render(resizedBefore, 620).let { bitmap ->
            try { (0 until 620).sumOf { y -> (0 until 620).count { x -> isDark(bitmap.getPixel(x, y)) } } }
            finally { bitmap.recycle() }
        }
        val resized = render(view, 620)
        verify(resized, 620, resizedDark)
        assertEquals("resize must not change saved coordinates", saved, view.exportArtwork())
        before.recycle()
        painted.recycle()
        resized.recycle()
    }
}
