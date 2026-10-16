package io.gastos.app.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import io.gastos.app.AppContainer
import io.gastos.app.GastosApp
import io.gastos.app.domain.AmountInput
import io.gastos.app.domain.Key

private fun Context.container(): AppContainer = (applicationContext as GastosApp).container

/** Applies [key] to the widget's input state and re-renders. No database work. */
private suspend fun applyKey(context: Context, glanceId: GlanceId, key: Key) {
    updateAppWidgetState(context, glanceId) { prefs ->
        prefs[GastosWidget.INPUT] = AmountInput.apply(prefs[GastosWidget.INPUT] ?: "", key)
    }
    GastosWidget().update(context, glanceId)
}

class DigitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val digit = parameters[DIGIT] ?: return
        applyKey(context, glanceId, Key.Digit(digit))
    }

    companion object {
        val DIGIT = ActionParameters.Key<Int>("digit")
    }
}

class DotAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        applyKey(context, glanceId, Key.Dot)
}

class BackspaceAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        applyKey(context, glanceId, Key.Backspace)
}

/** Advances the shared last-used label (also read by Home). */
class NextLabelAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val container = context.container()
        val labels = container.labels.all()
        if (labels.isEmpty()) return
        // Advance from the label actually shown, so a never-set pref still moves off the first label.
        val shown = container.prefs.resolveLabel(labels)
        container.prefs.setLastLabelId(WidgetLogic.nextLabel(labels, shown.id).id)
        GastosWidget().update(context, glanceId)
    }
}

/** Saves the typed amount under the shown label. Empty/zero input or no labels: no-op. */
class EnterAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val container = context.container()
        val labels = container.labels.all()
        if (labels.isEmpty()) return
        // Take and clear a valid amount in one state write so a double tap cannot save twice.
        // Empty/zero input is left untouched, like Home.
        var cents: Long? = null
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs: Preferences ->
            cents = WidgetLogic.amountToSave(prefs[GastosWidget.INPUT] ?: "")
            if (cents == null) prefs else prefs.toMutablePreferences().apply { remove(GastosWidget.INPUT) }
        }
        val amount = cents ?: return
        val label = container.prefs.resolveLabel(labels)
        // add() calls onDataChanged(), which refreshes every widget instance with the cleared input.
        container.expenses.add(amount, label.id)
    }
}
