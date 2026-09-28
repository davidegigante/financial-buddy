package com.davidegigante.spesesmart.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.davidegigante.spesesmart.MainActivity
import com.davidegigante.spesesmart.R
import com.davidegigante.spesesmart.SpeseSmartApp
import com.davidegigante.spesesmart.domain.Money
import com.davidegigante.spesesmart.domain.toKey
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Avvisi di budget (soglia e sforamento, settimana e mese) e promemoria delle spese fisse scadute.
 * Ogni avviso parte una sola volta per periodo: le chiavi già inviate restano nelle preferenze.
 */
object Reminders {
    private const val CHANNEL_BUDGET = "budget_alerts"
    private const val CHANNEL_FIXED = "fixed_reminders"
    private const val WORK_NAME = "daily_checks"

    private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", Locale.ITALY)

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_BUDGET, "Avvisi di budget", NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_FIXED, "Promemoria spese fisse", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    /** Controllo periodico: copre i promemoria delle spese fisse anche se non si apre l'app. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ChecksWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    suspend fun runChecks(app: SpeseSmartApp, today: LocalDate = LocalDate.now()) {
        val settings = app.budgetSettings.state.value
        val (week, month, fixedLines) = app.repository.snapshot(today)
        val sent = app.getSharedPreferences("sent_alerts", Context.MODE_PRIVATE)

        fun once(key: String, block: () -> Unit) {
            if (sent.getBoolean(key, false)) return
            block()
            sent.edit().putBoolean(key, true).apply()
        }

        if (settings.isSet) {
            val weekKey = "week-${week.start}"
            val threshold = settings.alertPercent / 100f
            if (week.quotaCents > 0 || week.spentCents > 0) {
                if (week.spentCents > week.quotaCents) {
                    once("$weekKey-over") {
                        notify(app, CHANNEL_BUDGET, weekKey.hashCode(), "Budget settimanale superato",
                            "Sei oltre di ${Money.format(-week.remainingCents)}. Le prossime settimane avranno una quota un po' più bassa.")
                    }
                } else if (week.usedFraction >= threshold) {
                    once("$weekKey-${settings.alertPercent}") {
                        notify(app, CHANNEL_BUDGET, weekKey.hashCode(), "Hai usato il ${(week.usedFraction * 100).toInt()}% della settimana",
                            "Restano ${Money.format(week.remainingCents)} fino a domenica.")
                    }
                }
            }

            val monthKey = "month-${month.month.toKey()}"
            if (month.spendableCents > 0 || month.spentCents > 0) {
                if (month.availableCents < 0) {
                    once("$monthKey-over") {
                        notify(app, CHANNEL_BUDGET, monthKey.hashCode(), "Budget mensile superato",
                            "Sei oltre di ${Money.format(-month.availableCents)} questo mese.")
                    }
                } else if (month.usedFraction >= threshold) {
                    once("$monthKey-${settings.alertPercent}") {
                        notify(app, CHANNEL_BUDGET, monthKey.hashCode(), "Hai usato il ${(month.usedFraction * 100).toInt()}% del mese",
                            "Restano ${Money.format(month.availableCents)} fino a fine mese.")
                    }
                }
            }
        }

        fixedLines.filter { !it.isPaid && !today.isBefore(it.dueDate) }.forEach { line ->
            once("fixed-${line.fixed.id}-${line.month.toKey()}") {
                notify(app, CHANNEL_FIXED, "fixed-${line.fixed.id}".hashCode(), "Hai pagato: ${line.fixed.name}?",
                    "Scadeva il ${dayMonth.format(line.dueDate)} (${Money.format(line.amountCents)}). Segnala come pagata nell'app.")
            }
        }
    }

    private fun notify(context: Context, channel: String, id: Int, title: String, text: String) {
        if (!TestNotifications.canPost(context)) return
        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}

class ChecksWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Reminders.runChecks(applicationContext as SpeseSmartApp)
        return Result.success()
    }
}
