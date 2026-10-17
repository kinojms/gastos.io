package io.gastos.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.gastos.app.ui.components.VoiceLanguage
import io.gastos.app.ui.theme.ThemeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.prefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "prefs")

class AppPrefs(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.prefsDataStore)

    val lastLabelId: Flow<Long?> = store.data.map { it[LAST_LABEL_ID] }

    suspend fun setLastLabelId(id: Long) {
        store.edit { it[LAST_LABEL_ID] = id }
    }

    val lastPaymentMethodId: Flow<Long?> = store.data.map { it[LAST_PAYMENT_METHOD_ID] }

    suspend fun setLastPaymentMethodId(id: Long) {
        store.edit { it[LAST_PAYMENT_METHOD_ID] = id }
    }

    /** Backup repo as `"owner/name"`, or null when not set. */
    val backupRepo: Flow<String?> = store.data.map { it[BACKUP_REPO] }

    /** Off on a fresh install until the owner restores or chooses "Start fresh". */
    val backupEnabled: Flow<Boolean> = store.data.map { it[BACKUP_ENABLED] ?: false }

    /** Epoch millis of the last successful backup. */
    val lastBackupAt: Flow<Long?> = store.data.map { it[LAST_BACKUP_AT] }

    /** User-facing message of the last failed backup; null after a success. */
    val lastBackupError: Flow<String?> = store.data.map { it[LAST_BACKUP_ERROR] }

    /** Epoch millis of the last "Save backup file". */
    val lastFileBackupAt: Flow<Long?> = store.data.map { it[LAST_FILE_BACKUP_AT] }

    suspend fun setLastFileBackupAt(millis: Long) {
        store.edit { it[LAST_FILE_BACKUP_AT] = millis }
    }

    /** Epoch millis when the Home backup reminder was last dismissed. */
    val backupReminderDismissedAt: Flow<Long?> = store.data.map { it[BACKUP_REMINDER_DISMISSED_AT] }

    suspend fun setBackupReminderDismissedAt(millis: Long) {
        store.edit { it[BACKUP_REMINDER_DISMISSED_AT] = millis }
    }

    suspend fun setBackupRepo(repo: String?) {
        store.edit { if (repo == null) it.remove(BACKUP_REPO) else it[BACKUP_REPO] = repo }
    }

    suspend fun setBackupEnabled(enabled: Boolean) {
        store.edit { it[BACKUP_ENABLED] = enabled }
    }

    suspend fun setLastBackupAt(millis: Long) {
        store.edit { it[LAST_BACKUP_AT] = millis }
    }

    suspend fun setLastBackupError(message: String?) {
        store.edit { if (message == null) it.remove(LAST_BACKUP_ERROR) else it[LAST_BACKUP_ERROR] = message }
    }

    /** Device-local theme choice; missing or unknown values fall back to Classic. */
    val themeId: Flow<ThemeId> = store.data.map { prefs ->
        ThemeId.values().firstOrNull { it.name == prefs[THEME_ID] } ?: ThemeId.CLASSIC
    }

    suspend fun setThemeId(id: ThemeId) {
        store.edit { it[THEME_ID] = id.name }
    }

    /** Speech-recognition language; missing or unknown values fall back to English. */
    val voiceLanguage: Flow<VoiceLanguage> = store.data.map { prefs ->
        VoiceLanguage.values().firstOrNull { it.name == prefs[VOICE_LANGUAGE] } ?: VoiceLanguage.ENGLISH
    }

    suspend fun setVoiceLanguage(v: VoiceLanguage) {
        store.edit { it[VOICE_LANGUAGE] = v.name }
    }

    /** Highest guided-tour version the user has seen or skipped; 0 when never. */
    val guideSeenVersion: Flow<Int> = store.data.map { it[GUIDE_SEEN_VERSION] ?: 0 }

    suspend fun setGuideSeenVersion(v: Int) {
        store.edit { it[GUIDE_SEEN_VERSION] = v }
    }

    /** Last-used label if it still exists, else the first by position. */
    suspend fun resolveLabel(labels: List<LabelEntity>): LabelEntity {
        val last = lastLabelId.first()
        return labels.firstOrNull { it.id == last } ?: labels.first()
    }

    /** Last-used payment method if it is still active, else the first active one. [active] must not be empty. */
    suspend fun resolvePaymentMethod(active: List<PaymentMethodEntity>): PaymentMethodEntity {
        return LastUsed.method(active, lastPaymentMethodId.first())
            ?: throw NoSuchElementException("No active payment method")
    }

    private companion object {
        val VOICE_LANGUAGE = stringPreferencesKey("voice_language")
        val GUIDE_SEEN_VERSION = intPreferencesKey("guide_seen_version")
        val THEME_ID = stringPreferencesKey("theme_id")
        val LAST_LABEL_ID = longPreferencesKey("last_label_id")
        val LAST_PAYMENT_METHOD_ID = longPreferencesKey("last_payment_method_id")
        val BACKUP_REPO = stringPreferencesKey("backup_repo")
        val BACKUP_ENABLED = booleanPreferencesKey("backup_enabled")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val LAST_BACKUP_ERROR = stringPreferencesKey("last_backup_error")
        val LAST_FILE_BACKUP_AT = longPreferencesKey("last_file_backup_at")
        val BACKUP_REMINDER_DISMISSED_AT = longPreferencesKey("backup_reminder_dismissed_at")
    }
}
