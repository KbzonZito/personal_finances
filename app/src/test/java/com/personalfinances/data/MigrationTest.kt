package com.personalfinances.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.personalfinances.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MigrationTest {
    private val name = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun migrate1To2_keepsDataAndFillsNewColumns() {
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO accounts (id, name, type, currencyCode, initialBalance, color, creditLimit, closingDay, dueDay, annualRate, lastAccrualEpochDay, lastDigits, archived, includeInTotal, sortOrder) VALUES (1, 'Visa', 'CREDIT_CARD', 'CLP', 0, 0, 1000000, 20, 5, 0.0, 0, NULL, 0, 1, 0)")
            execSQL("INSERT INTO categories (id, name, emoji, color, isIncome, sortOrder, archived) VALUES (1, 'Rentabilidad', '📈', 0, 1, 3, 0), (2, 'Comida', '🍔', 0, 0, 0, 0)")
            execSQL("INSERT INTO transactions (id, type, accountId, toAccountId, amount, toAmount, epochDay, createdAt, description, categoryId, installmentCount, installmentsPaid, interestMode, interestValue, deferMonths, source) VALUES (10, 'EXPENSE', 1, NULL, 300000, NULL, 20000, 0, 'TV', 2, 3, 0, 'NONE', 0.0, 0, 'MANUAL')")
            execSQL("INSERT INTO installments (transactionId, accountId, number, period, amount, principal) VALUES (10, 1, 1, 202503, 100000, 100000), (10, 1, 2, 202504, 100000, 100000), (10, 1, 3, 202505, 100000, 100000)")
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 2, true)
        db.query("SELECT systemKey FROM categories WHERE id = 1").use { it.moveToFirst(); assertEquals("yield", it.getString(0)) }
        db.query("SELECT systemKey FROM categories WHERE id = 2").use { it.moveToFirst(); assertEquals(true, it.isNull(0)) }
        db.query("SELECT firstPeriod, amount FROM transactions WHERE id = 10").use {
            it.moveToFirst(); assertEquals(202503, it.getInt(0)); assertEquals(300000L, it.getLong(1))
        }
        db.query("SELECT COUNT(*) FROM installments").use { it.moveToFirst(); assertEquals(3, it.getInt(0)) }
    }

    @Test fun migrate2To3_addsRecurringTable() {
        helper.createDatabase("m23.db", 2).apply {
            execSQL("INSERT INTO detections (id, packageName, appLabel, title, text, amount, currencyCode, merchant, isIncome, lastDigits, postedAt, status) VALUES (1, 'cl.bank', 'Banco', '', '', 5000, 'CLP', NULL, 0, NULL, 0, 'PENDING')")
            close()
        }
        val db = helper.runMigrationsAndValidate("m23.db", 3, true)
        db.query("SELECT recurringId FROM detections WHERE id = 1").use { it.moveToFirst(); assertEquals(true, it.isNull(0)) }
        db.query("SELECT COUNT(*) FROM recurring").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
    }
}
