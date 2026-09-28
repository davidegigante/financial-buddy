package com.davidegigante.spesesmart.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Impostazioni del budget. Si imposta solo il budget mensile:
 * la quota settimanale la calcola l'app (vedi BudgetCalculator).
 */
class BudgetSettings(context: Context) {

    data class State(
        /** 0 = budget non ancora impostato. */
        val monthlyBudgetCents: Long,
        /** Percentuale a cui mandare il primo avviso (es. 80). */
        val alertPercent: Int,
    ) {
        val isSet: Boolean get() = monthlyBudgetCents > 0
    }

    private val prefs = context.getSharedPreferences("budget_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<State> = _state.asStateFlow()

    private fun read() = State(
        monthlyBudgetCents = prefs.getLong(KEY_MONTHLY, 0L),
        alertPercent = prefs.getInt(KEY_ALERT_PERCENT, DEFAULT_ALERT_PERCENT),
    )

    fun setMonthlyBudget(cents: Long) {
        prefs.edit().putLong(KEY_MONTHLY, cents).apply()
        _state.value = _state.value.copy(monthlyBudgetCents = cents)
    }

    fun setAlertPercent(percent: Int) {
        prefs.edit().putInt(KEY_ALERT_PERCENT, percent).apply()
        _state.value = _state.value.copy(alertPercent = percent)
    }

    companion object {
        private const val KEY_MONTHLY = "monthly_budget_cents"
        private const val KEY_ALERT_PERCENT = "alert_percent"
        const val DEFAULT_ALERT_PERCENT = 80
    }
}
