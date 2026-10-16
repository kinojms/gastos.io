package io.gastos.app.ui.expenses

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.gastos.app.data.ExpenseRow
import io.gastos.app.data.LabelEntity
import io.gastos.app.data.PaymentMethodEntity
import io.gastos.app.domain.AmountInput
import io.gastos.app.domain.ExpenseText
import io.gastos.app.domain.Key
import io.gastos.app.domain.Money
import io.gastos.app.ui.ConfirmCopy
import io.gastos.app.ui.components.ConfirmDialog
import io.gastos.app.ui.components.VoiceLanguage
import io.gastos.app.ui.components.rememberVoiceInput
import io.gastos.app.ui.theme.GastosTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/** Rebuilds [raw] through [AmountInput] so typed/pasted text obeys the same rules as the numpad. */
internal fun sanitizeAmount(raw: String): String =
    raw.fold("") { acc, c ->
        when {
            c in '0'..'9' -> AmountInput.apply(acc, Key.Digit(c - '0'))
            c == '.' -> AmountInput.apply(acc, Key.Dot)
            else -> acc
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExpenseSheet(
    row: ExpenseRow,
    labels: List<LabelEntity>,
    methods: List<PaymentMethodEntity>,
    voiceLanguage: VoiceLanguage,
    zone: ZoneId,
    onSave: (
        amountCentavos: Long, labelId: Long, paymentMethodId: Long, date: LocalDate, time: LocalTime,
        description: String?, notes: String?,
    ) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = GastosTheme.colors
    val initial = remember(row) { Instant.ofEpochMilli(row.timestamp).atZone(zone) }
    var amountText by remember(row) { mutableStateOf(Money.plain(row.amountCentavos)) }
    var labelId by remember(row) { mutableStateOf(row.labelId) }
    var methodId by remember(row) { mutableStateOf(row.paymentMethodId) }
    var description by remember(row) { mutableStateOf(row.description.orEmpty()) }
    var notes by remember(row) { mutableStateOf(row.notes.orEmpty()) }
    var date by remember(row) { mutableStateOf(initial.toLocalDate()) }
    var time by remember(row) { mutableStateOf(initial.toLocalTime().withSecond(0).withNano(0)) }
    var labelMenu by remember { mutableStateOf(false) }
    var methodMenu by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var confirmSave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val cents = AmountInput.toCentavos(amountText)
    val labelName = labels.firstOrNull { it.id == labelId }?.name ?: row.labelName
    val methodName = methods.firstOrNull { it.id == methodId }?.name ?: row.paymentMethodName
    val voice = rememberVoiceInput(voiceLanguage) { description = it }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
                .navigationBarsPadding().padding(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Edit expense", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = sanitizeAmount(it) },
                label = { Text("Amount (₱)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Box {
                OutlinedButton(onClick = { labelMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("$labelName ▾", color = colors.fg)
                }
                DropdownMenu(expanded = labelMenu, onDismissRequest = { labelMenu = false }) {
                    labels.forEach { l ->
                        DropdownMenuItem(text = { Text(l.name) }, onClick = { labelId = l.id; labelMenu = false })
                    }
                }
            }
            OutlinedTextField(
                value = description,
                onValueChange = { description = it.take(ExpenseText.DESCRIPTION_MAX) },
                label = { Text("What did you buy?") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                trailingIcon = voice?.let { launch ->
                    {
                        IconButton(onClick = launch) {
                            Icon(Icons.Filled.Mic, contentDescription = "Speak description", tint = colors.muted)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Box {
                OutlinedButton(onClick = { methodMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Payment: $methodName ▾", color = colors.fg)
                }
                DropdownMenu(expanded = methodMenu, onDismissRequest = { methodMenu = false }) {
                    methods.forEach { m ->
                        DropdownMenuItem(text = { Text(m.name) }, onClick = { methodId = m.id; methodMenu = false })
                    }
                }
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it.take(ExpenseText.NOTES_MAX) },
                label = { Text("Notes") },
                placeholder = { Text("Add notes…") },
                minLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.weight(1f)) {
                    Text(date.format(dateFormat), color = colors.fg)
                }
                OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.weight(1f)) {
                    Text(time.format(timeFormat), color = colors.fg)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    border = BorderStroke(1.dp, colors.danger),
                    modifier = Modifier.weight(1f),
                ) { Text("Delete", color = colors.danger) }
                Button(
                    onClick = { confirmSave = true },
                    enabled = cents != null,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                    modifier = Modifier.weight(1f),
                ) { Text("Save", fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }

    if (pickTime) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = { time = LocalTime.of(state.hour, state.minute); pickTime = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } },
        )
    }

    if (confirmSave && cents != null) {
        ConfirmDialog(
            message = ConfirmCopy.saveExpense(cents, labelName),
            confirmText = "Save",
            onConfirm = { confirmSave = false; onSave(cents, labelId, methodId, date, time, ExpenseText.description(description), ExpenseText.notes(notes)); onDismiss() },
            onDismiss = { confirmSave = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            message = ConfirmCopy.deleteExpense(row.amountCentavos, row.labelName),
            confirmText = "Delete",
            destructive = true,
            onConfirm = { confirmDelete = false; onDelete(); onDismiss() },
            onDismiss = { confirmDelete = false },
        )
    }
}
