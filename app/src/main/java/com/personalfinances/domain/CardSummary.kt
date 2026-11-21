package com.personalfinances.domain

import java.time.LocalDate
import java.time.YearMonth

data class InstallmentLine(val period: YearMonth, val amount: Long, val principal: Long)

data class CardSummary(
    val limit: Long,
    /** Cupo utilizado (capital pendiente + intereses ya facturados no pagados). */
    val used: Long,
    val available: Long,
    /** Monto del último estado de cuenta cerrado que falta pagar. */
    val billedDue: Long,
    val billedPeriod: YearMonth,
    val dueDate: LocalDate,
    val closingDate: LocalDate,
    /** Lo que se está acumulando en el periodo abierto (descontando sobrepagos). */
    val openPeriodAmount: Long,
    val openPeriod: YearMonth,
    /** Proyección de lo que se facturará cada mes desde el periodo abierto en adelante. */
    val projection: List<Pair<YearMonth, Long>>,
) {
    val usedFraction: Float get() = if (limit <= 0) 0f else (used.toFloat() / limit).coerceIn(0f, 1f)
}

object CardCalculator {
    /**
     * @param installments todas las cuotas de la tarjeta.
     * @param credits pagos a la tarjeta + devoluciones/abonos.
     */
    fun summarize(
        cycle: CardCycle,
        limit: Long,
        installments: List<InstallmentLine>,
        credits: Long,
        today: LocalDate,
        projectionMonths: Int = 12,
    ): CardSummary {
        val open = cycle.openPeriod(today)
        val closed = open.minusMonths(1)

        val billedTotal = installments.filter { it.period <= closed }.sumOf { it.amount }
        val billedDue = (billedTotal - credits).coerceAtLeast(0)
        val overpaid = (credits - billedTotal).coerceAtLeast(0)

        val debt = installments.sumOf { if (it.period <= closed) it.amount else it.principal } - credits
        val used = debt.coerceAtLeast(0)

        val byPeriod = installments.filter { it.period >= open }.groupBy { it.period }
            .mapValues { (_, l) -> l.sumOf { it.amount } }
        var remainingCredit = overpaid
        val projection = (0 until projectionMonths).map { i ->
            val p = open.plusMonths(i.toLong())
            val raw = byPeriod[p] ?: 0L
            val applied = minOf(raw, remainingCredit)
            remainingCredit -= applied
            p to (raw - applied)
        }

        return CardSummary(
            limit = limit,
            used = used,
            available = limit - used,
            billedDue = billedDue,
            billedPeriod = closed,
            dueDate = cycle.dueDate(closed),
            closingDate = cycle.closingDate(open),
            openPeriodAmount = projection.first().second,
            openPeriod = open,
            projection = projection,
        )
    }
}
