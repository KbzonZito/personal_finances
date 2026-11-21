package com.personalfinances.domain

import java.time.LocalDate
import java.time.YearMonth

enum class EventKind { INCOME, EXPENSE, CARD_PAYMENT, YIELD }

data class ProjectedEvent(val date: LocalDate, val amount: Long, val label: String, val kind: EventKind)

data class MonthProjection(
    val month: YearMonth,
    val income: Long,
    val expenses: Long,
    val cardPayments: Long,
    val endBalance: Long,
)

data class ProjectionResult(
    val start: Long,
    val months: List<MonthProjection>,
    /** Saldo al final de cada día, desde hoy. */
    val daily: List<Pair<LocalDate, Long>>,
    val lowest: Pair<LocalDate, Long>,
    val events: List<ProjectedEvent>,
) {
    val endOfThisMonth: Long get() = months.firstOrNull()?.endBalance ?: start
}

/** Simula el saldo disponible día a día aplicando eventos futuros (recurrentes, pagos de tarjeta…). */
object Projection {
    fun compute(startBalance: Long, events: List<ProjectedEvent>, today: LocalDate, months: Int): ProjectionResult {
        val end = YearMonth.from(today).plusMonths((months - 1).toLong()).atEndOfMonth()
        val inRange = events.filter { !it.date.isBefore(today) && !it.date.isAfter(end) }.sortedBy { it.date }
        val byDay = inRange.groupBy { it.date }
        val daily = ArrayList<Pair<LocalDate, Long>>()
        var b = startBalance
        var lowest = today to startBalance
        var d = today
        while (!d.isAfter(end)) {
            byDay[d]?.forEach { b += it.amount }
            daily += d to b
            if (b < lowest.second) lowest = d to b
            d = d.plusDays(1)
        }
        val monthsOut = (0 until months).map { i ->
            val ym = YearMonth.from(today).plusMonths(i.toLong())
            val ev = inRange.filter { YearMonth.from(it.date) == ym }
            MonthProjection(
                month = ym,
                income = ev.filter { it.amount > 0 }.sumOf { it.amount },
                expenses = -ev.filter { it.amount < 0 && it.kind == EventKind.EXPENSE }.sumOf { it.amount },
                cardPayments = -ev.filter { it.kind == EventKind.CARD_PAYMENT }.sumOf { it.amount },
                endBalance = daily.last { YearMonth.from(it.first) == ym }.second,
            )
        }
        return ProjectionResult(startBalance, monthsOut, daily, lowest, inRange)
    }
}
