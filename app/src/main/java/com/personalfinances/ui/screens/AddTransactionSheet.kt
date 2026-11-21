package com.personalfinances.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personalfinances.container
import com.personalfinances.data.FinanceSnapshot
import com.personalfinances.data.TransactionDraft
import com.personalfinances.data.db.AccountEntity
import com.personalfinances.data.db.DetectionEntity
import com.personalfinances.data.db.TxSource
import com.personalfinances.data.db.TxType
import com.personalfinances.domain.Currency
import com.personalfinances.domain.Frequency
import com.personalfinances.domain.InstallmentPlanner
import com.personalfinances.domain.InterestSpec
import com.personalfinances.domain.Money
import com.personalfinances.ui.components.PillTabs
import com.personalfinances.ui.components.esCL
import com.personalfinances.ui.theme.AmountStyle
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class AddRequest(
    val type: TxType = TxType.EXPENSE,
    val transactionId: Long? = null,
    val detectionId: Long? = null,
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val amount: Long? = null,
)

private enum class InterestMode(val label: String) { NONE("Sin interés"), INSTALLMENT("Valor cuota"), TOTAL("Total"), RATE("Tasa %") }

private val types = listOf(TxType.EXPENSE, TxType.INCOME, TxType.TRANSFER)

/** Convierte el texto del teclado ("12345.5") a unidad mínima. */
private fun keypadToMinor(text: String, c: Currency): Long =
    if (text.isEmpty() || text == ".") 0 else runCatching { c.toMinor(BigDecimal(text)) }.getOrDefault(0)

private fun minorToKeypad(v: Long, c: Currency): String = c.toMajor(v).stripTrailingZeros().toPlainString().let { if (it == "0") "" else it }

private fun displayKeypad(text: String, c: Currency): String {
    if (text.isEmpty()) return c.symbol + "0"
    val parts = text.split(".")
    val intPart = parts[0].ifEmpty { "0" }.toBigInteger()
    val grouped = java.text.DecimalFormat("#,##0", java.text.DecimalFormatSymbols(esCL)).format(intPart)
    return c.symbol + grouped + if (parts.size > 1) "," + parts[1] else ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionContent(
    request: AddRequest,
    snapshot: FinanceSnapshot,
    onSave: (TransactionDraft) -> Unit,
    onDelete: (Long) -> Unit,
    onDismissDetection: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** Si se entrega, aparece "Guardar y otro": guarda y deja la hoja abierta (útil para cargar historial). */
    onSaveAndContinue: ((TransactionDraft) -> Unit)? = null,
) {
    val context = LocalContext.current
    val repo = context.container.repository
    val haptic = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    val accounts = snapshot.activeAccounts.map { it.account }

    var type by rememberSaveable { mutableStateOf(request.type) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var accountId by rememberSaveable { mutableStateOf(request.accountId) }
    var toAccountId by rememberSaveable { mutableStateOf(request.toAccountId) }
    var toAmountText by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var description by rememberSaveable { mutableStateOf("") }
    var epochDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var installments by rememberSaveable { mutableIntStateOf(1) }
    var interestMode by rememberSaveable { mutableStateOf(InterestMode.NONE) }
    var interestText by rememberSaveable { mutableStateOf("") }
    var deferMonths by rememberSaveable { mutableIntStateOf(0) }
    var paidInstallments by rememberSaveable { mutableIntStateOf(0) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var descFocused by remember { mutableStateOf(false) }
    var detection by remember { mutableStateOf<DetectionEntity?>(null) }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var categoryTouched by rememberSaveable { mutableStateOf(false) }
    var repeat by rememberSaveable { mutableStateOf<Frequency?>(null) }
    /** Moneda en que se ingresa el monto, si es distinta a la de la cuenta (ej. compra en US$). */
    var entryCurrency by rememberSaveable { mutableStateOf<Currency?>(null) }
    var chargedText by rememberSaveable { mutableStateOf("") }
    var savedCount by rememberSaveable { mutableIntStateOf(0) }
    var currencyMenu by remember { mutableStateOf(false) }

    val account = accounts.firstOrNull { it.id == accountId }
    val currency = account?.currency ?: snapshot.settings.mainCurrency
    val foreign = entryCurrency?.takeIf { it != currency && type != TxType.TRANSFER }
    val inputCurrency = foreign ?: currency
    val toAccount = accounts.firstOrNull { it.id == toAccountId }

    // Carga inicial: edición, detección o valores por defecto.
    LaunchedEffect(request, snapshot.loaded) {
        if (initialized || !snapshot.loaded) return@LaunchedEffect
        initialized = true
        val tx = request.transactionId?.let { repo.transaction(it) }
        if (tx != null) {
            type = if (tx.type in types) tx.type else TxType.EXPENSE
            accountId = tx.accountId
            toAccountId = tx.toAccountId
            val c = accounts.firstOrNull { it.id == tx.accountId }?.currency ?: Currency.CLP
            amountText = minorToKeypad(tx.amount, c)
            tx.toAmount?.let { ta -> toAmountText = minorToKeypad(ta, accounts.firstOrNull { it.id == tx.toAccountId }?.currency ?: c) }
            tx.originalCurrency?.let { oc ->
                val ocur = Currency.fromCode(oc)
                entryCurrency = ocur
                amountText = minorToKeypad(tx.originalAmount ?: 0, ocur)
                chargedText = minorToKeypad(tx.amount, c).replace('.', ',')
            }
            categoryId = tx.categoryId
            categoryTouched = true
            description = tx.description
            epochDay = tx.epochDay
            installments = tx.installmentCount
            paidInstallments = tx.installmentsPaid
            deferMonths = tx.deferMonths
            interestMode = runCatching { InterestMode.valueOf(tx.interestMode) }.getOrDefault(InterestMode.NONE)
            interestText = when (interestMode) {
                InterestMode.NONE -> ""
                InterestMode.RATE -> (tx.interestValue * 100).toBigDecimal().stripTrailingZeros().toPlainString()
                else -> minorToKeypad(tx.interestValue.toLong(), c)
            }
            return@LaunchedEffect
        }
        val det = request.detectionId?.let { repo.detection(it) }
        val template = det?.recurringId?.let { repo.recurring(it) }
        if (det != null && template != null) {
            detection = det
            type = template.type
            accountId = template.accountId
            toAccountId = template.toAccountId
            categoryId = template.categoryId
            categoryTouched = true
            description = template.description
            epochDay = Instant.ofEpochMilli(det.postedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
            amountText = minorToKeypad(template.amount, accounts.firstOrNull { it.id == template.accountId }?.currency ?: det.currency)
            return@LaunchedEffect
        }
        if (det != null) {
            detection = det
            type = if (det.isIncome) TxType.INCOME else TxType.EXPENSE
            description = det.merchant.orEmpty()
            epochDay = Instant.ofEpochMilli(det.postedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
            accountId = repo.suggestAccount(det.packageName, det.lastDigits)
                ?: accounts.firstOrNull { it.currencyCode == det.currencyCode }?.id ?: accounts.firstOrNull()?.id
            val c = accounts.firstOrNull { it.id == accountId }?.currency ?: det.currency
            if (c != det.currency) entryCurrency = det.currency
            amountText = minorToKeypad(det.amount, det.currency)
            categoryId = repo.suggestCategory(description)
            return@LaunchedEffect
        }
        request.amount?.let { amountText = minorToKeypad(it, accounts.firstOrNull { a -> a.id == (request.accountId ?: request.toAccountId) }?.currency ?: currency) }
        if (accountId == null) {
            accountId = if (type == TxType.TRANSFER) {
                accounts.firstOrNull { !it.type.isCard && it.id != toAccountId }?.id
            } else {
                snapshot.transactions.take(40).filter { it.tx.type == type }
                    .groupingBy { it.tx.accountId }.eachCount().maxByOrNull { it.value }?.key
                    ?.takeIf { id -> accounts.any { it.id == id } } ?: accounts.firstOrNull()?.id
            }
        }
    }

    // Sugerir categoría según el comercio escrito.
    LaunchedEffect(description) {
        if (!categoryTouched && description.length >= 3) repo.suggestCategory(description)?.let { categoryId = it }
    }

    val enteredAmount = keypadToMinor(amountText, inputCurrency)
    val chargedOverride = if (foreign != null) Money.parse(chargedText, currency)?.takeIf { it > 0 } else null
    val amount = when {
        foreign == null -> enteredAmount
        chargedOverride != null -> chargedOverride
        else -> snapshot.settings.rates.convert(enteredAmount, foreign, currency)
    }
    val isCardExpense = account?.type?.isCard == true && type == TxType.EXPENSE
    val crossCurrency = type == TxType.TRANSFER && toAccount != null && toAccount.currency != currency
    val interestSpec: InterestSpec = if (!isCardExpense || installments <= 1) InterestSpec.None else when (interestMode) {
        InterestMode.NONE -> InterestSpec.None
        InterestMode.INSTALLMENT -> keypadToMinor(interestText.replace(',', '.'), currency).takeIf { it > 0 }?.let { InterestSpec.FixedInstallment(it) } ?: InterestSpec.None
        InterestMode.TOTAL -> keypadToMinor(interestText.replace(',', '.'), currency).takeIf { it > 0 }?.let { InterestSpec.Total(it) } ?: InterestSpec.None
        InterestMode.RATE -> interestText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let { InterestSpec.MonthlyRate(it / 100) } ?: InterestSpec.None
    }
    val canSave = amount > 0 && account != null && (type != TxType.TRANSFER || (toAccount != null && toAccount.id != account.id))

    fun buildDraft(): TransactionDraft? {
        val acc = account ?: return null
        val toAmount = if (crossCurrency) keypadToMinor(toAmountText.replace(',', '.'), toAccount!!.currency).takeIf { it > 0 }
            ?: snapshot.settings.rates.convert(amount, currency, toAccount.currency) else null
        return (
            TransactionDraft(
                id = request.transactionId ?: 0,
                type = type,
                accountId = acc.id,
                toAccountId = if (type == TxType.TRANSFER) toAccountId else null,
                amount = amount,
                toAmount = toAmount,
                date = LocalDate.ofEpochDay(epochDay),
                description = description,
                categoryId = if (type == TxType.TRANSFER) null else categoryId,
                installments = if (isCardExpense) installments else 1,
                installmentsPaid = if (isCardExpense) paidInstallments else 0,
                interest = interestSpec,
                deferMonths = if (isCardExpense) deferMonths else 0,
                source = when {
                    detection?.recurringId != null -> TxSource.AUTO
                    detection != null -> TxSource.NOTIFICATION
                    else -> TxSource.MANUAL
                },
                detectionId = detection?.id,
                repeat = repeat,
                originalAmount = if (foreign != null) enteredAmount else null,
                originalCurrency = foreign?.code,
            )
        )
    }

    fun save() {
        val d = buildDraft() ?: return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onSave(d)
    }

    fun saveAndContinue() {
        val d = buildDraft() ?: return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onSaveAndContinue?.invoke(d)
        // Conserva tipo, cuenta, fecha y moneda; limpia lo demás para el siguiente.
        amountText = ""; chargedText = ""; description = ""; categoryId = null; categoryTouched = false
        installments = 1; interestMode = InterestMode.NONE; interestText = ""; deferMonths = 0; paidInstallments = 0; repeat = null
        savedCount++
    }

    Column(modifier.fillMaxWidth().imePadding()) {
        // Encabezado
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Cerrar") }
            PillTabs(
                options = listOf("Gasto", "Ingreso", "Transferir"),
                selected = types.indexOf(type),
                onSelect = { type = types[it]; categoryId = null; categoryTouched = false },
                modifier = Modifier.weight(1f),
            )
            if (request.transactionId != null) {
                IconButton(onClick = { onDelete(request.transactionId) }) { Icon(Icons.Outlined.DeleteOutline, "Eliminar", tint = MaterialTheme.colorScheme.error) }
            } else if (detection != null) {
                TextButton(onClick = { onDismissDetection(detection!!.id) }) { Text("Ignorar") }
            } else Spacer(Modifier.width(48.dp))
        }

        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).animateContentSize()) {
            detection?.let { det ->
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.NotificationsActive, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(if (det.recurringId != null) "Movimiento programado" else "Detectado en ${det.appLabel}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(det.text.ifBlank { det.title }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 2)
                        }
                    }
                }
            }

            // Monto
            val amountScale by animateFloatAsState(if (amountText.isEmpty()) 0.92f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "amt")
            Text(
                displayKeypad(amountText, inputCurrency),
                style = MaterialTheme.typography.displayMedium.merge(AmountStyle).copy(fontSize = if (amountText.length > 9) 40.sp else 52.sp),
                color = if (amountText.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp).scale(amountScale),
            )
            if (type != TxType.TRANSFER) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.AssistChip(
                        onClick = { currencyMenu = true },
                        label = { Text("${inputCurrency.flag} ${inputCurrency.code} ▾") },
                    )
                    androidx.compose.material3.DropdownMenu(currencyMenu, { currencyMenu = false }) {
                        (listOf(currency) + Currency.entries.filter { it != currency }).forEach { c ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("${c.flag}  ${c.code} — ${c.displayName}") },
                                onClick = { entryCurrency = if (c == currency) null else c; amountText = ""; chargedText = ""; currencyMenu = false },
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(foreign != null) {
                Column {
                    Text(
                        "≈ ${Money.format(amount, currency)} en ${account?.name ?: ""}" + if (chargedOverride == null) " (1 ${(foreign ?: currency).code} = ${Money.format(currency.fromMajorDouble(snapshot.settings.rates.rate(foreign ?: currency) / snapshot.settings.rates.rate(currency)), currency)})" else "",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    )
                    OutlinedTextField(
                        value = chargedText, onValueChange = { chargedText = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' } },
                        label = { Text("Monto cobrado en ${currency.code} (opcional)") },
                        supportingText = { Text("Si tu banco ya te informó el monto en ${currency.code}, ponlo aquí") },
                        prefix = { Text(currency.symbol) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).onFocusChanged { descFocused = it.isFocused },
                    )
                }
            }
            if (isCardExpense && installments > 1 && amount > 0) {
                val plan = InstallmentPlanner.plan(amount, installments, java.time.YearMonth.now(), interestSpec)
                val total = plan.sumOf { it.amount }
                Text(
                    "$installments cuotas de ${Money.format(plan.last().amount, currency)}" +
                        if (total > amount) " · total ${Money.format(total, currency)} (+${Money.format(total - amount, currency)} interés)" else " · sin interés",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                )
            }
            Spacer(Modifier.height(12.dp))

            // Cuentas
            FieldLabel(if (type == TxType.TRANSFER) "Desde" else "Cuenta")
            AccountChips(accounts, accountId, snapshot) { accountId = it }
            if (type == TxType.TRANSFER) {
                FieldLabel("Hacia")
                AccountChips(accounts.filter { it.id != accountId }, toAccountId, snapshot) { toAccountId = it }
                AnimatedVisibility(crossCurrency) {
                    OutlinedTextField(
                        value = toAmountText, onValueChange = { toAmountText = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' } },
                        label = { Text("Monto recibido en ${toAccount?.currency?.code}") },
                        placeholder = { toAccount?.let { Text(Money.format(snapshot.settings.rates.convert(amount, currency, it.currency), it.currency)) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).onFocusChanged { descFocused = it.isFocused },
                    )
                }
            }

            // Cuotas (solo tarjetas de crédito)
            AnimatedVisibility(isCardExpense, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    FieldLabel("Cuotas")
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(onClick = { if (installments > 1) installments-- }) { Icon(Icons.Outlined.Remove, "Menos") }
                        Text("$installments", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.width(56.dp))
                        FilledTonalIconButton(onClick = { if (installments < 48) installments++ }) { Icon(Icons.Outlined.Add, "Más") }
                        Spacer(Modifier.width(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(listOf(1, 3, 6, 12, 24)) { n ->
                                FilterChip(selected = installments == n, onClick = { installments = n }, label = { Text("$n") })
                            }
                        }
                    }
                    AnimatedVisibility(installments > 1) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                                InterestMode.entries.forEachIndexed { i, m ->
                                    SegmentedButton(
                                        selected = interestMode == m,
                                        onClick = { interestMode = m; interestText = "" },
                                        shape = SegmentedButtonDefaults.itemShape(i, InterestMode.entries.size),
                                        icon = {},
                                    ) { Text(m.label, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                                }
                            }
                            AnimatedVisibility(interestMode != InterestMode.NONE) {
                                OutlinedTextField(
                                    value = interestText,
                                    onValueChange = { interestText = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' } },
                                    label = {
                                        Text(when (interestMode) {
                                            InterestMode.INSTALLMENT -> "Valor de cada cuota (informado por el banco)"
                                            InterestMode.TOTAL -> "Total a pagar con intereses"
                                            else -> "Tasa de interés mensual (%)"
                                        })
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).onFocusChanged { descFocused = it.isFocused },
                                )
                            }
                            TextButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.padding(horizontal = 12.dp)) {
                                Text(if (showAdvanced) "Ocultar opciones" else "Primera cuota diferida o compra antigua")
                            }
                            AnimatedVisibility(showAdvanced) {
                                Column {
                                    FieldLabel("Primera cuota")
                                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(listOf(0, 1, 2, 3)) { m ->
                                            FilterChip(selected = deferMonths == m && paidInstallments == 0, onClick = { deferMonths = m; paidInstallments = 0 },
                                                label = { Text(if (m == 0) "Próximo estado" else "+$m ${if (m == 1) "mes" else "meses"}") })
                                        }
                                    }
                                    FieldLabel("Compra antigua: cuotas ya pagadas")
                                    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                                        FilledTonalIconButton(onClick = { if (paidInstallments > 0) paidInstallments-- }) { Icon(Icons.Outlined.Remove, "Menos") }
                                        Text("$paidInstallments", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.width(48.dp))
                                        FilledTonalIconButton(onClick = { if (paidInstallments < installments - 1) paidInstallments++ }) { Icon(Icons.Outlined.Add, "Más") }
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            if (paidInstallments > 0) "La cuota ${paidInstallments + 1} se cobra en el próximo estado de cuenta" else "Se cobran todas las cuotas",
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Categorías
            if (type != TxType.TRANSFER) {
                FieldLabel("Categoría")
                val cats = snapshot.categories.filter { it.isIncome == (type == TxType.INCOME) }
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(cats, key = { it.id }) { c ->
                        val selected = c.id == categoryId
                        FilterChip(
                            selected = selected,
                            onClick = { categoryId = if (selected) null else c.id; categoryTouched = true },
                            label = { Text("${c.emoji}  ${c.name}") },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = androidx.compose.ui.graphics.Color(c.color).copy(alpha = 0.25f)),
                        )
                    }
                }
            }

            // Descripción + fecha
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                placeholder = { Text(if (type == TxType.INCOME) "Descripción" else "¿Dónde o en qué?") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focus.clearFocus() }),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).onFocusChanged { descFocused = it.isFocused },
            )
            val today = LocalDate.now()
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = epochDay == today.toEpochDay(), onClick = { epochDay = today.toEpochDay() }, label = { Text("Hoy") }) }
                item { FilterChip(selected = epochDay == today.minusDays(1).toEpochDay(), onClick = { epochDay = today.minusDays(1).toEpochDay() }, label = { Text("Ayer") }) }
                item {
                    val custom = epochDay < today.minusDays(1).toEpochDay() || epochDay > today.toEpochDay()
                    FilterChip(
                        selected = custom, onClick = { showDatePicker = true },
                        leadingIcon = { Icon(Icons.Outlined.CalendarToday, null, Modifier.size(16.dp)) },
                        label = { Text(if (custom) LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("d MMM yyyy", esCL)) else "Otra fecha") },
                    )
                }
            }
            if (request.transactionId == null && detection == null) {
                LazyRow(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = repeat == null, onClick = { repeat = null }, label = { Text("No se repite") }) }
                    items(Frequency.entries) { f ->
                        FilterChip(
                            selected = repeat == f, onClick = { repeat = f },
                            leadingIcon = if (repeat == f) { { Icon(Icons.Outlined.Repeat, null, Modifier.size(16.dp)) } } else null,
                            label = { Text(f.every.replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Teclado numérico
        AnimatedVisibility(!descFocused, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Keypad(
                decimals = inputCurrency.decimals,
                onKey = { key ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    amountText = applyKey(amountText, key, inputCurrency.decimals)
                },
                onClear = { amountText = "" },
            )
        }
        AnimatedVisibility(savedCount > 0) {
            Text(
                "✓ $savedCount ${if (savedCount == 1) "guardado" else "guardados"}",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        val showContinue = onSaveAndContinue != null && request.transactionId == null && detection == null
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showContinue) {
            androidx.compose.material3.FilledTonalButton(
                onClick = ::saveAndContinue, enabled = canSave, shape = MaterialTheme.shapes.large,
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text("Guardar y otro", style = MaterialTheme.typography.titleSmall) }
        }
        Button(
            onClick = ::save,
            enabled = canSave,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.weight(if (showContinue) 1.3f else 1f).height(56.dp),
        ) {
            Text(
                when {
                    request.transactionId != null -> "Guardar cambios"
                    type == TxType.TRANSFER && toAccount?.type?.isCard == true -> "Pagar tarjeta"
                    type == TxType.TRANSFER -> "Transferir"
                    type == TxType.INCOME -> "Agregar ingreso"
                    else -> "Agregar gasto"
                },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
        }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = LocalDate.ofEpochDay(epochDay).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { epochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    showDatePicker = false
                }) { Text("Listo") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } },
        ) { DatePicker(state) }
    }
}

private fun applyKey(text: String, key: String, decimals: Int): String = when (key) {
    "⌫" -> text.dropLast(1)
    "." -> if (decimals == 0 || text.contains('.')) text else if (text.isEmpty()) "0." else "$text."
    "000" -> if (text.isEmpty() || text.length > 9) text else text + "000"
    else -> {
        val dot = text.indexOf('.')
        when {
            dot >= 0 && text.length - dot - 1 >= decimals -> text
            dot < 0 && text.length >= 12 -> text
            text == "0" -> key
            else -> text + key
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
    )
}

@Composable
private fun AccountChips(accounts: List<AccountEntity>, selected: Long?, snapshot: FinanceSnapshot, onSelect: (Long) -> Unit) {
    if (accounts.isEmpty()) {
        Text("Primero crea una cuenta en Inicio → Cuentas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp))
        return
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(selected, accounts.size) {
        val idx = accounts.indexOfFirst { it.id == selected }
        if (idx > 0) listState.animateScrollToItem(idx)
    }
    LazyRow(state = listState, contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(accounts, key = { it.id }) { a ->
            val isSel = a.id == selected
            val st = snapshot.account(a.id)
            FilterChip(
                selected = isSel,
                onClick = { onSelect(a.id) },
                shape = MaterialTheme.shapes.medium,
                label = {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text("${a.type.emoji} ${a.name}", fontWeight = FontWeight.Medium)
                        val sub = if (a.type.isCard) st?.card?.let { "Disponible ${Money.format(it.available, a.currency)}" } else st?.let { Money.format(it.balance, a.currency) }
                        if (sub != null && !snapshot.settings.hideAmounts) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = androidx.compose.ui.graphics.Color(a.color).copy(alpha = 0.28f)),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Keypad(decimals: Int, onKey: (String) -> Unit, onClear: () -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(if (decimals > 0) "." else "000", "0", "⌫"),
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val bg by androidx.compose.animation.animateColorAsState(
                        if (pressed) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh, label = "key",
                    )
                    Box(
                        Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(16.dp)).background(bg)
                            .combinedClickable(
                                interactionSource = interaction, indication = null,
                                onClick = { onKey(key) },
                                onLongClick = if (key == "⌫") onClear else null,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (key == "⌫") Icon(Icons.AutoMirrored.Outlined.Backspace, "Borrar")
                        else Text(if (key == ".") "," else key, style = MaterialTheme.typography.titleLarge.merge(AmountStyle))
                    }
                }
            }
        }
    }
}

