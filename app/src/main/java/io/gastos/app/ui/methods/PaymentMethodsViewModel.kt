package io.gastos.app.ui.methods

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.gastos.app.data.PaymentMethodEntity
import io.gastos.app.data.PaymentMethodRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PaymentMethodsViewModel(private val repo: PaymentMethodRepository) : ViewModel() {
    /** Active methods as stored (position order). Archived ones are not managed here. */
    val methods: StateFlow<List<PaymentMethodEntity>> =
        repo.observeActive().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Local, unsaved drag order (method ids), or null when untouched. */
    private val localOrder = MutableStateFlow<List<Long>?>(null)

    private fun applyOrder(stored: List<PaymentMethodEntity>, order: List<Long>?): List<PaymentMethodEntity> {
        if (order == null) return stored
        val byId = stored.associateBy { it.id }
        return order.mapNotNull { byId[it] } + stored.filter { it.id !in order }
    }

    /** Methods as shown: stored order with any unsaved drag order applied. */
    val displayed: StateFlow<List<PaymentMethodEntity>> = combine(methods, localOrder, ::applyOrder)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun currentOrder(): List<PaymentMethodEntity> = applyOrder(methods.value, localOrder.value)

    fun orderChanged(shown: List<PaymentMethodEntity> = currentOrder()): Boolean =
        methods.value.map { it.id } != shown.map { it.id }

    fun move(from: Int, to: Int) {
        val current = currentOrder().map { it.id }.toMutableList()
        if (from !in current.indices || to !in current.indices) return
        current.add(to, current.removeAt(from))
        localOrder.value = current
    }

    /** Always sends the full active id list, so positions stay contiguous. */
    suspend fun saveOrder() {
        repo.reorder(currentOrder().map { it.id })
        localOrder.value = null
    }

    val error = MutableStateFlow<String?>(null)

    fun clearError() { error.value = null }

    private fun launchWrite(block: suspend () -> String?) {
        viewModelScope.launch { withContext(NonCancellable) { block() }?.let { error.value = it } }
    }

    // Fire-and-forget variants run NonCancellable so leaving the screen cannot drop a confirmed write.
    fun addAsync(name: String, colorArgb: Int) = launchWrite { add(name, colorArgb) }
    fun renameAsync(id: Long, name: String) = launchWrite { rename(id, name) }
    fun recolorAsync(id: Long, colorArgb: Int) = launchWrite { recolor(id, colorArgb); null }
    fun saveOrderAsync() = launchWrite { saveOrder(); null }
    fun deleteAsync(method: PaymentMethodEntity) = launchWrite { delete(method) }

    fun discardOrder() { localOrder.value = null }

    /** Delete is hidden when only one active method remains. */
    fun canDelete(): Boolean = methods.value.size > 1

    /** Returns an error message, or null when [name] is acceptable. Only active methods can clash. */
    fun validateName(name: String, excludeId: Long?): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "Name can't be empty"
        val clash = methods.value.any { it.id != excludeId && it.name.equals(trimmed, ignoreCase = true) }
        return if (clash) "That payment method already exists" else null
    }

    suspend fun add(name: String, colorArgb: Int): String? {
        validateName(name, null)?.let { return it }
        repo.add(name.trim(), colorArgb)
        return null
    }

    suspend fun rename(id: Long, name: String): String? {
        validateName(name, id)?.let { return it }
        repo.rename(id, name.trim())
        return null
    }

    suspend fun recolor(id: Long, colorArgb: Int) = repo.recolor(id, colorArgb)

    /** Archives the method. Returns an error message if it cannot be removed. */
    suspend fun delete(method: PaymentMethodEntity): String? {
        if (!canDelete()) return null
        return try {
            repo.archive(method.id)
            localOrder.value = localOrder.value?.filter { it != method.id }
            null
        } catch (e: IllegalStateException) {
            "At least one payment method must stay"
        }
    }
}
