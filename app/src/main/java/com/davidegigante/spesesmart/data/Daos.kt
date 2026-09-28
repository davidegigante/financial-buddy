package com.davidegigante.spesesmart.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY id")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(categories: List<Category>)

    @Insert
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)
}

@Dao
interface ExpenseDao {
    /** Spese con occurredAt in [from, to). */
    @Query("SELECT * FROM expenses WHERE occurredAt >= :from AND occurredAt < :to ORDER BY occurredAt DESC, id DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE occurredAt >= :from AND occurredAt < :to")
    suspend fun getBetween(from: Long, to: Long): List<Expense>

    @Query("SELECT * FROM expenses WHERE status = 'PENDING' ORDER BY occurredAt DESC, id DESC")
    fun observePending(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses ORDER BY occurredAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: Long): Expense?

    @Query("SELECT COUNT(*) FROM expenses WHERE notificationId = :notificationId")
    suspend fun countFromNotification(notificationId: Long): Int

    @Insert
    suspend fun insert(expense: Expense): Long

    @Update
    suspend fun update(expense: Expense)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE expenses SET status = 'CONFIRMED' WHERE id = :id")
    suspend fun confirm(id: Long)

    @Query("UPDATE expenses SET status = 'CONFIRMED' WHERE status = 'PENDING'")
    suspend fun confirmAll()
}

@Dao
interface FixedExpenseDao {
    @Query("SELECT * FROM fixed_expenses ORDER BY dueDay, name")
    fun observeAll(): Flow<List<FixedExpense>>

    @Query("SELECT * FROM fixed_expenses")
    suspend fun getAll(): List<FixedExpense>

    @Query("SELECT * FROM fixed_expenses WHERE id = :id")
    suspend fun getById(id: Long): FixedExpense?

    @Insert
    suspend fun insert(fixed: FixedExpense): Long

    @Update
    suspend fun update(fixed: FixedExpense)

    @Delete
    suspend fun delete(fixed: FixedExpense)

    @Query("SELECT * FROM fixed_payments WHERE month >= :fromMonth")
    fun observePaymentsFrom(fromMonth: Int): Flow<List<FixedPayment>>

    @Query("SELECT * FROM fixed_payments WHERE month >= :fromMonth")
    suspend fun getPaymentsFrom(fromMonth: Int): List<FixedPayment>

    @Query("SELECT COUNT(*) FROM fixed_payments WHERE fixedExpenseId = :fixedId AND month = :month")
    suspend fun isPaid(fixedId: Long, month: Int): Int

    @Upsert
    suspend fun upsertPayment(payment: FixedPayment)

    @Query("DELETE FROM fixed_payments WHERE fixedExpenseId = :fixedId AND month = :month")
    suspend fun deletePayment(fixedId: Long, month: Int)
}

@Dao
interface MerchantRuleDao {
    @Query("SELECT * FROM merchant_rules WHERE merchantKey = :key")
    suspend fun get(key: String): MerchantRule?

    @Upsert
    suspend fun upsert(rule: MerchantRule)
}
