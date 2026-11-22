package com.personalfinances

import com.personalfinances.domain.Loans
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class LoansTest {
    @Test fun principalMatchesFrenchAmortization() {
        // Cuota de un crédito de 1.000.000 al 12% anual en 12 meses ≈ 88.849
        assertEquals(1_000_000.0, Loans.principal(88_849, 12, 12.0).toDouble(), 10.0)
        assertEquals(0L, Loans.principal(88_849, 0, 12.0))
        assertEquals(500_000L, Loans.principal(50_000, 10, 0.0))
    }

    @Test fun statusDatesAndTotals() {
        val s = Loans.status(installment = 1_000, total = 240, paid = 238, annualRatePercent = 4.0, paymentDay = 5, today = LocalDate.of(2025, 3, 10))
        assertEquals(2, s.remaining)
        assertEquals(LocalDate.of(2025, 4, 5), s.nextPayment)
        assertEquals(LocalDate.of(2025, 5, 5), s.lastPayment)
        assertEquals(2_000L, s.remainingToPay)
        val done = Loans.status(1_000, 12, 12, 4.0, 5, LocalDate.of(2025, 3, 10))
        assertNull(done.nextPayment); assertEquals(0L, done.principal)
    }
}
