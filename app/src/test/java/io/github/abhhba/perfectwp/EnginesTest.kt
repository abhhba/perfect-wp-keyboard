package io.github.abhhba.perfectwp

import io.github.abhhba.perfectwp.engine.EnglishEngine
import io.github.abhhba.perfectwp.engine.PinyinEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EnginesTest {
    companion object {
        val zh: PinyinEngine by lazy { File("src/main/assets/zh.tsv").bufferedReader().use { PinyinEngine.read(it) } }
        val en: EnglishEngine by lazy { File("src/main/assets/en.tsv").bufferedReader().use { EnglishEngine.read(it) } }
    }

    private fun first(input: String) = zh.candidates(input).first().text

    @Test fun cutsPinyin() {
        assertEquals(listOf("ni", "hao"), zh.split("nihao"))
        assertEquals(listOf("xi", "an"), zh.split("xi'an"))
        assertEquals(listOf("zhong", "guo"), zh.split("zhongguo"))
        assertEquals(listOf("n", "h"), zh.split("nh"))
        assertEquals(listOf("zhong", "g"), zh.split("zhongg"))
    }

    @Test fun readsWordsAndSentences() {
        assertEquals("你好", first("nihao"))
        assertEquals("中国", first("zhongguo"))
        assertEquals("我们", first("women"))
        assertEquals("中国", first("zhongg"))
        assertTrue(zh.candidates("nh").any { it.text == "你好" })
        assertEquals("西安", first("xi'an"))
        val sentence = first("womenshizhongguoren")
        assertEquals("我们是中国人", sentence)
    }

    @Test fun keepsTheRestOfThePinyin() {
        val c = zh.candidates("nihaoma").first { it.text == "你好" }
        assertEquals(2, c.pieces)
        assertEquals(5, zh.consumed("nihaoma", 2))
        assertEquals(3, zh.consumed("xi'an", 1))
    }

    @Test fun learnsWhatIsPicked() {
        val second = zh.candidates("shi").map { it.text }[3]
        repeat(5) { zh.learn(second) }
        assertEquals(second, zh.candidates("shi")[0].text)
    }

    @Test fun readsMicrosoftShuangpin() {
        val sp = io.github.abhhba.perfectwp.engine.Shuangpin.Decoder(zh.syllables)
        assertEquals(listOf("ni", "hao"), sp.decode("nihk"))
        assertEquals(listOf("zhong", "guo"), sp.decode("vsgo"))
        assertEquals(listOf("ying", "an"), sp.decode("y;oj"))
        assertEquals(listOf("shuang", "x"), sp.decode("udx"))
        assertEquals("你好", zh.candidatesFor(sp.decode("nihk")).first().text)
        assertEquals("我们", zh.candidatesFor(sp.decode("womf")).first().text)
    }

    @Test fun offersWhatComesNext() {
        assertTrue(zh.follow("中").contains("国"))
        zh.learnPair("你好", "世界")
        assertEquals("世界", zh.follow("你好").first())
    }

    @Test fun finishesAndFixesEnglish() {
        assertTrue(en.completions("hel", 8).contains("hello"))
        assertEquals("the", en.correction("teh"))
        assertEquals("don't", en.correction("dont"))
        assertEquals(null, en.correction("hello"))
        assertEquals("I", en.cased("i", "i"))
        assertEquals("Thank", en.suggest("Thnak").correction)
    }
}
