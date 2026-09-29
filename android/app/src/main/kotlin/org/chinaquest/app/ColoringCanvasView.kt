package org.chinaquest.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import android.view.MotionEvent
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

enum class ColoringTool { BRUSH, FILL }

/** A self-contained, bounded coloring surface. Coordinates in saved artwork are glyph-square fractions. */
class ColoringCanvasView(context: Context) : View(context) {
    companion object {
        private const val CANVAS_SIZE = 1000f
        private const val MAX_ACTIONS = 128
        private const val MAX_POINTS_PER_STROKE = 512
        private const val MAX_TOTAL_POINTS = 12_000
        private const val MAX_JSON_CHARS = 160_000
        private const val BRUSH_WIDTH = 0.055f
        private const val VERSION = 1
    }

    private data class Point(val x: Float, val y: Float)
    private sealed class Mark(open val color: Int) {
        data class Fill(override val color: Int) : Mark(color)
        data class Stroke(override val color: Int, val points: List<Point>) : Mark(color)
        data object Clear : Mark(Color.TRANSPARENT)
    }

    var character: String = "中"
        set(value) {
            require(value.codePointCount(0, value.length) == 1) { "Expected one character" }
            if (field == value) return
            endGesture()
            field = value
            marks.clear()
            rebuildGlyph()
            invalidate()
        }

    var selectedColor: Int = Color.rgb(224, 73, 55)
        set(value) {
            field = opaqueColor(value)
        }

    var tool: ColoringTool = ColoringTool.BRUSH
    var onArtworkChanged: ((String) -> Unit)? = null
    var onStatusMessage: ((String) -> Unit)? = null

    private val marks = ArrayList<Mark>()
    private var activePoints: MutableList<Point>? = null
    private var activeColor: Int = selectedColor
    private var activeTool: ColoringTool = tool
    private var activePointerId = -1
    private var fillStartedInside = false
    private val square = RectF()
    private val glyph = Path()
    private val glyphHit = Region()
    private val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeJoin = Paint.Join.ROUND
    }
    private val colorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    init {
        isClickable = true
        contentDescription = "给${character}涂颜色"
        rebuildGlyph()
    }

    val canUndo: Boolean get() = marks.isNotEmpty()
    val isEmpty: Boolean get() = marks.asReversed().takeWhile { it != Mark.Clear }.none { it is Mark.Fill || it is Mark.Stroke }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val inset = 12f * resources.displayMetrics.density
        val side = (min(w.toFloat(), h.toFloat()) - inset * 2f).coerceAtLeast(0f)
        square.set((w - side) / 2f, (h - side) / 2f, (w + side) / 2f, (h + side) / 2f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (square.width() <= 0f) return
        val save = canvas.save()
        canvas.translate(square.left, square.top)
        canvas.scale(square.width() / CANVAS_SIZE, square.height() / CANVAS_SIZE)
        canvas.drawRoundRect(0f, 0f, CANVAS_SIZE, CANVAS_SIZE, 24f, 24f, paper)
        // The clip makes every brush mark remain inside the actual font outline.
        val clip = canvas.save()
        canvas.clipPath(glyph)
        for (mark in marks) drawMark(canvas, mark)
        activePoints?.let { drawMark(canvas, Mark.Stroke(activeColor, it)) }
        canvas.restoreToCount(clip)
        canvas.drawPath(glyph, border)
        canvas.restoreToCount(save)
    }

    private fun drawMark(canvas: Canvas, mark: Mark) {
        colorPaint.color = mark.color
        when (mark) {
            is Mark.Fill -> {
                colorPaint.style = Paint.Style.FILL
                canvas.drawPath(glyph, colorPaint)
            }
            is Mark.Stroke -> {
                colorPaint.style = Paint.Style.STROKE
                colorPaint.strokeWidth = BRUSH_WIDTH * CANVAS_SIZE
                val path = Path()
                val first = mark.points.firstOrNull() ?: return
                path.moveTo(first.x * CANVAS_SIZE, first.y * CANVAS_SIZE)
                if (mark.points.size == 1) {
                    path.lineTo(first.x * CANVAS_SIZE + 0.1f, first.y * CANVAS_SIZE + 0.1f)
                } else {
                    for (point in mark.points.drop(1)) path.lineTo(point.x * CANVAS_SIZE, point.y * CANVAS_SIZE)
                }
                canvas.drawPath(path, colorPaint)
            }
            Mark.Clear -> {
                colorPaint.style = Paint.Style.FILL
                colorPaint.color = Color.WHITE
                canvas.drawPath(glyph, colorPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (square.width() <= 0f) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!square.contains(event.x, event.y)) return false
                endGesture()
                activePointerId = event.getPointerId(0)
                activeColor = selectedColor
                activeTool = tool
                fillStartedInside = glyphContains(event.x, event.y)
                activePoints = if (activeTool == ColoringTool.BRUSH) mutableListOf(normalized(event.x, event.y)) else null
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> return activePointerId != -1
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointerId) {
                    endGesture()
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(activePointerId)
                if (index < 0) {
                    endGesture()
                    invalidate()
                    return true
                }
                val points = activePoints
                if (points != null) {
                    for (history in 0 until event.historySize) appendPoint(points, normalized(event.getHistoricalX(index, history), event.getHistoricalY(index, history)))
                    appendPoint(points, normalized(event.getX(index), event.getY(index)))
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (activePointerId == -1) return false
                val index = event.findPointerIndex(activePointerId)
                val points = activePoints
                if (points != null && index >= 0) {
                    appendPoint(points, normalized(event.getX(index), event.getY(index)))
                    addMark(Mark.Stroke(activeColor, points.toList()))
                } else if (activeTool == ColoringTool.FILL && fillStartedInside && index >= 0 && glyphContains(event.getX(index), event.getY(index))) {
                    addMark(Mark.Fill(activeColor))
                }
                endGesture()
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                endGesture()
                invalidate()
                return true
            }
        }
        return activePointerId != -1
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        endGesture()
        super.onDetachedFromWindow()
    }

    fun undo(): Boolean {
        if (marks.isEmpty()) return false
        endGesture()
        marks.removeAt(marks.lastIndex)
        notifyChanged()
        return true
    }

    fun clear(): Boolean {
        if (isEmpty) return false
        endGesture()
        return addMark(Mark.Clear)
    }

    /** Replaces the view state; malformed, oversized, or wrong-character data loads as blank. */
    fun loadArtwork(json: String?) {
        endGesture()
        marks.clear()
        if (!json.isNullOrBlank() && json.length <= MAX_JSON_CHARS) {
            try {
                val root = JSONObject(json)
                if (root.getInt("v") == VERSION && root.getString("character") == character) {
                    val actions = root.getJSONArray("actions")
                    if (actions.length() <= MAX_ACTIONS) {
                        var totalPoints = 0
                        for (i in 0 until actions.length()) {
                            val mark = parseMark(actions.getJSONObject(i))
                            if (mark is Mark.Stroke) totalPoints += mark.points.size
                            require(totalPoints <= MAX_TOTAL_POINTS)
                            marks.add(mark)
                        }
                    }
                }
            } catch (_: Exception) {
                marks.clear()
            }
        }
        invalidate()
    }

    fun exportArtwork(): String {
        val actions = JSONArray()
        for (mark in marks) {
            val item = JSONObject()
            when (mark) {
                is Mark.Fill -> item.put("type", "fill").put("color", mark.color)
                is Mark.Stroke -> {
                    item.put("type", "stroke").put("color", mark.color)
                    val points = JSONArray()
                    for (point in mark.points) points.put(JSONArray().put((point.x * 1000).roundToInt()).put((point.y * 1000).roundToInt()))
                    item.put("points", points)
                }
                Mark.Clear -> item.put("type", "clear")
            }
            actions.put(item)
        }
        return JSONObject().put("v", VERSION).put("character", character).put("actions", actions).toString()
    }

    private fun parseMark(item: JSONObject): Mark {
        return when (item.getString("type")) {
            "clear" -> Mark.Clear
            "fill" -> Mark.Fill(parseColor(item))
            "stroke" -> {
                val color = parseColor(item)
                val array = item.getJSONArray("points")
                require(array.length() in 1..MAX_POINTS_PER_STROKE)
                val points = ArrayList<Point>(array.length())
                for (i in 0 until array.length()) {
                    val pair = array.getJSONArray(i)
                    require(pair.length() == 2)
                    val x = pair.getInt(0)
                    val y = pair.getInt(1)
                    require(x in 0..1000 && y in 0..1000)
                    points.add(Point(x / 1000f, y / 1000f))
                }
                Mark.Stroke(color, points)
            }
            else -> throw IllegalArgumentException("Unknown mark")
        }
    }

    private fun parseColor(item: JSONObject): Int = item.getInt("color").also {
        require(Color.alpha(it) == 255)
    }

    private fun addMark(mark: Mark): Boolean {
        val newPoints = if (mark is Mark.Stroke) mark.points.size else 0
        val oldPoints = marks.sumOf { if (it is Mark.Stroke) it.points.size else 0 }
        if (marks.size >= MAX_ACTIONS || oldPoints + newPoints > MAX_TOTAL_POINTS) {
            onStatusMessage?.invoke("画布已满。可以撤回一笔，或换一个字继续画。")
            invalidate()
            return false
        }
        marks.add(mark)
        notifyChanged()
        return true
    }

    private fun notifyChanged() {
        invalidate()
        onArtworkChanged?.invoke(exportArtwork())
    }

    private fun endGesture() {
        activePointerId = -1
        activePoints = null
        fillStartedInside = false
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun normalized(x: Float, y: Float) = Point(
        ((x - square.left) / square.width()).coerceIn(0f, 1f),
        ((y - square.top) / square.height()).coerceIn(0f, 1f),
    )

    private fun appendPoint(points: MutableList<Point>, point: Point) {
        if (points.size >= MAX_POINTS_PER_STROKE) return
        val last = points.lastOrNull()
        if (last == null || abs(last.x - point.x) + abs(last.y - point.y) >= 0.002f) points.add(point)
    }

    private fun glyphContains(x: Float, y: Float): Boolean {
        if (!square.contains(x, y)) return false
        val px = ((x - square.left) / square.width() * CANVAS_SIZE).toInt()
        val py = ((y - square.top) / square.height() * CANVAS_SIZE).toInt()
        return glyphHit.contains(px, py)
    }

    private fun rebuildGlyph() {
        glyph.reset()
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            textSize = CANVAS_SIZE
        }
        val raw = Path()
        textPaint.getTextPath(character, 0, character.length, 0f, 0f, raw)
        val bounds = RectF()
        raw.computeBounds(bounds, true)
        if (!bounds.isEmpty) {
            val scale = min(820f / bounds.width(), 820f / bounds.height())
            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(500f - (bounds.left + bounds.right) * scale / 2f, 500f - (bounds.top + bounds.bottom) * scale / 2f)
            }
            raw.transform(matrix, glyph)
        }
        glyphHit.setPath(glyph, Region(0, 0, CANVAS_SIZE.toInt(), CANVAS_SIZE.toInt()))
        contentDescription = "给${character}涂颜色"
    }

    private fun opaqueColor(color: Int): Int = color or 0xff000000.toInt()
}
