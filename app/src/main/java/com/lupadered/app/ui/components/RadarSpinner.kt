package com.lupadered.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animación de "radar" que gira mientras la app trabaja: un haz que da vueltas,
 * una onda que se expande y puntos que aparecen, como si encontrara dispositivos.
 */
@Composable
fun RadarSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "radar")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1600, easing = LinearEasing)),
        label = "angulo",
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1600, easing = LinearEasing)),
        label = "onda",
    )

    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = center
        val thin = 1.2.dp.toPx()

        // Anillos fijos
        drawCircle(color.copy(alpha = 0.22f), radius = r - thin, center = c, style = Stroke(thin))
        drawCircle(color.copy(alpha = 0.22f), radius = r * 0.55f, center = c, style = Stroke(thin))

        // Onda que se expande y se desvanece
        drawCircle(
            color.copy(alpha = 0.45f * (1f - pulse)),
            radius = (r - thin) * pulse,
            center = c,
            style = Stroke(1.6.dp.toPx()),
        )

        // "Dispositivos" que se encienden cuando el haz pasa por encima
        val blips = listOf(40f to 0.70f, 155f to 0.45f, 250f to 0.80f)
        for ((deg, dist) in blips) {
            val since = ((angle - deg) % 360f + 360f) % 360f // grados desde que pasó el haz
            val alpha = (1f - since / 300f).coerceIn(0f, 1f)
            if (alpha > 0f) {
                val rad = Math.toRadians(deg.toDouble())
                val p = Offset(c.x + (r * dist * cos(rad)).toFloat(), c.y + (r * dist * sin(rad)).toFloat())
                drawCircle(color.copy(alpha = alpha), radius = 2.4.dp.toPx(), center = p)
            }
        }

        // Haz giratorio con estela degradada
        rotate(angle, pivot = c) {
            drawArc(
                brush = Brush.sweepGradient(
                    0f to Color.Transparent,
                    0.75f to Color.Transparent,
                    1f to color.copy(alpha = 0.55f),
                    center = c,
                ),
                startAngle = 270f,
                sweepAngle = 90f,
                useCenter = true,
                topLeft = Offset(c.x - r, c.y - r),
                size = Size(r * 2, r * 2),
            )
            drawLine(
                color = color,
                start = c,
                end = Offset(c.x + r - thin, c.y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // Centro
        drawCircle(color, radius = 2.8.dp.toPx(), center = c)
    }
}

/** Radar pequeño + texto, para mostrar "trabajando…" en una sola línea. */
@Composable
fun RadarStatus(text: String, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        RadarSpinner(size = size)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
