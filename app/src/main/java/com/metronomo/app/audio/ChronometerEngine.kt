package com.metronomo.app.audio

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import com.metronomo.app.service.TimerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil

/**
 * Stopwatch / countdown timer with audible signals every N minutes.
 *
 * Elapsed time is always derived from SystemClock.elapsedRealtime(), so delays
 * in the UI ticker (or a sleeping screen) can never make the clock drift.
 * A foreground service keeps the process alive and the CPU awake while running.
 */
class ChronometerEngine(context: Context) {

    enum class Mode { STOPWATCH, TIMER }

    data class State(
        val running: Boolean = false,
        val mode: Mode = Mode.STOPWATCH,
        val elapsedMs: Long = 0L,
        val targetMs: Long = 10 * 60_000L,
        val intervalMinutes: Int = 5,
        val soundEnabled: Boolean = true,
        val vibrationEnabled: Boolean = true,
        val signals: Int = 0,
        val finished: Boolean = false,
        val pulse: Long = 0L,
    ) {
        val remainingMs: Long get() = (targetMs - elapsedMs).coerceAtLeast(0L)

        /** Milliseconds until the next interval signal, or null when disabled. */
        val nextSignalInMs: Long?
            get() {
                if (intervalMinutes <= 0) return null
                val interval = intervalMinutes * 60_000L
                val next = (signals + 1) * interval
                val limit = if (mode == Mode.TIMER) targetMs else Long.MAX_VALUE
                if (next > limit) return null
                return (next - elapsedMs).coerceAtLeast(0L)
            }
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("chronometer", Context.MODE_PRIVATE)
    private val chime = ChimePlayer(appContext)
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (appContext.getSystemService(VibratorManager::class.java))?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null
    private var startedAt: Long = 0L
    private var accumulatedMs: Long = 0L

    fun start() {
        val current = _state.value
        if (current.running) return

        val restartClock = current.mode == Mode.TIMER &&
            (current.elapsedMs >= current.targetMs || current.finished)
        if (restartClock) {
            accumulatedMs = 0L
            _state.update { it.copy(elapsedMs = 0L, finished = false, signals = 0) }
        } else {
            accumulatedMs = current.elapsedMs
        }

        startedAt = SystemClock.elapsedRealtime()
        _state.update { it.copy(running = true, finished = false) }
        startTicker()
        ensureService()
    }

    fun pause() {
        if (!_state.value.running) return
        accumulatedMs = elapsed()
        startedAt = 0L
        ticker?.cancel()
        ticker = null
        _state.update { it.copy(running = false, elapsedMs = accumulatedMs) }
    }

    fun toggle() {
        if (_state.value.running) pause() else start()
    }

    fun reset() {
        ticker?.cancel()
        ticker = null
        accumulatedMs = 0L
        startedAt = 0L
        _state.update {
            it.copy(running = false, elapsedMs = 0L, signals = 0, finished = false)
        }
    }

    fun setMode(mode: Mode) {
        if (mode == _state.value.mode) return
        reset()
        _state.update { it.copy(mode = mode) }
    }

    fun setInterval(minutes: Int) {
        val clamped = minutes.coerceIn(0, 180)
        if (clamped == _state.value.intervalMinutes) return
        _state.update {
            val emitted = if (clamped > 0) {
                (it.elapsedMs / (clamped * 60_000L)).toInt()
            } else {
                0
            }
            it.copy(
                intervalMinutes = clamped,
                signals = emitted,
            )
        }
        prefs.edit().putInt(KEY_INTERVAL, clamped).apply()
    }

    fun setTarget(minutes: Int) {
        if (_state.value.running) return
        val clamped = minutes.coerceIn(1, 600)
        _state.update {
            it.copy(targetMs = clamped * 60_000L, elapsedMs = 0L, finished = false, signals = 0)
        }
        accumulatedMs = 0L
        prefs.edit().putInt(KEY_TARGET, clamped).apply()
    }

    fun setSoundEnabled(enabled: Boolean) {
        _state.update { it.copy(soundEnabled = enabled) }
        prefs.edit().putBoolean(KEY_SOUND, enabled).apply()
    }

    fun setVibrationEnabled(enabled: Boolean) {
        _state.update { it.copy(vibrationEnabled = enabled) }
        prefs.edit().putBoolean(KEY_VIBRATION, enabled).apply()
    }

    // ---------------------------------------------------------------- ticker --

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                tick()
                delay(TICK_MS)
            }
        }
    }

    private fun tick() {
        val current = _state.value
        if (!current.running) return

        val elapsed = elapsed()
        var signals = current.signals
        var pulse = current.pulse
        var finished = false
        var running = true

        val intervalMs = current.intervalMinutes * 60_000L
        val deadline = if (current.mode == Mode.TIMER) current.targetMs else Long.MAX_VALUE

        if (intervalMs > 0) {
            // Catch up on every signal that became due, even if ticks were delayed.
            while ((signals + 1) * intervalMs <= elapsed && (signals + 1) * intervalMs <= deadline) {
                signals++
                pulse++
                if (current.soundEnabled) chime.signal()
                if (current.vibrationEnabled) vibrate(SIGNAL_VIBRATION)
            }
        }

        if (current.mode == Mode.TIMER && elapsed >= current.targetMs) {
            finished = true
            running = false
            pulse++
            if (current.soundEnabled) chime.finish()
            if (current.vibrationEnabled) vibrate(FINISH_VIBRATION)
            accumulatedMs = current.targetMs
            startedAt = 0L
            ticker?.cancel()
            ticker = null
        }

        _state.update {
            it.copy(
                elapsedMs = if (finished) current.targetMs else elapsed,
                signals = signals,
                pulse = pulse,
                finished = finished,
                running = running,
            )
        }
    }

    private fun elapsed(): Long {
        val live = if (startedAt != 0L) SystemClock.elapsedRealtime() - startedAt else 0L
        return accumulatedMs + live
    }

    private fun vibrate(pattern: LongArray) {
        val device = vibrator ?: return
        if (!device.hasVibrator()) return
        try {
            device.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (_: Throwable) {
        }
    }

    private fun ensureService() {
        val intent = Intent(appContext, TimerService::class.java)
        try {
            ContextCompat.startForegroundService(appContext, intent)
        } catch (_: Throwable) {
        }
    }

    fun stopService() {
        try {
            appContext.stopService(Intent(appContext, TimerService::class.java))
        } catch (_: Throwable) {
        }
    }

    // ---------------------------------------------------------------- prefs --

    private fun loadState(): State {
        return State(
            intervalMinutes = prefs.getInt(KEY_INTERVAL, 5).coerceIn(0, 180),
            targetMs = prefs.getInt(KEY_TARGET, 10).coerceIn(1, 600) * 60_000L,
            soundEnabled = prefs.getBoolean(KEY_SOUND, true),
            vibrationEnabled = prefs.getBoolean(KEY_VIBRATION, true),
        )
    }

    companion object {
        private const val TICK_MS = 33L
        private const val KEY_INTERVAL = "interval"
        private const val KEY_TARGET = "target"
        private const val KEY_SOUND = "sound"
        private const val KEY_VIBRATION = "vibration"

        private val SIGNAL_VIBRATION = longArrayOf(0, 80, 70, 120)
        private val FINISH_VIBRATION = longArrayOf(0, 160, 90, 160, 90, 300)

        fun formatClock(ms: Long): String {
            val totalSeconds = ms / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                "%d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%02d:%02d".format(minutes, seconds)
            }
        }

        fun formatStopwatch(ms: Long): String {
            val totalSeconds = ms / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val centis = (ms % 1000) / 10
            return "%02d:%02d,%02d".format(minutes, seconds, centis)
        }

        fun formatShort(ms: Long): String {
            val totalSeconds = ceil(ms / 1000.0).toLong()
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return if (minutes > 0) "%d:%02d".format(minutes, seconds) else "%d s".format(seconds)
        }
    }
}
