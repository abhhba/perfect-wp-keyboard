package io.github.abhhbasg.pivot.engine

/**
 * Microsoft Shuangpin: every syllable is two keys, the initial and the final. zh, ch and sh are v, i and u; a syllable without
 * an initial starts with o ("oa" 啊, "oj" 安); the finals sit on the keys of this table, ing on the ; key.
 */
object Shuangpin {
    private val initials = mapOf("zh" to 'v', "ch" to 'i', "sh" to 'u')
    private val keyInitials = mapOf('v' to "zh", 'i' to "ch", 'u' to "sh")

    private val finals = mapOf(
        "iu" to 'q', "ia" to 'w', "ua" to 'w', "e" to 'e', "uan" to 'r', "er" to 'r', "ue" to 't', "uai" to 'y', "v" to 'y',
        "u" to 'u', "i" to 'i', "uo" to 'o', "o" to 'o', "un" to 'p',
        "a" to 'a', "ong" to 's', "iong" to 's', "uang" to 'd', "iang" to 'd', "en" to 'f', "eng" to 'g', "ang" to 'h', "an" to 'j',
        "ao" to 'k', "ai" to 'l', "ing" to ';',
        "ei" to 'z', "ie" to 'x', "iao" to 'c', "ui" to 'v', "ve" to 'v', "ou" to 'b', "in" to 'n', "ian" to 'm',
    )

    /** The finals on each key, printed small on the keys of the Shuangpin keyboard. */
    val hints = mapOf(
        'q' to "iu", 'w' to "ia ua", 'e' to "e", 'r' to "uan er", 't' to "ue", 'y' to "uai ü", 'u' to "sh u", 'i' to "ch i",
        'o' to "uo o", 'p' to "un", 'a' to "a", 's' to "ong", 'd' to "uang", 'f' to "en", 'g' to "eng", 'h' to "ang", 'j' to "an",
        'k' to "ao", 'l' to "ai", 'z' to "ei", 'x' to "ie", 'c' to "iao", 'v' to "zh ui", 'b' to "ou", 'n' to "in", 'm' to "ian",
    )

    /** The two keys of [syllable], or null for one the scheme has no keys for. */
    fun encode(syllable: String): String? {
        val init = when {
            syllable.length > 2 && syllable.substring(0, 2) in initials -> syllable.substring(0, 2)
            syllable[0] in "bpmfdtnlgkhjqxrzcsyw" -> syllable.substring(0, 1)
            else -> ""
        }
        val fin = syllable.substring(init.length)
        val first = when { init.isEmpty() -> 'o'; init.length == 2 -> initials[init]!!; else -> init[0] }
        val second = finals[fin] ?: return null
        return "$first$second"
    }

    /** Reads keys back into Pinyin, made once for the syllables the dictionary knows. */
    class Decoder(syllables: Collection<String>) {
        private val byKeys = HashMap<String, String>()

        init {
            for (s in syllables.sortedBy { it.length }) encode(s)?.let { code -> byKeys.putIfAbsent(code, s) }
        }

        /**
         * The pieces of Pinyin for [keys]: a syllable for each pair, and for a last key alone the initial it stands for. A pair that
         * is no syllable is read as its initial, so typing never gets stuck.
         */
        fun decode(keys: String): List<String> {
            val out = ArrayList<String>()
            var i = 0
            while (i < keys.length) {
                val k1 = keys[i]
                if (i + 1 < keys.length) {
                    out += byKeys["$k1${keys[i + 1]}"] ?: initial(k1)
                    i += 2
                } else {
                    // A lone o is the start of a syllable without an initial: nothing to read yet.
                    if (k1 != 'o' && k1 != ';') out += initial(k1)
                    i += 1
                }
            }
            return out.filter { it.isNotEmpty() }
        }

        private fun initial(k: Char) = keyInitials[k] ?: if (k in "bpmfdtnlgkhjqxrzcsywae") k.toString() else ""

        /** How many keys the first [pieces] pieces took: two each. */
        fun consumed(keys: String, pieces: Int) = minOf(keys.length, pieces * 2)
    }
}
