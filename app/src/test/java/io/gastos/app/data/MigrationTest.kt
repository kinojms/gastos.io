package io.gastos.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), GastosDatabase::class.java)

    @Test fun v1DataSurvivesMigration() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO labels (id, name, colorArgb, position, isOther) VALUES (7, 'Food', 1, 0, 0)")
            execSQL("INSERT INTO expenses (id, amountCentavos, labelId, timestamp) VALUES (1, 4550, 7, 1000)")
            execSQL("INSERT INTO expenses (id, amountCentavos, labelId, timestamp) VALUES (2, 18500, 7, 2000)")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 2, true, GastosDatabase.MIGRATION_1_2)

        val methods = mutableListOf<Pair<Long, String>>()
        db.query("SELECT id, name, archived FROM payment_methods ORDER BY position").use { c ->
            while (c.moveToNext()) {
                methods += c.getLong(0) to c.getString(1)
                assertEquals(0, c.getInt(2))
            }
        }
        assertEquals(listOf("Cash", "GCash", "Maribank", "BPI", "Maya", "BDO"), methods.map { it.second })
        val cashId = methods.first().first

        val rows = mutableListOf<List<Any?>>()
        db.query("SELECT id, amountCentavos, labelId, timestamp, paymentMethodId, description, notes FROM expenses ORDER BY id")
            .use { c ->
                while (c.moveToNext()) {
                    rows += listOf(
                        c.getLong(0), c.getLong(1), c.getLong(2), c.getLong(3), c.getLong(4),
                        if (c.isNull(5)) null else c.getString(5),
                        if (c.isNull(6)) null else c.getString(6),
                    )
                }
            }
        assertEquals(
            listOf(
                listOf(1L, 4550L, 7L, 1000L, cashId, null, null),
                listOf(2L, 18500L, 7L, 2000L, cashId, null, null),
            ),
            rows,
        )
        db.close()
    }
}
