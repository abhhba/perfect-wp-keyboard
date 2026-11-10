package io.github.abhhbasg.pivot.engine

import java.io.BufferedReader
import kotlin.math.ln

/** A word of the dictionary: its characters, the syllable of each, and the logarithm of its probability. */
class Entry(val word: String, val syllables: Array<String>, val score: Float)

/** A choice for the bar: the characters, how many of the pieces of Pinyin they stand for, and how many typed letters that is. */
data class Candidate(val text: String, val pieces: Int, val letters: Int = 0)

/**
 * Typing Chinese with Pinyin. The letters typed are cut into pieces, each a whole syllable ("ni", "hao") or the start of one
 * ("n", "zh"); a word matches when each of its syllables starts with the piece in its place, so "nihao", "nih" and "nh" all
 * find 你好. The first choice is the whole input read as the likeliest sentence (the product of the words' probabilities); then
 * come the words for the first pieces, longest first. After a choice, [follow] offers what usually comes next.
 */
class PinyinEngine(entries: List<Entry>) {
    /** Words by the first letters of their syllables ("nh" for 你好). */
    private val byInitials = HashMap<String, MutableList<Entry>>()
    /** Words of two characters or more by their first character, for what comes after a choice. */
    private val byFirst = HashMap<Char, MutableList<Entry>>()
    val syllables = HashSet<String>()
    private val syllablePrefixes = HashSet<String>()
    private val maxLen: Int
    /** What the user picked, and how often: it raises those words. A key "a→b" counts how often b was written after a. */
    val learned = HashMap<String, Int>()

    init {
        var longest = 1
        for (e in entries) {
            byInitials.getOrPut(e.syllables.joinToString("") { it.take(1) }) { ArrayList() }.add(e)
            if (e.word.length > 1) byFirst.getOrPut(e.word[0]) { ArrayList() }.add(e)
            for (s in e.syllables) syllables += s
            longest = maxOf(longest, e.syllables.size)
        }
        for (s in syllables) for (i in 1..s.length) syllablePrefixes += s.substring(0, i)
        for (list in byInitials.values) list.sortByDescending { it.score }
        for (list in byFirst.values) list.sortByDescending { it.score }
        maxLen = longest
    }

    /**
     * A piece that must be read as a whole syllable. The interjections that are a bare consonant (嗯 "n", 呣 "m", "ng", "hm")
     * are not: a lone "n" is far more often the first letter of 你 or 那.
     */
    private fun whole(p: String) = p in syllables && p !in INTERJECTIONS

    /**
     * Cuts [input] into pieces. Whole syllables are preferred, longest first, as long as the rest can still be read; where no
     * syllable fits, the longest start of one is taken. An apostrophe forces a cut ("xi'an").
     */
    fun split(input: String): List<String> {
        val out = ArrayList<String>()
        for (part in input.lowercase().split('\'')) {
            if (part.isEmpty()) continue
            out += splitPart(part)
        }
        return out
    }

    private fun splitPart(s: String): List<String> {
        // Fewest pieces, with whole syllables before starts of syllables: a small search over the positions.
        val n = s.length
        val best = arrayOfNulls<Pair<Int, List<String>>>(n + 1) // cost, pieces
        best[n] = 0 to emptyList()
        for (i in n - 1 downTo 0) {
            var choice: Pair<Int, List<String>>? = null
            for (j in minOf(n, i + 6) downTo i + 1) {
                val piece = s.substring(i, j)
                val rest = best[j] ?: continue
                val cost = when {
                    whole(piece) -> 10
                    // An unfinished syllable costs more in the middle than at the end, where the user is still typing it.
                    piece in syllablePrefixes -> if (j == n) 12 else 25
                    else -> continue
                } + rest.first
                if (choice == null || cost < choice.first) choice = cost to listOf(piece) + rest.second
            }
            // A letter that starts no syllable (a "v" alone, say) is a piece of its own, so typing never gets stuck.
            best[i] = choice ?: (100 + (best[i + 1]?.first ?: 0) to listOf(s.substring(i, i + 1)) + (best[i + 1]?.second ?: emptyList()))
        }
        return best[0]!!.second
    }

    /**
     * Every way to cut [input] into whole syllables (the last may be unfinished), up to a few dozen: "hengaoxing" is both
     * hen gao xing and heng ao xing, and only the words tell which.
     */
    fun splits(input: String): List<List<String>> {
        val out = ArrayList<List<String>>()
        out += split(input)
        val parts = input.lowercase().split('\'').filter { it.isNotEmpty() }
        if (parts.size != 1 || parts[0].length > 24) return out
        val s = parts[0]
        fun dfs(i: Int, acc: MutableList<String>) {
            if (out.size >= 24) return
            if (i == s.length) { if (acc != out[0]) out += acc.toList(); return }
            for (j in minOf(s.length, i + 6) downTo i + 1) {
                val piece = s.substring(i, j)
                if (whole(piece) || (j == s.length && piece in syllablePrefixes)) { acc += piece; dfs(j, acc); acc.removeAt(acc.size - 1) }
            }
        }
        dfs(0, ArrayList())
        return out
    }

    private fun matches(e: Entry, pieces: List<String>, from: Int): Boolean {
        for (k in e.syllables.indices) {
            val p = pieces[from + k]
            val syl = e.syllables[k]
            if (whole(p)) { if (syl != p) return false } else if (!syl.startsWith(p)) return false
        }
        return true
    }

    private fun boost(w: String) = learned[w]?.let { 4.0 * ln(1.0 + it) } ?: 0.0
    private fun pairBoost(prev: String?, w: String) = if (prev == null) 0.0 else learned["$prev→$w"]?.let { 3.0 * ln(1.0 + it) } ?: 0.0

    /** The words that read pieces [from] .. [from]+[count]-1, best first. */
    fun words(pieces: List<String>, from: Int, count: Int, limit: Int, prev: String? = null): List<Entry> {
        val key = pieces.subList(from, from + count).joinToString("") { it.take(1) }
        val list = byInitials[key] ?: return emptyList()
        val out = ArrayList<Entry>()
        // Enough of the commonest to let a learned word overtake them, then sorted with what was learned.
        for (e in list) if (matches(e, pieces, from)) { out += e; if (out.size >= maxOf(limit * 3, 300)) break }
        return out.sortedByDescending { it.score + boost(it.word) + pairBoost(prev, it.word) }.take(limit)
    }

    /** The likeliest sentence for all the pieces, or null when there is none; [prev] is the word written before them. */
    fun sentence(pieces: List<String>, prev: String? = null): String? = scored(pieces, prev)?.first

    private fun scored(pieces: List<String>, prev: String?): Pair<String, Double>? {
        val n = pieces.size
        if (n == 0) return null
        val best = DoubleArray(n + 1) { Double.NEGATIVE_INFINITY }
        val back = arrayOfNulls<Entry>(n + 1)
        best[0] = 0.0
        for (i in 0 until n) {
            if (best[i] == Double.NEGATIVE_INFINITY) continue
            val before = if (i == 0) prev else null
            for (len in 1..minOf(maxLen, n - i)) {
                val w = words(pieces, i, len, 1, before).firstOrNull() ?: continue
                // The probabilities of the words multiplied: the reading in fewer, commoner words wins.
                val v = best[i] + w.score + boost(w.word) + pairBoost(before, w.word) - WORD_COST
                if (v > best[i + len]) { best[i + len] = v; back[i + len] = w }
            }
        }
        if (back[n] == null) return null
        val words = ArrayList<String>()
        var i = n
        while (i > 0) { val w = back[i]!!; words.add(0, w.word); i -= w.syllables.size }
        return words.joinToString("") to best[n]
    }

    /**
     * What the bar offers for [input]: of all the ways to cut it, the one whose sentence is likeliest; its sentence first, then the
     * words of its first pieces, longest first. Each choice says how many of the typed letters it uses.
     */
    fun candidates(input: String, limit: Int = 60, prev: String? = null): List<Candidate> {
        val options = splits(input)
        val pieces = options.maxByOrNull { scored(it, prev)?.second ?: Double.NEGATIVE_INFINITY } ?: return emptyList()
        return candidatesFor(pieces, limit, prev).map { it.copy(letters = lettersFor(input, pieces, it.pieces)) }
    }

    /** The letters of [input] (apostrophes included) that the first [count] of [pieces] were read from. */
    private fun lettersFor(input: String, pieces: List<String>, count: Int): Int {
        var i = 0
        for (p in 0 until count) {
            while (i < input.length && input[i] == '\'') i++
            i += pieces[p].length
        }
        while (i < input.length && input[i] == '\'') i++
        return minOf(i, input.length)
    }

    /** The same for pieces already read (Shuangpin gives whole syllables). */
    fun candidatesFor(pieces: List<String>, limit: Int = 60, prev: String? = null): List<Candidate> {
        if (pieces.isEmpty()) return emptyList()
        val out = LinkedHashMap<String, Candidate>()
        sentence(pieces, prev)?.let { out[it] = Candidate(it, pieces.size) }
        for (len in minOf(maxLen, pieces.size) downTo 1) {
            for (e in words(pieces, 0, len, if (len == 1) limit else 10, prev)) if (e.word !in out) out[e.word] = Candidate(e.word, len)
            if (out.size >= limit) break
        }
        return out.values.take(limit)
    }

    /** The letters of the first [count] pieces of [input], apostrophes included, so the rest can be kept. */
    fun consumed(input: String, count: Int): Int {
        var left = count
        var i = 0
        val pieces = split(input)
        var p = 0
        while (i < input.length && left > 0) {
            if (input[i] == '\'') { i++; continue }
            i += pieces[p].length; p++; left--
        }
        while (i < input.length && input[i] == '\'') i++
        return i
    }

    /**
     * What usually comes after [prev]: the words written after it before, then the dictionary's words that go on from it (中 →
     * 国, 心, 文…), each as the characters still to type.
     */
    fun follow(prev: String, limit: Int = 30): List<String> {
        if (prev.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        learned.entries.filter { it.key.startsWith("$prev→") }.sortedByDescending { it.value }.forEach { out += it.key.substringAfter('→') }
        fun from(stem: String) {
            for (e in byFirst[stem[0]].orEmpty()) {
                if (out.size >= limit) return
                if (e.word.length > stem.length && e.word.startsWith(stem)) out += e.word.substring(stem.length)
            }
        }
        from(prev)
        if (prev.length > 1 && out.size < limit / 2) from(prev.takeLast(1))
        return out.take(limit)
    }

    fun learn(word: String) { learned[word] = (learned[word] ?: 0) + 1 }

    /** [next] was written right after [prev]. */
    fun learnPair(prev: String, next: String) { if (prev.isNotEmpty() && next.isNotEmpty()) learned["$prev→$next"] = (learned["$prev→$next"] ?: 0) + 1 }

    companion object {
        private const val WORD_COST = 0.3
        private val INTERJECTIONS = setOf("n", "m", "ng", "hm", "hng")

        /** Reads the word list built by tools/build_dictionaries.py: syllables joined by ', the word, (ln p + 30) * 2000. */
        fun read(reader: BufferedReader): PinyinEngine {
            val entries = ArrayList<Entry>(240000)
            // About 400 syllables are shared by 230,000 words: one copy of each.
            val pool = HashMap<String, String>()
            reader.forEachLine { line ->
                val a = line.indexOf('\t'); val b = line.indexOf('\t', a + 1)
                if (a <= 0 || b <= a) return@forEachLine
                val syl = line.substring(0, a).split('\'').map { pool.getOrPut(it) { it } }.toTypedArray()
                val word = line.substring(a + 1, b)
                val f = line.substring(b + 1).toIntOrNull() ?: return@forEachLine
                if (syl.size == word.length) entries += Entry(word, syl, (f / 2000.0 - 30.0).toFloat())
            }
            return PinyinEngine(entries)
        }
    }
}
