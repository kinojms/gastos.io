package io.gastos.app.backup

/** When Home nudges the owner to back up. Pure so it can be tested without the UI. */
object BackupReminder {
    const val DAY_MS = 86_400_000L
    private const val STALE_DAYS = 14

    /**
     * Shown when GitHub backup isn't set up, there is data to lose, the last file backup (or, with none,
     * the oldest expense) is at least 14 days old, and the owner hasn't dismissed it in the last day.
     */
    fun shouldShow(now: Long, hasGitHub: Boolean, lastFileBackupAt: Long?, oldestExpenseAt: Long?, dismissedAt: Long?): Boolean {
        if (hasGitHub || oldestExpenseAt == null) return false
        val since = lastFileBackupAt ?: oldestExpenseAt
        if (now - since < STALE_DAYS * DAY_MS) return false
        if (dismissedAt != null && now - dismissedAt < DAY_MS) return false
        return true
    }
}
