package io.gastos.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

    /** Last-used label if it still exists, else the first by position. */
    suspend fun resolveLabel(labels: List<LabelEntity>): LabelEntity {
        val last = lastLabelId.first()
        return labels.firstOrNull { it.id == last } ?: labels.first()
    }

    private companion object {
        val LAST_LABEL_ID = longPreferencesKey("last_label_id")
    }
}
