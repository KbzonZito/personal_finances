package com.personalfinances.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personalfinances.data.AccountState
import com.personalfinances.data.FinanceSnapshot
import com.personalfinances.domain.Money
import com.personalfinances.ui.components.AmountText
import com.personalfinances.ui.components.AnimatedAmount
import com.personalfinances.ui.components.EmojiBadge
import com.personalfinances.ui.components.PressableCard
import com.personalfinances.ui.components.ProgressPill
import com.personalfinances.ui.components.SectionHeader
import com.personalfinances.ui.components.TransactionRow
import com.personalfinances.ui.components.esCL
import com.personalfinances.ui.components.monthLong
import com.personalfinances.ui.theme.LocalFinanceColors
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    snapshot: FinanceSnapshot,
    onToggleHide: () -> Unit,
    onOpenAccount: (AccountState) -> Unit,
    onNewAccount: () -> Unit,
    onOpenPending: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onPayCard: (AccountState) -> Unit,
    onOpenRecurring: () -> Unit,
    contentPadding: PaddingValues,
) {
    val hidden = snapshot.settings.hideAmounts
    val main = snapshot.settings.mainCurrency
    val fc = LocalFinanceColors.current

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(snapshot.today.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", esCL)).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Patrimonio neto", style = MaterialTheme.typography.titleMedium)
                }
                IconButton(onClick = onToggleHide) {
                    Icon(if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Ocultar montos")
                }
            }
        }
        item {
            AnimatedAmount(
                snapshot.netWorth, main, MaterialTheme.typography.displayMedium, hidden = hidden,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill(Icons.Outlined.ArrowDownward, "Gastos ${monthLong(YearMonth.from(snapshot.today)).lowercase()}", snapshot.monthExpense, MaterialTheme.colorScheme.onSurface, snapshot, Modifier.weight(1f))
                StatPill(Icons.Outlined.ArrowUpward, "Ingresos", snapshot.monthIncome, fc.income, snapshot, Modifier.weight(1f))
            }
        }

        item {
            AnimatedVisibility(snapshot.pending.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                PressableCard(onClick = onOpenPending, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.NotificationsActive, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(12.dp))
                        val n = snapshot.pending.size
                        Text(
                            if (n == 1) "1 movimiento detectado por confirmar" else "$n movimientos detectados por confirmar",
                            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }

        item { SectionHeader("Cuentas", action = "Nueva", onAction = onNewAccount) }
        item {
            if (snapshot.loaded && snapshot.activeAccounts.isEmpty()) {
                EmptyAccounts(onNewAccount)
            } else {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(snapshot.activeAccounts, key = { it.account.id }) { st ->
                        AccountCard(st, hidden, Modifier.animateItem()) { onOpenAccount(st) }
                    }
                    item {
                        PressableCard(onClick = onNewAccount, modifier = Modifier.size(width = 72.dp, height = 132.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, "Nueva cuenta") }
                        }
                    }
                }
            }
        }

        val due = snapshot.cards.filter { (it.card?.billedDue ?: 0) > 0 }.sortedBy { it.card!!.dueDate }
        if (due.isNotEmpty()) {
            item { SectionHeader("Por pagar", Modifier.padding(top = 12.dp)) }
            items(due, key = { "due" + it.account.id }) { st ->
                val c = st.card!!
                val days = java.time.temporal.ChronoUnit.DAYS.between(snapshot.today, c.dueDate)
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    EmojiBadge("💳", Color(st.account.color))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(st.account.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            when {
                                days < 0 -> "Vencida hace ${-days} días"
                                days == 0L -> "Vence hoy"
                                days == 1L -> "Vence mañana"
                                else -> "Vence el " + c.dueDate.format(DateTimeFormatter.ofPattern("d MMM", esCL)) + " · en $days días"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (days <= 3) fc.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        AmountText(c.billedDue, st.account.currency, MaterialTheme.typography.bodyLarge, hidden = hidden)
                        Text("Pagar", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp).clip(CircleShape).clickable { onPayCard(st) }.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
        }

        val upcoming = snapshot.recurring.filter { it.active && it.nextEpochDay <= snapshot.today.plusDays(10).toEpochDay() }.take(4)
        if (upcoming.isNotEmpty()) {
            item { SectionHeader("Próximos", Modifier.padding(top = 12.dp), action = "Ver todos", onAction = onOpenRecurring) }
            items(upcoming, key = { "rec" + it.id }) { r -> RecurringRow(r, snapshot, onClick = onOpenRecurring) }
        }

        if (snapshot.expenseByCategory.isNotEmpty()) {
            item { SectionHeader("En qué gastas este mes", Modifier.padding(top = 12.dp)) }
            val max = snapshot.expenseByCategory.first().second.coerceAtLeast(1)
            items(snapshot.expenseByCategory.take(5), key = { "cat" + (it.first?.id ?: -1) }) { (cat, total) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(cat?.emoji ?: "📦", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(cat?.name ?: "Sin categoría", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            AmountText(total, main, MaterialTheme.typography.bodyMedium, hidden = hidden)
                        }
                        Spacer(Modifier.height(6.dp))
                        ProgressPill(total.toFloat() / max, color = Color(cat?.color ?: 0xFF888888), height = 6)
                    }
                }
            }
        }

        if (snapshot.transactions.isNotEmpty()) {
            item { SectionHeader("Recientes", Modifier.padding(top = 12.dp), action = "Ver todo", onAction = onOpenTransactions) }
            items(snapshot.transactions.take(8), key = { "tx" + it.tx.id }) { item ->
                Box(Modifier.animateItem()) { TransactionRow(item, hidden, onClick = { onOpenTransaction(item.tx.id) }) }
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
private fun StatPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: Long, color: Color, snapshot: FinanceSnapshot, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(2.dp))
            AnimatedAmount(value, snapshot.settings.mainCurrency, MaterialTheme.typography.titleMedium, color = color, hidden = snapshot.settings.hideAmounts)
        }
    }
}

@Composable
fun AccountCard(st: AccountState, hidden: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val a = st.account
    val base = Color(a.color)
    PressableCard(onClick = onClick, color = Color.Transparent, modifier = modifier.size(width = 176.dp, height = 132.dp)) {
        Box(
            Modifier.fillMaxSize().background(Brush.linearGradient(listOf(base.copy(alpha = 0.95f), base.copy(alpha = 0.6f)))).padding(14.dp)
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.type.emoji)
                    Spacer(Modifier.width(6.dp))
                    Text(a.name, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column {
                    if (st.card != null) {
                        Text("Disponible", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
                        AnimatedAmount(st.card.available, a.currency, MaterialTheme.typography.titleLarge, color = Color.White, hidden = hidden)
                        Spacer(Modifier.height(6.dp))
                        ProgressPill(st.card.usedFraction, color = Color.White, track = Color.White.copy(alpha = 0.25f), height = 5)
                    } else {
                        if (a.annualRate > 0 && !hidden) {
                            Text("+${Money.format(st.monthlyYield, a.currency)}/mes", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                        } else Text(a.currency.code, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
                        AnimatedAmount(st.balance, a.currency, MaterialTheme.typography.titleLarge, color = Color.White, hidden = hidden)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyAccounts(onNew: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("👋", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            Text("Empieza agregando tus cuentas", style = MaterialTheme.typography.titleMedium)
            Text("Cuenta corriente, tarjetas de crédito, cuentas remuneradas como Tenpo…",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onNew) { Text("Crear cuenta") }
        }
    }
}

