package io.gastos.app

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import io.gastos.app.data.AppPrefs
import io.gastos.app.data.ExpenseRepository
import io.gastos.app.data.GastosDatabase
import io.gastos.app.data.LabelRepository
import io.gastos.app.export.ExportService
import io.gastos.app.widget.GastosWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId

class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext

    /** App-lifetime scope for fire-and-forget work such as widget refreshes. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val db: GastosDatabase = GastosDatabase.create(context)
    val prefs: AppPrefs = AppPrefs(context)
    val expenses: ExpenseRepository =
        ExpenseRepository(db, Clock.systemUTC(), { ZoneId.systemDefault() }, ::onDataChanged)
    val labels: LabelRepository = LabelRepository(db, ::onDataChanged)
    val exportService: ExportService = ExportService(context.applicationContext, expenses)

    /** Called after every write. Refreshes the widget; backup scheduling (Task 9) hooks in here too. */
    fun onDataChanged() {
        appScope.launch {
            try {
                GastosWidget().updateAll(appContext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Gastos", "Widget refresh failed", e)
            }
        }
    }
}
