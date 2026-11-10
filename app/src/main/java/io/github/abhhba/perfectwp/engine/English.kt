package io.github.abhhba.perfectwp.engine

import java.io.BufferedReader

/** What the bar offers for an English word being typed, and the fix that space would make (null for none). */
class Suggestions(val words: List<String>, val correction: String?)

/**
 * English words: finishing the word being typed and fixing a slip. A word that is not in the list is fixed when one change
 * (a letter missing, extra, wrong or two swapped, or a missing apostrophe) makes a common word of it.
 */
class EnglishEngine(words: List<Pair<String, Int>>) {
    /**
     * Lower-case forms, sorted, and the frequency of each, in plain arrays: a keyboard lives in a small heap, and maps of 160,000
     * words would fill it. Only the spellings that are not all lower case ("I", "London") are kept apart.
     */
    private val sorted: Array<String>
    private val freqs: IntArray
    private val spelling = HashMap<String, String>()
    val learned = HashMap<String, Int>()

    init {
        val best = HashMap<String, Int>(words.size * 2)
        for ((w, f) in words) {
            val k = w.lowercase()
            if ((best[k] ?: -1) < f) { best[k] = f; if (k != w) spelling[k] = w else spelling.remove(k) }
        }
        sorted = best.keys.toTypedArray().also { it.sort() }
        freqs = IntArray(sorted.size) { best[sorted[it]]!! }
    }

    private fun freqOf(k: String): Int { val i = sorted.binarySearch(k); return if (i >= 0) freqs[i] else 0 }
    private fun inList(k: String) = sorted.binarySearch(k) >= 0

    fun knows(word: String) = inList(word.lowercase()) || word.lowercase() in learned

    private fun weight(k: String) = freqOf(k) + (learned[k] ?: 0) * 20

    /** Words that start with [prefix], commonest first. */
    fun completions(prefix: String, limit: Int): List<String> {
        val p = prefix.lowercase()
        var lo = sorted.binarySearch(p).let { if (it < 0) -it - 1 else it }
        val found = ArrayList<String>()
        while (lo < sorted.size && sorted[lo].startsWith(p)) { found += sorted[lo]; lo++ }
        for (k in learned.keys) if (k.startsWith(p) && k !in found) found += k
        return found.sortedByDescending { weight(it) }.take(limit)
    }

    /** Keys next to each other on the letters, where a finger slips. */
    private val near: Map<Char, String> = run {
        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        val m = HashMap<Char, String>()
        rows.forEachIndexed { r, row ->
            row.forEachIndexed { i, ch ->
                val sb = StringBuilder()
                for (dr in -1..1) {
                    val other = rows.getOrNull(r + dr) ?: continue
                    // The second row is set in by half a key: its neighbours above and below are the key at i and i+1.
                    val offset = when { dr == 0 -> 0; r == 1 && dr == -1 -> 0; r == 1 && dr == 1 -> -1; r == 0 -> 0; else -> 1 }
                    for (d in -1..1) other.getOrNull(i + offset + d)?.let { if (it != ch) sb.append(it) }
                }
                m[ch] = sb.toString()
            }
        }
        m
    }

    /** Every word one change from [w], with what that change costs: a slip to the next key or a doubled letter costs little. */
    private fun edits(w: String): Map<String, Double> {
        val out = HashMap<String, Double>()
        fun add(s: String, cost: Double) { if ((out[s] ?: Double.MAX_VALUE) > cost) out[s] = cost }
        val letters = "abcdefghijklmnopqrstuvwxyz'"
        for (i in 0..w.length) {
            if (i < w.length) add(w.removeRange(i, i + 1), if (i > 0 && w[i] == w[i - 1]) 0.5 else 1.0)
            if (i < w.length - 1) add(w.substring(0, i) + w[i + 1] + w[i] + w.substring(i + 2), 0.7)
            for (c in letters) {
                if (i < w.length && c != w[i]) add(w.substring(0, i) + c + w.substring(i + 1), if (near[w[i]]?.contains(c) == true) 0.8 else 1.8)
                val doubled = (i > 0 && w[i - 1] == c) || (i < w.length && w[i] == c)
                add(w.substring(0, i) + c + w.substring(i), when { c == '\'' -> 0.4; doubled -> 0.5; else -> 1.0 })
            }
        }
        return out
    }

    /** The likeliest word one change away from [word], if it is common enough to be meant. */
    fun correction(word: String): String? {
        val k = word.lowercase()
        if (k.length < 2 || knows(k)) return null
        return edits(k).filter { (inList(it.key) || it.key in learned) && weight(it.key) >= 80 }
            .maxByOrNull { weight(it.key) - it.value * 40 }?.key
    }

    /** Puts [w] in the case the user typed it in: "Hello" stays capitalised, "HELLO" stays in capitals, "i" is always "I". */
    fun cased(w: String, typed: String): String {
        val proper = spelling[w] ?: w
        return when {
            typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> proper.uppercase()
            typed.firstOrNull()?.isUpperCase() == true -> proper.replaceFirstChar { it.uppercase() }
            else -> proper
        }
    }

    fun suggest(typed: String): Suggestions {
        if (typed.isEmpty()) return Suggestions(emptyList(), null)
        val fix = correction(typed)
        val list = LinkedHashSet<String>()
        if (fix != null) list += cased(fix, typed)
        for (w in completions(typed, 6)) list += cased(w, typed)
        if (fix != null || list.isEmpty()) list.add(typed)
        return Suggestions(list.toList(), fix?.let { cased(it, typed) })
    }

    fun learn(word: String) { val k = word.lowercase(); learned[k] = (learned[k] ?: 0) + 1 }

    companion object {
        fun read(reader: BufferedReader): EnglishEngine {
            val words = ArrayList<Pair<String, Int>>(170000)
            reader.forEachLine { line ->
                val t = line.indexOf('\t')
                if (t > 0) line.substring(t + 1).toIntOrNull()?.let { words += line.substring(0, t) to it }
            }
            return EnglishEngine(words)
        }
    }
}
