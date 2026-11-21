package com.personalfinances.domain

import java.time.YearMonth
import kotlin.math.pow
import kotlin.math.roundToLong

/** Cómo se define el interés de una compra en cuotas. */
sealed interface InterestSpec {
    /** Sin interés: cuotas = monto / n. */
    data object None : InterestSpec

    /** Valor de cuota informado por el banco (incluye interés). */
    data class FixedInstallment(val installmentAmount: Long) : InterestSpec

    /** Total final a pagar informado por el banco (incluye interés). */
    data class Total(val totalAmount: Long) : InterestSpec

    /** Tasa mensual (ej 0.0189 = 1,89%); cuota calculada con sistema francés. */
    data class MonthlyRate(val rate: Double) : InterestSpec
}

data class PlannedInstallment(
    val number: Int,
    val period: YearMonth,
    /** Monto a pagar en este estado de cuenta (capital + interés). */
    val amount: Long,
    /** Parte de capital (lo que libera cupo al pagarse). */
    val principal: Long,
) {
    val interest: Long get() = amount - principal
}

object InstallmentPlanner {

    fun plan(
        principal: Long,
        count: Int,
        firstPeriod: YearMonth,
        interest: InterestSpec = InterestSpec.None,
    ): List<PlannedInstallment> {
        require(count >= 1) { "count must be >= 1" }
        require(principal >= 0) { "principal must be >= 0" }
        val principals = splitEvenly(principal, count)
        val amounts: List<Long> = when (interest) {
            InterestSpec.None -> principals
            is InterestSpec.FixedInstallment -> List(count) { interest.installmentAmount.coerceAtLeast(0) }
            is InterestSpec.Total -> splitEvenly(interest.totalAmount.coerceAtLeast(principal), count)
            is InterestSpec.MonthlyRate -> return frenchSchedule(principal, count, firstPeriod, interest.rate)
        }
        return List(count) { i ->
            PlannedInstallment(i + 1, firstPeriod.plusMonths(i.toLong()), amounts[i], minOf(principals[i], amounts[i]))
        }
    }

    /** Cuota fija con tasa mensual (sistema francés). */
    fun frenchInstallment(principal: Long, count: Int, rate: Double): Long {
        if (rate <= 0.0) return (principal.toDouble() / count).roundToLong()
        val factor = rate / (1 - (1 + rate).pow(-count))
        return (principal * factor).roundToLong()
    }

    private fun frenchSchedule(principal: Long, count: Int, first: YearMonth, rate: Double): List<PlannedInstallment> {
        if (rate <= 0.0) return plan(principal, count, first, InterestSpec.None)
        val cuota = frenchInstallment(principal, count, rate)
        var balance = principal
        return List(count) { i ->
            val interest = (balance * rate).roundToLong()
            val isLast = i == count - 1
            val capital = if (isLast) balance else (cuota - interest).coerceIn(0, balance)
            balance -= capital
            PlannedInstallment(i + 1, first.plusMonths(i.toLong()), capital + interest, capital)
        }
    }

    /** Divide en partes enteras; el resto se suma a la primera cuota (como hacen los bancos). */
    fun splitEvenly(total: Long, count: Int): List<Long> {
        val base = total / count
        val remainder = total - base * count
        return List(count) { i -> if (i == 0) base + remainder else base }
    }
}
