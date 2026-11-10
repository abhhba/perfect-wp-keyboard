package io.github.abhhbasg.pivot.keyboard

/** What a key does. */
enum class Kind { CHAR, SHIFT, BACKSPACE, ENTER, SPACE, SYMBOLS, LETTERS, MORE_SYMBOLS, EMOJI, MODE }

/**
 * A key: what it types ([text], lower case for a letter), how wide it is in letter widths, and what holding it offers.
 * The view sets its rectangle when it lays the rows out.
 */
class Key(val kind: Kind, val text: String = "", val units: Float = 1f, val popup: List<String> = emptyList(), val label: String = text) {
    var left = 0f; var top = 0f; var right = 0f; var bottom = 0f
    /** Where a touch still counts as this key: the key and, at the ends of a row that is set in, the room up to the edge. */
    var hitLeft = 0f; var hitRight = 0f
    val cx get() = (left + right) / 2f
    val cy get() = (top + bottom) / 2f
    val width get() = right - left
    val height get() = bottom - top
    val isLetter get() = kind == Kind.CHAR && text.length == 1 && text[0].isLetter()
    fun hit(x: Float, y: Float) = x >= hitLeft && x < hitRight && y >= top && y < bottom
}

class Page(val rows: List<List<Key>>) {
    val keys = rows.flatten()
}

enum class PageId { LETTERS, SYMBOLS, MORE_SYMBOLS, NUMBERS, PHONE }

/** The pages of the keyboard, laid out as Windows 10 Mobile lays them out. */
object Layouts {
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

    private fun pops(s: String) = signs[s]?.split(' ').orEmpty()

    /**
     * The letters. Every letter key is as wide as the others, so the second row is set in by half a key on each side; shift and
     * backspace take a key and a half. The bottom row: &123, the smiley, the Chinese/English key of the Chinese keyboard, the
     * comma, the space bar, the full stop and enter.
     *
     * @param mode null for the English keyboard, "中" or "英" for the Chinese one (what it types now)
     */
    fun letters(mode: String?): Page {
        val chinese = mode == "中"
        fun row(s: String, first: Boolean) = s.map { ch ->
            val l = ch.toString()
            // Holding a key of the first row gives its digit, as on the phone; the others offer their accents.
            val digit = if (first) digits[s.indexOf(ch)].toString() else null
            val list = listOfNotNull(digit) + accents[l]?.split(' ').orEmpty()
            Key(Kind.CHAR, l, popup = if (list.isEmpty()) emptyList() else list + l)
        }
        val comma = if (chinese) "，" else ","
        val period = if (chinese) "。" else "."
        return Page(listOf(
            row("qwertyuiop", true),
            row("asdfghjkl", false),
            listOf(Key(Kind.SHIFT, units = 1.5f)) + row("zxcvbnm", false) + Key(Kind.BACKSPACE, units = 1.5f),
            bottom(Key(Kind.SYMBOLS, label = "&123", units = 1.5f), mode, comma, period),
        ))
    }

    private fun bottom(toggle: Key, mode: String?, comma: String, period: String): List<Key> = buildList {
        add(toggle)
        add(Key(Kind.EMOJI))
        if (mode != null) add(Key(Kind.MODE, label = mode))
        add(Key(Kind.CHAR, comma, popup = pops(comma)))
        add(Key(Kind.SPACE, " ", units = if (mode != null) 3f else 4f))
        add(Key(Kind.CHAR, period, popup = pops(period)))
        add(Key(Kind.ENTER, units = 1.5f))
    }

    /** The first page of digits and signs, and the second behind its arrow. Chinese signs on the Chinese keyboard. */
    fun symbols(more: Boolean, mode: String?): Page {
        val chinese = mode == "中"
        fun row(s: String) = s.split(' ').map { Key(Kind.CHAR, it, popup = pops(it)) }
        val rows = if (!more) listOf(
            row("1 2 3 4 5 6 7 8 9 0"),
            row(if (chinese) "@ # ¥ % & （ ） - 、" else "@ # $ % & ( ) - /"),
            listOf(Key(Kind.MORE_SYMBOLS, label = "1/2", units = 1.5f)) + row(if (chinese) "！ ； ： ‘ “ ？ /" else "! ; : ' \" ? *") + Key(Kind.BACKSPACE, units = 1.5f),
        ) else listOf(
            row("[ ] { } < > ^ + = ~"),
            row(if (chinese) "【 】 《 》 … — · $ €" else "_ \\ | ` • € £ ¥ °"),
            listOf(Key(Kind.MORE_SYMBOLS, label = "2/2", units = 1.5f)) + row(if (chinese) "~ ＠ ＃ ％ ＆ ＊ ＋" else "÷ × ± § © ® ™") + Key(Kind.BACKSPACE, units = 1.5f),
        )
        return Page(rows + listOf(bottom(Key(Kind.LETTERS, label = if (chinese) "拼音" else "abcd", units = 1.5f), mode, if (chinese) "，" else ",", if (chinese) "。" else ".")))
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
