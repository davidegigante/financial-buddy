package com.davidegigante.spesesmart.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidegigante.spesesmart.SpeseSmartApp
import com.davidegigante.spesesmart.data.Category
import com.davidegigante.spesesmart.data.Expense
import com.davidegigante.spesesmart.data.ExpenseStatus
import com.davidegigante.spesesmart.domain.Money
import com.davidegigante.spesesmart.domain.toLocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class ExpenseForm(
    val original: Expense?,
    val amountText: String,
    val merchant: String,
    val note: String,
    val categoryId: Long?,
    val date: LocalDate,
    /** true se la categoria è stata scelta a mano: da lì in poi non la cambiamo più da soli. */
    val categoryTouched: Boolean = false,
    /** true se la categoria attuale viene da una regola imparata. */
    val suggested: Boolean = false,
) {
    val amountCents: Long? get() = Money.parse(amountText)?.takeIf { it > 0 }
    val amountValid: Boolean get() = amountCents != null
    val isPending: Boolean get() = original?.isPending == true
    val rawText: String? get() = original?.rawText
}

class ExpenseEditorViewModel(private val app: SpeseSmartApp, private val expenseId: Long?) : ViewModel() {
    private val repo = app.repository

    private val _state = MutableStateFlow<ExpenseForm?>(null)
    val state: StateFlow<ExpenseForm?> = _state.asStateFlow()

    val categories: StateFlow<List<Category>> =
        repo.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var suggestJob: Job? = null

    init {
        viewModelScope.launch {
            val existing = expenseId?.let { repo.getExpense(it) }
            _state.value = if (existing != null) {
                ExpenseForm(
                    original = existing,
                    amountText = Money.toInput(existing.amountCents),
                    merchant = existing.merchant,
                    note = existing.note,
                    categoryId = existing.categoryId,
                    date = existing.occurredAt.toLocalDate(),
                    categoryTouched = existing.categoryId != null,
                )
            } else {
                ExpenseForm(original = null, amountText = "", merchant = "", note = "", categoryId = null, date = LocalDate.now())
            }
        }
    }

    fun onAmountChange(value: String) = _state.update { it?.copy(amountText = value) }

    fun onNoteChange(value: String) = _state.update { it?.copy(note = value) }

    fun onDateChange(value: LocalDate) = _state.update { it?.copy(date = value) }

    fun onCategoryChange(id: Long?) = _state.update { it?.copy(categoryId = id, categoryTouched = true, suggested = false) }

    fun onMerchantChange(value: String) {
        _state.update { it?.copy(merchant = value) }
        if (_state.value?.categoryTouched == true) return
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            delay(300)
            val suggestion = repo.suggestCategory(value)
            _state.update { s ->
                if (s == null || s.categoryTouched) s else s.copy(categoryId = suggestion, suggested = suggestion != null)
            }
        }
    }

    /** [confirm] = true per le spese da rivedere: le salva come confermate. */
    fun save(confirm: Boolean, onDone: () -> Unit) {
        val s = _state.value ?: return
        val amount = s.amountCents ?: return
        val original = s.original
        val occurredAt = occurredAt(s.date, original)
        val expense = original?.copy(
            amountCents = amount,
            merchant = s.merchant.trim(),
            note = s.note.trim(),
            categoryId = s.categoryId,
            occurredAt = occurredAt,
            status = if (confirm) ExpenseStatus.CONFIRMED else original.status,
        ) ?: Expense(
            amountCents = amount,
            merchant = s.merchant.trim(),
            note = s.note.trim(),
            categoryId = s.categoryId,
            occurredAt = occurredAt,
        )
        // Nello scope dell'app: il salvataggio finisce anche se la schermata si chiude subito.
        app.appScope.launch { repo.saveExpense(expense) }
        onDone()
    }

    fun delete(onDone: () -> Unit) {
        val id = expenseId ?: return
        app.appScope.launch { repo.deleteExpense(id) }
        onDone()
    }

    /** Mantiene l'ora originale se la data non cambia; per le spese nuove di oggi usa l'ora attuale. */
    private fun occurredAt(date: LocalDate, original: Expense?): Long {
        val zone = ZoneId.systemDefault()
        val time = when {
            original != null -> Instant.ofEpochMilli(original.occurredAt).atZone(zone).toLocalTime()
            date == LocalDate.now() -> LocalTime.now()
            else -> LocalTime.NOON
        }
        return date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    }
}
