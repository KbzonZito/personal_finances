package com.personalfinances

import com.personalfinances.domain.GoalCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class GoalsTest {
    private val today = LocalDate.of(2025, 1, 15)

    @Test fun neededPerMonthWithoutYield() {
        val p = GoalCalc.progress(1_200_000, 200_000, today, LocalDate.of(2025, 11, 1))
        assertEquals(10L, p.monthsLeft)
        assertEquals(100_000L, p.perMonthNeeded)
        assertEquals(YearMonth.of(2025, 11), p.eta)
    }

    @Test fun yieldReducesNeededContribution() {
        val plain = GoalCalc.neededWithYield(0, 1_000_000, 12, 0.0)
        val withYield = GoalCalc.neededWithYield(0, 1_000_000, 12, 5.0)
        assertEquals(83_334L, plain)
        assertTrue(withYield < plain)
    }

    @Test fun doneGoalAndNoDeadline() {
        val done = GoalCalc.progress(100, 150, today, null)
        assertTrue(done.done); assertNull(done.perMonthNeeded); assertEquals(1f, done.fraction)
        val open = GoalCalc.progress(1_000_000, 0, today, null, monthlyContribution = 250_000)
        assertNull(open.perMonthNeeded)
        assertEquals(YearMonth.of(2025, 5), open.eta)
    }
}
