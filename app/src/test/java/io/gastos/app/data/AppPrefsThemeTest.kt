package io.gastos.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.gastos.app.ui.theme.ThemeId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

class AppPrefsThemeTest {
    private lateinit var scope: CoroutineScope
    private lateinit var file: File

    @Before fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        file = File.createTempFile("prefs", ".preferences_pb").also { it.delete() }
    }

    @After fun tearDown() {
        scope.cancel()
        file.delete()
    }

    @Test fun themePrefDefaultsAndPersists() = runBlocking {
        val store = PreferenceDataStoreFactory.create(scope = scope) { file }
        val prefs = AppPrefs(store)
        assertEquals(ThemeId.CLASSIC, prefs.themeId.first())
        prefs.setThemeId(ThemeId.NORD)
        assertEquals(ThemeId.NORD, prefs.themeId.first())
        store.edit { it[stringPreferencesKey("theme_id")] = "BOGUS" }
        assertEquals(ThemeId.CLASSIC, prefs.themeId.first())
    }
}
