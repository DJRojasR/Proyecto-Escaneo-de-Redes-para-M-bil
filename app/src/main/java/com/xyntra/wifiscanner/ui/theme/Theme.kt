package com.xyntra.wifiscanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xyntra.wifiscanner.data.Appearance
import com.xyntra.wifiscanner.data.DarkMode
import com.xyntra.wifiscanner.data.ThemeColor

/*
 * Identidad visual "XYNTRA".
 *
 * Neutros fríos tipo pizarra (infraestructura, precisión) + el azul eléctrico del logo
 * como acento principal y su cian como color secundario (señal, conectividad). Nada de negros puros ni verdes "hacker":
 * en oscuro el fondo es un azul pizarra muy profundo y las superficies suben por capas.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF0A5FE0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBE7FF),
    onPrimaryContainer = Color(0xFF001A4A),
    inversePrimary = Color(0xFF7AB8FF),
    secondary = Color(0xFF4C5D6E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE5EE),
    onSecondaryContainer = Color(0xFF16222E),
    tertiary = Color(0xFF00809F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCCF0FA),
    onTertiaryContainer = Color(0xFF00313E),
    background = Color(0xFFF4F6F9),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFF4F6F9),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE3E8EE),
    onSurfaceVariant = Color(0xFF4A5565),
    surfaceTint = Color(0xFF0A5FE0),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDCE1E7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEDF1F5),
    surfaceContainerHigh = Color(0xFFE6EBF0),
    surfaceContainerHighest = Color(0xFFDFE5EB),
    inverseSurface = Color(0xFF1E2833),
    inverseOnSurface = Color(0xFFEFF3F7),
    outline = Color(0xFF7A8594),
    outlineVariant = Color(0xFFD9DFE6),
    error = Color(0xFFC62828),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDE3E1),
    onErrorContainer = Color(0xFF4A0B0B),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7AB8FF),
    onPrimary = Color(0xFF00305E),
    primaryContainer = Color(0xFF123F86),
    onPrimaryContainer = Color(0xFFD9E6FF),
    inversePrimary = Color(0xFF0A5FE0),
    secondary = Color(0xFFB4C3D2),
    onSecondary = Color(0xFF1F2D3A),
    secondaryContainer = Color(0xFF2A3846),
    onSecondaryContainer = Color(0xFFD7E3EF),
    tertiary = Color(0xFF5FD4F5),
    onTertiary = Color(0xFF003544),
    tertiaryContainer = Color(0xFF004E63),
    onTertiaryContainer = Color(0xFFC8F1FC),
    background = Color(0xFF090E1A),
    onBackground = Color(0xFFE3E9F0),
    surface = Color(0xFF090E1A),
    onSurface = Color(0xFFE3E9F0),
    surfaceVariant = Color(0xFF26313D),
    onSurfaceVariant = Color(0xFFA9B5C2),
    surfaceTint = Color(0xFF7AB8FF),
    surfaceBright = Color(0xFF2A3541),
    surfaceDim = Color(0xFF090E1A),
    surfaceContainerLowest = Color(0xFF050914),
    surfaceContainerLow = Color(0xFF111827),
    surfaceContainer = Color(0xFF162031),
    surfaceContainerHigh = Color(0xFF1D2939),
    surfaceContainerHighest = Color(0xFF253244),
    inverseSurface = Color(0xFFE3E9F0),
    inverseOnSurface = Color(0xFF1E2833),
    outline = Color(0xFF7D8A98),
    outlineVariant = Color(0xFF2A3643),
    error = Color(0xFFFF9E97),
    onError = Color(0xFF5C0F0B),
    errorContainer = Color(0xFF5E1A17),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000),
)

/**
 * Tipografía del sistema (sin fuentes extra que pesen en el APK), con una jerarquía
 * más marcada: títulos firmes y compactos, etiquetas claras y cifras tabulares.
 */
private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        bodyLarge = t.bodyLarge.copy(letterSpacing = 0.1.sp),
        bodyMedium = t.bodyMedium.copy(letterSpacing = 0.1.sp),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = t.labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
        labelSmall = t.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    )
}

/** Esquinas redondeadas moderadas y coherentes en toda la app. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** true si la app se está mostrando en modo oscuro (puede no coincidir con el sistema). */
val LocalIsDark = staticCompositionLocalOf { false }

/** Colores con significado (bien / atención / mal), coherentes en claro y oscuro. */
object StatusColors {
    val good: Color
        @Composable get() = if (LocalIsDark.current) Color(0xFF6FD99A) else Color(0xFF16804A)
    val warn: Color
        @Composable get() = if (LocalIsDark.current) Color(0xFFF5C35B) else Color(0xFF9A6200)
    val bad: Color
        @Composable get() = if (LocalIsDark.current) Color(0xFFFF8F87) else Color(0xFFC62828)
    val info: Color
        @Composable get() = MaterialTheme.colorScheme.primary
    val idle: Color
        @Composable get() = MaterialTheme.colorScheme.outline
}

/** Cifras tabulares (IPs, ms, %), para que no "bailen" al cambiar. */
val MonoStyle = TextStyle(fontFeatureSettings = "tnum", fontSize = 13.sp)

/** Color principal de cada tema: (claro, contenedor claro, texto sobre contenedor claro, oscuro, contenedor oscuro). */
private class Accent(
    val primary: Color, val container: Color, val onContainer: Color,
    val darkPrimary: Color, val darkContainer: Color, val darkOnContainer: Color, val darkOnPrimary: Color,
)

private val accents = mapOf(
    ThemeColor.TEAL to Accent(
        Color(0xFF00788A), Color(0xFFCDF1F6), Color(0xFF00343D),
        Color(0xFF5ED3E6), Color(0xFF0D4B57), Color(0xFFC9F3FA), Color(0xFF00363F),
    ),
    ThemeColor.PURPLE to Accent(
        Color(0xFF6B4FB8), Color(0xFFEBE1FF), Color(0xFF250A5E),
        Color(0xFFD1BEFF), Color(0xFF4E3592), Color(0xFFEBE1FF), Color(0xFF341A72),
    ),
    ThemeColor.ORANGE to Accent(
        Color(0xFFB4530E), Color(0xFFFFE1CC), Color(0xFF3A1500),
        Color(0xFFFFB585), Color(0xFF7D3600), Color(0xFFFFE1CC), Color(0xFF552200),
    ),
)

/** Color de muestra para el selector de temas. */
fun ThemeColor.swatch(): Color = accents[this]?.primary ?: LightColors.primary

private fun schemeFor(color: ThemeColor, dark: Boolean): ColorScheme {
    val base = if (dark) DarkColors else LightColors
    val a = accents[color] ?: return base
    return if (dark) {
        base.copy(
            primary = a.darkPrimary, onPrimary = a.darkOnPrimary,
            primaryContainer = a.darkContainer, onPrimaryContainer = a.darkOnContainer,
            surfaceTint = a.darkPrimary,
        )
    } else {
        base.copy(
            primary = a.primary, onPrimary = Color.White,
            primaryContainer = a.container, onPrimaryContainer = a.onContainer,
            surfaceTint = a.primary,
        )
    }
}

/**
 * Tema de la app. Todos los colores y el modo oscuro son gratis.
 */
@Composable
fun XyntraTheme(
    appearance: Appearance = Appearance(),
    premium: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Claro, oscuro o según el teléfono, elegido por el usuario en Ajustes.
    val dark = when (appearance.dark) {
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
        DarkMode.SYSTEM -> isSystemInDarkTheme()
    }
    val color = appearance.color
    CompositionLocalProvider(LocalIsDark provides dark) {
        MaterialTheme(
            colorScheme = schemeFor(color, dark),
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
