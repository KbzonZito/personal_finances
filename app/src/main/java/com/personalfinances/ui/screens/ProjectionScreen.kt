package com.personalfinances.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personalfinances.data.FinanceSnapshot
import com.personalfinances.data.ProjectionBuilder
import com.personalfinances.domain.EventKind
import com.personalfinances.ui.components.AmountText
import com.personalfinances.ui.components.AnimatedAmount
import com.personalfinances.ui.components.BalanceLine
import com.personalfinances.ui.components.SectionHeader
import com.personalfinances.ui.components.esCL
import com.personalfinances.ui.components.monthLong
import com.personalfinances.ui.components.monthShort
import com.personalfinances.ui.theme.LocalFinanceColors
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectionScreen(snapshot: FinanceSnapshot, onBack: () -> Unit, onOpenRecurring: () -> Unit) {
    var months by rememberSaveable { mutableIntStateOf(3) }
    val view = remember(snapshot, months) { ProjectionBuilder.build(snapshot, months) }
    val r = view.result
    val main = snapshot.settings.mainCurrency
    val hidden = snapshot.settings.hideAmounts
    val fc = LocalFinanceColors.current
    val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", esCL)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Proyección", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("Disponible a fin de ${monthLong(r.months.first().month).lowercase()}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AnimatedAmount(r.endOfThisMonth, main, MaterialTheme.typography.displaySmall, hidden = hidden,
                        color = if (r.endOfThisMonth < 0) fc.expense else MaterialTheme.colorScheme.onSurface)
                    Text("Hoy: ${if (hidden) "•••" else com.personalfinances.domain.Money.format(r.start, main)} en tus cuentas",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Row(Modifier.padding(horizontal = 20.dp).horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 3, 6, 12).forEach { m -> FilterChip(selected = months == m, onClick = { months = m }, label = { Text(if (m == 1) "Mes" else "$m meses", maxLines = 1, softWrap = false) }) }
                }
            }
            item {
                BalanceLine(r.daily.map { it.second }, Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 20.dp, vertical = 12.dp), negativeColor = fc.expense)
            }
            item {
                Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val (lowDate, low) = r.lowest
                    InfoTile("Punto más bajo", if (hidden) "•••" else com.personalfinances.domain.Money.format(low, main), lowDate.format(dayFmt),
                        if (low < 0) fc.expense else MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                    InfoTile("Sin cuotas desde", view.debtFreeFrom?.let { "${monthLong(it)} ${it.year}" } ?: "Ya sin cuotas", if (view.debtFreeFrom != null) "Último pago el mes anterior" else "🎉",
                        MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                }
            }
            if (r.lowest.second < 0) item {
                Surface(color = fc.expense.copy(alpha = 0.12f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Text("⚠️ Con lo programado, tu saldo quedaría negativo el ${r.lowest.first.format(dayFmt)}.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
                }
            }
            item { SectionHeader("Mes a mes", Modifier.padding(top = 12.dp)) }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                    listOf("Mes", "Ingresos", "Gastos", "Tarjetas", "Saldo").forEachIndexed { i, h ->
                        Text(h, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(if (i == 0) 0.7f else 1f))
                    }
                }
            }
            items(r.months, key = { it.month.toString() }) { m ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(monthShort(m.month), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.7f))
                    AmountText(m.income, main, MaterialTheme.typography.bodySmall, fc.income, hidden, modifier = Modifier.weight(1f))
                    AmountText(m.expenses, main, MaterialTheme.typography.bodySmall, hidden = hidden, modifier = Modifier.weight(1f))
                    AmountText(m.cardPayments, main, MaterialTheme.typography.bodySmall, hidden = hidden, modifier = Modifier.weight(1f))
                    AmountText(m.endBalance, main, MaterialTheme.typography.bodySmall, if (m.endBalance < 0) fc.expense else MaterialTheme.colorScheme.onSurface, hidden, modifier = Modifier.weight(1f))
                }
                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
            item { SectionHeader("Próximos 45 días", Modifier.padding(top = 12.dp), action = "Recurrentes", onAction = onOpenRecurring) }
            val soon = r.events.filter { !it.date.isAfter(snapshot.today.plusDays(45)) }
            if (soon.isEmpty()) item {
                Text("Nada programado. Agrega tu sueldo y gastos fijos como recurrentes para una proyección más útil.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
            }
            items(soon.size) { i ->
                val e = soon[i]
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(when (e.kind) { EventKind.INCOME -> "💰"; EventKind.EXPENSE -> "📤"; EventKind.CARD_PAYMENT -> "💳"; EventKind.YIELD -> "📈" })
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(e.date.format(dayFmt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AmountText(e.amount, main, MaterialTheme.typography.bodyMedium, if (e.amount > 0) fc.income else MaterialTheme.colorScheme.onSurface, hidden, showSign = e.amount > 0)
                }
            }
            item {
                Text(
                    "Considera el saldo de tus cuentas (sin tarjetas), movimientos recurrentes, pagos de tarjetas en su vencimiento y rentabilidad estimada. No incluye gastos que no estén programados.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp),
                )
            }
        }
    }
}

@Composable
private fun InfoTile(label: String, value: String, sub: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
