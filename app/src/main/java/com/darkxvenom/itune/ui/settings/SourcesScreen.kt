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

package com.darkxvenom.itune.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.data.remote.RemoteLibrarySettings
import com.darkxvenom.itune.data.remote.RemoteLibraryStore
import com.darkxvenom.itune.data.sources.MusicAddon
import com.darkxvenom.itune.data.sources.PlaybackSources
import com.darkxvenom.itune.data.sources.SourceConfig
import com.darkxvenom.itune.data.sources.SourceHealth
import com.darkxvenom.itune.data.sources.SourceKind
import com.darkxvenom.itune.data.sources.SourceRegistry
import com.darkxvenom.itune.data.tidal.TidalInstances
import com.darkxvenom.itune.playback.audio.LosslessOutput
import com.darkxvenom.itune.ui.artist.components.BottomFadeScrim
import com.darkxvenom.itune.ui.artist.components.FrostedTopBar
import com.darkxvenom.itune.ui.artist.components.FrostedTopBarTitlePill
import com.darkxvenom.itune.ui.artist.components.TopFadeBlur
import com.darkxvenom.itune.ui.artist.components.topBarContentPadding
import com.darkxvenom.itune.ui.artist.haptics.Haptic
import com.darkxvenom.itune.ui.artist.haptics.rememberHaptics
import com.darkxvenom.itune.ui.components.ReorderDragHandle
import com.darkxvenom.itune.ui.components.rememberSpringOverscrollState
import com.darkxvenom.itune.ui.components.springOverscroll
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import com.darkxvenom.itune.ui.theme.AppleTextSecondary
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SourcesCardGrey = Color(0xFF1C1C1E)
private val SourcesCardDivider = Color(0xFF2C2C2E)
private val SourcesIconContainerBg = Color(0xFF262628)
private val SourcesPillBg = Color(0xFF2C2C2E)
private val SourcesInputBg = Color(0xFF2C2C2E)

private fun getSourceSubtitle(config: SourceConfig): String = when (config.kind) {
    SourceKind.TIDAL -> "Lossless FLAC & Hi-Res · Up to 24-bit / 192 kHz"
    SourceKind.HIRES -> "Direct Hi-Res FLAC · tracks.monochrome.st"
    SourceKind.JIOSAAVN -> "High Quality AAC · Up to 320 kbps"
    SourceKind.YOUTUBE -> "Full music catalogue · Default fallback provider"
    SourceKind.ADDON -> if (config.baseUrl.isNotBlank()) config.baseUrl else "HTTP Addon Server"
    SourceKind.CUSTOM_MODULE, SourceKind.MODULE -> if (config.baseUrl.isNotBlank()) config.baseUrl else "QuickJS Extension Plugin"
}

private fun getSourceIcon(config: SourceConfig): ImageVector = when (config.kind) {
    SourceKind.TIDAL -> Icons.Rounded.MusicNote
    SourceKind.HIRES -> Icons.Rounded.GraphicEq
    SourceKind.JIOSAAVN -> Icons.Rounded.MusicNote
    SourceKind.YOUTUBE -> Icons.Rounded.Public
    SourceKind.ADDON -> Icons.Rounded.Cloud
    SourceKind.CUSTOM_MODULE, SourceKind.MODULE -> Icons.Rounded.Extension
}

/**
 * Sources Screen styled 1:1 with Apple Settings:
 * pure Apple OLED black background, uniform #1C1C1E grey grouped cards, #2C2C2E dividers,
 * frosted top bar, spring overscroll, real-time sliding priority reordering, and Apple glass switches.
 */
@Composable
fun SourcesScreen(
    hasMiniPlayer: Boolean = false,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val hazeState = remember { HazeState() }

    LaunchedEffect(Unit) {
        SourceRegistry.init(context)
        LosslessOutput.init(context)
        PlaybackSources.ensureInit(context)
        RemoteLibraryStore.ensureInit(context)
    }

    val configs by SourceRegistry.configs.collectAsState()
    val settings by SettingsStore.state.collectAsState()
    val captchaGuidance by PlaybackSources.captchaGuidance.collectAsState()
    val remoteSettings by RemoteLibraryStore.settings.collectAsState()

    var addSourceDialog by remember { mutableStateOf(false) }
    var browseExtensionsDialog by remember { mutableStateOf(false) }
    var detailConfig by remember { mutableStateOf<SourceConfig?>(null) }
    var remoteEditor by remember { mutableStateOf<String?>(null) }
    var remoteTracks by remember { mutableStateOf<List<MusicTrack>?>(null) }
    var remoteLoading by remember { mutableStateOf(false) }
    var remoteError by remember { mutableStateOf("") }

    val activeCount = remember(configs) {
        configs.count { it.enabled }
    }
    val isScrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 40 }
    }

    BackHandler(onBack = onBack)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppleOledBlack),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    top = topBarContentPadding() + 8.dp,
                    bottom = if (hasMiniPlayer) 200.dp else 120.dp,
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .springOverscroll(rememberSpringOverscrollState()),
            ) {
                // Header Title
                item(key = "sources_header_title") {
                    Text(
                        text = "Sources",
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.6).sp,
                        modifier = Modifier.padding(horizontal = 18.dp),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Choose audio playback streaming sources, lossless tiers, and fallback priority",
                        color = AppleTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(horizontal = 18.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Streaming Sources Priority Section Header
                item(key = "streaming_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "STREAMING SOURCES",
                            color = Color(0xFF8E8E93),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp,
                        )
                        Text(
                            text = "$activeCount of ${configs.size} Active",
                            color = AppleTextSecondary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                }

                // Streaming Sources Priority Card
                item(key = "streaming_card") {
                    SourcesUniformCard {
                        configs.forEachIndexed { index, config ->
                            SourceConfigRowView(
                                config = config,
                                rank = index + 1,
                                index = index,
                                count = configs.size,
                                onOpen = {
                                    haptics.play(Haptic.Select)
                                    detailConfig = config
                                },
                                onToggle = { enabled ->
                                    haptics.play(if (enabled) Haptic.ToggleOn else Haptic.ToggleOff)
                                    SourceRegistry.setEnabled(config.id, enabled)
                                    if (config.kind == SourceKind.JIOSAAVN) {
                                        SettingsStore.setBoolean(context, SettingsStore.KEY_JIOSAAVN_PROVIDER, enabled)
                                    }
                                },
                                onMove = { fromIndex, toIndex ->
                                    haptics.play(Haptic.Select)
                                    val next = configs.map { it.id }.toMutableList()
                                    if (fromIndex in next.indices && toIndex in next.indices) {
                                        val item = next.removeAt(fromIndex)
                                        next.add(toIndex, item)
                                        SourceRegistry.reorderAll(next)
                                    }
                                },
                            )
                            if (index < configs.lastIndex) {
                                HorizontalDivider(
                                    color = SourcesCardDivider,
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(start = 60.dp),
                                )
                            }
                        }
                    }
                    Text(
                        text = "iTune queries sources in the order shown. Drag installed extensions and providers to adjust playback priority.",
                        color = Color(0xFF636366),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 6.dp),
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // Add Source or Extension Section
                item(key = "add_source_header") {
                    SourcesSectionHeader(title = "ADD SOURCE OR EXTENSION")
                }

                item(key = "add_source_card") {
                    SourcesUniformCard {
                        SourcesActionRow(
                            icon = Icons.Rounded.Add,
                            iconTint = Color.White,
                            title = "Add Addon or Source…",
                            subtitle = "Connect an HTTP addon server, manifest, or endpoint",
                            trailingValue = "Add",
                            onClick = {
                                haptics.play(Haptic.Select)
                                addSourceDialog = true
                            },
                        )

                        HorizontalDivider(
                            color = SourcesCardDivider,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(start = 60.dp),
                        )

                        SourcesActionRow(
                            icon = Icons.Rounded.Extension,
                            iconTint = Color.White,
                            title = "Browse Extensions…",
                            subtitle = "Install QuickJS modules from an extension index",
                            trailingValue = "Browse",
                            onClick = {
                                haptics.play(Haptic.Select)
                                browseExtensionsDialog = true
                            },
                        )
                    }
                    Text(
                        text = "Add custom HTTP addon servers (speaking /manifest.json, /search, /stream) or sandbox-isolated QuickJS modules.",
                        color = Color(0xFF636366),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 6.dp),
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // Network Libraries Section
                item(key = "network_header") {
                    SourcesSectionHeader(title = "NETWORK LIBRARIES")
                }

                item(key = "network_card") {
                    SourcesUniformCard {
                        // WebDAV Row
                        SourcesActionRow(
                            icon = Icons.Rounded.Cloud,
                            iconTint = Color.White,
                            title = "WebDAV",
                            subtitle = if (remoteSettings.webDavUrl.isBlank()) {
                                "Connect a WebDAV or Nextcloud music folder"
                            } else {
                                remoteSettings.webDavUrl
                            },
                            trailingValue = if (remoteSettings.webDavUrl.isNotBlank()) "Connected" else "Configure",
                            onClick = {
                                haptics.play(Haptic.Select)
                                remoteEditor = "webdav"
                            },
                        )

                        HorizontalDivider(
                            color = SourcesCardDivider,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(start = 60.dp),
                        )

                        // SMB Share Row
                        SourcesActionRow(
                            icon = Icons.Rounded.Storage,
                            iconTint = Color.White,
                            title = "SMB Share",
                            subtitle = if (remoteSettings.smbHost.isBlank()) {
                                "Connect a NAS or Windows network share"
                            } else {
                                "${remoteSettings.smbHost} · ${remoteSettings.smbShare}"
                            },
                            trailingValue = if (remoteSettings.smbHost.isNotBlank()) "Connected" else "Configure",
                            onClick = {
                                haptics.play(Haptic.Select)
                                remoteEditor = "smb"
                            },
                        )
                    }
                    Text(
                        text = "Stream your bit-perfect lossless library directly from personal network shares or Nextcloud.",
                        color = Color(0xFF636366),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 6.dp),
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // Preferences Section
                item(key = "privacy_header") {
                    SourcesSectionHeader(title = "PREFERENCES & VERIFICATION")
                }

                item(key = "privacy_card") {
                    SourcesUniformCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SourcesIconContainerBg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Security,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "CAPTCHA Guidance",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "In-app guidance when audio providers challenge anti-bot checks",
                                    color = AppleTextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 15.sp,
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            AppleGlassSwitch(
                                checked = captchaGuidance,
                                onCheckedChange = { checked ->
                                    haptics.play(if (checked) Haptic.ToggleOn else Haptic.ToggleOff)
                                    PlaybackSources.setCaptchaGuidance(checked)
                                },
                            )
                        }

                        HorizontalDivider(
                            color = SourcesCardDivider,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(start = 60.dp),
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SourcesIconContainerBg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Lossless Audio Preference",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Prefer bit-perfect FLAC/ALAC up to 24-bit / 192 kHz when available",
                                    color = AppleTextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 15.sp,
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            AppleGlassSwitch(
                                checked = settings.losslessAudio,
                                onCheckedChange = { checked ->
                                    haptics.play(if (checked) Haptic.ToggleOn else Haptic.ToggleOff)
                                    SettingsStore.setBoolean(context, SettingsStore.KEY_LOSSLESS_AUDIO, checked)
                                },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // Restore Defaults
                item(key = "restore_defaults") {
                    SourcesUniformCard {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptics.play(Haptic.Tap)
                                    val reset = SourceRegistry.sourcesForInit(emptyList(), forceJioSaavnOff = false)
                                    SourceRegistry.reorderAll(reset.map { it.id })
                                    Toast.makeText(context, "Sources restored to defaults", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Restore Default Sources",
                                color = AppleMusicRed,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Restores JioSaavn and YouTube Music in the default order. JioSaavn starts off and YouTube Music starts on.",
                        color = Color(0xFF636366),
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    )
                }
            }

            TopFadeBlur(
                hazeState = hazeState,
                pageColor = AppleOledBlack,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            BottomFadeScrim(
                withMiniPlayer = hasMiniPlayer,
                pageColor = AppleOledBlack,
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            FrostedTopBar(
                transparentBackdrop = true,
                backButtonHazeState = hazeState,
                onBack = onBack,
                modifier = Modifier.align(Alignment.TopCenter),
                centerContent = {
                    AnimatedVisibility(
                        visible = isScrolled,
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(180)),
                    ) {
                        FrostedTopBarTitlePill(hazeState = hazeState) {
                            Text(
                                text = "Sources",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                },
            )
        }

        // Add Source / Addon Dialog
        if (addSourceDialog) {
            AddSourceDialog(
                onDismiss = { addSourceDialog = false },
                onAdd = { newConfig ->
                    SourceRegistry.add(newConfig)
                    addSourceDialog = false
                    Toast.makeText(context, "Added source: ${newConfig.displayName}", Toast.LENGTH_SHORT).show()
                },
            )
        }

        // Browse Available Extensions Dialog
        if (browseExtensionsDialog) {
            BrowseExtensionsDialog(
                installedConfigs = configs,
                onDismiss = { browseExtensionsDialog = false },
                onInstall = { chosen ->
                    chosen.forEach { addon ->
                        SourceRegistry.add(
                            SourceConfig(
                                kind = if (addon.moduleId != null) SourceKind.CUSTOM_MODULE else SourceKind.ADDON,
                                baseUrl = addon.baseUrl,
                                label = addon.name,
                            )
                        )
                    }
                    browseExtensionsDialog = false
                    Toast.makeText(context, "Installed ${chosen.size} extension(s)", Toast.LENGTH_SHORT).show()
                },
            )
        }

        // Source Config / Details Dialog
        detailConfig?.let { config ->
            SourceConfigDialog(
                config = config,
                onDismiss = { detailConfig = null },
                onUpdate = { updated ->
                    SourceRegistry.update(updated)
                    detailConfig = null
                },
                onRemove = {
                    SourceRegistry.remove(config.id)
                    detailConfig = null
                },
            )
        }

        // Remote Library (WebDAV / SMB) Editor Dialog
        remoteEditor?.let { type ->
            RemoteLibraryDialog(
                type = type,
                initial = remoteSettings,
                onDismiss = { remoteEditor = null },
                onSave = { form ->
                    if (type == "webdav") {
                        RemoteLibraryStore.saveWebDav(form.webDavUrl, form.webDavUser, form.webDavPassword)
                    } else {
                        RemoteLibraryStore.saveSmb(
                            form.smbHost,
                            form.smbShare,
                            form.smbFolder,
                            form.smbUser,
                            form.smbPassword,
                        )
                    }
                    remoteEditor = null
                },
                onBrowse = {
                    val form = if (type == "webdav") {
                        RemoteForm(webDavUrl = remoteSettings.webDavUrl, webDavUser = remoteSettings.webDavUser, webDavPassword = remoteSettings.webDavPassword)
                    } else {
                        RemoteForm(smbHost = remoteSettings.smbHost, smbShare = remoteSettings.smbShare, smbFolder = remoteSettings.smbFolder, smbUser = remoteSettings.smbUser, smbPassword = remoteSettings.smbPassword)
                    }
                    remoteLoading = true
                    remoteError = ""
                    remoteTracks = emptyList()
                    scope.launch {
                        if (type == "webdav") {
                            val result = com.darkxvenom.itune.data.remote.webdav.WebDavClient.listAudioFiles(form.webDavUrl, form.webDavUser, form.webDavPassword)
                            result.fold(
                                onSuccess = { entries ->
                                    remoteTracks = entries.map { entry ->
                                        MusicTrack(
                                            id = "webdav:${entry.url}",
                                            title = entry.displayName,
                                            artists = "WebDAV Library",
                                            album = "Cloud Storage",
                                        )
                                    }
                                    remoteLoading = false
                                },
                                onFailure = { err ->
                                    remoteError = err.message ?: "Failed to list WebDAV tracks"
                                    remoteLoading = false
                                },
                            )
                        } else {
                            val result = com.darkxvenom.itune.data.remote.smb.SmbClient.testAndList(
                                form.smbHost,
                                form.smbShare,
                                form.smbFolder,
                                form.smbUser,
                                form.smbPassword,
                            )
                            result.fold(
                                onSuccess = { listing ->
                                    remoteTracks = listing.audio.map { entry ->
                                        MusicTrack(
                                            id = "smb://${entry.path}",
                                            title = entry.path.substringAfterLast('/'),
                                            artists = "SMB Share",
                                            album = form.smbShare,
                                        )
                                    }
                                    remoteLoading = false
                                },
                                onFailure = { err ->
                                    remoteError = err.message ?: "Failed to list SMB tracks"
                                    remoteLoading = false
                                },
                            )
                        }
                    }
                },
            )
        }

        // Remote Tracks Browse Dialog
        remoteTracks?.let { list ->
            RemoteTracksDialog(
                tracks = list,
                loading = remoteLoading,
                error = remoteError,
                onDismiss = { remoteTracks = null },
                onPlay = { track ->
                    MusicPlayerState.play(track)
                    remoteTracks = null
                    onBack()
                },
            )
        }
    }
}

@Composable
private fun SourcesUniformCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SourcesCardGrey)
            .border(0.5.dp, SourcesCardDivider, RoundedCornerShape(16.dp)),
        content = content,
    )
}

@Composable
private fun SourcesSectionHeader(title: String) {
    Text(
        text = title,
        color = Color(0xFF8E8E93),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
    )
}

@Composable
private fun SourceConfigRowView(
    config: SourceConfig,
    rank: Int,
    index: Int,
    count: Int,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    var dragOffsetPx by remember(config.id) { mutableFloatStateOf(0f) }
    val rowAlpha by animateFloatAsState(
        targetValue = if (config.enabled) 1f else 0.45f,
        animationSpec = tween(180),
        label = "sourceRowAlpha",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = dragOffsetPx }
            .alpha(rowAlpha)
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icon container
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SourcesIconContainerBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = getSourceIcon(config),
                contentDescription = null,
                tint = if (config.enabled) Color.White else Color(0xFF8E8E93),
                modifier = Modifier.size(19.dp),
            )
        }

        Spacer(modifier = Modifier.width(13.dp))

        // Title and description (clean, no tags)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = config.displayName,
                color = Color.White,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (config.enabled) getSourceSubtitle(config) else "Disabled",
                color = AppleTextSecondary,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Drag handle for top-down priority ordering
        ReorderDragHandle(
            index = index,
            itemCount = count,
            onMove = onMove,
            itemStep = 58.dp,
            tint = Color(0xFF8E8E93),
            modifier = Modifier.size(28.dp),
            contentDescription = "Reorder source priority",
            onDragDistance = { dragOffsetPx += it },
            onDragFinished = { dragOffsetPx = 0f },
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Apple glass toggle switch
        AppleGlassSwitch(
            checked = config.enabled,
            onCheckedChange = onToggle,
        )
    }
}

@Composable
private fun SourcesActionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    trailingValue: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SourcesIconContainerBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = AppleTextSecondary,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (trailingValue.isNotBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = trailingValue,
                color = AppleTextSecondary,
                fontSize = 14.sp,
            )
        }

        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFF48484A),
            modifier = Modifier.size(18.dp),
        )
    }
}

// ── Apple Styled Dialogs ──

@Composable
private fun SourceConfigDialog(
    config: SourceConfig,
    onDismiss: () -> Unit,
    onUpdate: (SourceConfig) -> Unit,
    onRemove: () -> Unit,
) {
    var visible by remember { mutableStateOf(true) }
    var closePending by remember { mutableStateOf(false) }
    var testStatus by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var baseUrl by remember(config) { mutableStateOf(config.baseUrl) }
    var allowDownloads by remember(config) { mutableStateOf(config.allowDownloads) }
    var checkValidLossless by remember(config) { mutableStateOf(config.checkValidLossless) }

    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    fun dismissAnimated(after: () -> Unit = onDismiss) {
        if (closePending) return
        closePending = true
        visible = false
        scope.launch {
            delay(180)
            after()
        }
    }

    Dialog(
        onDismissRequest = { dismissAnimated() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { dismissAnimated() },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(160)) + scaleIn(tween(190), initialScale = 0.94f) + slideInVertically(tween(190)) { it / 18 },
                exit = fadeOut(tween(140)) + scaleOut(tween(150), targetScale = 0.96f) + slideOutVertically(tween(150)) { it / 22 },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .widthIn(max = 420.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { /* absorb */ }
                        .shadow(32.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black, spotColor = Color.Black)
                        .clip(RoundedCornerShape(24.dp))
                        .background(SourcesCardGrey)
                        .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
                        .padding(22.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = getSourceIcon(config),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column {
                            Text(
                                text = config.displayName,
                                color = Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = getSourceSubtitle(config),
                                color = AppleTextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (config.kind.isUserAdded) {
                        Text(
                            text = "Server URL / Manifest",
                            color = AppleTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        BasicTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it; testStatus = "" },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                            cursorBrush = SolidColor(AppleMusicRed),
                            decorationBox = { inner ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(SourcesInputBg)
                                        .border(0.6.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                ) {
                                    inner()
                                }
                            },
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Allow Downloads", color = Color.White, fontSize = 13.sp)
                            AppleGlassSwitch(
                                checked = allowDownloads,
                                onCheckedChange = { allowDownloads = it },
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Require Bit-Perfect Lossless Output", color = Color.White, fontSize = 13.sp)
                            AppleGlassSwitch(
                                checked = checkValidLossless,
                                onCheckedChange = { checkValidLossless = it },
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SourcesInputBg)
                                .padding(12.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = when (config.kind) {
                                        SourceKind.TIDAL -> "Endpoint: ${config.baseUrl.ifBlank { TidalInstances.BUNDLED.first().url }} (Failover pool: ${TidalInstances.BUNDLED.size} instances)"
                                        SourceKind.HIRES -> "Backend: Community Hi-Res FLAC (tracks.monochrome.st)"
                                        SourceKind.JIOSAAVN -> "Format: 320 kbps AAC (JioSaavn catalog)"
                                        SourceKind.YOUTUBE -> "Extractor: Built-in YouTube Music audio pipeline"
                                        else -> "Standard Source"
                                    },
                                    color = AppleTextSecondary,
                                    fontSize = 12.sp,
                                )
                                Text(
                                    text = "Lossless capability: ${if (config.kind.canServeLossless) "Yes (Bit-Perfect FLAC)" else "Lossy High-Bitrate"}",
                                    color = AppleTextSecondary,
                                    fontSize = 11.5.sp,
                                )
                            }
                        }
                    }

                    if (testStatus.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = testStatus,
                            color = AppleTextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (config.kind.isUserAdded) {
                            Text(
                                text = "Remove",
                                color = Color(0xFFFF453A),
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                modifier = Modifier
                                    .clickable {
                                        haptics.play(Haptic.Tap)
                                        dismissAnimated(onRemove)
                                    }
                                    .padding(vertical = 10.dp),
                            )
                        } else {
                            Text(
                                text = if (testing) "Probing…" else "Test",
                                color = AppleMusicRed,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                modifier = Modifier
                                    .clickable(enabled = !testing) {
                                        haptics.play(Haptic.Select)
                                        testing = true
                                        scope.launch {
                                            val health = SourceRegistry.probeCandidate(config)
                                            testStatus = when (health) {
                                                is SourceHealth.Ok -> "Online · ${health.detail ?: "Operational"}"
                                                is SourceHealth.Unreachable -> "Offline · ${health.reason}"
                                                is SourceHealth.Rejected -> "Rejected · ${health.reason}"
                                            }
                                            testing = false
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Cancel",
                                color = AppleTextSecondary,
                                fontSize = 15.sp,
                                modifier = Modifier
                                    .clickable { dismissAnimated() }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(AppleMusicRed)
                                    .clickable {
                                        haptics.play(Haptic.Select)
                                        if (config.kind.isUserAdded) {
                                            onUpdate(
                                                config.copy(
                                                    baseUrl = baseUrl,
                                                    allowDownloads = allowDownloads,
                                                    checkValidLossless = checkValidLossless,
                                                )
                                            )
                                        }
                                        dismissAnimated()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Done",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddSourceDialog(
    onDismiss: () -> Unit,
    onAdd: (SourceConfig) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var detectedConfig by remember { mutableStateOf<SourceConfig?>(null) }
    var error by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(true) }
    var closePending by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    fun dismissAnimated(after: () -> Unit = onDismiss) {
        if (closePending) return
        closePending = true
        visible = false
        scope.launch {
            delay(180)
            after()
        }
    }

    Dialog(
        onDismissRequest = { if (!testing) dismissAnimated() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { if (!testing) dismissAnimated() },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(160)) + scaleIn(tween(190), initialScale = 0.94f) + slideInVertically(tween(190)) { it / 18 },
                exit = fadeOut(tween(140)) + scaleOut(tween(150), targetScale = 0.96f) + slideOutVertically(tween(150)) { it / 22 },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .widthIn(max = 420.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { /* absorb */ }
                        .shadow(32.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black, spotColor = Color.Black)
                        .clip(RoundedCornerShape(24.dp))
                        .background(SourcesCardGrey)
                        .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
                        .padding(22.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Text(
                            text = "Add Streaming Source",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Enter an HTTP addon manifest URL, a QuickJS module index, or a Tidal instance endpoint.",
                        color = AppleTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    BasicTextField(
                        value = url,
                        onValueChange = { url = it; error = ""; detectedConfig = null },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = SolidColor(AppleMusicRed),
                        decorationBox = { inner ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SourcesInputBg)
                                    .border(0.6.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                            ) {
                                if (url.isBlank()) {
                                    Text(
                                        text = "https://example.com/manifest.json",
                                        color = Color.White.copy(alpha = 0.35f),
                                        fontSize = 14.sp,
                                    )
                                }
                                inner()
                            }
                        },
                    )

                    if (detectedConfig != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .border(0.6.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = "Detected: ${detectedConfig?.displayName}",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = if (detectedConfig?.kind?.canServeLossless == true) "Lossless Supported" else "Standard Stream",
                                    color = AppleTextSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }

                    if (error.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = error,
                            color = Color(0xFFFF453A),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Cancel",
                            color = AppleTextSecondary,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .clickable(enabled = !testing) { dismissAnimated() }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(AppleMusicRed)
                                .clickable(enabled = !testing && url.isNotBlank()) {
                                    haptics.play(Haptic.Select)
                                    if (detectedConfig == null) {
                                        testing = true
                                        scope.launch {
                                            SourceRegistry.identify(url).fold(
                                                onSuccess = { detected ->
                                                    detectedConfig = detected
                                                    testing = false
                                                },
                                                onFailure = { err ->
                                                    error = err.message ?: "Could not recognize source at this address."
                                                    testing = false
                                                },
                                            )
                                        }
                                    } else {
                                        detectedConfig?.let { onAdd(it) }
                                    }
                                }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (testing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                    )
                                }
                                Text(
                                    text = when {
                                        testing -> "Identifying…"
                                        detectedConfig == null -> "Identify"
                                        else -> "Add Source"
                                    },
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class PredefinedExtension(
    val id: String,
    val name: String,
    val baseUrl: String,
    val author: String,
    val version: String,
    val description: String,
)

private val PRESET_EXTENSIONS = listOf(
    PredefinedExtension(
        id = "preset_deezer_hifi",
        name = "Deezer Hi-Fi Provider",
        baseUrl = "https://api.zarz.moe/v2/deezer",
        author = "Mornye / Zarz",
        version = "2.4.0",
        description = "Lossless FLAC 16-bit / 44.1 kHz stream extraction & metadata matching.",
    ),
    PredefinedExtension(
        id = "preset_qobuz_studio",
        name = "Qobuz Studio Master",
        baseUrl = "https://tracks.monochrome.st/qobuz",
        author = "Monochrome",
        version = "2.1.0",
        description = "Studio Master Ultra Hi-Res Lossless 24-bit audio stream provider.",
    ),
    PredefinedExtension(
        id = "preset_tidal_mirror",
        name = "Tidal Hi-Fi Mirror",
        baseUrl = "https://api.monochrome.tf",
        author = "Monochrome TF",
        version = "3.2.0",
        description = "High-availability community mirror for lossless FLAC master playback.",
    ),
    PredefinedExtension(
        id = "preset_soundcloud_hq",
        name = "SoundCloud Go+ Lossless",
        baseUrl = "https://api.zarz.moe/v2/soundcloud",
        author = "Mornye / Zarz",
        version = "1.5.0",
        description = "High-bitrate audio streaming provider with instant catalog lookup.",
    ),
    PredefinedExtension(
        id = "preset_apple_spatial",
        name = "Apple Music Lossless",
        baseUrl = "https://api.zarz.moe/v2/applemusic",
        author = "Mornye Extension",
        version = "1.2.0",
        description = "Direct ALAC Lossless 16-bit / 24-bit audio stream substitution.",
    ),
    PredefinedExtension(
        id = "preset_amazon_hd",
        name = "Amazon Music HD / Ultra HD",
        baseUrl = "https://api.zarz.moe/v2/amazonmusic",
        author = "Mornye Extension",
        version = "1.8.0",
        description = "Lossless FLAC 24-bit / 192 kHz Ultra HD and 16-bit HD stream provider.",
    ),
    PredefinedExtension(
        id = "preset_spotify_provider",
        name = "Spotify Provider",
        baseUrl = "https://api.zarz.moe/v2/spotify",
        author = "Mornye Extension",
        version = "2.0.0",
        description = "Catalog metadata, Canvas video loops, and high-bitrate audio streams.",
    ),
    PredefinedExtension(
        id = "preset_monochrome_hires",
        name = "Monochrome Hi-Res Engine",
        baseUrl = "https://tracks.monochrome.st",
        author = "Monochrome",
        version = "3.0.0",
        description = "Direct extraction engine for verified master FLAC streams.",
    ),
)

@Composable
private fun BrowseExtensionsDialog(
    installedConfigs: List<SourceConfig> = emptyList(),
    onDismiss: () -> Unit,
    onInstall: (List<MusicAddon>) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<List<MusicAddon>>(emptyList()) }
    var selectedPresets by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedCustom by remember { mutableStateOf<Set<String>>(emptySet()) }
    var visible by remember { mutableStateOf(true) }
    var closePending by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    val installedUrls = remember(installedConfigs) {
        installedConfigs.map { it.baseUrl.trim().lowercase().removeSuffix("/") }.filter { it.isNotBlank() }.toSet()
    }
    val installedNames = remember(installedConfigs) {
        installedConfigs.map { it.displayName.trim().lowercase() }.toSet()
    }

    fun dismissAnimated(after: () -> Unit = onDismiss) {
        if (closePending) return
        closePending = true
        visible = false
        scope.launch {
            delay(180)
            after()
        }
    }

    Dialog(
        onDismissRequest = { if (!installing) dismissAnimated() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { if (!installing) dismissAnimated() },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(160)) + scaleIn(tween(190), initialScale = 0.94f) + slideInVertically(tween(190)) { it / 18 },
                exit = fadeOut(tween(140)) + scaleOut(tween(150), targetScale = 0.96f) + slideOutVertically(tween(150)) { it / 22 },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .widthIn(max = 440.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { /* absorb */ }
                        .shadow(32.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black, spotColor = Color.Black)
                        .clip(RoundedCornerShape(24.dp))
                        .background(SourcesCardGrey)
                        .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
                        .padding(20.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Extension,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Text(
                            text = "Browse Extensions",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Install pre-added Mornye community extensions or scan a custom repository index.",
                        color = AppleTextSecondary,
                        fontSize = 12.5.sp,
                        lineHeight = 16.sp,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 330.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "hdr_presets") {
                            Text(
                                text = "PRE-ADDED EXTENSIONS",
                                color = Color(0xFF8E8E93),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.4.sp,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                            )
                        }

                        itemsIndexed(PRESET_EXTENSIONS, key = { _, item -> item.id }) { _, preset ->
                            val cleanBase = preset.baseUrl.trim().lowercase().removeSuffix("/")
                            val isInstalled = cleanBase in installedUrls || preset.name.trim().lowercase() in installedNames
                            val isSelected = preset.id in selectedPresets

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        when {
                                            isInstalled -> Color.White.copy(alpha = 0.04f)
                                            isSelected -> AppleMusicRed.copy(alpha = 0.15f)
                                            else -> Color.White.copy(alpha = 0.06f)
                                        }
                                    )
                                    .border(
                                        0.5.dp,
                                        if (isSelected) AppleMusicRed.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable(enabled = !isInstalled) {
                                        haptics.play(Haptic.Select)
                                        selectedPresets = if (isSelected) selectedPresets - preset.id else selectedPresets + preset.id
                                    }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) AppleMusicRed.copy(alpha = 0.2f) else SourcesIconContainerBg),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Extension,
                                        contentDescription = null,
                                        tint = if (isSelected) AppleMusicRed else Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = preset.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = preset.description,
                                        color = AppleTextSecondary,
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                if (isInstalled) {
                                    Text(
                                        text = "Installed",
                                        color = AppleTextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) AppleMusicRed else Color.Transparent)
                                            .border(1.dp, if (isSelected) AppleMusicRed else Color.White.copy(alpha = 0.3f), CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(13.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "hdr_custom") {
                            Text(
                                text = "CUSTOM REPOSITORY INDEX OR URL",
                                color = Color(0xFF8E8E93),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.4.sp,
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                            )
                        }

                        item(key = "custom_input") {
                            BasicTextField(
                                value = url,
                                onValueChange = { url = it; error = "" },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                textStyle = TextStyle(color = Color.White, fontSize = 13.5.sp),
                                cursorBrush = SolidColor(AppleMusicRed),
                                decorationBox = { inner ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(SourcesInputBg)
                                            .border(0.6.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                    ) {
                                        if (url.isBlank()) {
                                            Text(
                                                text = "https://example.com/index.json",
                                                color = Color.White.copy(alpha = 0.35f),
                                                fontSize = 13.5.sp,
                                            )
                                        }
                                        inner()
                                    }
                                },
                            )
                        }

                        if (found.isNotEmpty()) {
                            itemsIndexed(found, key = { _, item -> item.id }) { _, addon ->
                                val isSelected = addon.id in selectedCustom
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) AppleMusicRed.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                                        .border(
                                            0.5.dp,
                                            if (isSelected) AppleMusicRed.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            haptics.play(Haptic.Select)
                                            selectedCustom = if (isSelected) selectedCustom - addon.id else selectedCustom + addon.id
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(SourcesIconContainerBg),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Extension,
                                            contentDescription = null,
                                            tint = if (isSelected) AppleMusicRed else Color.White,
                                            modifier = Modifier.size(15.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = addon.name,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                        )
                                        Text(
                                            text = "${addon.author.ifBlank { "iTune Plugin" }} · v${addon.version.ifBlank { "1.0" }}",
                                            color = AppleTextSecondary,
                                            fontSize = 11.sp,
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = AppleMusicRed,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (error.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error,
                            color = Color(0xFFFF453A),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val totalSelected = selectedPresets.size + selectedCustom.size
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Cancel",
                            color = AppleTextSecondary,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .clickable(enabled = !scanning && !installing) {
                                    haptics.play(Haptic.Select)
                                    dismissAnimated()
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(AppleMusicRed)
                                .clickable(enabled = !scanning && !installing && (totalSelected > 0 || url.isNotBlank())) {
                                    haptics.play(Haptic.Select)
                                    if (totalSelected > 0) {
                                        val installList = mutableListOf<MusicAddon>()
                                        PRESET_EXTENSIONS.filter { it.id in selectedPresets }.forEach { p ->
                                            installList.add(
                                                MusicAddon(
                                                    id = p.id,
                                                    name = p.name,
                                                    baseUrl = p.baseUrl,
                                                    version = p.version,
                                                    author = p.author,
                                                    description = p.description,
                                                )
                                            )
                                        }
                                        found.filter { it.id in selectedCustom }.forEach { c ->
                                            installList.add(c)
                                        }
                                        installing = true
                                        scope.launch {
                                            onInstall(installList)
                                        }
                                    } else if (url.isNotBlank()) {
                                        scanning = true
                                        scope.launch {
                                            PlaybackSources.discoverAddons(url).fold(
                                                onSuccess = { list ->
                                                    found = list
                                                    selectedCustom = if (list.size == 1) setOf(list.first().id) else emptySet()
                                                    if (list.isEmpty()) {
                                                        error = "No compatible extensions were found at this index."
                                                    }
                                                },
                                                onFailure = {
                                                    error = it.message ?: "Could not read the module index."
                                                },
                                            )
                                            scanning = false
                                        }
                                    }
                                }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (scanning || installing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                    )
                                }
                                Text(
                                    text = when {
                                        scanning -> "Checking…"
                                        installing -> "Installing…"
                                        totalSelected > 0 -> "Install ($totalSelected)"
                                        url.isNotBlank() -> "Scan Index"
                                        else -> "Install"
                                    },
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class RemoteForm(
    val webDavUrl: String = "",
    val webDavUser: String = "",
    val webDavPassword: String = "",
    val smbHost: String = "",
    val smbShare: String = "",
    val smbFolder: String = "",
    val smbUser: String = "",
    val smbPassword: String = "",
)

@Composable
private fun RemoteLibraryDialog(
    type: String,
    initial: RemoteLibrarySettings,
    onDismiss: () -> Unit,
    onSave: (RemoteForm) -> Unit,
    onBrowse: () -> Unit,
) {
    val webDav = type == "webdav"
    var url by remember(initial) { mutableStateOf(initial.webDavUrl) }
    var host by remember(initial) { mutableStateOf(initial.smbHost) }
    var share by remember(initial) { mutableStateOf(initial.smbShare) }
    var folder by remember(initial) { mutableStateOf(initial.smbFolder) }
    var user by remember(initial) { mutableStateOf(if (webDav) initial.webDavUser else initial.smbUser) }
    var password by remember(initial) { mutableStateOf(if (webDav) initial.webDavPassword else initial.smbPassword) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(true) }
    var closePending by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    fun dismissAnimated() {
        if (closePending) return
        closePending = true
        visible = false
        scope.launch {
            delay(180)
            onDismiss()
        }
    }

    fun form() = if (webDav) {
        RemoteForm(webDavUrl = url, webDavUser = user, webDavPassword = password)
    } else {
        RemoteForm(smbHost = host, smbShare = share, smbFolder = folder, smbUser = user, smbPassword = password)
    }

    Dialog(
        onDismissRequest = { if (!busy) dismissAnimated() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { if (!busy) dismissAnimated() },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(160)) + scaleIn(tween(190), initialScale = 0.94f) + slideInVertically(tween(190)) { it / 18 },
                exit = fadeOut(tween(140)) + scaleOut(tween(150), targetScale = 0.96f) + slideOutVertically(tween(150)) { it / 22 },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .widthIn(max = 420.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { /* absorb */ }
                        .shadow(32.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black, spotColor = Color.Black)
                        .clip(RoundedCornerShape(24.dp))
                        .background(SourcesCardGrey)
                        .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(22.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (webDav) Icons.Rounded.Cloud else Icons.Rounded.Storage,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Text(
                            text = if (webDav) "WebDAV Library" else "SMB Share Library",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (webDav) {
                            "Connect a WebDAV music folder or Nextcloud instance."
                        } else {
                            "Connect a shared network folder on your NAS or Windows computer."
                        },
                        color = AppleTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (webDav) {
                        RemoteInputField("Server URL", url, { url = it; status = "" }, "https://cloud.example.com/remote.php/dav/files/user/Music", KeyboardType.Uri)
                    } else {
                        RemoteInputField("Server Host / IP", host, { host = it; status = "" }, "nas.local or 192.168.1.100")
                        RemoteInputField("Share Name", share, { share = it; status = "" }, "Music")
                        RemoteInputField("Folder Path (optional)", folder, { folder = it; status = "" }, "Music/Lossless")
                    }
                    RemoteInputField("Username (optional)", user, { user = it; status = "" }, "username")
                    RemoteInputField("Password (optional)", password, { password = it; status = "" }, "password", isPassword = true)

                    if (status.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = status,
                            color = if (status.startsWith("Connected")) Color.White else Color(0xFFFF453A),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Cancel",
                            color = AppleTextSecondary,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .clickable(enabled = !busy, onClick = ::dismissAnimated)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                        Text(
                            text = "Test",
                            color = AppleMusicRed,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clickable(enabled = !busy) {
                                    haptics.play(Haptic.Select)
                                    busy = true
                                    onSave(form())
                                    scope.launch {
                                        val result = if (webDav) {
                                            com.darkxvenom.itune.data.remote.webdav.WebDavClient.testConnection(url, user, password)
                                        } else {
                                            com.darkxvenom.itune.data.remote.smb.SmbClient.testConnection(host, share, folder, user, password)
                                        }
                                        status = result.fold(
                                            onSuccess = { "Connected successfully!" },
                                            onFailure = { "Connection failed: ${it.message ?: "unknown error"}" },
                                        )
                                        busy = false
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(AppleMusicRed)
                                .clickable(enabled = !busy) {
                                    haptics.play(Haptic.Select)
                                    onSave(form())
                                    onBrowse()
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (busy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(13.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                    )
                                }
                                Text(
                                    text = "Browse",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteInputField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = title,
            color = AppleTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            cursorBrush = SolidColor(AppleMusicRed),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SourcesInputBg)
                        .border(0.6.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (value.isBlank()) {
                        Text(
                            text = placeholder,
                            color = Color.White.copy(alpha = 0.28f),
                            fontSize = 13.sp,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun RemoteTracksDialog(
    tracks: List<MusicTrack>,
    loading: Boolean,
    error: String,
    onDismiss: () -> Unit,
    onPlay: (MusicTrack) -> Unit,
) {
    var visible by remember { mutableStateOf(true) }
    var closePending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    fun dismissAnimated() {
        if (closePending) return
        closePending = true
        visible = false
        scope.launch {
            delay(180)
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = ::dismissAnimated,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { dismissAnimated() },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(160)) + scaleIn(tween(190), initialScale = 0.94f) + slideInVertically(tween(190)) { it / 18 },
                exit = fadeOut(tween(140)) + scaleOut(tween(150), targetScale = 0.96f) + slideOutVertically(tween(150)) { it / 22 },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .widthIn(max = 440.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { /* absorb */ }
                        .shadow(32.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black, spotColor = Color.Black)
                        .clip(RoundedCornerShape(24.dp))
                        .background(SourcesCardGrey)
                        .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
                        .padding(22.dp),
                ) {
                    Text(
                        text = "Remote Music Library",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when {
                            loading -> "Searching the connected library…"
                            error.isNotBlank() -> error
                            tracks.isEmpty() -> "No audio files found."
                            else -> "${tracks.size} tracks found · Tap a track to play"
                        },
                        color = if (error.isNotBlank()) Color(0xFFFF453A) else AppleTextSecondary,
                        fontSize = 12.sp,
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (loading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = AppleMusicRed, strokeWidth = 2.dp)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                            itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            haptics.play(Haptic.Select)
                                            onPlay(track)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(SourcesIconContainerBg),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MusicNote,
                                            contentDescription = null,
                                            tint = AppleMusicRed,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = listOfNotNull(track.artists, track.album).joinToString(" · "),
                                            color = AppleTextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                if (index < tracks.lastIndex) {
                                    HorizontalDivider(
                                        color = SourcesCardDivider,
                                        thickness = 0.5.dp,
                                        modifier = Modifier.padding(start = 52.dp),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(
                            text = "Done",
                            color = AppleMusicRed,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .clickable(onClick = ::dismissAnimated)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
