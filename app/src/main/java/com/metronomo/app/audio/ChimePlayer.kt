package com.metronomo.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Plays the chronometer chimes. The WAV files are generated once into the cache
 * directory, so the app ships without binary audio assets.
 */
class ChimePlayer(context: Context) {

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val signalId: Int
    private val finishId: Int

    init {
        val signal = ClickSynth.bell(doubleArrayOf(880.0, 1320.0, 1760.0), 900.0, 260.0, 0.75)
        val finish = ClickSynth.bell(doubleArrayOf(660.0, 990.0, 1320.0), 1500.0, 420.0, 0.8)
        signalId = soundPool.load(writeWav(context, "signal.wav", signal), 1)
        finishId = soundPool.load(writeWav(context, "finish.wav", finish), 1)
    }

    fun signal() {
        soundPool.play(signalId, 1f, 1f, 1, 0, 1f)
    }

    fun finish() {
        soundPool.play(finishId, 1f, 1f, 1, 0, 1f)
    }

    fun release() {
        soundPool.release()
    }

    private fun writeWav(context: Context, name: String, samples: ShortArray): String {
        val file = File(context.cacheDir, name)
        val dataSize = samples.size * 2
        DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { out ->
            out.writeBytes("RIFF")
            out.writeIntLe(36 + dataSize)
            out.writeBytes("WAVE")
            out.writeBytes("fmt ")
            out.writeIntLe(16)
            out.writeShortLe(1) // PCM
            out.writeShortLe(1) // mono
            out.writeIntLe(ClickSynth.SAMPLE_RATE)
            out.writeIntLe(ClickSynth.SAMPLE_RATE * 2)
            out.writeShortLe(2)
            out.writeShortLe(16)
            out.writeBytes("data")
            out.writeIntLe(dataSize)
            samples.forEach { out.writeShortLe(it.toInt()) }
        }
        return file.absolutePath
    }

    private fun DataOutputStream.writeIntLe(value: Int) {
        write(byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte(),
        ))
    }

    private fun DataOutputStream.writeShortLe(value: Int) {
        write(byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
        ))
    }
}
