package io.gastos.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.gastos.app.AppContainer
import io.gastos.app.ui.home.HomeScreen
import io.gastos.app.ui.home.HomeViewModel

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
        composable(Routes.EXPENSES) { Text("Expenses") }
        composable(Routes.DAY) { Text("Day ${it.arguments?.getString("isoDate")}") }
        composable(Routes.LABELS) { Text("Labels") }
        composable(Routes.SETTINGS) { Text("Settings") }
    }
}
