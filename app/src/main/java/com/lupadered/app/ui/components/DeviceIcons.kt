package com.lupadered.app.ui.components

import com.lupadered.app.i18n.tr
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector
import com.lupadered.app.knowledge.DeviceType

/** Íconos extra que el usuario puede elegir para un dispositivo (RF-32, Premium). */
object DeviceIcons {

    data class Option(val key: String, val label: String, val icon: ImageVector)

    val options: List<Option> = listOf(
        Option("laptop", tr("Laptop"), Icons.Outlined.Laptop),
        Option("desktop", tr("PC de escritorio"), Icons.Outlined.DesktopWindows),
        Option("watch", tr("Reloj"), Icons.Outlined.Watch),
        Option("headphones", tr("Audífonos"), Icons.Outlined.Headphones),
        Option("camera", tr("Cámara"), Icons.Outlined.CameraAlt),
        Option("light", tr("Foco"), Icons.Outlined.Lightbulb),
        Option("thermostat", tr("Clima"), Icons.Outlined.Thermostat),
        Option("kitchen", tr("Cocina / refri"), Icons.Outlined.Kitchen),
        Option("laundry", tr("Lavadora"), Icons.Outlined.LocalLaundryService),
        Option("home", tr("Casa"), Icons.Outlined.Home),
        Option("work", tr("Trabajo"), Icons.Outlined.Work),
        Option("school", tr("Estudios"), Icons.Outlined.School),
        Option("kids", tr("Niños"), Icons.Outlined.ChildCare),
        Option("pets", tr("Mascotas"), Icons.Outlined.Pets),
        Option("lock", tr("Seguridad"), Icons.Outlined.Lock),
        Option("star", tr("Favorito"), Icons.Outlined.Star),
    )

    private val byKey = options.associateBy { it.key }

    /** Ícono a mostrar: el elegido por el usuario o, si no hay, el de su tipo. */
    fun iconFor(type: DeviceType, customKey: String?): ImageVector =
        customKey?.let { byKey[it]?.icon } ?: type.icon()
}
