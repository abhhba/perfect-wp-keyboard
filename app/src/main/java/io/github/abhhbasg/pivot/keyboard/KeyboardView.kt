package io.github.abhhbasg.pivot.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import io.github.abhhbasg.pivot.ui.Colors
import io.github.abhhbasg.pivot.ui.Fonts
import io.github.abhhbasg.pivot.ui.Glyph
import io.github.abhhbasg.pivot.ui.Glyphs
import io.github.abhhbasg.pivot.ui.dp
import kotlin.math.abs

/** What the keys tell the input method. */
interface KeyListener {
    /** A finger went down on [key]: click and vibrate. */
    fun onDown(key: Key)
    /** [key] was typed; [text] is what to type (the key's own, or the one picked from the bar of a held key), not yet in capitals. */
    fun onKey(key: Key, text: String)
    /** Backspace held: called again for every repeat. */
    fun onRepeat(key: Key)
    /** The finger slid along the space bar: move the cursor by [steps]. */
    fun onCursor(steps: Int)
}

/**
 * The keys of the keyboard, drawn and touched. One view for the whole page: it lays the rows out, paints them in the
 * Windows 10 Mobile manner and turns touches into keys, held-key bars, repeats and cursor moves. Several fingers can type at once.
 */
class KeyboardView(context: Context) : View(context) {
    var listener: KeyListener? = null
    private var page: Page = Layouts.letters(null)
    private var colors: Colors? = null
    private var shift = 0
    private var enter = Glyph.ENTER
    private var preview = true
    private var heightPct = 100

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private class Touch(val id: Int, val start: Key, val x0: Float) {
        var key: Key? = start
        var bar: Key? = null
        var barIndex = 0
        var cursorX = x0
        var moved = false
        var held = false
        var timer: Runnable? = null
    }
    private val touches = HashMap<Int, Touch>()

    fun setPage(p: Page) { page = p; touches.clear(); requestLayout(); layoutKeys(); invalidate() }
    fun setColors(c: Colors) { colors = c; invalidate() }
    fun setShift(s: Int) { if (shift != s) { shift = s; invalidate() } }
    fun setEnter(g: Glyph) { if (enter != g) { enter = g; invalidate() } }
    fun setPreview(on: Boolean) { preview = on }
    fun setHeightPct(p: Int) { if (heightPct != p) { heightPct = p; requestLayout() } }
    val currentPage get() = page

    private fun rowHeight(): Float {
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return context.dp(if (land) 40f else 54f) * heightPct / 100f
    }

    private val gap get() = context.dp(4f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = page.rows.size * rowHeight() + gap
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h.toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = layoutKeys()

    /** Ten letter widths across; a shorter row is centred, and its end keys also take the room beside them. */
    private fun layoutKeys() {
        if (width == 0 || height == 0) return
        val unit = (width - gap) / 10f
        val rowH = (height - gap) / page.rows.size
        page.rows.forEachIndexed { r, row ->
            val total = row.sumOf { it.units.toDouble() }.toFloat() * unit
            var x = gap / 2f + (width - gap - total) / 2f
            val top = gap + r * rowH
            row.forEachIndexed { i, k ->
                val w = k.units * unit
                k.left = x + gap / 2f; k.right = x + w - gap / 2f
                k.top = top; k.bottom = top + rowH - gap
                k.hitLeft = if (i == 0) 0f else x
                k.hitRight = if (i == row.lastIndex) width.toFloat() else x + w
                x += w
            }
        }
    }

    // --- painting ---------------------------------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val c = colors ?: return
        canvas.drawColor(c.background)
        for (k in page.keys) drawKey(canvas, k, c)
        for (t in touches.values) {
            if (t.bar != null) drawBar(canvas, t, c)
            else if (preview && !t.moved) t.key?.let { if (it.kind == Kind.CHAR) drawPreview(canvas, it, c) }
        }
    }

    private fun isFunction(k: Key) = k.kind != Kind.CHAR && k.kind != Kind.SPACE && k.kind != Kind.ENTER

    private fun pressed(k: Key) = touches.values.any { it.key === k && it.bar == null && !(k.kind == Kind.SPACE && it.moved) }

    private fun drawKey(canvas: Canvas, k: Key, c: Colors) {
        val down = pressed(k)
        val shiftOn = k.kind == Kind.SHIFT && shift != 0
        val bg = when {
            down || shiftOn -> c.accent
            k.kind == Kind.ENTER -> c.text
            isFunction(k) -> c.function
            else -> c.key
        }
        val fg = when {
            down || shiftOn -> c.onAccent
            k.kind == Kind.ENTER -> c.background
            else -> c.text
        }
        fill.color = bg
        canvas.drawRect(k.left, k.top, k.right, k.bottom, fill)
        val h = k.height
        when (k.kind) {
            Kind.CHAR -> label(canvas, if (shift != 0) k.label.uppercase() else k.label, k.cx, k.cy, h * if (k.label.length > 1) 0.3f else 0.42f, fg)
            Kind.SHIFT -> Glyphs.draw(canvas, if (shift == 2) Glyph.CAPS_LOCK else Glyph.SHIFT, k.cx, k.cy, h * 0.34f, fg, line)
            Kind.BACKSPACE -> Glyphs.draw(canvas, Glyph.BACKSPACE, k.cx, k.cy, h * 0.42f, fg, line)
            Kind.ENTER -> Glyphs.draw(canvas, enter, k.cx, k.cy, h * 0.38f, fg, line)
            Kind.EMOJI -> Glyphs.draw(canvas, Glyph.SMILEY, k.cx, k.cy, h * 0.42f, fg, line)
            Kind.SPACE -> {}
            Kind.MODE -> label(canvas, k.label, k.cx, k.cy, h * 0.36f, fg)
            Kind.SYMBOLS, Kind.LETTERS, Kind.MORE_SYMBOLS -> {
                label(canvas, k.label, k.cx, k.cy, h * 0.3f, fg)
                if (k.kind == Kind.SYMBOLS) {
                    // The three small dots in the corner of the phone's &123.
                    fill.color = Palette_subtle(c, down)
                    val r = h * 0.022f
                    for (i in 0 until 3) canvas.drawCircle(k.left + h * 0.13f + i * r * 3.4f, k.top + h * 0.14f, r, fill)
                }
            }
        }
    }

    private fun Palette_subtle(c: Colors, down: Boolean) = if (down) c.onAccent else c.subtle

    private fun label(canvas: Canvas, s: String, cx: Float, cy: Float, size: Float, color: Int) {
        text.color = color
        text.textSize = size
        text.typeface = Fonts.semilight(context)
        val m = text.fontMetrics
        canvas.drawText(s, cx, cy - (m.ascent + m.descent) / 2f, text)
    }

    /** The letter above the finger, in the accent colour. */
    private fun drawPreview(canvas: Canvas, k: Key, c: Colors) {
        val w = maxOf(k.width * 1.3f, context.dp(48f)); val h = k.height * 1.25f
        val left = (k.cx - w / 2f).coerceIn(0f, width - w)
        val top = maxOf(0f, k.top - h - context.dp(6f))
        fill.color = c.accent
        canvas.drawRect(left, top, left + w, top + h, fill)
        label(canvas, if (shift != 0) k.label.uppercase() else k.label, left + w / 2f, top + h / 2f, h * 0.55f, c.onAccent)
    }

    private fun barGeometry(t: Touch): FloatArray {
        val k = t.bar!!
        val itemW = maxOf(k.width, context.dp(36f))
        val total = itemW * k.popup.size
        val left = (k.cx - itemW / 2f - if (k.cx > width * 0.6f) total - itemW else 0f).coerceIn(0f, maxOf(0f, width - total))
        val h = k.height * 1.1f
        return floatArrayOf(left, maxOf(0f, k.top - h - context.dp(6f)), itemW, h)
    }

    /** The bar of a held key: the choices side by side above it, the one under the finger in the accent colour. */
    private fun drawBar(canvas: Canvas, t: Touch, c: Colors) {
        val k = t.bar!!
        val (left, top, itemW, h) = barGeometry(t).toList()
        fill.color = c.popup
        canvas.drawRect(left, top, left + itemW * k.popup.size, top + h, fill)
        line.style = Paint.Style.STROKE; line.strokeWidth = context.dp(1f); line.color = c.subtle
        canvas.drawRect(left, top, left + itemW * k.popup.size, top + h, line)
        k.popup.forEachIndexed { i, s ->
            val x = left + i * itemW
            if (i == t.barIndex) { fill.color = c.accent; canvas.drawRect(x, top, x + itemW, top + h, fill) }
            label(canvas, if (shift != 0) s.uppercase() else s, x + itemW / 2f, top + h / 2f, h * 0.42f, if (i == t.barIndex) c.onAccent else c.text)
        }
    }

    // --- touch ------------------------------------------------------------------------------------------------------

    private fun keyAt(x: Float, y: Float): Key? {
        page.keys.firstOrNull { it.hit(x, y) }?.let { return it }
        // In a gap between keys: the nearest one, if it is near.
        return page.keys.minByOrNull { k ->
            val dx = if (x < k.left) k.left - x else if (x > k.right) x - k.right else 0f
            val dy = if (y < k.top) k.top - y else if (y > k.bottom) y - k.bottom else 0f
            dx * dx + dy * dy
        }?.takeIf { y >= 0 }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> down(e.getPointerId(e.actionIndex), e.getX(e.actionIndex), e.getY(e.actionIndex))
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) move(e.getPointerId(i), e.getX(i), e.getY(i))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> up(e.getPointerId(e.actionIndex), e.getX(e.actionIndex), e.getY(e.actionIndex))
            MotionEvent.ACTION_CANCEL -> { touches.values.forEach { t -> t.timer?.let { removeCallbacks(it) } }; touches.clear(); invalidate() }
        }
        return true
    }

    private fun down(id: Int, x: Float, y: Float) {
        val k = keyAt(x, y) ?: return
        val t = Touch(id, k, x)
        touches[id] = t
        listener?.onDown(k)
        if (k.kind == Kind.BACKSPACE) {
            listener?.onKey(k, "")
            val r = object : Runnable {
                var delay = 400L
                override fun run() {
                    if (touches[id] !== t) return
                    listener?.onRepeat(k)
                    delay = maxOf(40L, (delay * 0.8).toLong())
                    postDelayed(this, delay)
                }
            }
            t.timer = r; postDelayed(r, 400L)
        } else if (k.popup.isNotEmpty()) {
            val r = Runnable {
                if (touches[id] === t && t.key === k && !t.moved) {
                    t.bar = k; t.barIndex = 0; t.held = true
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    invalidate()
                }
            }
            t.timer = r; postDelayed(r, 300L)
        }
        invalidate()
    }

    private fun move(id: Int, x: Float, y: Float) {
        val t = touches[id] ?: return
        val bar = t.bar
        if (bar != null) {
            val g = barGeometry(t)
            val i = ((x - g[0]) / g[2]).toInt().coerceIn(0, bar.popup.size - 1)
            if (i != t.barIndex) { t.barIndex = i; invalidate() }
            return
        }
        if (t.start.kind == Kind.SPACE) {
            // Sliding along the space bar moves the cursor, a character for every step.
            val step = context.dp(12f)
            val dx = x - t.cursorX
            if (abs(dx) >= step) {
                val n = (dx / step).toInt()
                t.cursorX += n * step
                if (!t.moved) { t.moved = true; invalidate() }
                listener?.onCursor(n)
            }
            return
        }
        if (t.start.kind == Kind.BACKSPACE) return
        val k = keyAt(x, y)
        if (k !== t.key) { t.key = k; t.moved = true; t.timer?.let { removeCallbacks(it) }; invalidate() }
    }

    private fun up(id: Int, x: Float, y: Float) {
        val t = touches.remove(id) ?: return
        t.timer?.let { removeCallbacks(it) }
        val bar = t.bar
        when {
            bar != null -> listener?.onKey(bar, bar.popup[t.barIndex])
            t.start.kind == Kind.BACKSPACE -> {}
            t.start.kind == Kind.SPACE && t.moved -> {}
            else -> (t.key ?: keyAt(x, y))?.let { listener?.onKey(it, it.text) }
        }
        invalidate()
    }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); touches.clear() }
}
