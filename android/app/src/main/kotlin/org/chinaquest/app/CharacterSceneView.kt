package org.chinaquest.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/** One static drawing at a time. The caller owns state, text, and a matching action button. */
class CharacterSceneView(context: Context) : View(context) {
    private val ink = Color.BLACK
    private val paper = Color.WHITE
    private val coral = Color.rgb(206, 111, 84)
    private val gold = Color.rgb(215, 176, 84)
    private val blue = Color.rgb(113, 164, 188)
    private val green = Color.rgb(113, 155, 103)
    private val skin = Color.rgb(241, 206, 163)
    private val pale = Color.rgb(248, 241, 222)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    private var state: SceneState? = null
    private var onAdvance: (() -> Unit)? = null
    private var scaleFactor = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    init {
        setBackgroundColor(paper)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        minimumHeight = dp(310)
    }

    fun render(sceneState: SceneState, onAdvance: () -> Unit) {
        val stage = SceneCatalog.stage(sceneState)
        activePointerId = MotionEvent.INVALID_POINTER_ID
        state = sceneState
        this.onAdvance = onAdvance
        isClickable = !stage.complete
        contentDescription = if (stage.complete) "插画：${stage.prompt}" else stage.hotspotDescription
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize(dp(360), widthMeasureSpec), resolveSize(dp(310), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = state ?: return
        val scene = SceneCatalog.definition(current.sceneId) ?: return
        scaleFactor = min((width - dp(8)).toFloat() / 360f, (height - dp(8)).toFloat() / 310f).coerceAtLeast(0.01f)
        offsetX = (width - 360f * scaleFactor) / 2f
        offsetY = (height - 310f * scaleFactor) / 2f
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scaleFactor, scaleFactor)
        roundRect(canvas, 4f, 4f, 356f, 306f, 14f, paper, 3f)
        when (scene.family) {
            SceneFamily.EATING -> drawEating(canvas, current.step)
            SceneFamily.DOOR -> drawDoor(canvas, current.step, scene.id == "open-door")
            SceneFamily.DRINKING -> drawDrinking(canvas, current.step)
        }
        glyphBadge(canvas, scene.character)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val current = state ?: return false
        if (SceneCatalog.stage(current).complete) {
            activePointerId = MotionEvent.INVALID_POINTER_ID
            return false
        }
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                if (event.pointerCount == 1 && inHotspot(current, event.getX(0), event.getY(0))) {
                    activePointerId = event.getPointerId(0)
                    true
                } else false
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (activePointerId == MotionEvent.INVALID_POINTER_ID || event.pointerCount != 1 || pointerIndex < 0 ||
                    !inHotspot(current, event.getX(pointerIndex), event.getY(pointerIndex))) {
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    false
                } else true
            }
            MotionEvent.ACTION_UP -> {
                val valid = activePointerId != MotionEvent.INVALID_POINTER_ID && event.pointerCount == 1 &&
                    event.getPointerId(0) == activePointerId && inHotspot(current, event.getX(0), event.getY(0))
                activePointerId = MotionEvent.INVALID_POINTER_ID
                if (valid) performClick() else false
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val wasActive = activePointerId != MotionEvent.INVALID_POINTER_ID
                activePointerId = MotionEvent.INVALID_POINTER_ID
                wasActive
            }
            else -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                false
            }
        }
    }

    private fun inHotspot(current: SceneState, screenX: Float, screenY: Float): Boolean {
        val x = (screenX - offsetX) / scaleFactor
        val y = (screenY - offsetY) / scaleFactor
        val center = hotspot(current)
        // The hit target remains at least 56 dp across screen sizes and layouts.
        val radius = maxOf(43f, dp(28) / scaleFactor)
        return (x - center.first) * (x - center.first) + (y - center.second) * (y - center.second) <= radius * radius
    }

    override fun performClick(): Boolean {
        super.performClick()
        val current = state ?: return false
        if (SceneCatalog.stage(current).complete) return false
        onAdvance?.invoke() ?: return false
        return true
    }

    private fun hotspot(current: SceneState): Pair<Float, Float> {
        val scene = requireNotNull(SceneCatalog.definition(current.sceneId))
        return when (scene.family) {
            SceneFamily.EATING -> when (current.step) {
                0 -> 89f to 233f
                1 -> 139f to 174f
                else -> 207f to 135f
            }
            SceneFamily.DOOR -> {
                val opening = scene.id == "open-door"
                val aperture = if (opening) current.step else 2 - current.step
                (if (aperture == 0) 225f else if (aperture == 1) 165f else 130f) to 153f
            }
            SceneFamily.DRINKING -> if (current.step == 0) 105f to 230f else 151f to 155f
        }
    }

    private fun drawEating(c: Canvas, step: Int) {
        // Round table, checked cloth, plate, and a child's own two hands give the action a setting.
        oval(c, 29f, 237f, 301f, 289f, pale, 3f)
        line(c, 39f, 265f, 291f, 265f, ink, 3f)
        oval(c, 48f, 219f, 131f, 250f, paper, 3f)
        oval(c, 63f, 225f, 116f, 244f, pale, 2f)
        child(c, 214f, 112f, when (step) { 0 -> 0; 1, 2 -> 1; else -> 2 }, coral)
        val appleX = when (step) { 0 -> 88f; 1 -> 139f; 2 -> 177f; else -> 159f }
        val appleY = when (step) { 0 -> 222f; 1 -> 173f; 2 -> 147f; else -> 164f }
        // Hand and sleeve move only when a deliberate action advances the state.
        if (step > 0) {
            line(c, 176f, 216f, appleX + 15f, appleY + 19f, ink, 19f)
            line(c, 176f, 216f, appleX + 15f, appleY + 19f, coral, 13f)
            circle(c, appleX + 17f, appleY + 18f, 11f, skin, 3f)
        } else {
            line(c, 174f, 212f, 151f, 242f, ink, 19f)
            line(c, 174f, 212f, 151f, 242f, coral, 13f)
            circle(c, 149f, 244f, 10f, skin, 3f)
        }
        apple(c, appleX, appleY, step == 3)
        if (step == 3) {
            circle(c, 188f, 153f, 3f, gold, 1.5f)
            circle(c, 194f, 163f, 2.5f, gold, 1.5f)
        }
    }

    private fun drawDoor(c: Canvas, step: Int, opening: Boolean) {
        // The same doorway is traversed in opposite directions for 开 and 关.
        val aperture = if (opening) step else 2 - step
        line(c, 30f, 265f, 331f, 265f, ink, 3f)
        roundRect(c, 81f, 33f, 278f, 265f, 9f, pale, 4f)
        roundRect(c, 99f, 50f, 260f, 265f, 3f, blue, 3f)
        // A friendly panda waits behind the door; its white face also works in grayscale.
        panda(c, 187f, 139f)
        if (aperture == 0) {
            roundRect(c, 105f, 55f, 254f, 260f, 4f, gold, 4f)
            line(c, 116f, 72f, 243f, 72f, ink, 2.5f)
            line(c, 116f, 243f, 243f, 243f, ink, 2.5f)
            circle(c, 224f, 154f, 10f, paper, 3f)
        } else {
            val edge = if (aperture == 1) 198f else 145f
            polygon(c, floatArrayOf(105f, 54f, edge, 69f, edge, 250f, 105f, 261f), gold, 4f)
            line(c, 117f, 77f, edge - 11f, 88f, ink, 2.5f)
            line(c, 117f, 241f, edge - 11f, 235f, ink, 2.5f)
            circle(c, if (aperture == 1) 165f else 130f, 153f, 10f, paper, 3f)
        }
        // A small leaf and doormat make the doorway read as a place, not a symbol diagram.
        oval(c, 119f, 270f, 254f, 292f, green, 2.5f)
        line(c, 151f, 280f, 222f, 280f, ink, 2f)
    }

    private fun drawDrinking(c: Canvas, step: Int) {
        oval(c, 31f, 243f, 298f, 290f, pale, 3f)
        line(c, 42f, 266f, 287f, 266f, ink, 3f)
        child(c, 216f, 111f, if (step == 2) 2 else if (step == 1) 1 else 0, green)
        val cupX = if (step == 0) 105f else if (step == 1) 153f else 163f
        val cupY = if (step == 0) 216f else if (step == 1) 158f else 153f
        if (step > 0) {
            line(c, 174f, 214f, cupX + 29f, cupY + 19f, ink, 19f)
            line(c, 174f, 214f, cupX + 29f, cupY + 19f, green, 13f)
            circle(c, cupX + 31f, cupY + 19f, 10f, skin, 3f)
        } else {
            line(c, 174f, 216f, 151f, 245f, ink, 19f)
            line(c, 174f, 216f, 151f, 245f, green, 13f)
            circle(c, 151f, 246f, 10f, skin, 3f)
        }
        cup(c, cupX, cupY, step == 2)
        // A simple drop beside the cup identifies the water even without color.
        polygon(c, floatArrayOf(73f, 100f, 61f, 122f, 60f, 135f, 64f, 146f, 73f, 151f, 82f, 146f, 86f, 135f, 85f, 122f), blue, 3f)
        circle(c, 68f, 139f, 2.5f, paper, 0f)
        if (step == 2) {
            circle(c, 192f, 155f, 2.5f, blue, 1f)
            circle(c, 198f, 161f, 2f, blue, 1f)
        }
    }

    private fun child(c: Canvas, x: Float, y: Float, mouth: Int, shirt: Int) {
        // Hair, ears, face, cheeks, torso, collar, and arms remain visible on monochrome screens.
        oval(c, x - 58f, y - 51f, x + 58f, y + 61f, ink, 2.5f)
        circle(c, x - 53f, y + 11f, 10f, skin, 2.5f)
        circle(c, x + 53f, y + 11f, 10f, skin, 2.5f)
        oval(c, x - 49f, y - 42f, x + 49f, y + 61f, skin, 3f)
        polygon(c, floatArrayOf(x - 47f, y - 9f, x - 47f, y - 39f, x - 27f, y - 52f, x - 4f, y - 36f, x + 14f, y - 49f, x + 45f, y - 24f, x + 45f, y - 2f, x + 20f, y - 18f, x - 8f, y - 12f, x - 28f, y - 20f), ink, 0f)
        circle(c, x - 19f, y + 10f, 4f, ink, 0f)
        circle(c, x + 20f, y + 10f, 4f, ink, 0f)
        oval(c, x - 38f, y + 29f, x - 28f, y + 35f, coral, 0f)
        oval(c, x + 29f, y + 29f, x + 39f, y + 35f, coral, 0f)
        when (mouth) {
            0 -> arc(c, x - 13f, y + 24f, x + 13f, y + 42f, 15f, 150f, ink, 3f)
            1 -> oval(c, x - 12f, y + 29f, x + 12f, y + 48f, ink, 2f)
            else -> arc(c, x - 13f, y + 25f, x + 13f, y + 44f, 10f, 160f, ink, 3f)
        }
        oval(c, x - 56f, y + 67f, x + 56f, y + 166f, shirt, 3f)
        polygon(c, floatArrayOf(x - 19f, y + 68f, x, y + 87f, x + 19f, y + 68f), paper, 2f)
        line(c, x + 42f, y + 84f, x + 57f, y + 124f, ink, 20f)
        line(c, x + 42f, y + 84f, x + 57f, y + 124f, shirt, 14f)
        circle(c, x + 58f, y + 126f, 10f, skin, 3f)
    }

    private fun panda(c: Canvas, x: Float, y: Float) {
        oval(c, x - 40f, y + 35f, x + 45f, y + 118f, paper, 3f)
        oval(c, x - 47f, y - 65f, x - 16f, y - 27f, ink, 0f)
        oval(c, x + 18f, y - 65f, x + 49f, y - 27f, ink, 0f)
        oval(c, x - 58f, y - 48f, x + 58f, y + 56f, paper, 3f)
        oval(c, x - 39f, y - 20f, x - 9f, y + 12f, ink, 0f)
        oval(c, x + 10f, y - 20f, x + 40f, y + 12f, ink, 0f)
        circle(c, x - 23f, y - 3f, 4f, paper, 0f)
        circle(c, x + 24f, y - 3f, 4f, paper, 0f)
        oval(c, x - 9f, y + 17f, x + 9f, y + 29f, ink, 0f)
        arc(c, x - 16f, y + 22f, x + 16f, y + 39f, 10f, 160f, ink, 2.5f)
        line(c, x + 30f, y + 59f, x + 63f, y + 22f, ink, 18f)
        circle(c, x + 65f, y + 18f, 13f, ink, 0f)
        line(c, x - 28f, y + 59f, x - 54f, y + 76f, ink, 17f)
        oval(c, x - 34f, y + 105f, x - 8f, y + 130f, ink, 0f)
        oval(c, x + 11f, y + 105f, x + 37f, y + 130f, ink, 0f)
    }

    private fun apple(c: Canvas, x: Float, y: Float, bitten: Boolean) {
        // The final state has a clearly missing white crescent with a black cut edge.
        oval(c, x - 27f, y - 18f, x + 27f, y + 24f, coral, 3f)
        if (bitten) {
            circle(c, x + 23f, y - 9f, 12f, paper, 0f)
            arc(c, x + 11f, y - 21f, x + 35f, y + 3f, 100f, 160f, ink, 3f)
        }
        line(c, x, y - 17f, x + 3f, y - 28f, ink, 3f)
        oval(c, x + 4f, y - 32f, x + 22f, y - 22f, green, 2f)
    }

    private fun cup(c: Canvas, x: Float, y: Float, reduced: Boolean) {
        oval(c, x + 18f, y - 3f, x + 50f, y + 31f, paper, 3f)
        polygon(c, floatArrayOf(x - 29f, y - 10f, x + 29f, y - 10f, x + 22f, y + 43f, x - 22f, y + 43f), paper, 3f)
        val waterTop = if (reduced) y + 23f else y + 7f
        polygon(c, floatArrayOf(x - 23f, waterTop, x + 23f, waterTop, x + 19f, y + 39f, x - 19f, y + 39f), blue, 0f)
        line(c, x - 23f, waterTop, x + 23f, waterTop, ink, 2f)
        line(c, x - 29f, y - 10f, x - 22f, y + 43f, ink, 3f)
        line(c, x + 29f, y - 10f, x + 22f, y + 43f, ink, 3f)
        line(c, x - 22f, y + 43f, x + 22f, y + 43f, ink, 3f)
    }

    private fun glyphBadge(c: Canvas, glyph: String) {
        roundRect(c, 282f, 20f, 340f, 78f, 8f, paper, 3f)
        paint.style = Paint.Style.FILL; paint.color = ink; paint.textSize = 39f
        paint.textAlign = Paint.Align.CENTER; paint.isFakeBoldText = true
        c.drawText(glyph, 311f, 62f, paint)
        paint.isFakeBoldText = false
    }

    private fun circle(c: Canvas, x: Float, y: Float, r: Float, fill: Int, outline: Float) {
        paint.style = Paint.Style.FILL; paint.color = fill; c.drawCircle(x, y, r, paint)
        if (outline > 0f) { paint.style = Paint.Style.STROKE; paint.color = ink; paint.strokeWidth = outline; c.drawCircle(x, y, r, paint) }
    }
    private fun oval(c: Canvas, l: Float, t: Float, r: Float, b: Float, fill: Int, outline: Float) {
        val rect = RectF(l, t, r, b)
        paint.style = Paint.Style.FILL; paint.color = fill; c.drawOval(rect, paint)
        if (outline > 0f) { paint.style = Paint.Style.STROKE; paint.color = ink; paint.strokeWidth = outline; c.drawOval(rect, paint) }
    }
    private fun roundRect(c: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float, fill: Int, outline: Float) {
        val rect = RectF(l, t, r, b)
        paint.style = Paint.Style.FILL; paint.color = fill; c.drawRoundRect(rect, radius, radius, paint)
        if (outline > 0f) { paint.style = Paint.Style.STROKE; paint.color = ink; paint.strokeWidth = outline; c.drawRoundRect(rect, radius, radius, paint) }
    }
    private fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float) {
        paint.style = Paint.Style.STROKE; paint.color = color; paint.strokeWidth = width
        c.drawLine(x1, y1, x2, y2, paint)
    }
    private fun arc(c: Canvas, l: Float, t: Float, r: Float, b: Float, start: Float, sweep: Float, color: Int, width: Float) {
        paint.style = Paint.Style.STROKE; paint.color = color; paint.strokeWidth = width
        c.drawArc(RectF(l, t, r, b), start, sweep, false, paint)
    }
    private fun polygon(c: Canvas, xy: FloatArray, fill: Int, outline: Float) {
        val path = Path().apply { moveTo(xy[0], xy[1]); for (i in 2 until xy.size step 2) lineTo(xy[i], xy[i + 1]); close() }
        paint.style = Paint.Style.FILL; paint.color = fill; c.drawPath(path, paint)
        if (outline > 0f) { paint.style = Paint.Style.STROKE; paint.color = ink; paint.strokeWidth = outline; c.drawPath(path, paint) }
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
