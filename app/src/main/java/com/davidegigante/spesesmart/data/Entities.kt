package com.davidegigante.spesesmart.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
) {
    val label: String get() = "$emoji $name"
}

enum class ExpenseSource { MANUAL, NOTIFICATION }

/**
 * PENDING = catturata da una notifica e non ancora rivista.
 * Conta comunque nel budget: la conferma serve solo a sistemare i dettagli.
 */
enum class ExpenseStatus { CONFIRMED, PENDING }

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("occurredAt"), Index("categoryId"), Index("status")],
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountCents: Long,
    val merchant: String,
    val note: String = "",
    val categoryId: Long?,
    /** Quando è avvenuta la spesa (ms epoch). */
    val occurredAt: Long,
    val source: ExpenseSource = ExpenseSource.MANUAL,
    val status: ExpenseStatus = ExpenseStatus.CONFIRMED,
    /** Testo grezzo della notifica da cui è nata, per debug e riconciliazione. */
    val rawText: String? = null,
    val notificationId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isPending: Boolean get() = status == ExpenseStatus.PENDING
}

/**
 * Spesa fissa mensile (affitto, bollette, psicologa...). Viene accantonata a inizio mese
 * per tutti i mesi da [startMonth] a [endMonth] (yyyyMM, null = senza fine).
 */
@Entity(
    tableName = "fixed_expenses",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("categoryId")],
)
data class FixedExpense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Importo previsto. */
    val amountCents: Long,
    /** Giorno del mese entro cui va pagata (1-31). */
    val dueDay: Int,
    val categoryId: Long?,
    val startMonth: Int,
    val endMonth: Int? = null,
) {
    fun appliesTo(month: Int): Boolean = month >= startMonth && (endMonth == null || month <= endMonth)
}

/**
 * Pagamento di una spesa fissa in un certo mese. Se non esiste la riga, la spesa è "da pagare".
 * Pagarla non toglie altro dal budget: cambia solo lo stato e, se serve, l'importo reale.
 */
@Entity(
    tableName = "fixed_payments",
    primaryKeys = ["fixedExpenseId", "month"],
    foreignKeys = [
        ForeignKey(
            entity = FixedExpense::class,
            parentColumns = ["id"],
            childColumns = ["fixedExpenseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("month")],
)
data class FixedPayment(
    val fixedExpenseId: Long,
    val month: Int,
    val amountCents: Long,
    val paidAt: Long,
)

/** Regola imparata: questo esercente va in questa categoria. */
@Entity(
    tableName = "merchant_rules",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("categoryId")],
)
data class MerchantRule(
    /** Nome dell'esercente normalizzato (minuscolo, spazi compattati). */
    @PrimaryKey val merchantKey: String,
    val categoryId: Long,
    val updatedAt: Long,
) {
    companion object {
        fun keyOf(merchant: String): String = merchant.trim().lowercase().replace(Regex("\\s+"), " ")
    }
}
