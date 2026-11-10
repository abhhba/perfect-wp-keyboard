package io.github.abhhbasg.pivot.settings

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.github.abhhbasg.pivot.R
import io.github.abhhbasg.pivot.engine.Engines
import io.github.abhhbasg.pivot.ui.Palette

/**
 * The app: a pivot with three pages. *start* turns the keyboard on and has a box to try it; *settings* has the settings in three
 * groups; *about* says what the app is and where its parts come from.
 */
class SettingsActivity : Activity() {
    private lateinit var m: Metro
    private var enableStatus: TextView? = null
    private var chooseStatus: TextView? = null
    private var page = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        m = Metro(this)
        page = savedInstanceState?.getInt("page") ?: 0
        build()
    }

    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); outState.putInt("page", page) }

    private fun build() {
        val root = m.column()
        root.addView(m.text(getString(R.string.app_name).uppercase(), 14f, m.fg, 3).apply { setPadding(m.px(24f), m.px(20f), 0, 0) })
        val pivot = Pivot(m, listOf(getString(R.string.pivot_start), getString(R.string.pivot_settings), getString(R.string.pivot_about)),
            listOf(scroll(startPage()), scroll(settingsPage()), scroll(aboutPage()))) { page = it }
        root.addView(pivot, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.setBackgroundColor(m.bg)
        // Android 15 draws apps under the bars: keep the content clear of them and of the keyboard.
        if (Build.VERSION.SDK_INT >= 30) root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        } else root.fitsSystemWindows = true
        setContentView(root)
        if (page > 0) pivot.post { pivot.show(page, smooth = false) }
    }

    private fun scroll(content: View) = ScrollView(this).apply {
        isVerticalScrollBarEnabled = false
        addView(content.apply { setPadding(m.px(24f), m.px(4f), m.px(24f), m.px(32f)) })
    }

    // --- start ------------------------------------------------------------------------------------------------------

    private fun startPage(): View = m.column().apply {
        val (enable, e) = m.link("1. " + getString(R.string.step_enable), "") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        val (choose, ch) = m.link("2. " + getString(R.string.step_choose), "") { getSystemService(InputMethodManager::class.java).showInputMethodPicker() }
        enableStatus = e; chooseStatus = ch
        addView(enable); addView(choose)
        addView(m.heading(getString(R.string.try_here)))
        addView(m.textBox(getString(R.string.try_here)))
    }

    override fun onResume() {
        super.onResume()
        refreshSteps()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The keyboard picker is a window over the app: when it closes, see what was chosen.
        if (hasFocus) refreshSteps()
    }

    private fun refreshSteps() {
        val imm = getSystemService(InputMethodManager::class.java)
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val chosen = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith("$packageName/") == true
        enableStatus?.text = getString(if (enabled) R.string.step_enable_done else R.string.step_enable_todo)
        enableStatus?.setTextColor(if (enabled) m.subtle else m.accent)
        chooseStatus?.text = getString(if (chosen) R.string.step_choose_done else R.string.step_choose_todo)
        chooseStatus?.setTextColor(if (chosen) m.subtle else m.accent)
    }

    // --- settings ---------------------------------------------------------------------------------------------------

    private fun settingsPage(): View = m.column().apply {
        val p = m.prefs
        addView(m.heading(getString(R.string.group_look)).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = m.px(4f) })
        val themes = listOf("system", "dark", "light")
        addView(m.combo(getString(R.string.theme), listOf(getString(R.string.theme_system), getString(R.string.theme_dark), getString(R.string.theme_light)),
            { themes.indexOf(p.theme).coerceAtLeast(0) }) { p.theme = themes[it] })
        val (accent, accentName) = m.link(getString(R.string.accent), Palette.accents[p.accent].name) {}
        accent.setOnClickListener {
            m.pickAccent(getString(R.string.accent)) { i -> p.accent = i; recreate() }
        }
        accentName?.setTextColor(m.accent)
        addView(accent)
        addView(m.slider(getString(R.string.key_height), 70, 130, { p.keyHeight }) { p.keyHeight = it })

        addView(m.heading(getString(R.string.group_typing)))
        addView(m.toggle(getString(R.string.sound), get = { p.sound }) { p.sound = it })
        addView(m.toggle(getString(R.string.vibrate), get = { p.vibrate }) { p.vibrate = it })
        addView(m.toggle(getString(R.string.preview), get = { p.preview }) { p.preview = it })
        addView(m.toggle(getString(R.string.auto_caps), get = { p.autoCaps }) { p.autoCaps = it })
        addView(m.toggle(getString(R.string.double_space), get = { p.doubleSpace }) { p.doubleSpace = it })

        addView(m.heading(getString(R.string.group_words)))
        addView(m.toggle(getString(R.string.chinese), getString(R.string.chinese_summary), { p.chinese }) { p.chinese = it })
        addView(m.combo(getString(R.string.scheme), listOf(getString(R.string.scheme_full), getString(R.string.scheme_shuangpin)),
            { if (p.shuangpin) 1 else 0 }) { p.shuangpin = it == 1 })
        addView(m.toggle(getString(R.string.auto_correct), get = { p.autoCorrect }) { p.autoCorrect = it })
        addView(m.toggle(getString(R.string.learn), get = { p.learn }) { p.learn = it })
        addView(m.button(getString(R.string.forget)) {
            m.confirm(getString(R.string.forget_confirm), getString(R.string.ok), getString(R.string.cancel)) {
                Engines.forget(this@SettingsActivity)
                Toast.makeText(this@SettingsActivity, R.string.forgotten, Toast.LENGTH_SHORT).show()
            }
        })
    }

    // --- about ------------------------------------------------------------------------------------------------------

    /** The name, the version, one line, and where the parts come from: nothing more. */
    private fun aboutPage(): View = m.column().apply {
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        addView(m.text(getString(R.string.app_name), 26f, m.fg, 0))
        addView(m.text(getString(R.string.about_version, version), 14f, m.subtle).apply { setPadding(0, m.px(4f), 0, m.px(14f)) })
        addView(m.text(getString(R.string.about_text), 16f))
        addView(m.text(getString(R.string.about_credits), 12f, m.subtle).apply { setPadding(0, m.px(28f), 0, 0) })
    }
}
