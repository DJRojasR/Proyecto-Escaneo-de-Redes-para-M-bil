package com.lupadered.app.ui.screens

import com.lupadered.app.i18n.tr
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.lupadered.app.ui.components.CheckList
import com.lupadered.app.ui.components.RadarSpinner
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import com.lupadered.app.ui.theme.LocalIsDark
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lupadered.app.knowledge.DeviceType
import com.lupadered.app.knowledge.Finding
import com.lupadered.app.knowledge.Severity
import com.lupadered.app.ui.ExportViewModel
import com.lupadered.app.ui.LatencySample
import com.lupadered.app.ui.LatencyStats
import com.lupadered.app.ui.LatencyViewModel
import com.lupadered.app.ui.NetworksViewModel
import com.lupadered.app.ui.Presence
import com.lupadered.app.ui.ScreenScaffold
import com.lupadered.app.ui.SecurityViewModel
import com.lupadered.app.ui.components.DeviceAvatar
import com.lupadered.app.ui.components.EmptyState
import com.lupadered.app.ui.components.LockedCard
import com.lupadered.app.ui.components.RadarStatus
import com.lupadered.app.ui.components.SectionTitle
import com.lupadered.app.ui.components.Tag
import com.lupadered.app.ui.components.latencyText
import com.lupadered.app.ui.components.relativeTime
import com.lupadered.app.ui.components.rememberIsPremium
import com.lupadered.app.ui.theme.StatusColors

// ============================================================== Revisión de seguridad (RF-29)

@Composable
fun SecurityScreen(vm: SecurityViewModel, onOpenDevice: (Long) -> Unit, onBack: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val isPremium = rememberIsPremium()

    ScreenScaffold(title = tr("Revisión de seguridad"), onBack = onBack) { padding ->
        if (!isPremium) {
            Column(Modifier.padding(padding).padding(16.dp)) {
                LockedCard(
                    tr("Revisión de seguridad"),
                    tr("Revisamos uno por uno los equipos de tu red, te mostramos en vivo lo que encontramos, te damos un puntaje de 0 a 100 y los pasos para cerrar lo riesgoso."),
                )
            }
            return@ScreenScaffold
        }
        if (s.noNetwork) {
            EmptyState(Icons.Outlined.WifiOff, tr("No estás conectado a un WiFi"), tr("Conéctate a tu red para revisarla."), Modifier.padding(padding))
            return@ScreenScaffold
        }
        val r = s.report
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        ScoreRing(if (s.scanning) null else r?.score, s.scanning)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                when {
                                    s.scanning -> tr("Revisando tu red…")
                                    else -> r?.grade ?: tr("Calculando…")
                                },
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                if (s.scanning) tr("Mira abajo lo que vamos revisando.") else r?.summary.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (!s.scanning && r != null && r.onlineDevices > 0) {
                                Text(
                                    tr("Puertos revisados en %1\$s de %2\$s equipos conectados.", r.reviewedDevices, r.onlineDevices),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
            item {
                if (s.scanning) {
                    Column {
                        LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                        TextButton(onClick = vm::cancel) { Text(tr("Detener")) }
                    }
                } else {
                    Button(onClick = vm::scanAll, modifier = Modifier.fillMaxWidth()) {
                        Text(if (s.checks.isEmpty()) tr("Iniciar revisión completa") else tr("Volver a revisar"))
                    }
                    if (s.checks.isEmpty()) {
                        Text(
                            tr("Revisaremos los puertos de cada equipo conectado, buscaremos equipos desconocidos y calcularemos el puntaje. Tarda de 10 a 60 segundos."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            if (s.checks.isNotEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Text(tr("Lo que revisamos"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 4.dp))
                            CheckList(s.checks)
                        }
                    }
                }
            }
            s.summary?.let { summary ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Text(summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
                    }
                }
            }
            if (!s.scanning) {
                if (r != null && r.findings.isEmpty() && !s.loading) {
                    item {
                        Text(
                            tr("¡Bien! No encontramos nada que corregir. Vuelve a revisar cuando conectes equipos nuevos."),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                r?.findings?.takeIf { it.isNotEmpty() }?.let { list ->
                    item { SectionTitle(tr("Qué corregir (%1\$s)", list.size)) }
                    items(list) { f -> FindingCard(f, onOpenDevice) }
                }
            }
        }
    }
}

/** Anillo con el puntaje: se llena y cuenta hasta el valor con una animación. */
@Composable
private fun ScoreRing(score: Int?, scanning: Boolean) {
    val target = score ?: 0
    val animatedScore by animateIntAsState(target, animationSpec = tween(1200), label = "score")
    val sweep by animateFloatAsState(360f * target / 100f, animationSpec = tween(1200), label = "sweep")
    val color = when {
        score == null -> StatusColors.idle
        score >= 85 -> StatusColors.good
        score >= 50 -> StatusColors.warn
        else -> StatusColors.bad
    }
    val track = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
        if (scanning) {
            RadarSpinner(size = 84.dp)
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 10.dp.toPx())
                drawArc(track, -90f, 360f, false, style = stroke)
                drawArc(color, -90f, sweep, false, style = stroke)
            }
            Text(
                if (score == null) "…" else animatedScore.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun FindingCard(f: Finding, onOpenDevice: (Long) -> Unit) {
    var open by remember { mutableStateOf(f.severity == Severity.HIGH) }
    val color = when (f.severity) {
        Severity.HIGH -> StatusColors.bad
        Severity.MEDIUM -> StatusColors.warn
        Severity.LOW -> MaterialTheme.colorScheme.tertiary
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { open = !open },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Tag(f.severity.label, color)
            }
            Text(f.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (open) {
                Text(tr("Qué hacer:"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                f.steps.forEachIndexed { i, step ->
                    Text("${i + 1}. $step", style = MaterialTheme.typography.bodySmall)
                }
                f.deviceId?.let { id ->
                    TextButton(onClick = { onOpenDevice(id) }) { Text(tr("Ver el equipo")) }
                }
            }
        }
    }
}

// ============================================================== Monitor de latencia (RF-30)

@Composable
fun LatencyScreen(vm: LatencyViewModel, onBack: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val isPremium = rememberIsPremium()

    if (isPremium) {
        DisposableEffect(Unit) {
            vm.start()
            onDispose { vm.stop() }
        }
    }

    ScreenScaffold(title = tr("Monitor de latencia"), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                tr("Medimos cada segundo cuánto tardan en responder tu router y un servidor de Internet. Así sabes si la lentitud viene de tu WiFi o de tu proveedor."),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!isPremium) {
                LockedCard(
                    tr("Monitor de latencia en vivo"),
                    tr("Un gráfico en tiempo real del ping a tu router y a Internet, con pérdida de paquetes, variación (jitter) y un diagnóstico en palabras simples."),
                )
                return@Column
            }
            if (s.noNetwork) {
                Text(tr("No encontramos tu router. Conéctate a tu WiFi e inténtalo de nuevo."), color = StatusColors.warn)
                return@Column
            }

            // Naranja y azul: se distinguen bien entre sí, también para personas daltónicas.
            val dark = LocalIsDark.current
            val routerColor = if (dark) Color(0xFFFFA24D) else Color(0xFFE8710A)
            val netColor = if (dark) Color(0xFF8AB4F8) else Color(0xFF1A63D6)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(12.dp)) {
                    LatencyChart(s.samples, routerColor, netColor)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Legend(routerColor, tr("Router (%1\$s)", s.gateway ?: "—"), dashed = false)
                        Legend(netColor, tr("Internet"), dashed = true)
                    }
                }
            }
            LossAlert(s.samples, s.router, s.internet)
            SignalCard(s.signalDbm)
            StatsCard(tr("Router (tu WiFi)"), s.router, routerColor)
            StatsCard(tr("Internet (tu proveedor)"), s.internet, netColor)

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Text(
                    s.verdict ?: tr("Midiendo… en unos segundos te diremos qué encontramos."),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Text(
                tr("La medición se detiene al salir de esta pantalla."),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Aviso en rojo cuando el router o Internet dejan de responder. */
@Composable
private fun LossAlert(samples: List<LatencySample>, router: LatencyStats?, internet: LatencyStats?) {
    val last = samples.lastOrNull() ?: return
    val messages = buildList {
        if (last.router == null) add(tr("Tu router no respondió en la última medición."))
        else if (last.internet == null) add(tr("Internet no respondió en la última medición (tu router sí)."))
        val rl = router?.lossPercent ?: 0
        val il = internet?.lossPercent ?: 0
        if (rl > 0) add(tr("Se perdió el %1\$s%% de las respuestas del router en el último minuto: señal WiFi inestable.", rl))
        if (il > 0 && il > rl) add(tr("Se perdió el %1\$s%% de las respuestas de Internet: puede ser tu proveedor.", il))
    }
    AnimatedVisibility(visible = messages.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Warning, contentDescription = null, tint = StatusColors.bad)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(tr("Hay respuestas perdidas"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    messages.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    }
}

/** Intensidad de la señal WiFi en vivo, con barras y un consejo para mejorarla. */
@Composable
private fun SignalCard(dbm: Int?) {
    val (label, bars, color) = when {
        dbm == null -> Triple(tr("No disponible"), 0, StatusColors.idle)
        dbm >= -55 -> Triple(tr("Excelente"), 4, StatusColors.good)
        dbm >= -67 -> Triple(tr("Buena"), 3, StatusColors.good)
        dbm >= -75 -> Triple(tr("Regular"), 2, StatusColors.warn)
        else -> Triple(tr("Débil"), 1, StatusColors.bad)
    }
    val animatedBars by animateIntAsState(bars, label = "bars")
    val idle = MaterialTheme.colorScheme.outlineVariant
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.width(30.dp).height(22.dp)) {
                    val bw = size.width / 5f
                    for (b in 0 until 4) {
                        val bh = size.height * (b + 1) / 4f
                        drawRect(
                            if (b < animatedBars) color else idle,
                            topLeft = Offset(b * bw * 1.3f, size.height - bh),
                            size = androidx.compose.ui.geometry.Size(bw, bh),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Señal WiFi: %1\$s", label), style = MaterialTheme.typography.titleSmall, color = color)
                    Text(
                        dbm?.let { tr("%1\$s dBm (mientras más cerca de 0, mejor)", it) } ?: tr("Tu teléfono no informa la intensidad de la señal."),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                tr("Consejo: mientras más cerca estés del router, mejor señal y menos milisegundos. Camina con el teléfono por tu casa y mira cómo cambian la línea naranja y estas barras: así encuentras los lugares con mala cobertura."),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun Legend(color: Color, label: String, dashed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(22.dp).height(10.dp)) {
            drawLine(
                color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                strokeWidth = 3.dp.toPx(),
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/** Redondea hacia arriba a una escala "bonita" (50, 100, 200, 500 ms…) para leer el eje fácilmente. */
private fun niceScale(maxMs: Long): Long {
    val steps = listOf(20L, 50L, 100L, 150L, 200L, 300L, 500L, 750L, 1000L, 1500L, 2000L, 3000L, 5000L)
    val target = (maxMs * 1.1).toLong()
    return steps.firstOrNull { it >= target } ?: (((target / 1000) + 1) * 1000)
}

@Composable
private fun LatencyChart(samples: List<LatencySample>, routerColor: Color, netColor: Color) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val lost = StatusColors.bad
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val maxSample = samples.flatMap { listOfNotNull(it.router, it.internet) }.maxOrNull() ?: 0L
    val scale = niceScale(maxSample.coerceAtLeast(10L))
    val ticks = 4
    val chartHeight = 180.dp
    Column {
        Text("ms", style = MaterialTheme.typography.labelSmall, color = labelColor)
        Row {
            // Eje vertical: milisegundos
            Column(
                Modifier.height(chartHeight).width(44.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                for (i in ticks downTo 0) {
                    Text(
                        "${scale * i / ticks}",
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Canvas(Modifier.weight(1f).height(chartHeight).padding(vertical = 7.dp)) {
                val w = size.width
                val h = size.height
                for (i in 0..ticks) {
                    val y = h * i / ticks.toFloat()
                    drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = if (i == ticks) 2f else 1f)
                }
                val step = w / (LatencyViewModel.MAX_SAMPLES - 1)
                // Los datos se dibujan pegados a la derecha: lo más nuevo siempre está en "ahora".
                val offset = (LatencyViewModel.MAX_SAMPLES - samples.size).coerceAtLeast(0)
                fun series(values: List<Long?>, color: Color, dashed: Boolean) {
                    val path = Path()
                    var started = false
                    values.forEachIndexed { i, v ->
                        val x = (i + offset) * step
                        if (v == null) {
                            // Sin respuesta: una "x" en la base del gráfico
                            val r = 3.dp.toPx()
                            val cy = h - r
                            drawLine(lost, Offset(x - r, cy - r), Offset(x + r, cy + r), strokeWidth = 2f)
                            drawLine(lost, Offset(x - r, cy + r), Offset(x + r, cy - r), strokeWidth = 2f)
                            started = false
                        } else {
                            val y = h - (v.coerceAtMost(scale).toFloat() / scale) * h
                            if (!started) path.moveTo(x, y) else path.lineTo(x, y)
                            started = true
                        }
                    }
                    drawPath(
                        path, color,
                        style = Stroke(
                            width = 2.5.dp.toPx(),
                            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null,
                        ),
                    )
                }
                series(samples.map { it.internet }, netColor, dashed = true)
                series(samples.map { it.router }, routerColor, dashed = false)
            }
        }
        // Eje horizontal: tiempo
        Row(Modifier.fillMaxWidth().padding(start = 50.dp, top = 2.dp)) {
            Text(tr("hace 60 s"), style = MaterialTheme.typography.labelSmall, color = labelColor)
            Spacer(Modifier.weight(1f))
            Text(tr("hace 30 s"), style = MaterialTheme.typography.labelSmall, color = labelColor)
            Spacer(Modifier.weight(1f))
            Text(tr("ahora"), style = MaterialTheme.typography.labelSmall, color = labelColor)
        }
        Text(
            tr("× roja = no respondió (paquete perdido)"),
            style = MaterialTheme.typography.labelSmall,
            color = lost,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun StatsCard(title: String, st: LatencyStats?, color: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = color)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat(tr("Promedio"), latencyText(st?.avg))
                Stat(tr("Mín / máx"), st?.let { if (it.min == null) "—" else "${it.min}/${it.max}" } ?: "—")
                Stat(tr("Variación"), latencyText(st?.jitter))
                Stat(tr("Perdidos"), st?.let { "${it.lossPercent}%" } ?: "—")
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ============================================================== Mis redes (RF-33)

@Composable
fun NetworksScreen(vm: NetworksViewModel, onOpenNetwork: (String) -> Unit, onScanCurrent: () -> Unit, onBack: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val isPremium = rememberIsPremium()
    var renaming by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(Unit) { vm.load() }

    ScreenScaffold(title = tr("Mis redes"), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                Text(
                    tr("Cada red guarda su propio inventario. La app detecta sola cuando te conectas a otro WiFi; escanéalo y ponle un nombre para reconocerlo (\"Casa\", \"Router 2\"…)."),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            if (s.currentKey != null && s.networks.none { it.key == s.currentKey }) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("Red nueva detectada"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Tag(tr("Conectado"), StatusColors.good)
                            }
                            Text(
                                "${s.currentSubnet}" + (s.currentGateway?.let { tr(" · Router %1\$s", it) } ?: "") +
                                    tr("\nAún no la has escaneado, por eso no está guardada."),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 6.dp),
                            )
                            Button(onClick = onScanCurrent) { Text(tr("Escanear y guardar esta red")) }
                        }
                    }
                }
            }
            if (s.networks.isEmpty() && !s.loading) {
                item { Text(tr("Aún no hay redes guardadas. Escanea tu red para empezar.")) }
            }
            items(s.networks, key = { it.key }) { n ->
                val isCurrent = n.key == s.currentKey
                val locked = !isPremium && !isCurrent
                ListItem(
                    modifier = Modifier.clickable(enabled = !locked) { onOpenNetwork(n.key) },
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(n.displayName)
                            if (isCurrent) {
                                Spacer(Modifier.width(8.dp))
                                Tag(tr("Conectado"), StatusColors.good)
                            }
                        }
                    },
                    supportingContent = {
                        Text(
                            "${n.subnet}" + (n.gateway?.let { tr(" · Router %1\$s", it) } ?: "") +
                                tr("\n%1\$s equipos · visto %2\$s", n.deviceCount, relativeTime(n.lastSeen)),
                        )
                    },
                    trailingContent = {
                        if (isPremium || isCurrent) {
                            IconButton(onClick = { renaming = n.key to (n.name ?: "") }) {
                                Icon(Icons.Outlined.Edit, contentDescription = tr("Cambiar nombre"))
                            }
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(tr("¿Tienes dos routers?"), style = MaterialTheme.typography.titleSmall)
                        Text(
                            tr("• Si cada router reparte su propia red (por ejemplo, uno da direcciones 192.168.1.x y el otro 192.168.0.x), aparecerán aquí como dos redes distintas. Conéctate al WiFi de cada uno y escanéalo."),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            tr("• Si el segundo es un repetidor, un sistema mesh o un router en \"modo puente\", comparte la misma red: no aparece como otra red, sino como un equipo más (tipo \"Equipo de red\" o \"Router\") dentro de la red principal. Ábrela para ver los equipos que reparten tu WiFi."),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            tr("Para escanear una red tienes que estar conectado a ella: desde fuera no se pueden ver sus equipos."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (!isPremium && s.networks.any { it.key != s.currentKey }) {
                item {
                    LockedCard(
                        tr("Ver tus otras redes"),
                        tr("Con Premium puedes revisar el inventario y el historial de todas tus redes guardadas, aunque no estés conectado a ellas."),
                        Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }

    renaming?.let { (key, current) ->
        var text by remember(key) { mutableStateOf(current) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(tr("Nombre de la red")) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(30) },
                    singleLine = true,
                    placeholder = { Text(tr("Ej. Casa")) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.rename(key, text)
                    renaming = null
                }) { Text(tr("Guardar")) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(tr("Cancelar")) } },
        )
    }
}

@Composable
fun NetworkDetailScreen(vm: NetworksViewModel, key: String, onOpenDevice: (Long) -> Unit, onBack: () -> Unit) {
    val d by vm.detail.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(key) { vm.open(key) }

    ScreenScaffold(
        title = d.profile?.displayName ?: tr("Red"),
        onBack = onBack,
        actions = {
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Outlined.Delete, contentDescription = tr("Olvidar esta red"))
            }
        },
    ) { padding ->
        val p = d.profile
        if (p == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@ScreenScaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                Text(
                    "${p.subnet}" + (p.gateway?.let { tr(" · Router %1\$s", it) } ?: "") +
                        tr("\nÚltimo escaneo: ") + (d.lastScan?.let { relativeTime(it.startedAt) + tr(" · %1\$s equipos", it.found) } ?: tr("nunca")),
                    style = MaterialTheme.typography.bodyMedium,
                )
                val distributors = d.devices.filter {
                    it.isGateway || it.type == DeviceType.ROUTER || it.type == DeviceType.NETWORK
                }
                if (distributors.isNotEmpty()) {
                    SectionTitle(tr("Equipos que reparten esta red (%1\$s)", distributors.size))
                    Text(
                        if (distributors.size > 1)
                            tr("Esta red tiene %1\$s equipos de red: el router principal y repetidores, mesh u otro router en modo puente. Todos comparten la misma red.", distributors.size)
                        else tr("Solo encontramos el router principal."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    distributors.forEach { dev ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenDevice(dev.id) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DeviceAvatar(dev.type, Presence.ONLINE, iconKey = dev.customIcon)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(dev.displayName, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    dev.ip + " · " + if (dev.isGateway) tr("router principal (salida a Internet)") else tr("repetidor / segundo router"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                SectionTitle(tr("Equipos guardados (%1\$s)", d.devices.size))
            }
            items(d.devices, key = { it.id }) { dev ->
                val online = d.lastScan != null && dev.lastScanId == d.lastScan?.id
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenDevice(dev.id) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DeviceAvatar(dev.type, if (online) Presence.ONLINE else Presence.OFFLINE, iconKey = dev.customIcon)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(dev.displayName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            dev.ip + " · " + (if (online) tr("en el último escaneo") else tr("visto %1\$s", relativeTime(dev.lastSeen))),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("¿Olvidar esta red?")) },
            text = { Text(tr("Se borrarán sus dispositivos, nombres e historial. No se puede deshacer.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(key)
                    onBack()
                }) { Text(tr("Olvidar")) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancelar")) } },
        )
    }
}

// ============================================================== Exportar (RF-34)

@Composable
fun ExportScreen(vm: ExportViewModel, onBack: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val isPremium = rememberIsPremium()
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { vm.export(it, pdf = true) }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { vm.export(it, pdf = false) }
    }

    ScreenScaffold(title = tr("Exportar reporte"), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                tr("Guarda el inventario de tu red, su revisión de seguridad y el historial en un archivo para imprimirlo, enviarlo a quien te da soporte técnico o abrirlo en Excel."),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!isPremium) {
                LockedCard(tr("Reportes PDF y CSV"), tr("Exporta el inventario, los puertos, la revisión de seguridad y el historial."))
                return@Column
            }
            if (s.networks.isEmpty()) {
                Text(tr("Aún no hay redes guardadas. Escanea tu red primero."))
                return@Column
            }
            Text(tr("Red"), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.networks.forEach { n ->
                    FilterChip(selected = s.selectedKey == n.key, onClick = { vm.select(n.key) }, label = { Text(n.displayName) })
                }
            }
            Button(
                onClick = { pdfLauncher.launch(vm.suggestedName("pdf")) },
                enabled = !s.working,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(tr("Guardar reporte en PDF"))
            }
            OutlinedButton(
                onClick = { csvLauncher.launch(vm.suggestedName("csv")) },
                enabled = !s.working,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.TableChart, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(tr("Guardar tabla CSV (Excel)"))
            }
            if (s.working) RadarStatus(tr("Preparando el reporte…"))
        }
    }

    s.message?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::clearMessage,
            title = { Text(tr("Exportar reporte")) },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = vm::clearMessage) { Text(tr("Entendido")) } },
        )
    }
}
