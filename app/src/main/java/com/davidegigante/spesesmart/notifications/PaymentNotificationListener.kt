package com.davidegigante.spesesmart.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.davidegigante.spesesmart.SpeseSmartApp
import com.davidegigante.spesesmart.data.CapturedNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Riceve tutte le notifiche del telefono e salva solo quelle delle app
 * monitorate (o di tutte, se la modalità scoperta è attiva).
 * Per ora non interpreta nulla: salva il testo grezzo.
 */
class PaymentNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val app get() = application as SpeseSmartApp

    override fun onListenerConnected() {
        super.onListenerConnected()
        scope.launch { app.purgeOldUnwatched() }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // Su Samsung il sistema a volte scollega il listener: chiediamo di ricollegarlo.
        requestRebind(ComponentName(this, PaymentNotificationListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        val pkg = sbn.packageName

        if (pkg == packageName) {
            // Le nostre notifiche vanno ignorate, tranne quelle di prova.
            if (notification.channelId != TestNotifications.CHANNEL_ID) return
        } else {
            // Notifiche persistenti (musica, download, ecc.) e riepiloghi di gruppo: non sono pagamenti.
            if (sbn.isOngoing) return
            if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
            if (!app.captureSettings.state.value.shouldCapture(pkg)) return
        }

        val extras = notification.extras
        val title = (extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE))?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString("\n") { it.toString() }

        if (title.isNullOrBlank() && text.isNullOrBlank() && bigText.isNullOrBlank() && lines.isNullOrBlank()) return

        val captured = CapturedNotification(
            packageName = pkg,
            appLabel = appLabel(pkg),
            notificationKey = sbn.key,
            postTime = sbn.postTime,
            receivedAt = System.currentTimeMillis(),
            channelId = notification.channelId,
            category = notification.category,
            title = title,
            text = text,
            bigText = bigText,
            subText = subText,
            textLines = lines,
            contentHash = listOf(title, text, bigText, subText, lines).hashCode(),
        )

        scope.launch {
            try {
                app.saveNotification(captured)
                app.purgeOldUnwatched()
            } catch (e: Exception) {
                Log.e(TAG, "Salvataggio notifica fallito", e)
            }
        }
    }

    private fun appLabel(pkg: String): String = try {
        val info = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "SpeseSmartListener"
    }
}
