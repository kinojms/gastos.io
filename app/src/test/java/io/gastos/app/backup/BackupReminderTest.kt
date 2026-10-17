package io.gastos.app.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupReminderTest {
    private val day = BackupReminder.DAY_MS
    private val now = 100 * day

    private fun show(
        hasGitHub: Boolean = false,
        lastFile: Long? = null,
        oldest: Long? = now - 30 * day,
        dismissed: Long? = null,
    ) = BackupReminder.shouldShow(now, hasGitHub, lastFile, oldest, dismissed)

    @Test fun hiddenWithNoExpenses() = assertFalse(show(oldest = null))

    @Test fun hiddenWhenGitHubIsSetUp() = assertFalse(show(hasGitHub = true))

    @Test fun hiddenWhenOldestExpenseIs13DaysOld() = assertFalse(show(oldest = now - 13 * day))

    @Test fun shownWhenOldestExpenseIs14DaysOld() = assertTrue(show(oldest = now - 14 * day))

    @Test fun hiddenAfterRecentFileBackup() = assertFalse(show(lastFile = now - 3 * day))

    @Test fun shownWhenFileBackupIs15DaysOld() = assertTrue(show(lastFile = now - 15 * day))

    @Test fun hiddenWhenDismissedHoursAgo() = assertFalse(show(dismissed = now - day / 12))

    @Test fun shownWhenDismissed25HoursAgo() = assertTrue(show(dismissed = now - 25 * day / 24))
}
