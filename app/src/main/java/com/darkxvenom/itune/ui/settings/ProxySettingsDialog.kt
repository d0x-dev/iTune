package com.darkxvenom.itune.ui.settings

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.AppProxyManager
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleTextPrimary
import com.darkxvenom.itune.ui.theme.AppleTextSecondary
import kotlinx.coroutines.launch

private val PROXY_TYPES = listOf("HTTP", "SOCKS5", "SOCKS4")

@Composable
fun ProxySettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val currentConfig = SettingsStore.state.value

    var enabled by remember { mutableStateOf(currentConfig.proxyEnabled) }
    var proxyType by remember { mutableStateOf(currentConfig.proxyType.ifBlank { "HTTP" }) }
    var host by remember { mutableStateOf(currentConfig.proxyHost) }
    var port by remember { mutableStateOf(if (currentConfig.proxyPort > 0) currentConfig.proxyPort.toString() else "8080") }
    var user by remember { mutableStateOf(currentConfig.proxyUser) }
    var pass by remember { mutableStateOf(currentConfig.proxyPass) }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 380.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* absorb clicks */ }
                    .shadow(32.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1C1C1E))
                    .border(0.75.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                // Icon Badge
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(AppleMusicRed.copy(alpha = 0.15f))
                        .border(0.75.dp, AppleMusicRed.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings_proxy),
                        contentDescription = null,
                        tint = AppleMusicRed,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Network Proxy",
                    color = AppleTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp
                )

                Text(
                    text = "Route app network traffic via HTTP or SOCKS proxy",
                    color = AppleTextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Master Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Enable Proxy",
                            color = AppleTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (enabled) "Traffic routed through proxy" else "Direct connection active",
                            color = if (enabled) AppleMusicRed else AppleTextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            enabled = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AppleMusicRed,
                            uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                            uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Protocol Type Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .padding(2.5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PROXY_TYPES.forEach { type ->
                        val isSelected = proxyType.equals(type, ignoreCase = true)
                        val bg by animateColorAsState(
                            targetValue = if (isSelected) AppleMusicRed else Color.Transparent,
                            label = "ProxyTypeBg"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(bg)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    proxyType = type
                                    testResult = null
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = type,
                                color = if (isSelected) Color.White else AppleTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Input Fields Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF141416))
                        .border(0.75.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Host & Port Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it; testResult = null },
                            label = { Text("Server Host / IP", fontSize = 11.sp) },
                            placeholder = { Text("e.g. 127.0.0.1", color = AppleTextSecondary.copy(alpha = 0.5f), fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1.8f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppleMusicRed,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedTextColor = AppleTextPrimary,
                                unfocusedTextColor = AppleTextPrimary,
                                focusedLabelColor = AppleMusicRed,
                                unfocusedLabelColor = AppleTextSecondary
                            )
                        )

                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it.filter { c -> c.isDigit() }.take(5); testResult = null },
                            label = { Text("Port", fontSize = 11.sp) },
                            placeholder = { Text("8080", color = AppleTextSecondary.copy(alpha = 0.5f), fontSize = 13.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppleMusicRed,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedTextColor = AppleTextPrimary,
                                unfocusedTextColor = AppleTextPrimary,
                                focusedLabelColor = AppleMusicRed,
                                unfocusedLabelColor = AppleTextSecondary
                            )
                        )
                    }

                    // Optional Auth Fields
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it; testResult = null },
                        label = { Text("Username (Optional)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppleMusicRed,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedTextColor = AppleTextPrimary,
                            unfocusedTextColor = AppleTextPrimary,
                            focusedLabelColor = AppleMusicRed,
                            unfocusedLabelColor = AppleTextSecondary
                        )
                    )

                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it; testResult = null },
                        label = { Text("Password (Optional)", fontSize = 11.sp) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppleMusicRed,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedTextColor = AppleTextPrimary,
                            unfocusedTextColor = AppleTextPrimary,
                            focusedLabelColor = AppleMusicRed,
                            unfocusedLabelColor = AppleTextSecondary
                        )
                    )

                    // Test Connection Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable(enabled = !isTesting) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                isTesting = true
                                testResult = null
                                scope.launch {
                                    val parsedPort = port.toIntOrNull() ?: 8080
                                    testResult = AppProxyManager.testConnection(
                                        type = proxyType,
                                        host = host,
                                        port = parsedPort,
                                        user = user,
                                        pass = pass
                                    )
                                    isTesting = false
                                }
                            }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = AppleMusicRed
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Testing Proxy…",
                                color = AppleMusicRed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                text = "Test Proxy Connection",
                                color = AppleMusicRed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Test Result Feedback
                    testResult?.let { (success, msg) ->
                        Text(
                            text = if (success) "✓ $msg" else "✕ $msg",
                            color = if (success) Color(0xFF34C759) else Color(0xFFFF453A),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Actions Divider
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = Color.White.copy(alpha = 0.12f)
                )

                // Save Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            val parsedPort = port.toIntOrNull() ?: 8080
                            SettingsStore.setProxyConfig(
                                context = context,
                                enabled = enabled,
                                type = proxyType,
                                host = host.trim(),
                                port = parsedPort,
                                user = user.trim(),
                                pass = pass
                            )
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Save Proxy",
                        color = AppleMusicRed,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp
                    )
                }

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = Color.White.copy(alpha = 0.12f)
                )

                // Cancel Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Cancel",
                        color = AppleTextSecondary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = (-0.2).sp
                    )
                }
            }
        }
    }
}
