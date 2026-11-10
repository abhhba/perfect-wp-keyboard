package io.github.abhhbasg.pivot.engine

import java.io.BufferedReader
import kotlin.math.ln

/** A word of the dictionary: its characters, the syllable of each, and how common it is (log scale, larger is more common). */
class Entry(val word: String, val syllables: List<String>, val score: Double)

/** A choice for the bar: the characters, and how many of the typed pieces of Pinyin they stand for. */
data class Candidate(val text: String, val pieces: Int)

/**
 * Typing Chinese with Pinyin. The letters typed are cut into pieces, each a whole syllable ("ni", "hao") or the start of one
 * ("n", "zh"); a word matches when each of its syllables starts with the piece in its place, so "nihao", "nih" and "nh" all
 * find 你好. The first choice is the whole input read as the likeliest sentence (the product of the words' frequencies); then
 * come the words for the first pieces, longest first.
 */
class PinyinEngine(entries: List<Entry>) {
    /** Words by the first letters of their syllables ("nh" for 你好). */
    private val byInitials = HashMap<String, MutableList<Entry>>()
    private val syllables = HashSet<String>()
    private val syllablePrefixes = HashSet<String>()
    private val maxLen: Int
    /** The logarithm of all the frequencies together: a word's score less this is the logarithm of its probability. */
    private val lnTotal: Double
    /** What the user picked, and how often: it raises those words. */
    val learned = HashMap<String, Int>()

    init {
        var longest = 1
        for (e in entries) {
            byInitials.getOrPut(e.syllables.joinToString("") { it.take(1) }) { ArrayList() }.add(e)
            for (s in e.syllables) syllables += s
            longest = maxOf(longest, e.syllables.size)
        }
        for (s in syllables) for (i in 1..s.length) syllablePrefixes += s.substring(0, i)
        for (list in byInitials.values) list.sortByDescending { it.score }
        maxLen = longest
        lnTotal = ln(entries.sumOf { kotlin.math.exp(it.score) })
    }

    fun isSyllable(s: String) = s in syllables

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

    private fun matches(e: Entry, pieces: List<String>, from: Int): Boolean {
        for (k in e.syllables.indices) {
            val p = pieces[from + k]
            val syl = e.syllables[k]
            if (whole(p)) { if (syl != p) return false } else if (!syl.startsWith(p)) return false
        }
        return true
    }

    private fun boost(e: Entry) = learned[e.word]?.let { 4.0 * ln(1.0 + it) } ?: 0.0

    /** The words that read pieces [from] .. [from]+[count]-1, best first. */
    fun words(pieces: List<String>, from: Int, count: Int, limit: Int): List<Entry> {
        val key = pieces.subList(from, from + count).joinToString("") { it.take(1) }
        val list = byInitials[key] ?: return emptyList()
        val out = ArrayList<Entry>()
        // Enough of the commonest to let a learned word overtake them, then sorted with what was learned.
        for (e in list) if (matches(e, pieces, from)) { out += e; if (out.size >= maxOf(limit * 3, 300)) break }
        return out.sortedByDescending { it.score + boost(it) }.take(limit)
    }

    /** The likeliest sentence for all the pieces, or null when there is none. */
    fun sentence(pieces: List<String>): String? {
        val n = pieces.size
        if (n == 0) return null
        val best = DoubleArray(n + 1) { Double.NEGATIVE_INFINITY }
        val back = arrayOfNulls<Entry>(n + 1)
        best[0] = 0.0
        for (i in 0 until n) {
            if (best[i] == Double.NEGATIVE_INFINITY) continue
            for (len in 1..minOf(maxLen, n - i)) {
                val w = words(pieces, i, len, 1).firstOrNull() ?: continue
                // The probabilities of the words multiplied: the reading in fewer, commoner words wins.
                val v = best[i] + w.score - lnTotal + boost(w) - WORD_COST
                if (v > best[i + len]) { best[i + len] = v; back[i + len] = w }
            }
        }
        if (back[n] == null) return null
        val words = ArrayList<String>()
        var i = n
        while (i > 0) { val w = back[i]!!; words.add(0, w.word); i -= w.syllables.size }
        return words.joinToString("")
    }

    /** What the bar offers for [input]: the sentence, then the words of the first pieces, longest first. */
    fun candidates(input: String, limit: Int = 60): List<Candidate> {
        val pieces = split(input)
        if (pieces.isEmpty()) return emptyList()
        val out = LinkedHashMap<String, Candidate>()
        sentence(pieces)?.let { out[it] = Candidate(it, pieces.size) }
        for (len in minOf(maxLen, pieces.size) downTo 1) {
            for (e in words(pieces, 0, len, if (len == 1) limit else 8)) if (e.word !in out) out[e.word] = Candidate(e.word, len)
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

    fun learn(word: String) { learned[word] = (learned[word] ?: 0) + 1 }

    companion object {
        private const val WORD_COST = 0.5
        private val INTERJECTIONS = setOf("n", "m", "ng", "hm", "hng")

        /** Reads the word list built by tools/build_dictionaries.py: syllables joined by ', the word, the frequency. */
        fun read(reader: BufferedReader): PinyinEngine {
            val entries = ArrayList<Entry>(70000)
            reader.forEachLine { line ->
                val a = line.indexOf('\t'); val b = line.indexOf('\t', a + 1)
                if (a <= 0 || b <= a) return@forEachLine
                val syl = line.substring(0, a).split('\'')
                val word = line.substring(a + 1, b)
                val f = line.substring(b + 1).toIntOrNull() ?: return@forEachLine
                if (syl.size == word.length) entries += Entry(word, syl, f / 4000.0)
            }
            return PinyinEngine(entries)
        }
    }
}
