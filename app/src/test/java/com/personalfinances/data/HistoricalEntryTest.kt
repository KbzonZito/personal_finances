package com.personalfinances.data

import androidx.room.Room
import com.personalfinances.data.db.AccountEntity
import com.personalfinances.data.db.AccountType
import com.personalfinances.data.db.AppDatabase
import com.personalfinances.data.db.TxType
import com.personalfinances.data.db.toPeriod
import com.personalfinances.data.prefs.SettingsStore
import com.personalfinances.domain.CardCycle
import com.personalfinances.domain.Frequency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Simula pasar a mano datos pasados a la app: nada de eso debe descuadrar los saldos de hoy. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class HistoricalEntryTest {
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

    private suspend fun result() = Balances.compute(db.accounts().getAll(), db.transactions().getAll(), db.installments().getAll(), today)
    private suspend fun balance(id: Long) = result().balances[id]

    @Test fun pastMovementsAreHistoryNewOnesCount() = runBlocking {
        val acc = repo.saveAccount(AccountEntity(name = "Corriente", type = AccountType.CHECKING, initialBalance = 1_000_000))
        repo.save(TransactionDraft(type = TxType.EXPENSE, accountId = acc, amount = 50_000, date = today.minusDays(30), description = "Súper"))
        repo.save(TransactionDraft(type = TxType.INCOME, accountId = acc, amount = 900_000, date = today.minusDays(20), description = "Sueldo"))
        assertEquals(1_000_000L, balance(acc))

        // Movimiento nuevo de hoy sí cuenta.
        val coffee = repo.save(TransactionDraft(type = TxType.EXPENSE, accountId = acc, amount = 3_000, date = today))
        assertEquals(997_000L, balance(acc))

        // Editar uno histórico no lo vuelve "nuevo"; editar el de hoy mantiene que cuente.
        val past = db.transactions().getAll().first { it.description == "Súper" }
        repo.save(TransactionDraft(id = past.id, type = TxType.EXPENSE, accountId = acc, amount = 60_000, date = today.minusDays(30), description = "Súper"))
        repo.save(TransactionDraft(id = coffee, type = TxType.EXPENSE, accountId = acc, amount = 4_000, date = today))
        assertEquals(996_000L, balance(acc))
    }

    @Test fun reanchorWhenUserCorrectsCurrentBalance() = runBlocking {
        val acc = repo.saveAccount(AccountEntity(name = "Corriente", type = AccountType.CHECKING, initialBalance = 500_000))
        repo.save(TransactionDraft(type = TxType.EXPENSE, accountId = acc, amount = 20_000, date = today))
        // El usuario corrige el saldo actual en el editor (re-ancla).
        repo.saveAccount(db.accounts().get(acc)!!.copy(initialBalance = 700_000, balanceAnchorAt = System.currentTimeMillis() + 1))
        assertEquals(700_000L, balance(acc))
        repo.save(TransactionDraft(type = TxType.EXPENSE, accountId = acc, amount = 1_000, date = today.minusDays(3)))
        assertEquals(700_000L, balance(acc))
    }

    @Test fun oldAccountsKeepPreviousBehaviour() = runBlocking {
        val acc = repo.saveAccount(AccountEntity(name = "Antigua", type = AccountType.CHECKING, initialBalance = 100_000, balanceAnchorAt = 0))
        db.accounts().upsert(db.accounts().get(acc)!!.copy(balanceAnchorAt = 0)) // simula cuenta de v0.3
        repo.save(TransactionDraft(type = TxType.EXPENSE, accountId = acc, amount = 10_000, date = today.minusDays(40)))
        assertEquals(90_000L, balance(acc))
    }

    @Test fun pastTransferOnlyAffectsAccountsWhereItIsNew() = runBlocking {
        val old = repo.saveAccount(AccountEntity(name = "Antigua", type = AccountType.CHECKING, initialBalance = 100_000))
        db.accounts().upsert(db.accounts().get(old)!!.copy(balanceAnchorAt = 0))
        val fresh = repo.saveAccount(AccountEntity(name = "Nueva", type = AccountType.SAVINGS, initialBalance = 50_000))
        repo.save(TransactionDraft(type = TxType.TRANSFER, accountId = old, toAccountId = fresh, amount = 30_000, date = today.minusDays(10)))
        assertEquals(70_000L, balance(old))
        assertEquals(50_000L, balance(fresh))
    }

    @Test fun oldCardPurchasesOnlyChargeUnpaidStatements() = runBlocking {
        val cycle = CardCycle(20, 5)
        val lastClosed = cycle.lastClosedPeriod(today)
        val card = repo.saveAccount(AccountEntity(
            name = "Visa", type = AccountType.CREDIT_CARD, creditLimit = 2_000_000, closingDay = 20, dueDay = 5,
            paidThroughPeriod = lastClosed.minusMonths(1).toPeriod(),
        ))
        // Compra de hace ~4 estados de cuenta en 6 cuotas de 10.000, con su fecha real.
        val purchaseDate = cycle.closingDate(lastClosed.minusMonths(3)).minusDays(5)
        repo.save(TransactionDraft(type = TxType.EXPENSE, accountId = card, amount = 60_000, date = purchaseDate, installments = 6))
        // Pago histórico (ya cubierto por "estados pagados"): no debe contar doble.
        val checking = repo.saveAccount(AccountEntity(name = "Corriente", type = AccountType.CHECKING, initialBalance = 1_000_000))
        repo.save(TransactionDraft(type = TxType.TRANSFER, accountId = checking, toAccountId = card, amount = 10_000, date = today.minusDays(45)))

        val s = result().cards[card]!!
        assertEquals(10_000L, s.billedDue)          // solo la cuota del último estado (no pagado)
        assertEquals(30_000L, s.used)               // cuota pendiente + 2 futuras
        assertEquals(10_000L, s.openPeriodAmount)
        assertEquals(1_000_000L, balance(checking)) // el pago histórico tampoco mueve la corriente
    }

    @Test fun pastRecurringDoesNotCreateBacklog() = runBlocking {
        val acc = repo.saveAccount(AccountEntity(name = "Corriente", type = AccountType.CHECKING))
        repo.save(TransactionDraft(type = TxType.EXPENSE, accountId = acc, amount = 400_000, date = today.minusMonths(6), description = "Arriendo", repeat = Frequency.MONTHLY))
        val r = db.recurring().getAll().single()
        assert(LocalDate.ofEpochDay(r.nextEpochDay).isAfter(today))
        assertEquals(0, repo.processRecurring(today).size)
    }

    @Test fun yieldIgnoresHistoricalMovements() = runBlocking {
        val acc = repo.saveAccount(AccountEntity(name = "Tenpo", type = AccountType.YIELD, initialBalance = 1_000_000, annualRate = 3.65))
        db.accounts().upsert(db.accounts().get(acc)!!.copy(lastAccrualEpochDay = today.minusDays(3).toEpochDay(), balanceAnchorAt = System.currentTimeMillis() - 10))
        repo.save(TransactionDraft(type = TxType.INCOME, accountId = acc, amount = 5_000_000, date = today.minusDays(60))) // histórico
        repo.accrueYield(today)
        val interest = db.transactions().getAll().filter { it.type == TxType.INTEREST }.sumOf { it.amount }
        assertEquals(200.0, interest.toDouble(), 2.0) // ~100/día × 2 días sobre 1M, no sobre 6M
    }
}
