package io.gastos.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.gastos.app.domain.AmountInput
import io.gastos.app.domain.Money
import io.gastos.app.ui.components.Numpad
import io.gastos.app.ui.theme.GastosTheme
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)
private val AccentInk = Color(0xFF1C1300)

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onOpenExpenses: () -> Unit,
    onOpenLabels: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = GastosTheme.colors
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm) {
        vm.toasts.collect { msg ->
            snackbar.currentSnackbarData?.dismiss()
            launch { snackbar.showSnackbar(msg) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(state.today.format(dateFormat), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Today ${Money.format(state.todayTotal)}", fontSize = 12.sp, color = colors.muted)
                }
                IconButton(
                    onClick = onOpenExpenses,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = colors.surface2, contentColor = colors.fg),
                    modifier = Modifier.size(38.dp),
                ) { Icon(Icons.AutoMirrored.Outlined.ViewList, contentDescription = "Expenses list") }
                Box(Modifier.padding(start = 8.dp)) {
                    var menu by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { menu = true },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = colors.surface2, contentColor = colors.fg),
                        modifier = Modifier.size(38.dp),
                    ) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Labels") }, onClick = { menu = false; onOpenLabels() })
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; onOpenSettings() })
                    }
                }
            }

            Column(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("₱", fontSize = 32.sp, color = colors.muted, modifier = Modifier.padding(end = 4.dp, bottom = 5.dp))
                    Text(
                        AmountInput.display(state.input),
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp,
                        maxLines = 1,
                    )
                }
                Box {
                    var open by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = { open = true },
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, colors.line),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.surface2, contentColor = colors.fg),
                    ) { Text("${state.label?.name ?: "..."} ▾", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        state.labels.forEach { label ->
                            DropdownMenuItem(
                                text = { Text(label.name) },
                                leadingIcon = { Canvas(Modifier.size(12.dp)) { drawCircle(Color(label.colorArgb)) } },
                                onClick = { open = false; vm.selectLabel(label.id) },
                            )
                        }
                    }
                }
            }

            Numpad(onKey = vm::onKey)
            Button(
                onClick = vm::enter,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = AccentInk),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 16.dp)
                    .height(58.dp),
            ) { Text("Enter", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 90.dp),
        )
    }
}
