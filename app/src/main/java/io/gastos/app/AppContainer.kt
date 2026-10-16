package io.gastos.app

import android.content.Context
import io.gastos.app.data.AppPrefs
import io.gastos.app.data.ExpenseRepository
import io.gastos.app.data.GastosDatabase
import io.gastos.app.data.LabelRepository
import java.time.Clock
import java.time.ZoneId

class AppContainer(context: Context) {
    val db: GastosDatabase = GastosDatabase.create(context)
    val prefs: AppPrefs = AppPrefs(context)
    val expenses: ExpenseRepository =
        ExpenseRepository(db, Clock.systemUTC(), { ZoneId.systemDefault() }, ::onDataChanged)
    val labels: LabelRepository = LabelRepository(db, ::onDataChanged)

    /** Called after every write. Widget refresh (Task 8) and backup scheduling (Task 9) hook in here. */
    fun onDataChanged() {
    }
}
