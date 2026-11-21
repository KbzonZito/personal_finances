package com.personalfinances.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.personalfinances.domain.BudgetLevel
import com.personalfinances.domain.BudgetStatus
import com.personalfinances.domain.Money
import com.personalfinances.domain.Reports
import com.personalfinances.ui.MainViewModel
import com.personalfinances.ui.components.ProgressPill
import com.personalfinances.ui.theme.LocalFinanceColors
import java.time.YearMonth

@Composable
fun budgetColor(s: BudgetStatus): Color {
    val fc = LocalFinanceColors.current
    return when (s.level) {
        BudgetLevel.OVER -> fc.expense
        BudgetLevel.WARN -> fc.warning
        BudgetLevel.OK -> if (s.pace > 0.1f) fc.warning else fc.income
    }
}

@Composable
fun BudgetRow(s: BudgetStatus, snapshot: FinanceSnapshot, onClick: () -> Unit) {
    val cat = snapshot.categories.firstOrNull { it.id == s.categoryId }
    val main = snapshot.settings.mainCurrency
    val hidden = snapshot.settings.hideAmounts
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (s.categoryId == 0L) "🧾" else cat?.emoji ?: "📦", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(10.dp))
            Text(if (s.categoryId == 0L) "Total del mes" else cat?.name ?: "Categoría", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(if (hidden) "•••" else "${Money.format(s.spent, main)} / ${Money.format(s.limit, main)}", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
        ProgressPill(s.fraction, color = budgetColor(s), height = 8)
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                s.level == BudgetLevel.OVER -> "Te pasaste por ${if (hidden) "•••" else Money.format(-s.remaining, main)}"
                s.pace > 0.1f -> "Vas más rápido de lo normal · ${if (hidden) "•••" else Money.format(s.perDayLeft, main)} por día"
                else -> "Te quedan ${if (hidden) "•••" else Money.format(s.remaining, main)} · ${if (hidden) "•••" else Money.format(s.perDayLeft, main)} por día"
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(snapshot: FinanceSnapshot, vm: MainViewModel, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<Long?>(null) } // categoryId (0 = total, -1 = nuevo)
    val main = snapshot.settings.mainCurrency
    // Promedio de los últimos 3 meses completos por categoría (para sugerir montos).
    val averages = remember(snapshot) {
        val txs = snapshot.reportTxs().filter { !it.isIncome }
        val now = YearMonth.from(snapshot.today)
        val months = (1..3).map { now.minusMonths(it.toLong()) }
        val byCat = txs.filter { YearMonth.from(it.date) in months }.groupBy { it.categoryId ?: -1 }.mapValues { it.value.sumOf { t -> t.amount } / 3 }
        byCat + (0L to months.sumOf { Reports.totals(txs, it).expenses } / 3)
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Presupuestos", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") } },
                actions = { TextButton(onClick = { editing = -1 }) { Text("Nuevo") } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            if (snapshot.budgets.isEmpty()) item {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Define cuánto quieres gastar al mes", style = MaterialTheme.typography.titleMedium)
                        Text("Por categoría o en total. Te avisamos al llegar al 80% y si te pasas, y te decimos cuánto puedes gastar por día.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { editing = -1 }) { Text("Crear presupuesto") }
                    }
                }
            }
            items(snapshot.budgets, key = { it.categoryId }) { s -> Box(Modifier.animateItem()) { BudgetRow(s, snapshot) { editing = s.categoryId } } }
        }
    }

    editing?.let { id ->
        val existing = snapshot.budgets.firstOrNull { it.categoryId == id }
        var category by remember(id) { mutableStateOf(if (id == -1L) null else id) }
        var text by remember(id) { mutableStateOf(existing?.let { main.toMajor(it.limit).toPlainString() } ?: "") }
        val used = snapshot.budgets.map { it.categoryId }.toSet()
        val options = listOf(0L) + snapshot.categories.filter { !it.isIncome }.map { it.id }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (existing == null) "Nuevo presupuesto" else "Editar presupuesto") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (existing == null) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(0.dp)) {
                            items(options.filter { it !in used }) { cid ->
                                val c = snapshot.categories.firstOrNull { it.id == cid }
                                FilterChip(selected = category == cid, onClick = { category = cid }, label = { Text(if (cid == 0L) "🧾 Total" else "${c?.emoji} ${c?.name}") })
                            }
                        }
                    }
                    OutlinedTextField(text, { v -> text = v.filter { it.isDigit() || it == '.' || it == ',' } }, label = { Text("Monto mensual") },
                        prefix = { Text(main.symbol) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    category?.let { cid ->
                        val avg = averages[cid] ?: 0L
                        if (avg > 0) TextButton(onClick = { text = main.toMajor(avg).toPlainString() }) {
                            Text("Promedio últimos 3 meses: ${Money.format(avg, main)} · usar")
                        }
                    }
                    if (existing != null) TextButton(onClick = { vm.deleteBudget(id); editing = null }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                val amount = Money.parse(text, main) ?: 0
                TextButton(enabled = category != null && amount > 0, onClick = { vm.saveBudget(category!!, amount); editing = null }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancelar") } },
        )
    }
}
