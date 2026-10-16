package io.gastos.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.gastos.app.ui.ConfirmCopy
import io.gastos.app.ui.components.ConfirmDialog
import io.gastos.app.ui.theme.GastosTheme

private enum class Confirm { SAVE, RESTORE, START_FRESH }

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit, onOpenLabels: () -> Unit) {
    val colors = GastosTheme.colors
    val repo by vm.repoInput.collectAsState()
    val token by vm.tokenInput.collectAsState()
    val repoError by vm.repoError.collectAsState()
    val hasToken by vm.hasToken.collectAsState()
    val canUse by vm.canUseBackup.collectAsState()
    val enabled by vm.backupEnabled.collectAsState()
    val busy by vm.busy.collectAsState()
    val status by vm.status.collectAsState()
    val result by vm.result.collectAsState()
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Backup", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.muted)
            OutlinedTextField(
                value = repo,
                onValueChange = { vm.repoInput.value = it; vm.repoError.value = null },
                label = { Text("GitHub repo (owner/name)") },
                singleLine = true,
                isError = repoError != null,
                supportingText = repoError?.let { { Text(it, color = colors.danger) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = token,
                onValueChange = { vm.tokenInput.value = it },
                label = { Text("GitHub token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                supportingText = {
                    Text(if (hasToken) "Token saved. Leave empty to keep it." else "Use a fine-grained token with Contents read/write on this repo only.")
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { if (vm.validate()) confirm = Confirm.SAVE },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.bg),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save", fontWeight = FontWeight.SemiBold) }

            Text(status.text, color = if (status.isError) colors.danger else colors.muted, fontSize = 14.sp)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.backUpNow() },
                    enabled = canUse && enabled && !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.surface2, contentColor = colors.fg),
                    modifier = Modifier.weight(1f),
                ) { Text("Back up now", fontWeight = FontWeight.SemiBold) }
                if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = colors.accent)
            }
            result?.let { Text(it.text, color = if (it.isError) colors.danger else colors.fg, fontSize = 14.sp) }

            OutlinedButton(
                onClick = { confirm = Confirm.RESTORE },
                enabled = canUse && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Restore from GitHub") }
            if (!enabled) {
                OutlinedButton(
                    onClick = { confirm = Confirm.START_FRESH },
                    enabled = canUse && !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Start fresh") }
                Text("Restore your old data or start fresh to turn on backups.", color = colors.muted, fontSize = 13.sp)
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface)
                    .clickable(onClick = onOpenLabels).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Labels", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("›", color = colors.muted)
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    when (confirm) {
        Confirm.SAVE -> ConfirmDialog(
            message = ConfirmCopy.saveBackupSettings(),
            confirmText = "Save",
            onConfirm = { confirm = null; vm.save() },
            onDismiss = { confirm = null },
        )
        Confirm.RESTORE -> ConfirmDialog(
            message = ConfirmCopy.restore(),
            confirmText = "Restore",
            destructive = true,
            onConfirm = { confirm = null; vm.restore() },
            onDismiss = { confirm = null },
        )
        Confirm.START_FRESH -> ConfirmDialog(
            message = ConfirmCopy.startFresh(),
            confirmText = "Start fresh",
            onConfirm = { confirm = null; vm.startFresh() },
            onDismiss = { confirm = null },
        )
        null -> Unit
    }
}
