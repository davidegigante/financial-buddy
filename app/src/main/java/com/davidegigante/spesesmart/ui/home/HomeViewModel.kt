package com.davidegigante.spesesmart.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.davidegigante.spesesmart.SpeseSmartApp
import com.davidegigante.spesesmart.data.Dashboard
import com.davidegigante.spesesmart.domain.FixedLine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SpeseSmartApp
    private val repo = app.repository

    /** Il giorno di riferimento: si aggiorna quando si torna nell'app (può essere passata la mezzanotte). */
    private val today = MutableStateFlow(LocalDate.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val dashboard: StateFlow<Dashboard?> = today
        .flatMapLatest { repo.observeDashboard(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refreshDay() {
        today.value = LocalDate.now()
    }

    fun confirm(id: Long) = viewModelScope.launch { repo.confirmExpense(id) }

    fun confirmAll() = viewModelScope.launch { repo.confirmAll() }

    fun markPaid(line: FixedLine, amountCents: Long) =
        app.appScope.launch { repo.markFixedPaid(line.fixed.id, line.month, amountCents) }

    fun setBudget(cents: Long) {
        app.budgetSettings.setMonthlyBudget(cents)
        app.checkBudgetSoon()
    }
}
