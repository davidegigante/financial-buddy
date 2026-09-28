package com.davidegigante.spesesmart

import android.app.Application
import android.util.Log
import com.davidegigante.spesesmart.data.AppDatabase
import com.davidegigante.spesesmart.data.BudgetSettings
import com.davidegigante.spesesmart.data.CaptureSettings
import com.davidegigante.spesesmart.data.CapturedNotification
import com.davidegigante.spesesmart.data.SpeseRepository
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
        val keep = captureSettings.state.value.watchedPackages.toList() + packageName
        database.capturedNotificationDao().purgeUnwatched(
            before = System.currentTimeMillis() - CaptureSettings.UNWATCHED_RETENTION,
            keepPackages = keep,
        )
    }

    suspend fun saveNotification(notification: CapturedNotification): Boolean =
        database.capturedNotificationDao().insert(notification) != -1L
}
