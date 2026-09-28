package com.davidegigante.spesesmart.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.davidegigante.spesesmart.data.Dashboard
import com.davidegigante.spesesmart.domain.FixedLine
import com.davidegigante.spesesmart.domain.Money
import com.davidegigante.spesesmart.domain.toLocalDate
import com.davidegigante.spesesmart.notifications.TestNotifications
import com.davidegigante.spesesmart.ui.components.BudgetBar
import com.davidegigante.spesesmart.ui.components.ExpenseRow
import com.davidegigante.spesesmart.ui.components.Tab
import com.davidegigante.spesesmart.ui.components.TabScaffold
import com.davidegigante.spesesmart.ui.components.Warning
import com.davidegigante.spesesmart.ui.fixed.MarkPaidDialog
import com.davidegigante.spesesmart.ui.formatDayMonth
import com.davidegigante.spesesmart.ui.formatMonth
import com.davidegigante.spesesmart.ui.formatShortDay
import com.davidegigante.spesesmart.ui.settings.BudgetDialog

@Composable
fun HomeScreen(
    onTab: (Tab) -> Unit,
    onAddExpense: () -> Unit,
    onOpenExpense: (Long) -> Unit,
    onOpenFixed: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    var canNotify by remember { mutableStateOf(TestNotifications.canPost(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshDay()
        canNotify = TestNotifications.canPost(context)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canNotify = it }

    var editBudget by remember { mutableStateOf(false) }
    var payLine by remember { mutableStateOf<FixedLine?>(null) }

    TabScaffold(
        title = "Spese Smart",
        tab = Tab.Home,
        onTab = onTab,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddExpense,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Spesa") },
            )
        },
    ) { padding ->
        val d = dashboard ?: return@TabScaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!d.budget.isSet) {
                item { SetBudgetCard(onClick = { editBudget = true }) }
            } else {
                item { WeekCard(d) }
                item { MonthCard(d, onClick = onOpenFixed) }
            }
            if (!canNotify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item {
                    NotificationsCard(onEnable = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) })
                }
            }
            if (d.pending.isNotEmpty()) {
                item {
                    PendingCard(d, onOpen = onOpenExpense, onConfirm = viewModel::confirm, onConfirmAll = viewModel::confirmAll)
                }
            }
            val dueSoon = d.fixedLines.filter { !it.isPaid && !it.dueDate.isAfter(d.today.plusDays(3)) }
            if (dueSoon.isNotEmpty()) {
                item { DueFixedCard(dueSoon, d, onPay = { payLine = it }) }
            }
            item { RecentCard(d, onOpen = onOpenExpense) }
        }
    }

    if (editBudget) {
        BudgetDialog(
            currentCents = dashboard?.budget?.monthlyBudgetCents ?: 0,
            onDismiss = { editBudget = false },
            onSave = {
                viewModel.setBudget(it)
                editBudget = false
            },
        )
    }
    payLine?.let { line ->
        MarkPaidDialog(
            line = line,
            onDismiss = { payLine = null },
            onConfirm = {
                viewModel.markPaid(line, it)
                payLine = null
            },
        )
    }
}

@Composable
private fun SetBudgetCard(onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Imposta il budget mensile", style = MaterialTheme.typography.titleMedium)
            Text(
                "Basta un numero: quanto vuoi spendere in tutto ogni mese, spese fisse comprese. " +
                    "La quota di ogni settimana la calcolo io.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onClick) { Text("Imposta budget") }
        }
    }
}

@Composable
private fun WeekCard(d: Dashboard) {
    val w = d.week
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Questa settimana · ${formatShortDay(w.start)} – ${formatShortDay(w.end)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val over = w.remainingCents < 0
            Text(
                if (over) "Sforato di ${Money.format(-w.remainingCents)}" else "${Money.format(w.remainingCents)} rimasti",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "su ${Money.format(w.quotaCents)} · spesi ${Money.format(w.spentCents)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BudgetBar(w.usedFraction, d.budget.alertPercent, Modifier.padding(top = 4.dp))
            if (w.pendingCents > 0) {
                Text(
                    "di cui ${Money.format(w.pendingCents)} ancora da rivedere → senza quelle: ${Money.format(w.remainingWithoutPendingCents)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Warning,
                )
            }
        }
    }
}

@Composable
private fun MonthCard(d: Dashboard, onClick: () -> Unit) {
    val m = d.month
    Card(onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(formatMonth(m.month), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${Money.format(m.availableCents)} disponibili",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (m.availableCents < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            BudgetBar(m.usedFraction, d.budget.alertPercent, Modifier.padding(vertical = 4.dp))
            MoneyLine("Budget", m.budgetCents)
            MoneyLine(
                if (m.fixedToPayCents > 0) "Spese fisse (${Money.format(m.fixedToPayCents)} da pagare)" else "Spese fisse",
                -m.fixedTotalCents,
            )
            MoneyLine("Spese del mese", -m.spentCents)
        }
    }
}

@Composable
private fun MoneyLine(label: String, cents: Long) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(Money.format(cents), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NotificationsCard(onEnable: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Attiva le notifiche per ricevere gli avvisi di budget e i promemoria delle spese fisse.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEnable) { Text("Attiva") }
        }
    }
}

@Composable
private fun PendingCard(d: Dashboard, onOpen: (Long) -> Unit, onConfirm: (Long) -> Unit, onConfirmAll: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Warning.copy(alpha = 0.12f))) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (d.pending.size == 1) "1 spesa da rivedere" else "${d.pending.size} spese da rivedere",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (d.pending.size > 1) TextButton(onClick = onConfirmAll) { Text("Conferma tutte") }
            }
            Text(
                "Contano già nel budget. Toccale per correggerle o eliminarle.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            d.pending.take(5).forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        ExpenseRow(e, d.categories[e.categoryId], subtitle = null, onClick = { onOpen(e.id) })
                    }
                    IconButton(onClick = { onConfirm(e.id) }) { Icon(Icons.Outlined.Check, contentDescription = "Conferma") }
                }
            }
            if (d.pending.size > 5) {
                Text(
                    "e altre ${d.pending.size - 5}…",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun DueFixedCard(lines: List<FixedLine>, d: Dashboard, onPay: (FixedLine) -> Unit) {
    Card {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text("Spese fisse in scadenza", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
            lines.forEach { line ->
                Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${d.categories[line.fixed.categoryId]?.emoji ?: "📌"} ${line.fixed.name}", style = MaterialTheme.typography.bodyLarge)
                        val late = line.dueDate.isBefore(d.today)
                        Text(
                            (if (late) "Scaduta il " else "Entro il ") + formatDayMonth(line.dueDate) + " · " + Money.format(line.amountCents),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = { onPay(line) }) { Text("Pagata") }
                }
            }
        }
    }
}

@Composable
private fun RecentCard(d: Dashboard, onOpen: (Long) -> Unit) {
    Card {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text("Ultime spese", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
            if (d.recent.isEmpty()) {
                Text(
                    "Ancora nessuna spesa. Premi \"+ Spesa\" per aggiungerne una.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            d.recent.forEachIndexed { i, e ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 68.dp))
                ExpenseRow(
                    e,
                    d.categories[e.categoryId],
                    subtitle = formatShortDay(e.occurredAt.toLocalDate()),
                    onClick = { onOpen(e.id) },
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
