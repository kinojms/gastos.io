package io.gastos.app.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import io.gastos.app.BuildConfig
import io.gastos.app.ui.components.VoiceLanguage
import io.gastos.app.ui.theme.Palettes
import io.gastos.app.ui.theme.ThemeId
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

private enum class Confirm { SAVE, RESTORE, START_FRESH, THEME, VOICE }

@Composable
private fun NavRow(title: String, value: String? = null, onClick: () -> Unit) {
    val colors = GastosTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = colors.muted, fontSize = 14.sp)
        Text("  \u203A", color = colors.muted)
    }
}

@Composable
private fun ThemeTile(id: ThemeId, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = GastosTheme.colors
    val light = Palettes.of(id, dark = false)
    val dark = Palettes.of(id, dark = true)
    val accent = if (isSystemInDarkTheme()) dark.accent else light.accent
    Column(
        modifier.clickable(onClick = onClick).semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .border(if (selected) 3.dp else 1.dp, if (selected) colors.accent else colors.line, CircleShape)
                .drawBehind {
                    // Diagonal split: light bg upper-left, dark bg lower-right.
                    drawRect(light.bg)
                    drawPath(
                        Path().apply {
                            moveTo(size.width, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close()
                        },
                        dark.bg,
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(accent))
        }
        Text(
            id.displayName, fontSize = 12.sp, color = if (selected) colors.fg else colors.muted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
        )
    }
}

private fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        // No browser installed; nothing to do.
    }
}

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit, onOpenLabels: () -> Unit, onOpenMethods: () -> Unit) {
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
    val theme by vm.themeId.collectAsState()
    val voiceLang by vm.voiceLanguage.collectAsState()
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var pendingTheme by remember { mutableStateOf<ThemeId?>(null) }
    var pendingVoice by remember { mutableStateOf<VoiceLanguage?>(null) }
    var pickingVoice by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Theme", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.muted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ThemeId.values().forEach { id ->
                    ThemeTile(id, id == theme, Modifier.weight(1f)) {
                        if (id != theme) { pendingTheme = id; confirm = Confirm.THEME }
                    }
                }
            }
            Text("Light or dark follows your phone.", color = colors.muted, fontSize = 13.sp)

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
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
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
            NavRow("Voice language", voiceLang.displayName) { pickingVoice = true }
            NavRow("Labels", onClick = onOpenLabels)
            NavRow("Payment methods", onClick = onOpenMethods)

            Text("About the dev", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.muted)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
                        Text(AboutDev.NAME.take(1), color = colors.onAccent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Column {
                        Text(AboutDev.NAME, fontWeight = FontWeight.SemiBold)
                        Text(AboutDev.TAGLINE, color = colors.muted, fontSize = 14.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openLink(context, AboutDev.GITHUB) }) { Text("GitHub") }
                    OutlinedButton(onClick = { openLink(context, AboutDev.LINKEDIN) }) { Text("LinkedIn") }
                    Spacer(Modifier.weight(1f))
                    Text("v${BuildConfig.VERSION_NAME}", color = colors.muted, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (pickingVoice) {
        AlertDialog(
            onDismissRequest = { pickingVoice = false },
            title = { Text("Voice language") },
            text = {
                Column {
                    VoiceLanguage.values().forEach { lang ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                pickingVoice = false
                                if (lang != voiceLang) { pendingVoice = lang; confirm = Confirm.VOICE }
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                lang.displayName, modifier = Modifier.weight(1f),
                                fontWeight = if (lang == voiceLang) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            if (lang == voiceLang) Text("\u2713", color = colors.accent)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickingVoice = false }) { Text("Cancel") } },
        )
    }

    when (confirm) {
        Confirm.THEME -> pendingTheme?.let { t ->
            ConfirmDialog(
                message = ConfirmCopy.changeTheme(t.displayName),
                confirmText = "Change",
                onConfirm = { confirm = null; pendingTheme = null; vm.setTheme(t) },
                onDismiss = { confirm = null; pendingTheme = null },
            )
        }
        Confirm.VOICE -> pendingVoice?.let { v ->
            ConfirmDialog(
                message = ConfirmCopy.changeVoiceLanguage(v.displayName),
                confirmText = "Change",
                onConfirm = { confirm = null; pendingVoice = null; vm.setVoiceLanguage(v) },
                onDismiss = { confirm = null; pendingVoice = null },
            )
        }
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
