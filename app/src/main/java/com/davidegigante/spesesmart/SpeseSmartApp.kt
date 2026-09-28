package com.davidegigante.spesesmart

import android.app.Application
import com.davidegigante.spesesmart.data.AppDatabase
import com.davidegigante.spesesmart.data.CaptureSettings
import com.davidegigante.spesesmart.data.CapturedNotification
import com.davidegigante.spesesmart.notifications.TestNotifications

class SpeseSmartApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val captureSettings: CaptureSettings by lazy { CaptureSettings(this) }

    override fun onCreate() {
        super.onCreate()
        TestNotifications.createChannel(this)
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
