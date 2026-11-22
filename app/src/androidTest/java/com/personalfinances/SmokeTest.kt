package com.personalfinances

import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.personalfinances.data.db.AccountEntity
import com.personalfinances.data.db.AccountType
import com.personalfinances.data.db.TxType
import com.personalfinances.data.TransactionDraft
import com.personalfinances.ui.MainActivity
import com.personalfinances.ui.QuickAddActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Recorre la app real en un emulador: onboarding, primer gasto, pago de tarjeta y agregado rápido.
 * Corre en GitHub Actions (workflow "UI tests").
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<FinanceApp>()
    private val c get() = app.container

    @Before fun reset() = runBlocking {
        c.db.clearAllTables()
        c.settings.clearAll()
        c.repository.ensureSeeded()
    }

    private fun waitText(text: String, timeout: Long = 15_000) =
        compose.waitUntilAtLeastOneExists(hasText(text, substring = true), timeout)

    private fun type(amountDigits: String) = amountDigits.forEach { compose.onNodeWithTag("key_$it").performClick() }

    @Test fun onboardingThenFirstExpense() {
        ActivityScenario.launch(MainActivity::class.java)
        waitText("Empezar")
        compose.onNodeWithText("Empezar").performClick()
        waitText("Tu primera cuenta")
        compose.onNodeWithText("Nombre (ej. Cuenta RUT)").performTextInput("Corriente")
        compose.onNodeWithText("Saldo actual").performTextInput("500000")
        compose.onNodeWithText("Crear y continuar").performClick()
        waitText("Registra sin escribir")
        compose.onNodeWithText("Más tarde").performClick()
        waitText("Patrimonio neto")
        waitText("$500.000")

        compose.onNodeWithContentDescription("Agregar movimiento").performClick()
        waitText("Agregar gasto")
        type("12000")
        compose.onNodeWithText("Agregar gasto").performClick()
        waitText("$488.000")

        compose.onAllNodesWithText("Movimientos").onFirst().performClick()
        waitText("-$12.000")
    }

    @Test fun payCardReturnsLimitAndDebitsBank() {
        runBlocking {
            c.settings.setOnboardingDone()
            c.repository.saveAccount(AccountEntity(name = "Corriente", type = AccountType.CHECKING, initialBalance = 1_000_000))
            val card = c.repository.saveAccount(AccountEntity(name = "Visa Test", type = AccountType.CREDIT_CARD, creditLimit = 1_000_000))
            c.repository.reconcileCard(card, available = 800_000, billed = 150_000, nextStatement = 50_000)
        }
        ActivityScenario.launch(MainActivity::class.java)
        waitText("Patrimonio neto")
        compose.onAllNodesWithText("Tarjetas").onFirst().performClick()
        waitText("Visa Test")
        compose.onAllNodesWithText("Visa Test").onFirst().performClick()
        waitText("Estado de cuenta")
        compose.onAllNodesWithText("Pagar").onFirst().performClick()
        waitText("Pagar $150.000")
        compose.onNodeWithText("Pagar $150.000").performClick()
        waitText("Pago registrado")
        waitText("$950.000")
        val bank = runBlocking { c.repository.snapshot.value.accounts.first { it.account.name == "Corriente" }.balance }
        assertEquals(850_000L, bank)
    }

    @Test fun quickAddFromWidgetShortcut() {
        runBlocking {
            c.settings.setOnboardingDone()
            c.repository.saveAccount(AccountEntity(name = "Corriente", type = AccountType.CHECKING, initialBalance = 100_000))
        }
        val intent = QuickAddActivity.intent(app, TxType.EXPENSE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<QuickAddActivity>(intent)
        waitText("Agregar gasto")
        type("5000")
        compose.onNodeWithText("Agregar gasto").performClick()
        compose.waitUntil(10_000) { runBlocking { c.db.transactions().getAll() }.any { it.amount == 5_000L } }
    }

    @Test fun editingTransactionKeepsBalancesConsistent() {
        runBlocking {
            c.settings.setOnboardingDone()
            val acc = c.repository.saveAccount(AccountEntity(name = "Corriente", type = AccountType.CHECKING, initialBalance = 200_000))
            c.repository.save(TransactionDraft(type = TxType.EXPENSE, accountId = acc, amount = 20_000, date = LocalDate.now(), description = "Supermercado"))
        }
        ActivityScenario.launch(MainActivity::class.java)
        waitText("$180.000")
        compose.onAllNodesWithText("Supermercado").onFirst().performClick()
        waitText("Guardar cambios")
        compose.onNodeWithTag("key_⌫").performClick() // 20000 → 2000
        compose.onNodeWithText("Guardar cambios").performClick()
        waitText("$198.000")
    }
}
