package com.personalfinances.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.personalfinances.data.FinanceSnapshot
import com.personalfinances.data.GoalState
import com.personalfinances.data.db.GoalEntity
import com.personalfinances.domain.Money
import com.personalfinances.ui.MainViewModel
import com.personalfinances.ui.components.monthLong
import com.personalfinances.ui.theme.LocalFinanceColors
import java.time.LocalDate

@Composable
private fun Ring(fraction: Float, color: Color, emoji: String) {
    val f by animateFloatAsState(fraction, spring(dampingRatio = 0.7f, stiffness = 60f), label = "ring")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(56.dp)) {
            val s = Stroke(6.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, 0f, 360f, false, style = s)
            if (f > 0f) drawArc(color, -90f, 360f * f, false, style = s)
        }
        Text(emoji, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun GoalRow(g: GoalState, hidden: Boolean, onClick: () -> Unit) {
    val p = g.progress
    val cur = g.goal.currency
    val fc = LocalFinanceColors.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Ring(p.fraction, Color(g.goal.color), g.goal.emoji)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(g.goal.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                if (hidden) "•••" else "${Money.format(p.current, cur)} de ${Money.format(p.target, cur)} · ${(p.fraction * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                when {
                    p.done -> "¡Meta cumplida! 🎉"
                    p.perMonthNeeded != null -> "Ahorra ${if (hidden) "•••" else Money.format(p.perMonthNeeded, cur)}/mes para llegar en ${monthLong(java.time.YearMonth.from(LocalDate.ofEpochDay(g.goal.deadlineEpochDay!!))).lowercase()}"
                    else -> "Faltan ${if (hidden) "•••" else Money.format(p.remaining, cur)}" + (g.accountName?.let { " · en $it" } ?: "")
                },
                style = MaterialTheme.typography.bodySmall, color = if (p.done) fc.income else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(snapshot: FinanceSnapshot, vm: MainViewModel, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<GoalEntity?>(null) }
    var contributing by remember { mutableStateOf<GoalEntity?>(null) }
    val main = snapshot.settings.mainCurrency
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Metas de ahorro", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") } },
                actions = { TextButton(onClick = { editing = GoalEntity(name = "", target = 0, currencyCode = main.code) }) { Text("Nueva") } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            if (snapshot.goals.isEmpty()) item {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Text("¿Para qué estás ahorrando?", style = MaterialTheme.typography.titleMedium)
                        Text("Un viaje, un fondo de emergencia, el pie de un depto… Asocia la meta a una cuenta (ej. Tenpo) y el avance se actualiza solo, considerando su rentabilidad.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { editing = GoalEntity(name = "", target = 0, currencyCode = main.code) }) { Text("Crear meta") }
                    }
                }
            }
            items(snapshot.goals, key = { it.goal.id }) { g ->
                Box(Modifier.animateItem()) {
                    GoalRow(g, snapshot.settings.hideAmounts) { if (g.goal.accountId == null && !g.progress.done) contributing = g.goal else editing = g.goal }
                }
            }
            if (snapshot.goals.any { it.goal.accountId == null }) item {
                Text("Toca una meta sin cuenta para abonar o editarla.", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
            }
        }
    }
    editing?.let { g -> GoalDialog(g, snapshot, onDismiss = { editing = null }, onSave = { vm.saveGoal(it); editing = null }, onDelete = { vm.deleteGoal(g.id); editing = null }) }
    contributing?.let { g ->
        var text by remember(g.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { contributing = null },
            title = { Text("${g.emoji} ${g.name}") },
            text = {
                Column {
                    OutlinedTextField(text, { v -> text = v.filter { it.isDigit() || it == ',' || it == '.' || it == '-' } }, label = { Text("Monto a abonar (negativo para retirar)") },
                        prefix = { Text(g.currency.symbol) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    TextButton(onClick = { editing = g; contributing = null }) { Text("Editar meta") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = (Money.parse(text, g.currency) ?: 0) * if (text.trim().startsWith("-")) -1 else 1
                    if (v != 0L) vm.saveGoal(g.copy(manualSaved = (g.manualSaved + v).coerceAtLeast(0)))
                    contributing = null
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { contributing = null }) { Text("Cancelar") } },
        )
    }
}

private val goalEmojis = listOf("🎯", "✈️", "🏠", "🚗", "☂️", "🎓", "💍", "💻", "🏖️", "👶", "🐶", "🎸")
private val goalColors = listOf(0xFF00B894, 0xFF0984E3, 0xFF6C5CE7, 0xFFE17055, 0xFFFDCB6E, 0xFFE84393)

@Composable
private fun GoalDialog(g: GoalEntity, snapshot: FinanceSnapshot, onDismiss: () -> Unit, onSave: (GoalEntity) -> Unit, onDelete: () -> Unit) {
    var name by remember { mutableStateOf(g.name) }
    var emoji by remember { mutableStateOf(g.emoji) }
    var color by remember { mutableStateOf(g.color) }
    var target by remember { mutableStateOf(if (g.target > 0) g.currency.toMajor(g.target).toPlainString() else "") }
    var accountId by remember { mutableStateOf(g.accountId) }
    var deadlineMonths by remember {
        mutableStateOf(g.deadlineEpochDay?.let { java.time.temporal.ChronoUnit.MONTHS.between(snapshot.today, LocalDate.ofEpochDay(it)).toInt().coerceAtLeast(1) })
    }
    val savings = snapshot.activeAccounts.filter { !it.account.type.isCard }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (g.id == 0L) "Nueva meta" else "Editar meta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(goalEmojis) { e -> FilterChip(selected = emoji == e, onClick = { emoji = e }, label = { Text(e) }) }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Nombre (ej. Viaje a Japón)") }, singleLine = true)
                OutlinedTextField(target, { v -> target = v.filter { it.isDigit() || it == ',' || it == '.' } }, label = { Text("Monto objetivo") },
                    prefix = { Text(g.currency.symbol) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Text("¿Dónde lo ahorras?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(0.dp)) {
                    item { FilterChip(selected = accountId == null, onClick = { accountId = null }, label = { Text("Abonos a mano") }) }
                    items(savings) { st -> FilterChip(selected = accountId == st.account.id, onClick = { accountId = st.account.id }, label = { Text("${st.account.type.emoji} ${st.account.name}") }) }
                }
                Text("¿Para cuándo?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf<Int?>(null, 3, 6, 12, 24, 36)) { m ->
                        FilterChip(selected = deadlineMonths == m, onClick = { deadlineMonths = m }, label = { Text(if (m == null) "Sin fecha" else if (m < 12) "$m meses" else "${m / 12} año${if (m > 12) "s" else ""}") })
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(goalColors) { c ->
                        Canvas(Modifier.size(28.dp).clickable { color = c }) {
                            drawCircle(Color(c))
                            if (c == color) drawCircle(Color.White, radius = size.minDimension / 6)
                        }
                    }
                }
                if (g.id != 0L) TextButton(onClick = onDelete) { Text("Eliminar meta", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            val t = Money.parse(target, g.currency) ?: 0
            TextButton(enabled = name.isNotBlank() && t > 0, onClick = {
                onSave(g.copy(
                    name = name.trim(), emoji = emoji, color = color, target = t, accountId = accountId,
                    deadlineEpochDay = deadlineMonths?.let { snapshot.today.plusMonths(it.toLong()).withDayOfMonth(1).toEpochDay() },
                ))
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
