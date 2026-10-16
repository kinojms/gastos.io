package io.gastos.app.ui.components

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.gastos.app.data.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class VoiceInputTest {
    @Test fun intentCarriesLanguage() {
        val intent = VoiceInput.intent(VoiceLanguage.TAGALOG)
        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, intent.action)
        assertEquals("fil-PH", intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertEquals(RecognizerIntent.LANGUAGE_MODEL_FREE_FORM, intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL))
        assertEquals("What did you buy?", intent.getStringExtra(RecognizerIntent.EXTRA_PROMPT))
    }

    @Test fun firstResultPicksFirst() {
        val data = Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf("Jollibee", "Jolly bee"))
        assertEquals("Jollibee", VoiceInput.firstResult(data))
        assertNull(VoiceInput.firstResult(null))
        assertNull(VoiceInput.firstResult(Intent()))
    }

    @Test fun voicePrefDefaultsEnglish() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File.createTempFile("prefs", ".preferences_pb").also { it.delete() }
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { file }
            val prefs = AppPrefs(store)
            assertEquals(VoiceLanguage.ENGLISH, prefs.voiceLanguage.first())
            prefs.setVoiceLanguage(VoiceLanguage.TAGALOG)
            assertEquals(VoiceLanguage.TAGALOG, prefs.voiceLanguage.first())
            store.edit { it[stringPreferencesKey("voice_language")] = "BOGUS" }
            assertEquals(VoiceLanguage.ENGLISH, prefs.voiceLanguage.first())
        } finally {
            scope.cancel()
            file.delete()
        }
    }
}
