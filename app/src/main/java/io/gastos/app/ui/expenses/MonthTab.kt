package io.gastos.app.ui.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.gastos.app.domain.Money
import io.gastos.app.ui.theme.GastosTheme

@Composable
fun MonthTab(vm: ExpensesViewModel, modifier: Modifier = Modifier) {
    val month by vm.month.collectAsState()
    val byLabel by vm.monthByLabel.collectAsState()
    val byMethod by vm.methodTotals.collectAsState()
    val methodMax = byMethod.maxOfOrNull { it.totalCentavos } ?: 0L
    val total = byLabel.sumOf { it.totalCentavos }
    val max = byLabel.maxOfOrNull { it.totalCentavos } ?: 0L

    Column(modifier.fillMaxWidth()) {
        MonthSwitcher(month, vm::prevMonth, vm::nextMonth)
        Text(
            Money.format(total), fontSize = 26.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("By label")
            byLabel.sortedByDescending { it.totalCentavos }.forEach { lt ->
                TotalBar(lt.name, lt.totalCentavos, max, Color(lt.colorArgb))
            }
            SectionTitle("By payment method")
            byMethod.sortedByDescending { it.totalCentavos }.forEach { mt ->
                TotalBar(mt.name, mt.totalCentavos, methodMax, Color(mt.colorArgb))
            }
        }
        ExportButtons(Modifier.padding(16.dp)) { format -> vm.exportMonth(month, format) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = GastosTheme.colors.muted)
}

@Composable
private fun TotalBar(name: String, totalCentavos: Long, max: Long, color: Color) {
    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, fontSize = 13.sp)
            Text(Money.format(totalCentavos), fontSize = 13.sp)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)).background(GastosTheme.colors.surface2)) {
            val frac = if (max > 0) totalCentavos.toFloat() / max else 0f
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(99.dp)).background(color))
        }
    }
}
