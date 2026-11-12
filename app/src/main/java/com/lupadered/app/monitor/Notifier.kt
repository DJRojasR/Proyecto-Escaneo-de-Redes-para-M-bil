package com.lupadered.app.monitor

import com.lupadered.app.i18n.tr
import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lupadered.app.MainActivity
import com.lupadered.app.R
import com.lupadered.app.data.Device

/** Notificaciones de la vigilancia de la red (RF-25). */
object Notifier {

    const val CHANNEL_INTRUDERS = "intrusos"
    private const val ID_INTRUDERS = 1001

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_INTRUDERS,
            tr("Equipos nuevos en tu red"),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = tr("Te avisa cuando un dispositivo desconocido se conecta a la red que vigilas.")
        }
        nm.createNotificationChannel(channel)
    }

    /** ¿Puede la app mostrar notificaciones? (Android 13+ pide permiso). */
    fun canNotify(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission")
    fun notifyNewDevices(context: Context, devices: List<Device>, networkName: String?) {
        if (devices.isEmpty() || !canNotify(context)) return
        val title = if (devices.size == 1) tr("Se conectó un equipo desconocido")
        else tr("Se conectaron %1\$s equipos desconocidos", devices.size)
        val names = devices.take(4).joinToString(", ") { "${it.displayName} (${it.ip})" } +
            if (devices.size > 4) tr(" y %1\$s más", devices.size - 4) else ""
        val where = networkName?.let { tr(" a %1\$s", it) } ?: tr(" a tu red")
        val text = tr("Apareció%1\$s: %2\$s. Si no es tuyo, revisa quién tiene tu clave WiFi.", where, names)

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_ROUTE, "red")
        }
        val pi = PendingIntent.getActivity(
            context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_INTRUDERS)
            .setSmallIcon(R.drawable.ic_stat_lupa)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID_INTRUDERS, n)
        } catch (_: SecurityException) {
            // El usuario quitó el permiso justo ahora: no hacemos nada.
        }
    }
}
