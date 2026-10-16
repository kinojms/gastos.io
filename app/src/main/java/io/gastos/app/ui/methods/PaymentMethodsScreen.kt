package io.gastos.app.ui.methods

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.gastos.app.data.DEFAULT_PAYMENT_METHODS
import io.gastos.app.data.LABEL_PALETTE
import io.gastos.app.data.PaymentMethodEntity
import io.gastos.app.ui.ConfirmCopy
import io.gastos.app.ui.components.ConfirmDialog
import io.gastos.app.ui.theme.GastosTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Label palette plus the six brand colors, deduped. */
private val METHOD_SWATCHES: List<Int> = (LABEL_PALETTE + DEFAULT_PAYMENT_METHODS.map { it.second }).distinct()

/** One change awaiting the user's confirmation. */
private class Pending(
    val message: String,
    val confirmText: String,
    val destructive: Boolean = false,
    val run: () -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PaymentMethodsScreen(vm: PaymentMethodsViewModel, onBack: () -> Unit) {
    val colors = GastosTheme.colors
    val shown by vm.displayed.collectAsState()
    val changed = vm.orderChanged(shown)
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to -> vm.move(from.index, to.index) }

    var editing by remember { mutableStateOf<PaymentMethodEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    // Confirmation queue; the head is shown. Dismissing any dialog cancels the remaining ones.
    var queue by remember { mutableStateOf<List<Pending>>(emptyList()) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Payment methods", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (changed) {
                TextButton(onClick = {
                    queue = listOf(Pending(ConfirmCopy.reorderMethods(), "Save") { vm.saveOrderAsync() })
                }) { Text("Save order", color = colors.accent, fontWeight = FontWeight.SemiBold) }
            }
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            items(shown.size, key = { shown[it].id }) { index ->
                val method = shown[index]
                ReorderableItem(reorderState, key = method.id) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
                            .background(colors.surface).clickable { editing = method }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "⋮⋮",
                            color = colors.muted,
                            modifier = Modifier.draggableHandle().padding(end = 10.dp, top = 4.dp, bottom = 4.dp),
                        )
                        Dot(method)
                        Spacer(Modifier.width(12.dp))
                        Text(method.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("Edit", color = colors.muted, fontSize = 13.sp)
                    }
                }
            }
        }
        Button(
            onClick = { adding = true },
            colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.fg),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) { Text("+ Add payment method", fontWeight = FontWeight.SemiBold) }
    }

    if (adding) {
        MethodEditDialog(
            title = "Add payment method",
            initialName = "",
            initialColor = LABEL_PALETTE.first(),
            validate = { vm.validateName(it, null) },
            onDismiss = { adding = false },
            onSave = { name, color ->
                adding = false
                queue = listOf(Pending(ConfirmCopy.addMethod(name), "Add") { vm.addAsync(name, color) })
            },
            onDelete = null,
        )
    }
    editing?.let { method ->
        MethodEditDialog(
            title = "Edit payment method",
            initialName = method.name,
            initialColor = method.colorArgb,
            validate = { vm.validateName(it, method.id) },
            onDismiss = { editing = null },
            onSave = { name, color ->
                editing = null
                queue = buildList {
                    if (name != method.name) {
                        add(Pending(ConfirmCopy.renameMethod(method.name, name), "Rename") { vm.renameAsync(method.id, name) })
                    }
                    if (color != method.colorArgb) {
                        add(Pending(ConfirmCopy.recolorMethod(name), "Change") { vm.recolorAsync(method.id, color) })
                    }
                }
            },
            onDelete = if (vm.canDelete()) {
                {
                    editing = null
                    queue = listOf(
                        Pending(ConfirmCopy.deleteMethod(method.name), "Delete", destructive = true) { vm.deleteAsync(method) },
                    )
                }
            } else null,
        )
    }
    val writeError by vm.error.collectAsState()
    writeError?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::clearError,
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = vm::clearError) { Text("OK", color = colors.accent) } },
        )
    }
    queue.firstOrNull()?.let { p ->
        ConfirmDialog(
            message = p.message,
            confirmText = p.confirmText,
            destructive = p.destructive,
            onConfirm = {
                queue = queue.drop(1)
                p.run()
            },
            onDismiss = { queue = emptyList() },
        )
    }
}

@Composable
private fun Dot(method: PaymentMethodEntity) {
    Box(Modifier.size(30.dp).clip(CircleShape).background(Color(method.colorArgb)), contentAlignment = Alignment.Center) {
        Text(method.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MethodEditDialog(
    title: String,
    initialName: String,
    initialColor: Int,
    validate: (String) -> String?,
    onDismiss: () -> Unit,
    onSave: (name: String, color: Int) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val colors = GastosTheme.colors
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    singleLine = true,
                    label = { Text("Name") },
                    isError = error != null,
                    supportingText = error?.let { msg -> { Text(msg) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    METHOD_SWATCHES.forEach { argb ->
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(Color(argb))
                                .then(if (argb == color) Modifier.border(3.dp, colors.fg, CircleShape) else Modifier)
                                .clickable { color = argb },
                        )
                    }
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Delete payment method", color = colors.danger)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val problem = validate(name)
                if (problem != null) {
                    error = problem
                    return@TextButton
                }
                val trimmed = name.trim()
                if (trimmed == initialName && color == initialColor) onDismiss() else onSave(trimmed, color)
            }) { Text("Save", color = colors.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.muted) } },
    )
}
