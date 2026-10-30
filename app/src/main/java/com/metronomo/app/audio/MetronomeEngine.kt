package com.metronomo.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Arrays
import kotlin.math.roundToLong

/**
 * Sample-accurate metronome built on a streaming AudioTrack.
 *
 * Instead of scheduling clicks with timers (which jitter), the audio thread
 * renders the click track block by block. Every beat is placed at an exact
 * frame position, so the tempo never drifts, even after hours.
 */
class MetronomeEngine(context: Context) {

    data class State(
        val running: Boolean = false,
        val bpm: Int = 100,
        val beatsPerBar: Int = 4,
        val beatUnit: Int = 4,
        val subdivision: Int = 1,
        val accents: List<Int> = listOf(ACCENT_STRONG, ACCENT_NORMAL, ACCENT_NORMAL, ACCENT_NORMAL),
        val soundEnabled: Boolean = true,
        val currentBeat: Int = -1,
        val pulse: Long = 0L,
    ) {
        val tempoName: String
            get() = when {
                bpm < 40 -> "Largo"
                bpm < 60 -> "Larghetto"
                bpm < 66 -> "Adagio"
                bpm < 76 -> "Andante"
                bpm < 108 -> "Moderato"
                bpm < 120 -> "Allegretto"
                bpm < 156 -> "Allegro"
                bpm < 176 -> "Vivace"
                bpm < 200 -> "Presto"
                else -> "Prestissimo"
            }
    }

    private val prefs = context.applicationContext
        .getSharedPreferences("metronome", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile private var running = false
    @Volatile private var bpm: Int = _state.value.bpm
    @Volatile private var subdivision: Int = _state.value.subdivision
    @Volatile private var soundEnabled: Boolean = _state.value.soundEnabled
    @Volatile private var beatsPerBar: Int = _state.value.beatsPerBar
    @Volatile private var accents: IntArray = _state.value.accents.toIntArray()

    private var thread: Thread? = null

    fun toggle() {
        if (running) stop() else start()
    }

    fun start() {
        if (running) return
        running = true
        _state.update { it.copy(running = true, currentBeat = -1) }
        thread = Thread({ audioLoop() }, "metronome-audio").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        if (!running) return
        running = false
        thread?.join(200)
        thread = null
        _state.update { it.copy(running = false, currentBeat = -1) }
    }

    fun setBpm(value: Int) {
        val clamped = value.coerceIn(MIN_BPM, MAX_BPM)
        if (clamped == _state.value.bpm) return
        bpm = clamped
        _state.update { it.copy(bpm = clamped) }
        prefs.edit().putInt(KEY_BPM, clamped).apply()
    }

    fun setBeatsPerBar(count: Int) {
        val clamped = count.coerceIn(1, MAX_BEATS)
        if (clamped == _state.value.beatsPerBar) return
        val resized = IntArray(clamped) { index ->
            when {
                index < accents.size -> accents[index]
                index == 0 -> ACCENT_STRONG
                else -> ACCENT_NORMAL
            }
        }
        accents = resized
        beatsPerBar = clamped
        _state.update {
            it.copy(
                beatsPerBar = clamped,
                accents = resized.toList(),
                currentBeat = -1,
            )
        }
        prefs.edit()
            .putInt(KEY_BEATS, clamped)
            .putString(KEY_ACCENTS, resized.joinToString(","))
            .apply()
    }

    fun setBeatUnit(unit: Int) {
        if (unit == _state.value.beatUnit) return
        _state.update { it.copy(beatUnit = unit, currentBeat = -1) }
        prefs.edit().putInt(KEY_UNIT, unit).apply()
    }

    fun setSubdivision(value: Int) {
        val clamped = value.coerceIn(1, MAX_SUBDIVISION)
        if (clamped == _state.value.subdivision) return
        subdivision = clamped
        _state.update { it.copy(subdivision = clamped) }
        prefs.edit().putInt(KEY_SUBDIVISION, clamped).apply()
    }

    fun setSoundEnabled(enabled: Boolean) {
        soundEnabled = enabled
        _state.update { it.copy(soundEnabled = enabled) }
        prefs.edit().putBoolean(KEY_SOUND, enabled).apply()
    }

    /** Cycles the accent of one beat: strong -> normal -> silent -> strong. */
    fun cycleAccent(index: Int) {
        val current = _state.value.accents
        if (index !in current.indices) return
        val next = current.toMutableList()
        next[index] = when (current[index]) {
            ACCENT_STRONG -> ACCENT_NORMAL
            ACCENT_NORMAL -> ACCENT_SILENT
            else -> ACCENT_STRONG
        }
        accents = next.toIntArray()
        _state.update { it.copy(accents = next) }
        prefs.edit().putString(KEY_ACCENTS, next.joinToString(",")).apply()
    }

    fun resetAccents() {
        val resized = IntArray(_state.value.beatsPerBar) { index ->
            if (index == 0) ACCENT_STRONG else ACCENT_NORMAL
        }
        accents = resized
        _state.update { it.copy(accents = resized.toList()) }
        prefs.edit().putString(KEY_ACCENTS, resized.joinToString(",")).apply()
    }

    // ---------------------------------------------------------------- audio --

    private fun audioLoop() {
        val frames = 512
        val buffer = ShortArray(frames)
        val minBytes = AudioTrack.getMinBufferSize(
            ClickSynth.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBytes <= 0) {
            fail()
            return
        }

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(ClickSynth.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(minBytes * 2)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        } catch (t: Throwable) {
            fail()
            return
        }

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            fail()
            return
        }

        val strong = ClickSynth.click(1568.0, 70.0, 22.0, 0.95)
        val normal = ClickSynth.click(1046.0, 50.0, 13.0, 0.65)
        val soft = ClickSynth.click(784.0, 32.0, 8.0, 0.32)

        val voices = ArrayList<Voice>(8)
        var blockStart = 0L
        var nextEvent = 0.0
        var beatIndex = 0
        var subIndex = 0

        try {
            track.play()
            while (running) {
                val blockEnd = blockStart + frames

                // Schedule every grid point that falls inside this block.
                while (nextEvent < blockEnd) {
                    val startFrame = nextEvent.roundToLong()
                    val currentAccents = accents
                    val level = currentAccents[
                        (beatIndex % beatsPerBar).coerceIn(0, currentAccents.size - 1)
                    ]

                    if (soundEnabled) {
                        val samples = when {
                            level == ACCENT_SILENT -> null
                            subIndex > 0 -> soft
                            level >= ACCENT_STRONG -> strong
                            else -> normal
                        }
                        if (samples != null) {
                            voices.add(Voice(startFrame, samples))
                        }
                    }

                    if (subIndex == 0) {
                        val visibleBeat = beatIndex % beatsPerBar
                        _state.update { s ->
                            s.copy(currentBeat = visibleBeat, pulse = s.pulse + 1)
                        }
                    }

                    val samplesPerBeat = ClickSynth.SAMPLE_RATE * 60.0 / bpm
                    subIndex++
                    if (subIndex >= subdivision) {
                        subIndex = 0
                        beatIndex++
                    }
                    nextEvent += samplesPerBeat / subdivision
                }

                Arrays.fill(buffer, 0)
                val iterator = voices.iterator()
                while (iterator.hasNext()) {
                    val voice = iterator.next()
                    val end = voice.start + voice.samples.size
                    if (end <= blockStart) {
                        iterator.remove()
                        continue
                    }
                    val from = maxOf(voice.start, blockStart)
                    val to = minOf(end, blockEnd)
                    var i = from
                    while (i < to) {
                        val dst = (i - blockStart).toInt()
                        val src = (i - voice.start).toInt()
                        val mixed = buffer[dst] + voice.samples[src]
                        buffer[dst] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                        i++
                    }
                }

                var written = 0
                while (written < frames && running) {
                    val n = track.write(buffer, written, frames - written)
                    if (n <= 0) break
                    written += n
                }

                blockStart = blockEnd
            }
        } catch (_: Throwable) {
            // Audio device lost (e.g. route change): stop gracefully.
        } finally {
            try {
                track.stop()
            } catch (_: Throwable) {
            }
            track.release()
            _state.update { it.copy(running = false, currentBeat = -1) }
        }
    }

    private fun fail() {
        running = false
        _state.update { it.copy(running = false, currentBeat = -1) }
    }

    private class Voice(val start: Long, val samples: ShortArray)

    // ---------------------------------------------------------------- prefs --

    private fun loadState(): State {
        val beats = prefs.getInt(KEY_BEATS, 4).coerceIn(1, MAX_BEATS)
        val storedAccents = prefs.getString(KEY_ACCENTS, null)
            ?.split(",")
            ?.mapNotNull { it.toIntOrNull() }
            ?.takeIf { it.size == beats }
        val accentsList = storedAccents ?: List(beats) { index ->
            if (index == 0) ACCENT_STRONG else ACCENT_NORMAL
        }
        return State(
            bpm = prefs.getInt(KEY_BPM, 100).coerceIn(MIN_BPM, MAX_BPM),
            beatsPerBar = beats,
            beatUnit = prefs.getInt(KEY_UNIT, 4),
            subdivision = prefs.getInt(KEY_SUBDIVISION, 1).coerceIn(1, MAX_SUBDIVISION),
            accents = accentsList,
            soundEnabled = prefs.getBoolean(KEY_SOUND, true),
        )
    }

    companion object {
        const val MIN_BPM = 30
        const val MAX_BPM = 300
        const val MAX_BEATS = 16
        const val MAX_SUBDIVISION = 6

        const val ACCENT_SILENT = 0
        const val ACCENT_NORMAL = 1
        const val ACCENT_STRONG = 2

        private const val KEY_BPM = "bpm"
        private const val KEY_BEATS = "beats"
        private const val KEY_UNIT = "unit"
        private const val KEY_SUBDIVISION = "subdivision"
        private const val KEY_ACCENTS = "accents"
        private const val KEY_SOUND = "sound"
    }
}
