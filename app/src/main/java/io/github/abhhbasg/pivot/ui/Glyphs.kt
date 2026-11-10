package io.github.abhhbasg.pivot.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import io.github.abhhbasg.pivot.R

enum class Glyph { SHIFT, CAPS_LOCK, BACKSPACE, ENTER, SEARCH, SEND, GO, DONE, SMILEY, MIC, CHEVRON_DOWN, CHEVRON_UP, CHEVRON_LEFT, CHEVRON_RIGHT, BACK, CLOSE, GLOBE, GEAR, NEXT_PAGE }

/**
 * The thin line glyphs of the Windows 10 Mobile keyboard, drawn with paths in a square from -1 to 1 so they stay sharp at any
 * size. The line is a sixteenth of the glyph, as in Segoe MDL2.
 */
object Glyphs {
    private val path = Path()
    private val rect = RectF()

    fun draw(c: Canvas, g: Glyph, cx: Float, cy: Float, size: Float, color: Int, paint: Paint) {
        val s = size / 2f
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = maxOf(1.2f, size / 16f) / s
        c.save()
        c.translate(cx, cy)
        c.scale(s, s)
        when (g) {
            Glyph.SHIFT, Glyph.CAPS_LOCK -> {
                c.drawLine(0f, -0.85f, 0f, 0.8f, paint)
                path.reset(); path.moveTo(-0.62f, -0.23f); path.lineTo(0f, -0.85f); path.lineTo(0.62f, -0.23f); c.drawPath(path, paint)
                if (g == Glyph.CAPS_LOCK) c.drawLine(-0.62f, 1.05f, 0.62f, 1.05f, paint)
            }
            Glyph.BACKSPACE -> {
                path.reset()
                path.moveTo(-0.95f, 0f); path.lineTo(-0.45f, -0.58f); path.lineTo(0.95f, -0.58f); path.lineTo(0.95f, 0.58f); path.lineTo(-0.45f, 0.58f); path.close()
                c.drawPath(path, paint)
                c.drawLine(0.02f, -0.24f, 0.5f, 0.24f, paint); c.drawLine(0.5f, -0.24f, 0.02f, 0.24f, paint)
            }
            Glyph.ENTER -> {
                path.reset(); path.moveTo(0.85f, -0.75f); path.lineTo(0.85f, 0.2f); path.lineTo(-0.8f, 0.2f); c.drawPath(path, paint)
                path.reset(); path.moveTo(-0.35f, -0.25f); path.lineTo(-0.85f, 0.2f); path.lineTo(-0.35f, 0.65f); c.drawPath(path, paint)
            }
            Glyph.SEARCH -> {
                // The Windows magnifier: the lens up on the right, the handle down to the left.
                c.drawCircle(0.25f, -0.25f, 0.6f, paint)
                c.drawLine(-0.17f, 0.17f, -0.88f, 0.88f, paint)
            }
            Glyph.SEND -> {
                path.reset(); path.moveTo(-0.9f, -0.8f); path.lineTo(0.9f, 0f); path.lineTo(-0.9f, 0.8f); path.lineTo(-0.55f, 0f); path.close(); c.drawPath(path, paint)
                c.drawLine(-0.55f, 0f, 0.3f, 0f, paint)
            }
            Glyph.GO -> {
                c.drawLine(-0.85f, 0f, 0.8f, 0f, paint)
                path.reset(); path.moveTo(0.2f, -0.6f); path.lineTo(0.85f, 0f); path.lineTo(0.2f, 0.6f); c.drawPath(path, paint)
            }
            Glyph.DONE -> {
                path.reset(); path.moveTo(-0.85f, 0.05f); path.lineTo(-0.3f, 0.6f); path.lineTo(0.85f, -0.6f); c.drawPath(path, paint)
            }
            Glyph.SMILEY -> {
                c.drawCircle(0f, 0f, 0.9f, paint)
                paint.style = Paint.Style.FILL
                c.drawCircle(-0.3f, -0.22f, 0.1f, paint); c.drawCircle(0.3f, -0.22f, 0.1f, paint)
                paint.style = Paint.Style.STROKE
                rect.set(-0.5f, -0.35f, 0.5f, 0.52f); c.drawArc(rect, 30f, 120f, false, paint)
            }
            Glyph.MIC -> {
                rect.set(-0.3f, -0.92f, 0.3f, 0.25f); c.drawRoundRect(rect, 0.3f, 0.3f, paint)
                rect.set(-0.62f, -0.4f, 0.62f, 0.6f); c.drawArc(rect, 0f, 180f, false, paint)
                c.drawLine(0f, 0.6f, 0f, 0.92f, paint)
            }
            Glyph.CHEVRON_DOWN -> { path.reset(); path.moveTo(-0.7f, -0.3f); path.lineTo(0f, 0.4f); path.lineTo(0.7f, -0.3f); c.drawPath(path, paint) }
            Glyph.CHEVRON_UP -> { path.reset(); path.moveTo(-0.7f, 0.3f); path.lineTo(0f, -0.4f); path.lineTo(0.7f, 0.3f); c.drawPath(path, paint) }
            Glyph.CHEVRON_LEFT -> { path.reset(); path.moveTo(0.3f, -0.7f); path.lineTo(-0.4f, 0f); path.lineTo(0.3f, 0.7f); c.drawPath(path, paint) }
            Glyph.CHEVRON_RIGHT -> { path.reset(); path.moveTo(-0.3f, -0.7f); path.lineTo(0.4f, 0f); path.lineTo(-0.3f, 0.7f); c.drawPath(path, paint) }
            Glyph.NEXT_PAGE -> {
                // The circled arrow of the phone's symbol pages.
                c.drawCircle(0f, 0f, 0.88f, paint)
                path.reset(); path.moveTo(-0.15f, -0.4f); path.lineTo(0.25f, 0f); path.lineTo(-0.15f, 0.4f); c.drawPath(path, paint)
            }
            Glyph.GEAR -> {
                c.drawCircle(0f, 0f, 0.28f, paint)
                path.reset()
                for (i in 0 until 16) {
                    val a = Math.toRadians(i * 22.5 - 11.25)
                    val r = if (i % 2 == 0) 0.92 else 0.66
                    val x = (Math.cos(a) * r).toFloat(); val y = (Math.sin(a) * r).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    val a2 = Math.toRadians(i * 22.5 + 11.25)
                    path.lineTo((Math.cos(a2) * r).toFloat(), (Math.sin(a2) * r).toFloat())
                }
                path.close(); c.drawPath(path, paint)
            }
            Glyph.BACK -> {
                c.drawLine(-0.85f, 0f, 0.85f, 0f, paint)
                path.reset(); path.moveTo(-0.25f, -0.6f); path.lineTo(-0.85f, 0f); path.lineTo(-0.25f, 0.6f); c.drawPath(path, paint)
            }
            Glyph.CLOSE -> { c.drawLine(-0.65f, -0.65f, 0.65f, 0.65f, paint); c.drawLine(0.65f, -0.65f, -0.65f, 0.65f, paint) }
            Glyph.GLOBE -> {
                c.drawCircle(0f, 0f, 0.88f, paint)
                rect.set(-0.4f, -0.88f, 0.4f, 0.88f); c.drawOval(rect, paint)
                c.drawLine(-0.88f, 0f, 0.88f, 0f, paint)
            }
        }
        c.restore()
    }
}

/** Selawik, Microsoft's open replacement for Segoe UI, in the weights the phone uses. */
object Fonts {
    private var cache: Array<Typeface>? = null
    private fun load(c: Context): Array<Typeface> = cache ?: arrayOf(
        c.resources.getFont(R.font.selawik_light), c.resources.getFont(R.font.selawik_semilight),
        c.resources.getFont(R.font.selawik), c.resources.getFont(R.font.selawik_semibold),
    ).also { cache = it }

    fun light(c: Context) = load(c)[0]
    fun semilight(c: Context) = load(c)[1]
    fun regular(c: Context) = load(c)[2]
    fun semibold(c: Context) = load(c)[3]
}

fun Context.dp(v: Float): Float = v * resources.displayMetrics.density
