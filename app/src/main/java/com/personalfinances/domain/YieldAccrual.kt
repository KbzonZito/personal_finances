package com.personalfinances.domain

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.floor

/**
 * Interés diario compuesto para cuentas remuneradas (tipo Tenpo, Mercado Pago, etc.).
 * La tasa es anual (ej 4.5 = 4,5% anual) y se aplica tasa/365 cada día.
 */
object YieldAccrual {

    /**
     * Calcula el interés por mes para los días (from, to] (excluye [from], incluye [to]).
     * Devuelve el interés en unidad mínima agrupado por mes calendario.
     */
    fun accrue(balance: Long, annualRatePercent: Double, from: LocalDate, to: LocalDate): Map<YearMonth, Long> {
        if (!to.isAfter(from) || annualRatePercent <= 0.0 || balance <= 0) return emptyMap()
        val daily = annualRatePercent / 100.0 / 365.0
        var b = balance.toDouble()
        val result = linkedMapOf<YearMonth, Double>()
        var day = from.plusDays(1)
        while (!day.isAfter(to)) {
            val i = b * daily
            b += i
            val ym = YearMonth.from(day)
            result[ym] = (result[ym] ?: 0.0) + i
            day = day.plusDays(1)
        }
        return result.mapValues { floor(it.value + 1e-6).toLong() }.filterValues { it > 0 }
    }

    /** Proyección de ganancia para N días con el saldo actual. */
    fun project(balance: Long, annualRatePercent: Double, days: Int): Long {
        if (annualRatePercent <= 0 || balance <= 0) return 0
        val daily = annualRatePercent / 100.0 / 365.0
        var b = balance.toDouble()
        repeat(days) { b += b * daily }
        return floor(b - balance + 1e-6).toLong()
    }
}
