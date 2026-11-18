package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

enum class ReactorMode {
    STANDBY,
    LISTENING,
    PROCESSING,
    SPEAKING
}

@Composable
fun Modifier.jarvisHudBackdrop(): Modifier {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)
    val glowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)
    val goldGlow = MaterialTheme.colorScheme.secondary.copy(alpha = 0.06f)

    return this.drawBehind {
        val step = 34.dp.toPx()
        var x = 0f
        while (x <= size.width) {
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 0.75f
            )
            x += step
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 0.75f
            )
            y += step
        }

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glowColor, Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.18f),
                radius = size.maxDimension * 0.55f
            ),
            radius = size.maxDimension * 0.55f,
            center = Offset(size.width * 0.5f, size.height * 0.18f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(goldGlow, Color.Transparent),
                center = Offset(size.width * 0.88f, size.height * 0.82f),
                radius = size.maxDimension * 0.4f
            ),
            radius = size.maxDimension * 0.4f,
            center = Offset(size.width * 0.88f, size.height * 0.82f)
        )
    }
}

@Composable
fun ArcReactorCore(
    mode: ReactorMode,
    rmsDb: Float = 0f,
    size: Dp = 148.dp,
    onClick: (() -> Unit)? = null,
    contentDescriptionText: String = "JARVIS Arc Reactor Core",
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "jarvis_arc_reactor")

    val outerRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mode) {
                    ReactorMode.STANDBY -> 14000
                    ReactorMode.LISTENING -> 4500
                    ReactorMode.PROCESSING -> 3000
                    ReactorMode.SPEAKING -> 5500
                },
                easing = LinearEasing
            )
        ),
        label = "outer_rot"
    )

    val innerRotation by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mode) {
                    ReactorMode.STANDBY -> 9000
                    ReactorMode.LISTENING -> 2800
                    ReactorMode.PROCESSING -> 2000
                    ReactorMode.SPEAKING -> 3500
                },
                easing = LinearEasing
            )
        ),
        label = "inner_rot"
    )

    val pulse by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (mode == ReactorMode.STANDBY) 1600 else 520
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_pulse"
    )

    val rmsScale by animateFloatAsState(
        targetValue = if (mode == ReactorMode.LISTENING) {
            1f + (rmsDb.coerceIn(0f, 12f) / 12f) * 0.16f
        } else {
            1f
        },
        animationSpec = spring(),
        label = "rms_scale"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = when (mode) {
        ReactorMode.LISTENING -> MaterialTheme.colorScheme.secondary
        ReactorMode.PROCESSING -> MaterialTheme.colorScheme.tertiary
        ReactorMode.SPEAKING -> MaterialTheme.colorScheme.primary
        ReactorMode.STANDBY -> MaterialTheme.colorScheme.secondary
    }
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)

    val interactiveModifier = if (onClick != null) {
        modifier
            .size(size)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = contentDescriptionText }
    } else {
        modifier
            .size(size)
            .semantics { contentDescription = contentDescriptionText }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = interactiveModifier
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = (this.size.minDimension / 2f) * 0.92f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)

            // Ambient reactor glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.28f * pulse),
                        accentColor.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * 1.15f * rmsScale
                ),
                radius = radius * 1.15f * rmsScale,
                center = center
            )

            // Outer telemetry tick ring
            for (deg in 0 until 360 step 15) {
                val rad = Math.toRadians(deg.toDouble())
                val isMajor = deg % 45 == 0
                val innerR = radius * if (isMajor) 0.84f else 0.90f
                val outerR = radius * 0.96f
                drawLine(
                    color = if (isMajor) primaryColor.copy(alpha = 0.8f) else trackColor,
                    start = Offset(
                        center.x + (innerR * cos(rad)).toFloat(),
                        center.y + (innerR * sin(rad)).toFloat()
                    ),
                    end = Offset(
                        center.x + (outerR * cos(rad)).toFloat(),
                        center.y + (outerR * sin(rad)).toFloat()
                    ),
                    strokeWidth = if (isMajor) 2.2.dp.toPx() else 1.dp.toPx()
                )
            }

            // Counter-rotating segmented arc ring 1
            rotate(degrees = outerRotation, pivot = center) {
                val r1 = radius * 0.76f
                val arcSize1 = Size(r1 * 2f, r1 * 2f)
                val topLeft1 = Offset(center.x - r1, center.y - r1)
                for (i in 0 until 4) {
                    drawArc(
                        color = primaryColor.copy(alpha = 0.85f),
                        startAngle = i * 90f + 12f,
                        sweepAngle = 56f,
                        useCenter = false,
                        topLeft = topLeft1,
                        size = arcSize1,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Butt)
                    )
                }
            }

            // Counter-rotating segmented arc ring 2
            rotate(degrees = innerRotation, pivot = center) {
                val r2 = radius * 0.58f
                val arcSize2 = Size(r2 * 2f, r2 * 2f)
                val topLeft2 = Offset(center.x - r2, center.y - r2)
                for (i in 0 until 3) {
                    drawArc(
                        color = accentColor.copy(alpha = 0.9f),
                        startAngle = i * 120f + 15f,
                        sweepAngle = 78f,
                        useCenter = false,
                        topLeft = topLeft2,
                        size = arcSize2,
                        style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // Inner Arc Reactor triangular/circular core
            val coreRadius = radius * 0.34f * pulse * rmsScale
            drawCircle(
                color = primaryColor.copy(alpha = 0.22f),
                radius = coreRadius * 1.28f,
                center = center
            )
            drawCircle(
                color = primaryColor.copy(alpha = 0.88f),
                radius = coreRadius,
                center = center,
                style = Stroke(width = 2.5.dp.toPx())
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.92f), primaryColor),
                    center = center,
                    radius = coreRadius * 0.72f
                ),
                radius = coreRadius * 0.65f,
                center = center
            )
        }
    }
}

@Composable
fun JarvisHudCard(
    telemetryTag: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit
) {
    val bracketColor = accentColor.copy(alpha = 0.85f)
    Surface(
        shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp, topEnd = 4.dp, bottomStart = 4.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val len = 12.dp.toPx()
                val stroke = 2.dp.toPx()
                // Top-right reticle
                drawLine(bracketColor, Offset(size.width - len, 0f), Offset(size.width, 0f), stroke)
                drawLine(bracketColor, Offset(size.width, 0f), Offset(size.width, len), stroke)
                // Bottom-left reticle
                drawLine(bracketColor, Offset(0f, size.height - len), Offset(0f, size.height), stroke)
                drawLine(bracketColor, Offset(0f, size.height), Offset(len, size.height), stroke)
            }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                JarvisStatusPill(text = telemetryTag, accentColor = accentColor)
            }
            content()
        }
    }
}

@Composable
fun JarvisStatusPill(
    text: String,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CutCornerShape(4.dp),
        color = accentColor.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun JarvisSectionHeader(
    telemetryCode: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        JarvisStatusPill(text = telemetryCode)
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
