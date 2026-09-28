package com.davidegigante.spesesmart.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Una notifica così come è arrivata dal sistema, senza interpretazioni.
 * Serve per la schermata di debug e, più avanti, come testo grezzo
 * collegato a ogni spesa catturata.
 */
@Entity(
    tableName = "captured_notifications",
    indices = [
        Index(value = ["packageName", "notificationKey", "postTime", "contentHash"], unique = true),
        Index(value = ["receivedAt"]),
    ],
)
data class CapturedNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appLabel: String,
    /** StatusBarNotification.key: la stessa notifica aggiornata mantiene la stessa chiave. */
    val notificationKey: String,
    /** Quando l'app sorgente ha pubblicato la notifica (ms epoch). */
    val postTime: Long,
    /** Quando l'abbiamo ricevuta noi (ms epoch). */
    val receivedAt: Long,
    val channelId: String?,
    val category: String?,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val subText: String?,
    /** Righe di una notifica InboxStyle, separate da "\n". */
    val textLines: String?,
    /** Hash del contenuto testuale, per scartare i duplicati esatti. */
    val contentHash: Int,
) {
    /** Il testo più completo disponibile. */
    val fullText: String?
        get() = bigText?.takeIf { it.isNotBlank() } ?: text

    /** Rappresentazione completa, da copiare e incollare per costruire il parser. */
    fun toDebugString(formattedTime: String): String = buildString {
        appendLine("App: $appLabel")
        appendLine("Package: $packageName")
        appendLine("Ora: $formattedTime")
        appendLine("Canale: ${channelId ?: "-"}  Categoria: ${category ?: "-"}")
        appendLine("Titolo: ${title ?: ""}")
        appendLine("Testo: ${text ?: ""}")
        if (!bigText.isNullOrBlank() && bigText != text) appendLine("Testo esteso: $bigText")
        if (!subText.isNullOrBlank()) appendLine("Sottotesto: $subText")
        if (!textLines.isNullOrBlank()) appendLine("Righe:\n$textLines")
    }.trimEnd()
}
