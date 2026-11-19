package com.xyntra.wifiscanner.ui.heat

import com.xyntra.wifiscanner.data.HeatPoint
import com.xyntra.wifiscanner.i18n.tr
import kotlin.math.roundToInt

/** Textos del resumen del mapa, usados en la pantalla y en la imagen exportada. */
object HeatTexts {

    /** "35 puntos · mejor -48 dBm · peor -67 dBm · promedio -56 dBm", o null si no hay puntos. */
    fun summary(pts: List<HeatPoint>): String? {
        if (pts.isEmpty()) return null
        val best = pts.maxOf { it.rssi }
        val worst = pts.minOf { it.rssi }
        val avg = pts.map { it.rssi }.average().roundToInt()
        return tr("%1\$s puntos · mejor %2\$s dBm · peor %3\$s dBm · promedio %4\$s dBm", pts.size, best, worst, avg)
    }

    /** Qué significa el mapa y qué hacer. */
    fun verdict(pts: List<HeatPoint>): String {
        val worst = pts.minOfOrNull { it.rssi }
        val avg = pts.takeIf { it.isNotEmpty() }?.map { it.rssi }?.average()?.roundToInt()
        return when {
            pts.isEmpty() -> tr("Aún no hay mediciones en este piso.")
            pts.size < 5 -> tr("Mide en al menos 5 lugares para que el mapa se vea completo.")
            worst != null && worst <= -75 ->
                tr("Hay zonas con señal débil (en rojo). Ahí el Internet irá lento o se cortará. Pon el router en alto y al centro de la casa, lejos de paredes gruesas y del microondas, o instala un repetidor o un sistema mesh cerca de esa zona.")
            avg != null && avg <= -67 ->
                tr("La señal es regular en buena parte de este piso. Mover el router a un lugar más central o más alto puede mejorarla.")
            else -> tr("¡Buena cobertura! La señal llega bien a todos los lugares que mediste.")
        }
    }
}
