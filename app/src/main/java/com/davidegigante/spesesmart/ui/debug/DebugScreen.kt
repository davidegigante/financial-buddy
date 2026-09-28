package com.davidegigante.spesesmart.ui.debug

import android.Manifest
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.davidegigante.spesesmart.data.CaptureSettings
import com.davidegigante.spesesmart.data.CapturedNotification
import com.davidegigante.spesesmart.notifications.TestNotifications
import com.davidegigante.spesesmart.ui.formatDateTime
import com.davidegigante.spesesmart.ui.formatShortDateTime
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(viewModel: DebugViewModel = viewModel()) {
    val context = LocalContext.current
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val onlyWatched by viewModel.onlyWatched.collectAsStateWithLifecycle()

    var listenerEnabled by remember { mutableStateOf(isListenerEnabled(context)) }
    var canPostTest by remember { mutableStateOf(TestNotifications.canPost(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        listenerEnabled = isListenerEnabled(context)
        canPostTest = TestNotifications.canPost(context)
    }

    // Aggiorna ogni minuto il tempo residuo della modalità scoperta.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canPostTest = granted
        if (granted) TestNotifications.post(context)
    }

    var confirmDeleteAll by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifiche ricevute") },
                actions = {
                    IconButton(onClick = { confirmDeleteAll = true }) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = "Cancella tutte")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { PermissionCard(listenerEnabled, context) }
            item {
                DiscoveryCard(
                    settings = settings,
                    now = now,
                    onToggle = { enabled ->
                        now = System.currentTimeMillis()
                        viewModel.setDiscovery(enabled)
                    },
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = !onlyWatched,
                        onClick = { viewModel.onlyWatched.value = false },
                        label = { Text("Tutte") },
                    )
                    FilterChip(
                        selected = onlyWatched,
                        onClick = { viewModel.onlyWatched.value = true },
                        label = { Text("Solo monitorate") },
                    )
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = {
                        if (canPostTest) {
                            TestNotifications.post(context)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }) { Text("Prova") }
                }
            }
            if (notifications.isEmpty()) {
                item {
                    Text(
                        "Nessuna notifica salvata. Attiva l'accesso alle notifiche e la modalità scoperta, " +
                            "poi fai un pagamento o premi \"Prova\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(notifications, key = { it.id }) { n ->
                NotificationCard(
                    notification = n,
                    watched = n.packageName in settings.watchedPackages,
                    onCopy = { copyToClipboard(context, n.toDebugString(formatDateTime(n.postTime))) },
                    onToggleWatched = { viewModel.toggleWatched(n.packageName) },
                    onDelete = { viewModel.delete(n.id) },
                )
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Cancellare tutte le notifiche?") },
            text = { Text("Le notifiche salvate verranno eliminate. Le impostazioni restano.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAll()
                    confirmDeleteAll = false
                }) { Text("Cancella") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun PermissionCard(enabled: Boolean, context: Context) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (enabled) "Accesso alle notifiche: attivo" else "Accesso alle notifiche: NON attivo",
                style = MaterialTheme.typography.titleMedium,
            )
            if (!enabled) {
                Text(
                    "Se l'interruttore è grigio (\"Impostazione con limitazioni\"): apri Info app → " +
                        "menu ⋮ in alto a destra → \"Consenti impostazioni con limitazioni\", poi riprova.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    "Consiglio: in Info app → Batteria scegli \"Senza limitazioni\", " +
                        "altrimenti Samsung può fermare la lettura delle notifiche.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }) { Text("Accesso notifiche") }
                OutlinedButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                    )
                }) { Text("Info app") }
            }
        }
    }
}

@Composable
private fun DiscoveryCard(settings: CaptureSettings.State, now: Long, onToggle: (Boolean) -> Unit) {
    val active = settings.isDiscoveryActive(now)
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Modalità scoperta", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (active) "Attiva: salva le notifiche di tutte le app. Si spegne alle " +
                            formatShortDateTime(settings.discoveryUntil) + "."
                        else "Spenta: salva solo le app monitorate.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = active, onCheckedChange = onToggle)
            }
            Text(
                "Le notifiche di app non monitorate vengono cancellate dopo 24 ore.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "App monitorate: " + settings.watchedPackages.sorted().joinToString().ifEmpty { "nessuna" },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun NotificationCard(
    notification: CapturedNotification,
    watched: Boolean,
    onCopy: () -> Unit,
    onToggleWatched: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (watched) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(notification.appLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        notification.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(formatDateTime(notification.postTime), style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onToggleWatched) {
                    Icon(
                        if (watched) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        contentDescription = if (watched) "Smetti di monitorare" else "Monitora questa app",
                    )
                }
                IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copia") }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Elimina") }
            }
            SelectionContainer {
                Column(
                    Modifier.padding(end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    notification.title?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
                    notification.text?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    notification.bigText?.takeIf { it.isNotBlank() && it != notification.text }?.let {
                        Text("Testo esteso:", style = MaterialTheme.typography.labelSmall)
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    notification.subText?.takeIf { it.isNotBlank() }?.let {
                        Text("Sottotesto: $it", style = MaterialTheme.typography.bodySmall)
                    }
                    notification.textLines?.takeIf { it.isNotBlank() }?.let {
                        Text("Righe:", style = MaterialTheme.typography.labelSmall)
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun isListenerEnabled(context: Context): Boolean =
    context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val clip = ClipData.newPlainText("Notifica", text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Nasconde l'anteprima del testo copiato: può contenere dati bancari.
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Copiato", Toast.LENGTH_SHORT).show()
    }
}
