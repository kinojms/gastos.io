package io.gastos.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.gastos.app.AppContainer
import java.time.LocalDate
import io.gastos.app.ui.expenses.DayScreen
import io.gastos.app.ui.expenses.ExpensesScreen
import io.gastos.app.ui.expenses.ExpensesViewModel
import io.gastos.app.ui.home.HomeScreen
import io.gastos.app.ui.home.HomeViewModel
import io.gastos.app.ui.labels.LabelsScreen
import io.gastos.app.ui.labels.LabelsViewModel
import io.gastos.app.ui.settings.SettingsScreen
import io.gastos.app.ui.settings.SettingsViewModel

object Routes {
    const val HOME = "home"
    const val EXPENSES = "expenses"
    const val DAY = "day/{isoDate}"
    const val LABELS = "labels"
    const val SETTINGS = "settings"

    fun day(isoDate: String) = "day/$isoDate"
}

@Composable
fun GastosNavHost(container: AppContainer) {
    val nav = rememberNavController()
    val homeFactory = remember(container) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(container.expenses, container.labels, container.prefs) as T
        }
    }
    val expensesFactory = remember(container) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ExpensesViewModel(container.expenses, container.labels, exporter = container.exportService) as T
        }
    }
    val labelsFactory = remember(container) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LabelsViewModel(container.labels) as T
        }
    }
    val settingsFactory = remember(container) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(container.prefs, container.tokens, container.backupManager) as T
        }
    }
    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                vm = viewModel<HomeViewModel>(factory = homeFactory),
                onOpenExpenses = { nav.navigate(Routes.EXPENSES) },
                onOpenLabels = { nav.navigate(Routes.LABELS) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        // Placeholders; later tasks replace these.
        composable(Routes.EXPENSES) {
            ExpensesScreen(
                vm = viewModel<ExpensesViewModel>(factory = expensesFactory),
                onBack = { nav.popBackStack() },
                onOpenDay = { nav.navigate(Routes.day(it)) },
            )
        }
        composable(Routes.DAY) {
            val date = runCatching { LocalDate.parse(it.arguments?.getString("isoDate")) }.getOrNull()
            if (date == null) {
                LaunchedEffect(Unit) { nav.popBackStack() }
            } else {
                DayScreen(
                    vm = viewModel<ExpensesViewModel>(factory = expensesFactory),
                    date = date,
                    onBack = { nav.popBackStack() },
                )
            }
        }
        composable(Routes.LABELS) {
            LabelsScreen(vm = viewModel<LabelsViewModel>(factory = labelsFactory), onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                vm = viewModel<SettingsViewModel>(factory = settingsFactory),
                onBack = { nav.popBackStack() },
                onOpenLabels = { nav.navigate(Routes.LABELS) },
            )
        }
    }
}
