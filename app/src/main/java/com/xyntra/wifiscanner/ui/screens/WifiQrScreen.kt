package com.xyntra.wifiscanner.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import android.view.WindowManager
import com.xyntra.wifiscanner.ui.findActivity
import com.xyntra.wifiscanner.i18n.tr
import com.xyntra.wifiscanner.net.WifiProtection
import com.xyntra.wifiscanner.net.WifiSecurityCheck
import com.xyntra.wifiscanner.ui.ScreenScaffold
import com.xyntra.wifiscanner.ui.qr.QrSecurity
import com.xyntra.wifiscanner.ui.qr.WifiQrImage
import com.xyntra.wifiscanner.ui.qr.WifiQrPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Compartir tu WiFi con un QR (gratis). El usuario escribe la contraseña:
 * Android no deja a las apps leer las contraseñas guardadas. Nada se guarda ni se envía.
 */
@Composable
fun WifiQrScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var ssid by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var security by rememberSaveable { mutableStateOf(QrSecurity.WPA) }
    var hidden by rememberSaveable { mutableStateOf(false) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var prefilled by rememberSaveable { mutableStateOf(false) }

    // Rellenamos el nombre de la red y el tipo de protección si Android los da
    LaunchedEffect(Unit) {
        if (prefilled) return@LaunchedEffect
        prefilled = true
        val (name, protection) = withContext(Dispatchers.IO) { WifiSecurityCheck.quick(context) }
        if (ssid.isEmpty() && name != null) ssid = name
        security = when (protection) {
            WifiProtection.OPEN, WifiProtection.OWE -> QrSecurity.OPEN
            WifiProtection.WEP -> QrSecurity.WEP
            else -> QrSecurity.WPA
        }
    }

    // Pantalla protegida: no se pueden hacer capturas ni grabarla, ni se ve en "apps recientes".
    // Así el código solo sirve mostrándolo en persona a quien lo escanee con su cámara.
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    val problem = when {
        ssid.isBlank() -> tr("Escribe el nombre de tu WiFi.")
        security == QrSecurity.WPA && password.length !in 8..63 -> tr("La contraseña de un WiFi WPA tiene entre 8 y 63 caracteres.")
        security == QrSecurity.WEP && password.isEmpty() -> tr("Escribe la contraseña de tu WiFi.")
        else -> null
    }
    val payload = if (problem == null) WifiQrPayload.build(ssid.trim(), password, security, hidden) else null
    val qr = remember(payload) {
        payload?.let { WifiQrImage.matrix(it) }?.let { WifiQrImage.bitmap(it).asImageBitmap() }
    }

    ScreenScaffold(title = tr("Compartir tu WiFi"), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                tr("Muestra este código en tu pantalla a la visita: lo escanea con la cámara de su teléfono y se conecta sin que tengas que dictarle la contraseña."),
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedTextField(
                value = ssid,
                onValueChange = { ssid = it },
                label = { Text(tr("Nombre del WiFi")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(tr("Tipo de protección"), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(security == QrSecurity.WPA, { security = QrSecurity.WPA }, { Text("WPA/WPA2/WPA3") })
                FilterChip(security == QrSecurity.WEP, { security = QrSecurity.WEP }, { Text("WEP") })
                FilterChip(security == QrSecurity.OPEN, { security = QrSecurity.OPEN }, { Text(tr("Sin contraseña")) })
            }

            if (security != QrSecurity.OPEN) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(tr("Contraseña del WiFi")) },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showPassword) tr("Ocultar contraseña") else tr("Mostrar contraseña"),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    tr("¿No la recuerdas? Suele estar en la etiqueta del router. En Android 10 o más nuevo también la ves en Ajustes › WiFi › tu red › Compartir."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = hidden, onCheckedChange = { hidden = it })
                Text(tr("Es una red oculta (no aparece en la lista de WiFi)"), style = MaterialTheme.typography.bodyMedium)
            }

            if (qr == null) {
                problem?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        // Fondo blanco siempre, también en modo oscuro: así lo leen todas las cámaras
                        Image(
                            bitmap = qr,
                            contentDescription = tr("Código QR del WiFi %1\$s", ssid.trim()),
                            filterQuality = FilterQuality.None,
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White),
                        )
                        Text(ssid.trim(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
                        Text(
                            tr("Pide a tu visita que abra la cámara y apunte al código. Sube el brillo de la pantalla si no lo lee."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Button(
                            onClick = { showPassword = false; fullscreen = true },
                            modifier = Modifier.padding(top = 12.dp),
                        ) {
                            Icon(Icons.Outlined.Fullscreen, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(tr("Mostrar en grande para escanear"))
                        }
                    }
                }
            }
        }
    }

    // Pantalla completa: solo el código y el nombre. Así, al mostrarlo para escanear,
    // la contraseña no aparece cerca y nadie puede fotografiarla junto al QR.
    if (fullscreen && qr != null) {
        Dialog(
            onDismissRequest = { fullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                Modifier.fillMaxSize().background(Color.White).clickable { fullscreen = false },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Image(
                        bitmap = qr,
                        contentDescription = tr("Código QR del WiFi %1\$s", ssid.trim()),
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.fillMaxWidth(0.9f).aspectRatio(1f).background(Color.White),
                    )
                    Text(
                        ssid.trim(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.Black,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        tr("Apunta la cámara al código · toca para cerrar"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
