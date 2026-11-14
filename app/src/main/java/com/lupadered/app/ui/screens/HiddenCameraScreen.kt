package com.lupadered.app.ui.screens

import com.lupadered.app.i18n.tr
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lupadered.app.ui.HiddenCameraViewModel
import com.lupadered.app.ui.ScreenScaffold
import com.lupadered.app.ui.components.EmptyState
import com.lupadered.app.ui.components.LockedCard
import com.lupadered.app.ui.components.SectionTitle
import com.lupadered.app.ui.components.rememberIsPremium
import com.lupadered.app.ui.theme.StatusColors
import androidx.compose.material.icons.outlined.WifiOff

/**
 * Detector de cámaras ocultas (Premium, RF-48): combina lo que ya sabemos de
 * la red (cámaras IP conocidas por puerto/fabricante) con un medidor de campo
 * magnético para rastrear lentes con imanes o circuitos ocultos.
 */
@Composable
fun HiddenCameraScreen(vm: HiddenCameraViewModel, onOpenDevice: (Long) -> Unit, onBack: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val isPremium = rememberIsPremium()
    val context = LocalContext.current

    ScreenScaffold(title = tr("Detector de cámaras ocultas"), onBack = onBack) { padding ->
        if (!isPremium) {
            Column(Modifier.padding(padding).padding(16.dp)) {
                LockedCard(
                    tr("Detector de cámaras ocultas"),
                    tr("Buscamos cámaras IP conectadas a tu red y te damos un medidor de campo magnético para rastrear lentes escondidas en objetos."),
                )
            }
            return@ScreenScaffold
        }

        DisposableEffect(Unit) {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val sensor = sm?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            if (sm == null || sensor == null) {
                vm.setSensorAvailable(false)
                onDispose {}
            } else {
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        vm.onMagnetometerReading(event.values[0], event.values[1], event.values[2])
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
                onDispose { sm.unregisterListener(listener) }
            }
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SectionTitle(tr("Cámaras en tu red")) }
            if (s.loading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            } else if (s.noNetwork) {
                item {
                    EmptyState(Icons.Outlined.WifiOff, tr("No estás conectado a un WiFi"), tr("Conéctate a tu red para buscar cámaras."))
                }
            } else if (s.suspects.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Text(
                            tr("No encontramos cámaras ni grabadores en el último escaneo de tu red."),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            } else {
                items(s.suspects) { cam ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Videocam, contentDescription = null, tint = StatusColors.warn)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(cam.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    tr("%1\$s · indicio: %2\$s", cam.ip, cam.reason),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { onOpenDevice(cam.deviceId) }) { Text(tr("Ver")) }
                        }
                    }
                }
            }
            item { TextButton(onClick = vm::refresh) { Text(tr("Actualizar con el último escaneo")) } }

            item { Spacer(Modifier.height(8.dp)) }
            item { SectionTitle(tr("Medidor de campo magnético")) }
            item {
                if (!s.sensorAvailable) {
                    Text(
                        tr("Tu teléfono no tiene sensor magnético."),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    MagnetometerCard(magnitude = s.magnitude, baseline = s.baseline, onResetBaseline = vm::resetBaseline)
                }
            }
            item {
                Text(
                    tr("Mueve el teléfono despacio cerca de relojes, detectores de humo, enchufes, plantas o juguetes. Un aumento fuerte y repentino puede delatar un imán, una batería o un circuito escondido. No es una prueba definitiva: úsalo junto con una inspección visual."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MagnetometerCard(magnitude: Float, baseline: Float?, onResetBaseline: () -> Unit) {
    val base = baseline ?: magnitude
    val delta = magnitude - base
    val ratio = (magnitude / (base.takeIf { it > 1f } ?: 1f)).coerceIn(0f, 4f)
    val color = when {
        ratio >= 2.5f -> StatusColors.bad
        ratio >= 1.6f -> StatusColors.warn
        else -> StatusColors.good
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Sensors, contentDescription = null, tint = color)
                Spacer(Modifier.width(8.dp))
                Text(
                    tr("%1\$.1f µT", magnitude),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    tr("(base %1\$.1f µT)", base),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(Modifier.fillMaxWidth().height(10.dp)) {
                LinearProgressIndicator(
                    progress = { (ratio / 4f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = color,
                )
            }
            Text(
                when {
                    ratio >= 2.5f -> tr("Campo magnético muy por encima de lo normal aquí. Revisa de cerca.")
                    ratio >= 1.6f -> tr("Algo más alto de lo normal. Sigue acercando el teléfono.")
                    else -> tr("Sin anomalías por ahora.")
                },
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onResetBaseline) { Text(tr("Usar esta lectura como línea base")) }
        }
    }
}
