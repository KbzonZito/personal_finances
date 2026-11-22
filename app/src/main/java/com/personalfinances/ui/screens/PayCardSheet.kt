package com.personalfinances.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.personalfinances.container
import com.personalfinances.data.FinanceSnapshot
import com.personalfinances.data.TransactionDraft
import com.personalfinances.data.db.TxType
import com.personalfinances.domain.Money
import com.personalfinances.ui.components.AnimatedAmount
import com.personalfinances.ui.theme.LocalFinanceColors
import java.time.LocalDate

private enum class PayOption { BILLED, TOTAL, OTHER }

/** Pago de tarjeta en un paso: elige monto y cuenta de origen; descuenta del banco y devuelve el cupo. */
@Composable
fun PayCardSheet(snapshot: FinanceSnapshot, cardId: Long, onPay: (TransactionDraft) -> Unit) {
    val st = snapshot.account(cardId) ?: return
    val c = st.card ?: return
    val card = st.account
    val cur = card.currency
    val fc = LocalFinanceColors.current
    val repo = LocalContext.current.container.repository
    val sources = snapshot.activeAccounts.filter { it.account.type.isLiquid }

    var option by rememberSaveable { mutableStateOf(if (c.billedDue > 0) PayOption.BILLED else PayOption.TOTAL) }
    var other by rememberSaveable { mutableStateOf("") }
    var sourceId by rememberSaveable { mutableStateOf<Long?>(null) }
    var yesterday by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(cardId) {
        if (sourceId == null) sourceId = repo.usualPaymentSource(cardId)?.takeIf { id -> sources.any { it.account.id == id } }
            ?: sources.firstOrNull { it.account.currency == cur }?.account?.id ?: sources.firstOrNull()?.account?.id
    }

    val amount = when (option) {
        PayOption.BILLED -> c.billedDue
        PayOption.TOTAL -> c.used
        PayOption.OTHER -> Money.parse(other, cur) ?: 0
    }
    val source = sources.firstOrNull { it.account.id == sourceId }
    val fromAmount = source?.let { snapshot.settings.rates.convert(amount, cur, it.account.currency) } ?: 0
    val insufficient = source != null && fromAmount > source.balance

    Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(bottom = 12.dp)) {
        Text("Pagar ${card.name}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
        Text(
            if (c.billedDue > 0) "Facturado ${Money.format(c.billedDue, cur)} · vence el ${c.dueDate.dayOfMonth}/${c.dueDate.monthValue}" else "No tienes monto facturado pendiente",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(16.dp))
        AnimatedAmount(amount, cur, MaterialTheme.typography.displaySmall, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (c.billedDue > 0) item { FilterChip(selected = option == PayOption.BILLED, onClick = { option = PayOption.BILLED }, label = { Text("Facturado") }) }
            item { FilterChip(selected = option == PayOption.TOTAL, onClick = { option = PayOption.TOTAL }, label = { Text("Deuda total ${Money.format(c.used, cur)}") }) }
            item { FilterChip(selected = option == PayOption.OTHER, onClick = { option = PayOption.OTHER }, label = { Text("Otro monto") }) }
        }
        AnimatedVisibility(option == PayOption.OTHER) {
            OutlinedTextField(other, { v -> other = v.filter { it.isDigit() || it == ',' || it == '.' } }, label = { Text("Monto a pagar") },
                prefix = { Text(cur.symbol) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))
        }
        Text("Desde", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sources, key = { it.account.id }) { s ->
                FilterChip(
                    selected = s.account.id == sourceId, onClick = { sourceId = s.account.id }, shape = MaterialTheme.shapes.medium,
                    label = {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text("${s.account.type.emoji} ${s.account.name}", fontWeight = FontWeight.Medium)
                            if (!snapshot.settings.hideAmounts) Text(Money.format(s.balance, s.account.currency), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(s.account.color).copy(alpha = 0.28f)),
                )
            }
        }
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !yesterday, onClick = { yesterday = false }, label = { Text("Hoy") })
            FilterChip(selected = yesterday, onClick = { yesterday = true }, label = { Text("Ayer") })
        }
        if (source != null && amount > 0) {
            Text(
                buildString {
                    if (source.account.currency != cur) append("Se descontarán ${Money.format(fromAmount, source.account.currency)} de ${source.account.name}. ")
                    append("Cupo disponible después: ${Money.format(c.available + amount, cur)}")
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp),
            )
            if (insufficient) Text("⚠️ El saldo de ${source.account.name} no alcanza", style = MaterialTheme.typography.bodySmall, color = fc.warning, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
        Button(
            enabled = source != null && amount > 0,
            onClick = {
                val s = source ?: return@Button
                onPay(TransactionDraft(
                    type = TxType.TRANSFER, accountId = s.account.id, toAccountId = card.id, amount = fromAmount,
                    toAmount = if (s.account.currency != cur) amount else null,
                    date = LocalDate.now().minusDays(if (yesterday) 1 else 0), description = "Pago ${card.name}",
                ))
            },
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(56.dp),
        ) { Text("Pagar ${Money.format(amount, cur)}", style = MaterialTheme.typography.titleMedium) }
    }
}
