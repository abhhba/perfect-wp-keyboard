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
import kotlin.math.abs

/**
 * The strip above the keys, as on screenshots of the phone: the microphone on the left, then the words for what is being typed in
 * plain white, in a row that slides sideways; for Chinese an arrow at the right end opens all of them. With all of them open, the
 * strip is the first row of the grid below it: seven to a row, in the columns of the grid, the arrow pointing up.
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
    /** Dictation is on: the microphone on the accent colour, and what it is doing. */
    private var voice: String? = null
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
    fun setVoice(status: String?) { voice = status; invalidate() }
    fun setMicAvailable(on: Boolean) { if (mic != on) { mic = on; measureWords(); invalidate() } }

    fun setWords(list: List<String>, expandable: Boolean = false, expanded: Boolean = false) {
        if (list != words || expanded != this.expanded) scroll = 0f
        words = list; this.expandable = expandable; this.expanded = expanded
        measureWords(); invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        // A tenth of the width, as on the phone.
        setMeasuredDimension(w, (w * 0.107f).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = measureWords()

    private fun micWidth() = if (mic && !expanded) height * 1.15f else 0f
    private fun arrowWidth() = if (expandable || expanded) height * 1.1f else 0f
    private fun start() = micWidth()
    private fun room() = width - start() - arrowWidth()

    /** With all the candidates open: how many of them the bar shows, the first row of the grid. */
    var firstRow = 0; private set

    /** How many columns of the grid [w] takes. */
    fun columnsFor(w: String): Int { style(); return kotlin.math.ceil((text.measureText(w) + height * 0.5f) / gridCell).toInt().coerceIn(1, 7) }

    /** The columns of the open grid: a tenth of the width each, after a small margin. */
    val gridCell get() = width / 10f
    val gridPad get() = width * 0.028f

    private fun style() { text.textSize = height * 0.38f; text.typeface = Fonts.semilight(context) }

    private fun measureWords() {
        style()
        edges = FloatArray(words.size + 1)
        if (expanded) {
            // The columns of the grid below: a word takes as many as it needs, and the row ends when the next does not fit.
            edges[0] = gridPad - start()
            var used = 0
            firstRow = 0
            for ((i, w) in words.withIndex()) {
                val span = columnsFor(w)
                if (used + span > 7) break
                used += span
                edges[i + 1] = edges[i] + gridCell * span
                firstRow = i + 1
            }
            return
        }
        words.forEachIndexed { i, w -> edges[i + 1] = edges[i] + text.measureText(w) + height * 0.6f }
    }

    private fun maxScroll() = if (expanded) 0f else maxOf(0f, (edges.lastOrNull() ?: 0f) - room())

    override fun onDraw(canvas: Canvas) {
        val c = colors ?: return
        fill.color = c.bar; canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        val h = height.toFloat()
        val press = Palette.withAlpha(c.text, 0x26)
        voice?.let { status ->
            val mw = h * 1.15f
            fill.color = c.accent; canvas.drawRect(0f, 0f, mw, h, fill)
            Glyphs.draw(canvas, Glyph.MIC, mw / 2f, h / 2f, h * 0.42f, c.onAccent, line)
            style(); text.textAlign = Paint.Align.LEFT; text.color = c.text
            val m = text.fontMetrics
            canvas.drawText(status, mw + h * 0.3f, h / 2f - (m.ascent + m.descent) / 2f, text)
            text.textAlign = Paint.Align.CENTER
            return
        }
        if (micWidth() > 0f) {
            if (pressed == MIC) { fill.color = press; canvas.drawRect(0f, 0f, micWidth(), h, fill) }
            Glyphs.draw(canvas, Glyph.MIC, micWidth() / 2f, h / 2f, h * 0.42f, c.text, line)
        }
        canvas.save()
        canvas.clipRect(start(), 0f, start() + room(), h)
        style()
        val m = text.fontMetrics
        val shown = if (expanded) firstRow else words.size
        for (i in 0 until shown) {
            val l = start() + edges[i] - scroll; val r = start() + edges[i + 1] - scroll
            if (r < start() || l > start() + room()) continue
            if (pressed == i) { fill.color = press; canvas.drawRect(l, 0f, r, h, fill) }
            text.color = c.text
            canvas.drawText(words[i], (l + r) / 2f, h / 2f - (m.ascent + m.descent) / 2f, text)
        }
        canvas.restore()
        if (arrowWidth() > 0f) {
            val x = width - arrowWidth()
            if (pressed == ARROW) { fill.color = press; canvas.drawRect(x, 0f, width.toFloat(), h, fill) }
            Glyphs.draw(canvas, if (expanded) Glyph.CHEVRON_UP else Glyph.CHEVRON_DOWN, x + arrowWidth() / 2f, h / 2f, h * 0.36f, c.text, line)
        }
    }

    private fun indexAt(x: Float): Int {
        if (voice != null) return if (x < height * 1.15f) MIC else NONE
        if (x < micWidth()) return MIC
        if (arrowWidth() > 0f && x >= width - arrowWidth()) return ARROW
        val at = x - start() + scroll
        val shown = if (expanded) firstRow else words.size
        return (0 until shown).firstOrNull { at >= edges[it] && at < edges[it + 1] } ?: NONE
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
