package com.personalfinances.data

import com.personalfinances.data.db.TxType
import com.personalfinances.data.db.toYearMonth
import com.personalfinances.domain.EventKind
import com.personalfinances.domain.ProjectedEvent
import com.personalfinances.domain.Projection
import com.personalfinances.domain.ProjectionResult
import com.personalfinances.domain.Recurrence
import java.time.LocalDate
import java.time.YearMonth

data class ProjectionView(val result: ProjectionResult, val debtFreeFrom: YearMonth?)

/**
 * Arma la proyección desde el estado actual:
 * - saldo inicial = cuentas incluidas en el patrimonio que no son tarjetas;
 * - ingresos/gastos recurrentes de esas cuentas;
 * - gastos recurrentes en tarjetas se suman a su estado de cuenta;
 * - pagos de tarjetas en su fecha de vencimiento (facturado + cuotas futuras);
 * - rentabilidad estimada de cuentas remuneradas a fin de cada mes.
 */
object ProjectionBuilder {
    fun build(s: FinanceSnapshot, months: Int = 6, today: LocalDate = s.today): ProjectionView {
        val main = s.settings.mainCurrency
        val rates = s.settings.rates
        val liquid = s.activeAccounts.filter { it.account.includeInTotal && !it.account.type.isCard }
        val liquidIds = liquid.map { it.account.id }.toSet()
        val cards = s.cards.filter { it.account.includeInTotal }
        val cardIds = cards.map { it.account.id }.toSet()
        val start = liquid.sumOf { rates.convert(it.balance, it.account.currency, main) }
        val horizon = YearMonth.from(today).plusMonths((months - 1).toLong()).atEndOfMonth()

        val events = mutableListOf<ProjectedEvent>()
        val extraCardCharges = HashMap<Long, HashMap<YearMonth, Long>>()

        for (r in s.recurring.filter { it.active }) {
            val acc = s.account(r.accountId)?.account ?: continue
            val label = r.description.ifBlank { s.categories.firstOrNull { it.id == r.categoryId }?.name ?: "Programado" }
            for (date in Recurrence.pending(LocalDate.ofEpochDay(r.nextEpochDay), horizon, r.frequency, r.dayOfMonth, limit = 400)) {
                val amt = rates.convert(r.amount, acc.currency, main)
                when {
                    acc.id in liquidIds && r.type == TxType.INCOME -> events += ProjectedEvent(date, amt, label, EventKind.INCOME)
                    acc.id in liquidIds && r.type == TxType.EXPENSE -> events += ProjectedEvent(date, -amt, label, EventKind.EXPENSE)
                    // Transferencia a una cuenta fuera del patrimonio = sale dinero; a tarjetas se modela con el pago de la tarjeta.
                    acc.id in liquidIds && r.type == TxType.TRANSFER && r.toAccountId !in liquidIds && r.toAccountId !in cardIds ->
                        events += ProjectedEvent(date, -amt, label, EventKind.EXPENSE)
                    acc.id in cardIds && r.type == TxType.EXPENSE -> {
                        val p = acc.cycle.periodFor(date)
                        extraCardCharges.getOrPut(acc.id) { HashMap() }.merge(p, r.amount, Long::plus)
                    }
                }
            }
        }

        for (st in cards) {
            val c = st.card ?: continue
            val a = st.account
            if (c.billedDue > 0) {
                val date = if (c.dueDate.isBefore(today)) today else c.dueDate
                events += ProjectedEvent(date, -rates.convert(c.billedDue, a.currency, main), "Pago ${a.name}", EventKind.CARD_PAYMENT)
            }
            val extras = extraCardCharges[a.id].orEmpty()
            for ((period, amount) in c.projection) {
                val total = amount + (extras[period] ?: 0)
                if (total > 0) events += ProjectedEvent(a.cycle.dueDate(period), -rates.convert(total, a.currency, main), "Pago ${a.name}", EventKind.CARD_PAYMENT)
            }
        }

        // Rentabilidad estimada: a fin de cada mes.
        liquid.filter { it.account.annualRate > 0 && it.monthlyYield > 0 }.forEach { st ->
            for (i in 0 until months) {
                val date = YearMonth.from(today).plusMonths(i.toLong()).atEndOfMonth()
                events += ProjectedEvent(date, rates.convert(st.monthlyYield, st.account.currency, main), "Rentabilidad ${st.account.name}", EventKind.YIELD)
            }
        }

        // Mes desde el que ya no quedan cuotas por pagar.
        val lastPeriod = s.installments
            .filter { inst -> s.account(inst.accountId)?.account?.let { inst.period > it.paidThroughPeriod && it.id in cardIds } == true }
            .maxOfOrNull { it.period }?.toYearMonth()
        val open = YearMonth.from(today)
        val debtFree = lastPeriod?.takeIf { it >= open }?.plusMonths(1)

        return ProjectionView(Projection.compute(start, events, today, months), debtFree)
    }
}
