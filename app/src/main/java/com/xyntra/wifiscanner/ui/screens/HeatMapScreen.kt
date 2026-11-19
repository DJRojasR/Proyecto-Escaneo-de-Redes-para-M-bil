package com.xyntra.wifiscanner.ui.screens

import android.graphics.Bitmap
import com.xyntra.wifiscanner.ui.heat.HeatTexts
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.xyntra.wifiscanner.ui.heat.HeatGeometry
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.produceState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Slider
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xyntra.wifiscanner.data.HeatPoint
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WifiFind
import com.xyntra.wifiscanner.i18n.tr
import com.xyntra.wifiscanner.ui.FloorSummary
import com.xyntra.wifiscanner.ui.HeatMapState
import com.xyntra.wifiscanner.ui.HeatMapViewModel
import com.xyntra.wifiscanner.ui.ScreenScaffold
import com.xyntra.wifiscanner.ui.components.LockedCard
import com.xyntra.wifiscanner.ui.components.rememberIsPremium
import com.xyntra.wifiscanner.ui.components.relativeTime
import com.xyntra.wifiscanner.ui.theme.StatusColors
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


@Composable
fun HeatMapScreen(vm: HeatMapViewModel, onOpenAr: (floorId: Long, x: Float, y: Float) -> Unit, onBack: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val isPremium = rememberIsPremium()
    var menuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<HeatDialog?>(null) }
    val context = LocalContext.current
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) vm.saveImage(uri)
    }
    val onSave = { saveLauncher.launch(tr("mapa-wifi.png")) }
    val onShare = {
        vm.shareImage { uri ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, tr("Compartir mapa")))
        }
    }

    if (isPremium) {
        DisposableEffect(Unit) {
            vm.start()
            onDispose { vm.stop() }
        }
    }

    ScreenScaffold(
        title = tr("Mapa de calor WiFi"),
        onBack = onBack,
        actions = {
            if (isPremium && s.selectedFloor != null) {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = tr("Más opciones"))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(tr("Renombrar este piso")) },
                        onClick = { menuOpen = false; dialog = HeatDialog.RENAME },
                    )
                    DropdownMenuItem(
                        text = { Text(tr("Tamaño de la casa")) },
                        onClick = { menuOpen = false; dialog = HeatDialog.SIZE },
                    )
                    DropdownMenuItem(
                        text = { Text(if (s.selectedFloor?.routerX == null) tr("Marcar dónde está el router") else tr("Mover el router")) },
                        onClick = { menuOpen = false; vm.startPlacingRouter() },
                    )
                    if (s.selectedFloor?.routerX != null) {
                        DropdownMenuItem(
                            text = { Text(tr("Quitar el router de este piso")) },
                            onClick = { menuOpen = false; vm.removeRouter() },
                        )
                    }
                    if (s.points.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text(tr("Borrar las mediciones de este piso")) },
                            onClick = { menuOpen = false; dialog = HeatDialog.CLEAR },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(tr("Eliminar este piso")) },
                        onClick = { menuOpen = false; dialog = HeatDialog.DELETE },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!isPremium) {
                LockedCard(
                    tr("Mapa de calor WiFi"),
                    tr("Mide la señal en cada habitación y en cada piso, y descubre las zonas muertas de tu casa y dónde conviene poner el router o un repetidor."),
                )
                return@Column
            }
            if (s.noWifi) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WifiOff, contentDescription = null, tint = StatusColors.warn)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Conéctate al WiFi de tu casa para hacer el mapa de calor."), color = StatusColors.warn)
                }
                return@Column
            }
            if (s.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                return@Column
            }

            LiveSignalCard(s.liveRssi)
            if (s.roaming) RoamingWarning()
            Steps(s)

            // Los botones van arriba del plano para que se vean sin bajar
            when {
                s.placingRouter ->
                    OutlinedButton(onClick = vm::cancelPlacingRouter, modifier = Modifier.fillMaxWidth()) { Text(tr("Cancelar")) }
                s.measuring -> {
                    Text(tr("Quédate quieto, estamos midiendo…"), style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(progress = { s.measureProgress }, modifier = Modifier.fillMaxWidth())
                }
                s.walking -> WalkingCard(s, onStop = vm::stopWalk)
                else -> StartButtons(
                    s,
                    onAr = { s.selectedFloorId?.let { id -> onOpenAr(id, s.cursorX ?: 0.5f, s.cursorY ?: 0.5f) } },
                    onWalk = vm::startWalk,
                    onMeasure = vm::measureHere,
                )
            }
            s.message?.let { Text(it, color = StatusColors.warn, style = MaterialTheme.typography.bodyMedium) }

            // Con muchos puntos (realidad aumentada) se ven mejor solo los colores
            var showPoints by remember(s.selectedFloorId) { mutableStateOf<Boolean?>(null) }
            val pointsVisible = showPoints ?: (s.points.size <= 25)
            HeatPlan(s, pointsVisible, onTap = vm::tap)
            Legend()
            if (s.points.isNotEmpty()) {
                FilterChip(
                    selected = pointsVisible,
                    onClick = { showPoints = !pointsVisible },
                    label = { Text(tr("Mostrar los puntos medidos")) },
                )
            }

            s.selectedPoint?.let { p -> PointCard(p, onDelete = vm::deleteSelectedPoint) }

            VerdictCard(s)
            if (s.points.isNotEmpty() && !s.walking) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Compartir mapa"))
                    }
                    OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Guardar imagen"))
                    }
                }
                TextButton(onClick = { dialog = HeatDialog.CLEAR }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Borrar y empezar de cero"))
                }
            }
            // Los pisos y la ayuda van al final; arriba queda el mapa, que es lo importante
            FloorChips(s, onSelect = vm::selectFloor, onAdd = { dialog = HeatDialog.ADD })
            Text(
                tr("Camina por tu casa con el teléfono y mide la señal en cada lugar. Verás en colores dónde llega mejor el WiFi y dónde casi no llega."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (s.floors.size > 1) FloorsComparison(s)
            FloorsTip()
        }
    }

    when (dialog) {
        HeatDialog.ADD -> NameDialog(
            title = tr("Agregar piso"),
            initial = tr("Piso %1\$s", s.floors.size + 1),
            onDismiss = { dialog = null },
            onSave = { vm.addFloor(it); dialog = null },
        )
        HeatDialog.SIZE -> SizeDialog(
            initial = s.selectedFloor?.widthM ?: 12f,
            onDismiss = { dialog = null },
            onSave = { vm.setFloorWidth(it); dialog = null },
        )
        HeatDialog.RENAME -> NameDialog(
            title = tr("Renombrar este piso"),
            initial = s.selectedFloor?.name.orEmpty(),
            onDismiss = { dialog = null },
            onSave = { vm.renameFloor(it); dialog = null },
        )
        HeatDialog.CLEAR -> ConfirmDialog(
            title = tr("¿Borrar las mediciones de este piso?"),
            text = tr("Se borrarán los %1\$s puntos medidos en \"%2\$s\". El piso se queda para volver a medir.", s.points.size, s.selectedFloor?.name.orEmpty()),
            onDismiss = { dialog = null },
            onConfirm = { vm.clearFloor(); vm.resetRoamingWarning(); dialog = null },
        )
        HeatDialog.DELETE -> ConfirmDialog(
            title = tr("¿Eliminar este piso?"),
            text = tr("Se eliminará \"%1\$s\" con todas sus mediciones. No se puede deshacer.", s.selectedFloor?.name.orEmpty()),
            onDismiss = { dialog = null },
            onConfirm = { vm.deleteFloor(); dialog = null },
        )
        null -> Unit
    }

    // Si te conectas a otra red mientras mides, preguntamos con cuál seguir (así no se
    // mezclan dos mapas distintos en el mismo plano).
    s.changedNetworkName?.let { newName ->
        AlertDialog(
            onDismissRequest = { vm.keepCurrentNetwork() },
            icon = { Icon(Icons.Outlined.WifiFind, contentDescription = null) },
            title = { Text(tr("Te conectaste a otra red")) },
            text = {
                Text(tr("Ahora estás en \"%1\$s\". ¿Con cuál red quieres hacer este mapa? Si mezclas dos redes, el mapa no sirve.", newName))
            },
            confirmButton = {
                TextButton(onClick = { vm.useCurrentNetwork() }) { Text(tr("Usar \"%1\$s\"", newName)) }
            },
            dismissButton = {
                TextButton(onClick = { vm.keepCurrentNetwork() }) { Text(tr("Seguir con el mapa actual")) }
            },
        )
    }
}

private enum class HeatDialog { ADD, RENAME, CLEAR, DELETE, SIZE }

// ---------------------------------------------------------------- colores y textos de la señal

/** Puntos de color de la escala: verde (excelente) → amarillo → rojo (débil). */
private val STOPS = listOf(
    -50 to Color(0xFF1B9E4B),
    -60 to Color(0xFF7CC242),
    -67 to Color(0xFFF2C81B),
    -75 to Color(0xFFF28C1B),
    -85 to Color(0xFFD7301F),
)

internal fun signalColor(dbm: Int): Color {
    if (dbm >= STOPS.first().first) return STOPS.first().second
    if (dbm <= STOPS.last().first) return STOPS.last().second
    for (i in 0 until STOPS.size - 1) {
        val (a, ca) = STOPS[i]
        val (b, cb) = STOPS[i + 1]
        if (dbm in b..a) {
            val t = (a - dbm).toFloat() / (a - b)
            return Color(
                red = ca.red + (cb.red - ca.red) * t,
                green = ca.green + (cb.green - ca.green) * t,
                blue = ca.blue + (cb.blue - ca.blue) * t,
            )
        }
    }
    return STOPS.last().second
}

internal fun signalLabel(dbm: Int): String = when {
    dbm >= -55 -> tr("Excelente")
    dbm >= -67 -> tr("Buena")
    dbm >= -75 -> tr("Regular")
    else -> tr("Débil")
}

// ---------------------------------------------------------------- cálculo del mapa

// ---------------------------------------------------------------- piezas de la pantalla

@Composable
private fun FloorChips(s: HeatMapState, onSelect: (Long) -> Unit, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        s.floors.forEach { f ->
            FilterChip(
                selected = f.id == s.selectedFloorId,
                onClick = { onSelect(f.id) },
                label = { Text(f.name) },
            )
        }
        AssistChip(
            onClick = onAdd,
            label = { Text(tr("Agregar piso")) },
            leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
    }
}

@Composable
private fun LiveSignalCard(rssi: Int?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(18.dp).clip(CircleShape)
                    .background(if (rssi == null) MaterialTheme.colorScheme.outline else signalColor(rssi))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("Señal donde estás ahora"), style = MaterialTheme.typography.labelMedium)
                Text(
                    if (rssi == null) tr("Leyendo…") else signalLabel(rssi),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (rssi != null) {
                    Text(
                        signalDetail(rssi),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Línea secundaria en chico: solo el dBm, para quien lo quiera. En el mapa no
 * mostramos los Mbps del enlace WiFi porque confundían con la prueba de velocidad
 * (esa cifra es la capacidad del WiFi, no la velocidad real de Internet).
 */
internal fun signalDetail(dbm: Int): String = tr("%1\$s dBm", dbm)

@Composable
private fun Steps(s: HeatMapState) {
    val text = when {
        s.placingRouter -> tr("Toca en el plano el lugar donde está tu router.")
        s.walking -> tr("Camina despacio por toda la casa: el mapa se pinta solo. Si el punto azul se desvía, toca en el plano dónde estás de verdad.")
        s.cursorX == null ->
            tr("Toca un botón y camina despacio por tu casa: el mapa se pinta solo. Si quieres, antes toca en el plano dónde estás parado; si no, empezamos en el centro.")
        else -> tr("Mira hacia la parte de arriba del plano y empieza el recorrido. También puedes medir solo este punto.")
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun HeatPlan(s: HeatMapState, showPoints: Boolean, onTap: (Float, Float, Long?) -> Unit) {
    val floor = s.selectedFloor
    val widthM = floor?.widthM ?: 12f
    val heightM = HeatGeometry.heightOf(widthM)
    val points = s.points
    // El cálculo de las baldosas puede tardar con muchos puntos: lo hacemos fuera del hilo principal
    val meters = remember(points, widthM) { HeatGeometry.toMeters(points, widthM) }
    val cells by produceState(emptyList<HeatGeometry.Cell>(), meters) {
        value = withContext(Dispatchers.Default) { HeatGeometry.cells(meters) }
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val hitRadiusPx = with(density) { 22.dp.toPx() }
    val outline = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainerLowest
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val routerLabel = tr("Router")
    val bestLabel = tr("Mejor señal")
    val worstLabel = tr("Más débil")
    val walker = Color(0xFF1A73E8)
    val shape = RoundedCornerShape(12.dp)

    // Todo lo que debe entrar en la vista (en metros), aunque se salga del recuadro de la casa
    val extra = buildList {
        val rx = floor?.routerX
        val ry = floor?.routerY
        if (rx != null && ry != null) add(rx * widthM to ry * heightM)
        val wx = s.walkX
        val wy = s.walkY
        if (s.walking && wx != null && wy != null) add(wx * widthM to wy * heightM)
        val cx = s.cursorX
        val cy = s.cursorY
        if (cx != null && cy != null) add(cx * widthM to cy * heightM)
        meters.forEach { add(it.x to it.y) }
    }
    val bounds = HeatGeometry.bounds(widthM, cells, extra)

    // Zoom y desplazamiento con los dedos (se reinician al cambiar de piso)
    var zoom by remember(s.selectedFloorId) { mutableFloatStateOf(1f) }
    var pan by remember(s.selectedFloorId) { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(HeatGeometry.PLAN_ASPECT)
            .clip(shape)
            .background(surface)
            .border(1.dp, outline, shape)
    ) {
    Canvas(
        Modifier
            .fillMaxSize()
            // Dos dedos: acercar y mover. Ya acercado, un dedo también mueve el mapa;
            // sin acercar, un dedo deja que la pantalla haga scroll como siempre.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers >= 2 || (zoom > 1.01f && fingers == 1)) {
                            val z = event.calculateZoom()
                            val p = event.calculatePan()
                            if (z != 1f || p != Offset.Zero) {
                                zoom = (zoom * z).coerceIn(1f, 8f)
                                pan = if (zoom <= 1.01f) Offset.Zero else pan + p
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(points, s.placingRouter, bounds, showPoints, zoom, pan) {
                detectTapGestures(
                    // Doble toque: acercar ahí, o volver a ver todo
                    onDoubleTap = { pos ->
                        if (zoom > 1.01f) {
                            zoom = 1f
                            pan = Offset.Zero
                        } else {
                            zoom = 2.5f
                            pan = Offset((size.width / 2f - pos.x) * 2.5f, (size.height / 2f - pos.y) * 2.5f)
                        }
                    },
                ) { pos ->
                    val view = HeatGeometry.View(bounds, size.width.toFloat(), size.height.toFloat(), zoom, pan.x, pan.y)
                    val hit = if (s.placingRouter || !showPoints) null else meters.zip(points)
                        .map { (m, p) -> p to hypot(view.sx(m.x) - pos.x, view.sy(m.y) - pos.y) }
                        .filter { it.second <= hitRadiusPx }
                        .minByOrNull { it.second }?.first?.id
                    // De píxeles a coordenadas del plano (puede quedar fuera de 0..1, y está bien)
                    onTap(view.mx(pos.x) / widthM, view.my(pos.y) / heightM, hit)
                }
            }
    ) {
        val view = HeatGeometry.View(bounds, size.width, size.height, zoom, pan.x, pan.y)

        // Cuadrícula de 1 m (o más grande si la casa es muy grande) para dar escala
        val step = when {
            view.scale >= 18.dp.toPx() -> 1f
            view.scale >= 6.dp.toPx() -> 2f
            else -> 5f
        }
        val grid = outline.copy(alpha = 0.35f)
        var gx = kotlin.math.floor(bounds.minX / step) * step
        while (gx <= bounds.maxX) {
            drawLine(grid, Offset(view.sx(gx), 0f), Offset(view.sx(gx), size.height), strokeWidth = 1f); gx += step
        }
        var gy = kotlin.math.floor(bounds.minY / step) * step
        while (gy <= bounds.maxY) {
            drawLine(grid, Offset(0f, view.sy(gy)), Offset(size.width, view.sy(gy)), strokeWidth = 1f); gy += step
        }

        // Contorno de la casa (el recuadro original), punteado
        drawRect(
            outline,
            topLeft = Offset(view.sx(0f), view.sy(0f)),
            size = Size(widthM * view.scale, heightM * view.scale),
            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))),
        )

        // Baldosas de colores, agrupadas por color para dibujar rápido
        val side = HeatGeometry.CELL_M * view.scale
        cells.groupBy { HeatGeometry.bin(it.rssi) }.forEach { (bin, list) ->
            val path = Path()
            for (c in list) {
                val x0 = view.sx(c.i * HeatGeometry.CELL_M)
                val y0 = view.sy(c.k * HeatGeometry.CELL_M)
                path.addRect(Rect(x0, y0, x0 + side + 0.5f, y0 + side + 0.5f))
            }
            drawPath(path, signalColor(bin).copy(alpha = 0.85f))
        }

        // Puntos medidos: con etiqueta si son pocos; si son muchos, solo un punto chico
        if (showPoints) {
            val labels = points.size <= 25
            meters.zip(points).forEach { (m, p) ->
                val c = Offset(view.sx(m.x), view.sy(m.y))
                val selected = p.id == s.selectedPointId
                if (selected) drawCircle(onSurface, radius = 15.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
                if (labels) {
                    drawCircle(Color.White, radius = 9.dp.toPx(), center = c)
                    drawCircle(signalColor(p.rssi), radius = 7.dp.toPx(), center = c)
                    val t = measurer.measure("${p.rssi}", TextStyle(color = onSurface, fontSize = 10.sp, fontWeight = FontWeight.SemiBold))
                    drawText(t, topLeft = Offset(c.x - t.size.width / 2f, c.y + 10.dp.toPx()))
                } else {
                    drawCircle(Color.White.copy(alpha = 0.9f), radius = 3.dp.toPx(), center = c)
                }
            }
        }

        // Dónde está la mejor y la peor señal
        // (según los colores del mapa, para que la etiqueta caiga en la zona que se ve verde o roja)
        if (meters.size >= 5 && !s.walking) {
            HeatGeometry.extremes(cells)?.let { (best, worst) ->
                pin(measurer, Offset(view.sx(best.x), view.sy(best.y)), "$bestLabel · ${best.rssi}", Color(0xFF1B9E4B))
                pin(measurer, Offset(view.sx(worst.x), view.sy(worst.y)), "$worstLabel · ${worst.rssi}", Color(0xFFD7301F))
            }
        }

        // Router
        val rx = floor?.routerX
        val ry = floor?.routerY
        if (rx != null && ry != null) {
            val c = Offset(view.sx(rx * widthM), view.sy(ry * heightM))
            drawCircle(Color.White, radius = 15.dp.toPx(), center = c)
            drawCircle(primary, radius = 12.dp.toPx(), center = c)
            val t = measurer.measure("R", TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold))
            drawText(t, topLeft = Offset(c.x - t.size.width / 2f, c.y - t.size.height / 2f))
            val l = measurer.measure(routerLabel, TextStyle(color = onSurface, fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
            drawText(l, topLeft = Offset((c.x - l.size.width / 2f).coerceIn(0f, max(0f, size.width - l.size.width)), c.y + 16.dp.toPx()))
        }

        // Modo caminata: dónde creemos que vas y hacia dónde miras
        val wx = s.walkX
        val wy = s.walkY
        if (s.walking && wx != null && wy != null) {
            val c = Offset(view.sx(wx * widthM), view.sy(wy * heightM))
            val dir = Offset(kotlin.math.sin(s.walkHeading), -kotlin.math.cos(s.walkHeading))
            drawLine(walker, c, c + dir * 26.dp.toPx(), strokeWidth = 3.dp.toPx())
            drawCircle(walker.copy(alpha = 0.2f), radius = 20.dp.toPx(), center = c)
            drawCircle(Color.White, radius = 9.dp.toPx(), center = c)
            drawCircle(walker, radius = 7.dp.toPx(), center = c)
        }

        // Dónde dice el usuario que está
        val cx = s.cursorX
        val cy = s.cursorY
        if (cx != null && cy != null && !s.placingRouter) {
            val c = Offset(view.sx(cx * widthM), view.sy(cy * heightM))
            drawCircle(primary.copy(alpha = 0.18f), radius = 22.dp.toPx(), center = c)
            drawCircle(
                primary, radius = 22.dp.toPx(), center = c,
                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
            )
            drawLine(primary, Offset(c.x - 8.dp.toPx(), c.y), Offset(c.x + 8.dp.toPx(), c.y), strokeWidth = 2.dp.toPx())
            drawLine(primary, Offset(c.x, c.y - 8.dp.toPx()), Offset(c.x, c.y + 8.dp.toPx()), strokeWidth = 2.dp.toPx())
        }

        // Escala
        val scaleLen = step * view.scale
        val sy0 = size.height - 14.dp.toPx()
        val sx0 = 12.dp.toPx()
        drawLine(onSurface, Offset(sx0, sy0), Offset(sx0 + scaleLen, sy0), strokeWidth = 2.dp.toPx())
        val st = measurer.measure("${step.roundToInt()} m", TextStyle(color = onSurface, fontSize = 10.sp))
        drawText(st, topLeft = Offset(sx0, sy0 - st.size.height - 2.dp.toPx()))
    }

    // Botones para acercar, alejar y volver a ver todo
    Column(
        Modifier.align(Alignment.TopEnd).padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MapButton(Icons.Outlined.Add, tr("Acercar")) { zoom = (zoom * 1.5f).coerceAtMost(8f) }
        MapButton(Icons.Outlined.Remove, tr("Alejar")) {
            zoom = (zoom / 1.5f).coerceAtLeast(1f)
            if (zoom <= 1.01f) pan = Offset.Zero else pan /= 1.5f
        }
        if (zoom > 1.01f || pan != Offset.Zero) {
            MapButton(Icons.Outlined.CenterFocusStrong, tr("Ver todo el mapa")) {
                zoom = 1f
                pan = Offset.Zero
            }
        }
    }
    }
}

@Composable
private fun MapButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(38.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
    }
}

/** Una etiqueta de color sobre un punto del mapa ("Mejor señal", "Más débil"). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.pin(
    measurer: androidx.compose.ui.text.TextMeasurer, at: Offset, text: String, color: Color,
) {
    drawCircle(Color.White, radius = 8.dp.toPx(), center = at)
    drawCircle(color, radius = 5.dp.toPx(), center = at)
    val t = measurer.measure(text, TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold))
    val padH = 8.dp.toPx()
    val w = t.size.width + padH * 2
    val h = t.size.height + 4.dp.toPx()
    val left = (at.x - w / 2f).coerceIn(0f, max(0f, size.width - w))
    val top = (at.y - h - 10.dp.toPx()).coerceAtLeast(0f)
    drawRoundRect(color, topLeft = Offset(left, top), size = Size(w, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f))
    drawText(t, topLeft = Offset(left + padH, top + 2.dp.toPx()))
}

@Composable
private fun Legend() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
            drawRect(
                Brush.horizontalGradient(listOf(-85, -75, -67, -60, -50).map { signalColor(it) }),
                size = Size(size.width, size.height),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(tr("Débil"), tr("Regular"), tr("Buena"), tr("Excelente")).forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PointCard(p: HeatPoint, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(signalColor(p.rssi)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(signalLabel(p.rssi), style = MaterialTheme.typography.titleSmall)
                Text(
                    signalDetail(p.rssi) +
                        (p.pingMs?.let { " · " + tr("ping %1\$s ms", it) } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    tr("Medido %1\$s", relativeTime(p.at)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = tr("Borrar este punto")) }
        }
    }
}

@Composable
private fun VerdictCard(s: HeatMapState) {
    val summary = HeatTexts.summary(s.points)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            summary?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
            Text(HeatTexts.verdict(s.points), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FloorsComparison(s: HeatMapState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Comparación de pisos"), style = MaterialTheme.typography.titleSmall)
            s.floors.forEach { f ->
                val sum: FloorSummary? = s.summaries.firstOrNull { it.floorId == f.id }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(12.dp).clip(CircleShape)
                            .background(sum?.avg?.let { signalColor(it) } ?: MaterialTheme.colorScheme.outline)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(f.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (f.routerX != null) {
                        Icon(Icons.Outlined.Router, contentDescription = tr("Aquí está el router"), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    val avg = sum?.avg
                    Text(
                        if (avg == null) tr("sin medir") else signalLabel(avg),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val weakest = s.summaries.filter { it.avg != null && it.count >= 3 }.minByOrNull { it.avg ?: 0 }
            val weakestAvg = weakest?.avg
            val weakestFloor = s.floors.firstOrNull { it.id == weakest?.floorId }
            if (weakestAvg != null && weakestAvg <= -70 && weakestFloor != null) {
                Text(
                    tr("\"%1\$s\" es el piso con peor señal. Un repetidor o un nodo mesh en ese piso, cerca de la escalera, suele resolverlo.", weakestFloor.name),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun FloorsTip() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tr("¿Tu casa tiene varios pisos?"), style = MaterialTheme.typography.titleSmall)
            Text(
                tr("Crea un piso para cada nivel con \"Agregar piso\" y mide en cada uno. Marca en qué piso y en qué lugar está el router (menú ⋮). El techo de concreto frena mucho la señal, así que es normal que el piso del router salga más verde. Si todo un piso sale naranja o rojo, pon ahí un repetidor o un nodo mesh, de preferencia cerca de la escalera, donde todavía llega buena señal."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    singleLine = true,
                    label = { Text(tr("Nombre del piso")) },
                    placeholder = { Text(tr("Ej. Primer piso, Sótano, Azotea")) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text(tr("Guardar")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar")) } },
    )
}

@Composable
private fun ConfirmDialog(title: String, text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(tr("Borrar")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar")) } },
    )
}

@Composable
private fun StartButtons(s: HeatMapState, onAr: () -> Unit, onWalk: () -> Unit, onMeasure: () -> Unit) {
    // El recorrido puede empezar sin elegir punto (empieza en el centro del plano);
    // medir un solo punto sí necesita saber dónde estás.
    val ready = true
    val pointReady = s.cursorX != null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (s.arSupported) {
            // Si el teléfono puede, la realidad aumentada es lo más preciso
            Button(onClick = onAr, enabled = ready, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.ViewInAr, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(tr("Recorrer con la cámara (realidad aumentada)"))
            }
            OutlinedButton(onClick = onWalk, enabled = ready, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Outlined.DirectionsWalk, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(tr("Recorrer caminando"))
            }
        } else {
            Button(onClick = onWalk, enabled = ready, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Outlined.DirectionsWalk, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(tr("Recorrer caminando"))
            }
        }
        TextButton(onClick = onMeasure, enabled = pointReady, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(tr("Medir solo este punto"))
        }
    }
}

@Composable
private fun WalkingCard(s: HeatMapState, onStop: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Recorrido en curso"), style = MaterialTheme.typography.titleSmall)
            Text(
                tr("%1\$s pasos · %2\$s puntos en este piso", s.steps, s.points.size),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text(tr("Terminar recorrido")) }
        }
    }
}

@Composable
private fun SizeDialog(initial: Float, onDismiss: () -> Unit, onSave: (Float) -> Unit) {
    var width by remember { mutableStateOf(initial.coerceIn(4f, 40f)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Tamaño de la casa")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    tr("¿Cuánto mide aproximadamente este piso de lado a lado (de izquierda a derecha del plano)? Lo usamos para convertir tus pasos en distancia."),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(tr("%1\$s metros", width.roundToInt()), style = MaterialTheme.typography.titleMedium)
                Slider(value = width, onValueChange = { width = it }, valueRange = 4f..40f, steps = 35)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(width.roundToInt().toFloat()) }) { Text(tr("Guardar")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar")) } },
    )
}

/**
 * Aviso cuando el teléfono saltó de un punto de acceso a otro (redes con repetidor
 * o mesh, varios routers con el mismo nombre). Las señales de puntos distintos no se
 * pueden comparar bien en un mismo mapa.
 */
@Composable
private fun RoamingWarning() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = StatusColors.warn.copy(alpha = 0.15f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Sync, contentDescription = null, tint = StatusColors.warn)
            Spacer(Modifier.width(10.dp))
            Text(
                tr("Tu teléfono cambió de punto de acceso (tienes un repetidor o red mesh). Las mediciones de distintos puntos no se comparan bien en un mismo mapa; para un mapa fiable, mide con un solo router encendido."),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
