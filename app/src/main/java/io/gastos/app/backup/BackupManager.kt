package io.gastos.app.backup

import androidx.room.withTransaction
import io.gastos.app.data.AppPrefs
import io.gastos.app.data.DEFAULT_PAYMENT_METHODS
import io.gastos.app.data.ExpenseEntity
import io.gastos.app.data.GastosDatabase
import io.gastos.app.data.LabelEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import java.time.Clock
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A backup or restore could not proceed; [message] is shown to the owner as-is. */
class BackupException(message: String) : Exception(message)

/** Backs the whole database up to `backup.json` in the owner's GitHub repo, and restores it. */
class BackupManager(
    private val db: GastosDatabase,
    private val prefs: AppPrefs,
    private val tokens: TokenStore,
    private val github: GitHubClient,
    private val clock: Clock,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val onDataChanged: () -> Unit,
) {
    /** Serialises backup and restore so the worker and the manual button never overlap. */
    private val mutex = Mutex()

    /** Uploads all data. Does nothing (no network) while backup is disabled, e.g. on a fresh install. */
    suspend fun backupNow(): Result<Unit> = mutex.withLock {
        if (!prefs.backupEnabled.first()) return@withLock Result.success(Unit)
        val result = attempt {
            val (repo, token) = credentials()
            val (labels, expenses) = db.withTransaction { db.labelDao().all() to db.expenseDao().all() }
            val json = BackupSerializer.encode(toBackup(labels, expenses))
            val sha = github.getSha(repo, PATH, token)
            github.upload(repo, PATH, token, json, sha, commitMessage())
        }
        if (result.isSuccess) {
            prefs.setLastBackupAt(clock.millis())
            prefs.setLastBackupError(null)
        } else {
            prefs.setLastBackupError(result.exceptionOrNull()?.message ?: "Backup failed.")
        }
        result
    }

    /**
     * Replaces local data with the repo's backup. The file is downloaded, decoded and validated
     * before the database is touched, so any failure leaves local data as it was.
     */
    suspend fun restore(): Result<Unit> = mutex.withLock {
        attempt {
            val (repo, token) = credentials()
            val json = github.download(repo, PATH, token) ?: throw BackupException("No backup found in this repo.")
            val file = try {
                BackupSerializer.decode(json)
            } catch (e: SerializationException) {
                throw BackupException(DAMAGED)
            }
            val labels = file.labels.map { LabelEntity(it.id, it.name, it.colorArgb, it.position, it.isOther) }
            // Backup schema 1 has no payment methods: every restored expense goes to Cash (first active as fallback).
            val methodId = defaultPaymentMethodId()
            val expenses = file.expenses.map {
                ExpenseEntity(it.id, it.amountCentavos, it.labelId, it.timestamp, paymentMethodId = methodId)
            }
            validate(labels, expenses)
            db.replaceAll(labels, expenses)
            prefs.setBackupEnabled(true)
            prefs.setLastBackupError(null)
            onDataChanged()
        }
    }

    /** The owner chose to keep this phone's data: turn backup on and upload right away. */
    suspend fun startFresh(): Result<Unit> {
        prefs.setBackupEnabled(true)
        return backupNow()
    }

    private suspend fun credentials(): Pair<String, String> {
        val repo = prefs.backupRepo.first()?.takeIf { it.isNotBlank() }
            ?: throw BackupException("Set the GitHub repo first.")
        val token = tokens.load() ?: throw BackupException("Add your GitHub token first.")
        return repo to token
    }

    private suspend fun defaultPaymentMethodId(): Long {
        val active = db.paymentMethodDao().active()
        return (active.firstOrNull { it.name == DEFAULT_PAYMENT_METHODS.first().first } ?: active.firstOrNull())?.id
            ?: throw BackupException("No payment method to restore into.")
    }

    private fun toBackup(labels: List<LabelEntity>, expenses: List<ExpenseEntity>) = BackupFile(
        schemaVersion = BackupSerializer.CURRENT,
        exportedAt = clock.instant().toString(),
        labels = labels.map { BackupLabel(it.id, it.name, it.colorArgb, it.position, it.isOther) },
        expenses = expenses.map { BackupExpense(it.id, it.amountCentavos, it.labelId, it.timestamp) },
    )

    /** Rejects files that would leave the app in a broken state (no Other label, orphaned expenses). */
    private fun validate(labels: List<LabelEntity>, expenses: List<ExpenseEntity>) {
        val labelIds = labels.map { it.id }.toSet()
        val ok = labels.count { it.isOther } == 1 &&
            labelIds.size == labels.size && labelIds.none { it <= 0 } &&
            expenses.map { it.id }.toSet().size == expenses.size && expenses.none { it.id <= 0 } &&
            expenses.all { it.labelId in labelIds }
        if (!ok) throw BackupException(DAMAGED)
    }

    private fun commitMessage(): String =
        "Backup " + COMMIT_TIME.format(clock.instant().atZone(zone()))

    /** Like runCatching, but lets coroutine cancellation propagate. */
    private inline fun attempt(block: () -> Unit): Result<Unit> = try {
        block()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    companion object {
        const val PATH = "backup.json"
        private const val DAMAGED = "This backup file is damaged or isn't a Gastos.io backup."
        private val COMMIT_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
