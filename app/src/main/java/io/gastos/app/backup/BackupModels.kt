package io.gastos.app.backup

import kotlinx.serialization.Serializable

/** The whole backup: one JSON file in the owner's private GitHub repo. */
@Serializable
data class BackupFile(
    val schemaVersion: Int = BackupSerializer.CURRENT,
    val exportedAt: String, // ISO-8601 instant
    val labels: List<BackupLabel>,
    val expenses: List<BackupExpense>,
    /** Schema 2+, archived included. Empty in schema 1 files: restore then creates the six defaults. */
    val paymentMethods: List<BackupPaymentMethod> = emptyList(),
)

/** Mirrors [io.gastos.app.data.LabelEntity]. */
@Serializable
data class BackupLabel(
    val id: Long,
    val name: String,
    val colorArgb: Int,
    val position: Int,
    val isOther: Boolean = false,
)

/** Mirrors [io.gastos.app.data.ExpenseEntity]. */
@Serializable
data class BackupExpense(
    val id: Long,
    val amountCentavos: Long,
    val labelId: Long,
    val timestamp: Long, // epoch millis
    val paymentMethodId: Long? = null, // null in schema 1 files
    val description: String? = null,
    val notes: String? = null,
)

/** Mirrors [io.gastos.app.data.PaymentMethodEntity]. */
@Serializable
data class BackupPaymentMethod(
    val id: Long,
    val name: String,
    val colorArgb: Int,
    val position: Int,
    val archived: Boolean = false,
)

/** What a backup file holds, shown before the user confirms replacing local data with it. */
data class BackupPreview(val expenseCount: Int, val exportedAt: java.time.Instant)

class UnsupportedBackupException(message: String) : Exception(message)
