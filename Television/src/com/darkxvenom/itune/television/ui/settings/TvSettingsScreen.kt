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

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(AppleOledBlack)
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

                    TvSettingCard(
                        title = "Display Name",
                        subtitle = settingsState.displayName.ifBlank { "Dark" },
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    TvSettingCard(
                        title = "YouTube Music Account",
                        subtitle = if (ytAccountState.isSignedIn) "Signed in as ${ytAccountState.accountName}" else "Not signed in",
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
