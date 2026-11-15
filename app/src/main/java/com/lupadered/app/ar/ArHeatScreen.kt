package com.lupadered.app.ar

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.lupadered.app.i18n.tr
import com.google.ar.core.TrackingFailureReason
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.BackHandler
import com.lupadered.app.ui.screens.signalColor
import kotlin.math.hypot


/**
 * Mapa de calor con realidad aumentada: con la cámara se ve el piso real y,
 * mientras caminas, se va pintando el recorrido con el color de la señal.
 */
@Composable
fun ArHeatScreen(vm: ArHeatViewModel, floorId: Long, startX: Float, startY: Float, onDone: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val s by vm.state.collectAsStateWithLifecycle()

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var permissionDenied by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<Session?>(null) }
    var glView by remember { mutableStateOf<GLSurfaceView?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var installRequested by remember { mutableStateOf(false) }
    var config by remember { mutableStateOf<Config?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var torchUnsupported by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var finishing by remember { mutableStateOf(false) }

    // Al salir guardamos el recorrido ya corregido por las anclas
    val finish: () -> Unit = {
        if (!finishing) {
            finishing = true
            scope.launch {
                runCatching { vm.finish() }
                onDone()
            }
        }
    }
    BackHandler(onBack = finish)

    // Linterna: ARCore la enciende junto con la cámara (no todos los teléfonos lo permiten)
    val toggleTorch: () -> Unit = {
        val sess = session
        val cfg = config
        if (sess != null && cfg != null) {
            val want = !torchOn
            try {
                cfg.flashMode = if (want) Config.FlashMode.TORCH else Config.FlashMode.OFF
                sess.configure(cfg)
                torchOn = want
            } catch (_: Throwable) {
                runCatching { cfg.flashMode = Config.FlashMode.OFF }
                torchUnsupported = true
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        permissionDenied = !granted
    }

    LaunchedEffect(floorId) { vm.setup(floorId, startX, startY) }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    DisposableEffect(lifecycleOwner, hasPermission) {
        fun resume() {
            if (!hasPermission || activity == null || error != null) return
            if (session == null) {
                try {
                    when (ArCoreApk.getInstance().requestInstall(activity, !installRequested)) {
                        ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                            installRequested = true
                            return // Google Play instala ARCore y volvemos aquí al terminar
                        }
                        ArCoreApk.InstallStatus.INSTALLED -> Unit
                    }
                    session = Session(activity).also { sess ->
                        val cfg = Config(sess).apply {
                            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                            focusMode = Config.FocusMode.AUTO
                            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                            // Para saber si hay poca luz y avisar antes de que se pierda
                            lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
                        }
                        sess.configure(cfg)
                        config = cfg
                    }
                } catch (_: Throwable) {
                    error = tr("Este teléfono no pudo iniciar la realidad aumentada. Usa el modo caminata.")
                    return
                }
            }
            try {
                session?.resume()
                glView?.onResume()
            } catch (_: Throwable) {
                error = tr("No pudimos abrir la cámara. Cierra otras apps que la estén usando e inténtalo de nuevo.")
            }
            vm.start()
        }

        fun pause() {
            glView?.onPause()
            try { session?.pause() } catch (_: Throwable) { }
            vm.stop()
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resume()
                Lifecycle.Event.ON_PAUSE -> pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            pause()
            // Al salir de la pantalla liberamos la cámara
            try { session?.close() } catch (_: Throwable) { }
            session = null
            glView = null
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val sess = session
        if (sess != null && error == null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    GLSurfaceView(ctx).apply {
                        preserveEGLContextOnPause = true
                        setEGLContextClientVersion(2)
                        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                        setRenderer(ArCameraRenderer(sess, { displayRotation(activity) }, vm::onFrame))
                        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                        glView = this
                    }
                },
            )
            TrailOverlay(s)
        }

        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = finish,
                    modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Atrás"), tint = Color.White)
                }
                Spacer(Modifier.width(10.dp))
                SignalChip(s.liveRssi)
                Spacer(Modifier.weight(1f))
                if (session != null && !torchUnsupported) {
                    IconButton(
                        onClick = toggleTorch,
                        modifier = Modifier.clip(CircleShape)
                            .background(if (torchOn) Color(0xFFFFC107) else Color.Black.copy(alpha = 0.45f)),
                    ) {
                        Icon(
                            if (torchOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                            contentDescription = if (torchOn) tr("Apagar linterna") else tr("Encender linterna"),
                            tint = if (torchOn) Color.Black else Color.White,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            val f = s.frame
            val dark = f?.failure == TrackingFailureReason.INSUFFICIENT_LIGHT || (f?.light ?: 1f) < 0.12f
            val torchTip = if (!torchOn && !torchUnsupported) " " + tr("Toca la linterna de arriba a la derecha.") else ""
            val hint = when {
                permissionDenied -> tr("Para usar la realidad aumentada necesitamos permiso para usar la cámara. Si prefieres, usa el modo caminata.")
                error != null -> error
                session == null -> tr("Preparando la cámara…")
                f == null -> tr("Preparando la cámara…")
                !f.tracking -> when (f.failure) {
                    TrackingFailureReason.INSUFFICIENT_LIGHT ->
                        tr("Hay muy poca luz y la cámara no reconoce el lugar. Enciende las luces.") + torchTip
                    TrackingFailureReason.EXCESSIVE_MOTION ->
                        tr("Te moviste muy rápido. Quédate quieto un momento y luego camina y gira más despacio.")
                    TrackingFailureReason.INSUFFICIENT_FEATURES ->
                        tr("La cámara no encuentra detalles. Apunta a una parte del piso con muebles, alfombras o bordes: una pared o un piso liso no sirven.")
                    TrackingFailureReason.CAMERA_UNAVAILABLE ->
                        tr("Otra app está usando la cámara. Ciérrala y vuelve a intentarlo.")
                    else -> if (s.sampleCount == 0) {
                        tr("Mueve el teléfono despacio apuntando al piso para que reconozca el lugar.")
                    } else {
                        tr("Se perdió el lugar. Vuelve despacio a una zona que ya pintaste, apuntando al piso, para que lo reconozca.")
                    }
                } + (if (s.lostForMs > 8000) "\n" + tr("¿No lo logra? Puedes terminar y seguir con el modo caminata.") else "")
                dark -> tr("Está algo oscuro: el mapa podría desalinearse. Enciende más luces.") + torchTip
                s.sampleCount == 0 -> tr("¡Listo! Camina despacio por tu casa con la cámara apuntando un poco al piso.")
                else -> tr("Consejo: gira despacio y mantén el piso a la vista. Si el mapa se mueve un poco, se corrige solo al volver a un lugar conocido.")
            }
            hint?.let { Bubble(it) }

            Spacer(Modifier.weight(1f))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Legend()
                Text(
                    tr("%1\$s puntos guardados en el plano", s.saved),
                    color = Color.White, style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = finish, enabled = !finishing, modifier = Modifier.fillMaxWidth()) { Text(tr("Terminar recorrido")) }
                Text(
                    tr("Esta función usa los Servicios de Google Play para RA (ARCore), proporcionados por Google LLC y regidos por la Política de Privacidad de Google."),
                    color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/**
 * Pinta el mapa sobre el piso real: cada baldosa se proyecta con la cámara de
 * ARCore. Para dibujar rápido, juntamos las baldosas del mismo color en un solo trazo.
 */
@Composable
private fun TrailOverlay(s: ArHeatState) {
    Canvas(Modifier.fillMaxSize()) {
        val f = s.frame ?: return@Canvas
        if (!f.tracking) return@Canvas
        val floorY = f.floorY ?: (f.camY - 1.3f)
        val vp = FloatArray(16)
        Matrix.multiplyMM(vp, 0, f.projection, 0, f.view, 0)
        val inV = FloatArray(4)
        val out = FloatArray(4)
        fun project(x: Float, z: Float): Offset? {
            inV[0] = x; inV[1] = floorY; inV[2] = z; inV[3] = 1f
            Matrix.multiplyMV(out, 0, vp, 0, inV, 0)
            if (out[3] <= 0.05f) return null // detrás de la cámara
            return Offset((out[0] / out[3] + 1f) / 2f * size.width, (1f - out[1] / out[3]) / 2f * size.height)
        }
        // Grupos de 3 dBm: suficiente para que el degradado se vea suave
        val paths = HashMap<Int, Path>()
        for (c in s.cells) {
            val x0 = c.i * CELL_M
            val z0 = c.k * CELL_M
            if (hypot(x0 - f.camX, z0 - f.camZ) > 10f) continue
            val p1 = project(x0, z0) ?: continue
            val p2 = project(x0 + CELL_M, z0) ?: continue
            val p3 = project(x0 + CELL_M, z0 + CELL_M) ?: continue
            val p4 = project(x0, z0 + CELL_M) ?: continue
            val bin = (c.rssi / 3) * 3
            val path = paths.getOrPut(bin) { Path() }
            path.moveTo(p1.x, p1.y)
            path.lineTo(p2.x, p2.y)
            path.lineTo(p3.x, p3.y)
            path.lineTo(p4.x, p4.y)
            path.close()
        }
        for ((bin, path) in paths) drawPath(path, signalColor(bin).copy(alpha = 0.6f))
    }
}

@Composable
private fun SignalChip(rssi: Int?) {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(rssi?.let { signalColor(it) } ?: Color.Gray))
        Spacer(Modifier.width(8.dp))
        Text(
            if (rssi == null) tr("Leyendo…") else "$rssi dBm",
            color = Color.White, fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun Bubble(text: String) {
    Text(
        text,
        color = Color.White,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(12.dp),
    )
}

@Composable
private fun Legend() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
            drawRect(Brush.horizontalGradient(listOf(-85, -75, -67, -60, -50).map { signalColor(it) }))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(tr("Débil"), tr("Regular"), tr("Buena"), tr("Excelente")).forEach {
                Text(it, color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Suppress("DEPRECATION")
private fun displayRotation(activity: Activity?): Int {
    if (activity == null) return 0
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity.display?.rotation ?: 0
    else activity.windowManager.defaultDisplay.rotation
}
