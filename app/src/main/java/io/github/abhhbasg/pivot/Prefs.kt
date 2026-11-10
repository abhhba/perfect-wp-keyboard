package io.github.abhhbasg.pivot

import android.content.Context
import android.content.SharedPreferences

/** Every setting of the app, in one place, with its default. */
class Prefs(context: Context) {
    val sp: SharedPreferences = context.applicationContext.getSharedPreferences("pivot", Context.MODE_PRIVATE)

    private fun b(key: String, def: Boolean) = sp.getBoolean(key, def)
    private fun i(key: String, def: Int) = sp.getInt(key, def)
    private fun s(key: String, def: String) = sp.getString(key, def) ?: def

    /** "system", "dark" or "light". */
    var theme: String get() = s(THEME, "system"); set(v) = sp.edit().putString(THEME, v).apply()
    /** Index into [io.github.abhhbasg.pivot.ui.Palette.accents]. */
    var accent: Int get() = i(ACCENT, 1); set(v) = sp.edit().putInt(ACCENT, v).apply()
    var chinese: Boolean get() = b(CHINESE, true); set(v) = sp.edit().putBoolean(CHINESE, v).apply()
    /** Microsoft Shuangpin instead of full Pinyin; only in the settings, as on the phone. */
    var shuangpin: Boolean get() = b(SHUANGPIN, false); set(v) = sp.edit().putBoolean(SHUANGPIN, v).apply()
    var sound: Boolean get() = b(SOUND, true); set(v) = sp.edit().putBoolean(SOUND, v).apply()
    var vibrate: Boolean get() = b(VIBRATE, true); set(v) = sp.edit().putBoolean(VIBRATE, v).apply()
    var preview: Boolean get() = b(PREVIEW, true); set(v) = sp.edit().putBoolean(PREVIEW, v).apply()
    var autoCaps: Boolean get() = b(AUTO_CAPS, true); set(v) = sp.edit().putBoolean(AUTO_CAPS, v).apply()
    var doubleSpace: Boolean get() = b(DOUBLE_SPACE, true); set(v) = sp.edit().putBoolean(DOUBLE_SPACE, v).apply()
    var autoCorrect: Boolean get() = b(AUTO_CORRECT, true); set(v) = sp.edit().putBoolean(AUTO_CORRECT, v).apply()
    var learn: Boolean get() = b(LEARN, true); set(v) = sp.edit().putBoolean(LEARN, v).apply()
    /** Height of a row of keys, in percent of the normal height. */
    var keyHeight: Int get() = i(KEY_HEIGHT, 100); set(v) = sp.edit().putInt(KEY_HEIGHT, v).apply()

    companion object {
        const val THEME = "theme"
        const val ACCENT = "accent"
        const val CHINESE = "chinese"
        const val SHUANGPIN = "shuangpin"
        const val SOUND = "sound"
        const val VIBRATE = "vibrate"
        const val PREVIEW = "preview"
        const val AUTO_CAPS = "auto_caps"
        const val DOUBLE_SPACE = "double_space"
        const val AUTO_CORRECT = "auto_correct"
        const val LEARN = "learn"
        const val KEY_HEIGHT = "key_height"
    }
}
