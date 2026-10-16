package io.gastos.app.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import io.gastos.app.domain.ExpenseText

/** Speech-recognition language; [tag] goes to `EXTRA_LANGUAGE`. Stored by enum name. */
enum class VoiceLanguage(val tag: String, val displayName: String) {
    ENGLISH("en-PH", "English (Philippines)"),
    TAGALOG("fil-PH", "Tagalog"),
}

object VoiceInput {
    fun intent(lang: VoiceLanguage): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.tag)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "What did you buy?")

    fun isAvailable(context: Context): Boolean = try {
        intent(VoiceLanguage.ENGLISH).resolveActivity(context.packageManager) != null ||
            SpeechRecognizer.isRecognitionAvailable(context)
    } catch (e: RuntimeException) {
        false
    }

    fun firstResult(data: Intent?): String? =
        data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
}

/** Returns a launcher for the system speech dialog, or null when the device has no recognizer. */
@Composable
fun rememberVoiceInput(lang: VoiceLanguage, onText: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val currentOnText = rememberUpdatedState(onText)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            ExpenseText.description(VoiceInput.firstResult(result.data))?.let(currentOnText.value)
        }
    }
    val available = remember(context) { VoiceInput.isAvailable(context) }
    if (!available) return null
    return {
        try {
            launcher.launch(VoiceInput.intent(lang))
        } catch (e: ActivityNotFoundException) {
            // Recognizer vanished between the check and the launch; nothing to do.
        }
    }
}
