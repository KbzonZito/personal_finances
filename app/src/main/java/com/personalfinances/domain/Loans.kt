package com.personalfinances.domain

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

data class LoanStatus(
    val installment: Long,
    val total: Int,
    val paid: Int,
    /** Capital pendiente (lo que se debe hoy). */
    val principal: Long,
    val nextPayment: LocalDate?,
    val lastPayment: LocalDate?,
) {
    val remaining: Int get() = (total - paid).coerceAtLeast(0)
    /** Total que falta pagar sumando cuotas (capital + intereses). */
    val remainingToPay: Long get() = installment * remaining
    val remainingInterest: Long get() = (remainingToPay - principal).coerceAtLeast(0)
    val fraction: Float get() = if (total <= 0) 0f else paid.toFloat() / total
}

object Loans {
    /** Capital pendiente con sistema francés: valor presente de las cuotas restantes. */
    fun principal(installment: Long, remaining: Int, annualRatePercent: Double): Long {
        if (remaining <= 0) return 0
        val r = annualRatePercent / 100 / 12
        if (r <= 0) return installment * remaining
        return (installment * (1 - (1 + r).pow(-remaining)) / r).roundToLong()
    }

    fun status(installment: Long, total: Int, paid: Int, annualRatePercent: Double, paymentDay: Int, today: LocalDate): LoanStatus {
        val p = paid.coerceIn(0, total)
        val remaining = total - p
        val ym = YearMonth.from(today)
        val thisMonth = ym.atDay(min(paymentDay, ym.lengthOfMonth()))
        val next = if (remaining == 0) null else if (!thisMonth.isBefore(today)) thisMonth
            else ym.plusMonths(1).let { it.atDay(min(paymentDay, it.lengthOfMonth())) }
        val last = next?.let { n -> YearMonth.from(n).plusMonths((remaining - 1).toLong()).let { it.atDay(min(paymentDay, it.lengthOfMonth())) } }
        return LoanStatus(installment, total, p, principal(installment, remaining, annualRatePercent), next, last)
    }
}
