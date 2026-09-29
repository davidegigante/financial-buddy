package com.davidegigante.spesesmart.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import com.davidegigante.spesesmart.ui.components.AppCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidegigante.spesesmart.SpeseSmartApp
import com.davidegigante.spesesmart.domain.Money
import com.davidegigante.spesesmart.ui.components.AmountField
import com.davidegigante.spesesmart.ui.components.Tab
import com.davidegigante.spesesmart.ui.components.TabScaffold

@Composable
fun SettingsScreen(onTab: (Tab) -> Unit, onOpenCategories: () -> Unit, onOpenDebug: () -> Unit) {
    val app = LocalContext.current.applicationContext as SpeseSmartApp
    val budget by app.budgetSettings.state.collectAsStateWithLifecycle()
    var editBudget by remember { mutableStateOf(false) }

    TabScaffold(title = "Altro", tab = Tab.More, onTab = onTab) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard {
                    ListItem(
                        headlineContent = { Text("Budget mensile") },
                        supportingContent = {
                            Text(if (budget.isSet) Money.format(budget.monthlyBudgetCents) + " · spese fisse comprese" else "Non impostato")
                        },
                        leadingContent = { Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null) },
                        trailingContent = { TextButton(onClick = { editBudget = true }) { Text("Modifica") } },
                    )
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Primo avviso quando arrivi al", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(70, 80, 90).forEach { p ->
                                FilterChip(
                                    selected = budget.alertPercent == p,
                                    onClick = { app.budgetSettings.setAlertPercent(p) },
                                    label = { Text("$p%") },
                                )
                            }
                        }
                        Text(
                            "Vale per la settimana e per il mese. Un secondo avviso arriva quando superi il budget.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                AppCard(onClick = onOpenCategories) {
                    ListItem(
                        headlineContent = { Text("Categorie") },
                        supportingContent = { Text("Aggiungi, rinomina o elimina") },
                        leadingContent = { Icon(Icons.Outlined.Category, contentDescription = null) },
                        trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
                    )
                }
            }
            item {
                AppCard(onClick = onOpenDebug) {
                    ListItem(
                        headlineContent = { Text("Notifiche catturate") },
                        supportingContent = { Text("Accesso alle notifiche, modalità scoperta, testi grezzi") },
                        leadingContent = { Icon(Icons.Outlined.BugReport, contentDescription = null) },
                        trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
                    )
                }
            }
            item {
                AppCard {
                    ListItem(
                        headlineContent = { Text("Privacy") },
                        supportingContent = {
                            Text(
                                "L'app non ha accesso a internet: Android le impedisce qualsiasi connessione. " +
                                    "Tutti i dati restano su questo telefono e non vanno nel backup di Google.",
                            )
                        },
                        leadingContent = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    )
                }
            }
        }
    }

    if (editBudget) {
        BudgetDialog(
            currentCents = budget.monthlyBudgetCents,
            onDismiss = { editBudget = false },
            onSave = {
                app.budgetSettings.setMonthlyBudget(it)
                app.checkBudgetSoon()
                editBudget = false
            },
        )
    }
}

@Composable
fun BudgetDialog(currentCents: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by remember { mutableStateOf(if (currentCents > 0) Money.toInput(currentCents) else "") }
    val cents = Money.parse(text)?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Budget mensile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Quanto vuoi spendere in tutto ogni mese, spese fisse comprese.")
                AmountField(text, { text = it }, label = "Budget al mese")
            }
        },
        confirmButton = { TextButton(onClick = { cents?.let(onSave) }, enabled = cents != null) { Text("Salva") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}
