package io.gastos.app.ui.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.gastos.app.feedback.FeedbackClient
import io.gastos.app.feedback.FeedbackPayload
import io.gastos.app.feedback.FeedbackType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FeedbackViewModel(private val client: FeedbackClient, private val appVersion: String) : ViewModel() {
    private val _type = MutableStateFlow(FeedbackType.SUGGESTION)
    val type: StateFlow<FeedbackType> = _type.asStateFlow()

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    private val _contact = MutableStateFlow("")
    val contact: StateFlow<String> = _contact.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Why the last send failed; cleared when the user edits the message. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val canSend: StateFlow<Boolean> = combine(_message, _busy) { m, b -> m.isNotBlank() && !b }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun onType(t: FeedbackType) { _type.value = t }

    fun onMessage(text: String) {
        _message.value = text.take(MESSAGE_MAX)
        _error.value = null
    }

    fun onContact(text: String) { _contact.value = text.take(CONTACT_MAX) }

    /** Call after the user confirmed. Never throws; a failure keeps the text and sets [error]. */
    fun send(onSent: () -> Unit): Job = viewModelScope.launch {
        if (_busy.value || _message.value.isBlank()) return@launch
        _busy.value = true
        _error.value = null
        val payload = FeedbackPayload(_type.value.label, _message.value.trim(), _contact.value.trim(), appVersion)
        val result = try {
            client.send(payload)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        _busy.value = false
        if (result.isSuccess) {
            _type.value = FeedbackType.SUGGESTION
            _message.value = ""
            _contact.value = ""
            onSent()
        } else {
            val e = result.exceptionOrNull()
            _error.value = if (e is io.gastos.app.feedback.FeedbackException) e.message else FeedbackClient.FAILED
        }
    }

    companion object {
        const val MESSAGE_MAX = 2000
        const val CONTACT_MAX = 100
    }
}
