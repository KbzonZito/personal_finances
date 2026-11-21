package com.personalfinances.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.personalfinances.data.FinanceSnapshot
import com.personalfinances.data.db.AccountEntity
import com.personalfinances.data.db.AccountType
import com.personalfinances.domain.Money
import com.personalfinances.ui.MainViewModel
import kotlinx.coroutines.launch

/** Bienvenida de 3 pasos: moneda → primera cuenta → detección por notificaciones. */
@Composable
fun OnboardingScreen(snapshot: FinanceSnapshot, vm: MainViewModel, onSetupNotifications: () -> Unit) {
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    fun next() = scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center) {
            repeat(3) { i ->
                val w by animateDpAsState(if (pager.currentPage == i) 28.dp else 8.dp, label = "dot")
                val c by animateColorAsState(if (pager.currentPage == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, label = "dotc")
                Box(Modifier.padding(horizontal = 4.dp).height(8.dp).width(w).clip(CircleShape).background(c))
            }
        }
        HorizontalPager(pager, Modifier.weight(1f), userScrollEnabled = false) { page ->
            when (page) {
                0 -> WelcomeStep(snapshot, vm, onNext = { next() })
                1 -> FirstAccountStep(snapshot, vm, onNext = { next() })
                else -> NotificationsStep(
                    onSetup = { vm.finishOnboarding(); onSetupNotifications() },
                    onSkip = { vm.finishOnboarding() },
                )
            }
        }
    }
}

@Composable
private fun StepColumn(emoji: String, title: String, body: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(32.dp))
        Box(Modifier.size(96.dp).clip(RoundedCornerShape(32.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text(emoji, style = MaterialTheme.typography.displaySmall)
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        content()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun WelcomeStep(snapshot: FinanceSnapshot, vm: MainViewModel, onNext: () -> Unit) {
    StepColumn("👋", "Tus finanzas, simples", "Cuentas, tarjetas en cuotas y cuentas remuneradas en un solo lugar. Todo se guarda solo en tu teléfono.") {
        Text("Moneda principal", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
        CurrencyDropdown(snapshot.settings.mainCurrency, Modifier.fillMaxWidth()) { vm.setMainCurrency(it) }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Empezar") }
    }
}

private val quickTypes = listOf(AccountType.CHECKING, AccountType.CREDIT_CARD, AccountType.YIELD, AccountType.CASH)

@Composable
private fun FirstAccountStep(snapshot: FinanceSnapshot, vm: MainViewModel, onNext: () -> Unit) {
    var type by rememberSaveable { mutableStateOf(AccountType.CHECKING) }
    var name by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var closing by rememberSaveable { mutableStateOf("20") }
    var due by rememberSaveable { mutableStateOf("5") }
    var rate by rememberSaveable { mutableStateOf("") }
    val currency = snapshot.settings.mainCurrency
    val parsed = Money.parse(amount, currency)
    val valid = name.isNotBlank() && (!type.isCard || (parsed ?: 0) > 0)

    StepColumn("🏦", "Tu primera cuenta", "Puedes agregar más después desde Inicio.") {
        LazyRow(contentPadding = PaddingValues(0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(quickTypes) { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text("${t.emoji} ${t.label}") }) }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(name, { name = it }, label = { Text(if (type.isCard) "Nombre (ej. Visa Santander)" else "Nombre (ej. Cuenta RUT)") },
            singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(amount, { amount = it }, label = { Text(if (type.isCard) "Cupo total" else "Saldo actual") }, prefix = { Text(currency.symbol) },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        if (type.isCard) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(closing, { closing = it.filter(Char::isDigit).take(2) }, label = { Text("Día de facturación") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            OutlinedTextField(due, { due = it.filter(Char::isDigit).take(2) }, label = { Text("Día de pago") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
        }
        if (type == AccountType.YIELD) OutlinedTextField(rate, { rate = it }, label = { Text("Rentabilidad anual (%)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        Spacer(Modifier.height(24.dp))
        Button(
            enabled = valid,
            onClick = {
                vm.saveAccount(AccountEntity(
                    name = name.trim(), type = type, currencyCode = currency.code,
                    initialBalance = if (type.isCard) 0 else parsed ?: 0,
                    creditLimit = if (type.isCard) parsed ?: 0 else 0,
                    closingDay = (closing.toIntOrNull() ?: 20).coerceIn(1, 31), dueDay = (due.toIntOrNull() ?: 5).coerceIn(1, 31),
                    annualRate = rate.replace(',', '.').toDoubleOrNull() ?: 0.0,
                    paidThroughPeriod = if (type.isCard) com.personalfinances.domain.CardCycle(
                        (closing.toIntOrNull() ?: 20).coerceIn(1, 31), (due.toIntOrNull() ?: 5).coerceIn(1, 31),
                    ).lastClosedPeriod(java.time.LocalDate.now()).minusMonths(1).let { it.year * 100 + it.monthValue } else 0,
                ))
                onNext()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Crear y continuar") }
        TextButton(onClick = onNext) { Text("Saltar por ahora") }
    }
}

@Composable
private fun NotificationsStep(onSetup: () -> Unit, onSkip: () -> Unit) {
    StepColumn("🔔", "Registra sin escribir", "Cuando tu banco o Google Wallet te avise una compra, la app te propone registrarla. Siempre con tu confirmación y solo de las apps que elijas.") {
        Button(onClick = onSetup, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Configurar ahora") }
        TextButton(onClick = onSkip) { Text("Más tarde") }
    }
}
