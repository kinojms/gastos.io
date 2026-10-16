package io.gastos.app.ui.labels

import androidx.test.core.app.ApplicationProvider
import io.gastos.app.data.GastosDatabase
import io.gastos.app.data.LabelRepository
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
class LabelsViewModelTest {
    private lateinit var db: GastosDatabase
    private lateinit var repo: LabelRepository

    @Before fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = GastosDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        repo = LabelRepository(db) { }
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun LabelsViewModel.ready() = withTimeout(15_000) { labels.first { it.isNotEmpty() } }

    @Test fun addRejectsBlank() = runBlocking {
        val vm = LabelsViewModel(repo)
        vm.ready()
        assertEquals("Name can't be empty", vm.validateName("   ", null))
        assertEquals("Name can't be empty", vm.add("", 0xFF112233.toInt()))
        assertEquals(5, repo.all().size)
    }

    @Test fun addRejectsDuplicateCaseInsensitive() = runBlocking {
        val vm = LabelsViewModel(repo)
        vm.ready()
        assertEquals("That label already exists", vm.validateName("food", null))
        assertEquals("That label already exists", vm.add("  food ", 0xFF112233.toInt()))
        assertEquals(5, repo.all().size)
    }

    @Test fun addTrimsAndSaves() = runBlocking {
        val vm = LabelsViewModel(repo)
        vm.ready()
        assertNull(vm.add("  Pets ", 0xFF112233.toInt()))
        assertTrue(repo.all().any { it.name == "Pets" })
    }

    @Test fun renameAllowsCaseOnlyChangeOfSameLabel() = runBlocking {
        val vm = LabelsViewModel(repo)
        val food = vm.ready().first { it.name == "Food" }
        assertNull(vm.validateName("FOOD", food.id))
        assertEquals("That label already exists", vm.validateName("transpo", food.id))
        assertNull(vm.rename(food.id, " FOOD "))
        assertEquals("FOOD", repo.all().first { it.id == food.id }.name)
    }

    @Test fun otherCannotBeDeleted() = runBlocking {
        val vm = LabelsViewModel(repo)
        val all = vm.ready()
        assertFalse(vm.canDelete(all.first { it.isOther }))
        assertTrue(vm.canDelete(all.first { it.name == "Food" }))
    }

    @Test fun reorderSavesNewOrder() = runBlocking {
        val vm = LabelsViewModel(repo)
        val before = vm.ready()
        assertFalse(vm.orderChanged())
        vm.move(0, 2)
        assertTrue(vm.orderChanged())
        val expected = before.map { it.id }.toMutableList().also { it.add(2, it.removeAt(0)) }
        assertEquals(expected, vm.displayed.first { it.map { l -> l.id } == expected }.map { it.id })
        vm.saveOrder()
        assertEquals(expected, repo.all().map { it.id })
        assertFalse(vm.orderChanged())
    }

    @Test fun deleteMovesToOther() = runBlocking {
        val vm = LabelsViewModel(repo)
        val food = vm.ready().first { it.name == "Food" }
        vm.delete(food)
        assertFalse(repo.all().any { it.id == food.id })
    }

    @Test fun labelAddedDuringUnsavedReorderIsKept() = runBlocking {
        val vm = LabelsViewModel(repo)
        val before = vm.ready()
        vm.move(0, 2)
        assertNull(vm.add("Pets", 0xFF112233.toInt()))
        val shown = withTimeout(15_000) { vm.displayed.first { it.size == 6 } }.map { it.id }
        assertEquals(shown.size, shown.toSet().size)
        assertTrue(shown.containsAll(before.map { it.id }))
        vm.saveOrder()
        assertEquals(shown, repo.all().map { it.id })
    }

    @Test fun labelDeletedDuringUnsavedReorderIsDropped() = runBlocking {
        val vm = LabelsViewModel(repo)
        val before = vm.ready()
        vm.move(0, 2)
        val food = before.first { it.name == "Food" }
        vm.delete(food)
        val shown = withTimeout(15_000) { vm.displayed.first { it.size == 4 } }.map { it.id }
        assertTrue(food.id !in shown)
        vm.saveOrder()
        assertEquals(shown, repo.all().map { it.id })
    }

    @Test fun asyncWriteSurfacesValidationError() = runBlocking {
        val vm = LabelsViewModel(repo)
        vm.ready()
        vm.addAsync("food", 1)
        assertEquals("That label already exists", withTimeout(15_000) { vm.error.first { it != null } })
    }
}
