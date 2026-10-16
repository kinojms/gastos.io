package io.gastos.app.ui.labels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.gastos.app.data.LabelEntity
import io.gastos.app.data.LabelRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.stateIn

class LabelsViewModel(private val repo: LabelRepository) : ViewModel() {
    /** Labels as stored (position order). */
    val labels: StateFlow<List<LabelEntity>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Local, unsaved drag order (label ids), or null when untouched. */
    private val localOrder = MutableStateFlow<List<Long>?>(null)

    private fun applyOrder(stored: List<LabelEntity>, order: List<Long>?): List<LabelEntity> {
        if (order == null) return stored
        val byId = stored.associateBy { it.id }
        return order.mapNotNull { byId[it] } + stored.filter { it.id !in order }
    }

    /** Labels as shown: stored order with any unsaved drag order applied. */
    val displayed: StateFlow<List<LabelEntity>> = combine(labels, localOrder, ::applyOrder)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun currentOrder(): List<LabelEntity> = applyOrder(labels.value, localOrder.value)

    /** True when the dragged order differs from the stored one. */
    fun orderChanged(shown: List<LabelEntity> = currentOrder()): Boolean =
        labels.value.map { it.id } != shown.map { it.id }

    fun move(from: Int, to: Int) {
        val current = currentOrder().map { it.id }.toMutableList()
        if (from !in current.indices || to !in current.indices) return
        current.add(to, current.removeAt(from))
        localOrder.value = current
    }

    suspend fun saveOrder() {
        val order = currentOrder().map { it.id }
        repo.reorder(order)
        localOrder.value = null
    }

    /** Error from a confirmed write that failed validation; shown by the screen until [clearError]. */
    val error = MutableStateFlow<String?>(null)

    fun clearError() { error.value = null }

    private fun launchWrite(block: suspend () -> String?) {
        viewModelScope.launch { withContext(NonCancellable) { block() }?.let { error.value = it } }
    }

    // Fire-and-forget variants: the write runs NonCancellable, so leaving the screen (which cancels
    // viewModelScope) cannot drop a confirmed write.
    fun addAsync(name: String, colorArgb: Int) = launchWrite { add(name, colorArgb) }
    fun renameAsync(id: Long, name: String) = launchWrite { rename(id, name) }
    fun recolorAsync(id: Long, colorArgb: Int) = launchWrite { recolor(id, colorArgb); null }
    fun saveOrderAsync() = launchWrite { saveOrder(); null }
    fun deleteAsync(label: LabelEntity) = launchWrite { delete(label); null }
    fun loadExpenseCount(id: Long, onResult: (Int) -> Unit) {
        viewModelScope.launch { onResult(expenseCount(id)) }
    }

    fun discardOrder() { localOrder.value = null }

    fun canDelete(label: LabelEntity): Boolean = !label.isOther

    /** Returns an error message, or null when [name] is acceptable. [excludeId] is the label being edited. */
    fun validateName(name: String, excludeId: Long?): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "Name can't be empty"
        val clash = labels.value.any { it.id != excludeId && it.name.equals(trimmed, ignoreCase = true) }
        return if (clash) "That label already exists" else null
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

    suspend fun expenseCount(id: Long): Int = repo.expenseCount(id)

    suspend fun delete(label: LabelEntity) {
        if (!canDelete(label)) return
        repo.delete(label.id)
        localOrder.value = localOrder.value?.filter { it != label.id }
    }
}
