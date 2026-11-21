package com.personalfinances.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.personalfinances.data.FinanceSnapshot
import com.personalfinances.data.db.RecurringEntity
import com.personalfinances.data.db.TxType
import com.personalfinances.domain.Frequency
import com.personalfinances.domain.Money
import com.personalfinances.domain.Recurrence
import com.personalfinances.ui.MainViewModel
import com.personalfinances.ui.components.EmojiBadge
import com.personalfinances.ui.components.esCL
import com.personalfinances.ui.theme.LocalFinanceColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

fun recurringTitle(r: RecurringEntity, snapshot: FinanceSnapshot): String = r.description.ifBlank {
    when (r.type) {
        TxType.TRANSFER -> "Transferencia a ${snapshot.account(r.toAccountId)?.account?.name ?: ""}"
        else -> snapshot.categories.firstOrNull { it.id == r.categoryId }?.name ?: if (r.type == TxType.INCOME) "Ingreso" else "Gasto"
    }
}

fun nextLabel(epochDay: Long, today: LocalDate): String {
    val d = LocalDate.ofEpochDay(epochDay)
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, d)
    return when {
        days <= 0L -> "Hoy"
        days == 1L -> "Mañana"
        days < 7 -> "En $days días"
        else -> d.format(DateTimeFormatter.ofPattern("d MMM", esCL))
    }
}

@Composable
fun RecurringRow(r: RecurringEntity, snapshot: FinanceSnapshot, onClick: () -> Unit) {
    val fc = LocalFinanceColors.current
    val acc = snapshot.account(r.accountId)?.account
    val cat = snapshot.categories.firstOrNull { it.id == r.categoryId }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        EmojiBadge(if (r.type == TxType.TRANSFER) "⇄" else cat?.emoji ?: "🔁", Color(cat?.color ?: 0xFF6C5CE7))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(recurringTitle(r, snapshot), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(
                buildString {
                    append(if (r.active) nextLabel(r.nextEpochDay, snapshot.today) else "Pausado")
                    append(" · ${r.frequency.every}")
                    acc?.let { append(" · ${it.name}") }
                    if (r.autoCreate) append(" · automático")
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
        }
        val cur = acc?.currency ?: snapshot.settings.mainCurrency
        Text(
            if (snapshot.settings.hideAmounts) "•••" else (if (r.type == TxType.INCOME) "+" else "") + Money.format(r.amount, cur),
            style = MaterialTheme.typography.bodyLarge,
            color = if (r.type == TxType.INCOME) fc.income else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(snapshot: FinanceSnapshot, vm: MainViewModel, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<RecurringEntity?>(null) }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Recurrentes", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(
                        "Para crear uno, agrega un movimiento y elige \"Cada mes\" (o semana/año). Cuando llegue la fecha te pediremos confirmarlo, o se registrará solo si lo marcas como automático.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp),
                    )
                }
            }
            if (snapshot.recurring.isEmpty()) item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { Text("Aún no tienes movimientos recurrentes", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(snapshot.recurring, key = { it.id }) { r -> Box(Modifier.animateItem()) { RecurringRow(r, snapshot) { editing = r } } }
        }
    }
    editing?.let { r -> RecurringDialog(r, snapshot, onDismiss = { editing = null }, onSave = { vm.saveRecurring(it); editing = null }, onDelete = { vm.deleteRecurring(r.id); editing = null }) }
}

@Composable
private fun RecurringDialog(r: RecurringEntity, snapshot: FinanceSnapshot, onDismiss: () -> Unit, onSave: (RecurringEntity) -> Unit, onDelete: () -> Unit) {
    val cur = snapshot.account(r.accountId)?.account?.currency ?: snapshot.settings.mainCurrency
    var description by remember { mutableStateOf(r.description) }
    var amount by remember { mutableStateOf(cur.toMajor(r.amount).stripTrailingZeros().toPlainString().replace('.', ',')) }
    var frequency by remember { mutableStateOf(r.frequency) }
    var day by remember { mutableStateOf(r.dayOfMonth.toString()) }
    var auto by remember { mutableStateOf(r.autoCreate) }
    var active by remember { mutableStateOf(r.active) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(recurringTitle(r, snapshot)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(description, { description = it }, label = { Text("Descripción") }, singleLine = true)
                OutlinedTextField(amount, { v -> amount = v.filter { it.isDigit() || it == ',' || it == '.' } }, label = { Text("Monto") }, prefix = { Text(cur.symbol) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Frequency.entries.forEach { f -> FilterChip(selected = frequency == f, onClick = { frequency = f }, label = { Text(f.label) }) }
                }
                if (frequency != Frequency.WEEKLY) {
                    OutlinedTextField(day, { v -> day = v.filter(Char::isDigit).take(2) }, label = { Text("Día del mes") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Registrar automáticamente")
                        Text("Si está apagado, te pedimos confirmar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(auto, { auto = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Activo", Modifier.weight(1f)); Switch(active, { active = it }) }
                Text("Próxima: ${LocalDate.ofEpochDay(r.nextEpochDay).format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", esCL))}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { confirmDelete = true }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val dom = (day.toIntOrNull() ?: r.dayOfMonth).coerceIn(1, 31)
                // Si cambió el día, recalcula la próxima fecha dentro del mismo mes de la próxima ocurrencia.
                val currentNext = LocalDate.ofEpochDay(r.nextEpochDay)
                val next = if (frequency != Frequency.WEEKLY && dom != r.dayOfMonth) {
                    Recurrence.dayIn(YearMonth.from(currentNext), dom).let { if (it.isBefore(snapshot.today)) Recurrence.next(it, frequency, dom) else it }
                } else currentNext
                onSave(r.copy(
                    description = description.trim(), amount = Money.parse(amount, cur) ?: r.amount, frequency = frequency,
                    dayOfMonth = dom, autoCreate = auto, active = active, nextEpochDay = next.toEpochDay(),
                ))
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar recurrente?") },
            text = { Text("Los movimientos ya registrados se mantienen.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

