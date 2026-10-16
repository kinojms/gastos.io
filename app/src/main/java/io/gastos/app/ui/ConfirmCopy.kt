package io.gastos.app.ui

import io.gastos.app.domain.Money

/** Exact confirm-dialog copy shared across screens. */
object ConfirmCopy {
    fun saveExpense(amount: Long, label: String) = "Save changes to ${Money.format(amount)} · $label?"
    fun deleteExpense(amount: Long, label: String) = "Delete ${Money.format(amount)} · $label?"
    fun addLabel(name: String) = "Add label $name?"
    fun renameLabel(old: String, new: String) = "Rename $old to $new?"
    fun recolorLabel(name: String) = "Change color of $name?"
    fun reorderLabels() = "Save new label order?"
    fun deleteLabel(name: String, count: Int): String = when (count) {
        0 -> "Delete label $name?"
        1 -> "Delete label $name? 1 expense moves to Other."
        else -> "Delete label $name? $count expenses move to Other."
    }
    fun addMethod(name: String) = "Add payment method $name?"
    fun renameMethod(old: String, new: String) = "Rename $old to $new?"
    fun recolorMethod(name: String) = "Change color of $name?"
    fun reorderMethods() = "Save new payment method order?"
    fun deleteMethod(name: String) = "Delete $name? Past expenses keep it."
    fun restore() = "Restore from GitHub? This replaces all data on this phone."
    fun startFresh() = "Start fresh? Any existing backup in the repo will be replaced with this phone's data."
    fun saveBackupSettings() = "Save backup settings?"
    fun changeTheme(name: String) = "Change theme to $name?"
    fun changeVoiceLanguage(name: String) = "Change voice language to $name?"
}
