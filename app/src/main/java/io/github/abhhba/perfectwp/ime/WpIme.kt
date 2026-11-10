package io.github.abhhba.perfectwp.ime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.abhhba.perfectwp.Prefs
import io.github.abhhba.perfectwp.R
import io.github.abhhba.perfectwp.engine.Candidate
import io.github.abhhba.perfectwp.engine.Engines
import io.github.abhhba.perfectwp.keyboard.ButtonRow
import io.github.abhhba.perfectwp.keyboard.CandidateBar
import io.github.abhhba.perfectwp.keyboard.Emoji
import io.github.abhhba.perfectwp.keyboard.GridPanel
import io.github.abhhba.perfectwp.keyboard.Key
import io.github.abhhba.perfectwp.keyboard.KeyListener
import io.github.abhhba.perfectwp.keyboard.KeyboardView
import io.github.abhhba.perfectwp.keyboard.Kind
import io.github.abhhba.perfectwp.keyboard.Layouts
import io.github.abhhba.perfectwp.keyboard.Mode
import io.github.abhhba.perfectwp.keyboard.PageId
import io.github.abhhba.perfectwp.keyboard.SidePanel
import io.github.abhhba.perfectwp.keyboard.VoicePanel
import io.github.abhhba.perfectwp.settings.SettingsActivity
import io.github.abhhba.perfectwp.ui.Colors
import io.github.abhhba.perfectwp.ui.Glyph
import io.github.abhhba.perfectwp.ui.Palette
import io.github.abhhba.perfectwp.ui.dp

/**
 * The keyboard. Chinese is typed in Pinyin (or Microsoft Shuangpin, chosen in the settings): the letters stay in the text, cut
 * into syllables, and the bar offers the characters; space takes the first, a tap takes any, and what a choice does not cover
 * stays to be chosen. After a choice the bar offers what usually comes next. The 简/ENG key switches to English, where the word
 * being typed is finished or fixed when space is pressed (backspace right after undoes a fix).
 */
class WpIme : InputMethodService(), KeyListener, CandidateBar.Listener, Voice.Listener {
    private enum class Field { TEXT, PLAIN, PASSWORD, NUMBER, PHONE }
    private enum class Panel { NONE, CANDIDATES, EMOJI, VOICE }

    private lateinit var prefs: Prefs
    private var sounds: Sounds? = null
    private var voice: Voice? = null
    private var keyboard: KeyboardView? = null
    private var bar: CandidateBar? = null
    private var grid: GridPanel? = null
    private var buttons: ButtonRow? = null
    private var side: SidePanel? = null
    private var voicePanel: VoicePanel? = null
    private var panelBox: LinearLayout? = null
    private var spacer: View? = null
    private var body: LinearLayout? = null
    private var colors: Colors? = null

    private var field = Field.TEXT
    private var page = PageId.LETTERS
    private var panel = Panel.NONE
    private var shift = 0
    private var lastShiftTap = 0L
    /** Typing Chinese (简) and not English (ENG). Kept from one text box to the next. */
    private var chineseMode = true
    /** A box for an address: ".com" in place of the comma. */
    private var web = false
    /** All the candidates open, only the single characters. */
    private var singles = false
    /** The letters (or Shuangpin keys) being typed. */
    private val composing = StringBuilder()
    private var pinyin: List<Candidate> = emptyList()
    /** What comes after the last word chosen, offered while nothing is typed. */
    private var follow: List<String> = emptyList()
    /** The Chinese word written last, for what comes after it. */
    private var prevWord: String? = null
    private var english: List<String> = emptyList()
    private var englishFix: String? = null
    /** The last fix space made, so that backspace can give back what was typed. */
    private var lastFix: Pair<String, String>? = null
    /** What the press of backspace that began a slide deleted, and the text a slide up cleared, to bring back. */
    private var lastDeleted: String? = null
    private var cleared: String? = null
    private var autoSpace = false
    private var lastSpace = 0L
    private var emoji: List<Emoji.Category> = emptyList()
    private var emojiStarts: List<Int> = emptyList()

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        sounds = Sounds(this)
    }

    override fun onDestroy() { voice?.stop(); sounds?.release(); super.onDestroy() }

    // --- views ------------------------------------------------------------------------------------------------------

    /** Room above the keys where a held key of the first row draws its tiles; touches there go through to the app. */
    private fun overlayHeight() = (dp(60f) * prefs.keyHeight / 100f).toInt()

    override fun onCreateInputView(): View {
        val kb = KeyboardView(this).also { it.listener = this; keyboard = it }
        val b = CandidateBar(this).also { it.listener = this; bar = it }
        val g = GridPanel(this).also { grid = it }
        val btn = ButtonRow(this).also { buttons = it }
        val sp = SidePanel(this).also { side = it; it.onToggle = { singles = !singles; it.setSingles(singles); openCandidates() } }
        val vp = VoicePanel(this).also { voicePanel = it; it.visibility = View.GONE }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(g, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            addView(sp, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            visibility = View.GONE
        }
        panelBox = box
        val frame = FrameLayout(this).apply {
            clipChildren = false; clipToPadding = false
            addView(kb, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(box, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))
            addView(vp, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))
        }
        val bd = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false; clipToPadding = false
            addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        body = bd
        val sp0 = View(this).also { spacer = it }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false; clipToPadding = false
            addView(sp0, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, overlayHeight()))
            addView(bd, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        // Android 15 draws a keyboard under the navigation bar, and the strip with its hide and switch buttons is taller than the bar:
        // keep the keys above both.
        if (Build.VERSION.SDK_INT >= 30) bd.setOnApplyWindowInsetsListener { v, insets ->
            val bottom = maxOf(insets.getInsets(WindowInsets.Type.navigationBars()).bottom, insets.getInsets(WindowInsets.Type.tappableElement()).bottom)
            v.setPadding(0, 0, 0, bottom); insets
        }
        applyLook()
        return root
    }

    /** The app is laid out down to the top of the bar, not of the room above it, and touches in that room reach the app. */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        if (isFullscreenMode) return
        val extra = spacer?.height ?: 0
        outInsets.contentTopInsets += extra
        outInsets.visibleTopInsets += extra
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_CONTENT
    }

    private fun applyLook() {
        val c = Palette.colors(this, prefs)
        colors = c
        keyboard?.setColors(c); bar?.setColors(c); grid?.setColors(c); buttons?.setColors(c); side?.setColors(c); voicePanel?.setColors(c)
        body?.setBackgroundColor(c.background)
        keyboard?.setPreview(prefs.preview)
        keyboard?.setHeightPct(prefs.keyHeight)
        spacer?.layoutParams = spacer?.layoutParams?.apply { height = overlayHeight() }
        bar?.setMicAvailable(field != Field.PASSWORD && field != Field.NUMBER && field != Field.PHONE)
    }

    // --- starting and stopping --------------------------------------------------------------------------------------

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        composing.clear(); pinyin = emptyList(); english = emptyList(); follow = emptyList(); prevWord = null
        lastFix = null; autoSpace = false; cleared = null
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
        web = cls == InputType.TYPE_CLASS_TEXT && variation in WEB
        page = when (field) { Field.NUMBER -> PageId.NUMBERS; Field.PHONE -> PageId.PHONE; else -> PageId.LETTERS }
        if (panel == Panel.VOICE) voice?.stop()
        closePanel()
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
        if (voice?.active == true) voice?.stop()
        currentInputConnection?.finishComposingText()
        composing.clear()
        if (prefs.learn) Engines.save(this)
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // The cursor was moved out of the word being typed: leave the word as it is.
        if (composing.isNotEmpty() && (candidatesEnd == -1 || newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            composing.clear(); currentInputConnection?.finishComposingText()
            pinyin = emptyList(); english = emptyList(); follow = emptyList(); prevWord = null
            if (panel == Panel.CANDIDATES) closePanel()
            updateBar()
        }
        if (newSelStart != oldSelStart && composing.isEmpty()) autoCaps()
    }

    private fun usesChinese() = prefs.chinese && field == Field.TEXT
    private fun typingChinese() = usesChinese() && chineseMode && page == PageId.LETTERS
    private fun shuangpin() = prefs.shuangpin && typingChinese()

    /** The language key: 简 or ENG on the Chinese keyboard; none when only English is typed. */
    private fun mode(): Mode? = if (usesChinese()) (if (chineseMode) Mode.CHINESE else Mode.ENGLISH) else null

    private fun showPage() {
        val kb = keyboard ?: return
        kb.setPage(when (page) {
            PageId.LETTERS -> Layouts.letters(mode(), web, prefs.shuangpin)
            PageId.SYMBOLS -> Layouts.symbols(false, mode(), web)
            PageId.MORE_SYMBOLS -> Layouts.symbols(true, mode(), web)
            PageId.NUMBERS -> Layouts.numbers(false)
            PageId.PHONE -> Layouts.numbers(true)
        })
        kb.setKnob(page != PageId.NUMBERS && page != PageId.PHONE)
        kb.setShift(shift)
    }

    /** Switching language starts English in small letters, whatever the sentence. */
    private fun setChinese(on: Boolean) {
        if (chineseMode == on) return
        finishAll(); chineseMode = on; follow = emptyList(); prevWord = null
        setShift(0); showPage(); updateBar()
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
        if (prefs.sound) when (key.kind) {
            Kind.BACKSPACE -> sounds?.delete()
            Kind.SPACE, Kind.ENTER -> sounds?.space()
            else -> sounds?.key()
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
            Kind.LANGUAGE -> when (text) {
                // A tile of the held key: the settings, or one of the two languages; a plain tap switches.
                Layouts.SETTINGS -> { finishAll(); startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                Mode.CHINESE.key -> setChinese(true)
                "" -> setChinese(!chineseMode)
                else -> setChinese(false)
            }
        }
    }

    /** Every repeat of a held backspace clicks again: tap-tap-tap. */
    override fun onRepeat(key: Key) {
        if (prefs.sound) sounds?.delete()
        backspace(true)
    }

    override fun onArrow(keyCode: Int) { finishAll(); sendDownUpKeyEvents(keyCode) }

    /** Holding &123 opens the emoji, as the smiley in its corner says. */
    override fun onHold(key: Key) { if (key.kind == Kind.SYMBOLS) { finishAll(); openEmoji() } }

    /** Up from backspace clears all the text (the press had already deleted a character: it comes back first); down brings it back. */
    override fun onBackspaceSwipe(up: Boolean) {
        val ic = currentInputConnection ?: return
        if (up) {
            finishAll()
            lastDeleted?.let { ic.commitText(it, 1) }
            lastDeleted = null
            val all = ic.getExtractedText(ExtractedTextRequest(), 0)?.text?.toString()
            if (all.isNullOrEmpty()) return
            cleared = all
            ic.beginBatchEdit()
            ic.setSelection(0, all.length)
            ic.commitText("", 1)
            ic.endBatchEdit()
        } else {
            val back = cleared ?: return
            lastDeleted?.let { ic.commitText(it, 1) }
            lastDeleted = null
            ic.commitText(back, 1)
            cleared = null
        }
        follow = emptyList(); prevWord = null
        autoCaps(); updateBar()
    }

    private fun type(raw: String) {
        val ic = currentInputConnection ?: return
        val text = if (shift != 0) raw.uppercase() else raw
        lastFix = null; lastDeleted = null
        val letter = raw.length == 1 && raw[0] in 'a'..'z'
        if (typingChinese()) {
            // Shuangpin: ; is the ing key, a final, so only in the second place of a syllable.
            val ing = raw == ";" && shuangpin() && composing.length % 2 == 1
            if ((letter && shift == 0) || ing) { composing.append(raw); follow = emptyList(); showPinyin(); return }
            // A sign (or a capital): the best reading first, then the sign.
            if (composing.isNotEmpty()) pinyin.firstOrNull()?.let { commitPinyin(it, all = true) }
            ic.commitText(if (raw == ";") "；" else text, 1)
            follow = emptyList(); prevWord = null
            if (shift == 1) setShift(0)
            updateBar(); return
        }
        follow = emptyList(); prevWord = null
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
        lastDeleted = null
        if (typingChinese() && composing.isNotEmpty()) { pinyin.firstOrNull()?.let { commitPinyin(it, all = false) } ?: commitRaw(); return }
        follow = emptyList(); prevWord = null
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
        lastDeleted = null
        if (typingChinese() && composing.isNotEmpty()) { commitRaw(); return }
        follow = emptyList(); prevWord = null
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
        follow = emptyList(); prevWord = null
        val fix = lastFix
        // Only while the fixed word and its space are still right before the cursor (the cursor may have been moved since).
        if (!repeat && fix != null && ic.getTextBeforeCursor(fix.second.length + 1, 0)?.toString() == fix.second + " ") {
            // Backspace right after a fix gives back what was typed; taking the word back says it is a word: it is learned.
            ic.deleteSurroundingText(fix.second.length + 1, 0)
            ic.commitText(fix.first, 1)
            if (prefs.learn) Engines.english?.learn(fix.first)
            lastFix = null; updateBar(); return
        }
        lastFix = null; autoSpace = false
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) { ic.commitText("", 1); lastDeleted = null }
        else {
            if (!repeat) lastDeleted = ic.getTextBeforeCursor(1, 0)?.toString()
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
        if (!repeat) autoCaps()
        updateBar()
    }

    // --- Chinese ----------------------------------------------------------------------------------------------------

    private fun showPinyin() {
        val ic = currentInputConnection ?: return
        val eng = Engines.pinyin
        if (shuangpin()) {
            val pieces = Engines.shuangpin?.decode(composing.toString()) ?: emptyList()
            ic.setComposingText(pieces.joinToString("'").ifEmpty { composing.toString() }, 1)
            pinyin = eng?.candidatesFor(pieces, 120, prevWord)?.map { it.copy(letters = Engines.shuangpin!!.consumed(composing.toString(), it.pieces)) } ?: emptyList()
        } else {
            // The letters stay in the text, cut into the syllables they were read as.
            pinyin = eng?.candidates(composing.toString(), 120, prevWord) ?: emptyList()
            val shown = eng?.split(composing.toString())?.joinToString("'") ?: composing.toString()
            ic.setComposingText(shown, 1)
        }
        updateBar()
        if (panel == Panel.CANDIDATES) openCandidates()
    }

    /** Puts [c] in the text; with [all] false the Pinyin it does not stand for stays to be chosen. */
    private fun commitPinyin(c: Candidate, all: Boolean) {
        val ic = currentInputConnection ?: return
        val eng = Engines.pinyin
        val used = if (all) composing.length else (if (c.letters > 0) c.letters else eng?.consumed(composing.toString(), c.pieces) ?: composing.length)
        val rest = composing.substring(minOf(used, composing.length))
        // One edit for the editor: the choice and the rest of the Pinyin together, so the cursor moves once and the rest is kept.
        ic.beginBatchEdit()
        ic.commitText(c.text, 1)
        composing.clear()
        if (!all && rest.isNotEmpty()) composing.append(rest)
        if (composing.isNotEmpty()) ic.setComposingText(composing, 1)
        ic.endBatchEdit()
        if (prefs.learn) { eng?.learn(c.text); prevWord?.let { eng?.learnPair(it, c.text) } }
        prevWord = c.text
        if (composing.isNotEmpty()) { showPinyin(); return }
        pinyin = emptyList()
        follow = eng?.follow(c.text) ?: emptyList()
        if (panel == Panel.CANDIDATES) closePanel()
        updateBar()
    }

    private fun commitRaw() {
        currentInputConnection?.commitText(composing.toString(), 1)
        composing.clear(); pinyin = emptyList(); follow = emptyList(); prevWord = null
        if (panel == Panel.CANDIDATES) closePanel()
        updateBar()
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

    /** Leaving the letters (for the signs, the emoji, dictation or the other language): Pinyin goes in as its first reading. */
    private fun finishAll() {
        if (composing.isEmpty()) return
        if (typingChinese()) pinyin.firstOrNull()?.let { commitPinyin(it, all = true) } ?: commitRaw() else finishWord(fix = false)
    }

    // --- the bar ----------------------------------------------------------------------------------------------------

    private fun updateBar() {
        val b = bar ?: return
        if (panel == Panel.VOICE) return
        when {
            field == Field.PASSWORD || field == Field.NUMBER || field == Field.PHONE -> b.setWords(emptyList())
            typingChinese() && composing.isNotEmpty() ->
                b.setWords(shownPinyin().take(40).map { it.text }, expandable = pinyin.size > 1, expanded = panel == Panel.CANDIDATES)
            typingChinese() && follow.isNotEmpty() -> b.setWords(follow)
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
        if (typingChinese() && composing.isNotEmpty()) { shownPinyin().getOrNull(index)?.let { commitPinyin(it, all = false) }; return }
        if (typingChinese() && follow.isNotEmpty()) {
            // A word that comes next: it goes in, is learned after the one before, and what comes after it is offered.
            val w = follow.getOrNull(index) ?: return
            ic.commitText(w, 1)
            val eng = Engines.pinyin
            if (prefs.learn) prevWord?.let { eng?.learnPair(it, w) }
            prevWord = w
            follow = eng?.follow(w) ?: emptyList()
            updateBar(); return
        }
        val word = english.getOrNull(index) ?: return
        val typed = composing.toString()
        ic.commitText("$word ", 1)
        if (prefs.learn && word == typed && Engines.english?.knows(typed) == false) Engines.english?.learn(typed)
        composing.clear(); english = emptyList(); englishFix = null; lastFix = null
        autoSpace = true
        autoCaps()
        updateBar()
    }

    override fun onExpand() { if (panel == Panel.CANDIDATES) { closePanel(); updateBar() } else openCandidates() }

    // --- dictation --------------------------------------------------------------------------------------------------

    override fun onMic() {
        val v = voice ?: Voice(this, this).also { voice = it }
        if (v.active || panel == Panel.VOICE) { v.stop(); return }
        finishAll()
        openVoice()
        v.start(if (chineseMode && usesChinese()) "zh-CN" else "en-US")
    }

    override fun onVoiceState(state: Voice.State) {
        val b = bar ?: return
        when (state) {
            Voice.State.LISTENING -> b.setVoice(getString(R.string.voice_listening))
            Voice.State.THINKING -> b.setVoice(getString(R.string.voice_thinking))
            Voice.State.NO_PERMISSION -> b.setVoice(getString(R.string.voice_permission))
            Voice.State.UNAVAILABLE -> b.setVoice(getString(R.string.voice_unavailable))
            Voice.State.ERROR -> b.setVoice(getString(R.string.voice_error))
            Voice.State.STOPPED -> { currentInputConnection?.finishComposingText(); b.setVoice(null); closePanel(); updateBar() }
        }
    }

    override fun onPartial(text: String) { currentInputConnection?.setComposingText(text, 1) }

    override fun onFinal(text: String) { currentInputConnection?.commitText(text, 1) }

    private fun openVoice() {
        val vp = voicePanel ?: return
        val kb = keyboard ?: return
        panel = Panel.VOICE
        vp.layoutParams = vp.layoutParams.apply { height = kb.height.coerceAtLeast(1) }
        val chinese = chineseMode && usesChinese()
        vp.setItems(listOf(
            VoicePanel.Item(glyph = Glyph.BACKSPACE, repeat = true),
            VoicePanel.Item(label = if (chinese) "，" else ","),
            VoicePanel.Item(label = getString(R.string.voice_space)),
            VoicePanel.Item(glyph = Glyph.ENTER),
            VoicePanel.Item(label = if (chinese) Mode.CHINESE.key else Mode.ENGLISH.key),
        ))
        vp.onClick = { i ->
            val ic = currentInputConnection
            when (i) {
                0 -> { ic?.finishComposingText(); if (prefs.sound) sounds?.delete(); backspace(false) }
                1 -> { ic?.finishComposingText(); ic?.commitText(if (chinese) "，" else ",", 1) }
                2 -> { ic?.finishComposingText(); ic?.commitText(" ", 1) }
                3 -> { ic?.finishComposingText(); enter() }
                4 -> { voice?.stop(); setChinese(!chineseMode); onMic() }
            }
        }
        vp.visibility = View.VISIBLE
        kb.visibility = View.INVISIBLE
        panelBox?.visibility = View.GONE
        bar?.setVoice(getString(R.string.voice_listening))
    }

    // --- panels -----------------------------------------------------------------------------------------------------

    private fun showPanel(p: Panel) {
        val box = panelBox ?: return
        val kb = keyboard ?: return
        panel = p
        box.layoutParams = box.layoutParams.apply { height = kb.height.coerceAtLeast(1) }
        buttons?.visibility = if (p == Panel.CANDIDATES) View.GONE else View.VISIBLE
        if (p != Panel.CANDIDATES) side?.visibility = View.GONE
        box.visibility = View.VISIBLE
        voicePanel?.visibility = View.GONE
        kb.visibility = View.INVISIBLE
    }

    private fun closePanel() {
        if (panel == Panel.NONE) return
        panel = Panel.NONE
        singles = false
        panelBox?.visibility = View.GONE
        voicePanel?.visibility = View.GONE
        bar?.setVoice(null)
        keyboard?.visibility = View.VISIBLE
    }

    /** The candidates as shown: all of them, or only the single characters when the side panel asks for those. */
    private fun shownPinyin(): List<Candidate> = if (panel == Panel.CANDIDATES && singles) pinyin.filter { it.text.length == 1 } else pinyin

    /**
     * All the words for the Pinyin, in place of the keys, as on the phone: seven to a row in the columns the bar above lines up
     * with, the grey column beside them choosing single characters or all.
     */
    private fun openCandidates() {
        val g = grid ?: return
        val b = bar ?: return
        panel = Panel.CANDIDATES
        updateBar()
        val rest = shownPinyin().drop(b.firstRow)
        g.setItems(rest.map { it.text }, 7, 0.36f, cell = b.gridCell, pad = b.gridPad, row = b.height * 1.1f)
        g.onTopItem = null
        g.onPick = { i -> rest.getOrNull(i)?.let { commitPinyin(it, all = false) } }
        side?.layoutParams = (side?.layoutParams as? LinearLayout.LayoutParams)?.apply { width = (b.width - b.gridPad - b.gridCell * 7).toInt() }
        side?.visibility = View.VISIBLE
        side?.setSingles(singles)
        showPanel(Panel.CANDIDATES)
        updateBar()
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
        val items = listOf(ButtonRow.Item(label = if (mode() == Mode.CHINESE) "拼音" else "abc", weight = 1.4f)) +
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
        val WEB = setOf(InputType.TYPE_TEXT_VARIATION_URI, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
        val PLAIN = setOf(InputType.TYPE_TEXT_VARIATION_URI, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_FILTER)
    }
}
