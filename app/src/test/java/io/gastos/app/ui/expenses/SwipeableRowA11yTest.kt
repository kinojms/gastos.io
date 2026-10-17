package io.gastos.app.ui.expenses

import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** TalkBack focuses the row's clickable node, so Edit and Delete must be custom actions on that node. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SwipeableRowA11yTest {
    @get:Rule val compose = createComposeRule()

    @Test fun theTappableRowCarriesEditAndDeleteActions() {
        var edited = 0
        var deleted = 0
        compose.setContent {
            SwipeableRow(
                open = false,
                onOpenChange = {},
                onEdit = { edited++ },
                onDelete = { deleted++ },
                onClick = {},
            ) { Text("Jollibee ₱185.00") }
        }
        val row = compose.onNodeWithText("Jollibee ₱185.00", useUnmergedTree = false)
        row.assert(SemanticsMatcher("has Edit and Delete actions") { node ->
            val actions = node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }
            actions == listOf("Edit", "Delete")
        })
        val actions = row.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle {
            actions.first { it.label == "Edit" }.action()
            actions.first { it.label == "Delete" }.action()
        }
        assertEquals(1, edited)
        assertEquals(1, deleted)
    }
}
