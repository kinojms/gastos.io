package io.gastos.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.gastos.app.backup.BackupManager
import io.gastos.app.backup.BackupPreview
import io.gastos.app.backup.BackupRules
import io.gastos.app.backup.GitHubException
import io.gastos.app.backup.TokenStore
import io.gastos.app.data.AppPrefs
import io.gastos.app.ui.components.VoiceLanguage
import io.gastos.app.ui.theme.ThemeId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Developer details shown in Settings. */
object AboutDev {
    const val NAME = "Justin Sacdalan"
    const val TAGLINE = "keep on keeping on"
    const val GITHUB = "https://github.com/kinojms"
    const val LINKEDIN = "https://www.linkedin.com/in/justin-sacdalan"
}

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
    private val clock: Clock = Clock.systemUTC(),
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

    val themeId: StateFlow<ThemeId> =
        prefs.themeId.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeId.CLASSIC)

    val voiceLanguage: StateFlow<VoiceLanguage> =
        prefs.voiceLanguage.stateIn(viewModelScope, SharingStarted.Eagerly, VoiceLanguage.ENGLISH)

    private val savedRepo: StateFlow<String?> =
        prefs.backupRepo.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Repo and token are both saved, so backup actions can run. */
    val canUseBackup: StateFlow<Boolean> = combine(savedRepo, tokenSaved) { repo, token ->
        !repo.isNullOrBlank() && token
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val status: StateFlow<BackupStatus> = combine(prefs.lastBackupAt, prefs.lastBackupError) { at, error ->
        formatStatus(at, error, zone())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BackupStatus(NOT_BACKED_UP, false))

    val fileStatus: StateFlow<String> = prefs.lastFileBackupAt.map { at ->
        if (at == null) NO_FILE_BACKUP else "Last file backup: " + TIME_FORMAT.format(Instant.ofEpochMilli(at).atZone(zone()))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, NO_FILE_BACKUP)

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
                val changed = BackupRules.credentialsChanged(prefs.backupRepo.first(), repo, token.isNotEmpty())
                prefs.setBackupRepo(repo)
                // New or changed credentials pause auto-backup until Restore or Start fresh.
                if (changed) prefs.setBackupEnabled(false)
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

    /** Call after the user confirmed. No-op when [id] is already the saved theme. */
    fun setTheme(id: ThemeId): Job = viewModelScope.launch {
        withContext(NonCancellable) {
            if (prefs.themeId.first() != id) prefs.setThemeId(id)
        }
    }

    /** Call after the user confirmed. No-op when [v] is already the saved language. */
    fun setVoiceLanguage(v: VoiceLanguage): Job = viewModelScope.launch {
        withContext(NonCancellable) {
            if (prefs.voiceLanguage.first() != v) prefs.setVoiceLanguage(v)
        }
    }

    fun backupFileName(today: LocalDate): String = "gastos-backup-$today.json"

    suspend fun fileBackupJson(): String = backup.exportJson()

    /** Call after the backup file was written. */
    fun onFileSaved(): Job = viewModelScope.launch {
        prefs.setLastFileBackupAt(clock.millis())
        result.value = ActionResult("Backup file saved", false)
    }

    suspend fun previewFile(text: String): Result<BackupPreview> = backup.preview(text)

    /** Call after the user confirmed. */
    fun restoreFromFile(text: String): Job = run("Restored") { backup.restoreFromJson(text) }

    fun showFileError(message: String) {
        result.value = ActionResult(message, true)
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
        const val NO_FILE_BACKUP = "No backup file yet"
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
