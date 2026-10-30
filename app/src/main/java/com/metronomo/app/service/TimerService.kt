package com.metronomo.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.metronomo.app.MainActivity
import com.metronomo.app.MetronomoApp
import com.metronomo.app.R
import com.metronomo.app.audio.ChronometerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

/**
 * Keeps the chronometer alive (and the CPU awake) while it runs, so the
 * interval signal still fires with the screen off or another app in front.
 */
class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var engine: ChronometerEngine
    private var wakeLock: PowerManager.WakeLock? = null
    private var notificationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        engine = (application as MetronomoApp).chronometer
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(engine.state.value),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        acquireWakeLock(engine.state.value.running)
        notificationJob = scope.launch {
            var lastText = ""
            var lastAction = ""
            while (isActive) {
                val state = engine.state.value
                val text = notificationText(state)
                val action = if (state.running) ACTION_PAUSE else ACTION_RESUME
                if (text != lastText || action != lastAction) {
                    lastText = text
                    lastAction = action
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.notify(NOTIFICATION_ID, buildNotification(state))
                }
                if (!state.running && !state.finished) {
                    stopSelf()
                    break
                }
                delay(500)
            }
        }
        scope.launch {
            // The countdown reached zero: close the session.
            engine.state.first { it.finished }
            delay(1200)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> engine.pause()
            ACTION_RESUME -> engine.start()
            ACTION_RESET -> engine.reset()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        notificationJob?.cancel()
        scope.cancel()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun notificationText(state: ChronometerEngine.State): String {
        val clock = if (state.mode == ChronometerEngine.Mode.TIMER) {
            ChronometerEngine.formatClock(state.remainingMs)
        } else {
            ChronometerEngine.formatClock(state.elapsedMs)
        }
        val label = if (state.mode == ChronometerEngine.Mode.TIMER) "Rimangono" else "Tempo"
        val next = state.nextSignalInMs
        val signal = when {
            state.intervalMinutes <= 0 -> "segnali disattivati"
            next != null -> "prossimo segnale tra ${ChronometerEngine.formatShort(next)}"
            else -> "nessun altro segnale"
        }
        return "$label $clock · $signal"
    }

    private fun buildNotification(state: ChronometerEngine.State): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val toggleLabel = if (state.running) "Pausa" else "Riprendi"
        val toggle = PendingIntent.getService(
            this,
            1,
            Intent(this, TimerService::class.java)
                .setAction(if (state.running) ACTION_PAUSE else ACTION_RESUME),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val reset = PendingIntent.getService(
            this,
            2,
            Intent(this, TimerService::class.java).setAction(ACTION_RESET),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(
                if (state.mode == ChronometerEngine.Mode.TIMER) "Timer" else "Cronometro",
            )
            .setContentText(notificationText(state))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, toggleLabel, toggle)
            .addAction(0, "Azzera", reset)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    @Suppress("WakelockTimeout")
    private fun acquireWakeLock(active: Boolean) {
        if (!active) return
        val manager = getSystemService(PowerManager::class.java)
        if (wakeLock == null) {
            wakeLock = manager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "metronomo:chronometer",
            ).apply { setReferenceCounted(false) }
        }
        wakeLock?.acquire(12 * 60 * 60 * 1000L)
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    companion object {
        private const val CHANNEL_ID = "chronometer"
        private const val NOTIFICATION_ID = 41
        private const val ACTION_PAUSE = "com.metronomo.app.PAUSE"
        private const val ACTION_RESUME = "com.metronomo.app.RESUME"
        private const val ACTION_RESET = "com.metronomo.app.RESET"
    }
}
