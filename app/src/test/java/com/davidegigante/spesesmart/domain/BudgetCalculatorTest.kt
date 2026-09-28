package com.davidegigante.spesesmart.domain

import com.davidegigante.spesesmart.data.Expense
import com.davidegigante.spesesmart.data.ExpenseStatus
import com.davidegigante.spesesmart.data.FixedExpense
import com.davidegigante.spesesmart.data.FixedPayment
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class BudgetCalculatorTest {

    private val zone = ZoneOffset.UTC
    private val calc = BudgetCalculator(zone)

    private fun expense(date: LocalDate, euros: Long, pending: Boolean = false) = Expense(
        amountCents = euros * 100,
        merchant = "x",
        categoryId = null,
        occurredAt = date.atTime(12, 0).toInstant(zone).toEpochMilli(),
        status = if (pending) ExpenseStatus.PENDING else ExpenseStatus.CONFIRMED,
    )

    // Giugno 2026: 30 giorni, il 1° è lunedì. Budget 1.200 €, spese fisse 700 € → 500 € da spendere.
    private val rent = FixedExpense(id = 1, name = "Affitto", amountCents = 60_000, dueDay = 5, categoryId = null, startMonth = 202601)
    private val bills = FixedExpense(id = 2, name = "Bollette", amountCents = 10_000, dueDay = 20, categoryId = null, startMonth = 202601)
    private val fixed = listOf(rent, bills)

    @Test
    fun `prima settimana del mese - 500 euro su 30 giorni`() {
        val week = calc.week(LocalDate.of(2026, 6, 3), 120_000, fixed, emptyList(), emptyList())
        assertEquals(LocalDate.of(2026, 6, 1), week.start)
        assertEquals(50_000L * 7 / 30, week.quotaCents) // 116,66 €
    }

    @Test
    fun `chi sfora una settimana ha quote piu basse dopo`() {
        val expenses = listOf(expense(LocalDate.of(2026, 6, 3), 200))
        val week = calc.week(LocalDate.of(2026, 6, 8), 120_000, fixed, emptyList(), expenses)
        assertEquals(30_000L * 7 / 23, week.quotaCents) // 91,30 €
        assertEquals(0L, week.spentCents)
    }

    @Test
    fun `le spese da rivedere contano ma si vede quanto pesano`() {
        val expenses = listOf(
            expense(LocalDate.of(2026, 6, 2), 20),
            expense(LocalDate.of(2026, 6, 3), 30, pending = true),
        )
        val week = calc.week(LocalDate.of(2026, 6, 4), 120_000, fixed, emptyList(), expenses)
        assertEquals(5_000L, week.spentCents)
        assertEquals(3_000L, week.pendingCents)
        assertEquals(week.remainingCents + 3_000, week.remainingWithoutPendingCents)
    }

    @Test
    fun `settimana a cavallo tra due mesi`() {
        // Lun 28 settembre - dom 4 ottobre 2026. Settembre: 3 giorni rimasti, tutti in questa settimana.
        val expenses = listOf(expense(LocalDate.of(2026, 9, 10), 100))
        val week = calc.week(LocalDate.of(2026, 10, 2), 120_000, fixed, emptyList(), expenses)
        val september = 120_000L - 70_000 - 10_000 // tutto il residuo di settembre
        val october = 50_000L * 4 / 31
        assertEquals(september + october, week.quotaCents)
    }

    @Test
    fun `la spesa fissa pagata non viene contata due volte e usa l'importo reale`() {
        val payments = listOf(FixedPayment(fixedExpenseId = 2, month = 202606, amountCents = 8_700, paidAt = 0))
        val month = calc.month(LocalDate.of(2026, 6, 25), 120_000, fixed, payments, emptyList())
        assertEquals(68_700L, month.fixedTotalCents)
        assertEquals(60_000L, month.fixedToPayCents)
        assertEquals(51_300L, month.availableCents)
    }

    @Test
    fun `spesa fissa terminata non conta nei mesi dopo`() {
        val ended = bills.copy(endMonth = 202605)
        assertEquals(60_000L, calc.fixedTotal(java.time.YearMonth.of(2026, 6), listOf(rent, ended), emptyList()))
    }

    @Test
    fun `mese gia sforato - quota zero`() {
        val expenses = listOf(expense(LocalDate.of(2026, 6, 2), 600))
        val week = calc.week(LocalDate.of(2026, 6, 10), 120_000, fixed, emptyList(), expenses)
        assertEquals(0L, week.quotaCents)
    }
}
