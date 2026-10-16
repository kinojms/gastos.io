package io.gastos.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [LabelEntity::class, ExpenseEntity::class], version = 1, exportSchema = true)
abstract class GastosDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun labelDao(): LabelDao

    /** Seeds the default labels synchronously when the database is first created. */
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
        }
    }

    companion object {
        fun create(context: Context): GastosDatabase =
            Room.databaseBuilder(context.applicationContext, GastosDatabase::class.java, "gastos.db")
                .addCallback(SeedCallback)
                .build()

        fun createInMemory(context: Context): GastosDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, GastosDatabase::class.java)
                .addCallback(SeedCallback)
                .allowMainThreadQueries()
                .build()
    }
}
