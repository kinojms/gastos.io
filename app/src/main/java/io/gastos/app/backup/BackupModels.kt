package io.gastos.app.backup

import kotlinx.serialization.Serializable

/** The whole backup: one JSON file in the owner's private GitHub repo. */
@Serializable
data class BackupFile(
    val schemaVersion: Int = 1,
    val exportedAt: String, // ISO-8601 instant
    val labels: List<BackupLabel>,
    val expenses: List<BackupExpense>,
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
)

class UnsupportedBackupException(message: String) : Exception(message)
