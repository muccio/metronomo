package com.metronomo.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metronomo.app.audio.ChronometerEngine
import com.metronomo.app.audio.ChronometerEngine.Companion.formatClock
import com.metronomo.app.audio.ChronometerEngine.Companion.formatShort
import com.metronomo.app.audio.ChronometerEngine.Companion.formatStopwatch
import com.metronomo.app.ui.theme.Amber
import com.metronomo.app.ui.theme.Hairline
import com.metronomo.app.ui.theme.NumeralStyle
import com.metronomo.app.ui.theme.Surface

@Composable
fun ChronometerScreen(engine: ChronometerEngine) {
    val state by engine.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ModeSwitcher(state.mode) { engine.setMode(it) }
        TimePanel(state)
        if (state.mode == ChronometerEngine.Mode.TIMER) {
            DurationPanel(state, engine::setTarget)
        }
        SignalPanel(state, engine::setInterval)
        TogglesPanel(
            state = state,
            onSound = engine::setSoundEnabled,
            onVibration = engine::setVibrationEnabled,
        )
        Spacer(Modifier.height(2.dp))
        TransportRow(state, engine)
    }
}

@Composable
private fun ModeSwitcher(mode: ChronometerEngine.Mode, onChange: (ChronometerEngine.Mode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ModeTab("Cronometro", mode == ChronometerEngine.Mode.STOPWATCH, Modifier.weight(1f)) {
            onChange(ChronometerEngine.Mode.STOPWATCH)
        }
        ModeTab("Timer", mode == ChronometerEngine.Mode.TIMER, Modifier.weight(1f)) {
            onChange(ChronometerEngine.Mode.TIMER)
        }
    }
}

@Composable
private fun ModeTab(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (active) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimePanel(state: ChronometerEngine.State) {
    val glow = remember { Animatable(0f) }
    LaunchedEffect(state.pulse) {
        if (state.pulse > 0L) {
            glow.snapTo(1f)
            glow.animateTo(0f, animationSpec = tween(durationMillis = 1200, easing = LinearEasing))
        }
    }
    val border = lerp(Hairline, Amber, glow.value)
    val background = lerp(Surface, Color(0xFF2A1F05), glow.value * 0.9f)

    val clock = when {
        state.mode == ChronometerEngine.Mode.TIMER -> formatClock(state.remainingMs)
        else -> formatStopwatch(state.elapsedMs)
    }
    val label = when {
        state.mode == ChronometerEngine.Mode.TIMER && state.finished -> "Tempo scaduto"
        state.mode == ChronometerEngine.Mode.TIMER -> "Rimangono"
        else -> "Tempo trascorso"
    }

    Panel(borderColor = border) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(background)
                .padding(vertical = 10.dp),
        ) {
            Column(Modifier.padding(horizontal = 8.dp)) {
                SectionLabel(label)
                Spacer(Modifier.height(6.dp))
                Text(
                    clock,
                    style = NumeralStyle,
                    fontSize = if (state.mode == ChronometerEngine.Mode.TIMER) 56.sp else 50.sp,
                    lineHeight = 62.sp,
                    color = if (state.finished && state.mode == ChronometerEngine.Mode.TIMER) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        if (state.mode == ChronometerEngine.Mode.TIMER) {
            ProgressTrack(if (state.targetMs > 0) state.elapsedMs.toFloat() / state.targetMs else 0f)
            Spacer(Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val next = state.nextSignalInMs
            Column(Modifier.weight(1f)) {
                Text(
                    "Segnali emessi",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    state.signals.toString(),
                    style = NumeralStyle,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "Prossimo segnale",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    when {
                        state.intervalMinutes <= 0 -> "off"
                        next != null -> formatShort(next)
                        else -> "—"
                    },
                    style = NumeralStyle,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun DurationPanel(state: ChronometerEngine.State, onTarget: (Int) -> Unit) {
    val current = (state.targetMs / 60_000L).toInt()
    Panel {
        SectionLabel("Durata timer")
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", enabled = current > 1 && !state.running) { onTarget(current - 1) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "$current min",
                    style = NumeralStyle,
                    fontSize = 30.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            StepButton("+", enabled = current < 600 && !state.running) { onTarget(current + 1) }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(1, 5, 10, 15, 20, 25, 30, 45, 60).forEach { minutes ->
                ChipButton(
                    label = "$minutes′",
                    selected = current == minutes,
                    onClick = { if (!state.running) onTarget(minutes) },
                )
            }
        }
    }
}

@Composable
private fun SignalPanel(state: ChronometerEngine.State, onInterval: (Int) -> Unit) {
    val interval = state.intervalMinutes
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Segnale ogni", Modifier.weight(1f))
            Text(
                if (interval == 0) "disattivato" else "$interval min",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", enabled = interval > 0) { onInterval(interval - 1) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    if (interval == 0) "off" else interval.toString(),
                    style = NumeralStyle,
                    fontSize = 30.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            StepButton("+", enabled = interval < 180) { onInterval(interval + 1) }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(0, 1, 2, 5, 10, 15, 20, 30).forEach { minutes ->
                ChipButton(
                    label = if (minutes == 0) "Off" else "$minutes′",
                    selected = interval == minutes,
                    onClick = { onInterval(minutes) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (interval == 0) {
                "Segnali disattivati: il cronometro resta muto."
            } else {
                "Un segnale acustico ogni ${if (interval == 1) "minuto" else "$interval minuti"}, " +
                    "anche a schermo spento."
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TogglesPanel(
    state: ChronometerEngine.State,
    onSound: (Boolean) -> Unit,
    onVibration: (Boolean) -> Unit,
) {
    Panel {
        ToggleRow(
            title = "Segnale sonoro",
            subtitle = "Campanello all'intervallo e a fine timer",
            checked = state.soundEnabled,
            onCheckedChange = onSound,
        )
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
        ToggleRow(
            title = "Vibrazione",
            subtitle = "Utile in ambienti rumorosi",
            checked = state.vibrationEnabled,
            onCheckedChange = onVibration,
        )
    }
}

@Composable
private fun TransportRow(state: ChronometerEngine.State, engine: ChronometerEngine) {
    val label = when {
        state.running -> "Pausa"
        state.finished -> "Riavvia"
        state.elapsedMs > 0 -> "Riprendi"
        else -> "Avvia"
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PrimaryButton(
            label = label,
            glyph = if (state.running) Glyph.Pause else Glyph.Play,
            modifier = Modifier.weight(1f),
            onClick = if (state.finished) engine::start else engine::toggle,
        )
        SecondaryButton(
            label = "Azzera",
            glyph = Glyph.Reset,
            onClick = engine::reset,
        )
    }
}
