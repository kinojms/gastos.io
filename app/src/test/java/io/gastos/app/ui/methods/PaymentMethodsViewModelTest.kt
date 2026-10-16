package io.gastos.app.ui.methods

import androidx.test.core.app.ApplicationProvider
import io.gastos.app.data.GastosDatabase
import io.gastos.app.data.PaymentMethodRepository
import io.gastos.app.ui.ViewModelTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PaymentMethodsViewModelTest {
    private lateinit var db: GastosDatabase
    private lateinit var repo: PaymentMethodRepository
    private val vms = ViewModelTracker()

    @Before fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = GastosDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        repo = PaymentMethodRepository(db) { }
    }

    @After fun tearDown() {
        vms.clear()
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun PaymentMethodsViewModel.ready() = withTimeout(15_000) { methods.first { it.isNotEmpty() } }

    @Test fun addRejectsBlank() = runBlocking {
        val vm = vms.track { PaymentMethodsViewModel(repo) }
        vm.ready()
        assertEquals("Name can't be empty", vm.validateName("   ", null))
        assertEquals("Name can't be empty", vm.add("", 0xFF112233.toInt()))
        assertEquals(6, repo.active().size)
    }

    @Test fun addRejectsDuplicateCaseInsensitive() = runBlocking {
        val vm = vms.track { PaymentMethodsViewModel(repo) }
        vm.ready()
        assertEquals("That payment method already exists", vm.validateName("gcash", null))
        assertEquals("That payment method already exists", vm.add("  GCASH ", 0xFF112233.toInt()))
        assertEquals(6, repo.active().size)
    }

    @Test fun archivedNameCanBeReused() = runBlocking {
        val vm = vms.track { PaymentMethodsViewModel(repo) }
        val bdo = vm.ready().first { it.name == "BDO" }
        vm.delete(bdo)
        withTimeout(15_000) { vm.methods.first { list -> list.none { it.name == "BDO" } } }
        assertNull(vm.add("BDO", 0xFF112233.toInt()))
    }

    @Test fun renameAllowsCaseOnlyChangeOfSameMethod() = runBlocking {
        val vm = vms.track { PaymentMethodsViewModel(repo) }
        val cash = vm.ready().first { it.name == "Cash" }
        assertNull(vm.validateName("CASH", cash.id))
        assertEquals("That payment method already exists", vm.validateName("bpi", cash.id))
        assertNull(vm.rename(cash.id, " CASH "))
        assertEquals("CASH", repo.active().first { it.id == cash.id }.name)
    }

    @Test fun deleteHiddenForLastActive() = runBlocking {
        val vm = vms.track { PaymentMethodsViewModel(repo) }
        val all = vm.ready()
        assertTrue(vm.canDelete())
        all.drop(1).forEach { vm.delete(it) }
        val last = withTimeout(15_000) { vm.methods.first { it.size == 1 } }.single()
        assertFalse(vm.canDelete())
        vm.delete(last) // guarded: no throw, still one active
        assertEquals(1, repo.active().size)
    }

    @Test fun reorderPersistsFullActiveList() = runBlocking {
        val vm = vms.track { PaymentMethodsViewModel(repo) }
        val before = vm.ready()
        assertFalse(vm.orderChanged())
        vm.move(0, 2)
        assertTrue(vm.orderChanged())
        val expected = before.map { it.id }.toMutableList().also { it.add(2, it.removeAt(0)) }
        vm.saveOrder()
        assertEquals(expected, repo.active().map { it.id })
        assertFalse(vm.orderChanged())
    }

    @Test fun methodDeletedDuringUnsavedReorderIsDropped() = runBlocking {
        val vm = vms.track { PaymentMethodsViewModel(repo) }
        val before = vm.ready()
        vm.move(0, 2)
        val gcash = before.first { it.name == "GCash" }
        vm.delete(gcash)
        val shown = withTimeout(15_000) { vm.displayed.first { it.size == 5 } }.map { it.id }
        assertTrue(gcash.id !in shown)
        vm.saveOrder()
        assertEquals(shown, repo.active().map { it.id })
    }

    @Test fun asyncWriteSurfacesValidationError() = runBlocking {
        val vm = vms.track { PaymentMethodsViewModel(repo) }
        vm.ready()
        vm.addAsync("cash", 1)
        assertEquals("That payment method already exists", withTimeout(15_000) { vm.error.first { it != null } })
    }

    @Test fun confirmedAddSurvivesLeavingScreen() = runBlocking {
        val store = androidx.lifecycle.ViewModelStore()
        val factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = PaymentMethodsViewModel(repo) as T
        }
        val vm = androidx.lifecycle.ViewModelProvider(store, factory)[PaymentMethodsViewModel::class.java]
        vm.ready()
        vm.addAsync("Wise", 0xFF112233.toInt())
        store.clear()
        withTimeout(15_000) { while (repo.active().none { it.name == "Wise" }) kotlinx.coroutines.delay(20) }
    }
}
