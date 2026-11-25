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


import androidx.compose.ui.res.stringResource
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.lyrics.LyricsHelper
import com.darkxvenom.itune.backend.lyrics.LyricsProviderInfo
import com.darkxvenom.itune.backend.lyrics.LyricsTranslationHelper
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.ui.artist.components.BottomFadeScrim
import com.darkxvenom.itune.ui.artist.components.FrostedTopBar
import com.darkxvenom.itune.ui.artist.components.FrostedTopBarTitlePill
import com.darkxvenom.itune.ui.artist.components.TopFadeBlur
import com.darkxvenom.itune.ui.artist.components.topBarContentPadding
import com.darkxvenom.itune.ui.artist.haptics.Haptic
import com.darkxvenom.itune.ui.artist.haptics.rememberHaptics
import com.darkxvenom.itune.ui.components.rememberSpringOverscrollState
import com.darkxvenom.itune.ui.components.ReorderDragHandle
import com.darkxvenom.itune.ui.components.springOverscroll
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import com.darkxvenom.itune.ui.theme.AppleTextSecondary
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

private val LyricsCardGrey = Color(0xFF1C1C1E)
private val LyricsCardDivider = Color(0xFF2C2C2E)
private val LyricsPillBg = Color(0xFF2C2C2E)
private val LyricsIconContainerBg = Color(0xFF262628)

@Composable
fun LyricsSettingsScreen(
    hasMiniPlayer: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val haptics = rememberHaptics()
    val settingsState by SettingsStore.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val hazeState = remember { HazeState() }

    val order = settingsState.lyricsProviderOrder
    val disabledSet = settingsState.disabledLyricsProviders

    val metadataMap = remember {
        LyricsHelper.PROVIDER_METADATA.associateBy { it.id }
    }

    val orderedProviders = remember(order) {
        order.mapNotNull { id -> metadataMap[id] }
    }

    val activeCount = remember(orderedProviders, disabledSet) {
        orderedProviders.count { it.id !in disabledSet }
    }

    var showProviderDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showBaseUrlDialog by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showModeDialog by remember { mutableStateOf(false) }
    var showPromptDialog by remember { mutableStateOf(false) }

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
                item(key = "lyrics_header_title") {
                    Text(
                        text = stringResource(R.string.ui_lyrics),
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.6).sp,
                        modifier = Modifier.padding(horizontal = 18.dp),
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // General display switch
                item(key = "general_header") {
                    LyricsSectionHeader(title = "PLAYBACK DISPLAY")
                }
                item(key = "general_card") {
                    LyricsUniformCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(LyricsIconContainerBg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.lyrics),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.ui_time_synced_lyrics),
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.ui_real_time_syllable_and_line_scrolling_with_live_audio),
                                    color = AppleTextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 15.sp,
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            AppleGlassSwitch(
                                checked = settingsState.timeSyncedLyrics,
                                onCheckedChange = { checked ->
                                    haptics.play(if (checked) Haptic.ToggleOn else Haptic.ToggleOff)
                                    SettingsStore.setBoolean(context, SettingsStore.KEY_TIME_SYNCED_LYRICS, checked)
                                },
                            )
                        }
                        HorizontalDivider(
                            color = LyricsCardDivider,
                            thickness = 0.6.dp,
                            modifier = Modifier.padding(start = 62.dp),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(LyricsIconContainerBg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.lyrics),
                                    contentDescription = null,
                                    tint = Color(0xFFEC1717),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.ui_show_synced_lyrics_over_canvas),
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.ui_display_animated_synced_lyrics_above_the_song_title_wh),
                                    color = AppleTextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 15.sp,
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            AppleGlassSwitch(
                                checked = settingsState.showSyncedLyricsOverCanvas,
                                onCheckedChange = { checked ->
                                    haptics.play(if (checked) Haptic.ToggleOn else Haptic.ToggleOff)
                                    SettingsStore.setBoolean(
                                        context,
                                        SettingsStore.KEY_SHOW_SYNCED_LYRICS_OVER_CANVAS,
                                        checked,
                                    )
                                },
                            )
                        }
                    }
                }

                // Fallback Priority & Providers List
                item(key = "priority_header") {
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.ui_provider_priority_fallback_order),
                            color = Color(0xFF8E8E93),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp,
                        )
                        Text(
                            text = stringResource(R.string.ui_of_active, activeCount, orderedProviders.size),
                            color = if (activeCount > 0) Color(0xFF34C759) else Color(0xFFFF453A),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(
                        text = stringResource(R.string.ui_itune_queries_lyrics_in_the_exact_precedence_below_if),
                        color = Color(0xFF636366),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                item(key = "providers_card") {
                    LyricsUniformCard {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            orderedProviders.forEachIndexed { index, provider ->
                                val isEnabled = provider.id !in disabledSet
                                LyricsProviderRow(
                                    provider = provider,
                                    rank = index + 1,
                                    isEnabled = isEnabled,
                                    providerIndex = index,
                                    providerCount = orderedProviders.size,
                                    onMove = { from, to ->
                                        haptics.play(Haptic.Select)
                                        SettingsStore.moveLyricsProvider(context, from, to)
                                    },
                                    onToggle = { enabled ->
                                        haptics.play(if (enabled) Haptic.ToggleOn else Haptic.ToggleOff)
                                        SettingsStore.setLyricsProviderEnabled(context, provider.id, enabled)
                                    },
                                )
                                if (index < orderedProviders.lastIndex) {
                                    HorizontalDivider(
                                        color = LyricsCardDivider,
                                        thickness = 0.5.dp,
                                        modifier = Modifier.padding(start = 58.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // AI Lyrics Translation Section
                item(key = "ai_translation_header") {
                    Spacer(modifier = Modifier.height(20.dp))
                    LyricsSectionHeader(title = "AI LYRICS TRANSLATION")
                }

                item(key = "ai_translation_card") {
                    LyricsUniformCard {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val currentProviderLabel = LyricsTranslationHelper.SUPPORTED_PROVIDERS
                                .firstOrNull { it.first == settingsState.lyricsTranslationProvider }?.second
                                ?: settingsState.lyricsTranslationProvider

                            // Provider selector row
                            LyricsActionRow(
                                icon = Icons.Rounded.SmartToy,
                                title = "AI Provider",
                                value = currentProviderLabel,
                                onClick = {
                                    haptics.play(Haptic.Select)
                                    showProviderDialog = true
                                },
                            )

                            HorizontalDivider(
                                color = LyricsCardDivider,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 58.dp),
                            )

                            // API Key row
                            val hasKey = settingsState.lyricsTranslationApiKey.isNotBlank()
                            val keyPreview = if (hasKey) {
                                "••••••••" + settingsState.lyricsTranslationApiKey.takeLast(4)
                            } else {
                                "Not Configured"
                            }
                            LyricsActionRow(
                                icon = Icons.Rounded.Key,
                                title = "API Key",
                                value = keyPreview,
                                valueColor = if (hasKey) AppleTextSecondary else Color(0xFFFF9F0A),
                                onClick = {
                                    haptics.play(Haptic.Select)
                                    showApiKeyDialog = true
                                },
                            )

                            HorizontalDivider(
                                color = LyricsCardDivider,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 58.dp),
                            )

                            // Base URL row
                            val hasCustomUrl = settingsState.lyricsTranslationBaseUrl.isNotBlank()
                            val urlPreview = if (hasCustomUrl) {
                                settingsState.lyricsTranslationBaseUrl
                            } else {
                                "Default"
                            }
                            LyricsActionRow(
                                icon = Icons.Rounded.Link,
                                title = "Base URL",
                                value = urlPreview,
                                onClick = {
                                    haptics.play(Haptic.Select)
                                    showBaseUrlDialog = true
                                },
                            )

                            HorizontalDivider(
                                color = LyricsCardDivider,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 58.dp),
                            )

                            // Model row
                            val effectiveModel = settingsState.lyricsTranslationModel.ifBlank {
                                LyricsTranslationHelper.defaultModel(settingsState.lyricsTranslationProvider)
                            }
                            LyricsActionRow(
                                icon = Icons.Rounded.SmartToy,
                                title = "Model",
                                value = if (settingsState.lyricsTranslationModel.isBlank()) "$effectiveModel (default)" else effectiveModel,
                                onClick = {
                                    haptics.play(Haptic.Select)
                                    showModelDialog = true
                                },
                            )

                            HorizontalDivider(
                                color = LyricsCardDivider,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 58.dp),
                            )

                            // Target Language row
                            val currentLangName = LyricsTranslationHelper.AiTranslationLanguages[settingsState.lyricsTranslateLanguage]
                                ?: settingsState.lyricsTranslateLanguage
                            LyricsActionRow(
                                icon = Icons.Rounded.Language,
                                title = "Target Language",
                                value = currentLangName,
                                onClick = {
                                    haptics.play(Haptic.Select)
                                    showLanguageDialog = true
                                },
                            )

                            HorizontalDivider(
                                color = LyricsCardDivider,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 58.dp),
                            )

                            // Translation Mode row
                            LyricsActionRow(
                                icon = Icons.Rounded.Translate,
                                title = "Translation Mode",
                                value = settingsState.lyricsTranslateMode,
                                onClick = {
                                    haptics.play(Haptic.Select)
                                    showModeDialog = true
                                },
                            )

                            HorizontalDivider(
                                color = LyricsCardDivider,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 58.dp),
                            )

                            // Translation Prompt row
                            val hasCustomPrompt = settingsState.lyricsTranslationCustomPrompt.isNotBlank()
                            val promptPreview = if (hasCustomPrompt) "Custom Prompt" else "Default"
                            LyricsActionRow(
                                icon = Icons.Rounded.Edit,
                                title = "Custom Prompt Instructions",
                                value = promptPreview,
                                onClick = {
                                    haptics.play(Haptic.Select)
                                    showPromptDialog = true
                                },
                            )

                            HorizontalDivider(
                                color = LyricsCardDivider,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 58.dp),
                            )

                            // Translate actual lines row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF34C759).copy(alpha = 0.22f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Sync,
                                        contentDescription = null,
                                        tint = Color(0xFF30D158),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.ui_translate_actual_lines),
                                        color = Color.White,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (settingsState.lyricsReplaceOriginal)
                                            "Replaces original lines with translation"
                                        else
                                            "Displays translated subtitles below each line",
                                        color = AppleTextSecondary,
                                        fontSize = 12.sp,
                                        lineHeight = 15.sp,
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                AppleGlassSwitch(
                                    checked = settingsState.lyricsReplaceOriginal,
                                    onCheckedChange = { checked ->
                                        haptics.play(if (checked) Haptic.ToggleOn else Haptic.ToggleOff)
                                        SettingsStore.setLyricsReplaceOriginal(context, checked)
                                    },
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.ui_translate_lyrics_on_demand_for_any_song_from_the_playe),
                        color = Color(0xFF636366),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }

                // Defaults / Reset Section
                item(key = "reset_section") {
                    Spacer(modifier = Modifier.height(24.dp))
                    LyricsUniformCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptics.play(Haptic.Tap)
                                    SettingsStore.resetLyricsProviders(context)
                                    Toast.makeText(context, context.getString(R.string.toast_lyrics_providers_reset_to_defaults), Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = AppleMusicRed,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.ui_reset_provider_order_defaults),
                                color = AppleMusicRed,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.ui_restores_lrc_lib_kugou_and_simpmusic_as_top_preferred),
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
                    FrostedTopBarTitlePill(hazeState = hazeState) {
                        Text(
                            text = stringResource(R.string.ui_lyrics_providers),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
            )
        }

        if (showProviderDialog) {
            AiProviderPickerDialog(
                currentProvider = settingsState.lyricsTranslationProvider,
                haptics = haptics,
                onSelect = { providerId ->
                    if (!providerId.equals(settingsState.lyricsTranslationProvider, ignoreCase = true)) {
                        // A model id / URL from another provider would answer 404 here.
                        SettingsStore.setLyricsTranslationModel(context, "")
                        SettingsStore.setLyricsTranslationBaseUrl(context, "")
                    }
                    SettingsStore.setLyricsTranslationProvider(context, providerId)
                    LyricsTranslationHelper.clearCache()
                    showProviderDialog = false
                    Toast.makeText(context, context.getString(R.string.toast_provider_updated_to, providerId), Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showProviderDialog = false },
            )
        }

        if (showApiKeyDialog) {
            val providerLabel = LyricsTranslationHelper.SUPPORTED_PROVIDERS
                .firstOrNull { it.first == settingsState.lyricsTranslationProvider }?.second
                ?: settingsState.lyricsTranslationProvider
            AiApiKeyDialog(
                providerName = providerLabel,
                currentKey = settingsState.lyricsTranslationApiKey,
                haptics = haptics,
                onSave = { key ->
                    SettingsStore.setLyricsTranslationApiKey(context, key)
                    LyricsTranslationHelper.clearCache()
                    showApiKeyDialog = false
                    Toast.makeText(context, context.getString(R.string.toast_api_key_saved), Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showApiKeyDialog = false },
            )
        }

        if (showBaseUrlDialog) {
            val defaultUrl = LyricsTranslationHelper.defaultBaseUrl(settingsState.lyricsTranslationProvider)
            AiBaseUrlDialog(
                currentUrl = settingsState.lyricsTranslationBaseUrl,
                defaultUrl = defaultUrl,
                haptics = haptics,
                onSave = { url ->
                    SettingsStore.setLyricsTranslationBaseUrl(context, url)
                    LyricsTranslationHelper.clearCache()
                    showBaseUrlDialog = false
                    Toast.makeText(context, context.getString(R.string.toast_base_url_updated), Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showBaseUrlDialog = false },
            )
        }

        if (showModelDialog) {
            AiModelPickerDialog(
                provider = settingsState.lyricsTranslationProvider,
                apiKey = settingsState.lyricsTranslationApiKey,
                baseUrl = settingsState.lyricsTranslationBaseUrl,
                currentModel = settingsState.lyricsTranslationModel,
                haptics = haptics,
                onSelect = { model ->
                    SettingsStore.setLyricsTranslationModel(context, model)
                    LyricsTranslationHelper.clearCache()
                    showModelDialog = false
                    Toast.makeText(
                        context,
                        if (model.isBlank()) "Using default model" else "Model set to $model",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                onDismiss = { showModelDialog = false },
            )
        }

        if (showPromptDialog) {
            AiCustomPromptDialog(
                currentPrompt = settingsState.lyricsTranslationCustomPrompt,
                haptics = haptics,
                onSave = { prompt ->
                    SettingsStore.setLyricsTranslationCustomPrompt(context, prompt)
                    LyricsTranslationHelper.clearCache()
                    showPromptDialog = false
                    Toast.makeText(context, context.getString(R.string.toast_translation_prompt_updated), Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showPromptDialog = false },
            )
        }

        if (showLanguageDialog) {
            AiLanguagePickerDialog(
                currentLanguageCode = settingsState.lyricsTranslateLanguage,
                haptics = haptics,
                onSelect = { code ->
                    SettingsStore.setLyricsTranslateLanguage(context, code)
                    LyricsTranslationHelper.clearCache()
                    showLanguageDialog = false
                    val langName = LyricsTranslationHelper.AiTranslationLanguages[code] ?: code
                    Toast.makeText(context, context.getString(R.string.toast_target_language_set_to, langName), Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showLanguageDialog = false },
            )
        }

        if (showModeDialog) {
            AiModePickerDialog(
                currentMode = settingsState.lyricsTranslateMode,
                haptics = haptics,
                onSelect = { mode ->
                    SettingsStore.setLyricsTranslateMode(context, mode)
                    LyricsTranslationHelper.clearCache()
                    showModeDialog = false
                    Toast.makeText(context, context.getString(R.string.toast_translation_mode_set_to, mode), Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showModeDialog = false },
            )
        }
    }
}

@Composable
private fun LyricsProviderRow(
    provider: LyricsProviderInfo,
    rank: Int,
    isEnabled: Boolean,
    providerIndex: Int,
    providerCount: Int,
    onMove: (Int, Int) -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    var dragOffsetPx by remember(provider.id) { mutableFloatStateOf(0f) }
    val rowAlpha by animateFloatAsState(
        targetValue = if (isEnabled) 1f else 0.48f,
        animationSpec = tween(180),
        label = "providerRowAlpha",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = dragOffsetPx }
            .alpha(rowAlpha)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Provider icon with subtle container
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(LyricsIconContainerBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(provider.iconRes),
                contentDescription = null,
                tint = if (isEnabled) Color.White else Color(0xFF8E8E93),
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and description
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = provider.displayName,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(LyricsPillBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.ui_text_adebc50ade, rank),
                        color = Color(0xFF8E8E93),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isEnabled) provider.description else "Disabled (skipped during search)",
                color = if (isEnabled) AppleTextSecondary else Color(0xFFFF453A).copy(alpha = 0.8f),
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        ReorderDragHandle(
            index = providerIndex,
            itemCount = providerCount,
            onMove = onMove,
            itemStep = 58.dp,
            tint = Color(0xFF8E8E93),
            modifier = Modifier.size(28.dp),
            contentDescription = stringResource(R.string.ui_reorder_provider),
            onDragDistance = { dragOffsetPx += it },
            onDragFinished = { dragOffsetPx = 0f },
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Toggle Switch
        AppleGlassSwitch(
            checked = isEnabled,
            onCheckedChange = onToggle,
        )
    }
}

@Composable
private fun LyricsSectionHeader(title: String) {
    Text(
        text = title,
        color = Color(0xFF8E8E93),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun LyricsUniformCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(LyricsCardGrey),
    ) {
        content()
    }
}

@Composable
private fun LyricsActionRow(
    icon: ImageVector,
    title: String,
    value: String,
    valueColor: Color = AppleTextSecondary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(LyricsIconContainerBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 13.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 140.dp)
                .padding(end = 4.dp),
        )
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.28f),
            modifier = Modifier.size(18.dp),
        )
    }
}
// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

@Composable
private fun AiProviderPickerDialog(
    currentProvider: String,
    haptics: com.darkxvenom.itune.ui.artist.haptics.Haptics,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val providers = LyricsTranslationHelper.SUPPORTED_PROVIDERS

    SettingsModalFrame(onDismiss = onDismiss) {
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.ui_ai_provider),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = stringResource(R.string.ui_select_the_engine_to_use_for_line_by_line_lyrics_trans),
            color = AppleTextSecondary,
            fontSize = 12.5.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            providers.forEachIndexed { idx, (providerId, label) ->
                val selected = providerId.equals(currentProvider, ignoreCase = true)
                HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.10f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptics.play(Haptic.Select)
                            onSelect(providerId)
                        }
                        .padding(horizontal = 20.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = label,
                            color = if (selected) AppleMusicRed else Color.White,
                            fontSize = 15.5.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(
                            text = when (providerId) {
                                LyricsTranslationHelper.PROVIDER_OPENROUTER -> "Multi-model router (GPT-4o, Claude, etc.)"
                                LyricsTranslationHelper.PROVIDER_GEMINI -> "Fast & expressive Google AI"
                                LyricsTranslationHelper.PROVIDER_OPENAI -> "Official ChatGPT / OpenAI API"
                                LyricsTranslationHelper.PROVIDER_CLAUDE -> "Anthropic Claude API"
                                else -> "Standard LLM API"
                            },
                            color = Color.White.copy(alpha = 0.50f),
                            fontSize = 12.sp,
                        )
                    }
                    if (selected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.ui_selected),
                            tint = AppleMusicRed,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
                if (idx == providers.lastIndex) {
                    HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.10f))
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.cancel),
                color = Color(0xFF8E8E93),
                fontSize = 16.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun AiApiKeyDialog(
    providerName: String,
    currentKey: String,
    haptics: com.darkxvenom.itune.ui.artist.haptics.Haptics,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var fieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = currentKey,
                selection = TextRange(currentKey.length),
            ),
        )
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val cleanText = fieldValue.text.trim()

    SettingsModalFrame(onDismiss = onDismiss) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.ui_api_key, providerName),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = stringResource(R.string.ui_your_api_key_is_stored_securely_on_device_and_never_sh),
            color = AppleTextSecondary,
            fontSize = 12.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (fieldValue.text.isEmpty()) {
                Text(
                    text = stringResource(R.string.ui_paste_your_api_key_here),
                    color = Color.White.copy(alpha = 0.38f),
                    fontSize = 14.sp,
                )
            }
            BasicTextField(
                value = fieldValue,
                onValueChange = { fieldValue = it },
                singleLine = true,
                cursorBrush = SolidColor(AppleMusicRed),
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        haptics.play(Haptic.Select)
                        onSave(cleanText)
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }

        Spacer(Modifier.height(18.dp))
        HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.12f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable {
                    haptics.play(Haptic.Select)
                    onSave(cleanText)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.save),
                color = AppleMusicRed,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.12f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.cancel),
                color = Color(0xFF8E8E93),
                fontSize = 16.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun AiBaseUrlDialog(
    currentUrl: String,
    defaultUrl: String,
    haptics: com.darkxvenom.itune.ui.artist.haptics.Haptics,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var fieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = currentUrl,
                selection = TextRange(currentUrl.length),
            ),
        )
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val cleanText = fieldValue.text.trim()

    SettingsModalFrame(onDismiss = onDismiss) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.ui_api_base_url),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = stringResource(R.string.ui_leave_empty_to_use_default_endpoint_for_this_provider),
            color = AppleTextSecondary,
            fontSize = 12.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (fieldValue.text.isEmpty()) {
                Text(
                    text = defaultUrl,
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = fieldValue,
                onValueChange = { fieldValue = it },
                singleLine = true,
                cursorBrush = SolidColor(AppleMusicRed),
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        haptics.play(Haptic.Select)
                        onSave(cleanText)
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }

        Spacer(Modifier.height(18.dp))
        HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.12f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable {
                    haptics.play(Haptic.Select)
                    onSave(cleanText)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.save),
                color = AppleMusicRed,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (cleanText.isNotEmpty()) {
            HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.12f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable {
                        haptics.play(Haptic.Tap)
                        onSave("")
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.ui_reset_to_default_url),
                    color = Color(0xFF0A84FF),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.12f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.cancel),
                color = Color(0xFF8E8E93),
                fontSize = 16.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun AiCustomPromptDialog(
    currentPrompt: String,
    haptics: com.darkxvenom.itune.ui.artist.haptics.Haptics,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var fieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = currentPrompt,
                selection = TextRange(currentPrompt.length),
            ),
        )
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val cleanText = fieldValue.text.trim()

    SettingsModalFrame(onDismiss = onDismiss) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.ui_translation_prompt),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "Add custom instructions (e.g. \"Translate to poetic French\"). Leave empty for default.",
            color = AppleTextSecondary,
            fontSize = 12.5.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (fieldValue.text.isEmpty()) {
                Text(
                    text = stringResource(R.string.ui_translate_the_following_song_lyrics_lines_to_english_p),
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                )
            }
            BasicTextField(
                value = fieldValue,
                onValueChange = { fieldValue = it },
                minLines = 3,
                maxLines = 5,
                cursorBrush = SolidColor(AppleMusicRed),
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }

        Spacer(Modifier.height(18.dp))
        HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.12f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable {
                    haptics.play(Haptic.Select)
                    onSave(cleanText)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.save),
                color = AppleMusicRed,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (cleanText.isNotEmpty()) {
            HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.12f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable {
                        haptics.play(Haptic.Tap)
                        onSave("")
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.ui_clear_use_default),
                    color = Color(0xFF0A84FF),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.12f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.cancel),
                color = Color(0xFF8E8E93),
                fontSize = 16.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun AiLanguagePickerDialog(
    currentLanguageCode: String,
    haptics: com.darkxvenom.itune.ui.artist.haptics.Haptics,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val allLanguages = remember { LyricsTranslationHelper.AiTranslationLanguages.toList() }
    val filteredLanguages = remember(searchQuery) {
        if (searchQuery.isBlank()) allLanguages
        else allLanguages.filter { (code, name) ->
            name.contains(searchQuery, ignoreCase = true) || code.contains(searchQuery, ignoreCase = true)
        }
    }

    SettingsModalFrame(onDismiss = onDismiss) {
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.ui_target_language_ec8afa0d),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = stringResource(R.string.ui_select_the_target_language_for_lyrics_translation),
            color = AppleTextSecondary,
            fontSize = 12.5.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(14.dp))

        // Search field
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            if (searchQuery.isEmpty()) {
                Text(
                    text = stringResource(R.string.ui_search_languages),
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 13.sp,
                )
            }
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                cursorBrush = SolidColor(AppleMusicRed),
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 13.sp,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            filteredLanguages.forEach { (code, name) ->
                val selected = code == currentLanguageCode
                HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.10f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptics.play(Haptic.Select)
                            onSelect(code)
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = name,
                        color = if (selected) AppleMusicRed else Color.White,
                        fontSize = 14.5.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = AppleMusicRed,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.12f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.cancel),
                color = Color(0xFF8E8E93),
                fontSize = 16.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun AiModePickerDialog(
    currentMode: String,
    haptics: com.darkxvenom.itune.ui.artist.haptics.Haptics,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val modes = listOf(
        "Literal" to "Literal (Preserve poetic rhyme & rhythm)",
        "Hinglish" to "Hinglish (Hindi written in English alphabet)",
        "Romanized" to "Romanized (Latin alphabet transliteration)",
        "Transcribed" to "Transcribed (Phonetic script of language)",
        "Custom" to "Custom Prompt Instructions",
    )

    SettingsModalFrame(onDismiss = onDismiss) {
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.ui_translation_mode),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = stringResource(R.string.ui_select_the_default_prompt_template_for_lyrics_translat),
            color = AppleTextSecondary,
            fontSize = 12.5.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(14.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            modes.forEach { (modeKey, modeDesc) ->
                val selected = modeKey == currentMode
                HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.10f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptics.play(Haptic.Select)
                            onSelect(modeKey)
                        }
                        .padding(horizontal = 20.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = modeKey,
                            color = if (selected) AppleMusicRed else Color.White,
                            fontSize = 15.5.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(
                            text = modeDesc,
                            color = AppleTextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                        )
                    }
                    if (selected) {
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = AppleMusicRed,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.12f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.cancel),
                color = Color(0xFF8E8E93),
                fontSize = 16.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun AiModelPickerDialog(
    provider: String,
    apiKey: String,
    baseUrl: String,
    currentModel: String,
    haptics: com.darkxvenom.itune.ui.artist.haptics.Haptics,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaultModel = LyricsTranslationHelper.defaultModel(provider)
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var models by remember { mutableStateOf(LyricsTranslationHelper.suggestedModels(provider)) }

    // Ask the provider which models this key can actually use.
    LaunchedEffect(provider, apiKey, baseUrl) {
        loading = true
        loadError = null
        if (apiKey.isBlank()) {
            loading = false
            loadError = "Add your API key to load the live model list."
            return@LaunchedEffect
        }
        LyricsTranslationHelper.fetchModels(provider, apiKey, baseUrl)
            .onSuccess { models = it }
            .onFailure { loadError = it.message ?: "Couldn't load models." }
        loading = false
    }

    val trimmed = query.trim()
    val filtered = remember(models, trimmed) {
        if (trimmed.isEmpty()) models else models.filter { it.contains(trimmed, ignoreCase = true) }
    }
    val effectiveCurrent = currentModel.ifBlank { defaultModel }

    SettingsModalFrame(onDismiss = onDismiss) {
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.ui_model_68c2cc7f),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = when {
                loading -> "Loading available models…"
                loadError != null -> "$loadError Showing suggested models. You can also type any model id."
                else -> "${models.size} models available. Type to search or enter a custom id."
            },
            color = AppleTextSecondary,
            fontSize = 12.5.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.ui_search_or_type_a_model_id),
                    color = Color.White.copy(alpha = 0.38f),
                    fontSize = 14.sp,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                cursorBrush = SolidColor(AppleMusicRed),
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (trimmed.isNotEmpty()) {
                            haptics.play(Haptic.Select)
                            onSelect(trimmed)
                        }
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(10.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp)) {
            item(key = "__default") {
                ModelRow(
                    label = "Default",
                    detail = defaultModel,
                    selected = currentModel.isBlank(),
                    onClick = {
                        haptics.play(Haptic.Select)
                        onSelect("")
                    },
                )
            }
            if (trimmed.isNotEmpty() && models.none { it.equals(trimmed, ignoreCase = true) }) {
                item(key = "__custom") {
                    ModelRow(
                        label = "Use \"$trimmed\"",
                        detail = "Custom model id",
                        selected = false,
                        onClick = {
                            haptics.play(Haptic.Select)
                            onSelect(trimmed)
                        },
                    )
                }
            }
            items(filtered, key = { it }) { model ->
                ModelRow(
                    label = model,
                    detail = null,
                    selected = currentModel.isNotBlank() && model == effectiveCurrent,
                    onClick = {
                        haptics.play(Haptic.Select)
                        onSelect(model)
                    },
                )
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.10f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.cancel),
                color = Color(0xFF8E8E93),
                fontSize = 16.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ModelRow(
    label: String,
    detail: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.10f))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (selected) AppleMusicRed else Color.White,
                fontSize = 14.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(text = detail, color = Color.White.copy(alpha = 0.50f), fontSize = 12.sp, maxLines = 1)
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.ui_selected),
                tint = AppleMusicRed,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}
