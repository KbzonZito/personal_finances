package com.personalfinances.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.personalfinances.data.FinanceSnapshot
import com.personalfinances.data.TxItem
import com.personalfinances.data.db.TxType
import com.personalfinances.domain.Money
import com.personalfinances.ui.components.TransactionRow
import com.personalfinances.ui.components.esCL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionsScreen(snapshot: FinanceSnapshot, onOpenTransaction: (Long) -> Unit, contentPadding: PaddingValues) {
    var query by rememberSaveable { mutableStateOf("") }
    var accountFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var typeFilter by rememberSaveable { mutableStateOf<TxType?>(null) }

    val filtered = remember(snapshot.transactions, query, accountFilter, typeFilter) {
        val q = query.trim().lowercase()
        snapshot.transactions.filter { i ->
            (accountFilter == null || i.tx.accountId == accountFilter || i.tx.toAccountId == accountFilter) &&
                (typeFilter == null || i.tx.type == typeFilter || (typeFilter == TxType.INCOME && i.tx.type == TxType.INTEREST)) &&
                (q.isEmpty() || i.tx.description.lowercase().contains(q) || i.category?.name?.lowercase()?.contains(q) == true ||
                    i.account?.name?.lowercase()?.contains(q) == true)
        }.groupBy { it.tx.epochDay }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Column(Modifier.statusBarsPadding()) {
                Text("Movimientos", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 12.dp))
                TextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Buscar") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Limpiar") } },
                    singleLine = true,
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh, focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                )
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = typeFilter == TxType.EXPENSE, onClick = { typeFilter = if (typeFilter == TxType.EXPENSE) null else TxType.EXPENSE }, label = { Text("Gastos") }) }
                    item { FilterChip(selected = typeFilter == TxType.INCOME, onClick = { typeFilter = if (typeFilter == TxType.INCOME) null else TxType.INCOME }, label = { Text("Ingresos") }) }
                    item { FilterChip(selected = typeFilter == TxType.TRANSFER, onClick = { typeFilter = if (typeFilter == TxType.TRANSFER) null else TxType.TRANSFER }, label = { Text("Transferencias") }) }
                    items(snapshot.activeAccounts, key = { it.account.id }) { st ->
                        FilterChip(
                            selected = accountFilter == st.account.id,
                            onClick = { accountFilter = if (accountFilter == st.account.id) null else st.account.id },
                            label = { Text("${st.account.type.emoji} ${st.account.name}") },
                        )
                    }
                }
            }
        }
        if (filtered.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Text(if (snapshot.transactions.isEmpty()) "Aún no hay movimientos.\nToca + para agregar el primero." else "Sin resultados",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
        filtered.forEach { (day, list) ->
            stickyHeader(key = "h$day") { DayHeader(day, list, snapshot) }
            items(list, key = { it.tx.id }) { item ->
                Box(Modifier.animateItem()) { TransactionRow(item, snapshot.settings.hideAmounts, onClick = { onOpenTransaction(item.tx.id) }) }
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
private fun DayHeader(epochDay: Long, list: List<TxItem>, snapshot: FinanceSnapshot) {
    val date = LocalDate.ofEpochDay(epochDay)
    val today = snapshot.today
    val label = when (date) {
        today -> "Hoy"
        today.minusDays(1) -> "Ayer"
        else -> date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "EEEE d 'de' MMMM" else "d MMM yyyy", esCL)).replaceFirstChar { it.uppercase() }
    }
    val main = snapshot.settings.mainCurrency
    val spent = list.filter { it.tx.type == TxType.EXPENSE }.sumOf { snapshot.settings.rates.convert(it.tx.amount, it.account?.currency ?: main, main) }
    run {
        androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (spent > 0 && !snapshot.settings.hideAmounts) {
                    Text("-" + Money.format(spent, main), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
