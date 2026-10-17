package io.gastos.app.ui.feedback

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.gastos.app.feedback.FeedbackType
import io.gastos.app.ui.ConfirmCopy
import io.gastos.app.ui.components.ConfirmDialog
import io.gastos.app.ui.theme.GastosTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(vm: FeedbackViewModel, onBack: () -> Unit) {
    val colors = GastosTheme.colors
    val context = LocalContext.current
    val type by vm.type.collectAsState()
    val message by vm.message.collectAsState()
    val contact by vm.contact.collectAsState()
    val busy by vm.busy.collectAsState()
    val canSend by vm.canSend.collectAsState()
    val error by vm.error.collectAsState()
    var typeMenu by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Submit feedback", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExposedDropdownMenuBox(expanded = typeMenu, onExpandedChange = { typeMenu = it }) {
                OutlinedTextField(
                    value = type.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeMenu) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                    FeedbackType.entries.forEach { t ->
                        DropdownMenuItem(text = { Text(t.label) }, onClick = { vm.onType(t); typeMenu = false })
                    }
                }
            }
            OutlinedTextField(
                value = message,
                onValueChange = vm::onMessage,
                label = { Text("Message") },
                placeholder = { Text("Tell us what's on your mind…") },
                minLines = 6,
                supportingText = {
                    Text("${message.length} / ${FeedbackViewModel.MESSAGE_MAX}", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = contact,
                onValueChange = vm::onContact,
                label = { Text("Your name or contact (optional)") },
                supportingText = { Text("So we can get back to you") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { confirming = true },
                enabled = canSend,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (busy) "Sending…" else "Send", fontWeight = FontWeight.Bold) }
            error?.let { Text(it, color = colors.danger, fontSize = 14.sp) }
        }
    }

    if (confirming) {
        ConfirmDialog(
            message = ConfirmCopy.sendFeedback(),
            confirmText = "Send",
            onConfirm = {
                confirming = false
                vm.send {
                    Toast.makeText(context, "Thanks! Your feedback was sent.", Toast.LENGTH_SHORT).show()
                    onBack()
                }
            },
            onDismiss = { confirming = false },
        )
    }
}
