package io.gastos.app.ui.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.gastos.app.domain.Money
import io.gastos.app.ui.theme.GastosTheme
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val dayFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)
private val tabs = listOf("Today", "Days", "Month")

@Composable
fun ExpensesScreen(vm: ExpensesViewModel, onBack: () -> Unit, onOpenDay: (String) -> Unit) {
    val colors = GastosTheme.colors
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val today by vm.today.collectAsState()

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Expenses", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp))
                .background(colors.surface2).padding(4.dp),
        ) {
            tabs.forEachIndexed { i, name ->
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                        .background(if (i == tab) colors.surface else colors.surface2)
                        .clickable { tab = i }.padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (i == tab) colors.fg else colors.muted)
                }
            }
        }
        when (tab) {
            0 -> DayList(vm, today, Modifier.weight(1f))
            1 -> DaysTab(vm, onOpenDay, Modifier.weight(1f))
            else -> MonthTab(vm, Modifier.weight(1f))
        }
    }
}

@Composable
fun MonthSwitcher(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Text("‹", fontSize = 24.sp) }
        Text(month.format(monthFormat), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        IconButton(onClick = onNext) { Text("›", fontSize = 24.sp) }
    }
}

@Composable
private fun DaysTab(vm: ExpensesViewModel, onOpenDay: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = GastosTheme.colors
    val month by vm.month.collectAsState()
    val days by vm.daySummaries.collectAsState()
    Column(modifier.fillMaxWidth()) {
        MonthSwitcher(month, vm::prevMonth, vm::nextMonth)
        if (days.isEmpty()) {
            Text("No expenses this month", color = colors.muted, modifier = Modifier.padding(18.dp))
        }
        LazyColumn(Modifier.weight(1f)) {
            items(days, key = { it.date.toString() }) { d ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenDay(d.date.toString()) }.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(d.date.format(dayFormat), fontSize = 15.sp)
                        Text(if (d.count == 1) "1 expense" else "${d.count} expenses", fontSize = 12.sp, color = colors.muted)
                    }
                    Text(Money.format(d.totalCentavos), fontWeight = FontWeight.SemiBold)
                }
                HorizontalDivider(color = colors.line)
            }
        }
    }
}
