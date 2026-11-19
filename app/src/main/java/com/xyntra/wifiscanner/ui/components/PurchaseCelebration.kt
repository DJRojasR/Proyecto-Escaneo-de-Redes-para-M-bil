package com.xyntra.wifiscanner.ui.components

import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xyntra.wifiscanner.R
import com.xyntra.wifiscanner.i18n.tr
import com.xyntra.wifiscanner.premium.PremiumSource
import kotlin.math.cos
import kotlin.math.sin

/** Azul noche de la marca (el mismo fondo del ícono de la app). */
private val Night = Color(0xFF070B14)
private val MarkWhite = Color(0xFFE8EDF4)
private val BrandBlue = Color(0xFF7AB8FF)
private val PremiumGoldTone = Color(0xFFD9B66A)

/**
 * Pantalla de "Pago confirmado": sobria y elegante.
 *
 * Secuencia (unos 3 segundos): el fondo pasa a azul noche, aparece la X de XYNTRA y la
 * cruza un brillo de luz una sola vez, un anillo muy fino se dibuja alrededor y al cerrarse
 * aparece un pequeño ✓ (con una vibración breve). Luego "¡Pago confirmado!", la bienvenida,
 * el plan, lo que se desbloquea y el botón "Empezar a usar Premium".
 * Azul para la suscripción mensual; dorado para "Pro de por vida".
 * Si el teléfono tiene las animaciones desactivadas, se muestra directamente el final.
 */
@Composable
fun PurchaseCelebration(source: PremiumSource, onContinue: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val accent = if (source == PremiumSource.LIFETIME) PremiumGoldTone else BrandBlue
    val planText = if (source == PremiumSource.LIFETIME) tr("Pro de por vida") else tr("Suscripción mensual")

    // Una sola línea de tiempo (en segundos) que maneja todos los pasos
    val time = remember { Animatable(0f) }
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    LaunchedEffect(Unit) {
        if (reduceMotion) time.snapTo(END) else time.animateTo(END, tween((END * 1000).toInt(), easing = LinearEasing))
    }
    // Vibración breve justo cuando se cierra el anillo y aparece el ✓
    LaunchedEffect(Unit) {
        snapshotFlow { time.value >= 1.9f }.first { it }
        val constant = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
        view.performHapticFeedback(constant)
    }
    val t = time.value
    BackHandler(onBack = onContinue)

    val background = step(t, 0f, 0.4f)
    val ring = step(t, 0.3f, 1.6f, FastOutSlowInEasing)
    val sweep = step(t, 0.9f, 1.4f, FastOutSlowInEasing)
    val badge = step(t, 1.9f, 0.6f)

    Box(
        Modifier
            .fillMaxSize()
            .background(Night.copy(alpha = background))
            // Bloquea los toques a lo que hay detrás
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
                // La X de XYNTRA, con un brillo que la cruza una sola vez
                Image(
                    painter = painterResource(R.drawable.ic_stat_xyntra),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MarkWhite),
                    modifier = Modifier
                        .size(84.dp)
                        .graphicsLayer {
                            alpha = step(t, 0f, 1f)
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()
                            if (sweep > 0f && sweep < 1f) {
                                val w = size.width
                                val x = -w * 0.5f + sweep * w * 2f
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        listOf(Color.Transparent, Color.White.copy(alpha = 0.95f), Color.Transparent),
                                        startX = x - w * 0.25f,
                                        endX = x + w * 0.25f,
                                    ),
                                    blendMode = BlendMode.SrcAtop,
                                )
                            }
                        },
                )
                // Anillo fino que se dibuja despacio, y el ✓ discreto en su borde
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 1.2.dp.toPx()
                    val r = size.minDimension / 2f - 14.dp.toPx()
                    val c = Offset(size.width / 2f, size.height / 2f)
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = 360f * ring,
                        useCenter = false,
                        topLeft = Offset(c.x - r, c.y - r),
                        size = Size(r * 2, r * 2),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    if (badge > 0f) {
                        val a = Math.toRadians(45.0)
                        val b = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
                        val br = 11.dp.toPx()
                        drawCircle(Night, radius = br, center = b, alpha = badge)
                        drawCircle(accent, radius = br, center = b, alpha = badge, style = Stroke(stroke))
                        val u = br / 11f
                        val check = Path().apply {
                            moveTo(b.x - 5f * u, b.y)
                            lineTo(b.x - 1.5f * u, b.y + 3.5f * u)
                            lineTo(b.x + 5f * u, b.y - 3.5f * u)
                        }
                        drawPath(
                            check, accent, alpha = badge,
                            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Text(
                tr("¡Pago confirmado!"),
                color = Color(0xFFF2F5F9),
                fontSize = 24.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 0.3.sp,
                modifier = Modifier.rise(step(t, 2.1f, 0.9f, Smooth)),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                tr("Bienvenido a XYNTRA Premium"),
                color = Color(0xFF8D99A8),
                fontSize = 14.sp,
                modifier = Modifier.rise(step(t, 2.3f, 0.9f, Smooth)),
            )
            Spacer(Modifier.height(12.dp))
            // Plan contratado, en una etiqueta fina del color de acento
            Box(
                Modifier
                    .rise(step(t, 2.45f, 0.9f, Smooth))
                    .border(BorderStroke(1.dp, accent.copy(alpha = 0.6f)), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 4.dp),
            ) {
                Text(planText, color = accent, fontSize = 12.sp, letterSpacing = 0.4.sp)
            }
            Spacer(Modifier.height(24.dp))
            // Lo más importante que se desbloquea, uno debajo del otro
            val benefits = listOf(
                tr("Sin anuncios"), tr("Vigilancia de la red"),
                tr("Mapa de calor WiFi"), tr("Revisión de seguridad"),
                tr("Pruebas de velocidad ilimitadas"), tr("Historial completo"),
            )
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                benefits.forEachIndexed { index, label ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.rise(step(t, 2.6f + index * 0.1f, 0.8f, Smooth)),
                    ) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(label, color = Color(0xFFE3E9F0), fontSize = 15.sp)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = onContinue,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF3A4656)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MarkWhite),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .rise(step(t, 3.4f, 0.9f, Smooth)),
            ) {
                Text(tr("Empezar a usar Premium"), letterSpacing = 0.3.sp)
            }
        }
    }
}

private const val END = 4.4f
private val Smooth = CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f)

/** Avance (0..1) de un paso que empieza en [start] segundos y dura [duration]. */
private fun step(t: Float, start: Float, duration: Float, easing: androidx.compose.animation.core.Easing = LinearEasing): Float =
    easing.transform(((t - start) / duration).coerceIn(0f, 1f))

/** Aparece subiendo apenas unos píxeles mientras se hace visible. */
private fun Modifier.rise(p: Float): Modifier = graphicsLayer {
    alpha = p
    translationY = (1f - p) * 6.dp.toPx()
}
