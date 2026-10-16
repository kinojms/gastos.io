package io.gastos.app.backup

import androidx.room.withTransaction
import io.gastos.app.data.AppPrefs
import io.gastos.app.data.DEFAULT_PAYMENT_METHODS
import io.gastos.app.data.ExpenseEntity
import io.gastos.app.data.GastosDatabase
import io.gastos.app.data.LabelEntity
import io.gastos.app.data.PaymentMethodEntity
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
            val file = db.withTransaction {
                toBackup(db.labelDao().all(), db.paymentMethodDao().all(), db.expenseDao().all())
            }
            val json = BackupSerializer.encode(file)
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
            val (methods, expenses) = if (file.paymentMethods.isEmpty()) {
                // Schema 1 has no payment methods: restore the six defaults (Cash = id 1) and put
                // every expense on Cash, independent of whatever methods this phone has now.
                val defaults = DEFAULT_PAYMENT_METHODS.mapIndexed { i, (name, color) ->
                    PaymentMethodEntity(id = i + 1L, name = name, colorArgb = color, position = i, archived = false)
                }
                defaults to file.expenses.map { it.toEntity(defaults.first().id) }
            } else {
                val methods = file.paymentMethods.map { PaymentMethodEntity(it.id, it.name, it.colorArgb, it.position, it.archived) }
                methods to file.expenses.map { it.toEntity(it.paymentMethodId ?: throw BackupException(DAMAGED)) }
            }
            validate(labels, methods, expenses)
            db.replaceAll(labels, methods, expenses)
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

    private fun BackupExpense.toEntity(methodId: Long) =
        ExpenseEntity(id, amountCentavos, labelId, timestamp, methodId, description, notes)

    private fun toBackup(labels: List<LabelEntity>, methods: List<PaymentMethodEntity>, expenses: List<ExpenseEntity>) =
        BackupFile(
            schemaVersion = BackupSerializer.CURRENT,
            exportedAt = clock.instant().toString(),
            labels = labels.map { BackupLabel(it.id, it.name, it.colorArgb, it.position, it.isOther) },
            paymentMethods = methods.map { BackupPaymentMethod(it.id, it.name, it.colorArgb, it.position, it.archived) },
            expenses = expenses.map {
                BackupExpense(it.id, it.amountCentavos, it.labelId, it.timestamp, it.paymentMethodId, it.description, it.notes)
            },
        )

    /**
     * Rejects files that would leave the app in a broken state: no single Other label, no active
     * payment method, duplicate or non-positive ids, or expenses pointing at a missing label or method.
     */
    private fun validate(labels: List<LabelEntity>, methods: List<PaymentMethodEntity>, expenses: List<ExpenseEntity>) {
        val labelIds = labels.map { it.id }.toSet()
        val methodIds = methods.map { it.id }.toSet()
        val ok = labels.count { it.isOther } == 1 &&
            labelIds.size == labels.size && labelIds.none { it <= 0 } &&
            methodIds.size == methods.size && methodIds.none { it <= 0 } && methods.any { !it.archived } &&
            expenses.map { it.id }.toSet().size == expenses.size && expenses.none { it.id <= 0 } &&
            expenses.all { it.labelId in labelIds && it.paymentMethodId in methodIds }
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
