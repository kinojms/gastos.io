package io.gastos.app.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Debounced backups: each change pushes the pending backup 3 minutes out. */
object BackupScheduler {
    const val WORK_NAME = "gastos-backup"

    fun schedule(context: Context) = enqueue(context, delayMinutes = 3)

    fun runNow(context: Context) = enqueue(context, delayMinutes = 0)

    private fun enqueue(context: Context, delayMinutes: Long) {
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .apply { if (delayMinutes > 0) setInitialDelay(delayMinutes, TimeUnit.MINUTES) }
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
