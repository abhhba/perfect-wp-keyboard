package io.github.abhhbasg.pivot

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Everyday sentences typed in one go: how many come out right as the first choice. Writes the misses to build/accuracy.txt. */
class AccuracyTest {
    private val cases = listOf(
        "nihaoma" to "你好吗", "jintiantianqihenhao" to "今天天气很好", "woaini" to "我爱你", "womenyiqiquchifan" to "我们一起去吃饭",
        "zhegewentihenzhongyao" to "这个问题很重要", "xiexieni" to "谢谢你", "mingtianjian" to "明天见", "ninhao" to "您好",
        "woxianghuijia" to "我想回家", "zhongguorenmin" to "中国人民", "beijingdaxue" to "北京大学", "diannao" to "电脑",
        "shoujihaoma" to "手机号码", "jintianxiawu" to "今天下午", "nizaiganshenme" to "你在干什么", "wobuzhidao" to "我不知道",
        "zhegedifanghenpiaoliang" to "这个地方很漂亮", "tamenzaixuexi" to "他们在学习", "xianzaijidianle" to "现在几点了",
        "wojintianhengaoxing" to "我今天很高兴", "shijiezhenda" to "世界真大", "womenkaihui" to "我们开会", "gongzuohenmang" to "工作很忙",
        "dianhuahaoma" to "电话号码", "yinweitaibusheng" to "因为太不省", "suoyiwomen" to "所以我们", "ruguonixihuan" to "如果你喜欢",
        "keyibangwoma" to "可以帮我吗", "meiyouwenti" to "没有问题", "xiayuleqingdaisan" to "下雨了请带伞",
    )

    @Test fun sentences() {
        val zh = EnginesTest.zh
        val misses = cases.filter { (py, want) -> zh.candidates(py).firstOrNull()?.text != want }
            .map { (py, want) -> "$py: wanted $want, got ${zh.candidates(py).take(3).map { it.text }}" }
        val hit = cases.size - misses.size
        File("build/accuracy.txt").writeText("$hit / ${cases.size}\n" + misses.joinToString("\n"))
        assertTrue("$hit of ${cases.size}", hit >= cases.size * 0.8)
    }
}
