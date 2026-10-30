package com.metronomo.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metronomo.app.ui.theme.Amber

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, borderColor, RoundedCornerShape(26.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun ChipButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        label = "chipBackground",
    )
    val content by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "chipContent",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = content,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
fun StepButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val color = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = color, fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

enum class Glyph { Play, Pause, Stop, Reset }

@Composable
fun GlyphIcon(kind: Glyph, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        when (kind) {
            Glyph.Play -> {
                val path = Path().apply {
                    moveTo(w * 0.26f, h * 0.12f)
                    lineTo(w * 0.9f, h * 0.5f)
                    lineTo(w * 0.26f, h * 0.88f)
                    close()
                }
                drawPath(path, tint)
            }

            Glyph.Pause -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.22f, h * 0.15f),
                    size = Size(w * 0.19f, h * 0.7f),
                    cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.59f, h * 0.15f),
                    size = Size(w * 0.19f, h * 0.7f),
                    cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
                )
            }

            Glyph.Stop -> drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.22f, h * 0.22f),
                size = Size(w * 0.56f, h * 0.56f),
                cornerRadius = CornerRadius(w * 0.1f, w * 0.1f),
            )

            Glyph.Reset -> {
                val stroke = Stroke(width = w * 0.11f, cap = StrokeCap.Round)
                drawArc(
                    color = tint,
                    startAngle = -50f,
                    sweepAngle = 275f,
                    useCenter = false,
                    topLeft = Offset(w * 0.16f, h * 0.16f),
                    size = Size(w * 0.68f, h * 0.68f),
                    style = stroke,
                )
                val path = Path().apply {
                    moveTo(w * 0.68f, h * 0.02f)
                    lineTo(w * 0.94f, h * 0.16f)
                    lineTo(w * 0.66f, h * 0.34f)
                    close()
                }
                drawPath(path, tint)
            }
        }
    }
}

@Composable
fun PrimaryButton(
    label: String,
    glyph: Glyph,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        GlyphIcon(glyph, MaterialTheme.colorScheme.onPrimary, Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun SecondaryButton(
    label: String,
    glyph: Glyph,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(60.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        GlyphIcon(glyph, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(17.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun BeatDot(
    level: Int,
    active: Boolean,
    beatNumber: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val baseColor = when (level) {
        2 -> MaterialTheme.colorScheme.primary
        1 -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        else -> Color.Transparent
    }
    val color by animateColorAsState(
        if (active) MaterialTheme.colorScheme.onSurface else baseColor,
        label = "beatColor",
    )
    val scale by animateFloatAsState(
        targetValue = if (active) 1.18f else 1f,
        animationSpec = androidx.compose.animation.core.tween(90),
        label = "beatScale",
    )
    val dotSize = when (level) {
        2 -> 40.dp
        1 -> 30.dp
        else -> 20.dp
    }
    val interaction = remember { MutableInteractionSource() }
    val numberColor = when {
        active -> MaterialTheme.colorScheme.surface
        level != 0 -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .size(width = (dotSize.value + 22).dp, height = 66.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            )
        }
        Box(
            Modifier
                .size(dotSize * scale)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (level == 0 && !active) {
                        Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                beatNumber.toString(),
                fontSize = if (level == 2) 13.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                color = numberColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun ProgressTrack(fraction: Float, modifier: Modifier = Modifier) {
    val clamped = fraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (clamped > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(clamped)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Amber),
            )
        }
    }
}
