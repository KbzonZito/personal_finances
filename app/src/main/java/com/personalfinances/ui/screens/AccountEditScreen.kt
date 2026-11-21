package com.personalfinances.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.personalfinances.data.FinanceSnapshot
import com.personalfinances.data.db.AccountEntity
import com.personalfinances.data.db.AccountType
import com.personalfinances.domain.Currency
import com.personalfinances.domain.CardCycle
import com.personalfinances.data.db.toPeriod
import com.personalfinances.data.db.toYearMonth
import com.personalfinances.ui.components.monthLong
import com.personalfinances.domain.Money

private val palette = listOf(
    0xFF6C5CE7, 0xFF0984E3, 0xFF00B894, 0xFF00CEC9, 0xFFE17055, 0xFFD63031,
    0xFFE84393, 0xFFFDCB6E, 0xFF2D3436, 0xFF636E72, 0xFF8E44AD, 0xFF16A085,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditScreen(
    snapshot: FinanceSnapshot,
    accountId: Long?,
    presetType: AccountType?,
    onBack: () -> Unit,
    onSave: (AccountEntity) -> Unit,
    onDelete: (AccountEntity) -> Unit,
) {
    val existing = accountId?.let { snapshot.account(it)?.account }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(presetType ?: AccountType.CHECKING) }
    var currency by rememberSaveable { mutableStateOf(snapshot.settings.mainCurrency) }
    var initial by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable { mutableStateOf("") }
    var closingDay by rememberSaveable { mutableStateOf("20") }
    var dueDay by rememberSaveable { mutableStateOf("5") }
    var rate by rememberSaveable { mutableStateOf("") }
    var digits by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf(palette[snapshot.accounts.size % palette.size]) }
    var includeInTotal by rememberSaveable { mutableStateOf(true) }
    var archived by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var loadedBalanceText by rememberSaveable { mutableStateOf("") }
    var paidThrough by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(existing) {
        if (loaded || existing == null) return@LaunchedEffect
        loaded = true
        name = existing.name
        type = existing.type
        currency = existing.currency
        val current = snapshot.account(existing.id)?.balance ?: existing.initialBalance
        initial = if (current != 0L) existing.currency.toMajor(current).toPlainString().replace('.', ',') else ""
        loadedBalanceText = initial
        paidThrough = existing.paidThroughPeriod
        limit = if (existing.creditLimit != 0L) existing.currency.toMajor(existing.creditLimit).toPlainString().replace('.', ',') else ""
        closingDay = existing.closingDay.toString()
        dueDay = existing.dueDay.toString()
        rate = if (existing.annualRate > 0) existing.annualRate.toBigDecimal().stripTrailingZeros().toPlainString().replace('.', ',') else ""
        digits = existing.lastDigits.orEmpty()
        color = existing.color
        includeInTotal = existing.includeInTotal
        archived = existing.archived
    }

    val valid = name.isNotBlank() && (!type.isCard || (Money.parse(limit, currency) ?: 0) > 0) &&
        (closingDay.toIntOrNull() in 1..31) && (dueDay.toIntOrNull() in 1..31)

    val cycle = CardCycle((closingDay.toIntOrNull() ?: 20).coerceIn(1, 31), (dueDay.toIntOrNull() ?: 5).coerceIn(1, 31))
    val lastClosed = cycle.lastClosedPeriod(snapshot.today)
    val paidThroughValue = paidThrough ?: lastClosed.minusMonths(1).toPeriod()

    fun build(): AccountEntity {
        val base = existing ?: AccountEntity(name = "", type = type)
        val typed = (if (initial.trim().startsWith("-")) -1 else 1) * (Money.parse(initial, currency) ?: 0)
        // Si cambió el saldo actual, se "re-ancla": ese es el saldo de ahora y lo anterior queda como historial.
        val (initialBalance, anchor) = when {
            type.isCard -> 0L to base.balanceAnchorAt
            existing == null -> typed to 0L
            initial != loadedBalanceText -> typed to System.currentTimeMillis()
            else -> base.initialBalance to base.balanceAnchorAt
        }
        return base.copy(
        name = name.trim(),
        type = type,
        currencyCode = currency.code,
        initialBalance = initialBalance,
        balanceAnchorAt = anchor,
        paidThroughPeriod = if (type.isCard) paidThroughValue else 0,
        creditLimit = if (type.isCard) Money.parse(limit, currency) ?: 0 else 0,
        closingDay = closingDay.toIntOrNull() ?: 20,
        dueDay = dueDay.toIntOrNull() ?: 5,
        annualRate = if (type == AccountType.YIELD || type == AccountType.SAVINGS) rate.replace(',', '.').toDoubleOrNull() ?: 0.0 else 0.0,
        lastDigits = digits.takeIf { it.length == 4 },
        color = color,
        includeInTotal = includeInTotal,
        archived = archived,
    )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (existing == null) "Nueva cuenta" else "Editar cuenta", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") } },
                actions = { TextButton(enabled = valid, onClick = { onSave(build()) }) { Text("Guardar") } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Label("Tipo")
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AccountType.entries) { t ->
                    FilterChip(selected = type == t, onClick = { type = t }, label = { Text("${t.emoji} ${t.label}") })
                }
            }
            Field(name, { name = it }, if (type.isCard) "Nombre (ej. Visa Santander)" else "Nombre (ej. Cuenta RUT, Tenpo)", capitalize = true)

            Label("Moneda")
            CurrencyDropdown(currency) { currency = it }

            AnimatedVisibility(!type.isCard) {
                Column {
                    Field(initial, { initial = it }, "Saldo actual (hoy)", KeyboardType.Decimal, prefix = currency.symbol)
                    Text(
                        "Si cargas movimientos con fechas anteriores, quedan como historial y no cambian este saldo.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
            AnimatedVisibility(type.isCard) {
                Column {
                    Field(limit, { limit = it }, "Cupo total", KeyboardType.Decimal, prefix = currency.symbol)
                    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(closingDay, { closingDay = it.filter(Char::isDigit).take(2) }, label = { Text("Día de facturación") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(dueDay, { dueDay = it.filter(Char::isDigit).take(2) }, label = { Text("Día de pago") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Text(
                        "Las compras hasta el día de facturación se cobran en el estado de cuenta de ese mes; el pago vence el día de pago siguiente.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                    Label("Último estado de cuenta que ya pagaste")
                    val options = listOf(lastClosed, lastClosed.minusMonths(1), lastClosed.minusMonths(2)).map { it.toPeriod() } + 0
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(options) { p ->
                            FilterChip(selected = paidThroughValue == p, onClick = { paidThrough = p },
                                label = { Text(if (p == 0) "Ninguno" else monthLong(p.toYearMonth())) })
                        }
                    }
                    Text(
                        "Las cuotas de compras antiguas que caen en esos estados de cuenta se dan por pagadas. Así puedes cargar compras pasadas con su fecha real.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
            }
            AnimatedVisibility(type == AccountType.YIELD || type == AccountType.SAVINGS) {
                Column {
                    Field(rate, { rate = it }, "Rentabilidad anual (%)", KeyboardType.Decimal)
                    Text(
                        "Ej: Tenpo informa su tasa anual. La app sumará el interés diario estimado automáticamente.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
            Field(digits, { digits = it.filter(Char::isDigit).take(4) }, "Últimos 4 dígitos (opcional, para notificaciones)", KeyboardType.Number)

            Label("Color")
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(palette) { c ->
                    val sel = c == color
                    val s by animateFloatAsState(if (sel) 1.15f else 1f, label = "color")
                    Box(
                        Modifier.size(36.dp).scale(s).clip(CircleShape).background(Color(c))
                            .border(if (sel) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { color = c },
                        contentAlignment = Alignment.Center,
                    ) { if (sel) Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                }
            }

            SwitchRow("Incluir en patrimonio", includeInTotal) { includeInTotal = it }
            if (existing != null) {
                SwitchRow("Archivar (ocultar)", archived) { archived = it }
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                ) { Text("Eliminar cuenta", color = MaterialTheme.colorScheme.error) }
            }
            Button(onClick = { onSave(build()) }, enabled = valid, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).height(52.dp)) {
                Text("Guardar")
            }
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar ${existing.name}?") },
            text = { Text("Se eliminarán sus movimientos. Las transferencias con otras cuentas se conservan en esas cuentas como gastos o ingresos, así sus saldos no cambian. Si solo quieres ocultarla, usa Archivar.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(existing) }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Label(text: String) = Text(
    text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp),
)

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, keyboard: KeyboardType = KeyboardType.Text, prefix: String? = null, capitalize: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        prefix = prefix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, capitalization = if (capitalize) KeyboardCapitalization.Words else KeyboardCapitalization.None),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyDropdown(selected: Currency, modifier: Modifier = Modifier.padding(horizontal = 20.dp), onSelect: (Currency) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = it }, modifier) {
        OutlinedTextField(
            value = "${selected.flag}  ${selected.code} — ${selected.displayName}", onValueChange = {}, readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            Currency.entries.forEach { c ->
                DropdownMenuItem(text = { Text("${c.flag}  ${c.code} — ${c.displayName}") }, onClick = { onSelect(c); expanded = false })
            }
        }
    }
}

