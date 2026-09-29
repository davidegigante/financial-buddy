package com.davidegigante.spesesmart.ui.fixed

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import com.davidegigante.spesesmart.ui.components.AppCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.davidegigante.spesesmart.SpeseSmartApp
import com.davidegigante.spesesmart.data.Category
import com.davidegigante.spesesmart.domain.FixedLine
import com.davidegigante.spesesmart.domain.Money
import com.davidegigante.spesesmart.ui.components.AmountField
import com.davidegigante.spesesmart.ui.components.EmptyHint
import com.davidegigante.spesesmart.ui.components.Tab
import com.davidegigante.spesesmart.ui.components.TabScaffold
import com.davidegigante.spesesmart.ui.components.Warning
import com.davidegigante.spesesmart.ui.formatDayMonth
import com.davidegigante.spesesmart.ui.formatMonth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class FixedState(val month: YearMonth, val today: LocalDate, val lines: List<FixedLine>, val categories: Map<Long, Category>) {
    val totalCents: Long get() = lines.sumOf { it.amountCents }
    val toPayCents: Long get() = lines.filterNot { it.isPaid }.sumOf { it.amountCents }
}

class FixedViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SpeseSmartApp
    private val repo = app.repository
    private val month = YearMonth.now()

    val state: StateFlow<FixedState?> =
        combine(repo.observeFixed(), repo.observePaymentsFrom(month), repo.observeCategories()) { fixed, payments, cats ->
            FixedState(month, LocalDate.now(), repo.calculator.fixedLines(month, fixed, payments), cats.associateBy { it.id })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun markPaid(line: FixedLine, amountCents: Long) =
        app.appScope.launch { repo.markFixedPaid(line.fixed.id, line.month, amountCents) }

    fun unmarkPaid(line: FixedLine) = app.appScope.launch { repo.unmarkFixedPaid(line.fixed.id, line.month) }
}

@Composable
fun FixedScreen(onTab: (Tab) -> Unit, onAdd: () -> Unit, onOpen: (Long) -> Unit, viewModel: FixedViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var payLine by remember { mutableStateOf<FixedLine?>(null) }
    var unpayLine by remember { mutableStateOf<FixedLine?>(null) }

    TabScaffold(
        title = "Spese fisse",
        tab = Tab.Fixed,
        onTab = onTab,
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Icon(Icons.Outlined.Add, contentDescription = "Nuova spesa fissa") }
        },
    ) { padding ->
        val s = state ?: return@TabScaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(formatMonth(s.month), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Accantonate: ${Money.format(s.totalCents)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (s.toPayCents > 0) "Ancora da pagare: ${Money.format(s.toPayCents)}" else "Tutte pagate",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Vengono tolte dal budget a inizio mese, una volta sola. Segnarle come pagate non toglie altro: " +
                                "se l'importo reale è diverso, la differenza torna (o esce) dal disponibile.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (s.lines.isEmpty()) {
                item { EmptyHint("Nessuna spesa fissa. Aggiungi affitto, bollette, abbonamenti... con il pulsante +.") }
            }
            if (s.lines.isNotEmpty()) {
                item {
                    AppCard {
                        Column {
                            s.lines.forEachIndexed { i, line ->
                                if (i > 0) HorizontalDivider()
                                FixedRow(
                                    line = line,
                                    category = s.categories[line.fixed.categoryId],
                                    today = s.today,
                                    onClick = { onOpen(line.fixed.id) },
                                    onPay = { payLine = line },
                                    onUnpay = { unpayLine = line },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    payLine?.let { line ->
        MarkPaidDialog(line, onDismiss = { payLine = null }, onConfirm = { viewModel.markPaid(line, it); payLine = null })
    }
    unpayLine?.let { line ->
        AlertDialog(
            onDismissRequest = { unpayLine = null },
            title = { Text("Segnare come da pagare?") },
            text = { Text("${line.fixed.name} tornerà \"da pagare\" con l'importo previsto di ${Money.format(line.fixed.amountCents)}.") },
            confirmButton = { TextButton(onClick = { viewModel.unmarkPaid(line); unpayLine = null }) { Text("Sì") } },
            dismissButton = { TextButton(onClick = { unpayLine = null }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun FixedRow(line: FixedLine, category: Category?, today: LocalDate, onClick: () -> Unit, onPay: () -> Unit, onUnpay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${category?.emoji ?: "📌"} ${line.fixed.name}", style = MaterialTheme.typography.bodyLarge)
            Text(Money.format(line.amountCents), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            val (status, color) = when {
                line.isPaid -> "Pagata" to MaterialTheme.colorScheme.primary
                line.dueDate.isBefore(today) -> "Scaduta il ${formatDayMonth(line.dueDate)}" to MaterialTheme.colorScheme.error
                else -> "Da pagare entro il ${formatDayMonth(line.dueDate)}" to Warning
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = color)
        }
        if (line.isPaid) {
            TextButton(onClick = onUnpay) { Text("Annulla") }
        } else {
            OutlinedButton(onClick = onPay) { Text("Pagata") }
        }
    }
}

/** Chiede l'importo pagato davvero (precompilato con quello previsto). */
@Composable
fun MarkPaidDialog(line: FixedLine, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var text by remember { mutableStateOf(Money.toInput(line.fixed.amountCents)) }
    val cents = Money.parse(text)?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${line.fixed.name}: pagata") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Quanto hai pagato? Se è diverso dal previsto, la differenza viene sistemata nel disponibile.")
                AmountField(text, { text = it }, label = "Importo pagato", isError = cents == null)
            }
        },
        confirmButton = { TextButton(onClick = { cents?.let(onConfirm) }, enabled = cents != null) { Text("Conferma") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}
