package io.gastos.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Clock
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class LabelRepositoryTest {
    private val manila = ZoneId.of("Asia/Manila")
    private lateinit var db: GastosDatabase
    private lateinit var labels: LabelRepository
    private lateinit var expenses: ExpenseRepository
    private lateinit var scope: CoroutineScope
    private lateinit var prefsFile: File

    @Before fun setUp() {
        db = GastosDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        labels = LabelRepository(db) { }
        expenses = ExpenseRepository(db, Clock.system(manila), { manila }) { }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        prefsFile = File.createTempFile("prefs", ".preferences_pb").also { it.delete() }
    }

    @After fun tearDown() {
        db.close()
        scope.cancel()
        prefsFile.delete()
    }

    private suspend fun byName(n: String) = labels.all().first { it.name == n }

    @Test fun seedsDefaultLabelsInOrder() = runBlocking {
        assertEquals(listOf("Food", "Transpo", "Hobbies", "Bills", "Other"), labels.all().map { it.name })
    }

    @Test fun deleteMovesExpensesToOther() = runBlocking {
        val hobbies = byName("Hobbies")
        val e = expenses.add(1000, hobbies.id, db.paymentMethodDao().active().first().id)
        labels.delete(hobbies.id)
        val rows = expenses.rowsForDay(expenses.today())
        assertEquals(e, rows.single().id)
        assertEquals(byName("Other").id, rows.single().labelId)
    }

    @Test fun cannotDeleteOther() = runBlocking {
        val other = byName("Other")
        assertThrows(IllegalArgumentException::class.java) { runBlocking { labels.delete(other.id) } }
        Unit
    }

    @Test fun reorderPersists() = runBlocking {
        val ids = labels.all().map { it.id }.reversed()
        labels.reorder(ids)
        assertEquals(ids, labels.all().map { it.id })
    }

    @Test fun resolveLabelFallsBackWhenDeleted() = runBlocking {
        val prefs = AppPrefs(PreferenceDataStoreFactory.create(scope = scope) { prefsFile })
        val hobbies = byName("Hobbies")
        prefs.setLastLabelId(hobbies.id)
        assertEquals("Hobbies", prefs.resolveLabel(labels.all()).name)
        labels.delete(hobbies.id)
        assertEquals("Food", prefs.resolveLabel(labels.all()).name)
    }
}
