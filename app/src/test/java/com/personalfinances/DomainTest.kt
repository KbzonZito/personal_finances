package com.personalfinances

import com.personalfinances.domain.CardCalculator
import com.personalfinances.domain.CardCycle
import com.personalfinances.domain.Currency
import com.personalfinances.domain.InstallmentLine
import com.personalfinances.domain.InstallmentPlanner
import com.personalfinances.domain.InterestSpec
import com.personalfinances.domain.Money
import com.personalfinances.domain.YieldAccrual
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class DomainTest {

    @Test fun formatsClp() {
        assertEquals("$12.345", Money.format(12_345, Currency.CLP))
        assertEquals("-$3.000", Money.format(-3_000, Currency.CLP))
        assertEquals("US$12,50", Money.format(1_250, Currency.USD))
        assertEquals("+$500", Money.format(500, Currency.CLP, showSign = true))
    }

    @Test fun parsesAmounts() {
        assertEquals(12_345L, Money.parse("12.345", Currency.CLP))
        assertEquals(12_345L, Money.parse("$12,345", Currency.CLP))
        assertEquals(1_250L, Money.parse("12,50", Currency.USD))
        assertEquals(1_250L, Money.parse("12.5", Currency.USD))
        assertEquals(123_456_78L, Money.parse("123.456,78", Currency.USD))
        assertEquals(1_000_000L, Money.parse("1.000.000", Currency.CLP))
    }

    @Test fun cardCyclePeriods() {
        val c = CardCycle(closingDay = 20, dueDay = 5)
        assertEquals(YearMonth.of(2025, 3), c.periodFor(LocalDate.of(2025, 3, 20)))
        assertEquals(YearMonth.of(2025, 4), c.periodFor(LocalDate.of(2025, 3, 21)))
        assertEquals(LocalDate.of(2025, 4, 5), c.dueDate(YearMonth.of(2025, 3)))
        // cierre 31 en febrero → último día del mes
        val c2 = CardCycle(31, 15)
        assertEquals(LocalDate.of(2025, 2, 28), c2.closingDate(YearMonth.of(2025, 2)))
        assertEquals(LocalDate.of(2025, 3, 15), c2.dueDate(YearMonth.of(2025, 2)))
    }

    @Test fun installmentsWithoutInterest() {
        val plan = InstallmentPlanner.plan(100_000, 3, YearMonth.of(2025, 1))
        assertEquals(listOf(33_334L, 33_333L, 33_333L), plan.map { it.amount })
        assertEquals(100_000L, plan.sumOf { it.principal })
        assertEquals(YearMonth.of(2025, 3), plan.last().period)
    }

    @Test fun installmentsWithBankValue() {
        val plan = InstallmentPlanner.plan(120_000, 6, YearMonth.of(2025, 1), InterestSpec.FixedInstallment(21_500))
        assertEquals(129_000L, plan.sumOf { it.amount })
        assertEquals(120_000L, plan.sumOf { it.principal })
    }

    @Test fun installmentsFrench() {
        val plan = InstallmentPlanner.plan(1_000_000, 12, YearMonth.of(2025, 1), InterestSpec.MonthlyRate(0.02))
        assertEquals(1_000_000L, plan.sumOf { it.principal })
        // cuota francesa 2% 12 meses ≈ 94.560
        assertEquals(94_560L, InstallmentPlanner.frenchInstallment(1_000_000, 12, 0.02))
        assertTrue(plan.dropLast(1).all { it.amount == 94_560L })
    }

    @Test fun cardSummaryRecoversLimitOnPayment() {
        val cycle = CardCycle(20, 5)
        val today = LocalDate.of(2025, 3, 25) // periodo abierto: 2025-04, cerrado: 2025-03
        val lines = InstallmentPlanner.plan(300_000, 3, YearMonth.of(2025, 3)).map { InstallmentLine(it.period, it.amount, it.principal) }
        val before = CardCalculator.summarize(cycle, 1_000_000, lines, credits = 0, today = today)
        assertEquals(100_000L, before.billedDue)
        assertEquals(300_000L, before.used)
        assertEquals(700_000L, before.available)
        assertEquals(LocalDate.of(2025, 4, 5), before.dueDate)

        val after = CardCalculator.summarize(cycle, 1_000_000, lines, credits = 100_000, today = today)
        assertEquals(0L, after.billedDue)
        assertEquals(800_000L, after.available)
        assertEquals(100_000L, after.openPeriodAmount)
    }

    @Test fun cardInterestOnlyCountsWhenBilled() {
        val cycle = CardCycle(20, 5)
        val today = LocalDate.of(2025, 3, 25)
        val lines = InstallmentPlanner.plan(120_000, 6, YearMonth.of(2025, 3), InterestSpec.FixedInstallment(21_500))
            .map { InstallmentLine(it.period, it.amount, it.principal) }
        val s = CardCalculator.summarize(cycle, 500_000, lines, 0, today)
        // 1 cuota facturada con interés (21.500) + 5 cuotas de capital (5 × 20.000)
        assertEquals(121_500L, s.used)
    }

    @Test fun yieldAccrualDaily() {
        val r = YieldAccrual.accrue(1_000_000, 3.65, LocalDate.of(2025, 1, 29), LocalDate.of(2025, 2, 2))
        // 0,01% diario ≈ $100 por día: 2 días en enero, 2 en febrero
        assertEquals(200L, r[YearMonth.of(2025, 1)])
        assertEquals(200L, r[YearMonth.of(2025, 2)])
    }
}
