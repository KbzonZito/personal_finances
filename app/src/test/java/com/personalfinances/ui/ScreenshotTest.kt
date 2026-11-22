package com.personalfinances.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.graphics.Canvas
import com.personalfinances.data.FinanceSnapshot
import com.personalfinances.data.db.AccountEntity
import com.personalfinances.data.db.AccountType
import com.personalfinances.data.db.DefaultCategories
import com.personalfinances.data.db.DetectionEntity
import com.personalfinances.data.db.InstallmentEntity
import com.personalfinances.data.db.TransactionEntity
import com.personalfinances.data.db.TxType
import com.personalfinances.data.db.toPeriod
import com.personalfinances.data.prefs.Settings
import com.personalfinances.data.prefs.ThemeMode
import com.personalfinances.domain.InstallmentPlanner
import com.personalfinances.domain.InterestSpec
import com.personalfinances.ui.screens.AddRequest
import com.personalfinances.ui.screens.AddTransactionContent
import com.personalfinances.ui.screens.CardDetailScreen
import com.personalfinances.ui.screens.CardsScreen
import com.personalfinances.ui.screens.HomeScreen
import com.personalfinances.ui.screens.OnboardingScreen
import com.personalfinances.ui.screens.TransactionsScreen
import com.personalfinances.ui.theme.FinanceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Renderiza pantallas con datos de ejemplo a PNG (build/screenshots) para revisión visual. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.now()

    private fun sample(dark: Boolean): FinanceSnapshot {
        val cats = DefaultCategories.all().mapIndexed { i, c -> c.copy(id = i + 1L) }
        val accounts = listOf(
            AccountEntity(1, "Cuenta corriente", AccountType.CHECKING, initialBalance = 1_250_000, color = 0xFF0984E3),
            AccountEntity(2, "Tenpo", AccountType.YIELD, initialBalance = 3_400_000, color = 0xFF00B894, annualRate = 4.8),
            AccountEntity(3, "Visa Signature", AccountType.CREDIT_CARD, creditLimit = 2_000_000, closingDay = 20, dueDay = 5, color = 0xFF6C5CE7, lastDigits = "4321"),
            AccountEntity(4, "Ahorro USD", AccountType.SAVINGS, currencyCode = "USD", initialBalance = 250_000, color = 0xFF2D3436),
            AccountEntity(5, "Hipotecario", AccountType.LOAN, currencyCode = "UF", color = 0xFF8E44AD, annualRate = 4.2, dueDay = 5,
                loanInstallment = 18_50, loanTotalInstallments = 240, loanPaidInstallments = 37),
        )
        val card = accounts[2]
        val txs = mutableListOf<TransactionEntity>()
        val inst = mutableListOf<InstallmentEntity>()
        var id = 1L
        fun add(t: TransactionEntity, plan: Boolean = false, n: Int = 1, interest: InterestSpec = InterestSpec.None) {
            val tx = t.copy(id = id++, installmentCount = n)
            txs += tx
            if (plan) {
                val first = card.cycle.periodFor(LocalDate.ofEpochDay(tx.epochDay))
                InstallmentPlanner.plan(tx.amount, n, first, interest).forEach {
                    inst += InstallmentEntity(0, tx.id, card.id, it.number, it.period.toPeriod(), it.amount, it.principal)
                }
            }
        }
        val d = today.toEpochDay()
        add(TransactionEntity(type = TxType.INCOME, accountId = 1, amount = 1_800_000, epochDay = d - 20, description = "Sueldo", categoryId = 14))
        add(TransactionEntity(type = TxType.EXPENSE, accountId = 3, amount = 899_990, epochDay = d - 40, description = "Notebook", categoryId = 8), plan = true, n = 12, interest = InterestSpec.FixedInstallment(81_200))
        add(TransactionEntity(type = TxType.EXPENSE, accountId = 3, amount = 240_000, epochDay = d - 10, description = "Pasajes Santiago–Lima", categoryId = 11), plan = true, n = 3)
        add(TransactionEntity(type = TxType.EXPENSE, accountId = 1, amount = 54_320, epochDay = d - 2, description = "Jumbo", categoryId = 2))
        add(TransactionEntity(type = TxType.EXPENSE, accountId = 3, amount = 12_990, epochDay = d - 1, description = "Netflix", categoryId = 9), plan = true)
        add(TransactionEntity(type = TxType.EXPENSE, accountId = 1, amount = 4_500, epochDay = d, description = "Starbucks", categoryId = 1))
        add(TransactionEntity(type = TxType.TRANSFER, accountId = 1, toAccountId = 3, amount = 150_000, epochDay = d - 15))
        add(TransactionEntity(type = TxType.INTEREST, accountId = 2, amount = 8_930, epochDay = d - 1, description = "Rentabilidad noviembre", categoryId = 17))
        add(TransactionEntity(type = TxType.EXPENSE, accountId = 1, amount = 32_000, epochDay = d - 3, description = "Uber", categoryId = 3))
        val pending = listOf(DetectionEntity(1, "cl.bank", "Banco", "Compra", "Compra por $18.990 en FARMACIAS AHUMADA", 18_990, "CLP", "FARMACIAS AHUMADA", false, null, System.currentTimeMillis()))
        val recurring = listOf(
            com.personalfinances.data.db.RecurringEntity(1, TxType.EXPENSE, 1, amount = 450_000, description = "Arriendo", categoryId = 4, dayOfMonth = 1, nextEpochDay = d + 2),
            com.personalfinances.data.db.RecurringEntity(2, TxType.INCOME, 1, amount = 1_800_000, description = "Sueldo", categoryId = 14, dayOfMonth = 30, nextEpochDay = d + 6, autoCreate = true),
        )
        val budgets = listOf(com.personalfinances.data.db.BudgetEntity(2, 120_000), com.personalfinances.data.db.BudgetEntity(3, 40_000), com.personalfinances.data.db.BudgetEntity(0, 600_000))
        val goals = listOf(
            com.personalfinances.data.db.GoalEntity(1, "Fondo de emergencia", "☂️", 5_000_000, accountId = 2, color = 0xFF00B894),
            com.personalfinances.data.db.GoalEntity(2, "Viaje a Japón", "✈️", 3_000_000, manualSaved = 850_000, deadlineEpochDay = today.plusMonths(10).withDayOfMonth(1).toEpochDay(), color = 0xFFE84393),
        )
        val settings = Settings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
        return FinanceSnapshot.build(settings, accounts, cats, txs.sortedByDescending { it.epochDay }, inst, pending, recurring, today, budgets, goals)
    }

    private fun shot(name: String, dark: Boolean, content: @Composable (FinanceSnapshot) -> Unit) {
        val snap = sample(dark)
        compose.setContent {
            FinanceTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content(snap) }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bmp)) }
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val noop: () -> Unit = {}

    @Test fun home_dark() = shot("home_dark", true) { HomeScreen(it, noop, {}, noop, noop, noop, {}, {}, noop, noop, noop, noop, PaddingValues()) }
    @Test fun home_light() = shot("home_light", false) { HomeScreen(it, noop, {}, noop, noop, noop, {}, {}, noop, noop, noop, noop, PaddingValues()) }
    @Test fun onboarding_dark() = shot("onboarding_dark", true) { OnboardingScreen(it.copy(accounts = emptyList()), MainViewModel((org.robolectric.RuntimeEnvironment.getApplication() as com.personalfinances.FinanceApp).container), {}) }
    @Test fun projection_dark() = shot("projection_dark", true) { com.personalfinances.ui.screens.ProjectionScreen(it, noop, noop) }
    @Test fun reports_dark() = shot("reports_dark", true) { com.personalfinances.ui.screens.ReportsScreen(it, noop, PaddingValues()) }
    @Test fun goals_dark() = shot("goals_dark", true) { com.personalfinances.ui.screens.GoalsScreen(it, MainViewModel((org.robolectric.RuntimeEnvironment.getApplication() as com.personalfinances.FinanceApp).container), noop) }
    @Test fun loan_dark() = shot("loan_dark", true) { com.personalfinances.ui.screens.AccountDetailScreen(it, 5, noop, noop, {}, {}, {}) }
    @Test fun pay_card_dark() = shot("pay_card_dark", true) { com.personalfinances.ui.screens.PayCardSheet(it, 3) {} }
    @Test fun cards_dark() = shot("cards_dark", true) { CardsScreen(it, {}, noop, PaddingValues()) }
    @Test fun card_detail_dark() = shot("card_detail_dark", true) { CardDetailScreen(it, 3, noop, noop, {}, {}, {}) }
    @Test fun transactions_light() = shot("transactions_light", false) { TransactionsScreen(it, {}, PaddingValues()) }
    @Test fun add_card_expense_dark() = shot("add_card_expense_dark", true) {
        AddTransactionContent(AddRequest(type = TxType.EXPENSE, accountId = 3), it, {}, {}, {}, noop)
    }
}
