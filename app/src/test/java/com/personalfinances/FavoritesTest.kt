package com.personalfinances

import com.personalfinances.data.Favorites
import com.personalfinances.data.db.TransactionEntity
import com.personalfinances.data.db.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FavoritesTest {
    private val today = LocalDate.of(2025, 4, 30)
    private fun tx(desc: String, amount: Long, daysAgo: Long, acc: Long = 1, cat: Long? = 1) =
        TransactionEntity(type = TxType.EXPENSE, accountId = acc, amount = amount, epochDay = today.minusDays(daysAgo).toEpochDay(), description = desc, categoryId = cat)

    @Test fun frequentWithFixedAndVariableAmounts() {
        val txs = listOf(
            tx("Café", 3_500, 1), tx("café ", 3_500, 3), tx("Café", 3_500, 5), tx("Café", 4_000, 7),
            tx("Jumbo", 45_000, 2, cat = 2), tx("Jumbo", 62_000, 9, cat = 2), tx("Jumbo", 38_000, 20, cat = 2),
            tx("Uber", 5_000, 4), tx("Uber", 5_000, 6), // solo 2 veces
            tx("Café", 3_500, 120), tx("Café", 3_500, 130), // fuera de 90 días
        )
        val f = Favorites.compute(txs, today)
        assertEquals(listOf("Café", "Jumbo"), f.map { it.description })
        assertEquals(3_500L, f[0].amount)
        assertNull(f[1].amount)
    }
}
