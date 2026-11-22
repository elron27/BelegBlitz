package de.belegblitz.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.belegblitz.BelegBlitzApp
import de.belegblitz.MainActivity
import de.belegblitz.R
import de.belegblitz.domain.Erinnerung
import de.belegblitz.domain.EinreichungSicht
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** SR-aada699191a1: täglich prüfen und an Einreichungen ohne Bescheid nach Ablauf der Wartezeit erinnern. */
class ErinnerungsWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (context.applicationContext as BelegBlitzApp).container
        val faellig = try {
            val sichten = container.db.einreichungDao().alleEinmal()
                .map { EinreichungSicht(it.id, it.einreichungsdatum, it.status) }
            Erinnerung.faellige(sichten, LocalDate.now(), container.einstellungen.wartezeitWochen.value)
        } catch (e: Exception) {
            Log.e("Erinnerung", "Prüfung fehlgeschlagen", e)
            return Result.retry()
        }
        if (faellig.isNotEmpty()) zeige(faellig.size)
        return Result.success()
    }

    private fun zeige(anzahl: Int) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(KANAL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val oeffnen = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, KANAL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(context.resources.getQuantityString(R.plurals.reminder_notification_text, anzahl, anzahl))
            .setContentIntent(oeffnen)
            .setAutoCancel(true)
            .build()
        manager.notify(1, n)
    }

    companion object {
        private const val KANAL = "erinnerung"
        private const val ARBEIT = "erinnerung_taeglich"

        fun planen(context: Context) {
            val request = PeriodicWorkRequestBuilder<ErinnerungsWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(ARBEIT, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
