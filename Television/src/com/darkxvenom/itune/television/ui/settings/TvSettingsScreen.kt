/*
 * Copyright (C) 2026 DxV Studio ~~ Dark
 *
 * Originally created by DxV Studio ~~ Dark.
 * Credits: DxV Studio, AirBeats, and Dark.
 *
 * This file is part of iTune and is licensed under the GNU General Public License v3.0 (GPL-3.0).
 * Any copying, modification, or redistribution of this code must strictly comply with the
 * terms of the project LICENSE file and retain full author credit.
 */

package com.darkxvenom.itune.television.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkxvenom.itune.data.auth.GoogleAuthStore
import android.widget.Toast
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.darkxvenom.itune.data.auth.WebSessionMode
import com.darkxvenom.itune.data.auth.YouTubeAccountUiState
import com.darkxvenom.itune.television.ui.common.TvAmbientBackground
import com.darkxvenom.itune.ui.auth.GoogleLoginScreen
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.television.focus.SFProDisplay
import com.darkxvenom.itune.television.focus.TvBigButton
import com.darkxvenom.itune.television.focus.TvSecondaryButton
import com.darkxvenom.itune.television.focus.tvFocusable
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleOledBlack

// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

enum class TvSettingsTab(val label: String, val icon: ImageVector) {
    ACCOUNT("Account", Icons.Rounded.AccountCircle),
    AUDIO("Audio & Playback", Icons.Rounded.GraphicEq),
    APPEARANCE("Display & TV", Icons.Rounded.Tv),
    ABOUT("About iTune", Icons.Rounded.Info),
}

@Composable
fun TvSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val settingsState by SettingsStore.state.collectAsStateWithLifecycle()
    val ytAccountState by GoogleAuthStore.state.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(TvSettingsTab.ACCOUNT) }

    var loginMode by remember { mutableStateOf<WebSessionMode?>(null) }

    Box(modifier = modifier.fillMaxSize().background(AppleOledBlack)) {
    TvAmbientBackground(Modifier.fillMaxSize(), dim = 0.30f)
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 36.dp),
        horizontalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        // Left Column: Category Navigation List
        Column(
            modifier = Modifier
                .width(260.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Settings",
                color = Color.White,
                fontSize = 28.sp,
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp, start = 8.dp),
            )

            TvSettingsTab.entries.forEach { tab ->
                val isSelected = tab == activeTab
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .tvFocusable(
                            shape = RoundedCornerShape(12.dp),
                            focusedBorderColor = Color.White,
                            scaleOnFocus = 1.03f,
                            interactionSource = interactionSource,
                            onClick = { activeTab = tab },
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                isSelected -> AppleMusicRed.copy(alpha = 0.25f)
                                isFocused -> Color(0x28FFFFFF)
                                else -> Color.Transparent
                            }
                        )
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (isSelected) AppleMusicRed else Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = tab.label,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontFamily = SFProDisplay,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }

        // Right Column: Active Settings Pane
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0x14FFFFFF))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(24.dp))
                .padding(32.dp),
        ) {
            when (activeTab) {
                TvSettingsTab.ACCOUNT -> {
                    Text(
                        text = "Account & Synchronization",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    TvAccountPanel(
                        state = ytAccountState,
                        onSignIn = { loginMode = WebSessionMode.SIGN_IN },
                        onSwitchChannel = { loginMode = WebSessionMode.SWITCH_CHANNEL },
                        onSignOut = {
                            GoogleAuthStore.signOut(context)
                            Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                        },
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    TvSettingCard(
                        title = "Display Name",
                        subtitle = settingsState.displayName.ifBlank { "Dark" },
                    )
                }

                TvSettingsTab.AUDIO -> {
                    Text(
                        text = "Audio & Playback",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    TvSettingToggleCard(
                        title = "Hide Explicit Content",
                        subtitle = "Filter explicit songs from shelves and searches",
                        checked = settingsState.hideExplicitContent,
                        onCheckedChange = { SettingsStore.setBoolean(context, SettingsStore.KEY_HIDE_EXPLICIT, it) },
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    TvSettingToggleCard(
                        title = "Spotify Canvas Background",
                        subtitle = "Play animated visual loops over the player when available",
                        checked = settingsState.prioritizeSpotifyCanvas,
                        onCheckedChange = { SettingsStore.setBoolean(context, SettingsStore.KEY_PRIORITIZE_SPOTIFY_CANVAS, it) },
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    TvSettingCard(
                        title = "Audio Stream Quality",
                        subtitle = "Lossless 24-bit / High-Bitrate AAC",
                    )
                }

                TvSettingsTab.APPEARANCE -> {
                    Text(
                        text = "Display & Television",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    TvSettingCard(
                        title = "Television 10-Foot Mode",
                        subtitle = "Active and optimized for Android TV remote navigation",
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    TvSettingCard(
                        title = "App Font",
                        subtitle = "Apple SF Pro Display",
                    )
                }

                TvSettingsTab.ABOUT -> {
                    Text(
                        text = "About iTune TV",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    TvSettingCard(
                        title = "Version",
                        subtitle = "1.1 (Television Edition)",
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    TvSettingCard(
                        title = "Credits & Creators",
                        subtitle = "DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0",
                    )
                }
            }
        }
    }

    loginMode?.let { mode ->
        GoogleLoginScreen(
            mode = mode,
            initialCookie = if (mode == WebSessionMode.SWITCH_CHANNEL) GoogleAuthStore.activeCookie() else null,
            onSignedIn = {
                loginMode = null
                Toast.makeText(
                    context,
                    if (mode == WebSessionMode.SWITCH_CHANNEL) "Channel switched • Loading feed..." else "Signed in • Loading feed...",
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onClose = { loginMode = null },
            modifier = Modifier.fillMaxSize(),
        )
    }
    }
}

@Composable
private fun TvAccountPanel(
    state: YouTubeAccountUiState,
    onSignIn: () -> Unit,
    onSwitchChannel: () -> Unit,
    onSignOut: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(Color(0x28FFFFFF))
                .border(1.5.dp, Color.White.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (state.isSignedIn && !state.avatarUrl.isNullOrBlank()) {
                AsyncImage(state.avatarUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Rounded.AccountCircle, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(60.dp))
            }
        }
        Spacer(Modifier.width(20.dp))
        Column {
            Text(
                if (state.isSignedIn) state.accountName.ifBlank { "YouTube Music" } else "Not signed in",
                color = Color.White, fontSize = 24.sp, fontFamily = SFProDisplay, fontWeight = FontWeight.Bold,
            )
            Text(
                if (state.isSignedIn) state.accountEmail.ifBlank { state.accountHandle } else
                    "Sign in with Google to get your personalised feed, library and recommendations.",
                color = Color.White.copy(alpha = 0.65f), fontSize = 15.sp, fontFamily = SFProDisplay,
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    if (!state.isSignedIn) {
        TvAccountButton("Sign in with Google", Icons.Rounded.AccountCircle, primary = true, onClick = onSignIn)
    } else {
        TvAccountButton("Switch channel / account", Icons.Rounded.SwapHoriz, primary = false, onClick = onSwitchChannel)
        Spacer(Modifier.height(12.dp))
        TvAccountButton("Add another Google account", Icons.Rounded.PersonAdd, primary = false, onClick = onSignIn)
        Spacer(Modifier.height(12.dp))
        TvAccountButton("Sign out", Icons.AutoMirrored.Rounded.Logout, primary = false, destructive = true, onClick = onSignOut)
    }
}

@Composable
private fun TvAccountButton(
    label: String,
    icon: ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val fg = when {
        focused -> Color.Black
        destructive -> Color(0xFFFF453A)
        else -> Color.White
    }
    Row(
        Modifier
            .fillMaxWidth()
            .tvFocusable(
                shape = RoundedCornerShape(16.dp), focusedBorderWidth = 0.dp, scaleOnFocus = 1.02f,
                interactionSource = interaction, onClick = onClick,
            )
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    focused -> Color.White
                    primary -> AppleMusicRed
                    else -> Color(0x22FFFFFF)
                },
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (primary && !focused) Color.White else fg, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = if (primary && !focused) Color.White else fg, fontSize = 18.sp, fontFamily = SFProDisplay, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TvSettingCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .tvFocusable(
                shape = RoundedCornerShape(16.dp),
                focusedBorderColor = Color.White,
                scaleOnFocus = 1.02f,
                interactionSource = interactionSource,
            )
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x18FFFFFF))
            .padding(20.dp),
    ) {
        Column {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.5.sp,
            )
        }
    }
}

@Composable
private fun TvSettingToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .tvFocusable(
                shape = RoundedCornerShape(16.dp),
                focusedBorderColor = Color.White,
                scaleOnFocus = 1.02f,
                interactionSource = interactionSource,
                onClick = { onCheckedChange(!checked) },
            )
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x18FFFFFF))
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontFamily = SFProDisplay,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 13.5.sp,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AppleMusicRed,
                    uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.2f),
                ),
            )
        }
    }
}
