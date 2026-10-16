package io.gastos.app.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.gastos.app.GastosApp
import java.io.IOException

/** Runs one backup. Network trouble retries with backoff; anything else (bad token, no repo) stops. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val manager = (applicationContext as GastosApp).container.backupManager
        val error = manager.backupNow().exceptionOrNull() ?: return Result.success()
        return if (isRetryable(error)) Result.retry() else Result.failure()
    }

    private fun isRetryable(error: Throwable): Boolean =
        (error as? GitHubException)?.isRetryable == true ||
            generateSequence(error) { it.cause }.any { it is IOException }
}
