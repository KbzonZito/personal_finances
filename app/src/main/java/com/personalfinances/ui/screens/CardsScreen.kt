package com.personalfinances.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.personalfinances.data.AccountState
import com.personalfinances.data.FinanceSnapshot
import com.personalfinances.domain.Money
import com.personalfinances.ui.components.AmountText
import com.personalfinances.ui.components.AnimatedAmount
import com.personalfinances.ui.components.MonthBars
import com.personalfinances.ui.components.PressableCard
import com.personalfinances.ui.components.ProgressPill
import com.personalfinances.ui.components.SectionHeader
import com.personalfinances.ui.components.esCL
import java.time.format.DateTimeFormatter

@Composable
fun CardsScreen(snapshot: FinanceSnapshot, onOpenCard: (AccountState) -> Unit, onNewCard: () -> Unit, contentPadding: PaddingValues) {
    val hidden = snapshot.settings.hideAmounts
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Row(Modifier.statusBarsPadding().fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Tarjetas", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onNewCard) { Icon(Icons.Outlined.Add, "Nueva tarjeta") }
            }
        }
        if (snapshot.loaded && snapshot.cards.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💳", style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(12.dp))
                    Text("Agrega tu tarjeta de crédito", style = MaterialTheme.typography.titleMedium)
                    Text("Registra compras en cuotas, con o sin interés, y mira cómo se reparten mes a mes y cómo recuperas cupo al pagar.",
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onNewCard) { Text("Agregar tarjeta") }
                }
            }
        }
        items(snapshot.cards, key = { it.account.id }) { st ->
            Column(Modifier.animateItem().padding(horizontal = 20.dp, vertical = 8.dp)) {
                CardVisual(st, hidden) { onOpenCard(st) }
                val c = st.card ?: return@Column
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniStat("A pagar", c.billedDue, if (c.billedDue > 0) "Vence " + c.dueDate.format(DateTimeFormatter.ofPattern("d MMM", esCL)) else "Al día ✓", st, hidden, Modifier.weight(1f))
                    MiniStat("Próximo estado", c.openPeriodAmount, "Cierra " + c.closingDate.format(DateTimeFormatter.ofPattern("d MMM", esCL)), st, hidden, Modifier.weight(1f))
                }
            }
        }
        if (snapshot.cards.size > 1) {
            item { SectionHeader("Cuotas de todas las tarjetas", Modifier.padding(top = 12.dp)) }
            item {
                val main = snapshot.settings.mainCurrency
                val combined = snapshot.cards.mapNotNull { st -> st.card?.projection?.map { (ym, v) -> ym to snapshot.settings.rates.convert(v, st.account.currency, main) } }
                    .flatten().groupBy { it.first }.map { (k, v) -> k to v.sumOf { it.second } }.sortedBy { it.first }.take(8)
                if (combined.isNotEmpty()) MonthBars(combined, main, hidden, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
fun CardVisual(st: AccountState, hidden: Boolean, onClick: () -> Unit) {
    val a = st.account
    val c = st.card
    val base = Color(a.color)
    PressableCard(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth().aspectRatio(1.9f)) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(base, base.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.6f)))).padding(20.dp)) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.name, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    a.lastDigits?.takeIf { it.isNotBlank() }?.let { Text("•••• $it", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge) }
                }
                if (c != null) Column {
                    Text("Cupo disponible", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
                    AnimatedAmount(c.available, a.currency, MaterialTheme.typography.headlineLarge, color = Color.White, hidden = hidden)
                    Spacer(Modifier.height(10.dp))
                    ProgressPill(c.usedFraction, color = Color.White, track = Color.White.copy(alpha = 0.22f), height = 6)
                    Spacer(Modifier.height(6.dp))
                    Row {
                        Text(if (hidden) "Usado •••" else "Usado ${Money.format(c.used, a.currency)}", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        Text(if (hidden) "Cupo •••" else "Cupo ${Money.format(c.limit, a.currency)}", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: Long, sub: String, st: AccountState, hidden: Boolean, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AmountText(value, st.account.currency, MaterialTheme.typography.titleLarge, hidden = hidden)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        }
    }
}

