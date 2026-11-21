package com.personalfinances.domain

import java.time.LocalDate
import java.time.YearMonth

enum class BudgetLevel { OK, WARN, OVER }

data class BudgetStatus(
    val categoryId: Long,
    val limit: Long,
    val spent: Long,
    /** Lo que se puede gastar por día el resto del mes (incluye hoy). */
    val perDayLeft: Long,
    /** Diferencia entre lo gastado y lo "esperable" a esta altura del mes (0.1 = vas 10% adelantado). */
    val pace: Float,
) {
    val fraction: Float get() = if (limit <= 0) 0f else spent.toFloat() / limit
    val remaining: Long get() = limit - spent
    val level: BudgetLevel get() = when {
        spent > limit -> BudgetLevel.OVER
        fraction >= 0.8f -> BudgetLevel.WARN
        else -> BudgetLevel.OK
    }
}

object BudgetCalc {
    fun status(categoryId: Long, limit: Long, spent: Long, today: LocalDate): BudgetStatus {
        val ym = YearMonth.from(today)
        val daysLeft = ym.lengthOfMonth() - today.dayOfMonth + 1
        val expected = today.dayOfMonth.toFloat() / ym.lengthOfMonth()
        val fraction = if (limit <= 0) 0f else spent.toFloat() / limit
        return BudgetStatus(categoryId, limit, spent, ((limit - spent).coerceAtLeast(0)) / daysLeft, fraction - expected)
    }

    /** Alertas al pasar 80% y 100% (una vez por mes y presupuesto). */
    fun alerts(statuses: List<BudgetStatus>, names: Map<Long, String>, currency: Currency, today: LocalDate, sent: Set<String>): List<Reminder> {
        val ym = YearMonth.from(today)
        return statuses.mapNotNull { s ->
            val name = names[s.categoryId] ?: "Total"
            when (s.level) {
                BudgetLevel.OVER -> Reminder("budget100:${s.categoryId}:$ym", s.categoryId, "Te pasaste del presupuesto de $name",
                    "Llevas ${Money.format(s.spent, currency)} de ${Money.format(s.limit, currency)} este mes")
                BudgetLevel.WARN -> Reminder("budget80:${s.categoryId}:$ym", s.categoryId, "Vas en ${(s.fraction * 100).toInt()}% de $name",
                    "Te quedan ${Money.format(s.remaining, currency)} para el resto del mes")
                BudgetLevel.OK -> null
            }
        }.filter { it.key !in sent }
    }
}
