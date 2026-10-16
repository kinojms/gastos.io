package io.gastos.app.ui.expenses

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.gastos.app.data.ExpenseRow
import io.gastos.app.domain.Money
import io.gastos.app.ui.theme.GastosTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val headerFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)
private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/** One day's expenses: header (date + total), tappable rows that open the edit sheet, and export buttons. */
@Composable
fun DayList(vm: ExpensesViewModel, date: LocalDate, modifier: Modifier = Modifier) {
    val colors = GastosTheme.colors
    val rows by remember(date) { vm.rowsFor(date) }.collectAsState(initial = emptyList())
    val total by remember(date) { vm.totalFor(date) }.collectAsState(initial = 0L)
    val labels by vm.labels.collectAsState()
    val voiceLanguage by vm.voiceLanguage.collectAsState()
    var editing by remember { mutableStateOf<ExpenseRow?>(null) }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.Bottom) {
            Text(date.format(headerFormat), fontSize = 13.sp, color = colors.muted, modifier = Modifier.weight(1f))
            Text(Money.format(total), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        if (rows.isEmpty()) {
            Text("No expenses", color = colors.muted, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp).weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(rows, key = { it.id }) { row ->
                    Row(
                        Modifier.fillMaxWidth().clickable { editing = row }.padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Canvas(Modifier.size(10.dp)) { drawCircle(Color(row.paymentMethodColor)) }
                        Column(Modifier.weight(1f)) {
                            Text(RowText.title(row), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                RowText.subtitle(
                                    row, Instant.ofEpochMilli(row.timestamp).atZone(ZoneId.systemDefault()).format(timeFormat),
                                ),
                                fontSize = 12.sp, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(Money.format(row.amountCentavos), fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider(color = colors.line)
                }
            }
        }
        ExportButtons(Modifier.padding(16.dp)) { format -> vm.exportDay(date, format) }
    }

    editing?.let { row ->
        val methods by remember(row.paymentMethodId) { vm.pickerMethods(row.paymentMethodId) }
            .collectAsState(initial = emptyList())
        EditExpenseSheet(
            row = row,
            labels = labels,
            methods = methods,
            voiceLanguage = voiceLanguage,
            zone = ZoneId.systemDefault(),
            onSave = { amount, labelId, methodId, d, t, description, notes ->
                vm.saveEdit(row.id, amount, labelId, methodId, d, t, description, notes)
            },
            onDelete = { vm.delete(row.id) },
            onDismiss = { editing = null },
        )
    }
}

