package com.personalfinances.domain

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.min

/**
 * Ciclo de facturación de una tarjeta de crédito.
 *
 * Un "periodo" se identifica por el mes en que CIERRA el estado de cuenta.
 * Ej: cierre día 20 → una compra del 15/03 pertenece al periodo 2025-03,
 * una del 22/03 al periodo 2025-04. El vencimiento es la primera fecha con
 * día [dueDay] posterior al cierre.
 */
data class CardCycle(val closingDay: Int, val dueDay: Int) {

    fun closingDate(period: YearMonth): LocalDate = period.atDay(min(closingDay, period.lengthOfMonth()))

    fun dueDate(period: YearMonth): LocalDate {
        val closing = closingDate(period)
        val sameMonth = period.atDay(min(dueDay, period.lengthOfMonth()))
        if (sameMonth.isAfter(closing)) return sameMonth
        val next = period.plusMonths(1)
        return next.atDay(min(dueDay, next.lengthOfMonth()))
    }

    fun periodFor(date: LocalDate): YearMonth {
        val ym = YearMonth.from(date)
        return if (date.isAfter(closingDate(ym))) ym.plusMonths(1) else ym
    }

    /** Periodo abierto (aún no cierra) a la fecha [today]. */
    fun openPeriod(today: LocalDate): YearMonth = periodFor(today)

    /** Último periodo cerrado (el estado de cuenta que hay que pagar). */
    fun lastClosedPeriod(today: LocalDate): YearMonth = openPeriod(today).minusMonths(1)
}
