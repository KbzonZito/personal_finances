package com.personalfinances.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.min

@Serializable
enum class Frequency(val label: String, val every: String) {
    WEEKLY("Semanal", "cada semana"), MONTHLY("Mensual", "cada mes"), YEARLY("Anual", "cada año")
}

object Recurrence {
    /** Fecha del día [dayOfMonth] en [ym], ajustada a meses cortos (31 → 28/29/30). */
    fun dayIn(ym: YearMonth, dayOfMonth: Int): LocalDate = ym.atDay(min(dayOfMonth.coerceAtLeast(1), ym.lengthOfMonth()))

    /** Siguiente ocurrencia estrictamente posterior a [from]. */
    fun next(from: LocalDate, frequency: Frequency, dayOfMonth: Int): LocalDate = when (frequency) {
        Frequency.WEEKLY -> from.plusWeeks(1)
        Frequency.MONTHLY -> dayIn(YearMonth.from(from).plusMonths(1), dayOfMonth)
        Frequency.YEARLY -> dayIn(YearMonth.from(from).plusYears(1), dayOfMonth)
    }

    /** Todas las fechas pendientes desde [nextDate] hasta [today] inclusive (máx. [limit]). */
    fun pending(nextDate: LocalDate, today: LocalDate, frequency: Frequency, dayOfMonth: Int, limit: Int = 36): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        var d = nextDate
        while (!d.isAfter(today) && out.size < limit) {
            out += d
            d = next(d, frequency, dayOfMonth)
        }
        return out
    }
}
