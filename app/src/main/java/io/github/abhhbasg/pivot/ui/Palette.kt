package io.github.abhhbasg.pivot.ui

import android.content.Context
import android.content.res.Configuration
import io.github.abhhbasg.pivot.Prefs

/** One of the accent colours offered, with its name. */
class Accent(val name: String, val color: Int)

/** The colours the keyboard is painted with, resolved from the settings. */
data class Colors(
    val dark: Boolean,
    val background: Int,
    val bar: Int,
    val key: Int,
    val function: Int,
    val text: Int,
    val subtle: Int,
    val accent: Int,
    val onAccent: Int,
    val popup: Int,
)

object Palette {
    /** The accent colours of the phone's settings. */
    val accents = listOf(
        Accent("lime", 0xFFA4C400.toInt()), Accent("blue", 0xFF0078D7.toInt()), Accent("green", 0xFF60A917.toInt()),
        Accent("emerald", 0xFF008A00.toInt()), Accent("teal", 0xFF00ABA9.toInt()), Accent("cyan", 0xFF1BA1E2.toInt()),
        Accent("cobalt", 0xFF0050EF.toInt()), Accent("indigo", 0xFF6A00FF.toInt()), Accent("violet", 0xFFAA00FF.toInt()),
        Accent("pink", 0xFFF472D0.toInt()), Accent("magenta", 0xFFD80073.toInt()), Accent("crimson", 0xFFA20025.toInt()),
        Accent("red", 0xFFE51400.toInt()), Accent("orange", 0xFFFA6800.toInt()), Accent("amber", 0xFFF0A30A.toInt()),
        Accent("yellow", 0xFFE3C800.toInt()), Accent("brown", 0xFF825A2C.toInt()), Accent("olive", 0xFF6D8764.toInt()),
        Accent("steel", 0xFF647687.toInt()), Accent("mauve", 0xFF76608A.toInt()), Accent("taupe", 0xFF87794E.toInt()),
    )

    fun accent(p: Prefs): Int = accents.getOrElse(p.accent) { accents[1] }.color

    fun isDark(context: Context, p: Prefs): Boolean = when (p.theme) {
        "dark" -> true
        "light" -> false
        else -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * Measured on a Windows 10 Mobile phone: in the dark the keyboard is nearly black, the letters are dark grey keys, and shift,
     * backspace, &123 and the smiley are a lighter grey; the enter key is the inverse of the keyboard.
     */
    fun colors(context: Context, p: Prefs): Colors {
        val accent = accent(p)
        return if (isDark(context, p)) Colors(
            dark = true, background = 0xFF171717.toInt(), bar = 0xFF1F1F1F.toInt(), key = 0xFF333333.toInt(), function = 0xFF4A4A4A.toInt(),
            text = 0xFFFFFFFF.toInt(), subtle = 0xFF9A9A9A.toInt(), accent = accent, onAccent = onColor(accent), popup = 0xFF2B2B2B.toInt(),
        ) else Colors(
            dark = false, background = 0xFFDADADA.toInt(), bar = 0xFFE6E6E6.toInt(), key = 0xFFF7F7F7.toInt(), function = 0xFFC2C2C2.toInt(),
            text = 0xFF000000.toInt(), subtle = 0xFF5A5A5A.toInt(), accent = accent, onAccent = onColor(accent), popup = 0xFFFFFFFF.toInt(),
        )
    }

    /** Black or white, whichever reads better on [color]. */
    fun onColor(color: Int): Int = if (luminance(color) > 0.55) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()

    fun luminance(color: Int): Double {
        fun ch(v: Int): Double { val c = v / 255.0; return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch(color shr 16 and 0xFF) + 0.7152 * ch(color shr 8 and 0xFF) + 0.0722 * ch(color and 0xFF)
    }

    fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)
}
