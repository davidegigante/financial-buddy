package com.davidegigante.spesesmart.ui.debug

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.davidegigante.spesesmart.SpeseSmartApp
import com.davidegigante.spesesmart.data.CaptureSettings
import com.davidegigante.spesesmart.data.CapturedNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DebugViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SpeseSmartApp
    private val dao = app.database.capturedNotificationDao()
    private val settings = app.captureSettings

    /** true = mostra solo le app monitorate. */
    val onlyWatched = MutableStateFlow(false)

    val settingsState: StateFlow<CaptureSettings.State> = settings.state

    val notifications: StateFlow<List<CapturedNotification>> =
        combine(dao.observeLatest(), settings.state, onlyWatched) { list, state, only ->
            if (only) list.filter { it.packageName in state.watchedPackages || it.packageName == app.packageName }
            else list
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { app.purgeOldUnwatched() }
    }

    fun toggleWatched(packageName: String) {
        val watched = packageName in settings.state.value.watchedPackages
        settings.setWatched(packageName, !watched)
    }

    fun setDiscovery(enabled: Boolean) {
        if (enabled) settings.startDiscovery() else settings.stopDiscovery()
    }

    /** Trasforma una notifica salvata in una spesa "da rivedere" (importo cercato in modo generico). */
    fun createExpense(notification: CapturedNotification, rawText: String, onResult: (Boolean) -> Unit) =
        viewModelScope.launch { onResult(app.repository.createPendingFromNotification(notification, rawText)) }

    fun delete(id: Long) = viewModelScope.launch { dao.delete(id) }

    fun deleteAll() = viewModelScope.launch { dao.deleteAll() }
}
