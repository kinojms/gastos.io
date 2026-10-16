package io.gastos.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Clock
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class PaymentMethodRepositoryTest {
    private val manila = ZoneId.of("Asia/Manila")
    private lateinit var db: GastosDatabase
    private lateinit var methods: PaymentMethodRepository
    private lateinit var expenses: ExpenseRepository
    private lateinit var labels: LabelRepository
    private lateinit var scope: CoroutineScope
    private lateinit var prefsFile: File
    private var changes = 0

    @Before fun setUp() {
        db = GastosDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        methods = PaymentMethodRepository(db) { changes++ }
        expenses = ExpenseRepository(db, Clock.system(manila), { manila }) { }
        labels = LabelRepository(db) { }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        prefsFile = File.createTempFile("prefs", ".preferences_pb").also { it.delete() }
    }

    @After fun tearDown() {
        db.close()
        scope.cancel()
        prefsFile.delete()
    }

    private suspend fun byName(n: String) = methods.active().first { it.name == n }

    @Test fun seedsDefaultsInOrder() = runBlocking {
        val all = methods.observeAll().first()
        assertEquals(DEFAULT_PAYMENT_METHODS.map { it.first }, all.map { it.name })
        assertEquals(listOf("Cash", "GCash", "Maribank", "BPI", "Maya", "BDO"), all.map { it.name })
        assertEquals(DEFAULT_PAYMENT_METHODS.map { it.second }, all.map { it.colorArgb })
        assertEquals(0xFF007DFE.toInt(), all[1].colorArgb)
        assertFalse(all.any { it.archived })
    }

    @Test fun archiveHidesFromActiveButKeepsHistory() = runBlocking {
        val gcash = byName("GCash")
        expenses.add(18_500, labels.all().first().id, gcash.id)
        methods.archive(gcash.id)
        assertFalse(methods.observeActive().first().any { it.name == "GCash" })
        assertEquals(6, methods.observeAll().first().size)
        assertEquals("GCash", expenses.observeDay(expenses.today()).first().single().paymentMethodName)
        assertEquals(1, changes)
    }

    @Test fun cannotArchiveLastActive() = runBlocking {
        val all = methods.active()
        all.dropLast(1).forEach { methods.archive(it.id) }
        assertThrows(IllegalStateException::class.java) { runBlocking { methods.archive(all.last().id) } }
        assertEquals(listOf(all.last().id), methods.active().map { it.id })
    }

    @Test fun addRenameRecolorReorder() = runBlocking {
        val id = methods.add("UnionBank", 0xFFFF8000.toInt())
        assertEquals("UnionBank", methods.active().last().name)
        methods.rename(id, "UB")
        methods.recolor(id, 0xFF123456.toInt())
        val ub = methods.active().first { it.id == id }
        assertEquals("UB", ub.name)
        assertEquals(0xFF123456.toInt(), ub.colorArgb)
        val reversed = methods.active().map { it.id }.reversed()
        methods.reorder(reversed)
        assertEquals(reversed, methods.observeActive().first().map { it.id })
    }

    @Test fun resolveFallsBackWhenArchived() = runBlocking {
        val prefs = AppPrefs(PreferenceDataStoreFactory.create(scope = scope) { prefsFile })
        assertEquals("Cash", prefs.resolvePaymentMethod(methods.active()).name)
        val gcash = byName("GCash")
        prefs.setLastPaymentMethodId(gcash.id)
        assertEquals(gcash.id, prefs.lastPaymentMethodId.first())
        assertEquals("GCash", prefs.resolvePaymentMethod(methods.active()).name)
        methods.archive(gcash.id)
        assertEquals("Cash", prefs.resolvePaymentMethod(methods.active()).name)
    }
}
