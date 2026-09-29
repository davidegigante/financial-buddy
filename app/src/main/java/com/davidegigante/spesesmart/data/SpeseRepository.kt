package com.davidegigante.spesesmart.data

import com.davidegigante.spesesmart.domain.BudgetCalculator
import com.davidegigante.spesesmart.domain.FixedLine
import com.davidegigante.spesesmart.domain.Money
import com.davidegigante.spesesmart.domain.MonthSummary
import com.davidegigante.spesesmart.domain.ParsedPayment
import com.davidegigante.spesesmart.domain.PaymentParser
import com.davidegigante.spesesmart.domain.WeekSummary
import com.davidegigante.spesesmart.domain.startMillis
import com.davidegigante.spesesmart.domain.toLocalDate
import com.davidegigante.spesesmart.domain.toKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Tutto quello che serve alla home, calcolato per un giorno preciso. */
data class Dashboard(
    val today: LocalDate,
    val budget: BudgetSettings.State,
    val week: WeekSummary,
    val month: MonthSummary,
    val fixedLines: List<FixedLine>,
    val pending: List<Expense>,
    val recent: List<Expense>,
    val categories: Map<Long, Category>,
    /** Spese del mese per categoria (null = senza categoria), dalla più grande. */
    val monthByCategory: List<Pair<Long?, Long>>,
)

class SpeseRepository(
    private val db: AppDatabase,
    val budgetSettings: BudgetSettings,
    /** Chiamata dopo ogni modifica che può far scattare un avviso di budget. */
    private val onBudgetChanged: () -> Unit,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val categories = db.categoryDao()
    private val expenses = db.expenseDao()
    private val fixed = db.fixedExpenseDao()
    private val rules = db.merchantRuleDao()

    val calculator = BudgetCalculator(zone)

    // --- Categorie ---

    fun observeCategories(): Flow<List<Category>> = categories.observeAll()

    suspend fun ensureDefaultCategories() {
        if (categories.count() > 0) return
        categories.insertAll(
            listOf(
                "🛒" to "Spesa alimentare",
                "☕" to "Bar e ristoranti",
                "🚗" to "Trasporti e carburante",
                "🏠" to "Casa",
                "💡" to "Bollette",
                "💊" to "Salute",
                "🎮" to "Svago",
                "👕" to "Abbigliamento",
                "📺" to "Abbonamenti",
                "🎁" to "Regali",
                "📦" to "Altro",
            ).map { (emoji, name) -> Category(name = name, emoji = emoji) },
        )
    }

    suspend fun saveCategory(category: Category) {
        if (category.id == 0L) categories.insert(category) else categories.update(category)
    }

    suspend fun deleteCategory(category: Category) = categories.delete(category)

    // --- Spese ---

    fun observeExpensesBetween(from: LocalDate, toExclusive: LocalDate): Flow<List<Expense>> =
        expenses.observeBetween(from.startMillis(zone), toExclusive.startMillis(zone))

    suspend fun getExpense(id: Long): Expense? = expenses.getById(id)

    /** Salva la spesa e, se ha esercente e categoria, impara la regola per le prossime volte. */
    suspend fun saveExpense(expense: Expense) {
        if (expense.id == 0L) expenses.insert(expense) else expenses.update(expense)
        if (expense.merchant.isNotBlank() && expense.categoryId != null) {
            rules.upsert(MerchantRule(MerchantRule.keyOf(expense.merchant), expense.categoryId, System.currentTimeMillis()))
        }
        onBudgetChanged()
    }

    suspend fun deleteExpense(id: Long) {
        expenses.delete(id)
        onBudgetChanged()
    }

    suspend fun confirmExpense(id: Long) = expenses.confirm(id)

    suspend fun confirmAll() = expenses.confirmAll()

    suspend fun suggestCategory(merchant: String): Long? =
        merchant.takeIf { it.isNotBlank() }?.let { rules.get(MerchantRule.keyOf(it))?.categoryId }

    /**
     * Crea una spesa "da rivedere" da una notifica salvata.
     * Con [parser] usa le regole dell'app (es. Isybank); senza, dal pulsante di debug, cerca un importo qualsiasi.
     * Restituisce false se il testo non è un pagamento o se la spesa esiste già (anche da un'altra notifica).
     */
    suspend fun createPendingFromNotification(n: CapturedNotification, rawText: String, parser: PaymentParser?): Boolean {
        val text = listOfNotNull(n.title, n.fullText, n.textLines).joinToString("\n")
        val postedAt = Instant.ofEpochMilli(n.postTime).atZone(zone).toLocalDateTime()
        val parsed = if (parser != null) {
            parser.parse(text, postedAt) ?: return false
        } else {
            ParsedPayment(Money.findAmount(text) ?: return false, n.appLabel, occurredAt = null)
        }
        if (expenses.countFromNotification(n.id) > 0) return false

        val occurredAt = parsed.occurredAt?.atZone(zone)?.toInstant()?.toEpochMilli() ?: n.postTime
        // La stessa transazione può arrivare da più notifiche (es. banca e Google Pay): stesso importo a pochi minuti.
        val window = DUPLICATE_WINDOW_MINUTES * 60_000
        if (expenses.countCapturedSimilar(parsed.amountCents, occurredAt - window, occurredAt + window) > 0) return false

        expenses.insert(
            Expense(
                amountCents = parsed.amountCents,
                merchant = parsed.merchant,
                note = parsed.note,
                categoryId = suggestCategory(parsed.merchant),
                occurredAt = occurredAt,
                source = ExpenseSource.NOTIFICATION,
                status = ExpenseStatus.PENDING,
                rawText = rawText,
                notificationId = n.id,
            ),
        )
        onBudgetChanged()
        return true
    }

    // --- Spese fisse ---

    fun observeFixed(): Flow<List<FixedExpense>> = fixed.observeAll()

    fun observePaymentsFrom(month: YearMonth): Flow<List<FixedPayment>> = fixed.observePaymentsFrom(month.toKey())

    suspend fun getFixed(id: Long): FixedExpense? = fixed.getById(id)

    suspend fun saveFixed(item: FixedExpense) {
        if (item.id == 0L) fixed.insert(item) else fixed.update(item)
        onBudgetChanged()
    }

    /**
     * Elimina una spesa fissa da questo mese in avanti. Se nel mese corrente è già stata pagata
     * resta nei conti di questo mese; i mesi passati non cambiano.
     */
    suspend fun deleteFixed(item: FixedExpense, today: LocalDate) {
        val current = YearMonth.from(today)
        val paidThisMonth = fixed.isPaid(item.id, current.toKey()) > 0
        val lastMonth = if (paidThisMonth) current.toKey() else current.minusMonths(1).toKey()
        if (lastMonth < item.startMonth) fixed.delete(item) else fixed.update(item.copy(endMonth = lastMonth))
        onBudgetChanged()
    }

    suspend fun markFixedPaid(fixedId: Long, month: YearMonth, amountCents: Long) {
        fixed.upsertPayment(FixedPayment(fixedId, month.toKey(), amountCents, System.currentTimeMillis()))
        onBudgetChanged()
    }

    suspend fun unmarkFixedPaid(fixedId: Long, month: YearMonth) {
        fixed.deletePayment(fixedId, month.toKey())
        onBudgetChanged()
    }

    // --- Budget ---

    fun observeDashboard(today: LocalDate): Flow<Dashboard> {
        val (from, to) = calculator.neededRange(today)
        val base = combine(
            budgetSettings.state,
            categories.observeAll(),
            fixed.observeAll(),
            fixed.observePaymentsFrom(YearMonth.from(from).toKey()),
        ) { budget, cats, fixedList, payments -> Base(budget, cats, fixedList, payments) }

        return combine(
            base,
            observeExpensesBetween(from, to),
            expenses.observePending(),
            expenses.observeRecent(5),
        ) { b, rangeExpenses, pending, recent ->
            Dashboard(
                today = today,
                budget = b.budget,
                week = calculator.week(today, b.budget.monthlyBudgetCents, b.fixed, b.payments, rangeExpenses),
                month = calculator.month(today, b.budget.monthlyBudgetCents, b.fixed, b.payments, rangeExpenses),
                fixedLines = calculator.fixedLines(YearMonth.from(today), b.fixed, b.payments),
                pending = pending,
                recent = recent,
                categories = b.categories.associateBy { it.id },
                monthByCategory = rangeExpenses
                    .filter { YearMonth.from(it.occurredAt.toLocalDate(zone)) == YearMonth.from(today) }
                    .groupBy { it.categoryId }
                    .map { (id, items) -> id to items.sumOf { it.amountCents } }
                    .sortedByDescending { it.second },
            )
        }
    }

    /** Stessi conti della home, una volta sola: per gli avvisi in background. */
    suspend fun snapshot(today: LocalDate): Triple<WeekSummary, MonthSummary, List<FixedLine>> {
        val (from, to) = calculator.neededRange(today)
        val budget = budgetSettings.state.value.monthlyBudgetCents
        val fixedList = fixed.getAll()
        val payments = fixed.getPaymentsFrom(YearMonth.from(from).toKey())
        val range = expenses.getBetween(from.startMillis(zone), to.startMillis(zone))
        return Triple(
            calculator.week(today, budget, fixedList, payments, range),
            calculator.month(today, budget, fixedList, payments, range),
            calculator.fixedLines(YearMonth.from(today), fixedList, payments),
        )
    }

    private companion object {
        const val DUPLICATE_WINDOW_MINUTES = 15L
    }

    private data class Base(
        val budget: BudgetSettings.State,
        val categories: List<Category>,
        val fixed: List<FixedExpense>,
        val payments: List<FixedPayment>,
    )
}
