package com.davidegigante.spesesmart.ui.expenses

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.davidegigante.spesesmart.SpeseSmartApp
import com.davidegigante.spesesmart.data.Category
import com.davidegigante.spesesmart.data.Expense
import com.davidegigante.spesesmart.domain.Money
import com.davidegigante.spesesmart.domain.toLocalDate
import com.davidegigante.spesesmart.ui.components.EmptyHint
import com.davidegigante.spesesmart.ui.components.ExpenseRow
import com.davidegigante.spesesmart.ui.components.Tab
import com.davidegigante.spesesmart.ui.components.TabScaffold
import com.davidegigante.spesesmart.ui.formatDayHeader
import com.davidegigante.spesesmart.ui.formatMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

data class DayGroup(val date: LocalDate, val totalCents: Long, val expenses: List<Expense>)

data class ExpensesState(
    val month: YearMonth,
    val totalCents: Long,
    val days: List<DayGroup>,
    val categories: Map<Long, Category>,
)

class ExpensesViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as SpeseSmartApp).repository

    // Resta impostato mentre si apre e si chiude una spesa, così si torna allo stesso mese.
    val month = MutableStateFlow(YearMonth.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ExpensesState?> = month
        .flatMapLatest { m ->
            combine(repo.observeExpensesBetween(m.atDay(1), m.plusMonths(1).atDay(1)), repo.observeCategories()) { list, cats ->
                ExpensesState(
                    month = m,
                    totalCents = list.sumOf { it.amountCents },
                    days = list.groupBy { it.occurredAt.toLocalDate() }
                        .map { (date, items) -> DayGroup(date, items.sumOf { it.amountCents }, items) },
                    categories = cats.associateBy { it.id },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun ExpensesScreen(
    onTab: (Tab) -> Unit,
    onAddExpense: () -> Unit,
    onOpenExpense: (Long) -> Unit,
    viewModel: ExpensesViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    TabScaffold(
        title = "Spese",
        tab = Tab.Expenses,
        onTab = onTab,
        floatingActionButton = {
            FloatingActionButton(onClick = onAddExpense) { Icon(Icons.Outlined.Add, contentDescription = "Nuova spesa") }
        },
    ) { padding ->
        val s = state ?: return@TabScaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.month.value = s.month.minusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Mese precedente")
                    }
                    Text(
                        formatMonth(s.month),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { viewModel.month.value = s.month.plusMonths(1) },
                        enabled = s.month < YearMonth.now(),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Mese successivo")
                    }
                }
                Text(
                    "Totale spese: ${Money.format(s.totalCents)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
            }
            if (s.days.isEmpty()) {
                item { EmptyHint("Nessuna spesa in questo mese.") }
            }
            s.days.forEach { day ->
                item(key = day.date.toString()) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
                        Text(
                            formatDayHeader(day.date),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            Money.format(day.totalCents),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(day.expenses, key = { it.id }) { e ->
                    ExpenseRow(e, s.categories[e.categoryId], subtitle = null, onClick = { onOpenExpense(e.id) })
                }
            }
        }
    }
}
