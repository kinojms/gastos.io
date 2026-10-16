package io.gastos.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import io.gastos.app.ui.theme.GastosTheme

@Composable
fun ConfirmDialog(
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    val colors = GastosTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (destructive) colors.danger else colors.accent)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.muted) } },
    )
}
