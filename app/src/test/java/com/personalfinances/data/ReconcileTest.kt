package com.personalfinances.data

import androidx.room.Room
import com.personalfinances.data.db.AccountEntity
import com.personalfinances.data.db.AccountType
import com.personalfinances.data.db.AppDatabase
import com.personalfinances.data.db.TxType
import com.personalfinances.data.prefs.SettingsStore
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ReconcileTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: FinanceRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val today = LocalDate.now()

    @Before fun setUp() = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = FinanceRepository(db, SettingsStore(ctx), scope) {}
    }

    @After fun tearDown() { scope.cancel(); db.close() }

    private suspend fun card(id: Long) = Balances.compute(db.accounts().getAll(), db.transactions().getAll(), db.installments().getAll(), today).cards[id]!!

    @Test fun newCardMatchesBankFigures() = runBlocking {
        val id = repo.saveAccount(AccountEntity(name = "Visa", type = AccountType.CREDIT_CARD, creditLimit = 2_000_000))
        repo.reconcileCard(id, available = 1_350_000, billed = 220_000, nextStatement = 130_000, today = today)
        val c = card(id)
        assertEquals(1_350_000L, c.available)
        assertEquals(220_000L, c.billedDue)
        assertEquals(130_000L, c.openPeriodAmount)
        assertEquals(300_000L, c.projection[1].second) // 650.000 usados − 220.000 − 130.000 = cuotas futuras
    }

    @Test fun withoutNextStatementDifferenceGoesToCurrentPeriod() = runBlocking {
        val id = repo.saveAccount(AccountEntity(name = "Visa", type = AccountType.CREDIT_CARD, creditLimit = 1_000_000))
        repo.reconcileCard(id, available = 700_000, billed = 100_000, nextStatement = null, today = today)
        val c = card(id)
        assertEquals(700_000L, c.available)
        assertEquals(100_000L, c.billedDue)
        assertEquals(200_000L, c.openPeriodAmount)
    }

    @Test fun reconcilingAgainOnlyAdjustsTheDifferenceAndPaymentsStillWork() = runBlocking {
        val id = repo.saveAccount(AccountEntity(name = "Visa", type = AccountType.CREDIT_CARD, creditLimit = 1_000_000))
        val bank = repo.saveAccount(AccountEntity(name = "Corriente", type = AccountType.CHECKING, initialBalance = 500_000))
        repo.reconcileCard(id, 800_000, 150_000, 50_000, today)
        // El banco ahora muestra un poco menos de cupo (una compra que no registraste en el periodo actual).
        repo.reconcileCard(id, 790_000, 150_000, 60_000, today)
        assertEquals(790_000L, card(id).available)
        assertEquals(60_000L, card(id).openPeriodAmount)
        // Pagar el facturado devuelve el cupo y descuenta del banco.
        repo.save(TransactionDraft(type = TxType.TRANSFER, accountId = bank, toAccountId = id, amount = 150_000, date = today))
        assertEquals(0L, card(id).billedDue)
        assertEquals(940_000L, card(id).available)
        val bankBal = Balances.compute(db.accounts().getAll(), db.transactions().getAll(), db.installments().getAll(), today).balances[bank]
        assertEquals(350_000L, bankBal)
    }
}
