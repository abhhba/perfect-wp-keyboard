package io.github.abhhbasg.pivot.engine

import android.content.Context
import java.io.File
import java.util.concurrent.Executors

/**
 * The two word lists, loaded once in the background and shared by the keyboard, and what the user taught them, kept in a small
 * file on the phone (never sent anywhere).
 */
object Engines {
    @Volatile var pinyin: PinyinEngine? = null; private set
    @Volatile var english: EnglishEngine? = null; private set
    @Volatile var shuangpin: Shuangpin.Decoder? = null; private set
    private val worker = Executors.newSingleThreadExecutor()
    private var started = false

    fun load(context: Context, then: () -> Unit) {
        val app = context.applicationContext
        synchronized(this) { if (started) { if (pinyin != null && english != null) then(); return }; started = true }
        worker.execute {
            val zh = app.assets.open("zh.tsv").bufferedReader().use { PinyinEngine.read(it) }
            readLearned(File(app.filesDir, "learned_zh.tsv"), zh.learned)
            shuangpin = Shuangpin.Decoder(zh.syllables)
            pinyin = zh
            val en = app.assets.open("en.tsv").bufferedReader().use { EnglishEngine.read(it) }
            readLearned(File(app.filesDir, "learned_en.tsv"), en.learned)
            english = en
            android.os.Handler(android.os.Looper.getMainLooper()).post(then)
        }
    }

    private fun readLearned(f: File, into: MutableMap<String, Int>) {
        if (!f.exists()) return
        f.forEachLine { line -> val t = line.lastIndexOf('\t'); if (t > 0) line.substring(t + 1).toIntOrNull()?.let { into[line.substring(0, t)] = it } }
    }

    private fun writeLearned(f: File, from: Map<String, Int>) {
        val text = from.entries.joinToString("\n") { "${it.key}\t${it.value}" }
        val tmp = File(f.path + ".tmp")
        tmp.writeText(text)
        tmp.renameTo(f)
    }

    /** Saves what was learned, off the main thread. */
    fun save(context: Context) {
        val dir = context.applicationContext.filesDir
        val zh = pinyin?.learned?.toMap(); val en = english?.learned?.toMap()
        worker.execute {
            zh?.let { writeLearned(File(dir, "learned_zh.tsv"), it) }
            en?.let { writeLearned(File(dir, "learned_en.tsv"), it) }
        }
    }

    fun forget(context: Context) {
        pinyin?.learned?.clear(); english?.learned?.clear()
        File(context.filesDir, "learned_zh.tsv").delete(); File(context.filesDir, "learned_en.tsv").delete()
    }
}
