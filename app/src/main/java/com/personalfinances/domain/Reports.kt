package com.personalfinances.domain

import java.time.LocalDate
import java.time.YearMonth

/** Movimiento ya convertido a la moneda principal, para reportes. */
data class ReportTx(val date: LocalDate, val amount: Long, val isIncome: Boolean, val categoryId: Long?, val description: String)

data class MonthTotals(val month: YearMonth, val expenses: Long, val income: Long) {
    val saved: Long get() = income - expenses
    val savingsRate: Float get() = if (income <= 0) 0f else saved.toFloat() / income
}

data class MonthReport(
    val totals: MonthTotals,
    val previous: MonthTotals,
    val byCategory: List<Pair<Long?, Long>>,
    val topMerchants: List<Pair<String, Long>>,
    val dailyAverage: Long,
    /** Gasto estimado al cierre del mes (solo para el mes en curso). */
    val projectedExpenses: Long?,
    val trend: List<MonthTotals>,
) {
    /** Variación del gasto vs. el mes anterior (0.1 = +10%); null si no hay base. */
    val expenseChange: Float? get() = if (previous.expenses <= 0) null else (totals.expenses - previous.expenses).toFloat() / previous.expenses
}

object Reports {
    fun totals(txs: List<ReportTx>, ym: YearMonth): MonthTotals {
        val m = txs.filter { YearMonth.from(it.date) == ym }
        return MonthTotals(ym, m.filter { !it.isIncome }.sumOf { it.amount }, m.filter { it.isIncome }.sumOf { it.amount })
    }

    fun month(txs: List<ReportTx>, ym: YearMonth, today: LocalDate, trendMonths: Int = 6): MonthReport {
        val expenses = txs.filter { !it.isIncome && YearMonth.from(it.date) == ym }
        val totals = totals(txs, ym)
        val isCurrent = ym == YearMonth.from(today)
        val days = if (isCurrent) today.dayOfMonth else ym.lengthOfMonth()
        val daily = if (days == 0) 0 else totals.expenses / days
        return MonthReport(
            totals = totals,
            previous = totals(txs, ym.minusMonths(1)),
            byCategory = expenses.groupBy { it.categoryId }.map { (k, v) -> k to v.sumOf { it.amount } }.sortedByDescending { it.second },
            topMerchants = expenses.filter { it.description.isNotBlank() }
                .groupBy { it.description.trim().lowercase() }
                .map { (_, v) -> v.first().description.trim() to v.sumOf { it.amount } }
                .sortedByDescending { it.second }.take(5),
            dailyAverage = daily,
            projectedExpenses = if (isCurrent) daily * ym.lengthOfMonth() else null,
            trend = (trendMonths - 1 downTo 0).map { totals(txs, ym.minusMonths(it.toLong())) },
        )
    }
}
