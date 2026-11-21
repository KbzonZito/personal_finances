package com.personalfinances.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class GoalProgress(
    val target: Long,
    val current: Long,
    /** Ahorro mensual necesario para llegar a la fecha límite (null si no hay fecha o ya se cumplió). */
    val perMonthNeeded: Long?,
    val monthsLeft: Long?,
    /** Mes estimado en que se cumple con [perMonthNeeded]/aporte dado y la rentabilidad (si hay). */
    val eta: YearMonth?,
) {
    val fraction: Float get() = if (target <= 0) 0f else (current.toFloat() / target).coerceIn(0f, 1f)
    val remaining: Long get() = (target - current).coerceAtLeast(0)
    val done: Boolean get() = current >= target
}

object GoalCalc {
    fun progress(
        target: Long,
        current: Long,
        today: LocalDate,
        deadline: LocalDate?,
        annualRatePercent: Double = 0.0,
        monthlyContribution: Long? = null,
    ): GoalProgress {
        val remaining = (target - current).coerceAtLeast(0)
        if (remaining == 0L) return GoalProgress(target, current, null, null, null)
        val months = deadline?.let { ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(it)).coerceAtLeast(1) }
        val needed = months?.let { neededWithYield(current, target, it.toInt(), annualRatePercent) }
        val contribution = monthlyContribution ?: needed
        return GoalProgress(target, current, needed, months, contribution?.let { eta(current, target, it, annualRatePercent, today) })
    }

    /** Aporte mensual fijo necesario en [months] meses, considerando interés compuesto mensual. */
    fun neededWithYield(current: Long, target: Long, months: Int, annualRatePercent: Double): Long {
        val r = annualRatePercent / 100 / 12
        if (r <= 0) return Math.ceil((target - current).toDouble() / months).toLong().coerceAtLeast(0)
        val growth = Math.pow(1 + r, months.toDouble())
        val futureOfCurrent = current * growth
        val annuity = (growth - 1) / r
        return Math.ceil(((target - futureOfCurrent) / annuity).coerceAtLeast(0.0)).toLong()
    }

    /** Mes en que se alcanza la meta aportando [monthly] cada mes (máx. 50 años). */
    fun eta(current: Long, target: Long, monthly: Long, annualRatePercent: Double, today: LocalDate): YearMonth? {
        val r = annualRatePercent / 100 / 12
        var b = current.toDouble()
        var ym = YearMonth.from(today)
        repeat(600) {
            if (b >= target) return ym
            b = b * (1 + r) + monthly
            ym = ym.plusMonths(1)
        }
        return if (b >= target) ym else null
    }
}
