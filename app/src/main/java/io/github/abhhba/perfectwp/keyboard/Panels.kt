package io.github.abhhba.perfectwp.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import io.github.abhhba.perfectwp.ui.Colors
import io.github.abhhba.perfectwp.ui.Fonts
import io.github.abhhba.perfectwp.ui.Glyph
import io.github.abhhba.perfectwp.ui.Glyphs
import io.github.abhhba.perfectwp.ui.Palette
import io.github.abhhba.perfectwp.ui.dp
import kotlin.math.abs

/**
 * A grid of texts that slides up and down: all the Chinese words for the Pinyin typed (each cell as wide as its word) or the
 * emoji (cells of one size). A tap picks one.
 */
class GridPanel(context: Context) : View(context) {
    var onPick: ((Int) -> Unit)? = null
    /** Tells which item is at the top, so the emoji tabs can follow the list. */
    var onTopItem: ((Int) -> Unit)? = null
    private var colors: Colors? = null
    private var items: List<String> = emptyList()
    private var fixedColumns = 0
    private var textScale = 0.42f
    private var cellWidth = 0f
    private var padLeft = 0f
    private var rowHeight = 0f
    private var rects = FloatArray(0)
    private var contentHeight = 0f
    private var scroll = 0f
    private var pressed = -1
    private var downY = 0f
    private var scrollAtDown = 0f
    private var dragging = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rowH get() = if (rowHeight > 0f) rowHeight else context.dp(52f)

    fun setColors(c: Colors) { colors = c; invalidate() }

    /**
     * [columns] 0: each cell as wide as its text (words); otherwise that many cells to a row (emoji), each [cell] wide (or an even
     * share of the width) after [pad], in rows [row] high.
     */
    fun setItems(list: List<String>, columns: Int, scale: Float, cell: Float = 0f, pad: Float = 0f, row: Float = 0f) {
        items = list; fixedColumns = columns; textScale = scale; cellWidth = cell; padLeft = pad; rowHeight = row; scroll = 0f
        layoutItems(); invalidate()
    }

    fun scrollToItem(i: Int) { if (i in items.indices) { scroll = rects[i * 4 + 1].coerceIn(0f, maxScroll()); invalidate() } }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = layoutItems()

    private fun layoutItems() {
        rects = FloatArray(items.size * 4)
        if (width == 0) return
        text.textSize = rowH * textScale
        var x = padLeft; var y = 0f
        val minW = width / 6f
        items.forEachIndexed { i, s ->
            // In the grid of candidates a word takes as many columns as it needs.
            val w = when { cellWidth > 0f -> cellWidth * kotlin.math.ceil((text.measureText(s) + rowH * 0.45f) / cellWidth).coerceIn(1f, fixedColumns.coerceAtLeast(1).toFloat()); fixedColumns > 0 -> (width - padLeft) / fixedColumns.toFloat(); else -> maxOf(minW, text.measureText(s) + context.dp(28f)) }
            val full = if (cellWidth > 0f) x + w > padLeft + cellWidth * fixedColumns + 0.5f else (x + w > width + 0.5f || (fixedColumns > 0 && i > 0 && i % fixedColumns == 0))
            if (full && x > padLeft) { x = padLeft; y += rowH }
            rects[i * 4] = x; rects[i * 4 + 1] = y; rects[i * 4 + 2] = x + w; rects[i * 4 + 3] = y + rowH
            x += w
        }
        contentHeight = if (items.isEmpty()) 0f else y + rowH
    }

    private fun maxScroll() = maxOf(0f, contentHeight - height)

    override fun onDraw(canvas: Canvas) {
        val c = colors ?: return
        fill.color = c.background; canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        text.textSize = rowH * textScale
        text.typeface = Fonts.semilight(context)
        val m = text.fontMetrics
        fill.color = Palette.withAlpha(c.text, 0x18)
        items.forEachIndexed { i, s ->
            val l = rects[i * 4]; val t = rects[i * 4 + 1] - scroll; val r = rects[i * 4 + 2]; val b = rects[i * 4 + 3] - scroll
            if (b < 0 || t > height) return@forEachIndexed
            if (i == pressed) { fill.color = c.accent; canvas.drawRect(l, t, r, b, fill); fill.color = Palette.withAlpha(c.text, 0x18) }
            else if (fixedColumns == 0 && cellWidth == 0f) canvas.drawRect(l + 1f, b - 1f, r - 1f, b, fill)
            text.color = if (i == pressed) c.onAccent else c.text
            canvas.drawText(s, (l + r) / 2f, (t + b) / 2f - (m.ascent + m.descent) / 2f, text)
        }
    }

    private fun indexAt(x: Float, y: Float): Int {
        val yy = y + scroll
        for (i in items.indices) if (x >= rects[i * 4] && x < rects[i * 4 + 2] && yy >= rects[i * 4 + 1] && yy < rects[i * 4 + 3]) return i
        return -1
    }

    private fun reportTop() {
        val i = items.indices.firstOrNull { rects[it * 4 + 3] > scroll + 1f } ?: return
        onTopItem?.invoke(i)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { pressed = indexAt(e.x, e.y); downY = e.y; scrollAtDown = scroll; dragging = false; invalidate() }
            MotionEvent.ACTION_MOVE -> if (dragging || abs(e.y - downY) > slop) {
                dragging = true; pressed = -1
                scroll = (scrollAtDown - (e.y - downY)).coerceIn(0f, maxScroll()); reportTop(); invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val i = indexAt(e.x, e.y)
                if (!dragging && i >= 0 && i == pressed) onPick?.invoke(i)
                pressed = -1; invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { pressed = -1; invalidate() }
        }
        return true
    }
}

/** A row of flat buttons under a panel: a text or a glyph each, the selected one in the accent colour. */
class ButtonRow(context: Context) : View(context) {
    class Item(val label: String? = null, val glyph: Glyph? = null, val weight: Float = 1f, val repeat: Boolean = false)

    var onClick: ((Int) -> Unit)? = null
    private var colors: Colors? = null
    private var items: List<Item> = emptyList()
    private var selected = -1
    private var pressed = -1
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val repeater = object : Runnable {
        override fun run() { if (pressed >= 0) { onClick?.invoke(pressed); postDelayed(this, 60L) } }
    }

    fun setColors(c: Colors) { colors = c; invalidate() }
    fun setItems(list: List<Item>) { items = list; invalidate() }
    fun select(i: Int) { if (selected != i) { selected = i; invalidate() } }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), context.dp(48f).toInt())

    private fun edges(): FloatArray {
        val total = items.sumOf { it.weight.toDouble() }.toFloat()
        val e = FloatArray(items.size + 1)
        items.forEachIndexed { i, it -> e[i + 1] = e[i] + width * it.weight / total }
        return e
    }

    override fun onDraw(canvas: Canvas) {
        val c = colors ?: return
        fill.color = c.bar; canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        val e = edges()
        val h = height.toFloat()
        items.forEachIndexed { i, it ->
            val l = e[i]; val r = e[i + 1]
            if (i == pressed) { fill.color = c.accent; canvas.drawRect(l, 0f, r, h, fill) }
            val fg = when { i == pressed -> c.onAccent; i == selected -> c.accent; else -> c.text }
            if (it.glyph != null) Glyphs.draw(canvas, it.glyph, (l + r) / 2f, h / 2f, h * 0.4f, fg, line)
            else it.label?.let { s ->
                text.color = fg; text.textSize = h * 0.4f; text.typeface = Fonts.regular(context)
                val m = text.fontMetrics
                canvas.drawText(s, (l + r) / 2f, h / 2f - (m.ascent + m.descent) / 2f, text)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val edges = edges()
        val i = (0 until items.size).firstOrNull { e.x >= edges[it] && e.x < edges[it + 1] } ?: -1
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = i; invalidate()
                if (i >= 0 && items[i].repeat) { onClick?.invoke(i); postDelayed(repeater, 400L) }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(repeater)
                if (i >= 0 && i == pressed && !items[i].repeat) onClick?.invoke(i)
                pressed = -1; invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { removeCallbacks(repeater); pressed = -1; invalidate() }
        }
        return true
    }
}

/** The emoji, by category, read from the code points the phone can draw. */
object Emoji {
    class Category(val tab: String, val items: List<String>)

    private fun range(a: Int, b: Int) = (a..b).map { String(Character.toChars(it)) }

    fun categories(paint: Paint): List<Category> {
        fun ok(s: String) = paint.hasGlyph(s)
        return listOf(
            Category("☺", range(0x1F600, 0x1F64F) + range(0x1F910, 0x1F92F) + range(0x1F970, 0x1F97A)),
            Category("👍", range(0x1F440, 0x1F450) + range(0x1F466, 0x1F487) + range(0x1F4AA, 0x1F4AA) + range(0x1F590, 0x1F596) + range(0x1F918, 0x1F91F)),
            Category("❤", listOf("❤", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔") + range(0x1F493, 0x1F49F) + range(0x1F48B, 0x1F48F)),
            Category("🐶", range(0x1F400, 0x1F43F) + range(0x1F980, 0x1F9AE) + range(0x1F331, 0x1F344)),
            Category("🍔", range(0x1F345, 0x1F37F) + range(0x1F950, 0x1F96F)),
            Category("⚽", range(0x1F380, 0x1F3CA) + range(0x26BD, 0x26BE)),
            Category("🚗", range(0x1F680, 0x1F6C5) + range(0x1F3D4, 0x1F3F0)),
            Category("💡", range(0x1F4A1, 0x1F4FC) + range(0x1F50A, 0x1F53D)),
        ).map { cat -> Category(cat.tab, cat.items.filter(::ok)) }.filter { it.items.isNotEmpty() }
    }
}

/**
 * The grey column beside all the candidates, as on the phone: which of them to show, the single characters or all the words,
 * the one shown large and the other small above it.
 */
class SidePanel(context: Context) : View(context) {
    var onToggle: (() -> Unit)? = null
    private var colors: Colors? = null
    private var singles = false
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val fill = Paint()

    fun setColors(c: Colors) { colors = c; invalidate() }
    fun setSingles(on: Boolean) { singles = on; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val c = colors ?: return
        fill.color = if (c.dark) 0xFF474747.toInt() else 0xFFC8C8C8.toInt(); canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        val w = width.toFloat()
        text.typeface = Fonts.semilight(context)
        text.textAlign = Paint.Align.LEFT
        text.color = c.subtle; text.textSize = w * 0.15f
        canvas.drawText(if (singles) "全部" else "单字", w * 0.07f, w * 0.2f, text)
        text.textAlign = Paint.Align.CENTER
        text.color = c.text; text.textSize = w * 0.2f
        canvas.drawText(if (singles) "单字" else "全部", w / 2f, w * 0.42f, text)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP && e.y < width * 0.6f) onToggle?.invoke()
        return true
    }
}

/**
 * In place of the keys while dictating, as on the phone: the room on the left left empty, and on the right a column of the keys
 * still wanted: backspace, the comma, space, enter and the language.
 */
class VoicePanel(context: Context) : View(context) {
    class Item(val label: String? = null, val glyph: Glyph? = null, val repeat: Boolean = false)

    var onClick: ((Int) -> Unit)? = null
    private var colors: Colors? = null
    private var items: List<Item> = emptyList()
    private var pressed = -1
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val repeater = object : Runnable {
        override fun run() { if (pressed >= 0) { onClick?.invoke(pressed); postDelayed(this, 70L) } }
    }

    fun setColors(c: Colors) { colors = c; invalidate() }
    fun setItems(list: List<Item>) { items = list; invalidate() }

    private val gap get() = maxOf(context.dp(3f), width / 94f)
    private fun colLeft() = width - width * 0.15f - gap
    private fun cellH() = (height - gap) / items.size.coerceAtLeast(1)

    override fun onDraw(canvas: Canvas) {
        val c = colors ?: return
        fill.color = c.background; canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        val l = colLeft(); val r = width - gap; val ch = cellH()
        items.forEachIndexed { i, it ->
            val t = gap + i * ch; val b = t + ch - gap
            fill.color = if (i == pressed) c.accent else c.function
            canvas.drawRect(l, t, r, b, fill)
            val fg = if (i == pressed) c.onAccent else c.text
            if (it.glyph != null) Glyphs.draw(canvas, it.glyph, (l + r) / 2f, (t + b) / 2f, (b - t) * 0.4f, fg, line)
            else it.label?.let { s ->
                text.color = fg; text.textSize = (b - t) * 0.36f; text.typeface = Fonts.semilight(context)
                val m = text.fontMetrics
                canvas.drawText(s, (l + r) / 2f, (t + b) / 2f - (m.ascent + m.descent) / 2f, text)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val i = if (e.x >= colLeft()) ((e.y - gap) / cellH()).toInt().coerceIn(0, items.size - 1) else -1
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = i; invalidate()
                if (i >= 0 && items[i].repeat) { onClick?.invoke(i); postDelayed(repeater, 400L) }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(repeater)
                if (i >= 0 && i == pressed && !items[i].repeat) onClick?.invoke(i)
                pressed = -1; invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { removeCallbacks(repeater); pressed = -1; invalidate() }
        }
        return true
    }
}
