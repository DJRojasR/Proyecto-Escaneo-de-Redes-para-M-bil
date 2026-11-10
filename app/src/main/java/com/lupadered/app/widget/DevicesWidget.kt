package com.lupadered.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.lupadered.app.LupaApp
import com.lupadered.app.MainActivity
import com.lupadered.app.R
import com.lupadered.app.net.NetworkInfoProvider
import com.lupadered.app.ui.components.relativeTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Widget de pantalla de inicio (RF-35): cuántos equipos hay conectados en tu red
 * y cuántos están sin revisar. Se actualiza después de cada escaneo.
 */
class DevicesWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAll(context)
    }

    companion object {

        /** Vuelve a dibujar todos los widgets colocados. Seguro desde cualquier hilo. */
        fun updateAll(context: Context) {
            val app = context.applicationContext as LupaApp
            app.appScope.launch(Dispatchers.IO) {
                val manager = AppWidgetManager.getInstance(app)
                val ids = manager.getAppWidgetIds(ComponentName(app, DevicesWidget::class.java))
                if (ids.isEmpty()) return@launch
                val views = build(app)
                for (id in ids) manager.updateAppWidget(id, views)
            }
        }

        private fun build(app: LupaApp): RemoteViews {
            val views = RemoteViews(app.packageName, R.layout.widget_devices)

            if (!app.premium.isPremium) {
                views.setTextViewText(R.id.widget_title, "Lupa de Red")
                views.setTextViewText(R.id.widget_count, "Premium")
                views.setTextViewText(R.id.widget_detail, "Toca para activar el widget de tu red")
                views.setTextViewText(R.id.widget_alert, "")
                views.setOnClickPendingIntent(R.id.widget_root, openApp(app, "premium"))
                return views
            }

            val repo = app.repository
            val currentKey = NetworkInfoProvider.current(app)?.key
            val profile = currentKey?.let { repo.network(it) } ?: repo.networks().firstOrNull()
            if (profile == null) {
                views.setTextViewText(R.id.widget_title, "Lupa de Red")
                views.setTextViewText(R.id.widget_count, "—")
                views.setTextViewText(R.id.widget_detail, "Toca para escanear tu red")
                views.setTextViewText(R.id.widget_alert, "")
                views.setOnClickPendingIntent(R.id.widget_root, openApp(app, "red"))
                return views
            }
            val last = repo.latestScan(profile.key)
            val devices = repo.devices(profile.key)
            val online = devices.filter { last != null && it.lastScanId == last.id }
            val unknown = online.count { !it.trusted }

            views.setTextViewText(R.id.widget_title, profile.displayName)
            views.setTextViewText(R.id.widget_count, online.size.toString())
            views.setTextViewText(
                R.id.widget_detail,
                if (last == null) "Aún sin escanear"
                else (if (online.size == 1) "equipo conectado" else "equipos conectados") + " · " + relativeTime(last.startedAt),
            )
            views.setTextViewText(
                R.id.widget_alert,
                when {
                    unknown == 1 -> "1 sin revisar"
                    unknown > 1 -> "$unknown sin revisar"
                    else -> ""
                },
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp(app, "red"))
            return views
        }

        private fun openApp(context: Context, route: String): PendingIntent {
            val i = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_ROUTE, route)
            }
            return PendingIntent.getActivity(
                context, route.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
