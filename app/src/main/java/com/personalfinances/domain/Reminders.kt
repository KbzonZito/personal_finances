package com.personalfinances.domain

import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

data class CardForReminder(val id: Long, val name: String, val currency: Currency, val summary: CardSummary)

/** [key] identifica el recordatorio para no repetirlo. */
data class Reminder(val key: String, val cardId: Long, val title: String, val text: String)

object Reminders {
    private val es = Locale("es", "CL")

    fun compute(cards: List<CardForReminder>, today: LocalDate, alreadySent: Set<String>): List<Reminder> {
        val out = mutableListOf<Reminder>()
        for (c in cards) {
            val s = c.summary
            if (s.billedDue > 0) {
                val days = ChronoUnit.DAYS.between(today, s.dueDate)
                val amount = Money.format(s.billedDue, c.currency)
                when {
                    days == 0L -> out += Reminder("due0:${c.id}:${s.billedPeriod}", c.id, "Hoy vence ${c.name}", "Paga $amount para evitar intereses")
                    days in 1..3 -> out += Reminder(
                        "due3:${c.id}:${s.billedPeriod}", c.id,
                        "${c.name} vence " + if (days == 1L) "mañana" else "en $days días",
                        "Monto a pagar: $amount",
                    )
                }
            }
            if (today == s.closingDate) {
                val next = s.openPeriod.plusMonths(1).month.getDisplayName(TextStyle.FULL, es)
                out += Reminder(
                    "close:${c.id}:${s.openPeriod}", c.id, "Hoy cierra ${c.name}",
                    "Lo que compres desde mañana se cobra en el estado de cuenta de $next",
                )
            }
        }
        return out.filter { it.key !in alreadySent }
    }
}
