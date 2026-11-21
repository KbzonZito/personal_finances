package com.personalfinances.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.personalfinances.data.AccountState
import com.personalfinances.data.FinanceSnapshot
import com.personalfinances.data.TransactionDraft
import com.personalfinances.data.db.AccountType
import com.personalfinances.data.db.TxSource
import com.personalfinances.data.db.TxType
import com.personalfinances.domain.Money
import com.personalfinances.domain.YieldAccrual
import com.personalfinances.ui.components.AmountText
import com.personalfinances.ui.components.AnimatedAmount
import com.personalfinances.ui.components.MonthBars
import com.personalfinances.ui.components.ProgressPill
import com.personalfinances.ui.components.SectionHeader
import com.personalfinances.ui.components.TransactionRow
import com.personalfinances.ui.components.esCL
import com.personalfinances.ui.components.sharedAccount
import com.personalfinances.ui.components.monthLong
import com.personalfinances.ui.theme.LocalFinanceColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScaffold(title: String, onBack: () -> Unit, onEdit: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") } },
                actions = { IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Editar") } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        content = content,
    )
}

@Composable
fun CardDetailScreen(
    snapshot: FinanceSnapshot,
    cardId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onPay: (AccountState) -> Unit,
    onAddExpense: (AccountState) -> Unit,
    onOpenTransaction: (Long) -> Unit,
) {
    val st = snapshot.account(cardId) ?: return
    val c = st.card ?: return
    val a = st.account
    val hidden = snapshot.settings.hideAmounts
    val fc = LocalFinanceColors.current
    val purchases = remember(snapshot, cardId) { snapshot.activePurchases(cardId) }
    val txs = remember(snapshot, cardId) { snapshot.transactions.filter { it.tx.accountId == cardId || it.tx.toAccountId == cardId } }

    DetailScaffold(a.name, onBack, onEdit) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item { Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) { CardVisual(st, hidden) {} } }
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Estado de cuenta de ${monthLong(c.billedPeriod).lowercase()}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                AnimatedAmount(c.billedDue, a.currency, MaterialTheme.typography.headlineMedium, hidden = hidden)
                                Text(
                                    if (c.billedDue > 0) "Paga antes del " + c.dueDate.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", esCL)) else "Pagado ✓ — estás al día",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (c.billedDue > 0 && !c.dueDate.isAfter(snapshot.today.plusDays(3))) fc.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (c.billedDue > 0) Button(onClick = { onPay(st) }) { Text("Pagar") }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text("Periodo actual", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                AmountText(c.openPeriodAmount, a.currency, MaterialTheme.typography.titleMedium, hidden = hidden)
                                Text("Cierra el " + c.closingDate.format(DateTimeFormatter.ofPattern("d MMM", esCL)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Deuda total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                AmountText(c.used, a.currency, MaterialTheme.typography.titleMedium, hidden = hidden)
                                Text("${(c.usedFraction * 100).toInt()}% del cupo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = { onAddExpense(st) }, modifier = Modifier.weight(1f)) { Text("Nueva compra") }
                    FilledTonalButton(onClick = { onPay(st) }, modifier = Modifier.weight(1f)) { Text("Abonar") }
                }
            }
            item { SectionHeader("Próximos estados de cuenta", Modifier.padding(top = 12.dp)) }
            item { MonthBars(c.projection.take(8), a.currency, hidden, Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
            item {
                val next = c.projection.drop(1).take(3)
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    next.forEach { (ym, v) ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text(monthLong(ym), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            AmountText(v, a.currency, MaterialTheme.typography.bodyMedium, hidden = hidden)
                        }
                    }
                }
            }
            if (purchases.isNotEmpty()) {
                item { SectionHeader("Compras en cuotas", Modifier.padding(top = 8.dp)) }
                items(purchases, key = { "p" + it.item.tx.id }) { p ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.item.category?.emoji ?: "🛍️")
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.item.tx.description.ifBlank { p.item.category?.name ?: "Compra" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text("Cuota ${p.billed + 1} de ${p.total} · quedan ${if (hidden) "•••" else Money.format(p.remainingAmount, a.currency)}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            AmountText(p.nextAmount, a.currency, MaterialTheme.typography.bodyLarge, hidden = hidden)
                        }
                        Spacer(Modifier.height(8.dp))
                        ProgressPill(p.billed.toFloat() / p.total, height = 6)
                    }
                }
            }
            if (txs.isNotEmpty()) {
                item { SectionHeader("Movimientos", Modifier.padding(top = 8.dp)) }
                items(txs, key = { it.tx.id }) { TransactionRow(it, hidden, onClick = { onOpenTransaction(it.tx.id) }, showAccount = false) }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun AccountDetailScreen(
    snapshot: FinanceSnapshot,
    accountId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onTransfer: (AccountState) -> Unit,
    onAdjust: (TransactionDraft) -> Unit,
    onOpenTransaction: (Long) -> Unit,
) {
    val st = snapshot.account(accountId) ?: return
    val a = st.account
    val hidden = snapshot.settings.hideAmounts
    val fc = LocalFinanceColors.current
    val txs = remember(snapshot, accountId) { snapshot.transactions.filter { it.tx.accountId == accountId || it.tx.toAccountId == accountId } }
    var adjusting by remember { mutableStateOf(false) }
    val monthly = remember(txs) {
        txs.filter { it.tx.type == TxType.EXPENSE && it.tx.accountId == accountId }
            .groupBy { java.time.YearMonth.from(LocalDate.ofEpochDay(it.tx.epochDay)) }
            .mapValues { (_, l) -> l.sumOf { it.tx.amount } }
    }

    DetailScaffold(a.name, onBack, onEdit) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp).sharedAccount(a.id)) {
                    Text("${a.type.emoji} ${a.type.label} · ${a.currency.code}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AnimatedAmount(st.balance, a.currency, MaterialTheme.typography.displaySmall, hidden = hidden)
                }
            }
            if (a.type == AccountType.YIELD || a.annualRate > 0) {
                item {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Column(Modifier.padding(18.dp)) {
                            Text("Rentabilidad ${a.annualRate.toBigDecimal().stripTrailingZeros().toPlainString()}% anual", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(Modifier.height(10.dp))
                            Row {
                                YieldStat("Por día", YieldAccrual.project(st.balance, a.annualRate, 1), st, hidden, Modifier.weight(1f))
                                YieldStat("Por mes", st.monthlyYield, st, hidden, Modifier.weight(1f))
                                YieldStat("En un año", YieldAccrual.project(st.balance, a.annualRate, 365), st, hidden, Modifier.weight(1f))
                            }
                            val earned = txs.filter { it.tx.type == TxType.INTEREST && it.tx.accountId == accountId }.sumOf { it.tx.amount }
                            if (earned > 0) {
                                Spacer(Modifier.height(10.dp))
                                Text("Ganado en total: ${if (hidden) "•••" else Money.format(earned, a.currency)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            Text("Se suma automáticamente cada día. Ajusta el saldo si difiere del real.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = { onTransfer(st) }, modifier = Modifier.weight(1f)) { Text("Transferir") }
                    FilledTonalButton(onClick = { adjusting = true }, modifier = Modifier.weight(1f)) { Text("Ajustar saldo") }
                }
            }
            if (monthly.isNotEmpty()) {
                item { SectionHeader("Gastos por mes", Modifier.padding(top = 8.dp)) }
                item {
                    val now = java.time.YearMonth.from(snapshot.today)
                    val data = (5 downTo 0).map { now.minusMonths(it.toLong()) }.map { it to (monthly[it] ?: 0L) }
                    MonthBars(data, a.currency, hidden, Modifier.padding(horizontal = 20.dp), highlightFirst = false)
                }
            }
            if (txs.isNotEmpty()) {
                item { SectionHeader("Movimientos", Modifier.padding(top = 8.dp)) }
                items(txs, key = { it.tx.id }) { TransactionRow(it, hidden, onClick = { onOpenTransaction(it.tx.id) }, showAccount = false) }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (adjusting) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adjusting = false },
            title = { Text("Saldo real") },
            text = {
                Column {
                    Text("Ingresa el saldo que ves en tu banco. Se creará un ajuste por la diferencia.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = text, onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' || it == ',' || it == '-' } },
                        placeholder = { Text(Money.format(st.balance, a.currency)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val negative = text.trim().startsWith("-")
                    val real = Money.parse(text, a.currency)?.let { if (negative) -it else it }
                    if (real != null && real != st.balance) {
                        onAdjust(TransactionDraft(type = TxType.ADJUSTMENT, accountId = a.id, amount = real - st.balance, date = LocalDate.now(), description = "Ajuste de saldo", source = TxSource.MANUAL))
                    }
                    adjusting = false
                }) { Text("Ajustar") }
            },
            dismissButton = { TextButton(onClick = { adjusting = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun YieldStat(label: String, value: Long, st: AccountState, hidden: Boolean, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f))
        AmountText(value, st.account.currency, MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, hidden = hidden, showSign = true)
    }
}
