package com.metronomo.app.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metronomo.app.audio.MetronomeEngine
import com.metronomo.app.ui.theme.NumeralStyle
import com.metronomo.app.ui.theme.TextMuted
import androidx.compose.runtime.collectAsState
import kotlin.math.roundToInt

@Composable
fun MetronomeScreen(engine: MetronomeEngine) {
    val state by engine.state.collectAsState()
    var taps by remember { mutableStateOf<List<Long>>(emptyList()) }

    fun registerTap() {
        val now = SystemClock.elapsedRealtime()
        val updated = (taps + now).filter { now - it <= 2500L }
        taps = updated
        if (updated.size >= 2) {
            val intervals = updated.zipWithNext { a, b -> b - a }
            val average = intervals.average()
            if (average > 0) {
                engine.setBpm((60_000.0 / average).roundToInt())
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TempoPanel(
            state = state,
            onBpm = engine::setBpm,
            onToggle = engine::toggle,
            onTap = ::registerTap,
        )
        BeatPanel(state = state, onCycleAccent = engine::cycleAccent)
        SignaturePanel(
            state = state,
            onBeats = engine::setBeatsPerBar,
            onUnit = engine::setBeatUnit,
            onSubdivision = engine::setSubdivision,
            onResetAccents = engine::resetAccents,
        )
        Panel {
            ToggleRow(
                title = "Click audio",
                subtitle = if (state.soundEnabled) "Segnale sonoro su ogni battito" else "Modalità silenziosa: solo indicatore visivo",
                checked = state.soundEnabled,
                onCheckedChange = engine::setSoundEnabled,
            )
        }
        Spacer(Modifier.height(2.dp))
        PrimaryButton(
            label = if (state.running) "Ferma" else "Avvia",
            glyph = if (state.running) Glyph.Stop else Glyph.Play,
            onClick = engine::toggle,
        )
    }
}

@Composable
private fun TempoPanel(
    state: MetronomeEngine.State,
    onBpm: (Int) -> Unit,
    onToggle: () -> Unit,
    onTap: () -> Unit,
) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionLabel("Tempo")
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = state.bpm.toString(),
                        style = NumeralStyle,
                        fontSize = 58.sp,
                        lineHeight = 62.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "BPM",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                Text(
                    "${state.tempoName} · ${state.beatsPerBar}/${state.beatUnit}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }

            Box(
                modifier = Modifier
                    .size(66.dp)
                    .clip(CircleShape)
                    .background(
                        if (state.running) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(
                    kind = if (state.running) Glyph.Pause else Glyph.Play,
                    tint = if (state.running) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Slider(
            value = state.bpm.toFloat(),
            onValueChange = { onBpm(it.roundToInt()) },
            valueRange = MetronomeEngine.MIN_BPM.toFloat()..MetronomeEngine.MAX_BPM.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", enabled = state.bpm > MetronomeEngine.MIN_BPM) {
                onBpm(state.bpm - 1)
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onTap),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Tap tempo",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.width(10.dp))
            StepButton("+", enabled = state.bpm < MetronomeEngine.MAX_BPM) {
                onBpm(state.bpm + 1)
            }
        }
    }
}

@Composable
private fun BeatPanel(
    state: MetronomeEngine.State,
    onCycleAccent: (Int) -> Unit,
) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Battuta")
            Spacer(Modifier.weight(1f))
            Text(
                if (state.running) "in corso" else "pronto",
                fontSize = 11.sp,
                color = if (state.running) MaterialTheme.colorScheme.primary else TextMuted,
                letterSpacing = 1.sp,
            )
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.accents.forEachIndexed { index, level ->
                BeatDot(
                    level = level,
                    active = state.running && state.currentBeat == index,
                    beatNumber = index + 1,
                    onClick = { onCycleAccent(index) },
                )
            }
        }

        Spacer(Modifier.height(2.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendDot(MaterialTheme.colorScheme.primary, "Accento")
            LegendDot(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), "Battito")
            LegendDot(Color.Transparent, "Silenzio", outlined = true)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Tocca un cerchio per cambiare il suo accento.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String, outlined: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (outlined) Color.Transparent else color)
                .then(
                    if (outlined) {
                        Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    } else {
                        Modifier
                    },
                ),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SignaturePanel(
    state: MetronomeEngine.State,
    onBeats: (Int) -> Unit,
    onUnit: (Int) -> Unit,
    onSubdivision: (Int) -> Unit,
    onResetAccents: () -> Unit,
) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Metrica")
            Spacer(Modifier.weight(1f))
            Text(
                "${state.beatsPerBar}/${state.beatUnit}",
                style = NumeralStyle,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(10.dp))
        SectionLabel("Battiti per battuta", Modifier.padding(bottom = 8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (1..12).forEach { count ->
                ChipButton(
                    label = count.toString(),
                    selected = state.beatsPerBar == count,
                    onClick = { onBeats(count) },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("Unità di battito", Modifier.padding(bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(2, 4, 8, 16).forEach { unit ->
                ChipButton(
                    label = unit.toString(),
                    selected = state.beatUnit == unit,
                    onClick = { onUnit(unit) },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Suddivisione", Modifier.weight(1f))
            Text(
                subdivisionName(state.subdivision),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (1..6).forEach { value ->
                ChipButton(
                    label = "×$value",
                    selected = state.subdivision == value,
                    onClick = { onSubdivision(value) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Frazioni tra un battito e l'altro, con un click più leggero.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Reimposta accenti",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onResetAccents)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
}

private fun subdivisionName(value: Int): String = when (value) {
    1 -> "nessuna"
    2 -> "duine"
    3 -> "terzine"
    4 -> "quartine"
    5 -> "quintine"
    else -> "sestine"
}
