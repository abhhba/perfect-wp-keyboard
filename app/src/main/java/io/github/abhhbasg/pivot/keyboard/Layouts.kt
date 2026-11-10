package io.github.abhhbasg.pivot.keyboard

/** What a key does. */
enum class Kind { CHAR, SHIFT, BACKSPACE, ENTER, SPACE, SYMBOLS, LETTERS, MORE_SYMBOLS, LANGUAGE }

/**
 * A key: what it types ([text], lower case for a letter), how wide it is in letter widths, and the tiles holding it offers
 * ([popup], laid out [columns] to a row; an empty string is a gap). The view sets its rectangle when it lays the rows out.
 */
class Key(
    val kind: Kind,
    val text: String = "",
    val units: Float = 1f,
    val popup: List<String> = emptyList(),
    val label: String = text,
    val columns: Int = 0,
    /** The tile picked when the bar opens, before the finger moves. */
    val initial: Int = 0,
    /** Small text on the key: the finals of a Shuangpin key. */
    val hint: String? = null,
    /** Drawn as a function key (the ing key of Shuangpin takes the place and look of shift). */
    val fn: Boolean = false,
) {
    var left = 0f; var top = 0f; var right = 0f; var bottom = 0f
    /** Where a touch still counts as this key: the key and, at the ends of a row that is set in, the room up to the edge. */
    var hitLeft = 0f; var hitRight = 0f
    val cx get() = (left + right) / 2f
    val cy get() = (top + bottom) / 2f
    val width get() = right - left
    val height get() = bottom - top
    val isLetter get() = kind == Kind.CHAR && text.length == 1 && text[0].isLetter()
    val gridColumns get() = if (columns > 0) columns else if (popup.size <= 6) popup.size else (popup.size + 1) / 2
    fun hit(x: Float, y: Float) = x >= hitLeft && x < hitRight && y >= top && y < bottom
}

class Page(val rows: List<List<Key>>) {
    val keys = rows.flatten()
}

enum class PageId { LETTERS, SYMBOLS, MORE_SYMBOLS, NUMBERS, PHONE }

/** What the keyboard is typing: the language key shows it, and it decides the punctuation. */
enum class Mode(val key: String) { CHINESE("简"), ENGLISH("ENG") }

/** The pages of the keyboard, laid out as on screenshots of a Windows 10 Mobile phone. */
object Layouts {
    /** Holding the language key: the settings above, the two languages below. "\n" breaks a tile's label. */
    const val SETTINGS = "⚙"
    val LANGUAGE_TILES = listOf(SETTINGS, "", Mode.CHINESE.key, "ENG\nUS")
    val DOMAINS = listOf(".cn", ".net", ".edu", ".com", ".org", ".gov")

    private val accents = mapOf(
        "a" to "à á â ä ã å æ ā", "e" to "è é ê ë ē ę", "i" to "ì í î ï ī", "o" to "ò ó ô ö õ ø œ ō", "u" to "ù ú û ü ū ǖ ǘ ǚ ǜ",
        "c" to "ç ć č", "n" to "ñ ń", "s" to "ß ś š", "y" to "ý ÿ", "z" to "ž ź ż", "l" to "ł", "d" to "ð", "t" to "þ",
    )
    private val digits = "1234567890"

    private val signs = mapOf(
        "." to ". , ? ! : ; … @", "," to ", ; : ' \"", "-" to "- – — _ •", "'" to "' ‘ ’ `", "\"" to "\" “ ” « » „",
        "$" to "$ € £ ¥ ₩ ¢", "?" to "? ¿", "!" to "! ¡", "(" to "( [ { <", ")" to ") ] } >", "/" to "/ \\ |", "%" to "% ‰",
        "0" to "0 °", "&" to "& §", "*" to "* †", "+" to "+ ±",
        "，" to "， , ；", "。" to "。 . ……", "？" to "？ ?", "！" to "！ !", "：" to "： :", "“" to "“ ” \"", "（" to "（ ）",
    )

    private fun sign(s: String, units: Float = 1f) = Key(Kind.CHAR, s, units, popup = signs[s]?.split(' ').orEmpty())

    /**
     * The bottom row. &123 (with the smiley in its corner: holding it opens the emoji), the language key, the comma or, in a
     * box for an address, ".com" (holding it offers the other endings), the space bar, the full stop and enter.
     */
    private fun bottom(toggle: Key, mode: Mode?, web: Boolean): List<Key> = buildList {
        val chinese = mode == Mode.CHINESE
        add(toggle)
        if (mode != null && toggle.kind == Kind.SYMBOLS) add(Key(Kind.LANGUAGE, label = mode.key, popup = LANGUAGE_TILES, columns = 2, initial = if (chinese) 2 else 3))
        if (web) add(Key(Kind.CHAR, ".com", 1.5f, popup = DOMAINS, columns = 3, initial = 3))
        else add(sign(if (chinese) "，" else ","))
        val used = 1.5f + (if (mode != null && toggle.kind == Kind.SYMBOLS) 1f else 0f) + (if (web) 1.5f else 1f) + (if (web) 1.5f else 1f) + 1.5f
        add(Key(Kind.SPACE, " ", units = 10f - used))
        add(sign(if (chinese && !web) "。" else ".", if (web) 1.5f else 1f))
        add(Key(Kind.ENTER, units = 1.5f))
    }

    /**
     * The letters: every key as wide as the others, the second row set in by half a key, shift and backspace a key and a half.
     * For Shuangpin ([shuangpin] and Chinese) shift becomes the ing key (;); the keys stay plain letters, without their finals.
     */
    fun letters(mode: Mode?, web: Boolean, shuangpin: Boolean = false): Page {
        val sp = shuangpin && mode == Mode.CHINESE
        fun row(s: String, first: Boolean) = s.map { ch ->
            val l = ch.toString()
            // Holding a key of the first row gives its digit first; the other keys offer their accents.
            val list = listOfNotNull(if (first) digits[s.indexOf(ch)].toString() else null) + (if (sp) emptyList() else accents[l]?.split(' ').orEmpty())
            Key(Kind.CHAR, l, popup = if (list.isEmpty()) emptyList() else list + l)
        }
        val first = if (sp) Key(Kind.CHAR, ";", 1.5f, label = "ing", fn = true) else Key(Kind.SHIFT, units = 1.5f)
        return Page(listOf(
            row("qwertyuiop", true),
            row("asdfghjkl", false),
            listOf(first) + row("zxcvbnm", false) + Key(Kind.BACKSPACE, units = 1.5f),
            bottom(Key(Kind.SYMBOLS, label = "&123", units = 1.5f), mode, web),
        ))
    }

    /** The digits and signs, and the second page behind the circled arrow. The key back to the letters names what they type. */
    fun symbols(more: Boolean, mode: Mode?, web: Boolean): Page {
        val chinese = mode == Mode.CHINESE
        fun row(s: String) = s.split(' ').map { sign(it) }
        val rows = if (!more) listOf(
            row("1 2 3 4 5 6 7 8 9 0"),
            row(if (chinese) "@ # ¥ % & * （ ） - 、" else "@ # $ % & * ( ) - \\"),
            listOf(Key(Kind.MORE_SYMBOLS, units = 1.5f)) + row(if (chinese) "！ ； ： ‘ “ ？ /" else "! ; : ' \" ? /") + Key(Kind.BACKSPACE, units = 1.5f),
        ) else listOf(
            row("[ ] { } < > ^ + = ~"),
            row(if (chinese) "【 】 《 》 … — · $ € £" else "_ | ` • € £ ¥ ° § …"),
            listOf(Key(Kind.MORE_SYMBOLS, units = 1.5f)) + row(if (chinese) "＠ ＃ ％ ＆ ＊ ＋ ～" else "÷ × ± © ® ™ ¶") + Key(Kind.BACKSPACE, units = 1.5f),
        )
        val back = Key(Kind.LETTERS, label = if (chinese) "拼音" else "abc", units = 1.5f)
        return Page(rows + listOf(bottom(back, mode, web)))
    }

    /** The pad for numbers and phone numbers: big keys, three across. */
    fun numbers(phone: Boolean): Page {
        val third = 10f / 3f
        fun row(s: String) = s.split(' ').map { Key(Kind.CHAR, it, units = third) }
        return Page(listOf(
            row("1 2 3"), row("4 5 6"), row("7 8 9"),
            row(if (phone) "+ 0 #" else ", 0 ."),
            listOf(Key(Kind.CHAR, if (phone) "*" else "-", units = 2.5f), Key(Kind.SPACE, " ", units = 2.5f), Key(Kind.BACKSPACE, units = 2.5f), Key(Kind.ENTER, units = 2.5f)),
        ))
    }
}
