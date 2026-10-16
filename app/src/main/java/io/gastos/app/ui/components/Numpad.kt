package io.gastos.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.gastos.app.domain.Key
import io.gastos.app.ui.theme.GastosTheme

private val rows: List<List<Pair<String, Key>>> = listOf(
    listOf("1" to Key.Digit(1), "2" to Key.Digit(2), "3" to Key.Digit(3)),
    listOf("4" to Key.Digit(4), "5" to Key.Digit(5), "6" to Key.Digit(6)),
    listOf("7" to Key.Digit(7), "8" to Key.Digit(8), "9" to Key.Digit(9)),
    listOf("." to Key.Dot, "0" to Key.Digit(0), "⌫" to Key.Backspace),
)

@Composable
fun Numpad(onKey: (Key) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, key) ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(GastosTheme.colors.key)
                            .clickable(role = Role.Button) { onKey(key) }
                            .semantics { if (key == Key.Backspace) contentDescription = "Backspace" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = GastosTheme.colors.fg)
                    }
                }
            }
        }
    }
}
