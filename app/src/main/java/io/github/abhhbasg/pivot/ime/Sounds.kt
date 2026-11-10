package io.github.abhhbasg.pivot.ime

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * The clicks of the keys, made here rather than recorded: a short bright tick for a key and a lower, duller tap for backspace,
 * played again at every repeat so that holding backspace goes tap-tap-tap. Quick enough to keep up with fast typing.
 */
class Sounds(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val key: Int
    private val delete: Int
    private val space: Int

    init {
        val dir = context.cacheDir
        key = pool.load(write(File(dir, "key.wav"), 2600.0, 0.022, 0.55), 1)
        delete = pool.load(write(File(dir, "delete.wav"), 1300.0, 0.030, 0.6), 1)
        space = pool.load(write(File(dir, "space.wav"), 1800.0, 0.028, 0.5), 1)
    }

    fun key() = pool.play(key, VOLUME, VOLUME, 1, 0, 1f)
    fun delete() = pool.play(delete, VOLUME, VOLUME, 1, 0, 1f)
    fun space() = pool.play(space, VOLUME, VOLUME, 1, 0, 1f)
    fun release() = pool.release()

    /** A click: a tone that dies away in a few milliseconds over a breath of noise, as 16-bit mono PCM in a WAV file. */
    private fun write(f: File, hz: Double, seconds: Double, noise: Double): String {
        val rate = 44100
        val n = (rate * seconds).toInt()
        val rnd = java.util.Random(7)
        val pcm = ShortArray(n) { i ->
            val t = i / rate.toDouble()
            val env = exp(-t * 260.0)
            val v = (sin(2 * PI * hz * t) * (1 - noise) + (rnd.nextDouble() * 2 - 1) * noise) * env
            (v * 0.7 * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        RandomAccessFile(f, "rw").use { out ->
            out.setLength(0)
            fun le32(v: Int) { out.write(v and 0xFF); out.write(v shr 8 and 0xFF); out.write(v shr 16 and 0xFF); out.write(v shr 24 and 0xFF) }
            fun le16(v: Int) { out.write(v and 0xFF); out.write(v shr 8 and 0xFF) }
            out.writeBytes("RIFF"); le32(36 + n * 2); out.writeBytes("WAVE")
            out.writeBytes("fmt "); le32(16); le16(1); le16(1); le32(rate); le32(rate * 2); le16(2); le16(16)
            out.writeBytes("data"); le32(n * 2)
            val bytes = ByteArray(n * 2)
            for (i in 0 until n) { bytes[i * 2] = (pcm[i].toInt() and 0xFF).toByte(); bytes[i * 2 + 1] = (pcm[i].toInt() shr 8 and 0xFF).toByte() }
            out.write(bytes)
        }
        return f.path
    }

    private companion object { const val VOLUME = 0.6f }
}
