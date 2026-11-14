package com.darkxvenom.itune.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val BackupSecondary = Color(0xFF98989D)
private val BackupAccent = Color(0xFF0A84FF)

@Composable
internal fun BackupOptionsDialog(
    password: String,
    onPasswordChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreate: (Set<String>) -> Unit,
) {
    var account by remember { mutableStateOf(true) }
    var library by remember { mutableStateOf(true) }
    var confirmation by remember { mutableStateOf("") }
    BackupDialogFrame(title = "Back Up iTune", subtitle = "Choose what to include. Your backup is encrypted with a password you choose.", onDismiss = onDismiss) {
        BackupChoiceRow("All", "Accounts, every app setting, audio effects and library data", account && library) {
            account = !account || !library
            library = account
        }
        BackupChoiceRow("Account", "Google, Last.fm and Discord sign-in data", account) { account = !account }
        BackupChoiceRow("Settings, Audio Effects & Library", "Lyrics APIs, EQ, effect toggles, playlists, history, albums and stats", library) { library = !library }
        BackupPasswordField(password, onPasswordChange, "Encryption password")
        BackupPasswordField(confirmation, { confirmation = it }, "Confirm password")
        if (confirmation.isNotEmpty() && password != confirmation) {
            Text("Passwords don’t match", color = Color(0xFFFF453A), fontSize = 12.sp, modifier = Modifier.padding(bottom = 7.dp))
        }
        BackupAction("Save Encrypted Backup", enabled = (account || library) && password.length >= 8 && password == confirmation) {
            onCreate(buildSet { if (account) add("account"); if (library) add("library") })
        }
        BackupAction("Cancel", enabled = true, primary = false, onClick = onDismiss)
    }
}

@Composable
internal fun RestorePasswordDialog(
    password: String,
    onPasswordChange: (String) -> Unit,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onScan: () -> Unit,
) {
    BackupDialogFrame(title = "Unlock Backup", subtitle = "Enter the password used when this .itune backup was created.", onDismiss = onDismiss) {
        BackupPasswordField(password, onPasswordChange, "Backup password")
        if (error != null) Text(error, color = Color(0xFFFF453A), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        BackupAction(if (busy) "Scanning…" else "Scan Backup", enabled = !busy && password.isNotBlank(), onClick = onScan)
        BackupAction("Cancel", enabled = !busy, primary = false, onClick = onDismiss)
    }
}

@Composable
internal fun RestoreOptionsDialog(
    accountDescription: String,
    libraryDescription: String,
    available: Set<String>,
    onDismiss: () -> Unit,
    onRestore: (Set<String>) -> Unit,
) {
    var account by remember(available) { mutableStateOf("account" in available) }
    var library by remember(available) { mutableStateOf("library" in available) }
    BackupDialogFrame(title = "Choose What to Restore", subtitle = "The encrypted file contains the following data.", onDismiss = onDismiss) {
        if (available.containsAll(setOf("account", "library"))) {
            BackupChoiceRow("All", "Restore both sections", account && library) {
                account = !account || !library
                library = account
            }
        }
        if ("account" in available) BackupChoiceRow("Account", accountDescription, account) { account = !account }
        if ("library" in available) BackupChoiceRow("Settings, Audio Effects & Library", libraryDescription, library) { library = !library }
        BackupAction("Restore Selected", enabled = account || library) {
            onRestore(buildSet { if (account) add("account"); if (library) add("library") })
        }
        BackupAction("Cancel", enabled = true, primary = false, onClick = onDismiss)
    }
}

@Composable
internal fun AutoBackupDialog(
    initiallyEnabled: Boolean,
    savedPassword: String,
    onDismiss: () -> Unit,
    onSave: (Boolean, String) -> Unit,
) {
    var enabled by remember { mutableStateOf(initiallyEnabled) }
    var password by remember { mutableStateOf(savedPassword) }
    BackupDialogFrame(
        title = "Automatic Backup",
        subtitle = "Create an encrypted backup of your account, settings, audio effects and library once every day.",
        onDismiss = onDismiss,
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF171719))
                .clickable { enabled = !enabled }.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(if (enabled) "On" else "Off", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            androidx.compose.foundation.layout.Box(
                Modifier.size(width = 48.dp, height = 28.dp).clip(CircleShape)
                    .background(if (enabled) Color(0xFF30D158) else Color(0xFF48484A)),
                contentAlignment = if (enabled) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                androidx.compose.foundation.layout.Box(Modifier.padding(3.dp).size(22.dp).clip(CircleShape).background(Color.White))
            }
        }
        if (enabled) {
            Spacer(Modifier.size(12.dp))
            BackupPasswordField(password, { password = it }, "Backup password")
            Text(
                "Saved using Android Keystore. Backup files use PBKDF2-HMAC-SHA256 (310,000 rounds) and AES-256-GCM.",
                color = BackupSecondary, fontSize = 11.sp, lineHeight = 15.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )
        }
        Text("Saves to  Downloads/itune/backups", color = Color.White, fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color(0xFF171719))
                .padding(horizontal = 14.dp, vertical = 14.dp))
        BackupAction("Save", enabled = !enabled || password.length >= 8) { onSave(enabled, password) }
        BackupAction("Cancel", enabled = true, primary = false, onClick = onDismiss)
    }
}

@Composable
private fun BackupDialogFrame(title: String, subtitle: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.9f).clip(RoundedCornerShape(28.dp)).background(Color.Black)
                .border(1.dp, Color(0xFF2C2C2E), RoundedCornerShape(28.dp)).padding(22.dp),
        ) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = BackupSecondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 7.dp, bottom = 16.dp))
            content()
        }
    }
}

@Composable
private fun BackupChoiceRow(title: String, detail: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .background(Color(0xFF171719)).padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(22.dp).clip(CircleShape)
                .background(if (checked) BackupAccent else Color.Transparent)
                .border(1.dp, if (checked) BackupAccent else Color(0xFF68686D), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
        Column(Modifier.weight(1f).padding(start = 11.dp)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = BackupSecondary, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
    Spacer(Modifier.size(7.dp))
}

@Composable
private fun BackupPasswordField(value: String, onValueChange: (String) -> Unit, hint: String) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
        cursorBrush = SolidColor(BackupAccent),
        visualTransformation = PasswordVisualTransformation(),
        decorationBox = { inner ->
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth()) {
                if (value.isEmpty()) Text(hint, color = BackupSecondary, fontSize = 14.sp)
                inner()
            }
        },
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color(0xFF171719))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    )
    Text("At least 8 characters", color = BackupSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp, bottom = 9.dp))
}

@Composable
private fun BackupAction(label: String, enabled: Boolean, primary: Boolean = true, onClick: () -> Unit) {
    Text(
        label,
        color = if (primary) BackupAccent else BackupSecondary,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().padding(top = 7.dp).clip(RoundedCornerShape(13.dp))
            .background(if (primary) Color(0xFF071B30) else Color(0xFF151517))
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 14.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}
