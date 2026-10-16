package io.gastos.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [LabelEntity::class, ExpenseEntity::class, PaymentMethodEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class GastosDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun labelDao(): LabelDao
    abstract fun paymentMethodDao(): PaymentMethodDao

    /** Replaces every label and expense in one transaction, keeping the given ids. */
    suspend fun replaceAll(labels: List<LabelEntity>, expenses: List<ExpenseEntity>) {
        withTransaction {
            expenseDao().deleteAll()
            labelDao().deleteAll()
            labelDao().insertAll(labels)
            expenseDao().insertAll(expenses)
        }
    }

    /** Seeds the default labels and payment methods synchronously when the database is first created. */
    object SeedCallback : Callback() {
        private val defaults = listOf(
            "Food" to 0xFFD9822B.toInt(),
            "Transpo" to 0xFF2F7FC1.toInt(),
            "Hobbies" to 0xFF8A5CC9.toInt(),
            "Bills" to 0xFF3A9A6A.toInt(),
            "Other" to 0xFF77808B.toInt(),
        )

        override fun onCreate(db: SupportSQLiteDatabase) {
            defaults.forEachIndexed { index, (name, color) ->
                val isOther = if (name == "Other") 1 else 0
                db.execSQL(
                    "INSERT INTO labels (name, colorArgb, position, isOther) VALUES (?, ?, ?, ?)",
                    arrayOf<Any>(name, color, index, isOther),
                )
            }
            insertDefaultPaymentMethods(db)
        }
    }

    companion object {
        /** v1.1: adds payment methods (six defaults) and expense description/notes; v1 expenses go to Cash. */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `payment_methods` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `colorArgb` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                        "`archived` INTEGER NOT NULL)",
                )
                insertDefaultPaymentMethods(db)
                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `paymentMethodId` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `description` TEXT")
                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `notes` TEXT")
                db.execSQL(
                    "UPDATE `expenses` SET `paymentMethodId` = " +
                        "(SELECT `id` FROM `payment_methods` WHERE `position` = 0 ORDER BY `id` LIMIT 1)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_expenses_paymentMethodId` ON `expenses` (`paymentMethodId`)",
                )
            }
        }

        /** Inserts [DEFAULT_PAYMENT_METHODS] at positions 0..5, all active (Cash at position 0). */
        private fun insertDefaultPaymentMethods(db: SupportSQLiteDatabase) {
            DEFAULT_PAYMENT_METHODS.forEachIndexed { index, (name, color) ->
                db.execSQL(
                    "INSERT INTO payment_methods (name, colorArgb, position, archived) VALUES (?, ?, ?, 0)",
                    arrayOf<Any>(name, color, index),
                )
            }
        }

        fun create(context: Context): GastosDatabase =
            Room.databaseBuilder(context.applicationContext, GastosDatabase::class.java, "gastos.db")
                .addMigrations(MIGRATION_1_2)
                .addCallback(SeedCallback)
                .build()

        fun createInMemory(context: Context): GastosDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, GastosDatabase::class.java)
                .addCallback(SeedCallback)
                .allowMainThreadQueries()
                .build()
    }
}
