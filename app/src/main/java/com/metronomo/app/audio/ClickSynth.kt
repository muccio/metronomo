package com.metronomo.app.audio

import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Procedural click synthesis. Every click is a short sine burst with a fast
 * exponential decay, so the metronome needs no audio assets at all.
 */
object ClickSynth {

    const val SAMPLE_RATE = 48_000

    /**
     * @param freq      fundamental frequency in Hz
     * @param durationMs total click length
     * @param decayMs   time constant of the exponential decay
     * @param gain      0..1 peak amplitude
     */
    fun click(freq: Double, durationMs: Double, decayMs: Double, gain: Double): ShortArray {
        val count = (SAMPLE_RATE * durationMs / 1000.0).toInt().coerceAtLeast(1)
        val out = ShortArray(count)
        val tau = decayMs / 1000.0
        val attack = 0.0012
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = exp(-t / tau) * min(1.0, t / attack)
            val signal = sin(2.0 * Math.PI * freq * t) * 0.78 +
                sin(2.0 * Math.PI * freq * 2.01 * t) * 0.22
            out[i] = ((signal * envelope * gain) * Short.MAX_VALUE)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return out
    }

    /** Rich bell used by the chronometer signals. */
    fun bell(freqs: DoubleArray, durationMs: Double, decayMs: Double, gain: Double): ShortArray {
        val count = (SAMPLE_RATE * durationMs / 1000.0).toInt().coerceAtLeast(1)
        val out = ShortArray(count)
        val tau = decayMs / 1000.0
        val attack = 0.004
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = exp(-t / tau) * min(1.0, t / attack) * gain
            var signal = 0.0
            freqs.forEachIndexed { index, f ->
                val weight = 1.0 / (index + 1)
                signal += sin(2.0 * Math.PI * f * t) * weight
            }
            signal /= freqs.size * 0.65
            out[i] = (signal * envelope * Short.MAX_VALUE)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return out
    }
}
