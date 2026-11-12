package com.darkxvenom.itune.ui.settings

import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.darkxvenom.itune.R
import com.darkxvenom.itune.audio.AudioEngineManager
import com.darkxvenom.itune.backend.MusicCacheManager
import com.darkxvenom.itune.audio.AudioEngineUiState
import com.darkxvenom.itune.audio.AudioOutputManager
import com.darkxvenom.itune.data.auth.GoogleAccountSession
import com.darkxvenom.itune.data.auth.GoogleAuthStore
import com.darkxvenom.itune.data.auth.YouTubeLibrarySyncManager
import com.darkxvenom.itune.data.auth.YouTubeProfile
import com.darkxvenom.itune.data.library.ContentLocaleCatalog
import com.darkxvenom.itune.data.library.SearchHistoryStore
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.data.library.SettingsUiState
import com.darkxvenom.itune.ishare.IShareConnectionMode
import com.darkxvenom.itune.ishare.IShareSession
import com.darkxvenom.itune.ishare.IShareSyncManager
import com.darkxvenom.itune.island.DynamicIslandStore
import com.darkxvenom.itune.ui.artist.components.BottomFadeScrim
import com.darkxvenom.itune.ui.artist.components.FrostedTopBar
import com.darkxvenom.itune.ui.artist.components.FrostedTopBarTitlePill
import com.darkxvenom.itune.ui.artist.components.LocalAppBackdrop
import com.darkxvenom.itune.ui.artist.components.LocalLiquidGlassEnabled
import com.darkxvenom.itune.ui.artist.components.LocalReduceDynamicBlur
import com.darkxvenom.itune.ui.artist.components.TopFadeBlur
import com.darkxvenom.itune.ui.artist.components.backdrop.backdrops.layerBackdrop
import com.darkxvenom.itune.ui.artist.components.backdrop.backdrops.rememberLayerBackdrop
import com.darkxvenom.itune.ui.artist.components.backdrop.backdrops.rememberSnapshotBackdrop
import com.darkxvenom.itune.ui.artist.components.backdrop.backdrops.snapshotBackdropSource
import com.darkxvenom.itune.ui.artist.components.isGlassSupported
import com.darkxvenom.itune.ui.artist.components.topBarContentPadding
import com.darkxvenom.itune.ui.artist.haptics.Haptic
import com.darkxvenom.itune.ui.artist.haptics.Haptics
import com.darkxvenom.itune.ui.artist.haptics.rememberHaptics
import com.darkxvenom.itune.ui.artist.icons.ITubeIcons
import com.darkxvenom.itune.ui.artist.icons.SfIcons
import com.darkxvenom.itune.ui.artist.theme.ITubeArtistTheme
import com.darkxvenom.itune.ui.components.AppleMemojiAvatar
import com.darkxvenom.itune.ui.components.springOverscroll
import com.darkxvenom.itune.ui.components.rememberSpringOverscrollState
import com.darkxvenom.itune.ui.library.LibraryCrimsonAccent
import com.darkxvenom.itune.ui.library.LibraryFilterBar
import com.darkxvenom.itune.ui.library.LibraryGlassEmptyCard
import com.darkxvenom.itune.ui.player.EnginePopupPage
import com.darkxvenom.itune.ui.player.SFProDisplay
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import com.darkxvenom.itune.ui.theme.AppleTextSecondary
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

private val SettingsAppleGreen = Color(0xFF34C759)
private val SettingsDialogBg = Color(0xFF1C1C1E)
internal val SettingsScreenBlack = Color(0xFF000000)
internal val SettingsCardGrey = Color(0xFF1C1C1E)
internal val SettingsDividerGrey = Color(0xFF2C2C2E)

private data class SettingsOptionChoice(
    val label: String,
    val detail: String,
)

private sealed interface SettingsEntry {
    val id: String
    val title: String
    val subtitle: String?
    @get:DrawableRes
    val iconRes: Int
    val keywords: List<String>

    data class Toggle(
        override val id: String,
        override val title: String,
        override val subtitle: String?,
        @param:DrawableRes override val iconRes: Int,
        override val keywords: List<String>,
        val checked: Boolean,
        val onToggle: (Boolean) -> Unit,
    ) : SettingsEntry

    data class Picker(
        override val id: String,
        override val title: String,
        override val subtitle: String?,
        @param:DrawableRes override val iconRes: Int,
        override val keywords: List<String>,
        val currentValue: String,
        val description: String,
        val options: List<SettingsOptionChoice>,
        val onSelect: (String) -> Unit,
    ) : SettingsEntry

    data class Action(
        override val id: String,
        override val title: String,
        override val subtitle: String?,
        @param:DrawableRes override val iconRes: Int,
        override val keywords: List<String>,
        val trailingValue: String? = null,
        val badgeCount: Int? = null,
        val onAction: () -> Unit,
    ) : SettingsEntry

    data class EffectToggleAction(
        override val id: String,
        override val title: String,
        override val subtitle: String?,
        @param:DrawableRes override val iconRes: Int,
        override val keywords: List<String>,
        val checked: Boolean,
        val switchEnabled: Boolean = true,
        val onToggle: (Boolean) -> Unit,
        val onOpenDetails: () -> Unit,
    ) : SettingsEntry

    data class SegmentedToggle(
        override val id: String,
        override val title: String,
        override val subtitle: String?,
        @param:DrawableRes override val iconRes: Int,
        override val keywords: List<String>,
        val options: List<String>,
        val selectedOption: String,
        val onSelect: (String) -> Unit,
    ) : SettingsEntry
}

private data class SettingsSection(
    val id: String,
    val title: String,
    val entries: List<SettingsEntry>,
)

private fun SettingsEntry.matches(query: String, sectionTitle: String): Boolean {
    if (query.isBlank()) return true
    val needle = query.trim()
    val trailing = when (this) {
        is SettingsEntry.Picker -> currentValue
        is SettingsEntry.Action -> trailingValue.orEmpty()
        is SettingsEntry.Toggle -> if (checked) "on enabled" else "off disabled"
        is SettingsEntry.EffectToggleAction -> if (checked) "on enabled active" else "off disabled"
        is SettingsEntry.SegmentedToggle -> "$selectedOption ${options.joinToString(" ")}"
    }
    return title.contains(needle, ignoreCase = true) ||
        subtitle?.contains(needle, ignoreCase = true) == true ||
        sectionTitle.contains(needle, ignoreCase = true) ||
        trailing.contains(needle, ignoreCase = true) ||
        keywords.any { it.contains(needle, ignoreCase = true) }
}

/**
 * Apple iOS-inspired Dark Liquid Glass Settings Screen featuring:
 * - Artist-screen FrostedTopBar with Back & Search controls
 * - Search across all account and music preference rows
 * - Apple ID-style Account Hero Card that launches AvatarSelectionDialog
 * - Persisted music, playback, downloads, library, and general setting groups
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun AppleSettingsScreen(
    hasMiniPlayer: Boolean,
    onBack: () -> Unit,
    onAccountClick: () -> Unit,
    onOpenIShare: () -> Unit = {},
    onOpenDynamicIsland: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        SettingsStore.ensureInit(context)
        SearchHistoryStore.ensureInit(context)
        IShareSyncManager.start(context)
        DynamicIslandStore.ensureInit(context)
    }

    val state by SettingsStore.state.collectAsStateWithLifecycle()
    val searchHistory by SearchHistoryStore.history.collectAsStateWithLifecycle()
    val iShareSession by IShareSyncManager.session.collectAsStateWithLifecycle()
    val iShareMode by IShareSyncManager.connectionMode.collectAsStateWithLifecycle()
    val dynamicIslandConfig by DynamicIslandStore.state.collectAsStateWithLifecycle()
    val audioEngineState by AudioEngineManager.state.collectAsStateWithLifecycle()
    val audioOutputState by AudioOutputManager.state.collectAsStateWithLifecycle()
    val ytAccountState by GoogleAuthStore.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        GoogleAuthStore.ensureInit(context)
    }

    val appBackdrop = rememberLayerBackdrop()
    val pageBackdrop = rememberSnapshotBackdrop()
    val hazeState = remember { HazeState() }
    val listState = rememberLazyListState()
    val overscrollState = rememberSpringOverscrollState()

    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var focusSearchNext by remember { mutableStateOf(false) }

    var activePicker by remember { mutableStateOf<SettingsEntry.Picker?>(null) }
    var activeAudioEffectPage by remember { mutableStateOf<EnginePopupPage?>(null) }
    var showProfileAccount by remember { mutableStateOf(false) }
    var showLyricsSettings by rememberSaveable { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = showLyricsSettings && !showProfileAccount && activeAudioEffectPage == null && activePicker == null && !showNameDialog && !showAboutDialog) {
        showLyricsSettings = false
    }

    BackHandler(enabled = showProfileAccount && activeAudioEffectPage == null && activePicker == null && !showNameDialog && !showAboutDialog) {
        showProfileAccount = false
    }

    BackHandler(enabled = activeAudioEffectPage != null && !showProfileAccount && activePicker == null && !showNameDialog && !showAboutDialog) {
        activeAudioEffectPage = null
    }

    BackHandler(enabled = searchOpen && !showProfileAccount && activeAudioEffectPage == null && activePicker == null && !showNameDialog && !showAboutDialog) {
        searchOpen = false
        searchQuery = ""
    }

    var showClearSongCacheDialog by remember { mutableStateOf(false) }

    val sections = remember(
        state,
        searchHistory.size,
        iShareSession,
        iShareMode,
        dynamicIslandConfig,
        audioEngineState,
        audioOutputState.bitPerfectActive,
        audioOutputState.activeDevice,
        MusicCacheManager.cachedTracks.size,
        MusicCacheManager.cachingEnabled.value,
        MusicCacheManager.maxCacheLimitMB.longValue,
        MusicCacheManager.totalStorageBytes.longValue,
    ) {
        buildSettingsSections(
            state = state,
            audioEngine = audioEngineState,
            bitPerfectActive = audioOutputState.bitPerfectActive,
            activeOutputName = audioOutputState.activeDevice?.name ?: "Phone Speaker",
            searchHistoryCount = searchHistory.size,
            iShareSession = iShareSession,
            iShareIsLan = iShareMode == IShareConnectionMode.LAN,
            dynamicIslandEnabled = dynamicIslandConfig.enabled && android.provider.Settings.canDrawOverlays(context),
            onOpenIShare = onOpenIShare,
            onOpenDynamicIsland = onOpenDynamicIsland,
            onOpenAudioEffect = { page -> activeAudioEffectPage = page },
            onOpenLyricsSettings = { showLyricsSettings = true },
            onToggleBool = { key, enabled -> SettingsStore.setBoolean(context, key, enabled) },
            onSelectString = { key, value -> SettingsStore.setString(context, key, value) },
            onClearCache = {
                SettingsStore.clearAudioCache(context)
                Toast.makeText(context, "Audio & artwork cache cleared", Toast.LENGTH_SHORT).show()
            },
            onClearSongCache = { showClearSongCacheDialog = true },
            onEditName = { showNameDialog = true },
            onClearSearchHistory = {
                SearchHistoryStore.clearAll(context)
                Toast.makeText(context, "Search history cleared", Toast.LENGTH_SHORT).show()
            },
            onOpenAbout = { showAboutDialog = true },
        )
    }

    val trimmedQuery = searchQuery.trim()
    val filteredSections = remember(sections, trimmedQuery) {
        if (trimmedQuery.isEmpty()) {
            sections
        } else {
            sections.mapNotNull { section ->
                val matched = section.entries.filter { it.matches(trimmedQuery, section.title) }
                if (matched.isEmpty()) null else section.copy(entries = matched)
            }
        }
    }

    val showAccountCard = remember(state.displayName, state.accountSubtitle, trimmedQuery) {
        if (trimmedQuery.isEmpty()) {
            true
        } else {
            val haystack = "Account Profile Avatar Memoji Apple ID ${state.displayName} ${state.accountSubtitle}"
            haystack.contains(trimmedQuery, ignoreCase = true)
        }
    }

    val totalMatchedCount = remember(filteredSections, showAccountCard, trimmedQuery) {
        if (trimmedQuery.isEmpty()) 0
        else filteredSections.sumOf { it.entries.size } + if (showAccountCard) 1 else 0
    }

    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 96
        }
    }

    val glassActive = isGlassSupported() && state.liquidGlassChrome
    val contentPadding = PaddingValues(
        top = topBarContentPadding() + 6.dp,
        bottom = if (hasMiniPlayer) 210.dp else 140.dp,
    )

    ITubeArtistTheme(darkTheme = true) {
        CompositionLocalProvider(
            LocalAppBackdrop provides appBackdrop,
            LocalLiquidGlassEnabled provides glassActive,
            LocalReduceDynamicBlur provides !state.liquidGlassChrome,
        ) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(AppleOledBlack),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .snapshotBackdropSource(pageBackdrop, floor = AppleOledBlack)
                        .hazeSource(state = hazeState),
                ) {
                    SettingsBlackGlassBackdrop(
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(appBackdrop),
                    )

                    LazyColumn(
                        state = listState,
                        contentPadding = contentPadding,
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .springOverscroll(overscrollState),
                    ) {
                        item(key = "settings_header") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 18.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom,
                                ) {
                                    Text(
                                        text = "Settings",
                                        color = Color.White,
                                        fontSize = 34.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.6).sp,
                                    )
                                    if (trimmedQuery.isNotEmpty()) {
                                        Text(
                                            text = "$totalMatchedCount found",
                                            color = AppleMusicRed,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(bottom = 5.dp),
                                        )
                                    }
                                }

                                LibraryFilterBar(
                                    visible = searchOpen,
                                    query = searchQuery,
                                    onQueryChange = { searchQuery = it },
                                    onClose = {
                                        searchOpen = false
                                        searchQuery = ""
                                    },
                                    autoFocus = focusSearchNext,
                                    onFocused = { focusSearchNext = false },
                                    placeholder = "Search settings, audio, downloads...",
                                )
                            }
                        }

                        if (showAccountCard) {
                            item(key = "settings_account_card") {
                                val totalProfiles = ytAccountState.accounts.sumOf { acc -> acc.profiles.size.coerceAtLeast(1) }
                                val resolvedSubtitle = if (ytAccountState.isSignedIn) {
                                    val idText = ytAccountState.accountEmail.ifBlank { ytAccountState.accountName }
                                    val countSuffix = if (ytAccountState.accounts.size > 1) {
                                        " (${ytAccountState.accounts.size} accounts)"
                                    } else {
                                        ""
                                    }
                                    if (idText.isNotBlank()) "YouTube Music • $idText$countSuffix" else "YouTube Music Connected$countSuffix"
                                } else {
                                    state.accountSubtitle
                                }
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    SettingsAccountCard(
                                        displayName = state.displayName,
                                        subtitle = resolvedSubtitle,
                                        useBackdropGlass = glassActive,
                                        onClick = {
                                            haptics.play(Haptic.Tap)
                                            showProfileAccount = true
                                        },
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                    if (ytAccountState.isSignedIn && (ytAccountState.accounts.size > 1 || totalProfiles > 1)) {
                                        Spacer(Modifier.height(10.dp))
                                        SettingsAccountQuickSwitchRow(
                                            accounts = ytAccountState.accounts,
                                            activeAccountId = ytAccountState.activeAccountId,
                                            activeProfileId = ytAccountState.activeProfileId,
                                            onSelect = { session, profile ->
                                                haptics.play(Haptic.Select)
                                                val switched = YouTubeLibrarySyncManager.switchAccountOrProfile(
                                                    context = context,
                                                    accountId = session.accountId,
                                                    profileId = profile.profileId,
                                                )
                                                if (switched) {
                                                    val label = profile.name.ifBlank { session.name.ifBlank { "Google Account" } }
                                                    Toast.makeText(context, "Switched feed to $label", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            onManageAll = {
                                                haptics.play(Haptic.Tap)
                                                showProfileAccount = true
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        if (trimmedQuery.isNotEmpty() && filteredSections.isEmpty() && !showAccountCard) {
                            item(key = "settings_no_results") {
                                Spacer(Modifier.height(6.dp))
                                LibraryGlassEmptyCard(
                                    icon = ITubeIcons.Search,
                                    title = "No Settings Found",
                                    subtitle = "No options matched \"$trimmedQuery\". Try searching Audio, Lossless, Wi-Fi, or Avatar.",
                                    accent = LibraryCrimsonAccent,
                                    actionLabel = "Clear Search",
                                    onAction = { searchQuery = "" },
                                )
                            }
                        } else {
                            filteredSections.forEach { section ->
                                item(key = "sec_${section.id}") {
                                    SettingsGroupBlock(
                                        section = section,
                                        useBackdropGlass = glassActive,
                                        haptics = haptics,
                                        onOpenPicker = { picker -> activePicker = picker },
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                }
                            }
                        }

                        if (trimmedQuery.isEmpty()) {
                            item(key = "settings_footer") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = "iTune Music • Spatial Audio & Liquid Glass",
                                        color = Color.White.copy(alpha = 0.42f),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "Version 2.4 (2408) • Signed in as ${state.displayName}",
                                        color = Color.White.copy(alpha = 0.30f),
                                        fontSize = 11.5.sp,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }

                TopFadeBlur(
                    hazeState = hazeState,
                    pageColor = AppleOledBlack,
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                FrostedTopBar(
                    transparentBackdrop = true,
                    artworkPageChrome = true,
                    backButtonHazeState = hazeState,
                    onBack = {
                        haptics.play(Haptic.Tap)
                        onBack()
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                    centerContent = {
                        AnimatedVisibility(
                            visible = isScrolled || searchOpen,
                            enter = fadeIn(tween(180)),
                            exit = fadeOut(tween(180)),
                        ) {
                            FrostedTopBarTitlePill(hazeState = hazeState) {
                                Text(
                                    text = "Settings",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                haptics.play(Haptic.Tap)
                                if (searchOpen) {
                                    searchOpen = false
                                    searchQuery = ""
                                } else {
                                    searchOpen = true
                                    focusSearchNext = true
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(0)
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = if (searchOpen) Icons.Rounded.Close else ITubeIcons.Search,
                                contentDescription = if (searchOpen) "Close Settings Search" else "Search Settings",
                                tint = if (searchOpen) LibraryCrimsonAccent else Color.White,
                                modifier = Modifier.size(21.dp),
                            )
                        }
                    },
                )

                BottomFadeScrim(
                    withMiniPlayer = hasMiniPlayer,
                    pageColor = AppleOledBlack,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )

                AnimatedVisibility(
                    visible = activeAudioEffectPage != null,
                    enter = fadeIn(tween(200)) + slideInHorizontally(tween(260)) { it / 8 },
                    exit = fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 8 },
                ) {
                    activeAudioEffectPage?.let { effectPage ->
                        SettingsAudioEffectScreen(
                            initialPage = effectPage,
                            hasMiniPlayer = hasMiniPlayer,
                            onBack = { activeAudioEffectPage = null },
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showProfileAccount,
                    enter = fadeIn(tween(200)) + slideInHorizontally(tween(260)) { it / 8 },
                    exit = fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 8 },
                ) {
                    SettingsProfileAccountScreen(
                        hasMiniPlayer = hasMiniPlayer,
                        onBack = { showProfileAccount = false },
                        onOpenAvatarPopup = onAccountClick,
                    )
                }

                AnimatedVisibility(
                    visible = showLyricsSettings,
                    enter = fadeIn(tween(200)) + slideInHorizontally(tween(260)) { it / 8 },
                    exit = fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 8 },
                ) {
                    LyricsSettingsScreen(
                        hasMiniPlayer = hasMiniPlayer,
                        onBack = { showLyricsSettings = false },
                    )
                }
            }
        }
    }

    activePicker?.let { picker ->
        val latestPicker = (sections.asSequence()
            .flatMap { it.entries.asSequence() }
            .firstOrNull { it.id == picker.id } as? SettingsEntry.Picker) ?: picker
        SettingsOptionPickerDialog(
            picker = latestPicker,
            haptics = haptics,
            onDismiss = { activePicker = null },
        )
    }

    if (showNameDialog) {
        SettingsDisplayNameDialog(
            initialName = state.displayName,
            haptics = haptics,
            onSave = { updated ->
                SettingsStore.setString(context, SettingsStore.KEY_DISPLAY_NAME, updated)
                IShareSyncManager.setDisplayName(updated)
                showNameDialog = false
                Toast.makeText(context, "Profile name updated", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showNameDialog = false },
        )
    }

    if (showAboutDialog) {
        SettingsAboutDialog(
            displayName = state.displayName,
            onReplayWelcome = {
                showAboutDialog = false
                SettingsStore.setBoolean(context, SettingsStore.KEY_WELCOME_COMPLETED, false)
            },
            onDismiss = { showAboutDialog = false },
        )
    }

    if (showClearSongCacheDialog) {
        val count = MusicCacheManager.cachedTracks.size
        val sizeStr = MusicCacheManager.formatStorageSize(MusicCacheManager.totalStorageBytes.longValue)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClearSongCacheDialog = false },
            title = {
                Text(
                    text = "Clear Cached Songs?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            },
            text = {
                Text(
                    text = "This will permanently remove all $count offline songs ($sizeStr) from device storage.",
                    color = Color.White.copy(alpha = 0.76f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            },
            containerColor = Color(0xFF1C1C1E),
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        MusicCacheManager.clearAllCache()
                        showClearSongCacheDialog = false
                        Toast.makeText(context, "Cached songs cleared", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text("Clear All", color = Color(0xFFFF453A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showClearSongCacheDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.70f))
                }
            },
        )
    }
}

@Composable
private fun SettingsBlackGlassBackdrop(
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.background(SettingsScreenBlack))
}

@Composable
internal fun SettingsUniformGreyCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .background(SettingsCardGrey),
    ) {
        content()
    }
}

@Composable
private fun SettingsAccountQuickSwitchRow(
    accounts: List<GoogleAccountSession>,
    activeAccountId: String?,
    activeProfileId: String?,
    onSelect: (GoogleAccountSession, YouTubeProfile) -> Unit,
    onManageAll: () -> Unit,
) {
    val flattened = remember(accounts) {
        accounts.flatMap { acc ->
            val profiles = acc.profiles.ifEmpty {
                listOf(
                    YouTubeProfile(
                        profileId = acc.activeProfileId ?: acc.accountId,
                        name = acc.name.ifBlank { acc.email.ifBlank { "Account" } },
                        handle = acc.email,
                    ),
                )
            }
            profiles.map { acc to it }
        }
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(
            items = flattened,
            key = { (acc, prof) -> "${acc.accountId}:${prof.profileId}" },
        ) { (acc, prof) ->
            val resolvedActivePid = activeProfileId ?: acc.activeProfileId ?: acc.profiles.firstOrNull()?.profileId
            val selected = acc.accountId == activeAccountId && prof.profileId == resolvedActivePid
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (selected) AppleMusicRed.copy(alpha = 0.22f) else SettingsCardGrey,
                    )
                    .border(
                        width = if (selected) 1.dp else 0.6.dp,
                        color = if (selected) AppleMusicRed else Color.White.copy(alpha = 0.14f),
                        shape = CircleShape,
                    )
                    .clickable { onSelect(acc, prof) }
                    .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!prof.avatar.isNullOrBlank()) {
                        AsyncImage(
                            model = prof.avatar,
                            contentDescription = prof.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                        )
                    } else {
                        Image(
                            painter = painterResource(R.drawable.ic_google_logo),
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
                Text(
                    text = prof.name.ifBlank { acc.name.ifBlank { "Account" } },
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp),
                )
            }
        }
        item(key = "quick_manage_accounts") {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(SettingsCardGrey)
                    .border(0.6.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                    .clickable(onClick = onManageAll)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "+ Accounts",
                    color = AppleMusicRed,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SettingsAccountCard(
    displayName: String,
    subtitle: String,
    @Suppress("UNUSED_PARAMETER") useBackdropGlass: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "accountCardScale",
    )

    SettingsUniformGreyCard(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        cornerRadius = 14.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppleMemojiAvatar(
                size = 62.dp,
                onClick = onClick,
            )

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = AppleTextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(8.dp))

            Icon(
                imageVector = SfIcons.ChevronRight,
                contentDescription = "Choose Avatar",
                tint = Color.White.copy(alpha = 0.42f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun SettingsGroupBlock(
    section: SettingsSection,
    @Suppress("UNUSED_PARAMETER") useBackdropGlass: Boolean,
    haptics: Haptics,
    onOpenPicker: (SettingsEntry.Picker) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = section.title.uppercase(),
            color = Color.White.copy(alpha = 0.50f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.65.sp,
            modifier = Modifier.padding(start = 14.dp, bottom = 7.dp),
        )

        SettingsUniformGreyCard(
            cornerRadius = 14.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                section.entries.forEachIndexed { index, entry ->
                    SettingsEntryRow(
                        entry = entry,
                        haptics = haptics,
                        onOpenPicker = onOpenPicker,
                    )
                    if (index < section.entries.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 54.dp),
                            thickness = 0.5.dp,
                            color = SettingsDividerGrey,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsEntryRow(
    entry: SettingsEntry,
    haptics: Haptics,
    onOpenPicker: (SettingsEntry.Picker) -> Unit,
) {
    val rowClick: () -> Unit = when (entry) {
        is SettingsEntry.Toggle -> {
            {
                val next = !entry.checked
                haptics.play(if (next) Haptic.ToggleOn else Haptic.ToggleOff)
                entry.onToggle(next)
            }
        }
        is SettingsEntry.Picker -> {
            {
                haptics.play(Haptic.Tap)
                onOpenPicker(entry)
            }
        }
        is SettingsEntry.Action -> {
            {
                haptics.play(Haptic.Tap)
                entry.onAction()
            }
        }
        is SettingsEntry.EffectToggleAction -> {
            {
                haptics.play(Haptic.Tap)
                entry.onOpenDetails()
            }
        }
        is SettingsEntry.SegmentedToggle -> {
            {
                val next = if (entry.selectedOption == entry.options.firstOrNull()) {
                    entry.options.getOrNull(1) ?: entry.options.first()
                } else {
                    entry.options.first()
                }
                haptics.play(Haptic.Select)
                entry.onSelect(next)
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = rowClick)
            .padding(horizontal = 15.dp, vertical = if (entry.subtitle != null) 11.dp else 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(26.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = entry.iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White),
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(21.dp),
            )
        }

        Spacer(Modifier.width(13.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.title,
                    color = Color.White,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry is SettingsEntry.EffectToggleAction) {
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        imageVector = SfIcons.ChevronRight,
                        contentDescription = "Open ${entry.title} options",
                        tint = Color.White.copy(alpha = 0.42f),
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            entry.subtitle?.let { sub ->
                Spacer(Modifier.height(1.dp))
                Text(
                    text = sub,
                    color = AppleTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        when (entry) {
            is SettingsEntry.Toggle -> {
                AppleGlassSwitch(
                    checked = entry.checked,
                    onCheckedChange = { checked ->
                        haptics.play(if (checked) Haptic.ToggleOn else Haptic.ToggleOff)
                        entry.onToggle(checked)
                    },
                )
            }
            is SettingsEntry.SegmentedToggle -> {
                AppleSegmentedSwitch(
                    options = entry.options,
                    selectedOption = entry.selectedOption,
                    onSelect = { choice ->
                        haptics.play(Haptic.Select)
                        entry.onSelect(choice)
                    },
                )
            }
            is SettingsEntry.EffectToggleAction -> {
                Box(modifier = Modifier.alpha(if (entry.switchEnabled) 1f else 0.42f)) {
                    AppleGlassSwitch(
                        checked = entry.checked,
                        onCheckedChange = { checked ->
                            if (entry.switchEnabled) {
                                haptics.play(if (checked) Haptic.ToggleOn else Haptic.ToggleOff)
                                entry.onToggle(checked)
                            }
                        },
                    )
                }
            }
            is SettingsEntry.Picker -> {
                Text(
                    text = entry.currentValue,
                    color = AppleTextSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(7.dp))
                Icon(
                    imageVector = SfIcons.ChevronRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.36f),
                    modifier = Modifier.size(13.dp),
                )
            }
            is SettingsEntry.Action -> {
                entry.badgeCount?.takeIf { it > 0 }?.let { count ->
                    Box(
                        modifier = Modifier
                            .size(21.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3A3A3C)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = count.toString(),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                entry.trailingValue?.let { value ->
                    Text(
                        text = value,
                        color = AppleTextSecondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(7.dp))
                }
                Icon(
                    imageVector = SfIcons.ChevronRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.36f),
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

@Composable
fun AppleGlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) SettingsAppleGreen else Color.White.copy(alpha = 0.22f),
        animationSpec = tween(durationMillis = 180),
        label = "appleSwitchTrack",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "appleSwitchThumb",
    )

    Box(
        modifier = Modifier
            .size(width = 50.dp, height = 30.dp)
            .clip(CircleShape)
            .background(trackColor)
            .border(
                width = 0.6.dp,
                color = Color.White.copy(alpha = if (checked) 0.14f else 0.18f),
                shape = CircleShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onCheckedChange(!checked) }
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(26.dp)
                .shadow(4.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
fun AppleSegmentedSwitch(
    options: List<String>,
    selectedOption: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = options.indexOf(selectedOption).coerceAtLeast(0)
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = 0.76f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "appleSegmentedSwitchThumb",
    )

    BoxWithConstraints(
        modifier = modifier
            .width(152.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(2.dp),
    ) {
        val segmentWidth = maxWidth / options.size.coerceAtLeast(1)

        // Sliding white thumb
        Box(
            modifier = Modifier
                .offset(x = segmentWidth * animatedIndex)
                .width(segmentWidth)
                .fillMaxHeight()
                .shadow(
                    elevation = 3.dp,
                    shape = RoundedCornerShape(14.dp),
                    ambientColor = Color.Black.copy(alpha = 0.35f),
                    spotColor = Color.Black.copy(alpha = 0.35f),
                )
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White),
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (option != selectedOption) {
                                onSelect(option)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option,
                        style = TextStyle(
                            fontFamily = SFProDisplay,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            letterSpacing = (-0.2).sp,
                        ),
                        color = if (isSelected) Color(0xFF1C1C1E) else Color.White.copy(alpha = 0.72f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsOptionPickerDialog(
    picker: SettingsEntry.Picker,
    haptics: Haptics,
    onDismiss: () -> Unit,
) {
    SettingsModalFrame(onDismiss = onDismiss) {
        Spacer(Modifier.height(18.dp))
        Text(
            text = picker.title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = picker.description,
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
            picker.options.forEachIndexed { idx, option ->
                val selected = option.label == picker.currentValue
                HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.10f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptics.play(Haptic.Select)
                            picker.onSelect(option.label)
                            onDismiss()
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = option.label,
                            color = if (selected) AppleMusicRed else Color.White,
                            fontSize = 15.5.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(
                            text = option.detail,
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
                if (idx == picker.options.lastIndex) {
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
private fun SettingsDisplayNameDialog(
    initialName: String,
    haptics: Haptics,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var fieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialName,
                selection = TextRange(initialName.length),
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
            text = "Display Name",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "Update the name shown on your Apple ID profile card in Settings.",
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
                    text = "Enter your name",
                    color = Color.White.copy(alpha = 0.38f),
                    fontSize = 15.5.sp,
                )
            }
            BasicTextField(
                value = fieldValue,
                onValueChange = { fieldValue = it },
                singleLine = true,
                cursorBrush = SolidColor(AppleMusicRed),
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Medium,
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (cleanText.isNotEmpty()) {
                            haptics.play(Haptic.Select)
                            onSave(cleanText)
                        }
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
                .clickable(enabled = cleanText.isNotEmpty()) {
                    haptics.play(Haptic.Select)
                    onSave(cleanText)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Save",
                color = if (cleanText.isNotEmpty()) AppleMusicRed else Color.White.copy(alpha = 0.30f),
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
                fontSize = 17.sp,
            )
        }
    }
}

@Composable
private fun SettingsAboutDialog(
    displayName: String,
    onReplayWelcome: () -> Unit,
    onDismiss: () -> Unit,
) {
    SettingsModalFrame(onDismiss = onDismiss) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = "About iTune",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Apple Music-inspired dark Liquid Glass player for $displayName.",
            color = AppleTextSecondary,
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(0.6.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AboutInfoLine("Version", "2.4 (2408)")
            AboutInfoLine("Audio Engine", "Lossless ALAC & Spatial DSP")
            AboutInfoLine("UI Theme", "OLED Black • Liquid Glass")
            AboutInfoLine("Status", "Up to date")
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.12f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clickable { onReplayWelcome() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Replay Welcome Screen",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Medium,
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
                text = "Done",
                color = AppleMusicRed,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun AboutInfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.56f),
            fontSize = 13.sp,
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun SettingsModalFrame(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { win ->
                win.setDimAmount(0.28f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    win.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .widthIn(max = 350.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {}
                    .shadow(28.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(RoundedCornerShape(22.dp))
                    .background(SettingsDialogBg)
                    .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(22.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                content()
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun buildSettingsSections(
    state: SettingsUiState,
    audioEngine: AudioEngineUiState,
    bitPerfectActive: Boolean,
    activeOutputName: String,
    searchHistoryCount: Int,
    iShareSession: IShareSession?,
    iShareIsLan: Boolean,
    dynamicIslandEnabled: Boolean,
    onOpenIShare: () -> Unit,
    onOpenDynamicIsland: () -> Unit,
    onOpenAudioEffect: (EnginePopupPage) -> Unit,
    onOpenLyricsSettings: () -> Unit,
    onToggleBool: (String, Boolean) -> Unit,
    onSelectString: (String, String) -> Unit,
    onClearCache: () -> Unit,
    onClearSongCache: () -> Unit,
    onEditName: () -> Unit,
    onClearSearchHistory: () -> Unit,
    onOpenAbout: () -> Unit,
): List<SettingsSection> {
    val effectsDisabled = audioEngine.bypassAllEffects || (audioEngine.bitPerfectEnabled && bitPerfectActive)
    return listOf(
        SettingsSection(
            id = "ishare_together",
            title = "iShare & Dynamic Island",
            entries = listOf(
                SettingsEntry.Action(
                    id = "ishare",
                    title = "iShare",
                    subtitle = if (iShareSession != null) {
                        val modeTag = if (iShareIsLan) "Wi-Fi LAN" else "Pairing Code"
                        "Live ($modeTag) • ${iShareSession.code}"
                    } else {
                        "Listen together via Pairing Code & Wi-Fi LAN"
                    },
                    iconRes = R.drawable.ic_settings_ishare,
                    keywords = listOf(
                        "ishare", "share", "shareplay", "listen together", "airbeats",
                        "pairing", "code", "room", "devices", "lan", "wifi", "hotspot", "sync",
                    ),
                    trailingValue = if (iShareSession != null) {
                        "${iShareSession.participants} device${if (iShareSession.participants == 1) "" else "s"}"
                    } else {
                        "Open"
                    },
                    badgeCount = iShareSession?.participants?.takeIf { it > 0 },
                    onAction = onOpenIShare,
                ),
                SettingsEntry.Action(
                    id = "dynamic_island",
                    title = "Dynamic Island",
                    subtitle = "Floating water-bubble controller outside iTune",
                    iconRes = R.drawable.ic_settings_dynamic_island,
                    keywords = listOf(
                        "dynamic island", "notch", "pill", "overlay", "water",
                        "bubble", "popup", "floating", "cutout", "outside",
                    ),
                    trailingValue = if (dynamicIslandEnabled) "On" else "Off",
                    onAction = onOpenDynamicIsland,
                ),
            ),
        ),
        SettingsSection(
            id = "audio_engine_effects",
            title = "Audio Engine & Sound Effects",
            entries = listOf(
                SettingsEntry.EffectToggleAction(
                    id = "engine_dolby_atmos",
                    title = "Dolby Atmos",
                    subtitle = if (audioEngine.dolbyAtmosEnabled && !effectsDisabled) {
                        if (audioEngine.dolbyWidthWide) "Wide Concert (2.75x) • Tap to adjust" else "Studio Balanced (2.25x) • Active"
                    } else {
                        "Tap title to configure stage & system Atmos"
                    },
                    iconRes = R.drawable.ic_dolby_atmos,
                    keywords = listOf("dolby", "atmos", "spatial", "surround", "concert", "stage"),
                    checked = audioEngine.dolbyAtmosEnabled && !effectsDisabled,
                    switchEnabled = !effectsDisabled,
                    onToggle = { AudioEngineManager.setDolbyAtmosEnabled(it) },
                    onOpenDetails = { onOpenAudioEffect(EnginePopupPage.DOLBY_ATMOS) },
                ),
                SettingsEntry.EffectToggleAction(
                    id = "engine_8d_audio",
                    title = "8D Audio",
                    subtitle = if (audioEngine.eightDAudioEnabled && !effectsDisabled) {
                        "${audioEngine.eightDAudioLevel}D 360° orbit active • Tap to adjust"
                    } else {
                        "Orbital ITD & HRTF head-shadow (${audioEngine.eightDAudioLevel}D)"
                    },
                    iconRes = R.drawable.ic_fx_8d,
                    keywords = listOf("8d", "16d", "orbit", "binaural", "360", "itd", "hrtf"),
                    checked = audioEngine.eightDAudioEnabled && !effectsDisabled,
                    switchEnabled = !effectsDisabled,
                    onToggle = { AudioEngineManager.setEightDAudioEnabled(it) },
                    onOpenDetails = { onOpenAudioEffect(EnginePopupPage.EIGHT_D) },
                ),
                SettingsEntry.EffectToggleAction(
                    id = "engine_spatial_audio",
                    title = "Spatial Audio",
                    subtitle = if (audioEngine.spatialAudioEnabled && !effectsDisabled) {
                        "${"%.1f".format(audioEngine.spatialWidth)}x width • ${if (audioEngine.spatialCrossfeed) "Crossfeed On" else "Crossfeed Off"}"
                    } else {
                        "Mid/Side stereo soundstage + 15ms crossfeed"
                    },
                    iconRes = R.drawable.ic_fx_spatial,
                    keywords = listOf("spatial", "3d", "stereo", "width", "crossfeed", "soundstage"),
                    checked = audioEngine.spatialAudioEnabled && !effectsDisabled,
                    switchEnabled = !effectsDisabled,
                    onToggle = { AudioEngineManager.setSpatialAudioEnabled(it) },
                    onOpenDetails = { onOpenAudioEffect(EnginePopupPage.SPATIAL) },
                ),
                SettingsEntry.EffectToggleAction(
                    id = "engine_equalizer",
                    title = "Equalizer (EQ)",
                    subtitle = if (audioEngine.eqEnabled && !effectsDisabled) {
                        "Preset: ${audioEngine.eqPreset} • Dot Curve & Seekbars"
                    } else {
                        "5-band Dot Curve, Seekbars & 12 AirBeats presets"
                    },
                    iconRes = R.drawable.ic_settings_equalizer,
                    keywords = listOf("eq", "equalizer", "dots", "curve", "sliders", "bass", "treble", "preset"),
                    checked = audioEngine.eqEnabled && !effectsDisabled,
                    switchEnabled = !effectsDisabled,
                    onToggle = { AudioEngineManager.setEqualizerEnabled(it) },
                    onOpenDetails = { onOpenAudioEffect(EnginePopupPage.EQUALIZER) },
                ),
                SettingsEntry.EffectToggleAction(
                    id = "engine_dj_console",
                    title = "DJ Studio Panel",
                    subtitle = if (audioEngine.djEnabled && !effectsDisabled) {
                        val boostTag = if (audioEngine.djBoostPercent > 100) "${audioEngine.djBoostPercent}% Boost" else "Studio DSP Active"
                        "$boostTag • Tap to open Master/Echo/Mixer/Deck"
                    } else {
                        "Master Dials, 200% Boost, Echo, Club Filter & Deck"
                    },
                    iconRes = R.drawable.ic_dj_console,
                    keywords = listOf("dj", "console", "echo", "filter", "flanger", "deck", "pitch", "tempo", "boost", "saturation"),
                    checked = audioEngine.djEnabled && !effectsDisabled,
                    switchEnabled = !effectsDisabled,
                    onToggle = { AudioEngineManager.setDjEnabled(it) },
                    onOpenDetails = { onOpenAudioEffect(EnginePopupPage.DJ_CONSOLE) },
                ),
                SettingsEntry.EffectToggleAction(
                    id = "engine_normalization",
                    title = "Audio Normalization",
                    subtitle = if (audioEngine.normalizationEnabled && !effectsDisabled) {
                        "${audioEngine.normalizationProfile.label} (${audioEngine.normalizationProfile.targetLufs.toInt()} LUFS) • ${"%+.1f".format(audioEngine.normalizationOffsetMb / 100f)} dB"
                    } else {
                        "Sound Check LUFS loudness target & pre-gain trim"
                    },
                    iconRes = R.drawable.ic_fx_normalization,
                    keywords = listOf("normalization", "sound check", "lufs", "loudness", "gain", "volume"),
                    checked = audioEngine.normalizationEnabled && !effectsDisabled,
                    switchEnabled = !effectsDisabled,
                    onToggle = { AudioEngineManager.setNormalizationEnabled(it) },
                    onOpenDetails = { onOpenAudioEffect(EnginePopupPage.NORMALIZATION) },
                ),
                SettingsEntry.EffectToggleAction(
                    id = "engine_bit_perfect",
                    title = "USB Bit-Perfect DAC",
                    subtitle = if (bitPerfectActive) {
                        "Hardware bit-exact lock active"
                    } else if (audioEngine.bitPerfectEnabled) {
                        "Armed for USB-C DAC • Direct PCM"
                    } else {
                        "Bypass Android mixer on supported USB DACs"
                    },
                    iconRes = R.drawable.ic_output_bit_perfect,
                    keywords = listOf("bit", "perfect", "usb", "dac", "direct", "pcm", "resampler"),
                    checked = audioEngine.bitPerfectEnabled,
                    switchEnabled = true,
                    onToggle = { AudioEngineManager.setBitPerfectEnabled(it) },
                    onOpenDetails = { onOpenAudioEffect(EnginePopupPage.BIT_PERFECT) },
                ),
                SettingsEntry.Toggle(
                    id = "engine_pure_bypass",
                    title = "Pure Audio Bypass",
                    subtitle = if (audioEngine.bypassAllEffects) {
                        "Active • All DSP locked for untouched source"
                    } else {
                        "Bypass all effects for direct passthrough"
                    },
                    iconRes = R.drawable.ic_fx_bypass,
                    keywords = listOf("bypass", "pure", "direct", "untouched", "flat"),
                    checked = audioEngine.bypassAllEffects,
                    onToggle = { AudioEngineManager.setBypassAllEffects(it) },
                ),
                SettingsEntry.Action(
                    id = "engine_pipeline",
                    title = "Signal Pipeline",
                    subtitle = "${audioEngine.pillBadge.text} -> $activeOutputName",
                    iconRes = R.drawable.ic_output_pipeline,
                    keywords = listOf("pipeline", "signal", "chain", "dsp", "sample rate", "output"),
                    trailingValue = if (effectsDisabled) "Bypassed" else "Inspect",
                    onAction = { onOpenAudioEffect(EnginePopupPage.PIPELINE) },
                ),
            ),
        ),
        SettingsSection(
            id = "audio_playback",
            title = "Audio & Playback",
            entries = listOf(
                SettingsEntry.Toggle(
                    id = "lossless_audio",
                    title = "Lossless Audio",
                    subtitle = "Studio-grade uncompressed ALAC playback",
                    iconRes = R.drawable.ic_settings_lossless,
                    keywords = listOf("flac", "alac", "hires", "studio", "fidelity", "quality"),
                    checked = state.losslessAudio,
                    onToggle = { onToggleBool(SettingsStore.KEY_LOSSLESS_AUDIO, it) },
                ),
                SettingsEntry.Picker(
                    id = "audio_quality",
                    title = "Streaming Quality",
                    subtitle = "Wi-Fi & DAC bitrate preference",
                    iconRes = R.drawable.ic_settings_streaming_quality,
                    keywords = listOf("bitrate", "lossless", "aac", "24bit", "192khz", "wifi"),
                    currentValue = state.audioQuality,
                    description = "Select the target streaming audio resolution on Wi-Fi connections.",
                    options = listOf(
                        SettingsOptionChoice("Hi-Res Lossless", "ALAC up to 24-bit/192 kHz (External DAC recommended)"),
                        SettingsOptionChoice("Lossless (24-bit)", "ALAC up to 24-bit/48 kHz"),
                        SettingsOptionChoice("High Quality", "AAC 256 kbps for low latency & smooth streaming"),
                        SettingsOptionChoice("High Efficiency", "HE-AAC for minimal bandwidth usage"),
                    ),
                    onSelect = { onSelectString(SettingsStore.KEY_AUDIO_QUALITY, it) },
                ),
            ).let { baseEntries ->
                if (!state.automixEnabled) {
                    baseEntries + listOf(
                        SettingsEntry.Picker(
                            id = "crossfade",
                            title = "Crossfade",
                            subtitle = "Smooth transition between songs",
                            iconRes = R.drawable.ic_settings_crossfade,
                            keywords = listOf("gapless", "fade", "blend", "mix", "dj", "transition"),
                            currentValue = state.crossfadeDuration,
                            description = "Overlap the fade-out of the ending track with the start of the next.",
                            options = listOf(
                                SettingsOptionChoice("Off", "Play tracks cleanly back-to-back"),
                                SettingsOptionChoice("2 sec", "Subtle transition overlap"),
                                SettingsOptionChoice("4 sec", "Short seamless fade"),
                                SettingsOptionChoice("6 sec", "Balanced DJ-style transition"),
                                SettingsOptionChoice("8 sec", "Smooth extended blend"),
                                SettingsOptionChoice("12 sec", "Maximum continuous mix blend"),
                            ),
                            onSelect = { onSelectString(SettingsStore.KEY_CROSSFADE, it) },
                        )
                    )
                } else {
                    baseEntries
                }
            } + listOf(
                SettingsEntry.Toggle(
                    id = "automix",
                    title = "Automix",
                    subtitle = if (state.automixEnabled) "Intelligent continuous mix & auto-queue" else "Automix is off. Standard crossfade is active.",
                    iconRes = R.drawable.ic_settings_crossfade,
                    keywords = listOf("automix", "radio", "continuous", "queue", "dj", "endless", "similar", "smart fade"),
                    checked = state.automixEnabled,
                    onToggle = { onToggleBool(SettingsStore.KEY_AUTOMIX, it) },
                ),
            ),
        ),
    SettingsSection(
        id = "content_settings",
        title = "Content",
        entries = listOf(
            SettingsEntry.Picker(
                id = "content_language",
                title = "Default Content Language",
                subtitle = "Language for Moods, Browse & Search",
                iconRes = R.drawable.ic_settings_content_language,
                keywords = listOf("language", "locale", "hl", "region", "translate", "moods", "browse"),
                currentValue = ContentLocaleCatalog.languageLabel(state.contentLanguage),
                description = "Select the preferred catalog language used across Browse, Moods & Genres, Playlists, and New Releases.",
                options = ContentLocaleCatalog.languages.map { option ->
                    SettingsOptionChoice(option.displayName, option.detail)
                },
                onSelect = { chosenLabel ->
                    val code = ContentLocaleCatalog.normalizeLanguageChoice(chosenLabel)
                    onSelectString(SettingsStore.KEY_CONTENT_LANGUAGE, code)
                },
            ),
            SettingsEntry.Picker(
                id = "content_country",
                title = "Default Content Country",
                subtitle = "Region for Charts, Playlists & New Releases",
                iconRes = R.drawable.ic_settings_content_country,
                keywords = listOf("country", "region", "gl", "charts", "playlists", "newly added", "local"),
                currentValue = ContentLocaleCatalog.countryLabel(state.contentCountry),
                description = "Choose the regional storefront used for Newly Added albums, regional Moods, and Featured Playlists.",
                options = ContentLocaleCatalog.countries.map { option ->
                    SettingsOptionChoice(option.displayName, option.detail)
                },
                onSelect = { chosenLabel ->
                    val code = ContentLocaleCatalog.normalizeCountryChoice(chosenLabel)
                    onSelectString(SettingsStore.KEY_CONTENT_COUNTRY, code)
                },
            ),
            SettingsEntry.Toggle(
                id = "hide_explicit_content",
                title = "Hide Explicit Content",
                subtitle = "Filter explicit songs across Moods, Browse & Search",
                iconRes = R.drawable.ic_settings_explicit,
                keywords = listOf("explicit", "clean", "parental", "hide", "filter", "restricted"),
                checked = state.hideExplicitContent,
                onToggle = { onToggleBool(SettingsStore.KEY_HIDE_EXPLICIT, it) },
            ),
        ),
    ),
    SettingsSection(
        id = "visuals_glass",
        title = "Now Playing & Liquid Glass",
        entries = listOf(
            SettingsEntry.Toggle(
                id = "splash_screen",
                title = "Splash Screen",
                subtitle = "App launch brand animation & fluid title reveal",
                iconRes = R.drawable.ic_itune_waves,
                keywords = listOf("splash", "opening", "launch", "animation", "brand", "intro"),
                checked = state.splashScreenEnabled,
                onToggle = { onToggleBool(SettingsStore.KEY_SPLASH_SCREEN, it) },
            ),
            SettingsEntry.Toggle(
                id = "animated_artwork",
                title = "Animated Cover Art",
                subtitle = "Motion canvas on artist & album views",
                iconRes = R.drawable.ic_settings_animated_art,
                keywords = listOf("motion", "canvas", "video", "cover", "artwork", "animation"),
                checked = state.animatedArtwork,
                onToggle = { onToggleBool(SettingsStore.KEY_ANIMATED_ARTWORK, it) },
            ),
            SettingsEntry.SegmentedToggle(
                id = "player_type",
                title = "Player Type",
                subtitle = if (state.playerType == "Static") {
                    "Static • Welcome screen ambient mesh gradient"
                } else {
                    "Dynamic • Real-time blurred artwork backdrop"
                },
                iconRes = R.drawable.ic_settings_liquid_glass,
                keywords = listOf(
                    "player", "type", "dynamic", "static", "background", "mesh", "blur",
                    "artwork", "welcome", "ambient", "switch", "style"
                ),
                options = listOf("Dynamic", "Static"),
                selectedOption = state.playerType,
                onSelect = { onSelectString(SettingsStore.KEY_PLAYER_TYPE, it) },
            ),
            SettingsEntry.Toggle(
                id = "time_synced_lyrics",
                title = "Time-Synced Lyrics",
                subtitle = "Follow word and line lyrics in real time",
                iconRes = R.drawable.ic_settings_lyrics,
                keywords = listOf("lyrics", "sing", "karaoke", "text"),
                checked = state.timeSyncedLyrics,
                onToggle = { onToggleBool(SettingsStore.KEY_TIME_SYNCED_LYRICS, it) },
            ),
            SettingsEntry.Action(
                id = "lyrics_providers",
                title = "Lyrics Providers",
                subtitle = "11 sources • Prioritize & toggle fallback order",
                iconRes = R.drawable.lyrics,
                keywords = listOf(
                    "lyrics", "providers", "lrclib", "kugou", "simpmusic",
                    "subtitles", "betterlyrics", "youly", "megalobiz",
                    "paxsenix", "portato", "unison", "sources", "priority"
                ),
                trailingValue = "${state.lyricsProviderOrder.count { it !in state.disabledLyricsProviders }} Active",
                onAction = onOpenLyricsSettings,
            ),
            SettingsEntry.Toggle(
                id = "liquid_glass",
                title = "Liquid Glass Chrome",
                subtitle = "Refractive translucent headers & dock cards",
                iconRes = R.drawable.ic_settings_liquid_glass,
                keywords = listOf("blur", "frosted", "theme", "dark", "oled", "glass", "transparent"),
                checked = state.liquidGlassChrome,
                onToggle = { onToggleBool(SettingsStore.KEY_LIQUID_GLASS, it) },
            ),
            SettingsEntry.Toggle(
                id = "tactile_haptics",
                title = "Tactile Haptics",
                subtitle = "Subtle pulse feedback across player controls",
                iconRes = R.drawable.ic_settings_haptics,
                keywords = listOf("vibrate", "feedback", "touch", "haptic"),
                checked = state.tactileHaptics,
                onToggle = { onToggleBool(SettingsStore.KEY_TACTILE_HAPTICS, it) },
            ),
        ),
    ),
    SettingsSection(
        id = "downloads_storage",
        title = "Downloads & Storage",
        entries = listOf(
            SettingsEntry.Picker(
                id = "cellular_streaming",
                title = "Cellular Streaming",
                subtitle = "Audio bandwidth on mobile data",
                iconRes = R.drawable.ic_settings_cellular,
                keywords = listOf("mobile data", "5g", "lte", "cellular", "roaming"),
                currentValue = state.cellularStreaming,
                description = "Control audio fidelity when streaming away from Wi-Fi.",
                options = listOf(
                    SettingsOptionChoice("High Efficiency", "Use minimal cellular data"),
                    SettingsOptionChoice("High Quality", "AAC 256 kbps balanced mobile playback"),
                    SettingsOptionChoice("Lossless", "Full lossless over 5G/LTE networks"),
                    SettingsOptionChoice("Off", "Allow streaming on Wi-Fi only"),
                ),
                onSelect = { onSelectString(SettingsStore.KEY_CELLULAR_STREAMING, it) },
            ),
            SettingsEntry.Toggle(
                id = "download_wifi_only",
                title = "Download Over Wi-Fi",
                subtitle = "Prevent large album downloads on mobile data",
                iconRes = R.drawable.ic_settings_wifi,
                keywords = listOf("wifi", "offline", "save", "bandwidth"),
                checked = state.downloadOverWifiOnly,
                onToggle = { onToggleBool(SettingsStore.KEY_DOWNLOAD_WIFI_ONLY, it) },
            ),
            SettingsEntry.Toggle(
                id = "cache_songs_playing",
                title = "Cache Songs While Playing",
                subtitle = "Automatically save songs to offline storage as you listen",
                iconRes = R.drawable.ic_settings_offline,
                keywords = listOf("cache", "song", "offline", "playing", "streaming", "itube"),
                checked = MusicCacheManager.cachingEnabled.value,
                onToggle = { MusicCacheManager.setCachingEnabled(it) },
            ),
            SettingsEntry.Picker(
                id = "song_cache_limit",
                title = "Song Storage Limit",
                subtitle = if (MusicCacheManager.maxCacheLimitMB.longValue <= 0) "Unlimited storage" else "Auto-evicts oldest songs beyond limit",
                iconRes = R.drawable.ic_settings_storage,
                keywords = listOf("storage", "limit", "cache", "mb", "gb", "evict"),
                currentValue = when (val mb = MusicCacheManager.maxCacheLimitMB.longValue) {
                    500L -> "500 MB"
                    1024L -> "1 GB"
                    2048L -> "2 GB"
                    5120L -> "5 GB"
                    10240L -> "10 GB"
                    else -> "Unlimited"
                },
                description = "Maximum disk storage allocated for offline audio cache before older unplayed songs are evicted.",
                options = listOf(
                    SettingsOptionChoice("500 MB", "Compact cache (~70 songs)"),
                    SettingsOptionChoice("1 GB", "Moderate cache (~150 songs)"),
                    SettingsOptionChoice("2 GB", "Recommended default (~300 songs)"),
                    SettingsOptionChoice("5 GB", "Large offline collection (~800 songs)"),
                    SettingsOptionChoice("10 GB", "Very large library (~1,600 songs)"),
                    SettingsOptionChoice("Unlimited", "Never evict cached songs"),
                ),
                onSelect = { choice ->
                    val limitMb = when (choice) {
                        "500 MB" -> 500L
                        "1 GB" -> 1024L
                        "2 GB" -> 2048L
                        "5 GB" -> 5120L
                        "10 GB" -> 10240L
                        else -> -1L
                    }
                    MusicCacheManager.setMaxCacheLimitMB(limitMb)
                },
            ),
            SettingsEntry.Action(
                id = "clear_cached_songs",
                title = "Clear Cached Songs",
                subtitle = "${MusicCacheManager.cachedTracks.size} songs • ${MusicCacheManager.formatStorageSize(MusicCacheManager.totalStorageBytes.longValue)}",
                iconRes = R.drawable.ic_settings_clear_cache,
                keywords = listOf("clear", "cache", "songs", "delete", "free", "offline"),
                trailingValue = MusicCacheManager.formatStorageSize(MusicCacheManager.totalStorageBytes.longValue),
                onAction = onClearSongCache,
            ),
            SettingsEntry.Toggle(
                id = "automatic_downloads",
                title = "Automatic Downloads",
                subtitle = "Save newly liked & added tracks offline",
                iconRes = R.drawable.ic_settings_downloads,
                keywords = listOf("offline", "auto", "sync", "download"),
                checked = state.automaticDownloads,
                onToggle = { onToggleBool(SettingsStore.KEY_AUTO_DOWNLOADS, it) },
            ),
            SettingsEntry.Picker(
                id = "optimize_storage",
                title = "Optimize Storage",
                subtitle = "Automatically trim older unplayed downloads",
                iconRes = R.drawable.ic_settings_storage,
                keywords = listOf("space", "disk", "gb", "clean", "downloads"),
                currentValue = state.optimizeStorage,
                description = "Minimum storage budget reserved for downloaded albums and playlists.",
                options = listOf(
                    SettingsOptionChoice("4 GB", "Compact library cache (~600 tracks)"),
                    SettingsOptionChoice("8 GB", "Balanced storage (~1,200 tracks)"),
                    SettingsOptionChoice("16 GB", "Large offline library (~2,400 tracks)"),
                    SettingsOptionChoice("32 GB", "Maximum hi-res offline collection"),
                    SettingsOptionChoice("Off", "Keep all downloaded tracks indefinitely"),
                ),
                onSelect = { onSelectString(SettingsStore.KEY_OPTIMIZE_STORAGE, it) },
            ),
            SettingsEntry.Action(
                id = "audio_cache",
                title = "Clear App Buffer Cache",
                subtitle = "Remove temporary cover art & network buffers",
                iconRes = R.drawable.ic_settings_clear_cache,
                keywords = listOf("cache", "storage", "free", "clean", "memory", "buffer"),
                trailingValue = state.cachedSizeLabel,
                onAction = onClearCache,
            ),
        ),
    ),
    SettingsSection(
        id = "library_activity",
        title = "Library & Listening",
        entries = listOf(
            SettingsEntry.Toggle(
                id = "sync_library",
                title = "Sync Library",
                subtitle = "Keep artists, playlists & albums updated",
                iconRes = R.drawable.ic_settings_sync_library,
                keywords = listOf("cloud", "playlists", "library", "backup"),
                checked = state.syncLibrary,
                onToggle = { onToggleBool(SettingsStore.KEY_SYNC_LIBRARY, it) },
            ),
            SettingsEntry.Toggle(
                id = "listening_history",
                title = "Listening History",
                subtitle = "Shape Personalized Mixes & Recently Played",
                iconRes = R.drawable.ic_settings_history,
                keywords = listOf("privacy", "recommendations", "recent", "mix"),
                checked = state.useListeningHistory,
                onToggle = { onToggleBool(SettingsStore.KEY_LISTENING_HISTORY, it) },
            ),
            SettingsEntry.Toggle(
                id = "add_favorites",
                title = "Add Favorite Songs",
                subtitle = "Include liked tracks in Library automatically",
                iconRes = R.drawable.ic_settings_favorites,
                keywords = listOf("liked", "heart", "star", "favorites", "library"),
                checked = state.addFavoriteSongs,
                onToggle = { onToggleBool(SettingsStore.KEY_ADD_FAVORITE_SONGS, it) },
            ),
        ),
    ),
    SettingsSection(
        id = "notifications_general",
        title = "Notifications & General",
        entries = listOf(
            SettingsEntry.Toggle(
                id = "new_music_alerts",
                title = "New Music Alerts",
                subtitle = "Alerts when followed artists release singles",
                iconRes = R.drawable.ic_settings_notifications,
                keywords = listOf("alerts", "notifications", "releases", "artists"),
                checked = state.newMusicAlerts,
                onToggle = { onToggleBool(SettingsStore.KEY_NEW_MUSIC_ALERTS, it) },
            ),
            SettingsEntry.Toggle(
                id = "offline_mode",
                title = "Offline Mode",
                subtitle = "Show only downloaded songs & albums",
                iconRes = R.drawable.ic_settings_offline,
                keywords = listOf("airplane", "offline", "local", "disconnect"),
                checked = state.offlineMode,
                onToggle = { onToggleBool(SettingsStore.KEY_OFFLINE_MODE, it) },
            ),
            SettingsEntry.Picker(
                id = "sleep_timer",
                title = "Sleep Timer",
                subtitle = "Automatically fade out playback",
                iconRes = R.drawable.ic_settings_sleep_timer,
                keywords = listOf("bedtime", "night", "stop", "timer", "countdown"),
                currentValue = state.sleepTimer,
                description = "Stop audio gently after a set duration or when the current track finishes.",
                options = listOf(
                    SettingsOptionChoice("Off", "Keep playback active continuously"),
                    SettingsOptionChoice("15 min", "Fade out gently after 15 minutes"),
                    SettingsOptionChoice("30 min", "Fade out gently after 30 minutes"),
                    SettingsOptionChoice("45 min", "Fade out gently after 45 minutes"),
                    SettingsOptionChoice("60 min", "Fade out gently after 1 hour"),
                    SettingsOptionChoice("End of Track", "Pause automatically when current track ends"),
                ),
                onSelect = { onSelectString(SettingsStore.KEY_SLEEP_TIMER, it) },
            ),
            SettingsEntry.Action(
                id = "display_name",
                title = "Profile Name",
                subtitle = "Customize your Settings profile display name",
                iconRes = R.drawable.ic_settings_profile_name,
                keywords = listOf("name", "profile", "account", "rename", "user"),
                trailingValue = state.displayName,
                onAction = onEditName,
            ),
            SettingsEntry.Action(
                id = "clear_search_history",
                title = "Clear Search History",
                subtitle = "Remove recent queries from Search",
                iconRes = R.drawable.ic_settings_clear_history,
                keywords = listOf("search", "recent", "queries", "clear", "delete", "privacy"),
                trailingValue = if (searchHistoryCount == 0) "Empty" else "$searchHistoryCount saved",
                onAction = onClearSearchHistory,
            ),
            SettingsEntry.Action(
                id = "about_itune",
                title = "General & About",
                subtitle = "Build details, audio DSP & Liquid Glass",
                iconRes = R.drawable.ic_settings_about,
                keywords = listOf("version", "about", "general", "build", "info", "update"),
                trailingValue = "v2.4",
                badgeCount = 1,
                onAction = onOpenAbout,
            ),
        ),
    ),
)
}
