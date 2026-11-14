package com.darkxvenom.itune.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.provider.OpenableColumns
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.luminance
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
import com.darkxvenom.itune.data.library.LibraryStore
import com.darkxvenom.itune.data.backup.BackupRestoreManager
import com.darkxvenom.itune.data.backup.AutoBackupScheduler
import com.darkxvenom.itune.data.auth.EncryptedAuthPrefs
import com.darkxvenom.itune.data.updates.AppUpdateManager
import com.darkxvenom.itune.data.updates.GithubReleaseInfo
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
import com.darkxvenom.itune.ui.theme.LocalAppFontFamily
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppFont
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import com.darkxvenom.itune.ui.theme.AppleTextSecondary
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SettingsAppleGreen = Color(0xFF34C759)
private val SettingsDialogBg = Color.Black
internal val SettingsScreenBlack = Color(0xFF000000)
internal val SettingsCardGrey = Color(0xFF1C1C1E)
internal val SettingsDividerGrey = Color(0xFF2C2C2E)

private enum class SettingsClearAction { SEARCH_HISTORY, LISTENING_HISTORY, CACHED_SONGS }

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
        val enabled: Boolean = true,
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
        val enabled: Boolean = true,
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
        AutoBackupScheduler.ensureScheduled(context)
    }

    val state by SettingsStore.state.collectAsStateWithLifecycle()
    val searchHistory by SearchHistoryStore.history.collectAsStateWithLifecycle()
    val iShareSession by IShareSyncManager.session.collectAsStateWithLifecycle()
    val iShareMode by IShareSyncManager.connectionMode.collectAsStateWithLifecycle()
    val dynamicIslandConfig by DynamicIslandStore.state.collectAsStateWithLifecycle()
    val audioEngineState by AudioEngineManager.state.collectAsStateWithLifecycle()
    val audioOutputState by AudioOutputManager.state.collectAsStateWithLifecycle()
    val ytAccountState by GoogleAuthStore.state.collectAsStateWithLifecycle()
    val availableUpdate by AppUpdateManager.availableUpdate.collectAsStateWithLifecycle()
    val latestRelease by AppUpdateManager.latestRelease.collectAsStateWithLifecycle()
    val isCheckingForUpdate by AppUpdateManager.isChecking.collectAsStateWithLifecycle()

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
    var showDeveloperNews by rememberSaveable { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showAutoSleepTimerDialog by remember { mutableStateOf(false) }
    var showProxySettingsDialog by remember { mutableStateOf(false) }
    var showAodSettings by rememberSaveable { mutableStateOf(false) }
    var showWidgetSettings by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showAodSettings && !showLyricsSettings && !showDeveloperNews && !showProfileAccount && activeAudioEffectPage == null && activePicker == null && !showNameDialog && !showAboutDialog) {
        showAodSettings = false
    }
    BackHandler(enabled = showWidgetSettings && !showLyricsSettings && !showDeveloperNews && !showProfileAccount && activeAudioEffectPage == null && activePicker == null && !showNameDialog && !showAboutDialog) {
        showWidgetSettings = false
    }

    BackHandler(enabled = showDeveloperNews && !showLyricsSettings && !showProfileAccount && activeAudioEffectPage == null && activePicker == null && !showNameDialog && !showAboutDialog) {
        showDeveloperNews = false
    }

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

    var clearAction by remember { mutableStateOf<SettingsClearAction?>(null) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showAutoBackupDialog by remember { mutableStateOf(false) }
    var autoBackupEnabled by remember { mutableStateOf(AutoBackupScheduler.settings(context).getBoolean(AutoBackupScheduler.KEY_ENABLED, false)) }
    var backupPassword by remember { mutableStateOf("") }
    var backupPendingPassword by remember { mutableStateOf("") }
    var backupPendingSelection by remember { mutableStateOf(setOf("account", "library")) }
    var backupBusy by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restorePassword by remember { mutableStateOf("") }
    var restoreBusy by remember { mutableStateOf(false) }
    var restoreError by remember { mutableStateOf<String?>(null) }
    var scannedBackup by remember { mutableStateOf<BackupRestoreManager.ScannedBackup?>(null) }
    var pendingAutoBackupConfig by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val saveAutoBackup: (Boolean, String) -> Unit = { enabled, password ->
        val saved = AutoBackupScheduler.configure(context, enabled, password)
        if (saved) {
            autoBackupEnabled = enabled
            showAutoBackupDialog = false
            Toast.makeText(context, if (enabled) "Daily encrypted backup enabled" else "Automatic backup disabled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Could not encrypt the password securely. Automatic backup was not changed.", Toast.LENGTH_LONG).show()
        }
    }
    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val pending = pendingAutoBackupConfig
        pendingAutoBackupConfig = null
        if (granted && pending != null) saveAutoBackup(pending.first, pending.second)
        else if (!granted) Toast.makeText(context, "Storage permission is needed to save backups in Downloads", Toast.LENGTH_LONG).show()
    }

    val createBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) {
            backupPendingPassword = ""
        } else {
            backupBusy = true
            coroutineScope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val bytes = BackupRestoreManager.createEncryptedBackup(
                            context, backupPendingSelection, backupPendingPassword.toCharArray(),
                        )
                        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                            ?: error("Could not open the selected destination")
                    }
                }
                backupBusy = false
                backupPassword = ""
                backupPendingPassword = ""
                Toast.makeText(
                    context,
                    if (result.isSuccess) "Encrypted .itune backup saved" else result.exceptionOrNull()?.message ?: "Backup failed",
                    if (result.isSuccess) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()
            if (name.isNullOrBlank() || !name.endsWith(".itune", ignoreCase = true)) {
                Toast.makeText(context, "Choose an .itune backup file", Toast.LENGTH_LONG).show()
            } else {
                restoreUri = uri
                restorePassword = ""
                restoreError = null
                scannedBackup = null
            }
        }
    }

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
        MusicCacheManager.cacheQuality.value,
        MusicCacheManager.totalStorageBytes.longValue,
        availableUpdate?.tag,
        latestRelease?.tag,
        isCheckingForUpdate,
    ) {
        buildSettingsSections(
            state = state,
            audioEngine = audioEngineState,
            bitPerfectActive = audioOutputState.bitPerfectActive,
            activeOutputName = audioOutputState.activeDevice?.name ?: "Phone Speaker",
            searchHistoryCount = searchHistory.size,
            updateVersion = availableUpdate?.tag,
            latestVersion = latestRelease?.tag,
            isCheckingForUpdate = isCheckingForUpdate,
            iShareSession = iShareSession,
            iShareIsLan = iShareMode == IShareConnectionMode.LAN,
            dynamicIslandEnabled = dynamicIslandConfig.enabled && android.provider.Settings.canDrawOverlays(context),
            onOpenIShare = onOpenIShare,
            onOpenDynamicIsland = onOpenDynamicIsland,
            onOpenAudioEffect = { page -> activeAudioEffectPage = page },
            onOpenLyricsSettings = { showLyricsSettings = true },
            onOpenDeveloperNews = { showDeveloperNews = true },
            onToggleBool = { key, enabled ->
                SettingsStore.setBoolean(context, key, enabled)
                if (key == SettingsStore.KEY_SYNC_LIBRARY && enabled) {
                    YouTubeLibrarySyncManager.launchFullSync(context)
                }
                if (key == SettingsStore.KEY_ADD_FAVORITE_SONGS) {
                    LibraryStore.setLikedSongsSavedLocally(context, enabled)
                }
                if (key == SettingsStore.KEY_AUTO_DOWNLOADS && enabled) {
                    LibraryStore.likedSongs.value.forEach { com.darkxvenom.itune.backend.MusicPlayerState.enqueueAutoDownloadForSettings(it.toMusicTrack()) }
                }
            },
            onSelectString = { key, value ->
                if (key == SettingsStore.KEY_PCM_OUTPUT_FORMAT) {
                    com.darkxvenom.itune.backend.MusicPlayerState.setPcmOutputFormat(value)
                } else {
                    SettingsStore.setString(context, key, value)
                }
            },
            onClearCache = {
                SettingsStore.clearAudioCache(context)
                Toast.makeText(context, "Audio & artwork cache cleared", Toast.LENGTH_SHORT).show()
            },
            onClearSongCache = { clearAction = SettingsClearAction.CACHED_SONGS },
            onEditName = { showNameDialog = true },
            onClearSearchHistory = {
                clearAction = SettingsClearAction.SEARCH_HISTORY
            },
            onClearListeningHistory = {
                clearAction = SettingsClearAction.LISTENING_HISTORY
            },
            onOpenAbout = { showAboutDialog = true },
            onOpenBackup = { showBackupDialog = true },
            onOpenAutoBackup = { showAutoBackupDialog = true },
            onOpenRestore = { restoreBackupLauncher.launch(arrayOf("*/*")) },
            onOpenAutoSleepTimer = { showAutoSleepTimerDialog = true },
            onOpenProxySettings = { showProxySettingsDialog = true },
            onOpenAodSettings = { showAodSettings = true },
            onOpenWidgetSettings = { showWidgetSettings = true },
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

    ITubeArtistTheme {
        CompositionLocalProvider(
            LocalAppBackdrop provides appBackdrop,
            LocalLiquidGlassEnabled provides glassActive,
            LocalReduceDynamicBlur provides !state.liquidGlassChrome,
        ) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .snapshotBackdropSource(pageBackdrop, floor = MaterialTheme.colorScheme.background)
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
                                        color = MaterialTheme.colorScheme.onSurface,
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
                                        text = "Version ${AppUpdateManager.installedVersion} (${AppUpdateManager.installedVersionCode})",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        text = "GitHub repository · Open source",
                                        color = Color(0xFF0A84FF),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.clickable {
                                            runCatching {
                                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://github.com/d0x-dev/itune")))
                                            }.onFailure {
                                                Toast.makeText(context, "Could not open GitHub", Toast.LENGTH_SHORT).show()
                                            }
                                        },
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
                                    color = MaterialTheme.colorScheme.onSurface,
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

                AnimatedVisibility(
                    visible = showDeveloperNews,
                    enter = fadeIn(tween(200)) + slideInHorizontally(tween(260)) { it / 8 },
                    exit = fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 8 },
                ) {
                    DeveloperNewsScreen(
                        hasMiniPlayer = hasMiniPlayer,
                        onBack = { showDeveloperNews = false },
                    )
                }

                AnimatedVisibility(
                    visible = showAodSettings,
                    enter = fadeIn(tween(200)) + slideInHorizontally(tween(260)) { it / 8 },
                    exit = fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 8 },
                ) {
                    AlwaysOnDisplaySettingsScreen(
                        onBack = { showAodSettings = false },
                    )
                }

                AnimatedVisibility(
                    visible = showWidgetSettings,
                    enter = fadeIn(tween(200)) + slideInHorizontally(tween(260)) { it / 8 },
                    exit = fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 8 },
                ) {
                    WidgetSettingsScreen(
                        hasMiniPlayer = hasMiniPlayer,
                        onBack = { showWidgetSettings = false },
                    )
                }
            }
        }
    }

    activePicker?.let { picker ->
        val latestPicker = (sections.asSequence()
            .flatMap { it.entries.asSequence() }
            .firstOrNull { it.id == picker.id } as? SettingsEntry.Picker) ?: picker
        if (!latestPicker.enabled) {
            activePicker = null
        } else {
            SettingsOptionPickerDialog(
                picker = latestPicker,
                haptics = haptics,
                onDismiss = { activePicker = null },
            )
        }
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
            release = latestRelease,
            updateAvailable = availableUpdate != null,
            isCheckingForUpdate = isCheckingForUpdate,
            onOpenRelease = { release -> AppUpdateManager.downloadApk(context, release) },
            onReplayWelcome = {
                showAboutDialog = false
                SettingsStore.setBoolean(context, SettingsStore.KEY_WELCOME_COMPLETED, false)
            },
            onDismiss = { showAboutDialog = false },
        )
    }

    if (showAutoSleepTimerDialog) {
        AutoSleepTimerDialog(onDismiss = { showAutoSleepTimerDialog = false })
    }

    if (showProxySettingsDialog) {
        ProxySettingsDialog(onDismiss = { showProxySettingsDialog = false })
    }

    if (showBackupDialog) {
        BackupOptionsDialog(
            password = backupPassword,
            onPasswordChange = { backupPassword = it },
            onDismiss = { showBackupDialog = false; backupPassword = "" },
            onCreate = { selected ->
                backupPendingSelection = selected
                backupPendingPassword = backupPassword
                showBackupDialog = false
                createBackupLauncher.launch("iTune-backup-${System.currentTimeMillis()}.itune")
            },
        )
    }

    if (showAutoBackupDialog) {
        AutoBackupDialog(
            initiallyEnabled = autoBackupEnabled,
            savedPassword = EncryptedAuthPrefs.getDecrypted(context, AutoBackupScheduler.PASSWORD_KEY).orEmpty(),
            onDismiss = { showAutoBackupDialog = false },
            onSave = { enabled, password ->
                if (enabled && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                    context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                ) {
                    pendingAutoBackupConfig = enabled to password
                    legacyStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    saveAutoBackup(enabled, password)
                }
            },
        )
    }

    val pendingRestoreUri = restoreUri
    if (pendingRestoreUri != null && scannedBackup == null) {
        RestorePasswordDialog(
            password = restorePassword,
            onPasswordChange = { restorePassword = it; restoreError = null },
            busy = restoreBusy,
            error = restoreError,
            onDismiss = { if (!restoreBusy) { restoreUri = null; restorePassword = ""; restoreError = null } },
            onScan = {
                restoreBusy = true
                restoreError = null
                val password = restorePassword.toCharArray()
                coroutineScope.launch {
                    val result = runCatching {
                        withContext(Dispatchers.IO) {
                            val bytes = context.contentResolver.openInputStream(pendingRestoreUri)?.use { it.readBytes() }
                                ?: error("Could not read the selected backup")
                            BackupRestoreManager.scanEncryptedBackup(bytes, password)
                        }
                    }
                    password.fill('\u0000')
                    restoreBusy = false
                    result.onSuccess { scannedBackup = it }
                        .onFailure { restoreError = it.message ?: "Could not read this backup" }
                }
            },
        )
    }

    scannedBackup?.let { scanned ->
        RestoreOptionsDialog(
            accountDescription = scanned.accountDescription,
            libraryDescription = scanned.libraryDescription,
            available = scanned.categories,
            onDismiss = { scannedBackup = null; restoreUri = null; restorePassword = "" },
            onRestore = { selected ->
                restoreBusy = true
                coroutineScope.launch {
                    val result = runCatching {
                        withContext(Dispatchers.IO) { BackupRestoreManager.restore(context, scanned, selected) }
                        BackupRestoreManager.refreshRestoredState(context, selected)
                    }
                    restoreBusy = false
                    scannedBackup = null
                    restoreUri = null
                    restorePassword = ""
                    Toast.makeText(
                        context,
                        if (result.isSuccess) "Backup restored" else result.exceptionOrNull()?.message ?: "Restore failed",
                        if (result.isSuccess) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                    ).show()
                }
            },
        )
    }

    val pendingClearAction = clearAction
    if (pendingClearAction != null) {
        val cacheCount = MusicCacheManager.cachedTracks.size
        val cacheSize = MusicCacheManager.formatStorageSize(MusicCacheManager.totalStorageBytes.longValue)
        val (title, message, confirmLabel) = when (pendingClearAction) {
            SettingsClearAction.SEARCH_HISTORY -> Triple(
                "Clear Search History?",
                "This will remove your recent searches from this device.",
                "Clear History",
            )
            SettingsClearAction.LISTENING_HISTORY -> Triple(
                "Clear Song History?",
                "This will remove your listening history and recently played songs from this device.",
                "Clear History",
            )
            SettingsClearAction.CACHED_SONGS -> Triple(
                "Clear Cached Songs?",
                "This will permanently remove all $cacheCount offline songs ($cacheSize) from device storage.",
                "Clear All",
            )
        }
        AppleConfirmDialog(
            title = title,
            message = message,
            confirmLabel = confirmLabel,
            onDismiss = { clearAction = null },
            onConfirm = {
                when (pendingClearAction) {
                    SettingsClearAction.SEARCH_HISTORY -> {
                        SearchHistoryStore.clearAll(context)
                        Toast.makeText(context, "Search history cleared", Toast.LENGTH_SHORT).show()
                    }
                    SettingsClearAction.LISTENING_HISTORY -> {
                        com.darkxvenom.itune.data.library.ListeningProfileStore.clear(context)
                        com.darkxvenom.itune.data.library.LibraryStore.clearRecentSongs(context)
                        Toast.makeText(context, "Listening history cleared", Toast.LENGTH_SHORT).show()
                    }
                    SettingsClearAction.CACHED_SONGS -> {
                        MusicCacheManager.clearAllCache()
                        Toast.makeText(context, "Cached songs cleared", Toast.LENGTH_SHORT).show()
                    }
                }
                clearAction = null
            },
        )
    }
}

@Composable
private fun SettingsBlackGlassBackdrop(
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.background))
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
            .background(MaterialTheme.colorScheme.surface),
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
                    color = MaterialTheme.colorScheme.onSurface,
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
                    .background(MaterialTheme.colorScheme.surfaceVariant)
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
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = AppleTextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                )
            }

            Spacer(Modifier.width(8.dp))

            Icon(
                imageVector = SfIcons.ChevronRight,
                contentDescription = "Choose Avatar",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
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
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
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
    val isEntryEnabled = when (entry) {
        is SettingsEntry.Picker -> entry.enabled
        is SettingsEntry.Toggle -> entry.enabled
        else -> true
    }
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isEntryEnabled) 1f else 0.40f,
        label = "settingsRowAlpha"
    )

    val rowClick: () -> Unit = when (entry) {
        is SettingsEntry.Toggle -> {
            {
                val next = !entry.checked
                if (entry.enabled) {
                    haptics.play(if (next) Haptic.ToggleOn else Haptic.ToggleOff)
                    entry.onToggle(next)
                }
            }
        }
        is SettingsEntry.Picker -> {
            {
                if (entry.enabled) {
                    haptics.play(Haptic.Tap)
                    onOpenPicker(entry)
                }
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
            .alpha(animatedAlpha)
            .clickable(enabled = isEntryEnabled, onClick = rowClick)
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
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(21.dp),
            )
        }

        Spacer(Modifier.width(13.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).basicMarquee(iterations = Int.MAX_VALUE),
                )
                if (entry is SettingsEntry.EffectToggleAction) {
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        imageVector = SfIcons.ChevronRight,
                        contentDescription = "Open ${entry.title} options",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
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
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        when (entry) {
            is SettingsEntry.Toggle -> {
                AppleSettingsSlidingSwitch(
                    checked = entry.checked,
                    onCheckedChange = { checked ->
                        if (entry.enabled) {
                            haptics.play(if (checked) Haptic.ToggleOn else Haptic.ToggleOff)
                            entry.onToggle(checked)
                        }
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
                    AppleSettingsSlidingSwitch(
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
                    modifier = Modifier.widthIn(max = 120.dp).basicMarquee(iterations = Int.MAX_VALUE),
                )
                Spacer(Modifier.width(7.dp))
                Icon(
                    imageVector = SfIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.36f),
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
                            color = MaterialTheme.colorScheme.onSurface,
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
                        modifier = Modifier.widthIn(max = 120.dp).basicMarquee(iterations = Int.MAX_VALUE),
                    )
                    Spacer(Modifier.width(7.dp))
                }
                Icon(
                    imageVector = SfIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.36f),
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
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "appleSwitchThumb",
    )

    Box(
        modifier = Modifier
            .size(width = 50.dp, height = 30.dp)
            .clip(CircleShape)
            .background(trackColor)
            .border(0.6.dp, Color.White.copy(alpha = if (checked) 0.14f else 0.18f), CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                onCheckedChange(!checked)
            }
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
private fun AppleSettingsSlidingSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    AppleSegmentedSwitch(
        options = listOf("Off", "On"),
        selectedOption = if (checked) "On" else "Off",
        onSelect = { onCheckedChange(it == "On") },
        modifier = Modifier.width(86.dp),
    )
}

@Composable
fun AppleSegmentedSwitch(
    options: List<String>,
    selectedOption: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier.width(152.dp),
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
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
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
                            fontFamily = LocalAppFontFamily.current,
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
            color = MaterialTheme.colorScheme.onSurface,
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
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
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
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
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
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
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
            color = MaterialTheme.colorScheme.onSurface,
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
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    fontSize = 15.5.sp,
                )
            }
            BasicTextField(
                value = fieldValue,
                onValueChange = { fieldValue = it },
                singleLine = true,
                cursorBrush = SolidColor(AppleMusicRed),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
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
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

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

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

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
    release: GithubReleaseInfo?,
    updateAvailable: Boolean,
    isCheckingForUpdate: Boolean,
    onOpenRelease: (GithubReleaseInfo) -> Unit,
    onReplayWelcome: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val updateDownloadState by AppUpdateManager.downloadState.collectAsStateWithLifecycle()
    val downloading = updateDownloadState as? com.darkxvenom.itune.data.updates.UpdateDownloadState.Downloading
    val downloaded = updateDownloadState as? com.darkxvenom.itune.data.updates.UpdateDownloadState.Completed
    val downloadError = updateDownloadState as? com.darkxvenom.itune.data.updates.UpdateDownloadState.Failed
    SettingsModalFrame(onDismiss = onDismiss) {
        Spacer(Modifier.height(22.dp))
        Image(
            painter = painterResource(R.drawable.ic_itune_waves),
            contentDescription = "iTune icon",
            modifier = Modifier.size(54.dp),
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = "iTune",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .border(0.6.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AboutInfoLine("Version", AppUpdateManager.installedVersion)
            AboutInfoLine("Version code", AppUpdateManager.installedVersionCode.toString())
            AboutInfoLine("Latest on GitHub", release?.tag ?: if (isCheckingForUpdate) "Checking…" else "No release found")
            AboutInfoLine("Release codename", release?.name ?: "Not published")
            AboutInfoLine("Last update", release?.let { AppUpdateManager.formattedPublishedDate(it.publishedAt) } ?: "—")
            AboutInfoLine(
                "Status",
                when {
                    updateAvailable -> "Update available"
                    isCheckingForUpdate -> "Checking"
                    release != null -> "Up to date"
                    else -> "Release unavailable"
                },
            )
        }

        if (release != null && updateAvailable) {
            Spacer(Modifier.height(12.dp))
            downloading?.let { state ->
                val total = state.totalBytes.takeIf { it > 0L }
                val status = when {
                    state.downloadedBytes == 0L && total == null -> "Connecting to GitHub…"
                    state.downloadedBytes == 0L -> "Connected · preparing download…"
                    total == null -> "Downloading update · ${formatAboutBytes(state.downloadedBytes)}"
                    else -> "Downloading update · ${formatAboutBytes(state.downloadedBytes)} of ${formatAboutBytes(total)}"
                }
                Text(
                    text = status,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                )
                if (state.progressPercent < 0) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(4.dp).clip(RoundedCornerShape(3.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.16f),
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { state.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(4.dp).clip(RoundedCornerShape(3.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.16f),
                    )
                }
            }
            downloadError?.let { error ->
                Text(
                    text = error.message,
                    color = Color(0xFFFF6961),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color(0xFF0A84FF).copy(alpha = 0.18f))
                    .border(0.7.dp, Color(0xFF0A84FF).copy(alpha = 0.30f), RoundedCornerShape(13.dp))
                    .clickable {
                        when {
                            downloading != null -> Unit
                            downloaded != null -> AppUpdateManager.launchInstallerFlow(context, downloaded.apkFile)
                            else -> onOpenRelease(release)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when {
                        downloading != null -> "Downloading ${downloading.progressPercent.takeIf { it >= 0 }?.let { "$it%" } ?: "…"}"
                        downloaded != null -> "Install iTune ${release.tag}"
                        downloadError != null -> "Retry iTune ${release.tag} Download"
                        release.hasApkAsset -> "Download iTune ${release.tag}"
                        else -> "View iTune ${release.tag} Release"
                    },
                    color = Color(0xFF64AFFF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clickable { onReplayWelcome() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Replay Welcome Screen",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
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

private fun formatAboutBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024f)
    bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024f * 1024f))
    else -> "%.2f GB".format(bytes / (1024f * 1024f * 1024f))
}

@Composable
private fun AboutInfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
            fontSize = 13.sp,
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
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
                    .background(MaterialTheme.colorScheme.surface)
                    .border(0.75.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp)),
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
    updateVersion: String?,
    latestVersion: String?,
    isCheckingForUpdate: Boolean,
    iShareSession: IShareSession?,
    iShareIsLan: Boolean,
    dynamicIslandEnabled: Boolean,
    onOpenIShare: () -> Unit,
    onOpenDynamicIsland: () -> Unit,
    onOpenAudioEffect: (EnginePopupPage) -> Unit,
    onOpenLyricsSettings: () -> Unit,
    onOpenDeveloperNews: () -> Unit,
    onToggleBool: (String, Boolean) -> Unit,
    onSelectString: (String, String) -> Unit,
    onClearCache: () -> Unit,
    onClearSongCache: () -> Unit,
    onEditName: () -> Unit,
    onClearSearchHistory: () -> Unit,
    onClearListeningHistory: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAutoBackup: () -> Unit,
    onOpenRestore: () -> Unit,
    onOpenAutoSleepTimer: () -> Unit,
    onOpenProxySettings: () -> Unit,
    onOpenAodSettings: () -> Unit,
    onOpenWidgetSettings: () -> Unit,
): List<SettingsSection> {
    val effectsDisabled = audioEngine.bypassAllEffects || (audioEngine.bitPerfectEnabled && bitPerfectActive)
    return listOf(
        SettingsSection(
            id = "ishare_together",
            title = "iShare, Dynamic Island & StandBy",
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
                SettingsEntry.Action(
                    id = "always_on_display",
                    title = "Always On Display",
                    subtitle = if (state.aodEnabled) {
                        "Auto-activates on idle (${if (state.aodTimeoutSeconds > 0) "${state.aodTimeoutSeconds}s" else "Manual"}) • OLED StandBy"
                    } else {
                        "StandBy OLED widget with water bubble glass"
                    },
                    iconRes = R.drawable.ic_menu_aod,
                    keywords = listOf("aod", "always on display", "standby", "oled", "screen", "black", "idle", "timeout"),
                    trailingValue = if (state.aodEnabled) "On" else "Off",
                    onAction = onOpenAodSettings,
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
                SettingsEntry.Toggle(
                    id = "engine_low_pass_filter",
                    title = "Low-Pass Filtering",
                    subtitle = if (audioEngine.lowPassFilterActive && !effectsDisabled) {
                        "Active on hook line (11s) • Bass filter"
                    } else if (audioEngine.lowPassFilterEnabled && !effectsDisabled) {
                        "Auto-applies low-pass EQ curve on most repeated hook for 10-12s"
                    } else {
                        "Triggers muffled party EQ on most repeated synced lyric line"
                    },
                    iconRes = R.drawable.ic_fx_low_pass,
                    keywords = listOf("low-pass", "filter", "eq", "lyrics", "hook", "chorus", "bass", "audio"),
                    checked = audioEngine.lowPassFilterEnabled && !effectsDisabled,
                    onToggle = { AudioEngineManager.setLowPassFilterEnabled(it) },
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
                    id = "auto_skip_on_error",
                    title = "Auto Skip When Error Occurs",
                    subtitle = "Move to the next queued song if playback fails",
                    iconRes = R.drawable.skip_next,
                    keywords = listOf("auto skip", "skip", "error", "failed playback", "next track"),
                    checked = state.autoSkipOnError,
                    onToggle = { onToggleBool(SettingsStore.KEY_AUTO_SKIP_ON_ERROR, it) },
                ),
                SettingsEntry.Picker(
                    id = "download_quality",
                    title = "Download Quality",
                    subtitle = "Default quality used by song download actions",
                    iconRes = R.drawable.ic_settings_streaming_quality,
                    keywords = listOf("download", "quality", "audio", "maximum", "standard", "data saver"),
                    currentValue = state.downloadQuality,
                    description = "Choose the default audio quality for downloads across the app.",
                    options = listOf(
                        SettingsOptionChoice("Best Available", "Use the highest available source quality"),
                        SettingsOptionChoice("Maximum", "Choose the highest bitrate stream"),
                        SettingsOptionChoice("High", "Target about 160 kbps"),
                        SettingsOptionChoice("Standard", "Target about 128 kbps"),
                        SettingsOptionChoice("Data Saver", "Target about 64 kbps"),
                    ),
                    onSelect = { onSelectString(SettingsStore.KEY_DOWNLOAD_QUALITY, it) },
                ),
                SettingsEntry.Toggle(
                    id = "lossless_audio",
                    title = "Lossless Audio",
                    subtitle = if (state.losslessAudio) {
                        "Studio-grade maximum fidelity audio stream"
                    } else {
                        "Disabled · Select streaming bitrate below"
                    },
                    iconRes = R.drawable.ic_settings_lossless,
                    keywords = listOf("flac", "alac", "hires", "studio", "fidelity", "quality", "lossless"),
                    checked = state.losslessAudio,
                    onToggle = { onToggleBool(SettingsStore.KEY_LOSSLESS_AUDIO, it) },
                ),
                SettingsEntry.Picker(
                    id = "audio_quality",
                    title = "Streaming Quality",
                    subtitle = if (state.losslessAudio) {
                        "Locked to Maximum Quality (Lossless Active)"
                    } else {
                        "Select audio quality (Extreme, High, Medium, Low)"
                    },
                    iconRes = R.drawable.ic_settings_streaming_quality,
                    keywords = listOf("bitrate", "quality", "audio", "extreme", "high", "medium", "low", "stream"),
                    currentValue = if (state.losslessAudio) "Lossless (Max)" else state.audioQuality,
                    description = "Select your preferred streaming audio quality.",
                    enabled = !state.losslessAudio,
                    options = listOf(
                        SettingsOptionChoice("Extreme", "Maximum fidelity (256+ kbps)"),
                        SettingsOptionChoice("High", "High quality audio (~160 kbps)"),
                        SettingsOptionChoice("Medium", "Standard audio quality (~128 kbps)"),
                        SettingsOptionChoice("Low", "Data saver audio (~48-64 kbps)"),
                    ),
                    onSelect = { onSelectString(SettingsStore.KEY_AUDIO_QUALITY, it) },
                ),
                SettingsEntry.Picker(
                    id = "pcm_output_format",
                    title = "PCM Output Format",
                    subtitle = "Rebuilds playback at the current position",
                    iconRes = R.drawable.ic_settings_streaming_quality,
                    keywords = listOf("pcm", "16 bit", "32 bit", "float", "uac", "usb dac", "output format"),
                    currentValue = state.pcmOutputFormat,
                    description = "Choose the PCM format requested from Android's audio output. A USB DAC may convert it to a format it supports.",
                    options = listOf(
                        SettingsOptionChoice("16-bit PCM", "Standard integer PCM output; broadly compatible with USB Audio Class DACs"),
                        SettingsOptionChoice("32-bit float PCM", "Float PCM output when supported by Android and the connected audio path"),
                    ),
                    onSelect = { onSelectString(SettingsStore.KEY_PCM_OUTPUT_FORMAT, it) },
                ),
            ).let { baseEntries ->
                if (!state.autoMix) {
                    baseEntries + listOf(
                        SettingsEntry.Picker(
                            id = "crossfade",
                            title = "Crossfade",
                            subtitle = if (state.afterglowDuration == "Off") "Smooth transition between songs" else "Unavailable while Afterglow is active",
                            iconRes = R.drawable.ic_settings_crossfade,
                            keywords = listOf("gapless", "fade", "blend", "mix", "dj", "transition"),
                            currentValue = state.crossfadeDuration,
                            description = "Overlap the fade-out of the ending track with the start of the next.",
                            enabled = state.afterglowDuration == "Off",
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
                    title = "AutoMix",
                    subtitle = if (state.afterglowDuration == "Off") "Smart blends that adapt to every song" else "Unavailable while Afterglow is active",
                    iconRes = R.drawable.ic_settings_automix,
                    keywords = listOf("automix", "auto mix", "smart", "dj", "blend", "transition", "crossfade", "level", "loudness"),
                    checked = state.autoMix,
                    enabled = state.afterglowDuration == "Off",
                    onToggle = { onToggleBool(SettingsStore.KEY_AUTOMIX, it) },
                ),
                SettingsEntry.Picker(
                    id = "afterglow",
                    title = "Afterglow",
                    subtitle = if (state.afterglowDuration == "Off") "Apply the Low-Pass effect at each song’s end" else "Low-Pass for the final ${state.afterglowDuration.removeSuffix(" sec")} seconds",
                    iconRes = R.drawable.ic_fx_low_pass,
                    keywords = listOf("afterglow", "ending", "outro", "low pass", "final seconds", "song end"),
                    currentValue = state.afterglowDuration,
                    description = "Apply the same Low-Pass Equalizer effect during the selected final seconds of every song. Afterglow pauses AutoMix and Crossfade while enabled.",
                    enabled = true,
                    options = listOf(
                        SettingsOptionChoice("Off", "Keep normal playback effects and transitions"),
                        SettingsOptionChoice("5 sec", "Apply Low-Pass during each song’s final 5 seconds"),
                        SettingsOptionChoice("10 sec", "Apply Low-Pass during each song’s final 10 seconds"),
                        SettingsOptionChoice("15 sec", "Apply Low-Pass during each song’s final 15 seconds"),
                    ),
                    onSelect = { onSelectString(SettingsStore.KEY_AFTERGLOW_DURATION, it) },
                ),
                SettingsEntry.Toggle(
                    id = "permanent_shuffle",
                    title = "Permanent Shuffle",
                    subtitle = "Keep shuffle permanently locked on in player",
                    iconRes = R.drawable.shuffle,
                    keywords = listOf("shuffle", "permanent", "lock", "random", "playback"),
                    checked = state.permanentShuffle,
                    onToggle = { onToggleBool(SettingsStore.KEY_PERMANENT_SHUFFLE, it) },
                ),
                SettingsEntry.Toggle(
                    id = "pause_on_zero_volume",
                    title = "Pause at Volume 0",
                    subtitle = "Automatically pause playback when volume reaches zero",
                    iconRes = R.drawable.volume_off,
                    keywords = listOf("pause", "volume", "zero", "mute", "playback", "silence"),
                    checked = state.pauseOnZeroVolume,
                    onToggle = { onToggleBool(SettingsStore.KEY_PAUSE_ON_ZERO_VOLUME, it) },
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
                id = "player_dynamic_coloring",
                title = "Player Dynamic Coloring",
                subtitle = if (state.playerDynamicColoring) {
                    "Artwork colors on player controls, text & button bubbles"
                } else {
                    "Original white player colors"
                },
                iconRes = R.drawable.ic_settings_liquid_glass,
                keywords = listOf("player", "dynamic", "color", "colour", "artwork", "accent", "buttons", "text", "bubbles"),
                checked = state.playerDynamicColoring,
                onToggle = { onToggleBool(SettingsStore.KEY_PLAYER_DYNAMIC_COLORING, it) },
            ),
            SettingsEntry.SegmentedToggle(
                id = "top_card_type",
                title = "Home Top Card Style",
                subtitle = if (state.topCardType == SettingsStore.TOP_CARD_TYPE_PORTRAIT) {
                    "Portrait • Apple Music card with frosted blur"
                } else {
                    "Landscape • Wide 16:9 banner card"
                },
                iconRes = R.drawable.ic_settings_liquid_glass,
                keywords = listOf(
                    "top", "card", "hero", "portrait", "landscape", "home", "carousel",
                    "style", "orientation", "shape", "banner"
                ),
                options = listOf(SettingsStore.TOP_CARD_TYPE_LANDSCAPE, SettingsStore.TOP_CARD_TYPE_PORTRAIT),
                selectedOption = state.topCardType,
                onSelect = { onSelectString(SettingsStore.KEY_TOP_CARD_TYPE, it) },
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
            SettingsEntry.Picker(
                id = "app_font",
                title = "App Font",
                subtitle = "Change app typography; the player keeps its current SF Pro style",
                iconRes = R.drawable.ic_settings_liquid_glass,
                keywords = listOf("font", "typeface", "typography", "linotte", "poppins", "sf pro"),
                currentValue = AppFont.fromKey(state.appFont).label,
                description = "Choose a typeface for the app interface.",
                options = AppFont.entries.map { font ->
                    SettingsOptionChoice(font.label, "Use ${font.label} throughout the app")
                },
                onSelect = { onSelectString(SettingsStore.KEY_APP_FONT, it) },
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
                subtitle = "Wait for Wi-Fi before automatic downloads",
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
                id = "song_cache_quality",
                title = "Cache Quality",
                subtitle = "Lower quality saves storage",
                iconRes = R.drawable.ic_settings_storage,
                keywords = listOf("cache", "quality", "compress", "low", "high", "space", "storage", "bitrate"),
                currentValue = MusicCacheManager.cacheQuality.value,
                description = "Choose the quality of songs saved to offline storage. Applies to songs cached from now on.",
                options = listOf(
                    SettingsOptionChoice(MusicCacheManager.QUALITY_LOW, "Compressed to 64 kbps AAC • smallest files (~2 MB per song)"),
                    SettingsOptionChoice(MusicCacheManager.QUALITY_MEDIUM, "Compressed to 128 kbps AAC • balanced (~4 MB per song)"),
                    SettingsOptionChoice(MusicCacheManager.QUALITY_HIGH, "Highest available quality, never compressed"),
                ),
                onSelect = { MusicCacheManager.setCacheQuality(it) },
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
                title = "Keep Likes on This Device",
                subtitle = "Keep liked songs in iTune when signed out",
                iconRes = R.drawable.ic_settings_favorites,
                keywords = listOf("liked", "heart", "star", "favorites", "library", "offline", "sign out"),
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
                id = "offline_mode",
                title = "Offline Mode",
                subtitle = "Require a downloaded copy to start playback",
                iconRes = R.drawable.ic_settings_offline,
                keywords = listOf("airplane", "offline", "local", "disconnect"),
                checked = state.offlineMode,
                onToggle = { onToggleBool(SettingsStore.KEY_OFFLINE_MODE, it) },
            ),
            SettingsEntry.Toggle(
                id = "stop_music_on_task_clear",
                title = "Stop Music on Task Clear",
                subtitle = "Stop playback when iTune is swiped away from recents",
                iconRes = R.drawable.ic_settings_sleep_timer,
                keywords = listOf("stop", "close", "recents", "swipe", "task", "clear", "background", "playback"),
                checked = state.stopMusicOnTaskClear,
                onToggle = { onToggleBool(SettingsStore.KEY_STOP_MUSIC_ON_TASK_CLEAR, it) },
            ),
            SettingsEntry.Action(
                id = "auto_sleep_timer",
                title = "Auto Sleep Timer",
                subtitle = if (state.autoSleepTimerEnabled) {
                    val sH = if (state.autoSleepStartHour == 0) 12 else if (state.autoSleepStartHour > 12) state.autoSleepStartHour - 12 else state.autoSleepStartHour
                    val sAm = if (state.autoSleepStartHour >= 12) "PM" else "AM"
                    val eH = if (state.autoSleepEndHour == 0) 12 else if (state.autoSleepEndHour > 12) state.autoSleepEndHour - 12 else state.autoSleepEndHour
                    val eAm = if (state.autoSleepEndHour >= 12) "PM" else "AM"
                    "Active · %d:%02d %s – %d:%02d %s".format(sH, state.autoSleepStartMinute, sAm, eH, state.autoSleepEndMinute, eAm)
                } else {
                    "Off · Schedule recurring sleep hours"
                },
                iconRes = R.drawable.ic_settings_sleep_timer,
                keywords = listOf("sleep", "auto", "timer", "schedule", "night", "bedtime"),
                trailingValue = if (state.autoSleepTimerEnabled) "On" else "Off",
                onAction = onOpenAutoSleepTimer,
            ),
            SettingsEntry.Action(
                id = "network_proxy",
                title = "Network Proxy",
                subtitle = if (state.proxyEnabled && state.proxyHost.isNotBlank()) {
                    "${state.proxyType} · ${state.proxyHost}:${state.proxyPort}"
                } else {
                    "Off · Route via HTTP / SOCKS proxy"
                },
                iconRes = R.drawable.ic_settings_proxy,
                keywords = listOf("proxy", "socks", "socks5", "socks4", "http", "network", "vpn"),
                trailingValue = if (state.proxyEnabled) "On" else "Off",
                onAction = onOpenProxySettings,
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
                id = "developer_news",
                title = "News from dev",
                subtitle = "Announcements, releases & changelog",
                iconRes = R.drawable.newspaper,
                keywords = listOf("news", "developer", "dev", "updates", "changelog", "announcements", "release", "airbeats"),
                trailingValue = null,
                onAction = onOpenDeveloperNews,
            ),
            SettingsEntry.Action(
                id = "about_itune",
                title = "General & About",
                subtitle = when {
                    updateVersion != null -> "iTune $updateVersion is ready to update • Build details & Liquid Glass"
                    isCheckingForUpdate -> "Checking GitHub for updates • Build details & Liquid Glass"
                    else -> "Build details, audio DSP & Liquid Glass"
                },
                iconRes = R.drawable.ic_settings_about,
                keywords = listOf("version", "about", "general", "build", "info", "update"),
                trailingValue = latestVersion?.let { "v$it" } ?: "v${AppUpdateManager.installedVersion}",
                badgeCount = if (updateVersion != null) 1 else null,
                onAction = onOpenAbout,
            ),
        ),
    ),
    SettingsSection(
        id = "home_screen_widget",
        title = "Home Screen Widget",
        entries = listOf(
            SettingsEntry.Action(
                id = "widget_customization",
                title = "Widget Customization",
                subtitle = "Choose a size preview, artwork, controls and widget style",
                iconRes = R.drawable.ic_settings_about,
                keywords = listOf("widget", "home screen", "player", "artwork", "controls", "style", "customize"),
                trailingValue = "Customize",
                onAction = onOpenWidgetSettings,
            ),
        ),
    ),
    SettingsSection(
        id = "backup_restore",
        title = "Backup & Restore",
        entries = listOf(
            SettingsEntry.Action(
                id = "auto_backup_itune",
                title = "Automatic Daily Backup",
                subtitle = "Encrypted daily backups • Downloads/iTune/Backups",
                iconRes = R.drawable.ic_settings_storage,
                keywords = listOf("automatic", "daily", "backup", "password", "encrypted"),
                trailingValue = "Configure",
                onAction = onOpenAutoBackup,
            ),
            SettingsEntry.Action(
                id = "backup_itune",
                title = "Back Up iTune",
                subtitle = "Save accounts, all settings, audio effects, library data and stats encrypted",
                iconRes = R.drawable.ic_settings_storage,
                keywords = listOf("backup", "export", "save", "encrypted", "account", "playlist", "history", "stats"),
                trailingValue = "Create",
                onAction = onOpenBackup,
            ),
            SettingsEntry.Action(
                id = "restore_itune",
                title = "Restore from Backup",
                subtitle = "Restore settings, audio effects, accounts or library data from .itune",
                iconRes = R.drawable.ic_settings_history,
                keywords = listOf("restore", "import", "backup", "itune", "account", "library"),
                trailingValue = "Choose File",
                onAction = onOpenRestore,
            ),
        ),
    ),
    SettingsSection(
        id = "privacy_card",
        title = "Privacy",
        entries = listOf(
            SettingsEntry.Toggle(
                id = "pause_search_history",
                title = "Pause Search History",
                subtitle = "Do not save new search queries to history",
                iconRes = R.drawable.ic_settings_clear_history,
                keywords = listOf("pause", "search", "history", "private", "queries"),
                checked = state.pauseSearchHistory,
                onToggle = { onToggleBool(SettingsStore.KEY_PAUSE_SEARCH_HISTORY, it) },
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
            SettingsEntry.Toggle(
                id = "pause_listening_history",
                title = "Pause Listening History",
                subtitle = "Do not record played songs or update recommendations",
                iconRes = R.drawable.ic_settings_history,
                keywords = listOf("pause", "listening", "history", "playback", "privacy"),
                checked = state.pauseListeningHistory,
                onToggle = { onToggleBool(SettingsStore.KEY_PAUSE_LISTENING_HISTORY, it) },
            ),
            SettingsEntry.Action(
                id = "clear_listening_history",
                title = "Clear Listening History",
                subtitle = "Remove playback history and reset taste profile",
                iconRes = R.drawable.ic_settings_history,
                keywords = listOf("clear", "listening", "history", "reset", "privacy", "taste"),
                trailingValue = "Clear",
                onAction = onClearListeningHistory,
            ),
            SettingsEntry.Toggle(
                id = "disable_screenshot",
                title = "Disable Screenshot",
                subtitle = "Block screenshots and screen recording across app",
                iconRes = R.drawable.ic_settings_explicit,
                keywords = listOf("screenshot", "capture", "record", "security", "flag_secure", "block"),
                checked = state.disableScreenshot,
                onToggle = { onToggleBool(SettingsStore.KEY_DISABLE_SCREENSHOT, it) },
            ),
        ),
    ),
).let { settingsSections ->
    settingsSections.filterNot { it.id == "notifications_general" } + settingsSections.filter { it.id == "notifications_general" }
}
}
