package com.davidegigante.spesesmart

import android.app.Application
import android.util.Log
import com.davidegigante.spesesmart.data.AppDatabase
import com.davidegigante.spesesmart.data.BudgetSettings
import com.davidegigante.spesesmart.data.CaptureSettings
import com.davidegigante.spesesmart.data.CapturedNotification
import com.davidegigante.spesesmart.data.SpeseRepository
import com.davidegigante.spesesmart.domain.IsybankParser
import com.davidegigante.spesesmart.domain.PaymentParsers
import com.davidegigante.spesesmart.ui.formatDateTime
import com.davidegigante.spesesmart.notifications.Reminders
import com.davidegigante.spesesmart.notifications.TestNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SpeseSmartApp : Application() {

    /** Per lavori che devono finire anche se la schermata che li ha avviati viene chiusa. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val captureSettings: CaptureSettings by lazy { CaptureSettings(this) }
    val budgetSettings: BudgetSettings by lazy { BudgetSettings(this) }
    val repository: SpeseRepository by lazy {
        SpeseRepository(database, budgetSettings, onBudgetChanged = ::checkBudgetSoon)
    }

    override fun onCreate() {
        super.onCreate()
        TestNotifications.createChannel(this)
        Reminders.createChannels(this)
        Reminders.schedule(this)
        appScope.launch { repository.ensureDefaultCategories() }
    }

    fun checkBudgetSoon() {
        appScope.launch {
            try {
                Reminders.runChecks(this@SpeseSmartApp)
            } catch (e: Exception) {
                Log.e("SpeseSmart", "Controllo budget fallito", e)
            }
        }
    }

    /** Cancella le notifiche di app non monitorate oltre il periodo di conservazione. */
    suspend fun purgeOldUnwatched() {
        val keep = captureSettings.state.value.watchedPackages.toList() + PaymentParsers.packages + packageName
        database.capturedNotificationDao().purgeUnwatched(
            before = System.currentTimeMillis() - CaptureSettings.UNWATCHED_RETENTION,
            keepPackages = keep,
        )
    }

    /**
     * Salva la notifica grezza e, se viene da un'app con un parser (es. Isybank), crea la spesa "da rivedere".
     * Le notifiche di prova dell'app usano il parser di Isybank, così si può provare tutto senza pagare.
     */
    suspend fun saveNotification(notification: CapturedNotification) {
        val id = database.capturedNotificationDao().insert(notification)
        if (id == -1L) return // duplicato esatto: già gestita
        val parser = if (notification.packageName == packageName) IsybankParser else PaymentParsers.forPackage(notification.packageName)
        parser ?: return
        val saved = notification.copy(id = id)
        repository.createPendingFromNotification(saved, saved.toDebugString(formatDateTime(saved.postTime)), parser)
    }
}
