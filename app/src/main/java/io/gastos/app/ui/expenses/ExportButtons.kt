package io.gastos.app.ui.expenses

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.gastos.app.export.ExportResult
import io.gastos.app.export.Format
import io.gastos.app.ui.theme.GastosTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Export CSV / Export XLSX buttons. Each opens a small menu: Share (system chooser) or Save to phone.
 * [produce] builds the file for the chosen format. Failures show a short message and never crash.
 */
@Composable
fun ExportButtons(modifier: Modifier = Modifier, produce: suspend (Format) -> ExportResult) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingSave by remember { mutableStateOf<ExportResult?>(null) }

    fun onSaved(dest: android.net.Uri?) {
        val src = pendingSave
        pendingSave = null
        if (dest != null && src != null) {
            scope.launch {
                val ok = runCatching {
                    withContext(Dispatchers.IO) {
                        val input = context.contentResolver.openInputStream(src.uri) ?: error("no input")
                        val output = context.contentResolver.openOutputStream(dest, "wt") ?: error("no output")
                        input.use { i -> output.use { o -> i.copyTo(o) } }
                    }
                }.isSuccess
                toast(context, if (ok) "Saved ${src.fileName}" else "Couldn't save the file")
            }
        }
    }
    // CreateDocument fixes its MIME type at construction, so one launcher per format.
    val csvSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Format.CSV.mime), ::onSaved)
    val xlsxSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Format.XLSX.mime), ::onSaved)

    fun run(format: Format, save: Boolean) {
        scope.launch {
            val result = try {
                produce(format)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(context, "Export failed")
                return@launch
            }
            if (save) {
                pendingSave = result
                try {
                    (if (format == Format.CSV) csvSaver else xlsxSaver).launch(result.fileName)
                } catch (e: ActivityNotFoundException) {
                    pendingSave = null
                    toast(context, "No app can save files")
                }
            } else {
                share(context, result)
            }
        }
    }

    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ExportButton("Export CSV") { save -> run(Format.CSV, save) }
        ExportButton("Export XLSX") { save -> run(Format.XLSX, save) }
    }
}

@Composable
private fun RowScope.ExportButton(text: String, onChoose: (save: Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.weight(1f)) {
        OutlinedButton(
            onClick = { open = true },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, GastosTheme.colors.line),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = GastosTheme.colors.surface2, contentColor = GastosTheme.colors.fg),
            modifier = Modifier.fillMaxWidth().height(42.dp),
        ) { Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Share") }, onClick = { open = false; onChoose(false) })
            DropdownMenuItem(text = { Text("Save to phone") }, onClick = { open = false; onChoose(true) })
        }
    }
}

private fun share(context: Context, r: ExportResult) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = r.mime
        putExtra(Intent.EXTRA_STREAM, r.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, "Share export").apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(chooser)
    } catch (e: ActivityNotFoundException) {
        toast(context, "No app can share this file")
    }
}

private fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
