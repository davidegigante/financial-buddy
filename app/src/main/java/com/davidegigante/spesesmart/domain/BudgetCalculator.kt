package com.davidegigante.spesesmart.domain

import com.davidegigante.spesesmart.data.Expense
import com.davidegigante.spesesmart.data.FixedExpense
import com.davidegigante.spesesmart.data.FixedPayment
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Una spesa fissa in un mese preciso: pagata o da pagare. */
data class FixedLine(val fixed: FixedExpense, val payment: FixedPayment?, val month: YearMonth) {
    val isPaid: Boolean get() = payment != null
    /** Importo reale se pagata, altrimenti quello previsto. */
    val amountCents: Long get() = payment?.amountCents ?: fixed.amountCents
    val dueDate: LocalDate get() = month.dueDate(fixed.dueDay)
}

data class WeekSummary(
    val start: LocalDate,
    val end: LocalDate,
    val quotaCents: Long,
    val spentCents: Long,
    /** Parte di [spentCents] che viene da spese ancora da rivedere. */
    val pendingCents: Long,
) {
    val remainingCents: Long get() = quotaCents - spentCents
    val remainingWithoutPendingCents: Long get() = remainingCents + pendingCents
    /** Quota usata (0..∞). 1 = budget esaurito. */
    val usedFraction: Float get() = if (quotaCents <= 0) (if (spentCents > 0) 2f else 0f) else spentCents.toFloat() / quotaCents
}

data class MonthSummary(
    val month: YearMonth,
    val budgetCents: Long,
    val fixedTotalCents: Long,
    val fixedToPayCents: Long,
    val spentCents: Long,
    val pendingCents: Long,
) {
    /** Quanto resta davvero da spendere = budget − spese fisse − spese effettuate. */
    val availableCents: Long get() = budgetCents - fixedTotalCents - spentCents
    val spendableCents: Long get() = budgetCents - fixedTotalCents
    val usedFraction: Float
        get() = if (spendableCents <= 0) (if (spentCents > 0) 2f else 0f) else spentCents.toFloat() / spendableCents
}

/**
 * Tutti i conti del budget, senza Android: si può testare con JUnit.
 *
 * Regole:
 * - Si imposta solo il budget mensile. Le spese fisse vengono accantonate a inizio mese.
 * - Quota settimanale = disponibile del mese a inizio settimana ÷ giorni rimasti × giorni della settimana
 *   che cadono nel mese. Così chi sfora una settimana ha quote un po' più basse nelle successive, e viceversa.
 * - Una settimana a cavallo tra due mesi prende i giorni del mese nuovo dal budget del mese nuovo.
 * - Le spese da rivedere contano subito.
 */
class BudgetCalculator(private val zone: ZoneId = ZoneId.systemDefault()) {

    fun fixedLines(month: YearMonth, fixed: List<FixedExpense>, payments: List<FixedPayment>): List<FixedLine> {
        val key = month.toKey()
        return fixed.filter { it.appliesTo(key) }.map { f ->
            FixedLine(f, payments.find { it.fixedExpenseId == f.id && it.month == key }, month)
        }
    }

    fun fixedTotal(month: YearMonth, fixed: List<FixedExpense>, payments: List<FixedPayment>): Long =
        fixedLines(month, fixed, payments).sumOf { it.amountCents }

    fun month(
        today: LocalDate,
        budgetCents: Long,
        fixed: List<FixedExpense>,
        payments: List<FixedPayment>,
        expenses: List<Expense>,
    ): MonthSummary {
        val month = YearMonth.from(today)
        val lines = fixedLines(month, fixed, payments)
        val inMonth = expenses.filter { YearMonth.from(it.date()) == month }
        return MonthSummary(
            month = month,
            budgetCents = budgetCents,
            fixedTotalCents = lines.sumOf { it.amountCents },
            fixedToPayCents = lines.filterNot { it.isPaid }.sumOf { it.amountCents },
            spentCents = inMonth.sumOf { it.amountCents },
            pendingCents = inMonth.filter { it.isPending }.sumOf { it.amountCents },
        )
    }

    /**
     * [expenses] deve contenere almeno le spese dal primo giorno del mese del lunedì
     * fino alla domenica della settimana di [today].
     */
    fun week(
        today: LocalDate,
        budgetCents: Long,
        fixed: List<FixedExpense>,
        payments: List<FixedPayment>,
        expenses: List<Expense>,
    ): WeekSummary {
        val monday = today.startOfWeek()
        val sunday = monday.plusDays(6)

        val firstMonth = YearMonth.from(monday)
        val firstMonthEnd = firstMonth.atEndOfMonth()
        val spentBeforeMonday = expenses
            .filter { val d = it.date(); !d.isBefore(firstMonth.atDay(1)) && d.isBefore(monday) }
            .sumOf { it.amountCents }
        val availableAtMonday = budgetCents - fixedTotal(firstMonth, fixed, payments) - spentBeforeMonday
        val daysLeftInMonth = ChronoUnit.DAYS.between(monday, firstMonthEnd) + 1
        val weekDaysInFirstMonth = ChronoUnit.DAYS.between(monday, minOf(sunday, firstMonthEnd)) + 1
        var quota = availableAtMonday.coerceAtLeast(0) * weekDaysInFirstMonth / daysLeftInMonth

        if (sunday.isAfter(firstMonthEnd)) {
            val nextMonth = firstMonth.plusMonths(1)
            val weekDaysInNextMonth = ChronoUnit.DAYS.between(nextMonth.atDay(1), sunday) + 1
            val availableNext = budgetCents - fixedTotal(nextMonth, fixed, payments)
            quota += availableNext.coerceAtLeast(0) * weekDaysInNextMonth / nextMonth.lengthOfMonth()
        }

        val inWeek = expenses.filter { val d = it.date(); !d.isBefore(monday) && !d.isAfter(sunday) }
        return WeekSummary(
            start = monday,
            end = sunday,
            quotaCents = quota,
            spentCents = inWeek.sumOf { it.amountCents },
            pendingCents = inWeek.filter { it.isPending }.sumOf { it.amountCents },
        )
    }

    /** Intervallo [from, to) di spese che serve a [week] e [month] per il giorno [today]. */
    fun neededRange(today: LocalDate): Pair<LocalDate, LocalDate> {
        val monday = today.startOfWeek()
        val from = minOf(YearMonth.from(monday).atDay(1), YearMonth.from(today).atDay(1))
        val to = maxOf(monday.plusDays(7), YearMonth.from(today).atEndOfMonth().plusDays(1))
        return from to to
    }

    private fun Expense.date(): LocalDate = occurredAt.toLocalDate(zone)
}
