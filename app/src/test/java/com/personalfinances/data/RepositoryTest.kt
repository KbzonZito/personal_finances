package com.personalfinances.data

import androidx.room.Room
import com.personalfinances.data.db.AccountEntity
import com.personalfinances.data.db.AccountType
import com.personalfinances.data.db.AppDatabase
import com.personalfinances.data.db.DetectionEntity
import com.personalfinances.data.db.SystemCategory
import com.personalfinances.data.db.TxType
import com.personalfinances.data.db.toPeriod
import com.personalfinances.data.db.toYearMonth
import com.personalfinances.domain.Frequency
import com.personalfinances.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: FinanceRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val today = LocalDate.now()

    @Before fun setUp() = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = FinanceRepository(db, SettingsStore(ctx), scope) {}
        repo.ensureSeeded()
    }

    @After fun tearDown() { scope.cancel(); db.close() }

    private suspend fun balances(): Balances.Result =
        Balances.compute(db.accounts().getAll(), db.transactions().getAll(), db.installments().getAll(), today)

    @Test fun deletingAccountKeepsOtherBalances() = runBlocking {
        val checking = repo.saveAccount(AccountEntity(name = "Corriente", type = AccountType.CHECKING, initialBalance = 1_000_000))
        val savings = repo.saveAccount(AccountEntity(name = "Ahorro", type = AccountType.SAVINGS, initialBalance = 0))
        val card = repo.saveAccount(AccountEntity(name = "Visa", type = AccountType.CREDIT_CARD, creditLimit = 500_000))
        repo.save(TransactionDraft(type = TxType.TRANSFER, accountId = checking, toAccountId = savings, amount = 200_000, date = today))
        repo.save(TransactionDraft(type = TxType.TRANSFER, accountId = savings, toAccountId = checking, amount = 50_000, date = today))
        repo.save(TransactionDraft(type = TxType.TRANSFER, accountId = checking, toAccountId = card, amount = 30_000, date = today))
        assertEquals(820_000L, balances().balances[checking])

        repo.deleteAccount(db.accounts().get(savings)!!)
        repo.deleteAccount(db.accounts().get(card)!!)
        assertEquals(820_000L, balances().balances[checking])
        assertEquals(3, db.transactions().getAll().size)
        assertEquals(setOf(checking), db.transactions().getAll().map { it.accountId }.toSet())
    }

    @Test fun deletingCardWithCashAdvanceKeepsDestination() = runBlocking {
        val checking = repo.saveAccount(AccountEntity(name = "Corriente", type = AccountType.CHECKING))
        val card = repo.saveAccount(AccountEntity(name = "Visa", type = AccountType.CREDIT_CARD, creditLimit = 500_000))
        repo.save(TransactionDraft(type = TxType.TRANSFER, accountId = card, toAccountId = checking, amount = 100_000, date = today))
        repo.deleteAccount(db.accounts().get(card)!!)
        assertEquals(100_000L, balances().balances[checking])
        assertEquals(0, db.installments().getAll().size)
    }

    @Test fun editingOldInstallmentPurchaseKeepsPeriods() = runBlocking {
        val card = repo.saveAccount(AccountEntity(name = "Visa", type = AccountType.CREDIT_CARD, creditLimit = 1_000_000))
        val id = repo.save(TransactionDraft(type = TxType.EXPENSE, accountId = card, amount = 120_000, date = today, installments = 12, installmentsPaid = 4))
        // Simula que la compra se registró hace 2 meses: el ancla quedó 2 meses antes.
        val tx = db.transactions().get(id)!!
        val anchored = tx.firstPeriod.toYearMonth().minusMonths(2)
        db.transactions().upsert(tx.copy(firstPeriod = anchored.toPeriod()))

        repo.save(TransactionDraft(id = id, type = TxType.EXPENSE, accountId = card, amount = 120_000, date = today, installments = 12, installmentsPaid = 4, description = "editada"))
        val lines = db.installments().getAll().filter { it.transactionId == id }.sortedBy { it.number }
        assertEquals(8, lines.size)
        assertEquals(5, lines.first().number)
        assertEquals(anchored.plusMonths(4).toPeriod(), lines.first().period)
    }

    @Test fun samePurchaseFromTwoAppsIsDetectedOnce() = runBlocking {
        val now = System.currentTimeMillis()
        fun det(pkg: String, at: Long) = DetectionEntity(packageName = pkg, appLabel = pkg, title = "", text = "", amount = 12_990, currencyCode = "CLP", merchant = "LIDER", isIncome = false, lastDigits = null, postedAt = at)
        assertNotNull(repo.addDetection(det("cl.bank", now)))
        assertNull(repo.addDetection(det("com.google.android.apps.walletnfcrel", now + 40_000)))
        assertNotNull(repo.addDetection(det("cl.bank", now + 10 * 60_000)))
    }

    @Test fun yieldUsesDailyBalanceAndSystemCategory() = runBlocking {
        val start = today.minusDays(10)
        val acc = repo.saveAccount(AccountEntity(name = "Tenpo", type = AccountType.YIELD, initialBalance = 1_000_000, annualRate = 3.65, lastAccrualEpochDay = start.toEpochDay()))
        // Depósito de 1.000.000 hace 5 días: los días anteriores no deben ganar sobre ese monto.
        repo.save(TransactionDraft(type = TxType.INCOME, accountId = acc, amount = 1_000_000, date = today.minusDays(5)))
        // Renombrar la categoría no debe romper la asignación.
        val cat = db.categories().bySystemKey(SystemCategory.YIELD)!!
        db.categories().upsert(cat.copy(name = "Intereses"))

        repo.accrueYield(today)
        val interest = db.transactions().getAll().filter { it.type == TxType.INTEREST }
        val total = interest.sumOf { it.amount }
        // ~100/día por 1M: 5 días con 1M (días -9..-5) + 4 días con 2M (días -4..-1) ≈ 500 + 800
        assertEquals(1_300.0, total.toDouble(), 3.0)
        assertEquals(setOf(cat.id), interest.map { it.categoryId }.toSet())
        assertEquals(today.minusDays(1).toEpochDay(), db.accounts().get(acc)!!.lastAccrualEpochDay)
    }

    @Test fun repeatCreatesRecurringAndProcessingHandlesAutoAndConfirm() = runBlocking {
        val acc = repo.saveAccount(AccountEntity(name = "Corriente", type = AccountType.CHECKING))
        val start = today.minusMonths(2)
        repo.save(TransactionDraft(type = TxType.EXPENSE, accountId = acc, amount = 450_000, date = start, description = "Arriendo", repeat = Frequency.MONTHLY))
        repo.save(TransactionDraft(type = TxType.INCOME, accountId = acc, amount = 1_500_000, date = start, description = "Sueldo", repeat = Frequency.MONTHLY))
        val all = db.recurring().getAll()
        assertEquals(2, all.size)
        // El sueldo se registra solo; el arriendo pide confirmación.
        db.recurring().upsert(all.first { it.description == "Sueldo" }.copy(autoCreate = true))

        val created = repo.processRecurring(today)
        assertEquals(2, created.size) // arriendo: 2 meses atrasados → 2 pendientes
        assertEquals(setOf("Arriendo"), created.map { it.merchant }.toSet())
        assertEquals(3, db.transactions().getAll().count { it.description == "Sueldo" }) // original + 2 automáticos
        db.recurring().getAll().forEach { assertEquals(true, it.nextEpochDay > today.toEpochDay()) }
        // Volver a procesar no duplica.
        assertEquals(0, repo.processRecurring(today).size)
    }
}
