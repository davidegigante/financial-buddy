package com.davidegigante.spesesmart.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.davidegigante.spesesmart.R
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/** Notifiche finte, per verificare che il listener funzioni senza fare un pagamento vero. */
object TestNotifications {
    const val CHANNEL_ID = "debug_test"

    /** Testi con lo stesso formato delle notifiche reali di Isybank, con importi e orari casuali. */
    private fun sampleText(): String {
        val now = LocalDateTime.now()
        val cents = Random.nextLong(150, 4_500)
        val amount = "${cents / 100},${(cents % 100).toString().padStart(2, '0')} €"
        return if (Random.nextInt(4) == 0) {
            "E' stato addebitato il pagamento di una domiciliazione di $amount da parte di " +
                "MEDICI SENZA FRONTIERE ONLUS sul conto xxx202 in data ${now.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}"
        } else {
            val merchant = listOf("BAR GUATE, SALERNO", "ESSELUNGA, MILANO", "TRENITALIA, ROMA", "FARMACIA CENTRALE, SALERNO").random()
            "💸 Hai pagato $amount con la carta *6576 il ${now.format(DateTimeFormatter.ofPattern("dd.MM"))} " +
                "alle ore ${now.format(DateTimeFormatter.ofPattern("HH:mm"))} da $merchant."
        }
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Notifiche di prova", NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun post(context: Context) {
        if (!canPost(context)) return
        val text = sampleText()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Prova Spese Smart")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
    }
}
