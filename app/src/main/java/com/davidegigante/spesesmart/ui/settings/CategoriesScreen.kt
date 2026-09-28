package com.davidegigante.spesesmart.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.davidegigante.spesesmart.SpeseSmartApp
import com.davidegigante.spesesmart.data.Category
import com.davidegigante.spesesmart.ui.components.DetailScaffold
import kotlinx.coroutines.launch

@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as SpeseSmartApp
    val categoriesFlow = remember { app.repository.observeCategories() }
    val categories by categoriesFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }

    DetailScaffold(
        title = "Categorie",
        onBack = onBack,
        actions = {
            IconButton(onClick = { editing = Category(name = "", emoji = "") }) {
                Icon(Icons.Outlined.Add, contentDescription = "Nuova categoria")
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text(
                    "Quando cambi la categoria di una spesa, l'app se lo ricorda per le prossime spese dallo stesso esercente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(categories, key = { it.id }) { c ->
                ListItem(
                    headlineContent = { Text(c.name) },
                    leadingContent = { Text(c.emoji, style = MaterialTheme.typography.titleLarge) },
                    trailingContent = { TextButton(onClick = { deleting = c }) { Text("Elimina") } },
                    modifier = Modifier.clickable { editing = c },
                )
                HorizontalDivider()
            }
        }
    }

    editing?.let { c ->
        var name by remember(c) { mutableStateOf(c.name) }
        var emoji by remember(c) { mutableStateOf(c.emoji) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (c.id == 0L) "Nuova categoria" else "Modifica categoria") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it.take(4) },
                        label = { Text("Emoji") },
                        singleLine = true,
                        modifier = Modifier.width(120.dp),
                    )
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val updated = c.copy(name = name.trim(), emoji = emoji.trim().ifEmpty { "🏷️" })
                        scope.launch { app.repository.saveCategory(updated) }
                        editing = null
                    },
                ) { Text("Salva") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Annulla") } },
        )
    }

    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Eliminare \"${c.name}\"?") },
            text = { Text("Le spese di questa categoria restano, ma senza categoria.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { app.repository.deleteCategory(c) }
                    deleting = null
                }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annulla") } },
        )
    }
}
