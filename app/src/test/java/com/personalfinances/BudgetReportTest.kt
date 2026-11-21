package com.personalfinances

import com.personalfinances.domain.BudgetCalc
import com.personalfinances.domain.BudgetLevel
import com.personalfinances.domain.Currency
import com.personalfinances.domain.ReportTx
import com.personalfinances.domain.Reports
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BudgetReportTest {
    @Test fun budgetLevelsPaceAndPerDay() {
        val today = LocalDate.of(2025, 4, 10) // 30 días, quedan 21 incluyendo hoy
        val s = BudgetCalc.status(1, 300_000, 210_000, today)
        assertEquals(BudgetLevel.OK, s.level)
        assertEquals(90_000L / 21, s.perDayLeft)
        assertEquals(0.7f - 10f / 30, s.pace, 0.001f)
        assertEquals(BudgetLevel.WARN, BudgetCalc.status(1, 100_000, 85_000, today).level)
        assertEquals(BudgetLevel.OVER, BudgetCalc.status(1, 100_000, 100_001, today).level)
    }

    @Test fun budgetAlertsOncePerMonth() {
        val today = LocalDate.of(2025, 4, 10)
        val st = listOf(BudgetCalc.status(1, 100_000, 90_000, today), BudgetCalc.status(2, 100_000, 10_000, today))
        val a = BudgetCalc.alerts(st, mapOf(1L to "Comida"), Currency.CLP, today, emptySet())
        assertEquals(1, a.size)
        assertEquals("Vas en 90% de Comida", a[0].title)
        assertTrue(BudgetCalc.alerts(st, mapOf(1L to "Comida"), Currency.CLP, today, setOf(a[0].key)).isEmpty())
    }

    @Test fun monthReport() {
        val d = { m: Int, day: Int -> LocalDate.of(2025, m, day) }
        val txs = listOf(
            ReportTx(d(3, 5), 100_000, false, 1, "Jumbo"),
            ReportTx(d(4, 2), 60_000, false, 1, "Jumbo"),
            ReportTx(d(4, 3), 30_000, false, 2, "jumbo "),
            ReportTx(d(4, 4), 20_000, false, 3, "Uber"),
            ReportTx(d(4, 1), 1_000_000, true, 9, "Sueldo"),
        )
        val r = Reports.month(txs, YearMonth.of(2025, 4), d(4, 10))
        assertEquals(110_000L, r.totals.expenses)
        assertEquals(890_000L, r.totals.saved)
        assertEquals(0.1f, r.expenseChange!!, 0.001f)
        assertEquals(1L to 60_000L, r.byCategory.first())
        assertEquals("Jumbo" to 90_000L, r.topMerchants.first())
        assertEquals(11_000L, r.dailyAverage)
        assertEquals(330_000L, r.projectedExpenses)
        assertEquals(6, r.trend.size)
        assertNull(Reports.month(txs, YearMonth.of(2025, 3), d(4, 10)).projectedExpenses)
    }
}
