package com.davidegigante.spesesmart.ui.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.davidegigante.spesesmart.SpeseSmartApp
import com.davidegigante.spesesmart.ui.components.AmountField
import com.davidegigante.spesesmart.ui.components.CategoryPicker
import com.davidegigante.spesesmart.ui.components.DetailScaffold
import com.davidegigante.spesesmart.ui.components.Warning
import com.davidegigante.spesesmart.ui.formatFullDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditorScreen(expenseId: Long?, onDone: () -> Unit) {
    val app = LocalContext.current.applicationContext as SpeseSmartApp
    val viewModel: ExpenseEditorViewModel = viewModel(key = "expense-$expenseId") { ExpenseEditorViewModel(app, expenseId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }

    val s = state
    DetailScaffold(
        title = when {
            expenseId == null -> "Nuova spesa"
            s?.isPending == true -> "Spesa da rivedere"
            else -> "Modifica spesa"
        },
        onBack = onDone,
        actions = {
            if (expenseId != null) {
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Elimina") }
            }
        },
        bottomBar = {
            if (s != null) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (s.isPending) {
                        OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) { Text("Elimina") }
                        Button(
                            onClick = { showErrors = true; viewModel.save(confirm = true, onDone) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Conferma") }
                    } else {
                        Button(
                            onClick = { showErrors = true; viewModel.save(confirm = false, onDone) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Salva") }
                    }
                }
            }
        },
    ) { padding ->
        if (s == null) return@DetailScaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (s.isPending) {
                Text(
                    "Letta da una notifica: controlla importo, esercente e categoria. Conta già nel budget.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Warning,
                )
            }
            AmountField(
                value = s.amountText,
                onValueChange = viewModel::onAmountChange,
                label = "Importo",
                isError = showErrors && !s.amountValid,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = s.merchant,
                onValueChange = viewModel::onMerchantChange,
                label = { Text("Dove / cosa (es. Esselunga)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Categoria", style = MaterialTheme.typography.labelLarge)
                if (s.suggested) {
                    Text(
                        "Suggerita in base alle spese precedenti da \"${s.merchant.trim()}\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CategoryPicker(categories, s.categoryId, viewModel::onCategoryChange)
            }
            OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                Text(formatFullDate(s.date), modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedTextField(
                value = s.note,
                onValueChange = viewModel::onNoteChange,
                label = { Text("Nota (facoltativa)") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            s.rawText?.let { raw ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Notifica originale", style = MaterialTheme.typography.labelLarge)
                        SelectionContainer {
                            Text(raw, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }

    if (pickDate && s != null) {
        // Il DatePicker lavora in UTC: convertiamo solo la data, senza fuso orario.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = s.date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    utcTimeMillis <= LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        viewModel.onDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Annulla") } },
        ) { DatePicker(state = pickerState) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare la spesa?") },
            text = { Text("L'importo torna disponibile nel budget.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onDone)
                }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } },
        )
    }
}
