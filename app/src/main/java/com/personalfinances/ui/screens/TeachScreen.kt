package com.personalfinances.ui.screens

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personalfinances.data.db.UnrecognizedEntity
import com.personalfinances.domain.Currency
import com.personalfinances.domain.Money
import com.personalfinances.notifications.NotificationParser
import java.text.DateFormat
import java.util.Date

/** Notificaciones con montos que el lector no entendió; el usuario le enseña cómo leerlas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeachScreen(
    items: List<UnrecognizedEntity>,
    mainCurrency: Currency,
    onBack: () -> Unit,
    onTeach: (UnrecognizedEntity, kind: String, amountOrdinal: Int, prefix: String?, suffix: String?) -> Unit,
    onDismiss: (Long) -> Unit,
) {
    var selected by remember { mutableStateOf<UnrecognizedEntity?>(null) }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Enséñale al lector", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Text(
                    "Estas notificaciones tenían montos, pero no supe leerlas. Toca una y muéstrame qué es: la próxima vez que llegue una igual la reconoceré sola.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp),
                )
            }
            if (items.isEmpty()) item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { Text("Nada pendiente ✨", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(items, key = { it.id }) { u ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large,
                    modifier = Modifier.animateItem().fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).clickable { selected = u }) {
                    Column(Modifier.padding(16.dp)) {
                        Text("${u.appLabel} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(u.postedAt))}",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (u.title.isNotBlank()) Text(u.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(u.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                    }
                }
            }
        }
    }
    selected?.let { u -> TeachDialog(u, mainCurrency, onClose = { selected = null }, onSave = { k, o, p, s -> onTeach(u, k, o, p, s); selected = null }, onDiscard = { onDismiss(u.id); selected = null }) }
}

private val kinds = listOf("EXPENSE" to "Gasto", "INCOME" to "Ingreso", "TRANSFER" to "Transferencia", "IGNORE" to "Ignorar siempre")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeachDialog(u: UnrecognizedEntity, currency: Currency, onClose: () -> Unit, onSave: (String, Int, String?, String?) -> Unit, onDiscard: () -> Unit) {
    val full = NotificationParser.fullText(u.title, u.text)
    val amounts = remember(u.id) { NotificationParser.amounts(full, currency) }
    val words = remember(u.id) { NotificationParser.words(full) }
    var kind by remember { mutableStateOf("EXPENSE") }
    var amountIdx by remember { mutableIntStateOf(0) }
    var start by remember { mutableIntStateOf(-1) }
    var end by remember { mutableIntStateOf(-1) }
    fun clean(w: String) = w.lowercase().trim { !it.isLetterOrDigit() }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("¿Qué es esta notificación?") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(full, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    kinds.forEach { (k, label) -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(label) }) }
                }
                if (kind != "IGNORE") {
                    Text("Monto", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        amounts.forEachIndexed { i, (v, c) -> FilterChip(selected = amountIdx == i, onClick = { amountIdx = i }, label = { Text(Money.format(v, c)) }) }
                    }
                    Text("Comercio (opcional): toca la primera y la última palabra", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        words.forEachIndexed { i, w ->
                            val inRange = start >= 0 && i in start..maxOf(start, end)
                            FilterChip(selected = inRange, onClick = {
                                when {
                                    start < 0 || end >= 0 -> { start = i; end = -1 }
                                    i >= start -> end = i
                                    else -> { start = i; end = -1 }
                                }
                            }, label = { Text(w) })
                        }
                    }
                    if (start >= 0) {
                        val e = if (end >= 0) end else start
                        Text("Comercio: ${words.subList(start, e + 1).joinToString(" ").trim { !it.isLetterOrDigit() }}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                TextButton(onClick = onDiscard) { Text("Descartar solo esta", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = kind == "IGNORE" || amounts.isNotEmpty(), onClick = {
                val e = if (end >= 0) end else start
                val prefix = if (start > 0) clean(words[start - 1]).ifBlank { null } else null
                val suffix = if (start >= 0 && e + 1 < words.size) clean(words[e + 1]).ifBlank { null } else null
                onSave(kind, amountIdx, prefix, suffix)
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } },
    )
}

