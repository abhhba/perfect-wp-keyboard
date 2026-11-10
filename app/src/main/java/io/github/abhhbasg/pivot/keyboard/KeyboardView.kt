package io.github.abhhbasg.pivot.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import io.github.abhhbasg.pivot.ui.Colors
import io.github.abhhbasg.pivot.ui.Fonts
import io.github.abhhbasg.pivot.ui.Glyph
import io.github.abhhbasg.pivot.ui.Glyphs
import io.github.abhhbasg.pivot.ui.Palette
import io.github.abhhbasg.pivot.ui.dp
import kotlin.math.abs
import kotlin.math.hypot

/** What the keys tell the input method. */
interface KeyListener {
    /** A finger went down on [key]: click and vibrate. */
    fun onDown(key: Key)
    /** [key] was typed; [text] is what to type (the key's own, or the tile picked from a held key), not yet in capitals. */
    fun onKey(key: Key, text: String)
    /** Backspace held: called again for every repeat. */
    fun onRepeat(key: Key)
    /** A key without tiles was held: &123 opens the emoji. */
    fun onHold(key: Key)
    /** The cursor knob was dragged: move the cursor with this arrow key. */
    fun onArrow(keyCode: Int)
    /** The finger slid up from backspace (clear the text) or down (bring it back). */
    fun onBackspaceSwipe(up: Boolean)
}

/**
 * The keys, drawn and touched as on screenshots of a Windows 10 Mobile phone. One view for the whole page: it lays the rows out,
 * paints them, and turns touches into keys, held-key tiles, repeats and the cursor knob. Several fingers can type at once.
 */
class KeyboardView(context: Context) : View(context) {
    var listener: KeyListener? = null
    private var page: Page = Layouts.letters(null, false)
    private var colors: Colors? = null
    private var shift = 0
    private var enter = Glyph.ENTER
    private var preview = true
    private var heightPct = 100
    private var knob = true

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)

    private class Touch(val id: Int, val start: Key?, val x0: Float, val y0: Float) {
        var key: Key? = start
        var tiles: Key? = null
        var tile = 0
        var moved = false
        var held = false
        var knob = false
        /** Slid from backspace: 1 up, -1 down. */
        var swipe = 0
        var ax = x0; var ay = y0
        var timer: Runnable? = null
    }
    private val touches = HashMap<Int, Touch>()

    fun setPage(p: Page) { page = p; touches.clear(); requestLayout(); layoutKeys(); invalidate() }
    fun setColors(c: Colors) { colors = c; invalidate() }
    fun setShift(s: Int) { if (shift != s) { shift = s; invalidate() } }
    fun setEnter(g: Glyph) { if (enter != g) { enter = g; invalidate() } }
    fun setPreview(on: Boolean) { preview = on }
    fun setHeightPct(p: Int) { if (heightPct != p) { heightPct = p; requestLayout() } }
    /** The cursor knob, on the letters and the signs; not on the number pads. */
    fun setKnob(on: Boolean) { if (knob != on) { knob = on; invalidate() } }

    private fun rowHeight(): Float {
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return context.dp(if (land) 40f else 56f) * heightPct / 100f
    }

    /** The gap between keys: a ninetieth of the width, as on the phone. */
    private val gap get() = maxOf(context.dp(3f), width / 94f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val g = maxOf(context.dp(3f), w / 94f)
        setMeasuredDimension(w, (page.rows.size * rowHeight() + g).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = layoutKeys()

    /** Ten letter widths across; a shorter row is centred, and its end keys also take the room beside them. */
    private fun layoutKeys() {
        if (width == 0 || height == 0) return
        val g = gap
        val unit = (width - g) / 10f
        val rowH = (height - g) / page.rows.size
        page.rows.forEachIndexed { r, row ->
            val total = row.sumOf { it.units.toDouble() }.toFloat() * unit
            var x = g / 2f + (width - g - total) / 2f
            val top = g + r * rowH
            row.forEachIndexed { i, k ->
                val w = k.units * unit
                k.left = x + g / 2f; k.right = x + w - g / 2f
                k.top = top; k.bottom = top + rowH - g
                k.hitLeft = if (i == 0) 0f else x
                k.hitRight = if (i == row.lastIndex) width.toFloat() else x + w
                x += w
            }
        }
    }

    // --- the cursor knob --------------------------------------------------------------------------------------------

    /** Where the knob sits: between z and x, on the line between the last two rows. */
    private fun knobCenter(): Pair<Float, Float>? {
        if (!knob || page.rows.size < 4) return null
        val z = page.rows[2].getOrNull(1) ?: return null
        val below = page.rows[3].firstOrNull() ?: return null
        return (z.right + gap / 2f) to (z.bottom + below.top) / 2f
    }
    private val knobRadius get() = (width / 10f) * 0.31f

    // --- painting ---------------------------------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val c = colors ?: return
        fill.color = c.background; canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill) // not drawColor: the view may draw outside itself, over the bar
        for (k in page.keys) drawKey(canvas, k, c)
        val tiles = touches.values.firstOrNull { it.tiles != null }
        val knobTouch = touches.values.firstOrNull { it.knob }
        // While tiles are open or the knob is dragged, the rest of the keyboard sinks back.
        if (tiles != null || knobTouch != null) { fill.color = Palette.withAlpha(0xFF000000.toInt(), 0x80); canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill) }
        drawKnob(canvas, c, knobTouch != null)
        if (tiles != null) drawTiles(canvas, tiles, c)
        else if (preview) for (t in touches.values) if (!t.moved && !t.knob) t.key?.let { if (it.kind == Kind.CHAR) drawPreview(canvas, it, c) }
    }

    private fun isFunction(k: Key) = k.fn || k.kind == Kind.SHIFT || k.kind == Kind.BACKSPACE || k.kind == Kind.SYMBOLS || k.kind == Kind.LETTERS ||
        k.kind == Kind.MORE_SYMBOLS || k.kind == Kind.LANGUAGE

    private fun pressed(k: Key) = touches.values.any { it.key === k && !it.knob && it.swipe == 0 && (it.tiles == null || it.tiles === k) }

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
            Kind.CHAR -> {
                val s = if (shift != 0 && !k.fn) k.label.uppercase() else k.label
                // The phone sets the comma and the full stop low, where they sit in a line of text.
                val low = s == "." || s == ","
                val hint = k.hint
                if (hint != null) {
                    // A Shuangpin key: the letter a little up, its finals small under it.
                    label(canvas, s, k.cx, k.cy - h * 0.1f, h * 0.36f, fg)
                    label(canvas, hint, k.cx, k.bottom - h * 0.17f, h * 0.17f, if (down) fg else c.subtle)
                } else label(canvas, s, k.cx, if (low) k.cy + h * 0.06f else k.cy, h * if (s.length > 1) 0.3f else 0.4f, fg)
            }
            Kind.SHIFT -> Glyphs.draw(canvas, if (shift == 2) Glyph.CAPS_LOCK else Glyph.SHIFT, k.cx, k.cy, h * 0.3f, fg, line)
            Kind.BACKSPACE -> Glyphs.draw(canvas, Glyph.BACKSPACE, k.cx, k.cy, h * 0.36f, fg, line)
            Kind.ENTER -> Glyphs.draw(canvas, enter, k.cx, k.cy, h * 0.34f, fg, line)
            Kind.SPACE -> {
                // The grip: two short lines at the top.
                fill.color = Palette.withAlpha(c.text, 0x40)
                val w = h * 0.26f; val y = k.top + h * 0.12f
                canvas.drawRect(k.cx - w / 2f, y, k.cx + w / 2f, y + context.dp(1f), fill)
                canvas.drawRect(k.cx - w / 2f, y + context.dp(3.5f), k.cx + w / 2f, y + context.dp(4.5f), fill)
            }
            Kind.LANGUAGE -> label(canvas, k.label, k.cx, k.cy, h * if (k.label.length > 1) 0.24f else 0.3f, fg)
            Kind.MORE_SYMBOLS -> Glyphs.draw(canvas, Glyph.NEXT_PAGE, k.cx, k.cy, h * 0.3f, fg, line)
            Kind.SYMBOLS, Kind.LETTERS -> {
                label(canvas, k.label, k.cx, k.cy + h * 0.05f, h * if (k.kind == Kind.LETTERS && k.label.length <= 2) 0.32f else 0.25f, fg)
                // The smiley in the corner of &123: holding the key opens the emoji.
                if (k.kind == Kind.SYMBOLS) Glyphs.draw(canvas, Glyph.SMILEY, k.left + h * 0.18f, k.top + h * 0.18f, h * 0.22f, Palette.withAlpha(fg, 0x99), line)
            }
        }
    }

    private fun label(canvas: Canvas, s: String, cx: Float, cy: Float, size: Float, color: Int) {
        text.color = color
        text.textSize = size
        text.typeface = Fonts.semilight(context)
        val m = text.fontMetrics
        canvas.drawText(s, cx, cy - (m.ascent + m.descent) / 2f, text)
    }

    private fun drawKnob(canvas: Canvas, c: Colors, active: Boolean) {
        val (x, y) = knobCenter() ?: return
        val r = knobRadius
        fill.color = c.background
        canvas.drawCircle(x, y, r, fill)
        fill.color = c.accent
        canvas.drawCircle(x, y, r * 0.27f, fill)
        if (active) {
            // The four arrows the knob moves the cursor by.
            val d = r * 2.7f; val s = r * 1.3f
            Glyphs.draw(canvas, Glyph.CHEVRON_UP, x, y - d, s, c.text, line)
            Glyphs.draw(canvas, Glyph.CHEVRON_DOWN, x, y + d, s, c.text, line)
            Glyphs.draw(canvas, Glyph.CHEVRON_LEFT, x - d, y, s, c.text, line)
            Glyphs.draw(canvas, Glyph.CHEVRON_RIGHT, x + d, y, s, c.text, line)
        }
    }

    /** The letter above the finger: the key and a taller, wider accent tile over the row above it. */
    private fun drawPreview(canvas: Canvas, k: Key, c: Colors) {
        val w = k.width * 1.34f; val h = k.height * 1.15f
        val left = (k.cx - w / 2f).coerceIn(0f, width - w)
        val bottom = k.top - gap * 0.6f
        val top = bottom - h
        fill.color = c.accent
        canvas.drawRect(left, top, left + w, bottom, fill)
        label(canvas, if (shift != 0) k.label.uppercase() else k.label, left + w / 2f, (top + bottom) / 2f, k.height * 0.5f, c.onAccent)
    }

    /** The grid of tiles of a held key: [left, top, tile width, tile height, columns]. Its bottom row sits just above the key. */
    private fun tileGeometry(k: Key): FloatArray {
        val cols = k.gridColumns
        val rows = (k.popup.size + cols - 1) / cols
        val unit = (width - gap) / 10f
        val tw = maxOf(k.width, unit * if (k.kind == Kind.LANGUAGE) 1.45f else 1.2f)
        val th = k.height * if (k.kind == Kind.LANGUAGE) 1.15f else 1f
        val total = tw * cols
        val left = (if (k.cx > width * 0.6f) k.right - total else k.left).coerceIn(0f, maxOf(0f, width - total))
        // Above the key, over the bar if need be: the keyboard keeps room above itself for the tiles of the first row.
        val top = k.top - gap - rows * th
        return floatArrayOf(left, top, tw, th, cols.toFloat())
    }

    private fun drawTiles(canvas: Canvas, t: Touch, c: Colors) {
        val k = t.tiles!!
        val (left, top, tw, th) = tileGeometry(k).toList()
        val cols = k.gridColumns
        val onTile = Palette.onColor(c.popup)
        val g = context.dp(2f)
        k.popup.forEachIndexed { i, s ->
            if (s.isEmpty()) return@forEachIndexed
            val x = left + (i % cols) * tw; val y = top + (i / cols) * th
            val sel = i == t.tile
            fill.color = if (sel) c.accent else c.popup
            canvas.drawRect(x + g / 2f, y + g / 2f, x + tw - g / 2f, y + th - g / 2f, fill)
            val fg = if (sel) c.onAccent else onTile
            when {
                s == Layouts.SETTINGS -> Glyphs.draw(canvas, Glyph.GEAR, x + tw / 2f, y + th / 2f, th * 0.42f, fg, line)
                s.contains('\n') -> s.split('\n').let { (a, b) ->
                    label(canvas, a, x + tw / 2f, y + th * 0.36f, th * 0.24f, fg); label(canvas, b, x + tw / 2f, y + th * 0.66f, th * 0.24f, fg)
                }
                else -> label(canvas, if (shift != 0) s.uppercase() else s, x + tw / 2f, y + th / 2f, th * if (s.length > 2) 0.26f else 0.38f, fg)
            }
        }
    }

    // --- touch ------------------------------------------------------------------------------------------------------

    private fun keyAt(x: Float, y: Float): Key? {
        page.keys.firstOrNull { it.hit(x, y) }?.let { return it }
        return page.keys.minByOrNull { k ->
            val dx = if (x < k.left) k.left - x else if (x > k.right) x - k.right else 0f
            val dy = if (y < k.top) k.top - y else if (y > k.bottom) y - k.bottom else 0f
            dx * dx + dy * dy
        }
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
        knobCenter()?.let { (kx, ky) ->
            if (hypot(x - kx, y - ky) <= knobRadius * 1.25f) {
                touches[id] = Touch(id, null, x, y).apply { knob = true }
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                invalidate(); return
            }
        }
        val k = keyAt(x, y) ?: return
        val t = Touch(id, k, x, y)
        touches[id] = t
        listener?.onDown(k)
        if (k.kind == Kind.BACKSPACE) {
            listener?.onKey(k, "")
            val r = object : Runnable {
                var delay = 400L
                override fun run() {
                    if (touches[id] !== t || t.swipe != 0) return
                    listener?.onRepeat(k)
                    delay = maxOf(40L, (delay * 0.8).toLong())
                    postDelayed(this, delay)
                }
            }
            t.timer = r; postDelayed(r, 400L)
        } else if (k.popup.isNotEmpty() || k.kind == Kind.SYMBOLS) {
            val r = Runnable {
                if (touches[id] !== t || t.key !== k || t.moved) return@Runnable
                t.held = true
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                if (k.popup.isNotEmpty()) { t.tiles = k; t.tile = k.initial } else listener?.onHold(k)
                invalidate()
            }
            t.timer = r; postDelayed(r, 350L)
        }
        invalidate()
    }

    private fun move(id: Int, x: Float, y: Float) {
        val t = touches[id] ?: return
        if (t.knob) {
            // Every step of the finger away from where it was is one arrow; the strongest direction wins.
            val step = width / 18f
            val dx = x - t.ax; val dy = y - t.ay
            if (abs(dx) >= step && abs(dx) >= abs(dy)) {
                repeat((abs(dx) / step).toInt()) { listener?.onArrow(if (dx > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT) }
                t.ax = x; t.ay = y
            } else if (abs(dy) >= step * 1.6f) {
                listener?.onArrow(if (dy > 0) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP)
                t.ax = x; t.ay = y
            }
            return
        }
        val tk = t.tiles
        if (tk != null) {
            val g = tileGeometry(tk)
            val cols = tk.gridColumns
            val rows = (tk.popup.size + cols - 1) / cols
            val col = ((x - g[0]) / g[2]).toInt().coerceIn(0, cols - 1)
            val row = ((y - g[1]) / g[3]).toInt().coerceIn(0, rows - 1)
            val i = (row * cols + col).coerceIn(0, tk.popup.size - 1)
            if (i != t.tile && tk.popup[i].isNotEmpty()) { t.tile = i; invalidate() }
            return
        }
        if (t.start?.kind == Kind.BACKSPACE) {
            // Sliding up from backspace clears the text, down brings it back.
            val dy = y - t.y0
            val s = when { dy < -t.start.height * 0.8f -> 1; dy > t.start.height * 0.8f -> -1; else -> 0 }
            if (s != t.swipe) { t.swipe = s; if (s != 0) t.timer?.let { removeCallbacks(it) }; invalidate() }
            return
        }
        val k = keyAt(x, y)
        if (k !== t.key && hypot(x - t.x0, y - t.y0) > context.dp(8f)) { t.key = k; t.moved = true; t.timer?.let { removeCallbacks(it) }; invalidate() }
    }

    private fun up(id: Int, x: Float, y: Float) {
        val t = touches.remove(id) ?: return
        t.timer?.let { removeCallbacks(it) }
        val tk = t.tiles
        when {
            t.knob -> {}
            tk != null -> tk.popup.getOrNull(t.tile)?.takeIf { it.isNotEmpty() }?.let { listener?.onKey(tk, it) }
            t.held -> {}
            t.start?.kind == Kind.BACKSPACE -> if (t.swipe != 0) listener?.onBackspaceSwipe(t.swipe > 0)
            else -> (t.key ?: keyAt(x, y))?.let { listener?.onKey(it, it.text) }
        }
        invalidate()
    }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); touches.clear() }
}
