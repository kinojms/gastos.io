package io.gastos.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.gastos.app.backup.BackupManager
import io.gastos.app.backup.GitHubException
import io.gastos.app.backup.TokenStore
import io.gastos.app.data.AppPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The status line under the backup fields. */
data class BackupStatus(val text: String, val isError: Boolean)

/** Outcome of the last Back up / Restore / Start fresh action. */
data class ActionResult(val text: String, val isError: Boolean)

class SettingsViewModel(
    private val prefs: AppPrefs,
    private val tokens: TokenStore,
    private val backup: BackupManager,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    val repoInput = MutableStateFlow("")

    /** Never pre-filled: the saved token is not shown. */
    val tokenInput = MutableStateFlow("")
    val repoError = MutableStateFlow<String?>(null)

    private val tokenSaved = MutableStateFlow(false)
    val hasToken: StateFlow<Boolean> = tokenSaved

    /** True while Back up now / Restore / Start fresh is running. */
    val busy = MutableStateFlow(false)
    val result = MutableStateFlow<ActionResult?>(null)

    val backupEnabled: StateFlow<Boolean> =
        prefs.backupEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val savedRepo: StateFlow<String?> =
        prefs.backupRepo.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Repo and token are both saved, so backup actions can run. */
    val canUseBackup: StateFlow<Boolean> = combine(savedRepo, tokenSaved) { repo, token ->
        !repo.isNullOrBlank() && token
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val status: StateFlow<BackupStatus> = combine(prefs.lastBackupAt, prefs.lastBackupError) { at, error ->
        formatStatus(at, error, zone())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BackupStatus(NOT_BACKED_UP, false))

    init {
        viewModelScope.launch {
            val saved = prefs.backupRepo.first().orEmpty()
            if (repoInput.value.isEmpty()) repoInput.value = saved // don't clobber early typing
            // Only ever raise the flag: a Save that finished while this load ran must not be clobbered.
            if (withContext(io) { tokens.load() != null }) tokenSaved.value = true
        }
    }

    /** Validates the repo field; sets [repoError] and returns false when it is malformed. */
    fun validate(): Boolean {
        val ok = REPO_PATTERN.matches(repoInput.value.trim())
        repoError.value = if (ok) null else REPO_ERROR
        return ok
    }

    /** Saves repo and, if one was typed, the token. An empty token field keeps the existing token. */
    fun save(): Job? {
        if (busy.value || !validate()) return null
        val repo = repoInput.value.trim()
        val token = tokenInput.value.trim()
        return viewModelScope.launch {
            try {
                // Token first, so a failed token write never leaves a repo saved without its token.
                if (token.isNotEmpty()) withContext(io) { tokens.save(token) }
                prefs.setBackupRepo(repo)
                if (token.isNotEmpty()) {
                    tokenSaved.value = true
                    tokenInput.value = ""
                }
                result.value = ActionResult("Saved", false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                result.value = ActionResult("Couldn't save settings.", true)
            }
        }
    }

    fun backUpNow(): Job = run("Backed up") { backup.backupNow() }

    fun restore(): Job = run("Restored") { backup.restore() }

    fun startFresh(): Job = run("Backed up") { backup.startFresh() }

    private fun run(success: String, action: suspend () -> Result<Unit>): Job {
        if (!busy.compareAndSet(expect = false, update = true)) return Job().apply { complete() }
        result.value = null
        return viewModelScope.launch {
            try {
                val r = action()
                result.value = r.exceptionOrNull()?.let { ActionResult(failureText(it), true) }
                    ?: ActionResult(success, false)
            } finally {
                busy.value = false
            }
        }
    }

    private fun failureText(e: Throwable): String =
        (e as? GitHubException)?.userMessage ?: e.message ?: "Something went wrong."

    companion object {
        const val NOT_BACKED_UP = "Not backed up yet"
        const val REPO_ERROR = "Use the format owner/repo"
        private val REPO_PATTERN = Regex("^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$")
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US)

        /** A failed last backup wins over the timestamp; with neither, "Not backed up yet". */
        fun formatStatus(lastBackupAt: Long?, error: String?, zone: ZoneId): BackupStatus = when {
            error != null -> BackupStatus(error, true)
            lastBackupAt != null ->
                BackupStatus("Last backup: " + TIME_FORMAT.format(Instant.ofEpochMilli(lastBackupAt).atZone(zone)), false)
            else -> BackupStatus(NOT_BACKED_UP, false)
        }
    }
}
