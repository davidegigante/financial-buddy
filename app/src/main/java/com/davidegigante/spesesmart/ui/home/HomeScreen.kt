package com.davidegigante.spesesmart.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
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
import com.davidegigante.spesesmart.ui.components.AnimatedMoney
import com.davidegigante.spesesmart.ui.components.AppCard
import com.davidegigante.spesesmart.ui.components.BudgetBar
import com.davidegigante.spesesmart.ui.components.Donut
import com.davidegigante.spesesmart.ui.components.ExpenseRow
import com.davidegigante.spesesmart.ui.components.ProgressRing
import com.davidegigante.spesesmart.ui.components.Tab
import com.davidegigante.spesesmart.ui.components.TabScaffold
import com.davidegigante.spesesmart.ui.components.Warning
import com.davidegigante.spesesmart.ui.fixed.MarkPaidDialog
import com.davidegigante.spesesmart.ui.formatDayMonth
import com.davidegigante.spesesmart.ui.formatFullDate
import com.davidegigante.spesesmart.ui.formatMonth
import com.davidegigante.spesesmart.ui.formatShortDay
import com.davidegigante.spesesmart.ui.settings.BudgetDialog
import com.davidegigante.spesesmart.ui.theme.AppTheme
import com.davidegigante.spesesmart.ui.theme.TABULAR

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
        subtitle = dashboard?.let { formatFullDate(it.today) },
        tab = Tab.Home,
        onTab = onTab,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddExpense,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Spesa") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        val d = dashboard ?: return@TabScaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!d.budget.isSet) {
                item { SetBudgetCard(onClick = { editBudget = true }) }
            } else {
                item { WeekHero(d) }
                item { MonthCard(d, onClick = onOpenFixed) }
            }
            if (!canNotify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item { NotificationsCard(onEnable = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) }
            }
            if (d.pending.isNotEmpty()) {
                item { PendingCard(d, onOpen = onOpenExpense, onConfirm = viewModel::confirm, onConfirmAll = viewModel::confirmAll) }
            }
            val dueSoon = d.fixedLines.filter { !it.isPaid && !it.dueDate.isAfter(d.today.plusDays(3)) }
            if (dueSoon.isNotEmpty()) {
                item { DueFixedCard(dueSoon, d, onPay = { payLine = it }) }
            }
            if (d.monthByCategory.isNotEmpty()) {
                item { CategoriesCard(d) }
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
private fun SectionHeader(title: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
private fun SetBudgetCard(onClick: () -> Unit) {
    val colors = AppTheme.colors
    Box(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(colors.heroGradient).padding(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("👋 Iniziamo", style = MaterialTheme.typography.titleLarge, color = colors.onHero)
            Text(
                "Imposta quanto vuoi spendere in tutto ogni mese, spese fisse comprese. " +
                    "La quota di ogni settimana la calcolo io.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onHero.copy(alpha = 0.9f),
            )
            Button(
                onClick = onClick,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = colors.onHero, contentColor = MaterialTheme.colorScheme.primary),
            ) { Text("Imposta budget") }
        }
    }
}

/** La card principale: quanto resta questa settimana, su sfondo sfumato acqua → azzurro. */
@Composable
private fun WeekHero(d: Dashboard) {
    val w = d.week
    val colors = AppTheme.colors
    val over = w.remainingCents < 0
    Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(colors.heroGradient).padding(22.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Questa settimana",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onHero.copy(alpha = 0.85f),
                    )
                    Text(
                        "${formatShortDay(w.start)} – ${formatShortDay(w.end)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onHero.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(10.dp))
                    AnimatedMoney(
                        cents = if (over) -w.remainingCents else w.remainingCents,
                        style = MaterialTheme.typography.displaySmall,
                        color = colors.onHero,
                    )
                    Text(
                        if (over) "oltre il budget" else "ancora da spendere",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onHero.copy(alpha = 0.9f),
                    )
                }
                ProgressRing(
                    fraction = w.usedFraction,
                    color = if (over) Color(0xFFFFB4A9) else colors.onHero,
                    trackColor = colors.onHero.copy(alpha = 0.22f),
                    size = 92.dp,
                    stroke = 9.dp,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${(w.usedFraction * 100).toInt().coerceAtMost(999)}%",
                            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = TABULAR),
                            color = colors.onHero,
                        )
                        Text("usato", style = MaterialTheme.typography.labelSmall, color = colors.onHero.copy(alpha = 0.8f))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroChip("Quota ${Money.format(w.quotaCents)}")
                HeroChip("Spesi ${Money.format(w.spentCents)}")
            }
            if (w.pendingCents > 0) {
                Spacer(Modifier.height(8.dp))
                Surface(color = colors.onHero.copy(alpha = 0.16f), shape = MaterialTheme.shapes.small) {
                    Text(
                        "di cui ${Money.format(w.pendingCents)} da rivedere → senza quelle: ${Money.format(w.remainingWithoutPendingCents)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onHero,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroChip(text: String) {
    Surface(color = AppTheme.colors.onHero.copy(alpha = 0.16f), shape = CircleShape) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TABULAR),
            color = AppTheme.colors.onHero,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun MonthCard(d: Dashboard, onClick: () -> Unit) {
    val m = d.month
    AppCard(onClick = onClick) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(formatMonth(m.month), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AnimatedMoney(
                        m.availableCents,
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (m.availableCents < 0) AppTheme.colors.danger else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text("disponibili", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BudgetBar(m.usedFraction, d.budget.alertPercent)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Budget", m.budgetCents, Modifier.weight(1f))
                StatTile("Fisse", m.fixedTotalCents, Modifier.weight(1f), note = if (m.fixedToPayCents > 0) "${Money.format(m.fixedToPayCents)}\nda pagare" else "tutte pagate ✓")
                StatTile("Spese", m.spentCents, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatTile(label: String, cents: Long, modifier: Modifier, note: String? = null) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                Money.format(cents),
                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TABULAR),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            note?.let {
                Text(it, style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR), color = AppTheme.colors.warning, maxLines = 2)
            }
        }
    }
}

@Composable
private fun NotificationsCard(onEnable: () -> Unit) {
    AppCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "🔔 Attiva le notifiche per ricevere gli avvisi di budget e i promemoria delle spese fisse.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEnable) { Text("Attiva") }
        }
    }
}

@Composable
private fun PendingCard(d: Dashboard, onOpen: (Long) -> Unit, onConfirm: (Long) -> Unit, onConfirmAll: () -> Unit) {
    AppCard {
        Column(Modifier.padding(bottom = 8.dp)) {
            SectionHeader(
                if (d.pending.size == 1) "1 spesa da rivedere" else "${d.pending.size} spese da rivedere",
                action = if (d.pending.size > 1) ({ TextButton(onClick = onConfirmAll) { Text("Conferma tutte") } }) else null,
            )
            Text(
                "Lette dalle notifiche: contano già nel budget. Toccale per correggerle o eliminarle.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            d.pending.take(5).forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        ExpenseRow(e, d.categories[e.categoryId], subtitle = formatShortDay(e.occurredAt.toLocalDate()), onClick = { onOpen(e.id) })
                    }
                    FilledTonalIconButton(onClick = { onConfirm(e.id) }, modifier = Modifier.padding(end = 12.dp)) {
                        Icon(Icons.Outlined.Check, contentDescription = "Conferma")
                    }
                }
            }
            if (d.pending.size > 5) {
                Text("e altre ${d.pending.size - 5}…", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
    }
}

@Composable
private fun DueFixedCard(lines: List<FixedLine>, d: Dashboard, onPay: (FixedLine) -> Unit) {
    AppCard {
        Column(Modifier.padding(bottom = 12.dp)) {
            SectionHeader("Spese fisse in scadenza")
            lines.forEach { line ->
                Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${d.categories[line.fixed.categoryId]?.emoji ?: "📌"} ${line.fixed.name}", style = MaterialTheme.typography.bodyLarge)
                        val late = line.dueDate.isBefore(d.today)
                        Text(
                            (if (late) "Scaduta il " else "Entro il ") + formatDayMonth(line.dueDate) + " · " + Money.format(line.amountCents),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (late) AppTheme.colors.danger else Warning,
                        )
                    }
                    OutlinedButton(onClick = { onPay(line) }) { Text("Pagata") }
                }
            }
        }
    }
}

/** "Dove vanno i soldi": ciambella per categoria e le 5 voci principali. */
@Composable
private fun CategoriesCard(d: Dashboard) {
    val colors = AppTheme.colors
    val slices = d.monthByCategory.map { (id, cents) -> colors.category(id) to cents }
    val total = d.monthByCategory.sumOf { it.second }
    AppCard {
        Column(Modifier.padding(bottom = 16.dp)) {
            SectionHeader("Dove vanno i soldi · ${formatMonth(d.month.month).substringBefore(' ')}")
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Donut(slices, Modifier.size(120.dp), stroke = 16.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("totale", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            Money.format(total).substringBefore(','),
                            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TABULAR),
                        )
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    d.monthByCategory.take(5).forEach { (id, cents) ->
                        val c = id?.let { d.categories[it] }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(colors.category(id)))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                c?.name ?: "Senza categoria",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${(cents * 100 / total.coerceAtLeast(1))}%",
                                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TABULAR),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentCard(d: Dashboard, onOpen: (Long) -> Unit) {
    AppCard {
        Column(Modifier.padding(bottom = 8.dp)) {
            SectionHeader("Ultime spese")
            if (d.recent.isEmpty()) {
                Text(
                    "Ancora nessuna spesa. Premi \"+ Spesa\" per aggiungerne una.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
            d.recent.forEachIndexed { i, e ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 74.dp, end = 16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh)
                ExpenseRow(e, d.categories[e.categoryId], subtitle = formatShortDay(e.occurredAt.toLocalDate()), onClick = { onOpen(e.id) })
            }
        }
    }
}


