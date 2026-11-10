package io.github.abhhba.perfectwp.settings

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.abhhba.perfectwp.Prefs
import io.github.abhhba.perfectwp.R
import io.github.abhhba.perfectwp.ui.Fonts
import io.github.abhhba.perfectwp.ui.Glyph
import io.github.abhhba.perfectwp.ui.Glyphs
import io.github.abhhba.perfectwp.ui.Palette
import io.github.abhhba.perfectwp.ui.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/** The look of the app: Windows 10 Mobile's type, colours and controls, made from plain Android views. */
class Metro(val c: Context) {
    val prefs = Prefs(c)
    val fg = c.getColor(R.color.fg)
    val bg = c.getColor(R.color.bg)
    val subtle = c.getColor(R.color.subtle)
    val accent get() = Palette.accent(prefs)
    fun px(dp: Float) = c.dp(dp).roundToInt()

    fun text(s: CharSequence, sp: Float, color: Int = fg, weight: Int = 1): TextView = TextView(c).apply {
        text = s; textSize = sp; setTextColor(color); includeFontPadding = false
        typeface = when (weight) { 0 -> Fonts.light(c); 1 -> Fonts.regular(c); 2 -> Fonts.semilight(c); else -> Fonts.semibold(c) }
    }

    fun column(): LinearLayout = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

    private fun lp(top: Float = 0f, bottom: Float = 0f) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(top); bottomMargin = px(bottom) }

    /** A heading of a group of settings: plain semibold, as on the phone. */
    fun heading(s: String) = text(s, 20f, fg, 3).apply { layoutParams = lp(top = 22f, bottom = 4f) }

    /** The toggle switch: the setting's name above, the switch and On or Off below. */
    fun toggle(title: String, summary: String? = null, get: () -> Boolean, set: (Boolean) -> Unit): View {
        val box = column().apply { layoutParams = lp(top = 14f) }
        box.addView(text(title, 16f))
        summary?.let { box.addView(text(it, 13f, subtle).apply { setPadding(0, px(3f), 0, 0) }) }
        val sw = Toggle(c, accent, fg, get())
        val state = text(c.getString(if (get()) R.string.on else R.string.off), 16f)
        val row = LinearLayout(c).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(8f), 0, px(4f))
            addView(sw, LinearLayout.LayoutParams(px(48f), px(24f)))
            addView(state, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = px(14f) })
        }
        box.addView(row)
        box.isClickable = true
        box.setOnClickListener {
            val v = !get(); set(v); sw.on = v
            state.text = c.getString(if (v) R.string.on else R.string.off)
        }
        return box
    }

    /** The drop-down box: the setting's name above, the value in a rectangle with a chevron; it opens the picker. */
    fun combo(title: String, options: List<String>, get: () -> Int, set: (Int) -> Unit): View {
        val box = column().apply { layoutParams = lp(top = 14f) }
        box.addView(text(title, 16f))
        val value = text(options[get()], 16f).apply {
            background = GradientDrawable().apply { setStroke(px(2f), subtle) }
            setPadding(px(10f), px(8f), px(10f), px(8f))
            gravity = Gravity.CENTER_VERTICAL
        }
        val chevron = GlyphView(c, Glyph.CHEVRON_DOWN, fg)
        val frame = FrameLayout(c).apply {
            addView(value, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(chevron, FrameLayout.LayoutParams(px(16f), px(16f), Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd = px(12f) })
        }
        box.addView(frame, LinearLayout.LayoutParams(px(260f), ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(6f) })
        frame.setOnClickListener { pick(title, options, get()) { i -> set(i); value.text = options[i] } }
        return box
    }

    /** The slider: the name and the value above, a thin line with a tall narrow thumb below. */
    fun slider(title: String, min: Int, max: Int, get: () -> Int, set: (Int) -> Unit): View {
        val box = column().apply { layoutParams = lp(top = 14f) }
        val label = text("$title  ${get()}", 16f)
        box.addView(label)
        box.addView(Slider(c, accent, fg, min, max, get()) { v -> set(v); label.text = "$title  $v" },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(40f)))
        return box
    }

    /** A row that opens something: a big light name and a small grey line under it; it tilts a little when pressed. */
    fun link(title: String, summary: String?, onClick: () -> Unit): Pair<View, TextView?> {
        val box = column().apply { layoutParams = lp(top = 12f); setPadding(0, px(2f), 0, px(2f)) }
        box.addView(text(title, 22f, fg, 0))
        val sub = summary?.let { text(it, 13f, subtle).apply { setPadding(0, px(2f), 0, 0) } }
        sub?.let { box.addView(it) }
        box.isClickable = true
        box.setOnClickListener { onClick() }
        tilt(box)
        return box to sub
    }

    /** The outlined button. */
    fun button(s: String, onClick: () -> Unit): TextView = text(s, 16f).apply {
        gravity = Gravity.CENTER
        background = GradientDrawable().apply { setStroke(px(2f), fg) }
        setPadding(px(20f), px(10f), px(20f), px(10f))
        minWidth = px(120f)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(16f) }
    }

    /** The text box: a rectangle outlined in grey. */
    fun textBox(hint: String): EditText = EditText(c).apply {
        this.hint = hint; textSize = 16f; typeface = Fonts.regular(c)
        setTextColor(fg); setHintTextColor(subtle)
        background = GradientDrawable().apply { setStroke(px(2f), subtle) }
        setPadding(px(10f), px(10f), px(10f), px(10f))
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        minLines = 2
        layoutParams = lp(top = 12f)
    }

    /** The press of Windows Phone: the item leans away from the finger. */
    @SuppressLint("ClickableViewAccessibility")
    fun tilt(v: View) {
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.pivotX = view.width / 2f; view.pivotY = view.height / 2f
                    val dx = (e.x / view.width.coerceAtLeast(1) - 0.5f) * 2f
                    view.animate().rotationY(-dx * 6f).scaleX(0.98f).scaleY(0.98f).setDuration(80).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().rotationY(0f).scaleX(1f).scaleY(1f).setDuration(140).start()
            }
            false
        }
    }

    private fun fullScreen(): Dialog = Dialog(c, android.R.style.Theme_DeviceDefault_NoActionBar).apply {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.setBackgroundDrawableResource(R.color.bg)
    }

    /** The picker: the whole screen, the question in small capitals, the choices in large light type, the current in the accent colour. */
    fun pick(title: String, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
        val d = fullScreen()
        val list = column().apply { setPadding(px(24f), px(40f), px(24f), px(24f)) }
        list.addView(text(title.uppercase(), 14f, fg, 3).apply { setPadding(0, 0, 0, px(18f)) })
        options.forEachIndexed { i, o ->
            list.addView(text(o, 28f, if (i == selected) accent else fg, 0).apply {
                setPadding(0, px(8f), 0, px(8f))
                setOnClickListener { d.dismiss(); onPick(i) }
                tilt(this)
            })
        }
        d.setContentView(ScrollView(c).apply { addView(list) })
        d.show()
    }

    /** The accent colours as tiles, four to a row; the current one has a frame. */
    fun pickAccent(title: String, onPick: (Int) -> Unit) {
        val d = fullScreen()
        val list = column().apply { setPadding(px(24f), px(40f), px(24f), px(24f)) }
        list.addView(text(title.uppercase(), 14f, fg, 3).apply { setPadding(0, 0, 0, px(18f)) })
        val grid = GridLayout(c).apply { columnCount = 4 }
        val size = ((c.resources.displayMetrics.widthPixels - px(48f)) / 4f - px(8f)).roundToInt()
        Palette.accents.forEachIndexed { i, a ->
            grid.addView(View(c).apply {
                background = GradientDrawable().apply { setColor(a.color); if (i == prefs.accent) setStroke(px(3f), fg) }
                contentDescription = a.name
                setOnClickListener { d.dismiss(); onPick(i) }
                tilt(this)
            }, GridLayout.LayoutParams().apply { width = size; height = size; setMargins(0, 0, px(8f), px(8f)) })
        }
        list.addView(grid)
        d.setContentView(ScrollView(c).apply { addView(list) })
        d.show()
    }

    /** A message across the screen with square outlined buttons. */
    fun confirm(message: String, yes: String, no: String, onYes: () -> Unit) {
        val d = Dialog(c, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
        val box = column().apply {
            setBackgroundColor(Palette.withAlpha(if (Palette.luminance(bg) < 0.5) 0xFF2B2B2B.toInt() else 0xFFE6E6E6.toInt(), 0xFF))
            setPadding(px(24f), px(24f), px(24f), px(20f))
            addView(text(message, 18f))
        }
        val buttons = LinearLayout(c).apply { setPadding(0, px(20f), 0, 0) }
        buttons.addView(button(yes) { d.dismiss(); onYes() }.apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = px(8f) } })
        buttons.addView(button(no) { d.dismiss() }.apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = px(8f) } })
        box.addView(buttons)
        d.setContentView(box)
        d.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.TOP)
            setBackgroundDrawable(null)
        }
        d.show()
    }
}

/** The switch: an outlined pill with a small knob on the left when off; filled with the accent, the knob on the right, when on. */
class Toggle(context: Context, private val accent: Int, private val fg: Int, initial: Boolean) : View(context) {
    var on = initial; set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        val sw = context.dp(2f)
        val h = height.toFloat(); val w = width.toFloat(); val r = h / 2f
        if (on) { p.style = Paint.Style.FILL; p.color = accent; canvas.drawRoundRect(0f, 0f, w, h, r, r, p) }
        else { p.style = Paint.Style.STROKE; p.strokeWidth = sw; p.color = fg; canvas.drawRoundRect(sw / 2, sw / 2, w - sw / 2, h - sw / 2, r, r, p) }
        p.style = Paint.Style.FILL
        p.color = if (on) Palette.onColor(accent) else fg
        val k = h * 0.25f
        canvas.drawCircle(if (on) w - r else r, r, k, p)
    }
}

/** The slider: a thin line, the accent up to the thumb, a tall narrow thumb in the text colour. */
class Slider(context: Context, private val accent: Int, private val fg: Int, private val min: Int, private val max: Int, initial: Int, private val onChange: (Int) -> Unit) : View(context) {
    private var value = initial
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbW get() = context.dp(8f)
    override fun onDraw(canvas: Canvas) {
        val cy = height / 2f
        val usable = width - thumbW
        val x = thumbW / 2f + usable * (value - min) / (max - min).toFloat()
        p.color = Palette.withAlpha(fg, 0x66); canvas.drawRect(0f, cy - context.dp(1f), width.toFloat(), cy + context.dp(1f), p)
        p.color = accent; canvas.drawRect(0f, cy - context.dp(1f), x, cy + context.dp(1f), p)
        p.color = fg; canvas.drawRect(x - thumbW / 2f, cy - context.dp(12f), x + thumbW / 2f, cy + context.dp(12f), p)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        val f = ((e.x - thumbW / 2f) / (width - thumbW)).coerceIn(0f, 1f)
        val v = (min + f * (max - min)).roundToInt()
        if (v != value) { value = v; invalidate(); onChange(v) }
        return true
    }
}

class GlyphView(context: Context, private val g: Glyph, private val color: Int) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) = Glyphs.draw(canvas, g, width / 2f, height / 2f, minOf(width, height).toFloat(), color, p)
}

/**
 * The pivot: the titles side by side in large light type, the current one bright and the others faint, over pages that slide
 * with the finger and settle on one. The titles slide with the pages.
 */
@SuppressLint("ViewConstructor")
class Pivot(private val m: Metro, titles: List<String>, private val pages: List<View>, private val onPage: (Int) -> Unit) : LinearLayout(m.c) {
    private val labels = titles.map { t -> m.text(t, 44f, m.fg, 0).apply { setSingleLine(); setPadding(0, 0, m.px(22f), 0) } }
    private val strip = LinearLayout(m.c).apply { labels.forEach { addView(it) } }
    private var current = 0
    private val pager = object : HorizontalScrollView(m.c) {
        private var downX = 0f
        override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) { super.onScrollChanged(l, t, oldl, oldt); follow() }
        // The page under the finger gets the touch first; the pager takes it over only once it slides, so the start is noted here.
        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) downX = ev.x
            return super.dispatchTouchEvent(ev)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            val r = super.onTouchEvent(ev)
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                val w = width.coerceAtLeast(1)
                val moved = downX - ev.x
                // A short swipe is enough to go to the next page, as on the phone.
                val target = when { moved > w / 6f -> current + 1; moved < -w / 6f -> current - 1; else -> (scrollX + w / 2) / w }
                show(target.coerceIn(0, pages.size - 1), smooth = true)
            }
            return r
        }
        override fun fling(velocityX: Int) {}
    }
    private val row = LinearLayout(m.c)

    init {
        orientation = VERTICAL
        val clip = FrameLayout(m.c).apply { setPadding(m.px(22f), 0, 0, 0); clipChildren = true; addView(strip, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
        addView(clip, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        pager.isHorizontalScrollBarEnabled = false
        pager.overScrollMode = OVER_SCROLL_NEVER
        pager.addView(row)
        addView(pager, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        labels.forEachIndexed { i, l -> l.setOnClickListener { show(i, smooth = true) } }
        // Each page is as wide as the screen; the width is corrected once the pivot knows its own.
        val w = m.c.resources.displayMetrics.widthPixels
        pages.forEach { row.addView(it, LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT)) }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Not during this layout pass: a change of size asked for while laying out would be ignored.
        post {
            for (p in pages) p.layoutParams = (p.layoutParams as LinearLayout.LayoutParams).apply { width = w }
            row.requestLayout()
            show(current, smooth = false)
        }
    }

    fun show(i: Int, smooth: Boolean) {
        current = i
        val x = i * pager.width
        if (smooth) pager.smoothScrollTo(x, 0) else pager.scrollTo(x, 0)
        onPage(i)
        follow()
    }

    private fun follow() {
        val w = pager.width.coerceAtLeast(1)
        val pos = pager.scrollX / w.toFloat()
        val lower = pos.toInt().coerceIn(0, labels.lastIndex)
        val upper = (lower + 1).coerceAtMost(labels.lastIndex)
        fun left(i: Int) = labels.take(i).sumOf { it.width }.toFloat()
        strip.translationX = -(left(lower) + (left(upper) - left(lower)) * (pos - lower))
        labels.forEachIndexed { i, l -> l.alpha = 1f - 0.6f * abs(pos - i).coerceIn(0f, 1f) }
    }
}
