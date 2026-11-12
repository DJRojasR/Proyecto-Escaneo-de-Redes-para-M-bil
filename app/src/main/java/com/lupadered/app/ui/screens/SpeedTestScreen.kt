package com.lupadered.app.ui.screens

import com.lupadered.app.i18n.tr
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lupadered.app.data.AppSettings
import com.lupadered.app.data.SpeedTestRecord
import com.lupadered.app.ui.ScreenScaffold
import com.lupadered.app.ui.SpeedPhase
import com.lupadered.app.ui.SpeedTestState
import com.lupadered.app.ui.SpeedTestViewModel
import com.lupadered.app.ui.SpeedTestViewModel.Companion.formatMbps
import com.lupadered.app.ui.components.CheckList
import com.lupadered.app.ui.components.LockedCard
import com.lupadered.app.ui.components.PremiumTag
import com.lupadered.app.ui.components.SectionTitle
import com.lupadered.app.ui.components.dateTime
import com.lupadered.app.ui.components.rememberIsPremium
import com.lupadered.app.ui.theme.LocalIsDark
import com.lupadered.app.ui.theme.StatusColors
import kotlin.math.cos
import kotlin.math.sin

/** Marcas del velocímetro (Mbps). La escala no es lineal para que se lean bien 5 Mbps y también 500. */
private val GAUGE_MARKS = listOf(0.0, 1.0, 5.0, 10.0, 25.0, 50.0, 100.0, 250.0, 500.0, 1000.0)

private fun gaugeFraction(mbps: Double): Float {
    if (mbps <= 0) return 0f
    for (i in 1 until GAUGE_MARKS.size) {
        val lo = GAUGE_MARKS[i - 1]
        val hi = GAUGE_MARKS[i]
        if (mbps <= hi) {
            val local = (mbps - lo) / (hi - lo)
            return ((i - 1 + local) / (GAUGE_MARKS.size - 1)).toFloat()
        }
    }
    return 1f
}

@Composable
fun SpeedTestScreen(vm: SpeedTestViewModel, onBack: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val isPremium = rememberIsPremium()
    val dark = LocalIsDark.current
    val downColor = if (dark) Color(0xFF8AB4F8) else Color(0xFF1A63D6)
    val upColor = if (dark) Color(0xFFD7AEFB) else Color(0xFF8E24AA)

    ScreenScaffold(title = tr("Prueba de velocidad"), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                tr("Mide la velocidad real de tu Internet: cuánto bajas (videos, descargas), cuánto subes (videollamadas, fotos) y cuánto tarda en responder (juegos)."),
                style = MaterialTheme.typography.bodyMedium,
            )

            // ---------- Velocímetro
            val needleColor = when (s.phase) {
                SpeedPhase.UPLOAD -> upColor
                else -> downColor
            }
            Gauge(
                mbps = if (s.running || s.phase == SpeedPhase.DONE) s.liveMbps else 0.0,
                color = needleColor,
                label = when (s.phase) {
                    SpeedPhase.LOCATING -> tr("Conectando…")
                    SpeedPhase.DOWNLOAD -> tr("Bajada")
                    SpeedPhase.UPLOAD -> tr("Subida")
                    SpeedPhase.DONE -> tr("Bajada")
                    else -> tr("Listo para medir")
                },
            )

            // ---------- Botón
            val limitReached = !isPremium && s.testsToday >= AppSettings.FREE_SPEED_TESTS_PER_DAY && !s.running
            if (s.running) {
                OutlinedButton(onClick = vm::cancel, modifier = Modifier.fillMaxWidth()) { Text(tr("Detener")) }
            } else if (!limitReached) {
                Button(
                    onClick = vm::requestStart,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = downColor),
                ) {
                    Text(
                        if (s.phase == SpeedPhase.DONE || s.phase == SpeedPhase.ERROR) tr("Medir otra vez") else tr("Iniciar prueba"),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            if (!isPremium) {
                val left = (AppSettings.FREE_SPEED_TESTS_PER_DAY - s.testsToday).coerceAtLeast(0)
                if (limitReached) {
                    LockedCard(
                        tr("Pruebas ilimitadas"),
                        tr("La versión gratis incluye %1\$s pruebas por día y ya las usaste. Con Premium mides cuantas veces quieras y guardas el historial con gráfico.", AppSettings.FREE_SPEED_TESTS_PER_DAY),
                    )
                } else {
                    Text(
                        tr("Te quedan %1\$s %2\$s gratis hoy.", left, if (left == 1) tr("prueba") else tr("pruebas")),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            s.error?.let { Text(it, color = StatusColors.bad, style = MaterialTheme.typography.bodyMedium) }

            // ---------- Resultados
            if (s.downloadMbps != null || s.uploadMbps != null || s.running) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResultTile(Icons.Outlined.ArrowDownward, tr("Bajada"), s.downloadMbps?.let { formatMbps(it) }, tr("Mbps"), downColor, Modifier.weight(1f))
                    ResultTile(Icons.Outlined.ArrowUpward, tr("Subida"), s.uploadMbps?.let { formatMbps(it) }, tr("Mbps"), upColor, Modifier.weight(1f))
                    ResultTile(Icons.Outlined.Timer, tr("Latencia"), s.pingMs?.toInt()?.toString(), "ms", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                }
            }

            if (s.phase == SpeedPhase.DONE) {
                Explanation(s)
            }

            // ---------- Pasos en vivo
            if (s.checks.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(tr("Lo que medimos"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 4.dp))
                        CheckList(s.checks)
                    }
                }
            }

            // ---------- Historial (Premium)
            if (s.history.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(tr("Historial de pruebas"))
                    if (!isPremium) {
                        Spacer(Modifier.width(8.dp))
                        PremiumTag()
                    }
                }
                if (isPremium) {
                    HistoryChart(s.history.take(20).reversed(), downColor, upColor)
                    s.history.take(30).forEach { HistoryRow(it, downColor, upColor) }
                } else {
                    s.history.take(1).forEach { HistoryRow(it, downColor, upColor) }
                    LockedCard(
                        tr("Historial de velocidad"),
                        tr("Guarda todas tus pruebas y mira en un gráfico si tu Internet va más lento a ciertas horas."),
                    )
                }
            }

            Text(
                tr("Las pruebas usan los servidores abiertos de M-Lab (measurementlab.net). Cada prueba usa datos: con Internet rápido puede gastar más de 100 MB."),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (s.askConsent) {
        AlertDialog(
            onDismissRequest = vm::dismissDialogs,
            title = { Text(tr("Antes de tu primera prueba")) },
            text = {
                Text(
                    tr("Para medir tu velocidad usamos los servidores de M-Lab (Measurement Lab), una plataforma abierta de investigación sobre Internet apoyada por Google.\n\nM-Lab guarda y publica el resultado de cada prueba como datos abiertos, junto con tu dirección IP pública (la de tu conexión a Internet, no la de tu teléfono en tu red). No se envía tu nombre, tu correo ni nada de tu red local.\n\n¿Aceptas hacer la prueba con M-Lab?"),
                )
            },
            confirmButton = { TextButton(onClick = vm::acceptConsent) { Text(tr("Acepto")) } },
            dismissButton = { TextButton(onClick = vm::dismissDialogs) { Text(tr("Ahora no")) } },
        )
    }
    if (s.askCellular) {
        AlertDialog(
            onDismissRequest = vm::dismissDialogs,
            title = { Text(tr("Estás usando datos móviles")) },
            text = {
                Text(
                    tr("La prueba puede gastar varios cientos de MB de tu plan de datos. Te recomendamos conectarte a un WiFi primero."),
                )
            },
            confirmButton = { TextButton(onClick = vm::confirmCellular) { Text(tr("Medir igual")) } },
            dismissButton = { TextButton(onClick = vm::dismissDialogs) { Text(tr("Cancelar")) } },
        )
    }
}

/** Velocímetro: arco de 240° con escala 0–1000 Mbps y aguja animada. */
@Composable
private fun Gauge(mbps: Double, color: Color, label: String) {
    val fraction by animateFloatAsState(gaugeFraction(mbps), animationSpec = tween(400), label = "gauge")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val needle = MaterialTheme.colorScheme.onSurface
    val labels = listOf("0", "1", "5", "10", "25", "50", "100", "250", "500", "1000")
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = tickColor)

    Box(Modifier.fillMaxWidth().aspectRatio(1.35f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val stroke = 18.dp.toPx()
            val radius = minOf(size.width / 2f, size.height / 1.55f) - stroke
            val center = Offset(size.width / 2f, radius + stroke + 8.dp.toPx())
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
            val start = 150f
            val sweep = 240f
            drawArc(track, start, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color, start, sweep * fraction, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            // Marcas y números
            labels.forEachIndexed { i, text ->
                val angle = Math.toRadians((start + sweep * i / (labels.size - 1)).toDouble())
                val inner = radius - stroke * 1.1f
                val px = center.x + (inner * cos(angle)).toFloat()
                val py = center.y + (inner * sin(angle)).toFloat()
                val layout = textMeasurer.measure(text, labelStyle)
                drawText(layout, topLeft = Offset(px - layout.size.width / 2f, py - layout.size.height / 2f))
            }
            // Aguja
            val a = Math.toRadians((start + sweep * fraction).toDouble())
            val len = radius - stroke * 2.2f
            drawLine(
                needle, center,
                Offset(center.x + (len * cos(a)).toFloat(), center.y + (len * sin(a)).toFloat()),
                strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round,
            )
            drawCircle(needle, radius = 8.dp.toPx(), center = center)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (mbps > 0) formatMbps(mbps) else "—",
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(tr("Mbps · %1\$s", label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ResultTile(icon: ImageVector, label: String, value: String?, unit: String, color: Color, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
            Text(value ?: "—", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
            Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Consejo para medir bien la velocidad. */
@Composable
private fun Explanation(s: SpeedTestState) {
    if (s.downloadMbps == null) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tr("Consejo"), style = MaterialTheme.typography.titleSmall)
            Text(
                tr("Para medir la velocidad real de tu Internet, haz la prueba cerca del router. Lejos del router verás menos velocidad por la señal WiFi, no por tu proveedor."),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Gráfico de barras de las últimas pruebas: bajada y subida. */
@Composable
private fun HistoryChart(list: List<SpeedTestRecord>, downColor: Color, upColor: Color) {
    if (list.size < 2) return
    val max = list.maxOf { maxOf(it.downloadMbps, it.uploadMbps) }.coerceAtLeast(1.0)
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column {
        Text(tr("Máx. %1\$s Mbps", formatMbps(max)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val slot = size.width / list.size
            val bar = slot * 0.35f
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
            list.forEachIndexed { i, t ->
                val x = i * slot + slot * 0.1f
                val hd = (t.downloadMbps / max * size.height).toFloat()
                val hu = (t.uploadMbps / max * size.height).toFloat()
                drawRect(downColor, Offset(x, size.height - hd), androidx.compose.ui.geometry.Size(bar, hd))
                drawRect(upColor, Offset(x + bar, size.height - hu), androidx.compose.ui.geometry.Size(bar, hu))
            }
        }
        Text(
            tr("Últimas %1\$s pruebas, de la más antigua a la más reciente · azul = bajada, morado = subida", list.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistoryRow(t: SpeedTestRecord, downColor: Color, upColor: Color) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(dateTime(t.at), style = MaterialTheme.typography.bodyMedium)
                t.server?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text("↓ ${formatMbps(t.downloadMbps)}", color = downColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Text("↑ ${formatMbps(t.uploadMbps)}", color = upColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Text(t.pingMs?.let { "${it.toInt()} ms" } ?: "—", style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}
