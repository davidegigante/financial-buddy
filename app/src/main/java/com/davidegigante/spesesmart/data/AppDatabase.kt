package com.davidegigante.spesesmart.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CapturedNotification::class,
        Category::class,
        Expense::class,
        FixedExpense::class,
        FixedPayment::class,
        MerchantRule::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // 1 → 2: aggiunge spese, categorie, spese fisse e regole. Le notifiche già salvate restano.
        AutoMigration(from = 1, to = 2),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun capturedNotificationDao(): CapturedNotificationDao
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun fixedExpenseDao(): FixedExpenseDao
    abstract fun merchantRuleDao(): MerchantRuleDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "spese_smart.db").build()
    }
}
