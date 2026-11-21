package com.personalfinances

import com.personalfinances.domain.EventKind
import com.personalfinances.domain.ProjectedEvent
import com.personalfinances.domain.Projection
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ProjectionTest {
    @Test fun dailyBalanceMonthlyTotalsAndLowestPoint() {
        val today = LocalDate.of(2025, 3, 10)
        val events = listOf(
            ProjectedEvent(LocalDate.of(2025, 3, 15), -400_000, "Arriendo", EventKind.EXPENSE),
            ProjectedEvent(LocalDate.of(2025, 4, 5), -300_000, "Pago Visa", EventKind.CARD_PAYMENT),
            ProjectedEvent(LocalDate.of(2025, 3, 30), 1_000_000, "Sueldo", EventKind.INCOME),
            ProjectedEvent(LocalDate.of(2025, 3, 1), -999, "Pasado (se ignora)", EventKind.EXPENSE),
            ProjectedEvent(LocalDate.of(2025, 6, 1), -1, "Fuera de rango", EventKind.EXPENSE),
        )
        val r = Projection.compute(500_000, events, today, months = 2)
        assertEquals(1_100_000L, r.months[0].endBalance)
        assertEquals(1_000_000L, r.months[0].income)
        assertEquals(400_000L, r.months[0].expenses)
        assertEquals(300_000L, r.months[1].cardPayments)
        assertEquals(800_000L, r.months[1].endBalance)
        assertEquals(LocalDate.of(2025, 3, 15) to 100_000L, r.lowest)
        assertEquals(YearMonth.of(2025, 4), YearMonth.from(r.daily.last().first))
        assertEquals(3, r.events.size)
    }
}
