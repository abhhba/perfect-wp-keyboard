package io.github.abhhbasg.pivot.ime

import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.abhhbasg.pivot.Prefs
import io.github.abhhbasg.pivot.engine.Candidate
import io.github.abhhbasg.pivot.engine.Engines
import io.github.abhhbasg.pivot.keyboard.ButtonRow
import io.github.abhhbasg.pivot.keyboard.CandidateBar
import io.github.abhhbasg.pivot.keyboard.Emoji
import io.github.abhhbasg.pivot.keyboard.GridPanel
import io.github.abhhbasg.pivot.keyboard.Key
import io.github.abhhbasg.pivot.keyboard.KeyListener
import io.github.abhhbasg.pivot.keyboard.KeyboardView
import io.github.abhhbasg.pivot.keyboard.Kind
import io.github.abhhbasg.pivot.keyboard.Layouts
import io.github.abhhbasg.pivot.keyboard.PageId
import io.github.abhhbasg.pivot.ui.Colors
import io.github.abhhbasg.pivot.ui.Glyph
import io.github.abhhbasg.pivot.ui.Palette

/**
 * The keyboard. Chinese is typed in Pinyin: the letters stay in the text, underlined and cut into syllables, and the bar offers
 * the characters; space takes the first, a tap takes any, and what a choice does not cover stays to be chosen. The 中/英 key
 * switches to English, where the word being typed is finished or fixed when space is pressed (backspace right after undoes a fix).
 */
class PivotIme : InputMethodService(), KeyListener, CandidateBar.Listener {
    private enum class Field { TEXT, PLAIN, PASSWORD, NUMBER, PHONE }
    private enum class Panel { NONE, CANDIDATES, EMOJI }

    private lateinit var prefs: Prefs
    private var keyboard: KeyboardView? = null
    private var bar: CandidateBar? = null
    private var grid: GridPanel? = null
    private var buttons: ButtonRow? = null
    private var panelBox: LinearLayout? = null
    private var root: LinearLayout? = null
    private var colors: Colors? = null

    private var field = Field.TEXT
    private var page = PageId.LETTERS
    private var panel = Panel.NONE
    private var shift = 0
    private var lastShiftTap = 0L
    /** The Chinese keyboard is typing Chinese (中) and not English (英). Kept from one text box to the next. */
    private var chineseMode = true
    private val composing = StringBuilder()
    private var pinyin: List<Candidate> = emptyList()
    private var english: List<String> = emptyList()
    private var englishFix: String? = null
    /** The last fix space made, so that backspace can give back what was typed. */
    private var lastFix: Pair<String, String>? = null
    private var autoSpace = false
    private var lastSpace = 0L
    private var emoji: List<Emoji.Category> = emptyList()
    private var emojiStarts: List<Int> = emptyList()

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
    }

    // --- views ------------------------------------------------------------------------------------------------------

    override fun onCreateInputView(): View {
        val kb = KeyboardView(this).also { it.listener = this; keyboard = it }
        val b = CandidateBar(this).also { it.listener = this; bar = it }
        val g = GridPanel(this).also { grid = it }
        val btn = ButtonRow(this).also { buttons = it }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(g, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            visibility = View.GONE
        }
        panelBox = box
        val frame = FrameLayout(this).apply {
            addView(kb, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(box, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))
        }
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        // Android 15 draws a keyboard under the navigation bar, and the strip with its hide and switch buttons is taller than the bar:
        // keep the keys above both.
        if (Build.VERSION.SDK_INT >= 30) r.setOnApplyWindowInsetsListener { v, insets ->
            val bottom = maxOf(insets.getInsets(WindowInsets.Type.navigationBars()).bottom, insets.getInsets(WindowInsets.Type.tappableElement()).bottom)
            v.setPadding(0, 0, 0, bottom); insets
        }
        root = r
        applyLook()
        return r
    }

    private fun applyLook() {
        val c = Palette.colors(this, prefs)
        colors = c
        keyboard?.setColors(c); bar?.setColors(c); grid?.setColors(c); buttons?.setColors(c)
        root?.setBackgroundColor(c.background)
        keyboard?.setPreview(prefs.preview)
        keyboard?.setHeightPct(prefs.keyHeight)
        bar?.setMicAvailable(voiceIme() != null && field != Field.PASSWORD)
        if (Build.VERSION.SDK_INT >= 28) window?.window?.navigationBarColor = c.background
    }

    // --- starting and stopping ------------------------------------------------------------------------------------------

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        composing.clear(); pinyin = emptyList(); english = emptyList(); lastFix = null; autoSpace = false
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        field = when {
            cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_DATETIME -> Field.NUMBER
            cls == InputType.TYPE_CLASS_PHONE -> Field.PHONE
            cls == InputType.TYPE_CLASS_TEXT && variation in PASSWORDS -> Field.PASSWORD
            cls == InputType.TYPE_CLASS_TEXT && (variation in PLAIN || info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) -> Field.PLAIN
            cls == 0 -> Field.PLAIN
            else -> Field.TEXT
        }
        page = when (field) { Field.NUMBER -> PageId.NUMBERS; Field.PHONE -> PageId.PHONE; else -> PageId.LETTERS }
        panel = Panel.NONE
        shift = 0
        applyLook()
        showPage()
        updateEnter(info)
        autoCaps()
        Engines.load(this) { updateBar() }
        updateBar()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        currentInputConnection?.finishComposingText()
        composing.clear()
        if (prefs.learn) Engines.save(this)
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // The cursor was moved out of the word being typed: leave the word as it is.
        if (composing.isNotEmpty() && (candidatesEnd == -1 || newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            composing.clear(); currentInputConnection?.finishComposingText()
            pinyin = emptyList(); english = emptyList(); closePanel(); updateBar()
        }
        if (newSelStart != oldSelStart && composing.isEmpty()) autoCaps()
    }

    private fun usesChinese() = prefs.chinese && field == Field.TEXT
    private fun typingChinese() = usesChinese() && chineseMode && page == PageId.LETTERS
    private fun modeLabel() = if (usesChinese()) (if (chineseMode) "中" else "英") else null

    private fun showPage() {
        val kb = keyboard ?: return
        kb.setPage(when (page) {
            PageId.LETTERS -> Layouts.letters(modeLabel())
            PageId.SYMBOLS -> Layouts.symbols(false, modeLabel())
            PageId.MORE_SYMBOLS -> Layouts.symbols(true, modeLabel())
            PageId.NUMBERS -> Layouts.numbers(false)
            PageId.PHONE -> Layouts.numbers(true)
        })
        kb.setShift(shift)
    }

    private fun updateEnter(info: EditorInfo?) {
        val opts = info?.imeOptions ?: 0
        keyboard?.setEnter(if (opts and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) Glyph.ENTER else when (opts and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEARCH -> Glyph.SEARCH
            EditorInfo.IME_ACTION_SEND -> Glyph.SEND
            EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_NEXT -> Glyph.GO
            EditorInfo.IME_ACTION_DONE -> Glyph.DONE
            else -> Glyph.ENTER
        })
    }

    private fun setShift(s: Int) { shift = s; keyboard?.setShift(s) }

    /** A capital at the start of a sentence, where the text box asks for it, when English is typed. */
    private fun autoCaps() {
        if (shift == 2) return
        if (!prefs.autoCaps || field != Field.TEXT || typingChinese() || page != PageId.LETTERS) { if (shift == 1) setShift(0); return }
        val info = currentInputEditorInfo ?: return
        val caps = currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0
        setShift(if (caps != 0) 1 else 0)
    }

    // --- keys -------------------------------------------------------------------------------------------------------

    override fun onDown(key: Key) {
        if (prefs.sound) {
            val fx = when (key.kind) {
                Kind.BACKSPACE -> AudioManager.FX_KEYPRESS_DELETE
                Kind.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                Kind.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            (getSystemService(AUDIO_SERVICE) as AudioManager).playSoundEffect(fx, -1f)
        }
        if (prefs.vibrate) keyboard?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    override fun onKey(key: Key, text: String) {
        when (key.kind) {
            Kind.CHAR -> type(text)
            Kind.SHIFT -> {
                val now = SystemClock.uptimeMillis()
                setShift(when { shift == 2 -> 0; shift == 1 && now - lastShiftTap < 400 -> 2; shift == 1 -> 0; else -> 1 })
                lastShiftTap = now
            }
            Kind.BACKSPACE -> backspace(false)
            Kind.ENTER -> enter()
            Kind.SPACE -> space()
            Kind.SYMBOLS -> { finishAll(); page = PageId.SYMBOLS; setShift(0); showPage() }
            Kind.MORE_SYMBOLS -> { page = if (page == PageId.SYMBOLS) PageId.MORE_SYMBOLS else PageId.SYMBOLS; showPage() }
            Kind.LETTERS -> { page = PageId.LETTERS; showPage(); autoCaps() }
            Kind.EMOJI -> { finishAll(); openEmoji() }
            Kind.MODE -> { finishAll(); chineseMode = !chineseMode; setShift(0); showPage(); autoCaps(); updateBar() }
        }
    }

    override fun onRepeat(key: Key) = backspace(true)

    override fun onCursor(steps: Int) {
        finishAll()
        val code = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        repeat(kotlin.math.abs(steps)) { sendDownUpKeyEvents(code) }
    }

    private fun type(raw: String) {
        val ic = currentInputConnection ?: return
        val text = if (shift != 0) raw.uppercase() else raw
        lastFix = null
        val letter = raw.length == 1 && raw[0] in 'a'..'z'
        if (typingChinese()) {
            if (letter && shift == 0) {
                composing.append(raw); showPinyin(); return
            }
            // A sign (or a capital): the best reading first, then the sign.
            if (composing.isNotEmpty()) pinyin.firstOrNull()?.let { commitPinyin(it, all = true) }
            ic.commitText(text, 1)
            if (shift == 1) setShift(0)
            updateBar(); return
        }
        val wordChar = text.length == 1 && (text[0].isLetter() || (text[0] == '\'' && composing.isNotEmpty()))
        if (wordChar && field != Field.PASSWORD && field != Field.NUMBER && field != Field.PHONE) {
            composing.append(text)
            ic.setComposingText(composing, 1)
            autoSpace = false
            if (shift == 1) setShift(0)
            updateBar(); return
        }
        val ends = text.length == 1 && text[0] in ".,!?;:"
        finishWord(fix = ends)
        // Punctuation goes right after the word, not after the space a picked word brought.
        if (ends && autoSpace) ic.deleteSurroundingText(1, 0)
        ic.commitText(text, 1)
        autoSpace = false
        if (shift == 1) setShift(0)
        if (text.length == 1 && text[0] in ".!?") autoCaps()
        updateBar()
    }

    private fun space() {
        val ic = currentInputConnection ?: return
        if (typingChinese() && composing.isNotEmpty()) { pinyin.firstOrNull()?.let { commitPinyin(it, all = false) } ?: commitRaw(); return }
        val now = SystemClock.uptimeMillis()
        if (composing.isEmpty() && prefs.doubleSpace && field == Field.TEXT && now - lastSpace < 600) {
            val before = ic.getTextBeforeCursor(2, 0)
            if (before != null && before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                ic.deleteSurroundingText(1, 0); ic.commitText(". ", 1); lastSpace = 0; autoSpace = false; autoCaps(); updateBar(); return
            }
        }
        val fixed = finishWord(fix = true)
        ic.commitText(" ", 1)
        if (fixed == null) lastFix = null
        lastSpace = now
        autoSpace = false
        autoCaps()
        updateBar()
    }

    private fun enter() {
        val ic = currentInputConnection ?: return
        if (typingChinese() && composing.isNotEmpty()) { commitRaw(); return }
        finishWord(fix = false)
        val info = currentInputEditorInfo
        val opts = info?.imeOptions ?: 0
        val action = opts and EditorInfo.IME_MASK_ACTION
        if (opts and EditorInfo.IME_FLAG_NO_ENTER_ACTION == 0 && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) ic.performEditorAction(action)
        else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        autoSpace = false
        autoCaps()
        updateBar()
    }

    private fun backspace(repeat: Boolean) {
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) {
            composing.setLength(composing.length - 1)
            if (typingChinese()) { if (composing.isEmpty()) { ic.commitText("", 1); pinyin = emptyList(); closePanel(); updateBar() } else showPinyin() }
            else { if (composing.isEmpty()) ic.commitText("", 1) else ic.setComposingText(composing, 1); updateBar() }
            return
        }
        val fix = lastFix
        // Only while the fixed word and its space are still right before the cursor (the cursor may have been moved since).
        if (!repeat && fix != null && ic.getTextBeforeCursor(fix.second.length + 1, 0)?.toString() == fix.second + " ") {
            // Backspace right after a fix gives back what was typed.
            ic.deleteSurroundingText(fix.second.length + 1, 0)
            ic.commitText(fix.first, 1)
            // Taking the word back is saying it is a word: it is learned, and not fixed again.
            if (prefs.learn) Engines.english?.learn(fix.first)
            lastFix = null; updateBar(); return
        }
        lastFix = null; autoSpace = false
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) ic.commitText("", 1) else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        if (!repeat) autoCaps()
        updateBar()
    }

    // --- Chinese ----------------------------------------------------------------------------------------------------

    private fun showPinyin() {
        val ic = currentInputConnection ?: return
        val eng = Engines.pinyin
        // The letters stay in the text, cut into the syllables they were read as.
        val shown = eng?.split(composing.toString())?.joinToString("'") ?: composing.toString()
        ic.setComposingText(shown, 1)
        pinyin = eng?.candidates(composing.toString(), 120) ?: emptyList()
        updateBar()
        if (panel == Panel.CANDIDATES) openCandidates()
    }

    /** Puts [c] in the text; with [all] false the Pinyin it does not stand for stays to be chosen. */
    private fun commitPinyin(c: Candidate, all: Boolean) {
        val ic = currentInputConnection ?: return
        val eng = Engines.pinyin
        val used = if (all || eng == null) composing.length else eng.consumed(composing.toString(), c.pieces)
        ic.commitText(c.text, 1)
        if (prefs.learn) eng?.learn(c.text)
        val rest = composing.substring(used)
        composing.clear()
        if (!all && rest.isNotEmpty()) { composing.append(rest); showPinyin() }
        else { pinyin = emptyList(); closePanel(); updateBar() }
    }

    private fun commitRaw() {
        currentInputConnection?.commitText(composing.toString(), 1)
        composing.clear(); pinyin = emptyList(); closePanel(); updateBar()
    }

    // --- English ----------------------------------------------------------------------------------------------------

    /** Ends the English word being typed, fixed if [fix] and a fix is offered; returns the fix made, if any. */
    private fun finishWord(fix: Boolean): String? {
        if (composing.isEmpty()) return null
        val ic = currentInputConnection ?: return null
        val typed = composing.toString()
        val f = englishFix.takeIf { fix && prefs.autoCorrect && field == Field.TEXT }
        ic.commitText(f ?: typed, 1)
        composing.clear(); english = emptyList(); englishFix = null
        lastFix = f?.let { typed to it }
        return f
    }

    private fun finishAll() {
        if (composing.isEmpty()) return
        if (typingChinese()) commitRaw() else finishWord(fix = false)
    }

    // --- the bar ----------------------------------------------------------------------------------------------------

    private fun updateBar() {
        val b = bar ?: return
        when {
            field == Field.PASSWORD || field == Field.NUMBER || field == Field.PHONE -> b.setWords(emptyList())
            typingChinese() && composing.isNotEmpty() -> b.setWords(pinyin.take(40).map { it.text }, expandable = pinyin.size > 1, expanded = panel == Panel.CANDIDATES)
            composing.isNotEmpty() && field == Field.TEXT -> {
                val typed = composing.toString()
                val eng = Engines.english
                val fix = eng?.correction(typed)?.let { eng.cased(it, typed) }
                englishFix = fix
                val list = LinkedHashSet<String>()
                // The first word is what space will put in the text.
                if (fix != null) list += fix
                list += typed
                eng?.completions(typed, 6)?.forEach { list += eng.cased(it, typed) }
                english = list.toList()
                b.setWords(english)
            }
            else -> b.setWords(emptyList())
        }
    }

    override fun onPick(index: Int) {
        val ic = currentInputConnection ?: return
        if (typingChinese() && composing.isNotEmpty()) { pinyin.getOrNull(index)?.let { commitPinyin(it, all = false) }; return }
        val word = english.getOrNull(index) ?: return
        val typed = composing.toString()
        ic.commitText("$word ", 1)
        if (prefs.learn && word == typed && Engines.english?.knows(typed) == false) Engines.english?.learn(typed)
        composing.clear(); english = emptyList(); englishFix = null; lastFix = null
        autoSpace = true
        autoCaps()
        updateBar()
    }

    override fun onExpand() { if (panel == Panel.CANDIDATES) closePanel() else openCandidates(); updateBar() }

    override fun onMic() {
        val (id, subtype) = voiceIme() ?: return
        finishAll()
        if (Build.VERSION.SDK_INT >= 28) switchInputMethod(id, subtype)
    }

    /** The phone's voice typing, if one is turned on: the microphone switches to it, as the phone's keyboard does. */
    private fun voiceIme(): Pair<String, android.view.inputmethod.InputMethodSubtype>? {
        if (Build.VERSION.SDK_INT < 28) return null
        val imm = getSystemService(InputMethodManager::class.java) ?: return null
        for (info in imm.enabledInputMethodList) {
            if (info.packageName == packageName) continue
            for (st in imm.getEnabledInputMethodSubtypeList(info, true)) if (st.mode == "voice") return info.id to st
        }
        return null
    }

    // --- panels -----------------------------------------------------------------------------------------------------

    private fun showPanel(p: Panel) {
        val box = panelBox ?: return
        val kb = keyboard ?: return
        panel = p
        box.layoutParams = box.layoutParams.apply { height = kb.height.coerceAtLeast(1) }
        box.visibility = View.VISIBLE
        kb.visibility = View.INVISIBLE
    }

    private fun closePanel() {
        if (panel == Panel.NONE) return
        panel = Panel.NONE
        panelBox?.visibility = View.GONE
        keyboard?.visibility = View.VISIBLE
    }

    /** All the words for the Pinyin, in place of the keys. */
    private fun openCandidates() {
        val g = grid ?: return
        g.setItems(pinyin.map { it.text }, 0, 0.42f)
        g.onTopItem = null
        g.onPick = { i -> pinyin.getOrNull(i)?.let { commitPinyin(it, all = false) } }
        buttons?.setItems(listOf(ButtonRow.Item(glyph = Glyph.BACK, weight = 1f), ButtonRow.Item(weight = 3f), ButtonRow.Item(glyph = Glyph.BACKSPACE, weight = 1f, repeat = true)))
        buttons?.select(-1)
        buttons?.onClick = { i -> when (i) { 0 -> { closePanel(); updateBar() }; 2 -> backspace(false) } }
        showPanel(Panel.CANDIDATES)
    }

    private fun openEmoji() {
        val g = grid ?: return
        if (emoji.isEmpty()) {
            emoji = Emoji.categories(android.graphics.Paint())
            var n = 0
            emojiStarts = emoji.map { c -> n.also { n += c.items.size } }
        }
        val all = emoji.flatMap { it.items }
        g.setItems(all, 8, 0.55f)
        g.onPick = { i -> currentInputConnection?.commitText(all[i], 1) }
        val items = listOf(ButtonRow.Item(label = if (typingChinese() || modeLabel() != null) "拼音" else "abc", weight = 1.4f)) +
            emoji.map { ButtonRow.Item(label = it.tab) } + ButtonRow.Item(glyph = Glyph.BACKSPACE, weight = 1.2f, repeat = true)
        buttons?.setItems(items)
        buttons?.select(1)
        buttons?.onClick = { i ->
            when (i) {
                0 -> { closePanel(); updateBar() }
                items.lastIndex -> backspace(false)
                else -> { g.scrollToItem(emojiStarts[i - 1]); buttons?.select(i) }
            }
        }
        g.onTopItem = { top -> val cat = emojiStarts.indexOfLast { it <= top }; if (cat >= 0) buttons?.select(cat + 1) }
        showPanel(Panel.EMOJI)
    }

    private companion object {
        val PASSWORDS = setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
        val PLAIN = setOf(InputType.TYPE_TEXT_VARIATION_URI, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_FILTER)
    }
}
