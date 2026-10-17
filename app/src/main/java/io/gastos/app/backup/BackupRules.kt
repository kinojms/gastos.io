package io.gastos.app.backup

/** Pure rules for when GitHub backup must pause. */
object BackupRules {
    /**
     * True when saving settings points backup somewhere new: a token was typed, or the repo differs
     * from [savedRepo] (ignoring case and spaces). Auto-backup then stays off until the owner restores or starts fresh,
     * so a repo that already holds a backup is never silently overwritten.
     */
    fun credentialsChanged(savedRepo: String?, newRepo: String, tokenTyped: Boolean): Boolean =
        tokenTyped || !newRepo.trim().equals(savedRepo?.trim(), ignoreCase = true)
}
