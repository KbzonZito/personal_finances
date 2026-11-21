package com.personalfinances.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personalfinances.data.FinanceSnapshot
import com.personalfinances.domain.Money
import com.personalfinances.domain.MonthTotals
import com.personalfinances.domain.Reports
import com.personalfinances.ui.components.AmountText
import com.personalfinances.ui.components.AnimatedAmount
import com.personalfinances.ui.components.Donut
import com.personalfinances.ui.components.SectionHeader
import com.personalfinances.ui.components.monthLong
import com.personalfinances.ui.components.monthShort
import com.personalfinances.ui.theme.LocalFinanceColors
import java.time.YearMonth

@Composable
fun ReportsScreen(snapshot: FinanceSnapshot, onOpenBudgets: () -> Unit, contentPadding: PaddingValues) {
    var monthKey by rememberSaveable { mutableStateOf(YearMonth.from(snapshot.today).toString()) }
    val ym = YearMonth.parse(monthKey)
    val txs = remember(snapshot.transactions, snapshot.settings) { snapshot.reportTxs() }
    val report = remember(txs, ym) { Reports.month(txs, ym, snapshot.today) }
    val main = snapshot.settings.mainCurrency
    val hidden = snapshot.settings.hideAmounts
    val fc = LocalFinanceColors.current
    val canGoNext = ym < YearMonth.from(snapshot.today)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Row(Modifier.statusBarsPadding().fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Reportes", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { monthKey = ym.minusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Mes anterior") }
                AnimatedContent(ym, transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally { it * dir / 2 } + fadeIn()) togetherWith (slideOutHorizontally { -it * dir / 2 } + fadeOut())
                }, label = "month") { m ->
                    Text("${monthShort(m)} ${m.year}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(84.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                IconButton(enabled = canGoNext, onClick = { monthKey = ym.plusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Mes siguiente") }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Gastos de ${monthLong(ym).lowercase()}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AnimatedAmount(report.totals.expenses, main, MaterialTheme.typography.displaySmall, hidden = hidden)
                report.expenseChange?.let { ch ->
                    val up = ch > 0
                    Text(
                        "${if (up) "▲" else "▼"} ${"%.0f".format(kotlin.math.abs(ch * 100))}% vs ${monthLong(ym.minusMonths(1)).lowercase()}",
                        style = MaterialTheme.typography.bodyMedium, color = if (up) fc.expense else fc.income,
                    )
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Ingresos", if (hidden) "•••" else Money.format(report.totals.income, main), fc.income, Modifier.weight(1f))
                Stat("Ahorro", if (hidden) "•••" else Money.format(report.totals.saved, main), if (report.totals.saved >= 0) MaterialTheme.colorScheme.onSurface else fc.expense, Modifier.weight(1f),
                    sub = if (report.totals.income > 0) "${(report.totals.savingsRate * 100).toInt()}% de tus ingresos" else null)
            }
            Row(Modifier.padding(horizontal = 20.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Promedio diario", if (hidden) "•••" else Money.format(report.dailyAverage, main), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                report.projectedExpenses?.let {
                    Stat("Al ritmo actual", if (hidden) "•••" else Money.format(it, main), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f), sub = "gasto a fin de mes")
                } ?: Spacer(Modifier.weight(1f))
            }
        }
        if (snapshot.budgets.isNotEmpty() && ym == YearMonth.from(snapshot.today)) {
            item { SectionHeader("Presupuestos", Modifier.padding(top = 12.dp), action = "Ver", onAction = onOpenBudgets) }
            snapshot.budgets.take(3).forEach { s -> item(key = "b${s.categoryId}") { BudgetRow(s, snapshot, onOpenBudgets) } }
        }
        if (report.byCategory.isNotEmpty()) {
            item { SectionHeader("Por categoría", Modifier.padding(top = 12.dp)) }
            item {
                val cats = report.byCategory.map { (id, v) -> snapshot.categories.firstOrNull { it.id == id } to v }
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                        Donut(cats.map { (c, v) -> v.toFloat() to Color(c?.color ?: 0xFF888888) }, Modifier.size(150.dp), track = MaterialTheme.colorScheme.surfaceContainerHighest)
                        Text("${cats.size}\ncategorías", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        cats.take(6).forEach { (c, v) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(c?.color ?: 0xFF888888)))
                                Spacer(Modifier.width(8.dp))
                                Text(c?.name ?: "Sin categoría", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                                Text("${(v * 100 / report.totals.expenses.coerceAtLeast(1))}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
        item { SectionHeader("Últimos 6 meses", Modifier.padding(top = 12.dp)) }
        item { TrendBars(report.trend, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
        item {
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend(fc.income, "Ingresos"); Legend(MaterialTheme.colorScheme.primary, "Gastos")
            }
        }
        if (report.topMerchants.isNotEmpty()) {
            item { SectionHeader("Dónde más gastaste", Modifier.padding(top = 12.dp)) }
            report.topMerchants.forEachIndexed { i, (name, v) ->
                item(key = "m$i") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(24.dp))
                        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
                        AmountText(v, main, MaterialTheme.typography.bodyLarge, hidden = hidden)
                    }
                }
            }
        }
        if (report.totals.expenses == 0L && report.totals.income == 0L) item {
            Text("Sin movimientos en ${monthLong(ym).lowercase()}.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier, sub: String? = null) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = modifier.padding(bottom = 10.dp).fillMaxHeight()) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
            sub?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Barras pareadas ingresos/gastos por mes. */
@Composable
private fun TrendBars(data: List<MonthTotals>, modifier: Modifier) {
    val fc = LocalFinanceColors.current
    val primary = MaterialTheme.colorScheme.primary
    val max = (data.maxOfOrNull { maxOf(it.income, it.expenses) } ?: 0L).coerceAtLeast(1L).toFloat()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        data.forEachIndexed { i, m ->
            val inc by animateFloatAsState(m.income / max, tween(600, delayMillis = i * 40), label = "inc")
            val exp by animateFloatAsState(m.expenses / max, tween(600, delayMillis = i * 40 + 20), label = "exp")
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                    val w = size.width / 2 - 2.dp.toPx()
                    val r = CornerRadius(5.dp.toPx())
                    drawRoundRect(fc.income, Offset(0f, size.height * (1 - inc)), Size(w, size.height * inc), r)
                    drawRoundRect(primary, Offset(w + 4.dp.toPx(), size.height * (1 - exp)), Size(w, size.height * exp), r)
                }
                Spacer(Modifier.height(4.dp))
                Text(monthShort(m.month), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
