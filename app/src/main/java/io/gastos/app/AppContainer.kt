package io.gastos.app

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import io.gastos.app.backup.BackupManager
import io.gastos.app.backup.BackupScheduler
import io.gastos.app.backup.GitHubClient
import io.gastos.app.backup.TokenStore
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
import okhttp3.OkHttpClient
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

    /** GitHub token, encrypted with the Android Keystore (touched lazily, on save/load only). */
    val tokens: TokenStore = TokenStore(context)
    val backupManager: BackupManager =
        BackupManager(db, prefs, tokens, GitHubClient(OkHttpClient()), Clock.systemUTC()) { onDataChanged() }

    /** Called after every write. Refreshes the widget and schedules a debounced backup. */
    fun onDataChanged() {
        try {
            // backupNow() is a no-op while backup is disabled, so scheduling is always safe.
            BackupScheduler.schedule(appContext)
        } catch (e: IllegalStateException) {
            Log.w("Gastos", "Backup scheduling unavailable", e) // WorkManager not initialised (tests)
        }
        refreshWidgets()
    }

    /** Saves the shared last-used label, then re-renders every widget so it shows the same label as Home. */
    suspend fun setLastLabel(id: Long) {
        prefs.setLastLabelId(id)
        refreshWidgets()
    }

    private fun refreshWidgets() {
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
