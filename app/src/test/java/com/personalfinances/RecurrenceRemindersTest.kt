package com.personalfinances

import com.personalfinances.domain.Frequency
import com.personalfinances.domain.CardCalculator
import com.personalfinances.domain.CardCycle
import com.personalfinances.domain.CardForReminder
import com.personalfinances.domain.Currency
import com.personalfinances.domain.InstallmentLine
import com.personalfinances.domain.Recurrence
import com.personalfinances.domain.Reminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class RecurrenceRemindersTest {
    @Test fun monthlyClampsToShortMonths() {
        assertEquals(LocalDate.of(2025, 2, 28), Recurrence.next(LocalDate.of(2025, 1, 31), Frequency.MONTHLY, 31))
        assertEquals(LocalDate.of(2025, 3, 31), Recurrence.next(LocalDate.of(2025, 2, 28), Frequency.MONTHLY, 31))
        assertEquals(LocalDate.of(2024, 2, 29), Recurrence.next(LocalDate.of(2023, 2, 28), Frequency.YEARLY, 29))
        assertEquals(LocalDate.of(2025, 1, 8), Recurrence.next(LocalDate.of(2025, 1, 1), Frequency.WEEKLY, 1))
    }

    @Test fun pendingCatchesUpMissedOccurrences() {
        val p = Recurrence.pending(LocalDate.of(2025, 1, 5), LocalDate.of(2025, 3, 10), Frequency.MONTHLY, 5)
        assertEquals(listOf(LocalDate.of(2025, 1, 5), LocalDate.of(2025, 2, 5), LocalDate.of(2025, 3, 5)), p)
        assertTrue(Recurrence.pending(LocalDate.of(2025, 4, 1), LocalDate.of(2025, 3, 10), Frequency.MONTHLY, 1).isEmpty())
    }

    private fun card(today: LocalDate, credits: Long = 0): CardForReminder {
        val cycle = CardCycle(20, 5)
        val lines = listOf(InstallmentLine(YearMonth.of(2025, 3), 100_000, 100_000))
        return CardForReminder(1, "Visa", Currency.CLP, CardCalculator.summarize(cycle, 1_000_000, lines, credits, today))
    }

    @Test fun dueReminders() {
        val t = LocalDate.of(2025, 4, 2) // vence 5/4
        val r = Reminders.compute(listOf(card(t)), t, emptySet())
        assertEquals(1, r.size)
        assertEquals("Visa vence en 3 días", r[0].title)
        assertTrue(Reminders.compute(listOf(card(t)), t, setOf(r[0].key)).isEmpty())
        val dueDay = LocalDate.of(2025, 4, 5)
        assertEquals("Hoy vence Visa", Reminders.compute(listOf(card(dueDay)), dueDay, setOf(r[0].key)).single().title)
        // Pagada: sin recordatorio
        assertTrue(Reminders.compute(listOf(card(t, credits = 100_000)), t, emptySet()).isEmpty())
    }

    @Test fun closingReminder() {
        val t = LocalDate.of(2025, 4, 20)
        val r = Reminders.compute(listOf(card(t, credits = 100_000)), t, emptySet())
        assertEquals("Hoy cierra Visa", r.single().title)
        assertTrue(r.single().text.endsWith("mayo"))
    }
}
