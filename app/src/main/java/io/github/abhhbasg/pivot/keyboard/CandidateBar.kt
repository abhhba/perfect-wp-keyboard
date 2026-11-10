package io.github.abhhbasg.pivot.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import io.github.abhhbasg.pivot.ui.Colors
import io.github.abhhbasg.pivot.ui.Fonts
import io.github.abhhbasg.pivot.ui.Glyph
import io.github.abhhbasg.pivot.ui.Glyphs
import io.github.abhhbasg.pivot.ui.dp
import kotlin.math.abs

/**
 * The strip above the keys, as on Windows 10 Mobile: with nothing typed, only a microphone on the left; while a word is typed,
 * the words for it in a row that slides sideways, the one space would take in the accent colour, and for Chinese an arrow at
 * the end that shows all of them in place of the keys.
 */
class CandidateBar(context: Context) : View(context) {
    interface Listener {
        fun onPick(index: Int)
        fun onExpand()
        fun onMic()
    }

    var listener: Listener? = null
    private var colors: Colors? = null
    private var words: List<String> = emptyList()
    private var expandable = false
    private var expanded = false
    private var mic = true
    private var edges = FloatArray(0)
    private var scroll = 0f
    private var pressed = NONE
    private var downX = 0f
    private var scrollAtDown = 0f
    private var dragging = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setColors(c: Colors) { colors = c; invalidate() }
    fun setMicAvailable(on: Boolean) { mic = on; invalidate() }

    /** [expandable]: the arrow that opens the page of all the words; [expanded]: that page is open (the arrow points up). */
    fun setWords(list: List<String>, expandable: Boolean = false, expanded: Boolean = false) {
        if (list != words) scroll = 0f
        words = list; this.expandable = expandable; this.expanded = expanded
        measureWords(); invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), context.dp(48f).toInt())

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = measureWords()

    private fun arrowWidth() = if (expandable) height * 1.1f else 0f
    private fun room() = width - arrowWidth()

    private fun style() { text.textSize = height * 0.42f; text.typeface = Fonts.regular(context) }

    /** Each word gets what it needs and at least a third (English) or a seventh (Chinese) of the bar. */
    private fun measureWords() {
        style()
        val min = if (expandable) room() / 7f else room() / 3f
        edges = FloatArray(words.size + 1)
        words.forEachIndexed { i, w -> edges[i + 1] = edges[i] + maxOf(min, text.measureText(w) + context.dp(24f)) }
        // Three English words share the bar evenly when they fit.
        if (!expandable && edges.last() <= room()) for (i in words.indices) edges[i + 1] = room() / words.size * (i + 1)
    }

    private fun maxScroll() = maxOf(0f, (edges.lastOrNull() ?: 0f) - room())

    override fun onDraw(canvas: Canvas) {
        val c = colors ?: return
        canvas.drawColor(c.bar)
        val h = height.toFloat()
        if (words.isEmpty()) {
            if (mic) {
                if (pressed == MIC) { fill.color = Palette_press(c); canvas.drawRect(0f, 0f, h * 1.2f, h, fill) }
                Glyphs.draw(canvas, Glyph.MIC, h * 0.6f, h / 2f, h * 0.44f, c.text, line)
            }
            return
        }
        canvas.save()
        canvas.clipRect(0f, 0f, room(), h)
        style()
        words.forEachIndexed { i, w ->
            val l = edges[i] - scroll; val r = edges[i + 1] - scroll
            if (r < 0 || l > room()) return@forEachIndexed
            if (pressed == i) { fill.color = Palette_press(c); canvas.drawRect(l, 0f, r, h, fill) }
            text.color = if (i == 0) c.accent.takeIf { readable(it, c.bar) } ?: c.text else c.text
            text.typeface = if (i == 0) Fonts.semibold(context) else Fonts.regular(context)
            val m = text.fontMetrics
            canvas.drawText(w, (l + r) / 2f, h / 2f - (m.ascent + m.descent) / 2f, text)
        }
        canvas.restore()
        if (expandable) {
            val x = room()
            if (pressed == ARROW) { fill.color = Palette_press(c); canvas.drawRect(x, 0f, width.toFloat(), h, fill) }
            Glyphs.draw(canvas, if (expanded) Glyph.CHEVRON_UP else Glyph.CHEVRON_DOWN, x + arrowWidth() / 2f, h / 2f, h * 0.34f, c.text, line)
        }
    }

    private fun Palette_press(c: Colors) = io.github.abhhbasg.pivot.ui.Palette.withAlpha(c.text, 0x26)
    private fun readable(a: Int, bg: Int) = abs(io.github.abhhbasg.pivot.ui.Palette.luminance(a) - io.github.abhhbasg.pivot.ui.Palette.luminance(bg)) > 0.15

    private fun indexAt(x: Float): Int {
        if (words.isEmpty()) return if (mic && x < height * 1.2f) MIC else NONE
        if (expandable && x >= room()) return ARROW
        val at = x + scroll
        return words.indices.firstOrNull { at < edges[it + 1] } ?: NONE
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { pressed = indexAt(e.x); downX = e.x; scrollAtDown = scroll; dragging = false; invalidate() }
            MotionEvent.ACTION_MOVE -> if (dragging || (pressed >= 0 && abs(e.x - downX) > slop && maxScroll() > 0f)) {
                dragging = true; pressed = NONE
                scroll = (scrollAtDown - (e.x - downX)).coerceIn(0f, maxScroll()); invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val i = indexAt(e.x)
                if (!dragging && i == pressed) when {
                    i == MIC -> listener?.onMic()
                    i == ARROW -> listener?.onExpand()
                    i >= 0 -> listener?.onPick(i)
                }
                pressed = NONE; invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { pressed = NONE; invalidate() }
        }
        return true
    }

    private companion object { const val NONE = -1; const val MIC = -2; const val ARROW = -3 }
}
