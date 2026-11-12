package com.darkxvenom.itune.ui.settings

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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
                        text = "Lyrics",
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
                                    text = "Time-Synced Lyrics",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Real-time syllable and line scrolling with live audio",
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
                            text = "PROVIDER PRIORITY (FALLBACK ORDER)",
                            color = Color(0xFF8E8E93),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp,
                        )
                        Text(
                            text = "$activeCount of ${orderedProviders.size} Active",
                            color = if (activeCount > 0) Color(0xFF34C759) else Color(0xFFFF453A),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(
                        text = "iTune queries lyrics in the exact precedence below. If the highest active provider has no synced lyrics, iTune seamlessly falls back to the next one.",
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
                                    canMoveUp = index > 0,
                                    canMoveDown = index < orderedProviders.lastIndex,
                                    onMoveUp = {
                                        haptics.play(Haptic.Select)
                                        SettingsStore.moveLyricsProvider(context, index, index - 1)
                                    },
                                    onMoveDown = {
                                        haptics.play(Haptic.Select)
                                        SettingsStore.moveLyricsProvider(context, index, index + 1)
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
                                        text = "Translate actual lines",
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
                        text = "Translate lyrics on-demand for any song from the player menu (... > Lyrics > Translate). When 'Translate actual lines' is off, translations appear as subtitles below the original text.",
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
                                    Toast.makeText(context, "Lyrics providers reset to defaults", Toast.LENGTH_SHORT).show()
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
                                text = "Reset Provider Order & Defaults",
                                color = AppleMusicRed,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Restores LRC Lib, KuGou, and SimpMusic as top preferred providers and re-enables all 11 sources.",
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
                            text = "Lyrics Providers",
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
                    SettingsStore.setLyricsTranslationProvider(context, providerId)
                    LyricsTranslationHelper.clearCache()
                    showProviderDialog = false
                    Toast.makeText(context, "Provider updated to $providerId", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(context, "API Key saved", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(context, "Base URL updated", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showBaseUrlDialog = false },
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
                    Toast.makeText(context, "Translation prompt updated", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(context, "Target language set to $langName", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(context, "Translation mode set to $mode", Toast.LENGTH_SHORT).show()
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
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val rowAlpha by animateFloatAsState(
        targetValue = if (isEnabled) 1f else 0.48f,
        animationSpec = tween(180),
        label = "providerRowAlpha",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                        text = "#$rank",
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

        // Move Up / Move Down buttons for priority ordering
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(
                onClick = onMoveUp,
                enabled = canMoveUp,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "Move up",
                    tint = if (canMoveUp) Color(0xFF0A84FF) else Color(0xFF3A3A3C),
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(
                onClick = onMoveDown,
                enabled = canMoveDown,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Move down",
                    tint = if (canMoveDown) Color(0xFF0A84FF) else Color(0xFF3A3A3C),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

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
    content: @Composable () -> Unit,
) {
    Box(
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
            text = "AI Provider",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "Select the engine to use for line-by-line lyrics translation.",
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
                            contentDescription = "Selected",
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
                text = "Cancel",
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
            text = "$providerName API Key",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "Your API key is stored securely on device and never shared.",
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
                    text = "Paste your API key here",
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
                text = "Save",
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
                text = "Cancel",
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
            text = "API Base URL",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "Leave empty to use default endpoint for this provider.",
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
                text = "Save",
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
                    text = "Reset to Default URL",
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
                text = "Cancel",
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
            text = "Translation Prompt",
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
                    text = "Translate the following song lyrics lines to English. Preserve poetic flow.",
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
                text = "Save",
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
                    text = "Clear & Use Default",
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
                text = "Cancel",
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
            text = "Target Language",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "Select the target language for lyrics translation.",
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
                    text = "Search languages...",
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
                text = "Cancel",
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
            text = "Translation Mode",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "Select the default prompt template for lyrics translation.",
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
                text = "Cancel",
                color = Color(0xFF8E8E93),
                fontSize = 16.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
