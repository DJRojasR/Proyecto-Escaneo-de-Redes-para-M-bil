package com.lupadered.app.monitor

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.lupadered.app.data.MonitorSettings
import com.lupadered.app.data.ScanSource
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Programa los escaneos en segundo plano con WorkManager (RF-24, RF-26).
 * Android no permite repetir una tarea más seguido que cada 15 minutos.
 */
object MonitorScheduler {

    private const val WORK_AUTO = "lupa_vigilancia"
    private const val WORK_DAILY = "lupa_programado"

    /**
     * Activa o cancela las tareas según los ajustes. [reschedule] = true cuando el
     * usuario acaba de cambiar los ajustes (recalcula la hora del escaneo diario).
     */
    fun apply(context: Context, s: MonitorSettings, premium: Boolean, reschedule: Boolean) {
        val wm = WorkManager.getInstance(context)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        if (premium && s.autoEnabled && s.networkKey != null) {
            val minutes = s.intervalMinutes.coerceAtLeast(15).toLong()
            val req = PeriodicWorkRequestBuilder<BackgroundScanWorker>(minutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInputData(workDataOf(BackgroundScanWorker.KEY_SOURCE to ScanSource.AUTO.name))
                .build()
            wm.enqueueUniquePeriodicWork(WORK_AUTO, ExistingPeriodicWorkPolicy.UPDATE, req)
        } else {
            wm.cancelUniqueWork(WORK_AUTO)
        }

        if (premium && s.dailyEnabled && s.networkKey != null) {
            val req = PeriodicWorkRequestBuilder<BackgroundScanWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(millisUntil(s.dailyHour, s.dailyMinute), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(BackgroundScanWorker.KEY_SOURCE to ScanSource.SCHEDULED.name))
                .build()
            val policy = if (reschedule) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
            else ExistingPeriodicWorkPolicy.KEEP
            wm.enqueueUniquePeriodicWork(WORK_DAILY, policy, req)
        } else {
            wm.cancelUniqueWork(WORK_DAILY)
        }
    }

    /** Milisegundos desde ahora hasta la próxima vez que el reloj marque [hour]:[minute]. */
    fun millisUntil(hour: Int, minute: Int, now: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis - now
    }
}
