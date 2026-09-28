package com.davidegigante.spesesmart.ui.fixed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.davidegigante.spesesmart.SpeseSmartApp
import com.davidegigante.spesesmart.data.Category
import com.davidegigante.spesesmart.data.FixedExpense
import com.davidegigante.spesesmart.domain.Money
import com.davidegigante.spesesmart.domain.toKey
import com.davidegigante.spesesmart.ui.components.AmountField
import com.davidegigante.spesesmart.ui.components.CategoryPicker
import com.davidegigante.spesesmart.ui.components.DetailScaffold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class FixedForm(
    val original: FixedExpense?,
    val name: String,
    val amountText: String,
    val dueDayText: String,
    val categoryId: Long?,
) {
    val amountCents: Long? get() = Money.parse(amountText)?.takeIf { it > 0 }
    val dueDay: Int? get() = dueDayText.toIntOrNull()?.takeIf { it in 1..31 }
    val valid: Boolean get() = name.isNotBlank() && amountCents != null && dueDay != null
}

class FixedEditorViewModel(private val app: SpeseSmartApp, private val fixedId: Long?) : ViewModel() {
    private val repo = app.repository

    val state = MutableStateFlow<FixedForm?>(null)
    val categories: StateFlow<List<Category>> =
        repo.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val f = fixedId?.let { repo.getFixed(it) }
            state.value = if (f != null) {
                FixedForm(f, f.name, Money.toInput(f.amountCents), f.dueDay.toString(), f.categoryId)
            } else {
                FixedForm(null, "", "", "1", null)
            }
        }
    }

    fun update(block: (FixedForm) -> FixedForm) = state.update { it?.let(block) }

    fun save(onDone: () -> Unit) {
        val s = state.value ?: return
        if (!s.valid) return
        val item = s.original?.copy(name = s.name.trim(), amountCents = s.amountCents!!, dueDay = s.dueDay!!, categoryId = s.categoryId)
            ?: FixedExpense(
                name = s.name.trim(),
                amountCents = s.amountCents!!,
                dueDay = s.dueDay!!,
                categoryId = s.categoryId,
                // Una spesa fissa nuova vale già da questo mese.
                startMonth = YearMonth.now().toKey(),
            )
        app.appScope.launch { repo.saveFixed(item) }
        onDone()
    }

    fun delete(onDone: () -> Unit) {
        val original = state.value?.original ?: return
        app.appScope.launch { repo.deleteFixed(original, LocalDate.now()) }
        onDone()
    }
}

@Composable
fun FixedEditorScreen(fixedId: Long?, onDone: () -> Unit) {
    val app = LocalContext.current.applicationContext as SpeseSmartApp
    val viewModel: FixedEditorViewModel = viewModel(key = "fixed-$fixedId") { FixedEditorViewModel(app, fixedId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var showErrors by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    DetailScaffold(
        title = if (fixedId == null) "Nuova spesa fissa" else "Modifica spesa fissa",
        onBack = onDone,
        actions = {
            if (fixedId != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Elimina") }
        },
        bottomBar = {
            Button(
                onClick = { showErrors = true; viewModel.save(onDone) },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp),
            ) { Text("Salva") }
        },
    ) { padding ->
        val s = state ?: return@DetailScaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = s.name,
                onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                label = { Text("Nome (es. Affitto, Psicologa)") },
                singleLine = true,
                isError = showErrors && s.name.isBlank(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            AmountField(
                value = s.amountText,
                onValueChange = { v -> viewModel.update { it.copy(amountText = v) } },
                label = "Importo previsto al mese",
                isError = showErrors && s.amountCents == null,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = s.dueDayText,
                onValueChange = { v -> viewModel.update { it.copy(dueDayText = v.filter(Char::isDigit).take(2)) } },
                label = { Text("Da pagare entro il giorno (1-31)") },
                supportingText = { Text("Se passa questo giorno e non è segnata come pagata, ricevi un promemoria.") },
                singleLine = true,
                isError = showErrors && s.dueDay == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Categoria", style = MaterialTheme.typography.labelLarge)
                CategoryPicker(categories, s.categoryId) { id -> viewModel.update { it.copy(categoryId = id) } }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare la spesa fissa?") },
            text = {
                Text(
                    "Non verrà più accantonata dai prossimi mesi. Se questo mese l'hai già pagata resta nei conti di questo mese; " +
                        "altrimenti sparisce anche da questo mese.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete(onDone) }) { Text("Elimina") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } },
        )
    }
}
