package com.davidegigante.spesesmart.data

import android.content.Context
import com.davidegigante.spesesmart.domain.PaymentParsers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Impostazioni della cattura notifiche.
 *
 * - App monitorate: le loro notifiche vengono sempre salvate.
 * - Modalità scoperta: per un tempo limitato salva le notifiche di TUTTE le app,
 *   così si può individuare il package name di Isybank / Google Wallet.
 *   Scade da sola perché le notifiche di altre app possono contenere dati
 *   privati (codici OTP, messaggi); quelle non monitorate vengono poi cancellate.
 */
class CaptureSettings(context: Context) {

    data class State(
        val watchedPackages: Set<String>,
        val discoveryUntil: Long,
    ) {
        fun isDiscoveryActive(now: Long = System.currentTimeMillis()) = discoveryUntil > now
        /** Le app con un parser (es. Isybank) si leggono sempre, anche se non sono nella lista. */
        fun shouldCapture(packageName: String, now: Long = System.currentTimeMillis()) =
            packageName in watchedPackages || packageName in PaymentParsers.packages || isDiscoveryActive(now)
    }

    private val prefs = context.getSharedPreferences("capture_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<State> = _state.asStateFlow()

    private fun read() = State(
        watchedPackages = prefs.getStringSet(KEY_WATCHED, null)?.toSet() ?: DEFAULT_WATCHED,
        discoveryUntil = prefs.getLong(KEY_DISCOVERY_UNTIL, 0L),
    )

    fun setWatched(packageName: String, watched: Boolean) {
        val updated = _state.value.watchedPackages.let { if (watched) it + packageName else it - packageName }
        prefs.edit().putStringSet(KEY_WATCHED, updated).apply()
        _state.value = _state.value.copy(watchedPackages = updated)
    }

    fun startDiscovery(durationMillis: Long = DISCOVERY_DURATION) {
        val until = System.currentTimeMillis() + durationMillis
        prefs.edit().putLong(KEY_DISCOVERY_UNTIL, until).apply()
        _state.value = _state.value.copy(discoveryUntil = until)
    }

    fun stopDiscovery() {
        prefs.edit().putLong(KEY_DISCOVERY_UNTIL, 0L).apply()
        _state.value = _state.value.copy(discoveryUntil = 0L)
    }

    companion object {
        private const val KEY_WATCHED = "watched_packages"
        private const val KEY_DISCOVERY_UNTIL = "discovery_until"

        const val DISCOVERY_DURATION = 24L * 60 * 60 * 1000

        /** Per quanto tempo si tengono le notifiche di app non monitorate. */
        const val UNWATCHED_RETENTION = 24L * 60 * 60 * 1000

        /** Google Wallet / Google Pay: per ora solo registrata, il parser arriverà con un testo reale. */
        val DEFAULT_WATCHED = setOf("com.google.android.apps.walletnfcrel")
    }
}
