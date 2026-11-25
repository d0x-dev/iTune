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

import android.app.Activity
import android.content.Context

import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.automirrored.rounded.Sort
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
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
import com.darkxvenom.itune.data.library.AppLocaleManager
import com.darkxvenom.itune.data.library.SearchHistoryStore
import com.darkxvenom.itune.data.library.LibraryStore
import com.darkxvenom.itune.data.backup.BackupRestoreManager
import com.darkxvenom.itune.data.backup.AutoBackupScheduler
import com.darkxvenom.itune.data.auth.EncryptedAuthPrefs
import com.darkxvenom.itune.data.updates.AppUpdateManager
import com.darkxvenom.itune.data.updates.GithubReleaseInfo
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.data.library.SettingsUiState
import kotlin.math.roundToInt
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.backend.highQualityThumbnail
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
import com.darkxvenom.itune.data.account.UserAccount
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
import com.darkxvenom.itune.ui.spotify.SpotifyIntegrationScreen
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
    onAddNewAccount: () -> Unit = {},
    onSwitchAccount: (UserAccount, Rect?) -> Unit = { _, _ -> },
    onOpenSources: () -> Unit = {},
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
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showAutoSleepTimerDialog by remember { mutableStateOf(false) }
    var showProxySettingsDialog by remember { mutableStateOf(false) }
    var showCornerLengthDialog by remember { mutableStateOf(false) }
    var showAodSettings by rememberSaveable { mutableStateOf(false) }
    var showWidgetSettings by rememberSaveable { mutableStateOf(false) }
    var showSpotifyIntegration by rememberSaveable { mutableStateOf(false) }
    // Minimised layout: which grouped screen is open (null = the main settings screen).
    var activeSectionId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAppearancePopup by remember { mutableStateOf(false) }

    BackHandler(enabled = activeSectionId != null && !showSpotifyIntegration && !showAodSettings && !showWidgetSettings && !showLyricsSettings && !showDeveloperNews && !showProfileAccount && activeAudioEffectPage == null && activePicker == null && !showNameDialog && !showAboutDialog) {
        activeSectionId = null
    }
    BackHandler(enabled = showSpotifyIntegration && !showLyricsSettings && !showDeveloperNews && !showProfileAccount && activeAudioEffectPage == null && activePicker == null && !showNameDialog && !showAboutDialog) {
        showSpotifyIntegration = false
    }
    BackHandler(enabled = showAodSettings && !showSpotifyIntegration && !showLyricsSettings && !showDeveloperNews && !showProfileAccount && activeAudioEffectPage == null && activePicker == null && !showNameDialog && !showAboutDialog) {
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
            Toast.makeText(
                context,
                context.getString(if (enabled) R.string.daily_encrypted_backup_enabled else R.string.automatic_backup_disabled),
                Toast.LENGTH_SHORT,
            ).show()
        } else {
            Toast.makeText(context, context.getString(R.string.toast_could_not_encrypt_the_password_securely_automatic), Toast.LENGTH_LONG).show()
        }
    }
    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val pending = pendingAutoBackupConfig
        pendingAutoBackupConfig = null
        if (granted && pending != null) saveAutoBackup(pending.first, pending.second)
        else if (!granted) Toast.makeText(context, context.getString(R.string.toast_storage_permission_is_needed_to_save_backups_in_do), Toast.LENGTH_LONG).show()
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
                Toast.makeText(context, context.getString(R.string.toast_choose_an_itune_backup_file), Toast.LENGTH_LONG).show()
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
            context = context,
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
            dynamicIslandEnabled = dynamicIslandConfig.enabled && (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || android.provider.Settings.canDrawOverlays(context)),
            onOpenIShare = onOpenIShare,
            onOpenDynamicIsland = onOpenDynamicIsland,
            onOpenAudioEffect = { page -> activeAudioEffectPage = page },
            onOpenSources = onOpenSources,
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
                    if (key == SettingsStore.KEY_APP_LANGUAGE && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                        (context as? Activity)?.recreate()
                    }
                }
            },
            onClearCache = {
                SettingsStore.clearAudioCache(context)
                Toast.makeText(context, context.getString(R.string.toast_audio_artwork_cache_cleared), Toast.LENGTH_SHORT).show()
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
            onOpenSpotifyIntegration = { showSpotifyIntegration = true },
            onOpenCornerLength = { showCornerLengthDialog = true },
        )
    }

    val trimmedQuery = searchQuery.trim()
    val minimised = state.settingsMinimised && trimmedQuery.isEmpty()
    // "News from dev" and "About" always live on the main screen in the Minimised layout.
    val devAboutIds = setOf("developer_news", "about_itune")
    val devAboutEntries = remember(sections) {
        sections.flatMap { it.entries }.filter { it.id in devAboutIds }
    }
    val minimisedMainSections = remember(sections, devAboutEntries) {
        val top = sections.filter { it.id == "ishare_together" }
        val navIds = sections.filter { it.id != "ishare_together" }
        // Notifications & General is the last navigation card, then News from dev + About below it.
        val ordered = navIds.filter { it.id != "notifications_general" } + navIds.filter { it.id == "notifications_general" }
        top to ordered
    }
    val openSection = remember(sections, activeSectionId, minimised) {
        if (!minimised) null
        else sections.firstOrNull { it.id == activeSectionId }
            ?.let { sec -> sec.copy(entries = sec.entries.filter { it.id !in devAboutIds }) }
    }
    LaunchedEffect(minimised) { if (!minimised) activeSectionId = null }
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
                                        text = openSection?.title ?: "Settings",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = if ((openSection?.title?.length ?: 0) > 18) 28.sp else 34.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.6).sp,
                                    )
                                    if (trimmedQuery.isNotEmpty()) {
                                        Text(
                                            text = stringResource(R.string.ui_found, totalMatchedCount),
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

                        if (showAccountCard && openSection == null) {
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
                                                    Toast.makeText(context, context.getString(R.string.toast_switched_feed_to, label), Toast.LENGTH_SHORT).show()
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
                        } else if (openSection != null) {
                            // Minimised layout: the grouped screen that was opened.
                            item(key = "sec_open_${openSection.id}") {
                                SettingsGroupBlock(
                                    section = openSection.copy(title = ""),
                                    useBackdropGlass = glassActive,
                                    haptics = haptics,
                                    onOpenPicker = { picker -> activePicker = picker },
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        } else if (minimised) {
                            // Minimised layout main screen: iShare / Dynamic Island / AOD as-is, then one card
                            // per group that opens its own screen, then News from dev + About.
                            minimisedMainSections.first.forEach { section ->
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
                            // One grouped card: every row opens its own screen (no spacing, no option counts).
                            val navEntries = minimisedMainSections.second.mapNotNull { section ->
                                val visibleEntries = section.entries.filter { it.id !in devAboutIds }
                                if (visibleEntries.isEmpty()) return@mapNotNull null
                                SettingsEntry.Action(
                                    id = "nav_${section.id}",
                                    title = section.title,
                                    subtitle = null,
                                    iconRes = visibleEntries.first().iconRes,
                                    keywords = emptyList(),
                                    onAction = {
                                        haptics.play(Haptic.Tap)
                                        activeSectionId = section.id
                                    },
                                )
                            }
                            if (navEntries.isNotEmpty()) {
                                item(key = "sec_minimised_nav") {
                                    SettingsGroupBlock(
                                        section = SettingsSection("minimised_nav", "", navEntries),
                                        useBackdropGlass = glassActive,
                                        haptics = haptics,
                                        onOpenPicker = { picker -> activePicker = picker },
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                }
                            }
                            if (devAboutEntries.isNotEmpty()) {
                                item(key = "sec_dev_about") {
                                    SettingsGroupBlock(
                                        section = SettingsSection("dev_about", "", devAboutEntries),
                                        useBackdropGlass = glassActive,
                                        haptics = haptics,
                                        onOpenPicker = { picker -> activePicker = picker },
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                }
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

                        if (trimmedQuery.isEmpty() && openSection == null) {
                            item(key = "settings_footer") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Image(
                                        painter = painterResource(R.drawable.ic_itune_waves),
                                        contentDescription = stringResource(R.string.ui_itune_icon),
                                        modifier = Modifier.size(38.dp),
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.ui_version, AppUpdateManager.installedVersion, AppUpdateManager.installedVersionCode),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        text = stringResource(R.string.ui_github_repository_open_source),
                                        color = Color(0xFF0A84FF),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.clickable {
                                            runCatching {
                                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://github.com/d0x-dev/itune")))
                                            }.onFailure {
                                                Toast.makeText(context, context.getString(R.string.toast_could_not_open_github), Toast.LENGTH_SHORT).show()
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
                        if (openSection != null) activeSectionId = null else onBack()
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
                                    text = openSection?.title ?: "Settings",
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
                        Box {
                            IconButton(
                                onClick = {
                                    haptics.play(Haptic.Tap)
                                    showAppearancePopup = true
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Sort,
                                    contentDescription = stringResource(R.string.ui_settings_appearance),
                                    tint = Color.White,
                                    modifier = Modifier.size(21.dp),
                                )
                            }
                            if (showAppearancePopup) {
                                SettingsAppearancePopup(
                                    minimised = state.settingsMinimised,
                                    onSelect = { min ->
                                        haptics.play(Haptic.Select)
                                        SettingsStore.setBoolean(context, SettingsStore.KEY_SETTINGS_MINIMISED, min)
                                        activeSectionId = null
                                        showAppearancePopup = false
                                        coroutineScope.launch { listState.scrollToItem(0) }
                                    },
                                    onDismiss = { showAppearancePopup = false },
                                )
                            }
                        }
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
                        onOpenSpotify = {
                            showProfileAccount = false
                            showSpotifyIntegration = true
                        },
                        onAddNewAccount = {
                            showProfileAccount = false
                            onAddNewAccount()
                        },
                        onSwitchAccount = { target, bounds ->
                            showProfileAccount = false
                            onSwitchAccount(target, bounds)
                        },
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
                    visible = showSpotifyIntegration,
                    enter = fadeIn(tween(200)) + slideInHorizontally(tween(260)) { it / 8 },
                    exit = fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 8 },
                ) {
                    SpotifyIntegrationScreen(
                        hasMiniPlayer = hasMiniPlayer,
                        onBack = { showSpotifyIntegration = false },
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
                Toast.makeText(context, context.getString(R.string.toast_profile_name_updated), Toast.LENGTH_SHORT).show()
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
            onOpenLicense = { showLicenseDialog = true },
            onDismiss = { showAboutDialog = false },
        )
    }

    if (showLicenseDialog) {
        SettingsLicenseDialog(onDismiss = { showLicenseDialog = false })
    }

    if (showAutoSleepTimerDialog) {
        AutoSleepTimerDialog(onDismiss = { showAutoSleepTimerDialog = false })
    }

    if (showProxySettingsDialog) {
        ProxySettingsDialog(onDismiss = { showProxySettingsDialog = false })
    }

    if (showCornerLengthDialog) {
        CoverCornerRadiusDialog(
            initialRadius = state.coverCornerRadius,
            haptics = haptics,
            onSave = { updatedRadius ->
                SettingsStore.setInt(context, SettingsStore.KEY_COVER_CORNER_RADIUS, updatedRadius)
                showCornerLengthDialog = false
            },
            onDismiss = { showCornerLengthDialog = false },
        )
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
            backupAccounts = scanned.backupAccounts,
            onDismiss = { scannedBackup = null; restoreUri = null; restorePassword = "" },
            onRestore = { selected, selectedAccountIds ->
                restoreBusy = true
                coroutineScope.launch {
                    val result = runCatching {
                        withContext(Dispatchers.IO) {
                            BackupRestoreManager.restore(context, scanned, selected, selectedAccountIds)
                        }
                        BackupRestoreManager.refreshRestoredState(context, selected, selectedAccountIds)
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
                        Toast.makeText(context, context.getString(R.string.toast_search_history_cleared), Toast.LENGTH_SHORT).show()
                    }
                    SettingsClearAction.LISTENING_HISTORY -> {
                        com.darkxvenom.itune.data.library.ListeningProfileStore.clear(context)
                        com.darkxvenom.itune.data.library.LibraryStore.clearRecentSongs(context)
                        Toast.makeText(context, context.getString(R.string.toast_listening_history_cleared), Toast.LENGTH_SHORT).show()
                    }
                    SettingsClearAction.CACHED_SONGS -> {
                        MusicCacheManager.clearAllCache()
                        Toast.makeText(context, context.getString(R.string.toast_cached_songs_cleared), Toast.LENGTH_SHORT).show()
                    }
                }
                clearAction = null
            },
        )
    }
}

/** "Settings Appearance" popup: Maximised (one long list) vs Minimised (grouped into separate screens). */
@Composable
private fun SettingsAppearancePopup(
    minimised: Boolean,
    onSelect: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    androidx.compose.ui.window.Popup(
        alignment = Alignment.TopEnd,
        offset = androidx.compose.ui.unit.IntOffset(0, 130),
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier
                .width(270.dp)
                .shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        0f to Color(0xE6505057),
                        1f to Color(0xE6242428),
                    ),
                )
                .border(
                    1.dp,
                    Brush.linearGradient(
                        0f to Color(0x99FFFFFF),
                        0.5f to Color(0x1AFFFFFF),
                        1f to Color(0x66FFFFFF),
                    ),
                    shape,
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.ui_settings_appearance),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            AppleSegmentedSwitch(
                options = listOf("Maximised", "Minimised"),
                selectedOption = if (minimised) "Minimised" else "Maximised",
                onSelect = { onSelect(it == "Minimised") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = if (minimised) {
                    "Groups settings into separate screens. News from dev and About stay on the main screen."
                } else {
                    "Shows every setting on one scrolling screen."
                },
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
            )
        }
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
                    text = stringResource(R.string.ui_accounts),
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
                contentDescription = stringResource(R.string.ui_choose_avatar),
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
                val index = entry.options.indexOf(entry.selectedOption)
                val next = entry.options[(index + 1) % entry.options.size]
                haptics.play(Haptic.Select)
                entry.onSelect(next)
            }
        }
    }

    val stackedSegments = entry is SettingsEntry.SegmentedToggle && entry.options.size > 2
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(animatedAlpha)
            .clickable(enabled = isEntryEnabled, onClick = rowClick),
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                colorFilter = if (entry.iconRes == R.drawable.ic_spotify) null else ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
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
                        contentDescription = stringResource(R.string.open_entry_options, entry.title),
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
                if (!stackedSegments) {
                    AppleSegmentedSwitch(
                        options = entry.options,
                        selectedOption = entry.selectedOption,
                        onSelect = { choice ->
                            haptics.play(Haptic.Select)
                            entry.onSelect(choice)
                        },
                    )
                }
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
    if (entry is SettingsEntry.SegmentedToggle && stackedSegments) {
        AppleSegmentedSwitch(
            options = entry.options,
            selectedOption = entry.selectedOption,
            onSelect = { choice ->
                haptics.play(Haptic.Select)
                entry.onSelect(choice)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 54.dp, end = 15.dp, bottom = 12.dp),
        )
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
// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

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
                            contentDescription = stringResource(R.string.ui_selected),
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
                text = stringResource(R.string.cancel),
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
            text = stringResource(R.string.ui_display_name),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = stringResource(R.string.ui_update_the_name_shown_on_your_apple_id_profile_card_in),
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
                    text = stringResource(R.string.ui_enter_your_name),
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
                text = stringResource(R.string.save),
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
                text = stringResource(R.string.cancel),
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
    onOpenLicense: () -> Unit,
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
            contentDescription = stringResource(R.string.ui_itune_icon),
            modifier = Modifier.size(54.dp),
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = stringResource(R.string.app_name),
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


        Spacer(Modifier.height(14.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.045f))
                .border(0.6.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenLicense() }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ui_license), color = MaterialTheme.colorScheme.onSurface, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.ui_gnu_gpl_v3_0_or_later_copyright_2026_itune_project), color = AppleTextSecondary, fontSize = 11.5.sp)
                }
                Text(stringResource(R.string.ui_view), color = AppleMusicRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://github.com/d0x-dev/iTune")))
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.ui_github_repository), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.open), color = AppleMusicRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.done),
                color = AppleMusicRed,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun CoverCornerRadiusDialog(
    initialRadius: Int,
    haptics: Haptics,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var radiusValue by remember { mutableFloatStateOf(initialRadius.toFloat().coerceIn(0f, 40f)) }
    val currentTrack = MusicPlayerState.currentTrack
    val currentArtUrl = currentTrack?.thumbnailUrl?.highQualityThumbnail() ?: currentTrack?.thumbnailUrl

    SettingsModalFrame(onDismiss = onDismiss) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.ui_corner_length),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.ui_adjust_the_corner_edge_of_square_album_artwork),
            color = AppleTextSecondary,
            fontSize = 12.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(18.dp))

        // Live Square Artwork Preview Container
        val currentDp = radiusValue.roundToInt().dp
        val previewShape = RoundedCornerShape(currentDp)
        Box(
            modifier = Modifier
                .size(150.dp)
                .shadow(
                    elevation = 14.dp,
                    shape = previewShape,
                    ambientColor = Color.Black,
                    spotColor = Color.Black,
                )
                .clip(previewShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF26262A), Color(0xFF141416))
                    )
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.22f),
                    shape = previewShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (!currentArtUrl.isNullOrBlank()) {
                AsyncImage(
                    model = currentArtUrl,
                    contentDescription = stringResource(R.string.ui_artwork_preview),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lib_albums),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.70f),
                        modifier = Modifier.size(54.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.ui_itune_music),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Value Indicator: e.g. "8 dp"
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "${radiusValue.roundToInt()}",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.ui_dp),
                color = AppleTextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

        // Seek Bar Slider
        Slider(
            value = radiusValue,
            onValueChange = { next ->
                val rounded = next.roundToInt().toFloat()
                if (rounded != radiusValue) {
                    radiusValue = rounded
                    haptics.play(Haptic.Tap)
                }
            },
            valueRange = 0f..40f,
            steps = 39,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = AppleMusicRed,
                inactiveTrackColor = Color.White.copy(alpha = 0.18f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )

        // Preset Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                0 to "0 (Sharp)",
                8 to "8 (Default)",
                18 to "18 (Medium)",
                28 to "28 (Round)",
            ).forEach { (preset, label) ->
                val isSelected = radiusValue.roundToInt() == preset
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) AppleMusicRed.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f))
                        .border(
                            0.75.dp,
                            if (isSelected) AppleMusicRed else Color.White.copy(alpha = 0.12f),
                            RoundedCornerShape(8.dp),
                        )
                        .clickable {
                            haptics.play(Haptic.Select)
                            radiusValue = preset.toFloat()
                        }
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) AppleMusicRed else AppleTextSecondary,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

        // Done button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable {
                    haptics.play(Haptic.Select)
                    onSave(radiusValue.roundToInt())
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.done),
                color = AppleMusicRed,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

        // Cancel button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.cancel),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
                fontSize = 17.sp,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}


@Composable
private fun SettingsLicenseDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    SettingsModalFrame(onDismiss = onDismiss) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.ui_itune_license),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.ui_gnu_gpl_v3_0_or_later),
            color = AppleTextSecondary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.ui_copyright_2026_itune_project_this_software_is_provided),
            color = AppleTextSecondary,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black)
                .border(0.6.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
        ) {
            Text(
                text = """GNU GENERAL PUBLIC LICENSE
Version 3, 29 June 2007

Copyright © 2007 Free Software Foundation, Inc. <https://fsf.org/>

Everyone is permitted to copy and distribute verbatim copies of this license document, but changing it is not allowed.

Preamble

The GNU General Public License is a free, copyleft license for software and other kinds of works.

The licenses for most software and other practical works are designed to take away your freedom to share and change the works. By contrast, the GNU General Public License is intended to guarantee your freedom to share and change all versions of a program--to make sure it remains free software for all its users. We, the Free Software Foundation, use the GNU General Public License for most of our software; it applies also to any other work released this way by its authors. You can apply it to your programs, too.

When we speak of free software, we are referring to freedom, not price. Our General Public Licenses are designed to make sure that you have the freedom to distribute copies of free software (and charge for them if you wish), that you receive source code or can get it if you want it, that you can change the software or use pieces of it in new free programs, and that you know you can do these things.

To protect your rights, we need to prevent others from denying you these rights or asking you to surrender the rights. Therefore, you have certain responsibilities if you distribute copies of the software, or if you modify it: responsibilities to respect the freedom of others.

For example, if you distribute copies of such a program, whether gratis or for a fee, you must pass on to the recipients the same freedoms that you received. You must make sure that they, too, receive or can get the source code. And you must show them these terms so they know their rights.

Developers that use the GNU GPL protect your rights with two steps: (1) assert copyright on the software, and (2) offer you this License giving you legal permission to copy, distribute and/or modify it.

For the developers' and authors' protection, the GPL clearly explains that there is no warranty for this free software. For both users' and authors' sake, the GPL requires that modified versions be marked as changed, so that their problems will not be attributed erroneously to authors of previous versions.

Some devices are designed to deny users access to install or run modified versions of the software inside them, although the manufacturer can do so. This is fundamentally incompatible with the aim of protecting users' freedom to change the software. The systematic pattern of such abuse occurs in the area of products for individuals to use, which is precisely where it is most unacceptable. Therefore, we have designed this version of the GPL to prohibit the practice for those products. If such problems arise substantially in other domains, we stand ready to extend this provision to those domains in future versions of the GPL, as needed to protect the freedom of users.

Finally, every program is threatened constantly by software patents. States should not allow patents to restrict development and use of software on general-purpose computers, but in those that do, we wish to avoid the special danger that patents applied to a free program could make it effectively proprietary. To prevent this, the GPL assures that patents cannot be used to render the program non-free.

The precise terms and conditions for copying, distribution and modification follow.

TERMS AND CONDITIONS

0. Definitions.

“This License” refers to version 3 of the GNU General Public License.

“Copyright” also means copyright-like laws that apply to other kinds of works, such as semiconductor masks.

“The Program” refers to any copyrightable work licensed under this License. Each licensee is addressed as “you”. “Licensees” and “recipients” may be individuals or organizations.

To “modify” a work means to copy from or adapt all or part of the work in a fashion requiring copyright permission, other than the making of an exact copy. The resulting work is called a “modified version” of the earlier work or a work “based on” the earlier work.

A “covered work” means either the unmodified Program or a work based on the Program.

To “propagate” a work means to do anything with it that, without permission, would make you directly or secondarily liable for infringement under applicable copyright law, except executing it on a computer or modifying a private copy. Propagation includes copying, distribution (with or without modification), making available to the public, and in some countries other activities as well.

To “convey” a work means any kind of propagation that enables other parties to make or receive copies. Mere interaction with a user through a computer network, with no transfer of a copy, is not conveying.

An interactive user interface displays “Appropriate Legal Notices” to the extent that it includes a convenient and prominently visible feature that (1) displays an appropriate copyright notice, and (2) tells the user that there is no warranty for the work (except to the extent that warranties are provided), that licensees may convey the work under this License, and how to view a copy of this License. If the interface presents a list of user commands or options, such as a menu, a prominent item in the list meets this criterion.

1. Source Code.
The “source code” for a work means the preferred form of the work for making modifications to it. “Object code” means any non-source form of a work.

A “Standard Interface” means an interface that either is an official standard defined by a recognized standards body, or, in the case of interfaces specified for a particular programming language, one that is widely used among developers working in that language.

The “System Libraries” of an executable work include anything, other than the work as a whole, that (a) is included in the normal form of packaging a Major Component, but which is not part of that Major Component, and (b) serves only to enable use of the work with that Major Component, or to implement a Standard Interface for which an implementation is available to the public in source code form. A “Major Component”, in this context, means a major essential component (kernel, window system, and so on) of the specific operating system (if any) on which the executable work runs, or a compiler used to produce the work, or an object code interpreter used to run it.

The “Corresponding Source” for a work in object code form means all the source code needed to generate, install, and (for an executable work) run the object code and to modify the work, including scripts to control those activities. However, it does not include the work's System Libraries, or general-purpose tools or generally available free programs which are used unmodified in performing those activities but which are not part of the work. For example, Corresponding Source includes interface definition files associated with source files for the work, and the source code for shared libraries and dynamically linked subprograms that the work is specifically designed to require, such as by intimate data communication or control flow between those subprograms and other parts of the work.

The Corresponding Source need not include anything that users can regenerate automatically from other parts of the Corresponding Source.

The Corresponding Source for a work in source code form is that same work.

2. Basic Permissions.
All rights granted under this License are granted for the term of copyright on the Program, and are irrevocable provided the stated conditions are met. This License explicitly affirms your unlimited permission to run the unmodified Program. The output from running a covered work is covered by this License only if the output, given its content, constitutes a covered work. This License acknowledges your rights of fair use or other equivalent, as provided by copyright law.

You may make, run and propagate covered works that you do not convey, without conditions so long as your license otherwise remains in force. You may convey covered works to others for the sole purpose of having them make modifications exclusively for you, or provide you with facilities for running those works, provided that you comply with the terms of this License in conveying all material for which you do not control copyright. Those thus making or running the covered works for you must do so exclusively on your behalf, under your direction and control, on terms that prohibit them from making any copies of your copyrighted material outside their relationship with you.

Conveying under any other circumstances is permitted solely under the conditions stated below. Sublicensing is not allowed; section 10 makes it unnecessary.

3. Protecting Users' Legal Rights From Anti-Circumvention Law.
No covered work shall be deemed part of an effective technological measure under any applicable law fulfilling obligations under article 11 of the WIPO copyright treaty adopted on 20 December 1996, or similar laws prohibiting or restricting circumvention of such measures.

When you convey a covered work, you waive any legal power to forbid circumvention of technological measures to the extent such circumvention is effected by exercising rights under this License with respect to the covered work, and you disclaim any intention to limit operation or modification of the work as a means of enforcing, against the work's users, your or third parties' legal rights to forbid circumvention of technological measures.

4. Conveying Verbatim Copies.
You may convey verbatim copies of the Program's source code as you receive it, in any medium, provided that you conspicuously and appropriately publish on each copy an appropriate copyright notice; keep intact all notices stating that this License and any non-permissive terms added in accord with section 7 apply to the code; keep intact all notices of the absence of any warranty; and give all recipients a copy of this License along with the Program.

You may charge any price or no price for each copy that you convey, and you may offer support or warranty protection for a fee.

5. Conveying Modified Source Versions.
You may convey a work based on the Program, or the modifications to produce it from the Program, in the form of source code under the terms of section 4, provided that you also meet all of these conditions:

     a) The work must carry prominent notices stating that you modified it, and giving a relevant date.

     b) The work must carry prominent notices stating that it is released under this License and any conditions added under section 7. This requirement modifies the requirement in section 4 to “keep intact all notices”.

     c) You must license the entire work, as a whole, under this License to anyone who comes into possession of a copy. This License will therefore apply, along with any applicable section 7 additional terms, to the whole of the work, and all its parts, regardless of how they are packaged. This License gives no permission to license the work in any other way, but it does not invalidate such permission if you have separately received it.

     d) If the work has interactive user interfaces, each must display Appropriate Legal Notices; however, if the Program has interactive interfaces that do not display Appropriate Legal Notices, your work need not make them do so.

A compilation of a covered work with other separate and independent works, which are not by their nature extensions of the covered work, and which are not combined with it such as to form a larger program, in or on a volume of a storage or distribution medium, is called an “aggregate” if the compilation and its resulting copyright are not used to limit the access or legal rights of the compilation's users beyond what the individual works permit. Inclusion of a covered work in an aggregate does not cause this License to apply to the other parts of the aggregate.

6. Conveying Non-Source Forms.
You may convey a covered work in object code form under the terms of sections 4 and 5, provided that you also convey the machine-readable Corresponding Source under the terms of this License, in one of these ways:

     a) Convey the object code in, or embodied in, a physical product (including a physical distribution medium), accompanied by the Corresponding Source fixed on a durable physical medium customarily used for software interchange.

     b) Convey the object code in, or embodied in, a physical product (including a physical distribution medium), accompanied by a written offer, valid for at least three years and valid for as long as you offer spare parts or customer support for that product model, to give anyone who possesses the object code either (1) a copy of the Corresponding Source for all the software in the product that is covered by this License, on a durable physical medium customarily used for software interchange, for a price no more than your reasonable cost of physically performing this conveying of source, or (2) access to copy the Corresponding Source from a network server at no charge.

     c) Convey individual copies of the object code with a copy of the written offer to provide the Corresponding Source. This alternative is allowed only occasionally and noncommercially, and only if you received the object code with such an offer, in accord with subsection 6b.

     d) Convey the object code by offering access from a designated place (gratis or for a charge), and offer equivalent access to the Corresponding Source in the same way through the same place at no further charge. You need not require recipients to copy the Corresponding Source along with the object code. If the place to copy the object code is a network server, the Corresponding Source may be on a different server (operated by you or a third party) that supports equivalent copying facilities, provided you maintain clear directions next to the object code saying where to find the Corresponding Source. Regardless of what server hosts the Corresponding Source, you remain obligated to ensure that it is available for as long as needed to satisfy these requirements.

     e) Convey the object code using peer-to-peer transmission, provided you inform other peers where the object code and Corresponding Source of the work are being offered to the general public at no charge under subsection 6d.

A separable portion of the object code, whose source code is excluded from the Corresponding Source as a System Library, need not be included in conveying the object code work.

A “User Product” is either (1) a “consumer product”, which means any tangible personal property which is normally used for personal, family, or household purposes, or (2) anything designed or sold for incorporation into a dwelling. In determining whether a product is a consumer product, doubtful cases shall be resolved in favor of coverage. For a particular product received by a particular user, “normally used” refers to a typical or common use of that class of product, regardless of the status of the particular user or of the way in which the particular user actually uses, or expects or is expected to use, the product. A product is a consumer product regardless of whether the product has substantial commercial, industrial or non-consumer uses, unless such uses represent the only significant mode of use of the product.

“Installation Information” for a User Product means any methods, procedures, authorization keys, or other information required to install and execute modified versions of a covered work in that User Product from a modified version of its Corresponding Source. The information must suffice to ensure that the continued functioning of the modified object code is in no case prevented or interfered with solely because modification has been made.

If you convey an object code work under this section in, or with, or specifically for use in, a User Product, and the conveying occurs as part of a transaction in which the right of possession and use of the User Product is transferred to the recipient in perpetuity or for a fixed term (regardless of how the transaction is characterized), the Corresponding Source conveyed under this section must be accompanied by the Installation Information. But this requirement does not apply if neither you nor any third party retains the ability to install modified object code on the User Product (for example, the work has been installed in ROM).

The requirement to provide Installation Information does not include a requirement to continue to provide support service, warranty, or updates for a work that has been modified or installed by the recipient, or for the User Product in which it has been modified or installed. Access to a network may be denied when the modification itself materially and adversely affects the operation of the network or violates the rules and protocols for communication across the network.

Corresponding Source conveyed, and Installation Information provided, in accord with this section must be in a format that is publicly documented (and with an implementation available to the public in source code form), and must require no special password or key for unpacking, reading or copying.

7. Additional Terms.
“Additional permissions” are terms that supplement the terms of this License by making exceptions from one or more of its conditions. Additional permissions that are applicable to the entire Program shall be treated as though they were included in this License, to the extent that they are valid under applicable law. If additional permissions apply only to part of the Program, that part may be used separately under those permissions, but the entire Program remains governed by this License without regard to the additional permissions.

When you convey a copy of a covered work, you may at your option remove any additional permissions from that copy, or from any part of it. (Additional permissions may be written to require their own removal in certain cases when you modify the work.) You may place additional permissions on material, added by you to a covered work, for which you have or can give appropriate copyright permission.

Notwithstanding any other provision of this License, for material you add to a covered work, you may (if authorized by the copyright holders of that material) supplement the terms of this License with terms:

     a) Disclaiming warranty or limiting liability differently from the terms of sections 15 and 16 of this License; or

     b) Requiring preservation of specified reasonable legal notices or author attributions in that material or in the Appropriate Legal Notices displayed by works containing it; or

     c) Prohibiting misrepresentation of the origin of that material, or requiring that modified versions of such material be marked in reasonable ways as different from the original version; or

     d) Limiting the use for publicity purposes of names of licensors or authors of the material; or

     e) Declining to grant rights under trademark law for use of some trade names, trademarks, or service marks; or

     f) Requiring indemnification of licensors and authors of that material by anyone who conveys the material (or modified versions of it) with contractual assumptions of liability to the recipient, for any liability that these contractual assumptions directly impose on those licensors and authors.

All other non-permissive additional terms are considered “further restrictions” within the meaning of section 10. If the Program as you received it, or any part of it, contains a notice stating that it is governed by this License along with a term that is a further restriction, you may remove that term. If a license document contains a further restriction but permits relicensing or conveying under this License, you may add to a covered work material governed by the terms of that license document, provided that the further restriction does not survive such relicensing or conveying.

If you add terms to a covered work in accord with this section, you must place, in the relevant source files, a statement of the additional terms that apply to those files, or a notice indicating where to find the applicable terms.

Additional terms, permissive or non-permissive, may be stated in the form of a separately written license, or stated as exceptions; the above requirements apply either way.

8. Termination.
You may not propagate or modify a covered work except as expressly provided under this License. Any attempt otherwise to propagate or modify it is void, and will automatically terminate your rights under this License (including any patent licenses granted under the third paragraph of section 11).

However, if you cease all violation of this License, then your license from a particular copyright holder is reinstated (a) provisionally, unless and until the copyright holder explicitly and finally terminates your license, and (b) permanently, if the copyright holder fails to notify you of the violation by some reasonable means prior to 60 days after the cessation.

Moreover, your license from a particular copyright holder is reinstated permanently if the copyright holder notifies you of the violation by some reasonable means, this is the first time you have received notice of violation of this License (for any work) from that copyright holder, and you cure the violation prior to 30 days after your receipt of the notice.

Termination of your rights under this section does not terminate the licenses of parties who have received copies or rights from you under this License. If your rights have been terminated and not permanently reinstated, you do not qualify to receive new licenses for the same material under section 10.

9. Acceptance Not Required for Having Copies.
You are not required to accept this License in order to receive or run a copy of the Program. Ancillary propagation of a covered work occurring solely as a consequence of using peer-to-peer transmission to receive a copy likewise does not require acceptance. However, nothing other than this License grants you permission to propagate or modify any covered work. These actions infringe copyright if you do not accept this License. Therefore, by modifying or propagating a covered work, you indicate your acceptance of this License to do so.

10. Automatic Licensing of Downstream Recipients.
Each time you convey a covered work, the recipient automatically receives a license from the original licensors, to run, modify and propagate that work, subject to this License. You are not responsible for enforcing compliance by third parties with this License.

An “entity transaction” is a transaction transferring control of an organization, or substantially all assets of one, or subdividing an organization, or merging organizations. If propagation of a covered work results from an entity transaction, each party to that transaction who receives a copy of the work also receives whatever licenses to the work the party's predecessor in interest had or could give under the previous paragraph, plus a right to possession of the Corresponding Source of the work from the predecessor in interest, if the predecessor has it or can get it with reasonable efforts.

You may not impose any further restrictions on the exercise of the rights granted or affirmed under this License. For example, you may not impose a license fee, royalty, or other charge for exercise of rights granted under this License, and you may not initiate litigation (including a cross-claim or counterclaim in a lawsuit) alleging that any patent claim is infringed by making, using, selling, offering for sale, or importing the Program or any portion of it.

11. Patents.
A “contributor” is a copyright holder who authorizes use under this License of the Program or a work on which the Program is based. The work thus licensed is called the contributor's “contributor version”.

A contributor's “essential patent claims” are all patent claims owned or controlled by the contributor, whether already acquired or hereafter acquired, that would be infringed by some manner, permitted by this License, of making, using, or selling its contributor version, but do not include claims that would be infringed only as a consequence of further modification of the contributor version. For purposes of this definition, “control” includes the right to grant patent sublicenses in a manner consistent with the requirements of this License.

Each contributor grants you a non-exclusive, worldwide, royalty-free patent license under the contributor's essential patent claims, to make, use, sell, offer for sale, import and otherwise run, modify and propagate the contents of its contributor version.

In the following three paragraphs, a “patent license” is any express agreement or commitment, however denominated, not to enforce a patent (such as an express permission to practice a patent or covenant not to sue for patent infringement). To “grant” such a patent license to a party means to make such an agreement or commitment not to enforce a patent against the party.

If you convey a covered work, knowingly relying on a patent license, and the Corresponding Source of the work is not available for anyone to copy, free of charge and under the terms of this License, through a publicly available network server or other readily accessible means, then you must either (1) cause the Corresponding Source to be so available, or (2) arrange to deprive yourself of the benefit of the patent license for this particular work, or (3) arrange, in a manner consistent with the requirements of this License, to extend the patent license to downstream recipients. “Knowingly relying” means you have actual knowledge that, but for the patent license, your conveying the covered work in a country, or your recipient's use of the covered work in a country, would infringe one or more identifiable patents in that country that you have reason to believe are valid.

If, pursuant to or in connection with a single transaction or arrangement, you convey, or propagate by procuring conveyance of, a covered work, and grant a patent license to some of the parties receiving the covered work authorizing them to use, propagate, modify or convey a specific copy of the covered work, then the patent license you grant is automatically extended to all recipients of the covered work and works based on it.

A patent license is “discriminatory” if it does not include within the scope of its coverage, prohibits the exercise of, or is conditioned on the non-exercise of one or more of the rights that are specifically granted under this License. You may not convey a covered work if you are a party to an arrangement with a third party that is in the business of distributing software, under which you make payment to the third party based on the extent of your activity of conveying the work, and under which the third party grants, to any of the parties who would receive the covered work from you, a discriminatory patent license (a) in connection with copies of the covered work conveyed by you (or copies made from those copies), or (b) primarily for and in connection with specific products or compilations that contain the covered work, unless you entered into that arrangement, or that patent license was granted, prior to 28 March 2007.

Nothing in this License shall be construed as excluding or limiting any implied license or other defenses to infringement that may otherwise be available to you under applicable patent law.

12. No Surrender of Others' Freedom.
If conditions are imposed on you (whether by court order, agreement or otherwise) that contradict the conditions of this License, they do not excuse you from the conditions of this License. If you cannot convey a covered work so as to satisfy simultaneously your obligations under this License and any other pertinent obligations, then as a consequence you may not convey it at all. For example, if you agree to terms that obligate you to collect a royalty for further conveying from those to whom you convey the Program, the only way you could satisfy both those terms and this License would be to refrain entirely from conveying the Program.

13. Use with the GNU Affero General Public License.
Notwithstanding any other provision of this License, you have permission to link or combine any covered work with a work licensed under version 3 of the GNU Affero General Public License into a single combined work, and to convey the resulting work. The terms of this License will continue to apply to the part which is the covered work, but the special requirements of the GNU Affero General Public License, section 13, concerning interaction through a network will apply to the combination as such.

14. Revised Versions of this License.
The Free Software Foundation may publish revised and/or new versions of the GNU General Public License from time to time. Such new versions will be similar in spirit to the present version, but may differ in detail to address new problems or concerns.

Each version is given a distinguishing version number. If the Program specifies that a certain numbered version of the GNU General Public License “or any later version” applies to it, you have the option of following the terms and conditions either of that numbered version or of any later version published by the Free Software Foundation. If the Program does not specify a version number of the GNU General Public License, you may choose any version ever published by the Free Software Foundation.

If the Program specifies that a proxy can decide which future versions of the GNU General Public License can be used, that proxy's public statement of acceptance of a version permanently authorizes you to choose that version for the Program.

Later license versions may give you additional or different permissions. However, no additional obligations are imposed on any author or copyright holder as a result of your choosing to follow a later version.

15. Disclaimer of Warranty.
THERE IS NO WARRANTY FOR THE PROGRAM, TO THE EXTENT PERMITTED BY APPLICABLE LAW. EXCEPT WHEN OTHERWISE STATED IN WRITING THE COPYRIGHT HOLDERS AND/OR OTHER PARTIES PROVIDE THE PROGRAM “AS IS” WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE. THE ENTIRE RISK AS TO THE QUALITY AND PERFORMANCE OF THE PROGRAM IS WITH YOU. SHOULD THE PROGRAM PROVE DEFECTIVE, YOU ASSUME THE COST OF ALL NECESSARY SERVICING, REPAIR OR CORRECTION.

16. Limitation of Liability.
IN NO EVENT UNLESS REQUIRED BY APPLICABLE LAW OR AGREED TO IN WRITING WILL ANY COPYRIGHT HOLDER, OR ANY OTHER PARTY WHO MODIFIES AND/OR CONVEYS THE PROGRAM AS PERMITTED ABOVE, BE LIABLE TO YOU FOR DAMAGES, INCLUDING ANY GENERAL, SPECIAL, INCIDENTAL OR CONSEQUENTIAL DAMAGES ARISING OUT OF THE USE OR INABILITY TO USE THE PROGRAM (INCLUDING BUT NOT LIMITED TO LOSS OF DATA OR DATA BEING RENDERED INACCURATE OR LOSSES SUSTAINED BY YOU OR THIRD PARTIES OR A FAILURE OF THE PROGRAM TO OPERATE WITH ANY OTHER PROGRAMS), EVEN IF SUCH HOLDER OR OTHER PARTY HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.

17. Interpretation of Sections 15 and 16.
If the disclaimer of warranty and limitation of liability provided above cannot be given local legal effect according to their terms, reviewing courts shall apply local law that most closely approximates an absolute waiver of all civil liability in connection with the Program, unless a warranty or assumption of liability accompanies a copy of the Program in return for a fee.

END OF TERMS AND CONDITIONS

How to Apply These Terms to Your New Programs

If you develop a new program, and you want it to be of the greatest possible use to the public, the best way to achieve this is to make it free software which everyone can redistribute and change under these terms.

To do so, attach the following notices to the program. It is safest to attach them to the start of each source file to most effectively state the exclusion of warranty; and each file should have at least the “copyright” line and a pointer to where the full notice is found.

     <one line to give the program's name and a brief idea of what it does.>
     Copyright (C) <year>  <name of author>

     This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

     This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.

     You should have received a copy of the GNU General Public License along with this program.  If not, see <https://www.gnu.org/licenses/>.

Also add information on how to contact you by electronic and paper mail.

If the program does terminal interaction, make it output a short notice like this when it starts in an interactive mode:

     <program>  Copyright (C) <year>  <name of author>
     This program comes with ABSOLUTELY NO WARRANTY; for details type `show w'.
     This is free software, and you are welcome to redistribute it under certain conditions; type `show c' for details.

The hypothetical commands `show w' and `show c' should show the appropriate parts of the General Public License. Of course, your program's commands might be different; for a GUI interface, you would use an “about box”.

You should also get your employer (if you work as a programmer) or school, if any, to sign a “copyright disclaimer” for the program, if necessary. For more information on this, and how to apply and follow the GNU GPL, see <https://www.gnu.org/licenses/>.

The GNU General Public License does not permit incorporating your program into proprietary programs. If your program is a subroutine library, you may consider it more useful to permit linking proprietary applications with the library. If this is what you want to do, use the GNU Lesser General Public License instead of this License. But first, please read <https://www.gnu.org/philosophy/why-not-lgpl.html>.
""",
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/d0x-dev/iTune"),
                    )
                    context.startActivity(intent)
                }
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.ui_github_repository), color = Color(0xFF64AFFF), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
        Box(
            modifier = Modifier.fillMaxWidth().height(48.dp).clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.done), color = AppleMusicRed, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
    context: Context,
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
    onOpenSources: () -> Unit,
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
    onOpenSpotifyIntegration: () -> Unit,
    onOpenCornerLength: () -> Unit,
): List<SettingsSection> {
    val effectsDisabled = audioEngine.bypassAllEffects || (audioEngine.bitPerfectEnabled && bitPerfectActive)
    return listOf(
        SettingsSection(
            id = "ishare_together",
            title = context.getString(R.string.settings_ishare_dynamic_island_standby),
            entries = listOf(
                SettingsEntry.Action(
                    id = "ishare",
                    title = context.getString(R.string.settings_ishare),
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
                    title = context.getString(R.string.settings_dynamic_island),
                    subtitle = context.getString(R.string.settings_floating_water_bubble_controller_outside_itune),
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
                    title = context.getString(R.string.settings_always_on_display),
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
            title = context.getString(R.string.settings_audio_engine_sound_effects),
            entries = listOf(
                SettingsEntry.EffectToggleAction(
                    id = "engine_dolby_atmos",
                    title = context.getString(R.string.settings_dolby_atmos),
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
                    title = context.getString(R.string.settings_text_8948f8179a),
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
                    title = context.getString(R.string.settings_spatial_audio),
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
                    title = context.getString(R.string.settings_equalizer_eq),
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
                    title = context.getString(R.string.settings_low_pass_filtering),
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
                    title = context.getString(R.string.settings_dj_studio_panel),
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
                    title = context.getString(R.string.settings_audio_normalization),
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
                    title = context.getString(R.string.settings_usb_bit_perfect_dac),
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
                    title = context.getString(R.string.settings_pure_audio_bypass),
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
                    title = context.getString(R.string.settings_signal_pipeline),
                    subtitle = context.getString(R.string.settings_audioengine_pillbadge_text_activeoutputname),
                    iconRes = R.drawable.ic_output_pipeline,
                    keywords = listOf("pipeline", "signal", "chain", "dsp", "sample rate", "output"),
                    trailingValue = if (effectsDisabled) "Bypassed" else "Inspect",
                    onAction = { onOpenAudioEffect(EnginePopupPage.PIPELINE) },
                ),
            ),
        ),
        SettingsSection(
            id = "audio_playback",
            title = context.getString(R.string.settings_audio_playback),
            entries = listOf(
                SettingsEntry.Action(
                    id = "music_sources",
                    title = context.getString(R.string.settings_sources),
                    subtitle = context.getString(R.string.settings_manage_youtube_music_jiosaavn_installable_extens),
                    iconRes = R.drawable.ic_settings_streaming_quality,
                    keywords = listOf("sources", "addons", "extensions", "jiosaavn", "youtube music", "priority"),
                    trailingValue = "Manage",
                    onAction = onOpenSources,
                ),
                SettingsEntry.Toggle(
                    id = "auto_skip_on_error",
                    title = context.getString(R.string.settings_auto_skip_when_error_occurs),
                    subtitle = context.getString(R.string.settings_move_to_the_next_queued_song_if_playback_fails),
                    iconRes = R.drawable.skip_next,
                    keywords = listOf("auto skip", "skip", "error", "failed playback", "next track"),
                    checked = state.autoSkipOnError,
                    onToggle = { onToggleBool(SettingsStore.KEY_AUTO_SKIP_ON_ERROR, it) },
                ),
                SettingsEntry.Picker(
                    id = "download_quality",
                    title = context.getString(R.string.settings_download_quality),
                    subtitle = context.getString(R.string.settings_default_quality_used_by_song_download_actions),
                    iconRes = R.drawable.ic_settings_streaming_quality,
                    keywords = listOf("download", "quality", "audio", "maximum", "standard", "data saver"),
                    currentValue = state.downloadQuality,
                    description = context.getString(R.string.settings_choose_the_default_audio_quality_for_downloads_a),
                    options = listOf(
                        SettingsOptionChoice(context.getString(R.string.settings_best_available), context.getString(R.string.settings_use_the_highest_available_source_quality)),
                        SettingsOptionChoice(context.getString(R.string.settings_maximum), context.getString(R.string.settings_choose_the_highest_bitrate_stream)),
                        SettingsOptionChoice(context.getString(R.string.settings_high), context.getString(R.string.settings_target_about_160_kbps)),
                        SettingsOptionChoice(context.getString(R.string.settings_standard), context.getString(R.string.settings_target_about_128_kbps)),
                        SettingsOptionChoice(context.getString(R.string.settings_data_saver), context.getString(R.string.settings_target_about_64_kbps)),
                    ),
                    onSelect = { onSelectString(SettingsStore.KEY_DOWNLOAD_QUALITY, it) },
                ),
                SettingsEntry.Toggle(
                    id = "lossless_audio",
                    title = context.getString(R.string.settings_lossless_audio),
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
                SettingsEntry.Toggle(
                    id = "upgrade_cached_songs",
                    title = context.getString(R.string.settings_upgrade_quality_of_cached_songs),
                    subtitle = if (state.upgradeCachedSongs) {
                        if (state.jioSaavnProvider) "Cached YouTube songs are replaced with JioSaavn quality when played"
                        else "On · needs JioSaavn Provider to take effect"
                    } else {
                        "Off · Cached songs play as they are"
                    },
                    iconRes = R.drawable.ic_settings_lossless,
                    keywords = listOf("cache", "cached", "upgrade", "jiosaavn", "replace", "quality"),
                    checked = state.upgradeCachedSongs,
                    onToggle = { onToggleBool(SettingsStore.KEY_UPGRADE_CACHED, it) },
                ),
                SettingsEntry.Picker(
                    id = "audio_quality",
                    title = context.getString(R.string.settings_streaming_quality),
                    subtitle = if (state.losslessAudio) {
                        "Locked to Maximum Quality (Lossless Active)"
                    } else {
                        "Select audio quality (Extreme, High, Medium, Low)"
                    },
                    iconRes = R.drawable.ic_settings_streaming_quality,
                    keywords = listOf("bitrate", "quality", "audio", "extreme", "high", "medium", "low", "stream"),
                    currentValue = if (state.losslessAudio) "Lossless (Max)" else state.audioQuality,
                    description = context.getString(R.string.settings_select_your_preferred_streaming_audio_quality),
                    enabled = !state.losslessAudio,
                    options = listOf(
                        SettingsOptionChoice(context.getString(R.string.settings_extreme), context.getString(R.string.settings_maximum_fidelity_256_kbps)),
                        SettingsOptionChoice(context.getString(R.string.settings_high_b1a5954a), context.getString(R.string.settings_high_quality_audio_160_kbps)),
                        SettingsOptionChoice(context.getString(R.string.settings_medium), context.getString(R.string.settings_standard_audio_quality_128_kbps)),
                        SettingsOptionChoice(context.getString(R.string.settings_low), context.getString(R.string.settings_data_saver_audio_48_64_kbps)),
                    ),
                    onSelect = { onSelectString(SettingsStore.KEY_AUDIO_QUALITY, it) },
                ),
                SettingsEntry.Picker(
                    id = "pcm_output_format",
                    title = context.getString(R.string.settings_pcm_output_format),
                    subtitle = context.getString(R.string.settings_rebuilds_playback_at_the_current_position),
                    iconRes = R.drawable.ic_settings_streaming_quality,
                    keywords = listOf("pcm", "16 bit", "32 bit", "float", "uac", "usb dac", "output format"),
                    currentValue = state.pcmOutputFormat,
                    description = context.getString(R.string.settings_choose_the_pcm_format_requested_from_android_s_a),
                    options = listOf(
                        SettingsOptionChoice(context.getString(R.string.settings_text_bc7f1ee919), context.getString(R.string.settings_standard_integer_pcm_output_broadly_compatible_w)),
                        SettingsOptionChoice(context.getString(R.string.settings_text_6697202e38), context.getString(R.string.settings_float_pcm_output_when_supported_by_android_and_t)),
                    ),
                    onSelect = { onSelectString(SettingsStore.KEY_PCM_OUTPUT_FORMAT, it) },
                ),
            ).let { baseEntries ->
                if (!state.autoMix) {
                    baseEntries + listOf(
                        SettingsEntry.Picker(
                            id = "crossfade",
                            title = context.getString(R.string.settings_crossfade),
                            subtitle = if (state.afterglowDuration == "Off") "Smooth transition between songs" else "Unavailable while Afterglow is active",
                            iconRes = R.drawable.ic_settings_crossfade,
                            keywords = listOf("gapless", "fade", "blend", "mix", "dj", "transition"),
                            currentValue = state.crossfadeDuration,
                            description = context.getString(R.string.settings_overlap_the_fade_out_of_the_ending_track_with_th),
                            enabled = state.afterglowDuration == "Off",
                            options = listOf(
                                SettingsOptionChoice(context.getString(R.string.settings_off), context.getString(R.string.settings_play_tracks_cleanly_back_to_back)),
                                SettingsOptionChoice(context.getString(R.string.settings_text_6597740315), context.getString(R.string.settings_subtle_transition_overlap)),
                                SettingsOptionChoice(context.getString(R.string.settings_text_7bd0ce5454), context.getString(R.string.settings_short_seamless_fade)),
                                SettingsOptionChoice(context.getString(R.string.settings_text_56628a6fa5), context.getString(R.string.settings_balanced_dj_style_transition)),
                                SettingsOptionChoice(context.getString(R.string.settings_text_81e9ba46e0), context.getString(R.string.settings_smooth_extended_blend)),
                                SettingsOptionChoice(context.getString(R.string.settings_text_e0529daa1e), context.getString(R.string.settings_maximum_continuous_mix_blend)),
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
                    title = context.getString(R.string.settings_automix),
                    subtitle = if (state.afterglowDuration == "Off") "Smart blends that adapt to every song" else "Unavailable while Afterglow is active",
                    iconRes = R.drawable.ic_settings_automix,
                    keywords = listOf("automix", "auto mix", "smart", "dj", "blend", "transition", "crossfade", "level", "loudness"),
                    checked = state.autoMix,
                    enabled = state.afterglowDuration == "Off",
                    onToggle = { onToggleBool(SettingsStore.KEY_AUTOMIX, it) },
                ),
                SettingsEntry.Picker(
                    id = "afterglow",
                    title = context.getString(R.string.settings_afterglow),
                    subtitle = if (state.afterglowDuration == "Off") "Apply the Low-Pass effect at each song’s end" else "Low-Pass for the final ${state.afterglowDuration.removeSuffix(" sec")} seconds",
                    iconRes = R.drawable.ic_fx_low_pass,
                    keywords = listOf("afterglow", "ending", "outro", "low pass", "final seconds", "song end"),
                    currentValue = state.afterglowDuration,
                    description = context.getString(R.string.settings_apply_the_same_low_pass_equalizer_effect_during),
                    enabled = true,
                    options = listOf(
                        SettingsOptionChoice(context.getString(R.string.settings_off_e3de5ab0), context.getString(R.string.settings_keep_normal_playback_effects_and_transitions)),
                        SettingsOptionChoice(context.getString(R.string.settings_text_6db610cca6), context.getString(R.string.settings_apply_low_pass_during_each_songs_final_5_seconds)),
                        SettingsOptionChoice(context.getString(R.string.settings_text_65ca1d0712), context.getString(R.string.settings_apply_low_pass_during_each_songs_final_10_second)),
                        SettingsOptionChoice(context.getString(R.string.settings_text_eb4354110e), context.getString(R.string.settings_apply_low_pass_during_each_songs_final_15_second)),
                    ),
                    onSelect = { onSelectString(SettingsStore.KEY_AFTERGLOW_DURATION, it) },
                ),
                SettingsEntry.Toggle(
                    id = "permanent_shuffle",
                    title = context.getString(R.string.settings_permanent_shuffle),
                    subtitle = context.getString(R.string.settings_keep_shuffle_permanently_locked_on_in_player),
                    iconRes = R.drawable.shuffle,
                    keywords = listOf("shuffle", "permanent", "lock", "random", "playback"),
                    checked = state.permanentShuffle,
                    onToggle = { onToggleBool(SettingsStore.KEY_PERMANENT_SHUFFLE, it) },
                ),
                SettingsEntry.Toggle(
                    id = "pause_on_zero_volume",
                    title = context.getString(R.string.settings_pause_at_volume_0),
                    subtitle = context.getString(R.string.settings_automatically_pause_playback_when_volume_reaches),
                    iconRes = R.drawable.volume_off,
                    keywords = listOf("pause", "volume", "zero", "mute", "playback", "silence"),
                    checked = state.pauseOnZeroVolume,
                    onToggle = { onToggleBool(SettingsStore.KEY_PAUSE_ON_ZERO_VOLUME, it) },
                ),
                SettingsEntry.Picker(
                    id = "volume_bar_side",
                    title = context.getString(R.string.settings_volume_bar),
                    subtitle = context.getString(R.string.settings_shown_on_the_state_volumebarside_lowercase_edge),
                    iconRes = R.drawable.volume_off,
                    keywords = listOf("volume", "bar", "left", "right", "side", "slider", "hud"),
                    currentValue = state.volumeBarSide,
                    description = context.getString(R.string.settings_choose_which_edge_of_the_screen_the_in_app_volum),
                    options = listOf(
                        SettingsOptionChoice(context.getString(R.string.settings_right), context.getString(R.string.settings_show_the_volume_bar_on_the_right_edge_default)),
                        SettingsOptionChoice(context.getString(R.string.settings_left), context.getString(R.string.settings_show_the_volume_bar_on_the_left_edge)),
                    ),
                    onSelect = { onSelectString(SettingsStore.KEY_VOLUME_BAR_SIDE, it) },
                ),
            ),
        ),
    SettingsSection(
        id = "content_settings",
        title = context.getString(R.string.settings_content),
        entries = listOf(
            SettingsEntry.Picker(
                id = "content_language",
                title = context.getString(R.string.settings_default_content_language),
                subtitle = context.getString(R.string.settings_language_for_moods_browse_search),
                iconRes = R.drawable.ic_settings_content_language,
                keywords = listOf("language", "locale", "hl", "region", "translate", "moods", "browse"),
                currentValue = ContentLocaleCatalog.languageLabel(state.contentLanguage),
                description = context.getString(R.string.settings_select_the_preferred_catalog_language_used_acros),
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
                title = context.getString(R.string.settings_default_content_country),
                subtitle = context.getString(R.string.settings_region_for_charts_playlists_new_releases),
                iconRes = R.drawable.ic_settings_content_country,
                keywords = listOf("country", "region", "gl", "charts", "playlists", "newly added", "local"),
                currentValue = ContentLocaleCatalog.countryLabel(state.contentCountry),
                description = context.getString(R.string.settings_choose_the_regional_storefront_used_for_newly_ad),
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
                title = context.getString(R.string.settings_hide_explicit_content),
                subtitle = context.getString(R.string.settings_filter_explicit_songs_across_moods_browse_search),
                iconRes = R.drawable.ic_settings_explicit,
                keywords = listOf("explicit", "clean", "parental", "hide", "filter", "restricted"),
                checked = state.hideExplicitContent,
                onToggle = { onToggleBool(SettingsStore.KEY_HIDE_EXPLICIT, it) },
            ),
        ),
    ),
    SettingsSection(
        id = "visuals_glass",
        title = context.getString(R.string.settings_now_playing_liquid_glass),
        entries = listOfNotNull(
            SettingsEntry.Toggle(
                id = "splash_screen",
                title = context.getString(R.string.settings_splash_screen),
                subtitle = context.getString(R.string.settings_app_launch_brand_animation_fluid_title_reveal),
                iconRes = R.drawable.ic_itune_waves,
                keywords = listOf("splash", "opening", "launch", "animation", "brand", "intro"),
                checked = state.splashScreenEnabled,
                onToggle = { onToggleBool(SettingsStore.KEY_SPLASH_SCREEN, it) },
            ),
            SettingsEntry.Toggle(
                id = "animated_artwork",
                title = context.getString(R.string.settings_animated_cover_art),
                subtitle = context.getString(R.string.settings_spotify_canvas_apple_music_tidal_community_motio),
                iconRes = R.drawable.ic_settings_animated_art,
                keywords = listOf("motion", "canvas", "video", "cover", "artwork", "animation", "spotify", "apple", "tidal"),
                checked = state.animatedArtwork,
                onToggle = { onToggleBool(SettingsStore.KEY_ANIMATED_ARTWORK, it) },
            ),
            SettingsEntry.Action(
                id = "spotify_integration",
                title = context.getString(R.string.settings_spotify_canvas_setup),
                subtitle = if (state.spotifySpdcToken.isNotBlank()) {
                    "Connected • Canvas, Playlists & Import"
                } else {
                    "Sign in to Spotify for Canvas, playlists & import"
                },
                iconRes = R.drawable.ic_spotify,
                keywords = listOf("spotify", "canvas", "sp_dc", "token", "playlist", "import", "motion", "video"),
                trailingValue = if (state.spotifySpdcToken.isNotBlank()) "Connected" else "Set Up",
                onAction = onOpenSpotifyIntegration,
            ),
            SettingsEntry.Toggle(
                id = "canvas_auto_hide",
                title = context.getString(R.string.settings_auto_hide_canvas_controls),
                subtitle = context.getString(R.string.settings_minimize_controls_after_5s_while_canvas_plays_ta),
                iconRes = R.drawable.ic_settings_animated_art,
                keywords = listOf("canvas", "spotify", "auto", "hide", "minimize", "controls", "player"),
                checked = state.spotifyCanvasAutoHide,
                onToggle = { onToggleBool(SettingsStore.KEY_SPOTIFY_CANVAS_AUTO_HIDE, it) },
            ),
            SettingsEntry.Toggle(
                id = "show_synced_lyrics_over_canvas",
                title = context.getString(R.string.settings_show_synced_lyrics_over_canvas),
                subtitle = context.getString(R.string.settings_display_animated_synced_lyrics_above_the_song_na),
                iconRes = R.drawable.lyrics,
                keywords = listOf("canvas", "synced", "lyrics", "kinetic", "overlay", "words", "animation"),
                checked = state.showSyncedLyricsOverCanvas,
                onToggle = { onToggleBool(SettingsStore.KEY_SHOW_SYNCED_LYRICS_OVER_CANVAS, it) },
            ),
            SettingsEntry.Toggle(
                id = "prioritize_spotify_canvas",
                title = context.getString(R.string.settings_prioritize_spotify_canvas),
                subtitle = context.getString(R.string.settings_try_spotify_canvas_before_apple_music_tidal_comm),
                iconRes = R.drawable.ic_spotify,
                keywords = listOf("spotify", "canvas", "priority", "first", "apple", "tidal"),
                checked = state.prioritizeSpotifyCanvas,
                onToggle = { onToggleBool(SettingsStore.KEY_PRIORITIZE_SPOTIFY_CANVAS, it) },
            ),
            SettingsEntry.Toggle(
                id = "canvas_over_cellular",
                title = context.getString(R.string.settings_play_animated_cover_over_cellular),
                subtitle = context.getString(R.string.settings_allow_looping_canvas_videos_over_mobile_data),
                iconRes = R.drawable.ic_settings_animated_art,
                keywords = listOf("canvas", "cellular", "mobile", "data", "video", "cover"),
                checked = state.canvasOverCellular,
                onToggle = { onToggleBool(SettingsStore.KEY_CANVAS_OVER_CELLULAR, it) },
            ),
            SettingsEntry.SegmentedToggle(
                id = "player_type",
                title = context.getString(R.string.settings_player_type),
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
            SettingsEntry.SegmentedToggle(
                id = "player_style",
                title = context.getString(R.string.settings_player_style),
                subtitle = if (state.playerStyle == SettingsStore.PLAYER_STYLE_SECONDARY) {
                    "Secondary • Album-led player with a dedicated queue layout"
                } else {
                    "Primary • Current player layout"
                },
                iconRes = R.drawable.ic_settings_liquid_glass,
                keywords = listOf("player", "style", "primary", "secondary", "layout", "queue", "album", "controls"),
                options = listOf(SettingsStore.PLAYER_STYLE_PRIMARY, SettingsStore.PLAYER_STYLE_SECONDARY),
                selectedOption = state.playerStyle,
                onSelect = { onSelectString(SettingsStore.KEY_PLAYER_STYLE, it) },
            ),
            SettingsEntry.Toggle(
                id = "player_dynamic_coloring",
                title = context.getString(R.string.settings_player_dynamic_coloring),
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
            SettingsEntry.Toggle(
                id = "full_screen_cover_art",
                title = context.getString(R.string.settings_full_screen_cover_art),
                subtitle = if (state.fullScreenCoverArt) {
                    "Full screen cover art expands to fill the player"
                } else {
                    "Show album artwork in classic centered square container"
                },
                iconRes = R.drawable.ic_lib_albums,
                keywords = listOf("cover", "art", "artwork", "full", "screen", "square", "album"),
                checked = state.fullScreenCoverArt,
                onToggle = { onToggleBool(SettingsStore.KEY_FULL_SCREEN_COVER_ART, it) },
            ),
            if (!state.fullScreenCoverArt) {
                SettingsEntry.Action(
                    id = "cover_corner_radius",
                    title = context.getString(R.string.settings_corner_length),
                    subtitle = context.getString(R.string.settings_adjust_corner_radius_of_square_album_artwork),
                    iconRes = R.drawable.ic_lib_albums,
                    keywords = listOf("corner", "length", "radius", "square", "edge", "cover", "art"),
                    trailingValue = context.getString(R.string.settings_corner_radius_value, state.coverCornerRadius),
                    onAction = onOpenCornerLength,
                )
            } else null,
            SettingsEntry.Toggle(
                id = "hide_quality_tag",
                title = context.getString(R.string.settings_hide_quality_tag),
                subtitle = if (state.hideQualityTag) {
                    "Quality pill badge is hidden from both players"
                } else {
                    "Show audio quality and stream badge below progress bar"
                },
                iconRes = R.drawable.ic_lossless,
                keywords = listOf("hide", "quality", "tag", "pill", "lossless", "audio", "badge", "player"),
                checked = state.hideQualityTag,
                onToggle = { onToggleBool(SettingsStore.KEY_HIDE_QUALITY_TAG, it) },
            ),
            SettingsEntry.Toggle(
                id = "hide_volume_bar",
                title = context.getString(R.string.settings_hide_volume_bar),
                subtitle = if (state.hideVolumeBar) {
                    "Volume slider is hidden from the player"
                } else {
                    "Show volume slider below playback controls"
                },
                iconRes = R.drawable.ic_settings_liquid_glass,
                keywords = listOf("volume", "bar", "slider", "seek", "hide", "player", "controls"),
                checked = state.hideVolumeBar,
                onToggle = { onToggleBool(SettingsStore.KEY_HIDE_VOLUME_BAR, it) },
            ),
            SettingsEntry.SegmentedToggle(
                id = "top_card_type",
                title = context.getString(R.string.settings_home_top_card_style),
                subtitle = when (state.topCardType) {
                    SettingsStore.TOP_CARD_TYPE_PORTRAIT -> "Portrait — Apple Music card with frosted blur"
                    SettingsStore.TOP_CARD_TYPE_APPLE -> "Apple — Full artwork, title on top, frosted details"
                    else -> "Landscape — Wide 16:9 banner card"
                },
                iconRes = R.drawable.ic_settings_liquid_glass,
                keywords = listOf(
                    "top", "card", "hero", "portrait", "landscape", "home", "carousel",
                    "style", "orientation", "shape", "banner", "apple"
                ),
                options = listOf(SettingsStore.TOP_CARD_TYPE_LANDSCAPE, SettingsStore.TOP_CARD_TYPE_PORTRAIT, SettingsStore.TOP_CARD_TYPE_APPLE),
                selectedOption = state.topCardType,
                onSelect = { onSelectString(SettingsStore.KEY_TOP_CARD_TYPE, it) },
            ),
            SettingsEntry.Toggle(
                id = "liquid_glass_nav",
                title = context.getString(R.string.settings_liquid_glass_nav_mini_player),
                subtitle = context.getString(R.string.settings_glass_bar_and_mini_player_with_a_water_bubble_le),
                iconRes = R.drawable.ic_settings_liquid_glass,
                keywords = listOf("liquid", "glass", "nav", "navigation", "mini", "player", "bubble", "lens", "blur", "bar"),
                checked = state.liquidGlassNav,
                onToggle = { onToggleBool(SettingsStore.KEY_LIQUID_GLASS_NAV, it) },
            ),
            SettingsEntry.Toggle(
                id = "time_synced_lyrics",
                title = context.getString(R.string.settings_time_synced_lyrics),
                subtitle = context.getString(R.string.settings_follow_word_and_line_lyrics_in_real_time),
                iconRes = R.drawable.ic_settings_lyrics,
                keywords = listOf("lyrics", "sing", "karaoke", "text"),
                checked = state.timeSyncedLyrics,
                onToggle = { onToggleBool(SettingsStore.KEY_TIME_SYNCED_LYRICS, it) },
            ),
            SettingsEntry.Picker(
                id = "app_font",
                title = context.getString(R.string.settings_app_font),
                subtitle = context.getString(R.string.settings_change_app_typography_player_controls_retain_the),
                iconRes = R.drawable.ic_settings_liquid_glass,
                keywords = listOf("font", "typeface", "typography", "doto", "dotrice", "linotte", "poppins", "sf pro"),
                currentValue = AppFont.fromKey(state.appFont).label,
                description = context.getString(R.string.settings_choose_a_typeface_for_the_app_interface),
                options = AppFont.entries.map { font ->
                    SettingsOptionChoice(font.label, "Use ${font.label} throughout the app")
                },
                onSelect = { onSelectString(SettingsStore.KEY_APP_FONT, it) },
            ),
            SettingsEntry.Action(
                id = "lyrics_providers",
                title = context.getString(R.string.settings_lyrics_providers),
                subtitle = context.getString(R.string.settings_text_2707ccbc8e),
                iconRes = R.drawable.lyrics,
                keywords = listOf(
                    "lyrics", "providers", "lrclib", "kugou", "simpmusic",
                    "subtitles", "betterlyrics", "youly", "megalobiz",
                    "paxsenix", "portato", "unison", "sources", "priority"
                ),
                trailingValue = context.getString(R.string.settings_active_lyrics_providers, state.lyricsProviderOrder.count { it !in state.disabledLyricsProviders }),
                onAction = onOpenLyricsSettings,
            ),
            SettingsEntry.Toggle(
                id = "liquid_glass",
                title = context.getString(R.string.settings_liquid_glass_chrome),
                subtitle = context.getString(R.string.settings_refractive_translucent_headers_dock_cards),
                iconRes = R.drawable.ic_settings_liquid_glass,
                keywords = listOf("blur", "frosted", "theme", "dark", "oled", "glass", "transparent"),
                checked = state.liquidGlassChrome,
                onToggle = { onToggleBool(SettingsStore.KEY_LIQUID_GLASS, it) },
            ),
            SettingsEntry.Toggle(
                id = "tactile_haptics",
                title = context.getString(R.string.settings_tactile_haptics),
                subtitle = context.getString(R.string.settings_subtle_pulse_feedback_across_player_controls),
                iconRes = R.drawable.ic_settings_haptics,
                keywords = listOf("vibrate", "feedback", "touch", "haptic"),
                checked = state.tactileHaptics,
                onToggle = { onToggleBool(SettingsStore.KEY_TACTILE_HAPTICS, it) },
            ),
        ),
    ),
    SettingsSection(
        id = "downloads_storage",
        title = context.getString(R.string.settings_downloads_storage),
        entries = listOf(
            SettingsEntry.Picker(
                id = "cellular_streaming",
                title = context.getString(R.string.settings_cellular_streaming),
                subtitle = context.getString(R.string.settings_audio_bandwidth_on_mobile_data),
                iconRes = R.drawable.ic_settings_cellular,
                keywords = listOf("mobile data", "5g", "lte", "cellular", "roaming"),
                currentValue = state.cellularStreaming,
                description = context.getString(R.string.settings_control_audio_fidelity_when_streaming_away_from),
                options = listOf(
                    SettingsOptionChoice(context.getString(R.string.settings_high_efficiency), context.getString(R.string.settings_use_minimal_cellular_data)),
                    SettingsOptionChoice(context.getString(R.string.settings_high_quality), context.getString(R.string.settings_aac_256_kbps_balanced_mobile_playback)),
                    SettingsOptionChoice(context.getString(R.string.settings_lossless), context.getString(R.string.settings_full_lossless_over_5g_lte_networks)),
                    SettingsOptionChoice(context.getString(R.string.settings_off_e3de5ab0_x), context.getString(R.string.settings_allow_streaming_on_wi_fi_only)),
                ),
                onSelect = { onSelectString(SettingsStore.KEY_CELLULAR_STREAMING, it) },
            ),
            SettingsEntry.Toggle(
                id = "download_wifi_only",
                title = context.getString(R.string.settings_download_over_wi_fi),
                subtitle = context.getString(R.string.settings_wait_for_wi_fi_before_automatic_downloads),
                iconRes = R.drawable.ic_settings_wifi,
                keywords = listOf("wifi", "offline", "save", "bandwidth"),
                checked = state.downloadOverWifiOnly,
                onToggle = { onToggleBool(SettingsStore.KEY_DOWNLOAD_WIFI_ONLY, it) },
            ),
            SettingsEntry.Toggle(
                id = "cache_songs_playing",
                title = context.getString(R.string.settings_cache_songs_after_playback),
                subtitle = context.getString(R.string.settings_save_a_song_to_offline_storage_after_it_finishes),
                iconRes = R.drawable.ic_settings_offline,
                keywords = listOf("cache", "song", "offline", "playing", "streaming", "itube"),
                checked = MusicCacheManager.cachingEnabled.value,
                onToggle = { MusicCacheManager.setCachingEnabled(it) },
            ),
            SettingsEntry.Picker(
                id = "song_cache_quality",
                title = context.getString(R.string.settings_cache_quality),
                subtitle = context.getString(R.string.settings_lower_quality_saves_storage),
                iconRes = R.drawable.ic_settings_storage,
                keywords = listOf("cache", "quality", "compress", "low", "high", "space", "storage", "bitrate"),
                currentValue = MusicCacheManager.cacheQuality.value,
                description = context.getString(R.string.settings_choose_the_quality_of_songs_saved_after_playback),
                options = listOf(
                    SettingsOptionChoice(MusicCacheManager.QUALITY_LOW, "Compressed to 64 kbps AAC • smallest files (~2 MB per song)"),
                    SettingsOptionChoice(MusicCacheManager.QUALITY_MEDIUM, "Compressed to 128 kbps AAC • balanced (~4 MB per song)"),
                    SettingsOptionChoice(MusicCacheManager.QUALITY_HIGH, "Highest available quality, never compressed"),
                ),
                onSelect = { MusicCacheManager.setCacheQuality(it) },
            ),
            SettingsEntry.Picker(
                id = "song_cache_limit",
                title = context.getString(R.string.settings_song_storage_limit),
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
                description = context.getString(R.string.settings_maximum_disk_storage_allocated_for_offline_audio),
                options = listOf(
                    SettingsOptionChoice(context.getString(R.string.settings_text_03a001f575), context.getString(R.string.settings_compact_cache_70_songs)),
                    SettingsOptionChoice(context.getString(R.string.settings_text_33015e637f), context.getString(R.string.settings_moderate_cache_150_songs)),
                    SettingsOptionChoice(context.getString(R.string.settings_text_d3b403ba96), context.getString(R.string.settings_recommended_default_300_songs)),
                    SettingsOptionChoice(context.getString(R.string.settings_text_227edb6ac4), context.getString(R.string.settings_large_offline_collection_800_songs)),
                    SettingsOptionChoice(context.getString(R.string.settings_text_ca3389aabc), context.getString(R.string.settings_very_large_library_1_600_songs)),
                    SettingsOptionChoice(context.getString(R.string.settings_unlimited), context.getString(R.string.settings_never_evict_cached_songs)),
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
                title = context.getString(R.string.settings_clear_cached_songs),
                subtitle = context.getString(R.string.settings_musiccachemanager_cachedtracks_size_songs_musicc),
                iconRes = R.drawable.ic_settings_clear_cache,
                keywords = listOf("clear", "cache", "songs", "delete", "free", "offline"),
                trailingValue = MusicCacheManager.formatStorageSize(MusicCacheManager.totalStorageBytes.longValue),
                onAction = onClearSongCache,
            ),
            SettingsEntry.Toggle(
                id = "automatic_downloads",
                title = context.getString(R.string.settings_automatic_downloads),
                subtitle = context.getString(R.string.settings_save_newly_liked_added_tracks_offline),
                iconRes = R.drawable.ic_settings_downloads,
                keywords = listOf("offline", "auto", "sync", "download"),
                checked = state.automaticDownloads,
                onToggle = { onToggleBool(SettingsStore.KEY_AUTO_DOWNLOADS, it) },
            ),
            SettingsEntry.Action(
                id = "audio_cache",
                title = context.getString(R.string.settings_clear_app_buffer_cache),
                subtitle = context.getString(R.string.settings_remove_temporary_cover_art_network_buffers),
                iconRes = R.drawable.ic_settings_clear_cache,
                keywords = listOf("cache", "storage", "free", "clean", "memory", "buffer"),
                trailingValue = state.cachedSizeLabel,
                onAction = onClearCache,
            ),
        ),
    ),
    SettingsSection(
        id = "library_activity",
        title = context.getString(R.string.settings_library_listening),
        entries = listOf(
            SettingsEntry.Toggle(
                id = "sync_library",
                title = context.getString(R.string.settings_sync_library),
                subtitle = context.getString(R.string.settings_keep_artists_playlists_albums_updated),
                iconRes = R.drawable.ic_settings_sync_library,
                keywords = listOf("cloud", "playlists", "library", "backup"),
                checked = state.syncLibrary,
                onToggle = { onToggleBool(SettingsStore.KEY_SYNC_LIBRARY, it) },
            ),
            SettingsEntry.Toggle(
                id = "listening_history",
                title = context.getString(R.string.settings_listening_history),
                subtitle = context.getString(R.string.settings_shape_personalized_mixes_recently_played),
                iconRes = R.drawable.ic_settings_history,
                keywords = listOf("privacy", "recommendations", "recent", "mix"),
                checked = state.useListeningHistory,
                onToggle = { onToggleBool(SettingsStore.KEY_LISTENING_HISTORY, it) },
            ),
            SettingsEntry.Toggle(
                id = "add_favorites",
                title = context.getString(R.string.settings_keep_likes_on_this_device),
                subtitle = context.getString(R.string.settings_keep_liked_songs_in_itune_when_signed_out),
                iconRes = R.drawable.ic_settings_favorites,
                keywords = listOf("liked", "heart", "star", "favorites", "library", "offline", "sign out"),
                checked = state.addFavoriteSongs,
                onToggle = { onToggleBool(SettingsStore.KEY_ADD_FAVORITE_SONGS, it) },
            ),
        ),
    ),
    SettingsSection(
        id = "notifications_general",
        title = context.getString(R.string.settings_notifications_general),
        entries = listOf(
            SettingsEntry.Picker(
                id = "app_language",
                title = context.getString(R.string.app_language),
                subtitle = when (state.appLanguage) {
                    AppLocaleManager.HINDI -> context.getString(R.string.hindi_interface)
                    AppLocaleManager.SPANISH -> context.getString(R.string.spanish_interface)
                    AppLocaleManager.FRENCH -> context.getString(R.string.french_interface)
                    AppLocaleManager.DUTCH -> context.getString(R.string.dutch_interface)
                    AppLocaleManager.RUSSIAN -> context.getString(R.string.russian_interface)
                    AppLocaleManager.URDU -> context.getString(R.string.urdu_interface)
                    AppLocaleManager.CHINESE -> context.getString(R.string.chinese_interface)
                    AppLocaleManager.GERMAN -> context.getString(R.string.german_interface)
                    AppLocaleManager.PORTUGUESE -> context.getString(R.string.portuguese_interface)
                    AppLocaleManager.ITALIAN -> context.getString(R.string.italian_interface)
                    AppLocaleManager.JAPANESE -> context.getString(R.string.japanese_interface)
                    AppLocaleManager.KOREAN -> context.getString(R.string.korean_interface)
                    AppLocaleManager.SWEDISH -> context.getString(R.string.swedish_interface)
                    AppLocaleManager.ARABIC -> context.getString(R.string.arabic_interface)
                    AppLocaleManager.AZERBAIJANI -> context.getString(R.string.azerbaijani_interface)
                    AppLocaleManager.BENGALI -> context.getString(R.string.bengali_interface)
                    AppLocaleManager.BULGARIAN -> context.getString(R.string.bulgarian_interface)
                    AppLocaleManager.CATALAN -> context.getString(R.string.catalan_interface)
                    AppLocaleManager.CZECH -> context.getString(R.string.czech_interface)
                    AppLocaleManager.DANISH -> context.getString(R.string.danish_interface)
                    AppLocaleManager.FINNISH -> context.getString(R.string.finnish_interface)
                    AppLocaleManager.GREEK -> context.getString(R.string.greek_interface)
                    AppLocaleManager.HEBREW -> context.getString(R.string.hebrew_interface)
                    AppLocaleManager.HUNGARIAN -> context.getString(R.string.hungarian_interface)
                    AppLocaleManager.INDONESIAN -> context.getString(R.string.indonesian_interface)
                    AppLocaleManager.MALAY -> context.getString(R.string.malay_interface)
                    AppLocaleManager.NORWEGIAN -> context.getString(R.string.norwegian_interface)
                    AppLocaleManager.PERSIAN -> context.getString(R.string.persian_interface)
                    AppLocaleManager.POLISH -> context.getString(R.string.polish_interface)
                    AppLocaleManager.ROMANIAN -> context.getString(R.string.romanian_interface)
                    AppLocaleManager.SLOVAK -> context.getString(R.string.slovak_interface)
                    AppLocaleManager.THAI -> context.getString(R.string.thai_interface)
                    AppLocaleManager.TURKISH -> context.getString(R.string.turkish_interface)
                    AppLocaleManager.UKRAINIAN -> context.getString(R.string.ukrainian_interface)
                    AppLocaleManager.VIETNAMESE -> context.getString(R.string.vietnamese_interface)
                    AppLocaleManager.SWAHILI -> context.getString(R.string.swahili_interface)
                    AppLocaleManager.TAGALOG -> context.getString(R.string.tagalog_interface)
                    AppLocaleManager.ENGLISH -> context.getString(R.string.english_interface)
                    else -> context.getString(R.string.follow_device_language)
                },
                iconRes = R.drawable.ic_settings_content_language,
                keywords = listOf("app language", "interface language", "hindi", "हिन्दी", "english", "system"),
                currentValue = when (state.appLanguage) {
                    AppLocaleManager.HINDI -> context.getString(R.string.hindi_language)
                    AppLocaleManager.SPANISH -> context.getString(R.string.spanish_language)
                    AppLocaleManager.FRENCH -> context.getString(R.string.french_language)
                    AppLocaleManager.DUTCH -> context.getString(R.string.dutch_language)
                    AppLocaleManager.RUSSIAN -> context.getString(R.string.russian_language)
                    AppLocaleManager.URDU -> context.getString(R.string.urdu_language)
                    AppLocaleManager.CHINESE -> context.getString(R.string.chinese_language)
                    AppLocaleManager.GERMAN -> context.getString(R.string.german_language)
                    AppLocaleManager.PORTUGUESE -> context.getString(R.string.portuguese_language)
                    AppLocaleManager.ITALIAN -> context.getString(R.string.italian_language)
                    AppLocaleManager.JAPANESE -> context.getString(R.string.japanese_language)
                    AppLocaleManager.KOREAN -> context.getString(R.string.korean_language)
                    AppLocaleManager.SWEDISH -> context.getString(R.string.swedish_language)
                    AppLocaleManager.ARABIC -> context.getString(R.string.arabic_language)
                    AppLocaleManager.AZERBAIJANI -> context.getString(R.string.azerbaijani_language)
                    AppLocaleManager.BENGALI -> context.getString(R.string.bengali_language)
                    AppLocaleManager.BULGARIAN -> context.getString(R.string.bulgarian_language)
                    AppLocaleManager.CATALAN -> context.getString(R.string.catalan_language)
                    AppLocaleManager.CZECH -> context.getString(R.string.czech_language)
                    AppLocaleManager.DANISH -> context.getString(R.string.danish_language)
                    AppLocaleManager.FINNISH -> context.getString(R.string.finnish_language)
                    AppLocaleManager.GREEK -> context.getString(R.string.greek_language)
                    AppLocaleManager.HEBREW -> context.getString(R.string.hebrew_language)
                    AppLocaleManager.HUNGARIAN -> context.getString(R.string.hungarian_language)
                    AppLocaleManager.INDONESIAN -> context.getString(R.string.indonesian_language)
                    AppLocaleManager.MALAY -> context.getString(R.string.malay_language)
                    AppLocaleManager.NORWEGIAN -> context.getString(R.string.norwegian_language)
                    AppLocaleManager.PERSIAN -> context.getString(R.string.persian_language)
                    AppLocaleManager.POLISH -> context.getString(R.string.polish_language)
                    AppLocaleManager.ROMANIAN -> context.getString(R.string.romanian_language)
                    AppLocaleManager.SLOVAK -> context.getString(R.string.slovak_language)
                    AppLocaleManager.THAI -> context.getString(R.string.thai_language)
                    AppLocaleManager.TURKISH -> context.getString(R.string.turkish_language)
                    AppLocaleManager.UKRAINIAN -> context.getString(R.string.ukrainian_language)
                    AppLocaleManager.VIETNAMESE -> context.getString(R.string.vietnamese_language)
                    AppLocaleManager.SWAHILI -> context.getString(R.string.swahili_language)
                    AppLocaleManager.TAGALOG -> context.getString(R.string.tagalog_language)
                    AppLocaleManager.ENGLISH -> context.getString(R.string.english_language)
                    else -> context.getString(R.string.system_default_language)
                },
                description = context.getString(R.string.app_language_description),
                options = listOf(
                    SettingsOptionChoice(context.getString(R.string.system_default_language), context.getString(R.string.follow_device_language)),
                    SettingsOptionChoice(context.getString(R.string.english_language), context.getString(R.string.english_interface)),
                    SettingsOptionChoice(context.getString(R.string.hindi_language), context.getString(R.string.hindi_interface)),
                    SettingsOptionChoice(context.getString(R.string.spanish_language), context.getString(R.string.spanish_interface)),
                    SettingsOptionChoice(context.getString(R.string.french_language), context.getString(R.string.french_interface)),
                    SettingsOptionChoice(context.getString(R.string.dutch_language), context.getString(R.string.dutch_interface)),
                    SettingsOptionChoice(context.getString(R.string.russian_language), context.getString(R.string.russian_interface)),
                    SettingsOptionChoice(context.getString(R.string.urdu_language), context.getString(R.string.urdu_interface)),
                    SettingsOptionChoice(context.getString(R.string.chinese_language), context.getString(R.string.chinese_interface)),
                    SettingsOptionChoice(context.getString(R.string.german_language), context.getString(R.string.german_interface)),
                    SettingsOptionChoice(context.getString(R.string.portuguese_language), context.getString(R.string.portuguese_interface)),
                    SettingsOptionChoice(context.getString(R.string.italian_language), context.getString(R.string.italian_interface)),
                    SettingsOptionChoice(context.getString(R.string.japanese_language), context.getString(R.string.japanese_interface)),
                    SettingsOptionChoice(context.getString(R.string.korean_language), context.getString(R.string.korean_interface)),
                    SettingsOptionChoice(context.getString(R.string.swedish_language), context.getString(R.string.swedish_interface)),
                    SettingsOptionChoice(context.getString(R.string.arabic_language), context.getString(R.string.arabic_interface)),
                    SettingsOptionChoice(context.getString(R.string.azerbaijani_language), context.getString(R.string.azerbaijani_interface)),
                    SettingsOptionChoice(context.getString(R.string.bengali_language), context.getString(R.string.bengali_interface)),
                    SettingsOptionChoice(context.getString(R.string.bulgarian_language), context.getString(R.string.bulgarian_interface)),
                    SettingsOptionChoice(context.getString(R.string.catalan_language), context.getString(R.string.catalan_interface)),
                    SettingsOptionChoice(context.getString(R.string.czech_language), context.getString(R.string.czech_interface)),
                    SettingsOptionChoice(context.getString(R.string.danish_language), context.getString(R.string.danish_interface)),
                    SettingsOptionChoice(context.getString(R.string.finnish_language), context.getString(R.string.finnish_interface)),
                    SettingsOptionChoice(context.getString(R.string.greek_language), context.getString(R.string.greek_interface)),
                    SettingsOptionChoice(context.getString(R.string.hebrew_language), context.getString(R.string.hebrew_interface)),
                    SettingsOptionChoice(context.getString(R.string.hungarian_language), context.getString(R.string.hungarian_interface)),
                    SettingsOptionChoice(context.getString(R.string.indonesian_language), context.getString(R.string.indonesian_interface)),
                    SettingsOptionChoice(context.getString(R.string.malay_language), context.getString(R.string.malay_interface)),
                    SettingsOptionChoice(context.getString(R.string.norwegian_language), context.getString(R.string.norwegian_interface)),
                    SettingsOptionChoice(context.getString(R.string.persian_language), context.getString(R.string.persian_interface)),
                    SettingsOptionChoice(context.getString(R.string.polish_language), context.getString(R.string.polish_interface)),
                    SettingsOptionChoice(context.getString(R.string.romanian_language), context.getString(R.string.romanian_interface)),
                    SettingsOptionChoice(context.getString(R.string.slovak_language), context.getString(R.string.slovak_interface)),
                    SettingsOptionChoice(context.getString(R.string.thai_language), context.getString(R.string.thai_interface)),
                    SettingsOptionChoice(context.getString(R.string.turkish_language), context.getString(R.string.turkish_interface)),
                    SettingsOptionChoice(context.getString(R.string.ukrainian_language), context.getString(R.string.ukrainian_interface)),
                    SettingsOptionChoice(context.getString(R.string.vietnamese_language), context.getString(R.string.vietnamese_interface)),
                    SettingsOptionChoice(context.getString(R.string.swahili_language), context.getString(R.string.swahili_interface)),
                    SettingsOptionChoice(context.getString(R.string.tagalog_language), context.getString(R.string.tagalog_interface)),
                ),
                onSelect = { chosenLabel ->
                    val language = when (chosenLabel) {
                        context.getString(R.string.hindi_language) -> AppLocaleManager.HINDI
                        context.getString(R.string.spanish_language) -> AppLocaleManager.SPANISH
                        context.getString(R.string.french_language) -> AppLocaleManager.FRENCH
                        context.getString(R.string.dutch_language) -> AppLocaleManager.DUTCH
                        context.getString(R.string.russian_language) -> AppLocaleManager.RUSSIAN
                        context.getString(R.string.urdu_language) -> AppLocaleManager.URDU
                        context.getString(R.string.chinese_language) -> AppLocaleManager.CHINESE
                        context.getString(R.string.german_language) -> AppLocaleManager.GERMAN
                        context.getString(R.string.portuguese_language) -> AppLocaleManager.PORTUGUESE
                        context.getString(R.string.italian_language) -> AppLocaleManager.ITALIAN
                        context.getString(R.string.japanese_language) -> AppLocaleManager.JAPANESE
                        context.getString(R.string.korean_language) -> AppLocaleManager.KOREAN
                        context.getString(R.string.swedish_language) -> AppLocaleManager.SWEDISH
                        context.getString(R.string.arabic_language) -> AppLocaleManager.ARABIC
                        context.getString(R.string.azerbaijani_language) -> AppLocaleManager.AZERBAIJANI
                        context.getString(R.string.bengali_language) -> AppLocaleManager.BENGALI
                        context.getString(R.string.bulgarian_language) -> AppLocaleManager.BULGARIAN
                        context.getString(R.string.catalan_language) -> AppLocaleManager.CATALAN
                        context.getString(R.string.czech_language) -> AppLocaleManager.CZECH
                        context.getString(R.string.danish_language) -> AppLocaleManager.DANISH
                        context.getString(R.string.finnish_language) -> AppLocaleManager.FINNISH
                        context.getString(R.string.greek_language) -> AppLocaleManager.GREEK
                        context.getString(R.string.hebrew_language) -> AppLocaleManager.HEBREW
                        context.getString(R.string.hungarian_language) -> AppLocaleManager.HUNGARIAN
                        context.getString(R.string.indonesian_language) -> AppLocaleManager.INDONESIAN
                        context.getString(R.string.malay_language) -> AppLocaleManager.MALAY
                        context.getString(R.string.norwegian_language) -> AppLocaleManager.NORWEGIAN
                        context.getString(R.string.persian_language) -> AppLocaleManager.PERSIAN
                        context.getString(R.string.polish_language) -> AppLocaleManager.POLISH
                        context.getString(R.string.romanian_language) -> AppLocaleManager.ROMANIAN
                        context.getString(R.string.slovak_language) -> AppLocaleManager.SLOVAK
                        context.getString(R.string.thai_language) -> AppLocaleManager.THAI
                        context.getString(R.string.turkish_language) -> AppLocaleManager.TURKISH
                        context.getString(R.string.ukrainian_language) -> AppLocaleManager.UKRAINIAN
                        context.getString(R.string.vietnamese_language) -> AppLocaleManager.VIETNAMESE
                        context.getString(R.string.swahili_language) -> AppLocaleManager.SWAHILI
                        context.getString(R.string.tagalog_language) -> AppLocaleManager.TAGALOG
                        context.getString(R.string.english_language) -> AppLocaleManager.ENGLISH
                        else -> AppLocaleManager.SYSTEM_DEFAULT
                    }
                    onSelectString(SettingsStore.KEY_APP_LANGUAGE, language)
                },
            ),
            SettingsEntry.Toggle(
                id = "offline_mode",
                title = context.getString(R.string.settings_offline_mode),
                subtitle = context.getString(R.string.settings_require_a_downloaded_copy_to_start_playback),
                iconRes = R.drawable.ic_settings_offline,
                keywords = listOf("airplane", "offline", "local", "disconnect"),
                checked = state.offlineMode,
                onToggle = { onToggleBool(SettingsStore.KEY_OFFLINE_MODE, it) },
            ),
            SettingsEntry.Toggle(
                id = "stop_music_on_task_clear",
                title = context.getString(R.string.settings_stop_music_on_task_clear),
                subtitle = context.getString(R.string.settings_stop_playback_when_itune_is_swiped_away_from_rec),
                iconRes = R.drawable.ic_settings_sleep_timer,
                keywords = listOf("stop", "close", "recents", "swipe", "task", "clear", "background", "playback"),
                checked = state.stopMusicOnTaskClear,
                onToggle = { onToggleBool(SettingsStore.KEY_STOP_MUSIC_ON_TASK_CLEAR, it) },
            ),
            SettingsEntry.Action(
                id = "auto_sleep_timer",
                title = context.getString(R.string.settings_auto_sleep_timer),
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
                title = context.getString(R.string.settings_network_proxy),
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
                title = context.getString(R.string.settings_profile_name),
                subtitle = context.getString(R.string.settings_customize_your_settings_profile_display_name),
                iconRes = R.drawable.ic_settings_profile_name,
                keywords = listOf("name", "profile", "account", "rename", "user"),
                trailingValue = state.displayName,
                onAction = onEditName,
            ),
            SettingsEntry.Action(
                id = "developer_news",
                title = context.getString(R.string.settings_news_from_dev),
                subtitle = context.getString(R.string.settings_announcements_releases_changelog),
                iconRes = R.drawable.newspaper,
                keywords = listOf("news", "developer", "dev", "updates", "changelog", "announcements", "release", "airbeats"),
                trailingValue = null,
                onAction = onOpenDeveloperNews,
            ),
            SettingsEntry.Action(
                id = "about_itune",
                title = context.getString(R.string.settings_general_about),
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
        title = context.getString(R.string.settings_home_screen_widget),
        entries = listOf(
            SettingsEntry.Action(
                id = "widget_customization",
                title = context.getString(R.string.settings_widget_customization),
                subtitle = context.getString(R.string.settings_choose_a_size_preview_artwork_controls_and_widge),
                iconRes = R.drawable.ic_settings_about,
                keywords = listOf("widget", "home screen", "player", "artwork", "controls", "style", "customize"),
                trailingValue = "Customize",
                onAction = onOpenWidgetSettings,
            ),
        ),
    ),
    SettingsSection(
        id = "backup_restore",
        title = context.getString(R.string.settings_backup_restore),
        entries = listOf(
            SettingsEntry.Action(
                id = "auto_backup_itune",
                title = context.getString(R.string.settings_automatic_daily_backup),
                subtitle = context.getString(R.string.settings_encrypted_daily_backups_downloads_itune_backups),
                iconRes = R.drawable.ic_settings_storage,
                keywords = listOf("automatic", "daily", "backup", "password", "encrypted"),
                trailingValue = "Configure",
                onAction = onOpenAutoBackup,
            ),
            SettingsEntry.Action(
                id = "backup_itune",
                title = context.getString(R.string.settings_back_up_itune),
                subtitle = context.getString(R.string.settings_save_accounts_all_settings_audio_effects_library),
                iconRes = R.drawable.ic_settings_storage,
                keywords = listOf("backup", "export", "save", "encrypted", "account", "playlist", "history", "stats"),
                trailingValue = "Create",
                onAction = onOpenBackup,
            ),
            SettingsEntry.Action(
                id = "restore_itune",
                title = context.getString(R.string.settings_restore_from_backup),
                subtitle = context.getString(R.string.settings_restore_settings_audio_effects_accounts_or_libra),
                iconRes = R.drawable.ic_settings_history,
                keywords = listOf("restore", "import", "backup", "itune", "account", "library"),
                trailingValue = "Choose File",
                onAction = onOpenRestore,
            ),
        ),
    ),
    SettingsSection(
        id = "privacy_card",
        title = context.getString(R.string.settings_privacy),
        entries = listOf(
            SettingsEntry.Toggle(
                id = "pause_search_history",
                title = context.getString(R.string.settings_pause_search_history),
                subtitle = context.getString(R.string.settings_do_not_save_new_search_queries_to_history),
                iconRes = R.drawable.ic_settings_clear_history,
                keywords = listOf("pause", "search", "history", "private", "queries"),
                checked = state.pauseSearchHistory,
                onToggle = { onToggleBool(SettingsStore.KEY_PAUSE_SEARCH_HISTORY, it) },
            ),
            SettingsEntry.Action(
                id = "clear_search_history",
                title = context.getString(R.string.settings_clear_search_history),
                subtitle = context.getString(R.string.settings_remove_recent_queries_from_search),
                iconRes = R.drawable.ic_settings_clear_history,
                keywords = listOf("search", "recent", "queries", "clear", "delete", "privacy"),
                trailingValue = if (searchHistoryCount == 0) "Empty" else "$searchHistoryCount saved",
                onAction = onClearSearchHistory,
            ),
            SettingsEntry.Toggle(
                id = "pause_listening_history",
                title = context.getString(R.string.settings_pause_listening_history),
                subtitle = context.getString(R.string.settings_do_not_record_played_songs_or_update_recommendat),
                iconRes = R.drawable.ic_settings_history,
                keywords = listOf("pause", "listening", "history", "playback", "privacy"),
                checked = state.pauseListeningHistory,
                onToggle = { onToggleBool(SettingsStore.KEY_PAUSE_LISTENING_HISTORY, it) },
            ),
            SettingsEntry.Action(
                id = "clear_listening_history",
                title = context.getString(R.string.settings_clear_listening_history),
                subtitle = context.getString(R.string.settings_remove_playback_history_and_reset_taste_profile),
                iconRes = R.drawable.ic_settings_history,
                keywords = listOf("clear", "listening", "history", "reset", "privacy", "taste"),
                trailingValue = "Clear",
                onAction = onClearListeningHistory,
            ),
            SettingsEntry.Toggle(
                id = "disable_screenshot",
                title = context.getString(R.string.settings_disable_screenshot),
                subtitle = context.getString(R.string.settings_block_screenshots_and_screen_recording_across_ap),
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
