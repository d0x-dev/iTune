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
import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Sync
import com.darkxvenom.itune.data.account.MultiAccountManager
import com.darkxvenom.itune.data.account.UserAccount
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.darkxvenom.itune.R
import com.darkxvenom.itune.data.auth.GoogleAccountSession
import com.darkxvenom.itune.data.auth.GoogleAuthStore
import com.darkxvenom.itune.data.auth.CapturedSession
import com.darkxvenom.itune.data.auth.WebSessionMode
import com.darkxvenom.itune.data.auth.looksSignedInCookies
import com.darkxvenom.itune.data.auth.YouTubeLibrarySyncManager
import com.darkxvenom.itune.data.auth.YouTubeProfile
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.data.lastfm.LastFmIntegration
import com.darkxvenom.itune.data.discord.DiscordPresenceManager
import com.darkxvenom.itune.data.repository.AvatarManager
import com.darkxvenom.itune.ishare.IShareSyncManager
import com.darkxvenom.itune.ui.artist.components.BottomFadeScrim
import com.darkxvenom.itune.ui.artist.components.FrostedTopBar
import com.darkxvenom.itune.ui.artist.components.FrostedTopBarTitlePill
import com.darkxvenom.itune.ui.artist.components.TopFadeBlur
import com.darkxvenom.itune.ui.artist.components.topBarContentPadding
import com.darkxvenom.itune.ui.artist.haptics.Haptic
import com.darkxvenom.itune.ui.artist.haptics.rememberHaptics
import com.darkxvenom.itune.ui.artist.icons.SfIcons
import com.darkxvenom.itune.ui.auth.GoogleLoginScreen
import com.darkxvenom.itune.ui.auth.verifyAndPersistCapturedSession
import com.darkxvenom.itune.ui.components.AppleMemojiAvatar
import com.darkxvenom.itune.ui.components.rememberSpringOverscrollState
import com.darkxvenom.itune.ui.components.springOverscroll
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import com.darkxvenom.itune.ui.theme.AppleTextSecondary
import com.darkxvenom.itune.ui.welcome.welcomeBackspaceDelayMs
import com.darkxvenom.itune.ui.welcome.welcomeTypingKeystrokeDelayMs
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope

private val ProfileCardGrey = Color(0xFF1C1C1E)
private val ProfileDivider = Color(0xFF2C2C2E)
private val GoogleRedStart = Color(0xFFFA2D48)
private val GoogleRedEnd = Color(0xFFD61D39)

/**
 * Apple Settings-styled Profile & Google Account screen opened by tapping the top Profile card
 * inside AppleSettingsScreen. Allows editing display name, picking avatar popup, and signing
 * into Google / YouTube Music for full real-time sync.
 */
@Composable
fun SettingsProfileAccountScreen(
    hasMiniPlayer: Boolean,
    onBack: () -> Unit,
    onOpenAvatarPopup: () -> Unit,
    onOpenSpotify: () -> Unit = {},
    onAddNewAccount: () -> Unit = {},
    onSwitchAccount: (UserAccount, Rect?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val integrationScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val haptics = rememberHaptics()
    val settingsState by SettingsStore.state.collectAsStateWithLifecycle()
    val ytAccount by GoogleAuthStore.state.collectAsStateWithLifecycle()
    val accountBoundsMap = remember { mutableStateMapOf<String, Rect>() }

    LaunchedEffect(Unit) {
        GoogleAuthStore.ensureInit(context)
        LastFmIntegration.initialize(context)
        DiscordPresenceManager.initialize(context)
    }

    val activeAccount by MultiAccountManager.activeAccount.collectAsStateWithLifecycle()
    val accountsList by MultiAccountManager.accounts.collectAsStateWithLifecycle()
    var nameInput by remember(activeAccount?.displayName, settingsState.displayName) {
        mutableStateOf(activeAccount?.displayName ?: settingsState.displayName)
    }
    val cleanName = nameInput.trim()
    val nameDirty = cleanName.isNotEmpty() && cleanName != settingsState.displayName

    var loginMode by remember { mutableStateOf<WebSessionMode?>(null) }
    var showAdvancedCookieLogin by remember { mutableStateOf(false) }
    var advancedCookieLoginBusy by remember { mutableStateOf(false) }
    var showLastFmSignIn by remember { mutableStateOf(false) }
    var lastFmBusy by remember { mutableStateOf(false) }
    var showDiscordLogin by remember { mutableStateOf(false) }
    var discordLoginBusy by remember { mutableStateOf(false) }

    var accountActionTarget by remember { mutableStateOf<UserAccount?>(null) }
    var showAccountActionMenu by remember { mutableStateOf(false) }
    var showRenameAccountDialog by remember { mutableStateOf(false) }
    var showDeleteAccountConfirmDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val overscrollState = rememberSpringOverscrollState()
    val hazeState = remember { HazeState() }
    val isScrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 70 }
    }

    fun commitName() {
        if (cleanName.isBlank()) return
        haptics.play(Haptic.Select)
        SettingsStore.setString(context, SettingsStore.KEY_DISPLAY_NAME, cleanName)
        IShareSyncManager.setDisplayName(cleanName)
        MultiAccountManager.updateActiveAccountProfile(context, displayName = cleanName)
        focusManager.clearFocus()
        Toast.makeText(context, context.getString(R.string.toast_name_saved), Toast.LENGTH_SHORT).show()
    }

    BackHandler(enabled = loginMode == null) {
        onBack()
    }

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
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .springOverscroll(overscrollState),
            ) {
                // Header
                item(key = "profile_header") {
                    Text(
                        text = stringResource(R.string.ui_account),
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.6).sp,
                        modifier = Modifier.padding(horizontal = 18.dp),
                    )
                }

                // Tappable Memoji Avatar Hero opening AvatarSelectionDialog
                item(key = "profile_avatar_hero") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(104.dp)
                                .clickable {
                                    haptics.play(Haptic.Tap)
                                    onOpenAvatarPopup()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            AppleMemojiAvatar(
                                size = 96.dp,
                                avatarId = activeAccount?.avatarId ?: AvatarManager.currentAvatarId.value,
                                onClick = {
                                    haptics.play(Haptic.Tap)
                                    onOpenAvatarPopup()
                                },
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(2.dp)
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(AppleMusicRed)
                                    .border(2.dp, AppleOledBlack, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PhotoCamera,
                                    contentDescription = stringResource(R.string.ui_choose_avatar),
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.ui_tap_avatar_to_customize),
                            color = AppleTextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable {
                                haptics.play(Haptic.Tap)
                                onOpenAvatarPopup()
                            },
                        )
                    }
                }

                // Profile Identity Grouped Settings Card (Editable Name + Avatar Picker)
                item(key = "profile_identity_group") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.ui_profile),
                            color = Color.White.copy(alpha = 0.56f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.4.sp,
                            modifier = Modifier.padding(start = 12.dp, bottom = 7.dp),
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(ProfileCardGrey),
                        ) {
                            // Editable Name row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.ui_name),
                                    color = Color.White,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(68.dp),
                                )
                                BasicTextField(
                                    value = nameInput,
                                    onValueChange = { nameInput = it },
                                    singleLine = true,
                                    cursorBrush = SolidColor(AppleMusicRed),
                                    textStyle = TextStyle(
                                        color = Color.White,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Normal,
                                    ),
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Words,
                                        imeAction = ImeAction.Done,
                                    ),
                                    keyboardActions = KeyboardActions(onDone = { commitName() }),
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { inner ->
                                        Box(contentAlignment = Alignment.CenterStart) {
                                            if (nameInput.isEmpty()) {
                                                Text(
                                                    text = stringResource(R.string.ui_your_name),
                                                    color = Color.White.copy(alpha = 0.35f),
                                                    fontSize = 15.5.sp,
                                                )
                                            }
                                            inner()
                                        }
                                    },
                                )
                                if (nameDirty) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(AppleMusicRed)
                                            .clickable { commitName() }
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = stringResource(R.string.ui_save_name),
                                                tint = Color.White,
                                                modifier = Modifier.size(13.dp),
                                            )
                                            Spacer(Modifier.width(3.dp))
                                            Text(
                                                text = stringResource(R.string.save),
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = stringResource(R.string.ui_editable_name),
                                        tint = Color.White.copy(alpha = 0.38f),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                thickness = 0.5.dp,
                                color = ProfileDivider,
                            )

                            // Avatar selector row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptics.play(Haptic.Tap)
                                        onOpenAvatarPopup()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.ui_avatar),
                                    color = Color.White,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = stringResource(R.string.ui_memoji_style),
                                    color = AppleTextSecondary,
                                    fontSize = 14.sp,
                                )
                                Spacer(Modifier.width(8.dp))
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

                // ── Multi-Account Management Card ──
                item(key = "multi_account_group") {
                    val accounts by MultiAccountManager.accounts.collectAsStateWithLifecycle()
                    val activeAccount by MultiAccountManager.activeAccount.collectAsStateWithLifecycle()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.ui_accounts_d7cfa423),
                            color = Color.White.copy(alpha = 0.56f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.4.sp,
                            modifier = Modifier.padding(start = 12.dp, bottom = 7.dp),
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(ProfileCardGrey),
                        ) {
                            accounts.forEachIndexed { index, acc ->
                                val isActive = acc.id == activeAccount?.id

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (!isActive) {
                                                haptics.play(Haptic.Select)
                                                val bounds = accountBoundsMap[acc.id]
                                                onSwitchAccount(acc, bounds)
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // Left side: profile in circle
                                    Box(
                                        modifier = Modifier.onGloballyPositioned { coords ->
                                            accountBoundsMap[acc.id] = coords.boundsInRoot()
                                        }
                                    ) {
                                        AppleMemojiAvatar(
                                            size = 40.dp,
                                            avatarId = acc.avatarId,
                                        )
                                    }

                                    Spacer(Modifier.width(14.dp))

                                    // Right side: Name
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = acc.displayName,
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (isActive) {
                                            Text(
                                                text = stringResource(R.string.ui_current_account),
                                                color = AppleMusicRed,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Normal,
                                            )
                                        }
                                    }

                                    if (isActive) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = stringResource(R.string.ui_active),
                                            tint = AppleMusicRed,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }

                                    // 3-dot account options button (Rename / Delete)
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                haptics.play(Haptic.Tap)
                                                accountActionTarget = acc
                                                showAccountActionMenu = true
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MoreVert,
                                            contentDescription = stringResource(R.string.ui_account_options),
                                            tint = Color.White.copy(alpha = 0.58f),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }

                                if (index < accounts.lastIndex) {
                                    HorizontalDivider(
                                        color = Color.White.copy(alpha = 0.08f),
                                        thickness = 0.5.dp,
                                        modifier = Modifier.padding(start = 70.dp),
                                    )
                                }
                            }

                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.10f),
                                thickness = 0.5.dp,
                            )

                            // Below that: Add Account button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptics.play(Haptic.Tap)
                                        onAddNewAccount()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.10f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = stringResource(R.string.ui_add_account),
                                        tint = AppleMusicRed,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }

                                Spacer(Modifier.width(14.dp))

                                Text(
                                    text = stringResource(R.string.ui_add_account),
                                    color = AppleMusicRed,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }

                // Google / YouTube Music Card Section
                item(key = "google_account_section") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.ui_youtube_music_google),
                            color = Color.White.copy(alpha = 0.56f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.4.sp,
                            modifier = Modifier.padding(start = 12.dp, bottom = 7.dp),
                        )

                        if (!ytAccount.isSignedIn) {
                            ContinueWithGoogleRedCard(
                                subtitle = "Sync YouTube Music playlists, liked songs, artists & history",
                                subtitlePhrases = DEFAULT_GOOGLE_CARD_PHRASES,
                                onClick = {
                                    haptics.play(Haptic.Tap)
                                    loginMode = WebSessionMode.SIGN_IN
                                },
                            )
                        } else {
                            // Signed-In Account Card + Red Google Switch Card + Sync / Sign Out controls
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(ProfileCardGrey),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(Color.White),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (!ytAccount.avatarUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = ytAccount.avatarUrl,
                                                contentDescription = ytAccount.accountName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                            )
                                        } else {
                                            Image(
                                                painter = painterResource(R.drawable.ic_google_logo),
                                                contentDescription = stringResource(R.string.ui_google),
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }
                                    }

                                    Spacer(Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = ytAccount.accountName.ifBlank { "Google Account" },
                                            color = Color.White,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        val detailLine = ytAccount.accountEmail
                                            .ifBlank { ytAccount.accountHandle }
                                            .ifBlank { "Connected to YouTube Music" }
                                        Text(
                                            text = detailLine,
                                            color = AppleTextSecondary,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (!ytAccount.channelName.isNullOrBlank()) {
                                            Text(
                                                text = stringResource(R.string.ui_channel, ytAccount.channelName.orEmpty()),
                                                color = Color(0xFF34C759),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        }
                                    }

                                    Image(
                                        painter = painterResource(R.drawable.ic_google_logo),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 16.dp),
                                    thickness = 0.5.dp,
                                    color = ProfileDivider,
                                )

                                // Sync Library Now
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !ytAccount.isSyncing) {
                                            haptics.play(Haptic.Tap)
                                            YouTubeLibrarySyncManager.launchFullSync(context)
                                            Toast.makeText(context, context.getString(R.string.toast_syncing_youtube_music_library), Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(29.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(Color(0xFF0A84FF)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (ytAccount.isSyncing) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                strokeWidth = 1.8.dp,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.Sync,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(17.dp),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = if (ytAccount.isSyncing) "Syncing Library..." else "Sync YouTube Music Now",
                                        color = Color.White,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(
                                        imageVector = SfIcons.ChevronRight,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.36f),
                                        modifier = Modifier.size(13.dp),
                                    )
                                }

                                if (!ytAccount.lastSyncError.isNullOrBlank()) {
                                    Text(
                                        text = stringResource(R.string.ui_sync_issue, ytAccount.lastSyncError.orEmpty()),
                                        color = Color(0xFFFF6961),
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp),
                                    )
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 16.dp),
                                    thickness = 0.5.dp,
                                    color = ProfileDivider,
                                )

                                // Switch YouTube Channel on current Google account
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            haptics.play(Haptic.Tap)
                                            loginMode = WebSessionMode.SWITCH_CHANNEL
                                        }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(29.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(Color(0xFF5E5CE6)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.SwapHoriz,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(17.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.ui_choose_youtube_channel),
                                            color = Color.White,
                                            fontSize = 15.5.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        val channelSub = ytAccount.channelName?.takeIf { it.isNotBlank() }
                                            ?: "Pick personal or brand channel"
                                        Text(
                                            text = channelSub,
                                            color = AppleTextSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Icon(
                                        imageVector = SfIcons.ChevronRight,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.36f),
                                        modifier = Modifier.size(13.dp),
                                    )
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 16.dp),
                                    thickness = 0.5.dp,
                                    color = ProfileDivider,
                                )

                                // Sign Out active account (preserves remaining saved accounts if any)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            haptics.play(Haptic.Select)
                                            val activeId = ytAccount.activeAccountId
                                            if (!activeId.isNullOrBlank() && ytAccount.accounts.size > 1) {
                                                val stillSignedIn = YouTubeLibrarySyncManager.removeAccountAndSwitch(context, activeId)
                                                val msg = if (stillSignedIn) {
                                                    "Signed out active account • Switched feed"
                                                } else {
                                                    "Signed out (local likes preserved)"
                                                }
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            } else {
                                                YouTubeLibrarySyncManager.signOutAndPreserveLocal(context)
                                                Toast.makeText(context, context.getString(R.string.toast_signed_out_local_likes_preserved), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(29.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(AppleMusicRed),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = if (ytAccount.accounts.size > 1) {
                                            "Sign Out of Active Account"
                                        } else {
                                            "Sign Out of Google"
                                        },
                                        color = AppleMusicRed,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f),
                                    )
                                }

                                if (ytAccount.accounts.size > 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 16.dp),
                                        thickness = 0.5.dp,
                                        color = ProfileDivider,
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                haptics.play(Haptic.Select)
                                                YouTubeLibrarySyncManager.signOutAndPreserveLocal(context)
                                                Toast.makeText(context, context.getString(R.string.toast_signed_out_of_all_google_accounts), Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Spacer(Modifier.width(41.dp))
                                        Text(
                                            text = stringResource(R.string.ui_sign_out_all_accounts),
                                            color = AppleMusicRed.copy(alpha = 0.88f),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                            }

                            if (ytAccount.accounts.isNotEmpty()) {
                                Spacer(Modifier.height(18.dp))
                                Text(
                                    text = if (ytAccount.accounts.size > 1) {
                                        "SWITCH ACCOUNT & CHANNELS (${ytAccount.accounts.size})"
                                    } else {
                                        "ACCOUNT & CHANNELS"
                                    },
                                    color = Color.White.copy(alpha = 0.56f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.4.sp,
                                    modifier = Modifier.padding(start = 12.dp, bottom = 7.dp),
                                )
                                SavedAccountsAndChannelsCard(
                                    accounts = ytAccount.accounts,
                                    activeAccountId = ytAccount.activeAccountId,
                                    activeProfileId = ytAccount.activeProfileId,
                                    onSelectProfile = { session, profile ->
                                        haptics.play(Haptic.Select)
                                        val changed = YouTubeLibrarySyncManager.switchAccountOrProfile(
                                            context = context,
                                            accountId = session.accountId,
                                            profileId = profile.profileId,
                                        )
                                        if (changed) {
                                            val label = profile.name.ifBlank { session.name.ifBlank { "Google Account" } }
                                            Toast.makeText(context, context.getString(R.string.toast_switched_feed_to, label), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onRemoveAccount = { session ->
                                        haptics.play(Haptic.Select)
                                        val stillSignedIn = YouTubeLibrarySyncManager.removeAccountAndSwitch(context, session.accountId)
                                        val label = session.email.ifBlank { session.name.ifBlank { "account" } }
                                        Toast.makeText(
                                            context,
                                            if (stillSignedIn) "Removed $label" else "Signed out (local likes preserved)",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                )
                            }

                            Spacer(Modifier.height(14.dp))

                            ContinueWithGoogleRedCard(
                                title = "Switch Google Account or Channel",
                                subtitle = "Add another Google account while keeping existing ones",
                                subtitlePhrases = SWITCH_GOOGLE_CARD_PHRASES,
                                onClick = {
                                    haptics.play(Haptic.Tap)
                                    loginMode = WebSessionMode.SIGN_IN
                                },
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.ui_likes_added_while_signed_in_are_saved_both_locally_and),
                            color = AppleTextSecondary.copy(alpha = 0.72f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(horizontal = 10.dp),
                        )
                    }
                }

                item(key = "music_services_section") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.ui_music_services),
                            color = Color.White.copy(alpha = 0.56f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.4.sp,
                            modifier = Modifier.padding(start = 12.dp, bottom = 7.dp),
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ProfileCardGrey),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    haptics.play(Haptic.Tap)
                                    onOpenSpotify()
                                }.padding(horizontal = 16.dp, vertical = 15.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Image(
                                        painter = painterResource(R.drawable.ic_spotify),
                                        contentDescription = stringResource(R.string.ui_spotify),
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.ui_spotify_canvas), color = Color.White, fontSize = 15.5.sp, fontWeight = FontWeight.Medium)
                                    val spotifySub = if (settingsState.spotifySpdcToken.isNotBlank()) {
                                        "Connected · Canvas, Playlists & Import"
                                    } else {
                                        "Optional · Spotify Canvas, playlists & import"
                                    }
                                    Text(spotifySub, color = AppleTextSecondary, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(
                                    if (settingsState.spotifySpdcToken.isNotBlank()) "Manage" else "Connect",
                                    color = if (settingsState.spotifySpdcToken.isNotBlank()) Color(0xFF1DB954) else AppleMusicRed,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(start = 58.dp), thickness = 0.5.dp, color = ProfileDivider)
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (LastFmIntegration.username.isNotBlank()) {
                                        LastFmIntegration.signOut(context)
                                    } else {
                                        showLastFmSignIn = true
                                    }
                                }.padding(horizontal = 16.dp, vertical = 15.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFD51007)), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.ui_l), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.ui_last_fm_scrobbling), color = Color.White, fontSize = 15.5.sp, fontWeight = FontWeight.Medium)
                                    Text(LastFmIntegration.status, color = AppleTextSecondary, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(
                                    if (LastFmIntegration.username.isNotBlank()) "Disconnect" else "Connect",
                                    color = AppleMusicRed,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(start = 58.dp), thickness = 0.5.dp, color = ProfileDivider)
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (DiscordPresenceManager.username.isBlank()) showDiscordLogin = true
                                    else DiscordPresenceManager.setPresenceEnabled(!DiscordPresenceManager.enabled)
                                }.padding(horizontal = 16.dp, vertical = 15.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF5865F2)), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.ui_d), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.ui_discord_rich_presence), color = Color.White, fontSize = 15.5.sp, fontWeight = FontWeight.Medium)
                                    val subtitle = when {
                                        DiscordPresenceManager.username.isBlank() -> "Optional · Show what you’re listening to"
                                        DiscordPresenceManager.enabled -> "On · ${DiscordPresenceManager.displayName}"
                                        else -> "Off · ${DiscordPresenceManager.displayName}"
                                    }
                                    Text(subtitle, color = AppleTextSecondary, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(
                                    when {
                                        DiscordPresenceManager.username.isBlank() -> "Connect"
                                        DiscordPresenceManager.enabled -> "On"
                                        else -> "Off"
                                    },
                                    color = if (DiscordPresenceManager.enabled || DiscordPresenceManager.username.isBlank()) AppleMusicRed else AppleTextSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            if (DiscordPresenceManager.username.isNotBlank()) {
                                HorizontalDivider(modifier = Modifier.padding(start = 58.dp), thickness = 0.5.dp, color = ProfileDivider)
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        DiscordPresenceManager.disconnect()
                                        Toast.makeText(context, context.getString(R.string.toast_discord_disconnected), Toast.LENGTH_SHORT).show()
                                    }.padding(start = 58.dp, end = 16.dp, top = 11.dp, bottom = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(stringResource(R.string.ui_disconnect_discord), color = AppleMusicRed, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                item(key = "advanced_google_login") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Text(
                            text = stringResource(R.string.ui_advanced_login),
                            color = Color.White.copy(alpha = 0.56f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.4.sp,
                            modifier = Modifier.padding(start = 12.dp, bottom = 7.dp),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ProfileCardGrey)
                                .clickable { haptics.play(Haptic.Tap); showAdvancedCookieLogin = true }
                                .padding(horizontal = 16.dp, vertical = 15.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF3A3A3C)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.ui_advanced_cookie_login), color = Color.White, fontSize = 15.5.sp, fontWeight = FontWeight.Medium)
                                Text(stringResource(R.string.ui_connect_youtube_music_with_session_cookies), color = AppleTextSecondary, fontSize = 12.5.sp)
                            }
                            Icon(SfIcons.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.36f), modifier = Modifier.size(13.dp))
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
                backButtonHazeState = hazeState,
                onBack = onBack,
                modifier = Modifier.align(Alignment.TopCenter),
                centerContent = {
                    if (isScrolled) {
                        FrostedTopBarTitlePill(hazeState = hazeState) {
                            Text(
                                text = stringResource(R.string.ui_account),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                },
            )

            BottomFadeScrim(
                withMiniPlayer = hasMiniPlayer,
                pageColor = AppleOledBlack,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        loginMode?.let { mode ->
            GoogleLoginScreen(
                mode = mode,
                initialCookie = if (mode == WebSessionMode.SWITCH_CHANNEL) GoogleAuthStore.activeCookie() else null,
                onSignedIn = {
                    val toastMsg = if (mode == WebSessionMode.SWITCH_CHANNEL) {
                        "Channel switched • Loading feed..."
                    } else {
                        "Google account added • Loading feed..."
                    }
                    loginMode = null
                    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                },
                onClose = { loginMode = null },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (showAdvancedCookieLogin) {
            AdvancedGoogleCookieLoginDialog(
                initialCookie = GoogleAuthStore.activeCookie().orEmpty(),
                busy = advancedCookieLoginBusy,
                onDismiss = { if (!advancedCookieLoginBusy) showAdvancedCookieLogin = false },
                onSave = { cookie, visitorData ->
                    if (!looksSignedInCookies(cookie)) {
                        Toast.makeText(context, context.getString(R.string.toast_enter_a_valid_signed_in_youtube_music_cookie_sapis), Toast.LENGTH_LONG).show()
                    } else {
                        advancedCookieLoginBusy = true
                        integrationScope.launch {
                            val attempt = runCatching {
                                withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    verifyAndPersistCapturedSession(
                                        context.applicationContext,
                                        CapturedSession(
                                            cookie = cookie,
                                            dataSyncId = null,
                                            pageId = null,
                                            authUser = null,
                                            visitorData = visitorData.takeIf { it.isNotBlank() },
                                            clientVersion = null,
                                            loggedIn = true,
                                        ),
                                        WebSessionMode.SIGN_IN,
                                    )
                                }
                            }
                            advancedCookieLoginBusy = false
                            if (attempt.isSuccess) {
                                showAdvancedCookieLogin = false
                                Toast.makeText(context, context.getString(R.string.toast_youtube_music_account_connected), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, attempt.exceptionOrNull()?.message ?: context.getString(R.string.cookie_login_failed), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
            )
        }

        if (showLastFmSignIn) {
            LastFmSignInDialog(
                busy = lastFmBusy,
                onDismiss = { if (!lastFmBusy) showLastFmSignIn = false },
                onBrowserSignIn = {
                    integrationScope.launch {
                        LastFmIntegration.beginSignIn(context).onFailure {
                            Toast.makeText(context, it.message ?: context.getString(R.string.could_not_open_last_fm), Toast.LENGTH_LONG).show()
                        }
                        showLastFmSignIn = false
                    }
                },
                onDirectSignIn = { username, password ->
                    lastFmBusy = true
                    integrationScope.launch {
                        val result = LastFmIntegration.directSignIn(context, username, password)
                        lastFmBusy = false
                        if (result.isSuccess) {
                            showLastFmSignIn = false
                            Toast.makeText(context, context.getString(R.string.last_fm_connected_as, result.getOrNull().orEmpty()), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, result.exceptionOrNull()?.message ?: context.getString(R.string.last_fm_sign_in_failed), Toast.LENGTH_LONG).show()
                        }
                    }
                },
            )
        }

        if (showDiscordLogin) {
            DiscordLoginDialog(
                busy = discordLoginBusy,
                onDismiss = { if (!discordLoginBusy) showDiscordLogin = false },
                onToken = { token ->
                    discordLoginBusy = true
                    integrationScope.launch {
                        val result = DiscordPresenceManager.connect(token)
                        discordLoginBusy = false
                        if (result.isSuccess) {
                            showDiscordLogin = false
                            Toast.makeText(context, context.getString(R.string.discord_connected_as, result.getOrNull().orEmpty()), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, result.exceptionOrNull()?.message ?: context.getString(R.string.discord_sign_in_failed), Toast.LENGTH_LONG).show()
                        }
                    }
                },
            )
        }

        if (showAccountActionMenu && accountActionTarget != null) {
            AccountActionMenuDialog(
                account = accountActionTarget!!,
                canDelete = accountsList.size > 1,
                onDismiss = {
                    showAccountActionMenu = false
                    accountActionTarget = null
                },
                onRename = {
                    showAccountActionMenu = false
                    showRenameAccountDialog = true
                },
                onDelete = {
                    showAccountActionMenu = false
                    showDeleteAccountConfirmDialog = true
                }
            )
        }

        if (showRenameAccountDialog && accountActionTarget != null) {
            AccountRenameDialog(
                account = accountActionTarget!!,
                onDismiss = {
                    showRenameAccountDialog = false
                    accountActionTarget = null
                },
                onConfirm = { newName ->
                    showRenameAccountDialog = false
                    val target = accountActionTarget
                    if (target != null && newName.isNotBlank()) {
                        haptics.play(Haptic.Select)
                        MultiAccountManager.updateAccountProfile(context, target.id, newName.trim(), target.avatarId)
                        if (target.id == activeAccount?.id) {
                            SettingsStore.setString(context, SettingsStore.KEY_DISPLAY_NAME, newName.trim())
                            IShareSyncManager.setDisplayName(newName.trim())
                        }
                        Toast.makeText(context, context.getString(R.string.toast_account_renamed), Toast.LENGTH_SHORT).show()
                    }
                    accountActionTarget = null
                }
            )
        }

        if (showDeleteAccountConfirmDialog && accountActionTarget != null) {
            AccountDeleteConfirmDialog(
                account = accountActionTarget!!,
                onDismiss = {
                    showDeleteAccountConfirmDialog = false
                    accountActionTarget = null
                },
                onConfirm = {
                    showDeleteAccountConfirmDialog = false
                    val target = accountActionTarget
                    if (target != null) {
                        haptics.play(Haptic.Tap)
                        val success = MultiAccountManager.deleteAccount(context, target.id)
                        if (success) {
                            Toast.makeText(context, context.getString(R.string.toast_account_deleted), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, context.getString(R.string.toast_cannot_delete_your_only_account), Toast.LENGTH_SHORT).show()
                        }
                    }
                    accountActionTarget = null
                }
            )
        }
    }
}
// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun DiscordLoginDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onToken: (String) -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var tokenCaptured by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.94f).heightIn(max = 720.dp).clip(RoundedCornerShape(26.dp))
                .background(Color.Black).border(1.dp, Color(0xFF2C2C2E), RoundedCornerShape(26.dp))
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.ui_connect_discord), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(
                if (busy) "Verifying your Discord account…" else "Sign in securely on Discord to enable listening presence.",
                color = AppleTextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp, bottom = 12.dp),
            )
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 380.dp, max = 540.dp)
                    .clip(RoundedCornerShape(16.dp)),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webView = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        // Match AirBeats' working Discord web-login client UA; Discord rejects
                        // the stock Android WebView UA on some devices and leaves a blank page.
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; SM-S921U; Build/UP1A.231005.007) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.363"
                        val tokenScript = "javascript:(function()%7Bvar%20i%3Ddocument.createElement('iframe')%3Bdocument.body.appendChild(i)%3Balert(i.contentWindow.localStorage.token.slice(1,-1))%7D)()"
                        webViewClient = object : WebViewClient() {
                            private fun captureWhenReady(view: WebView, url: String) {
                                if (url.contains("/app") || url.contains("/channels/")) {
                                    view.stopLoading()
                                    view.loadUrl(tokenScript)
                                }
                            }
                            override fun onPageFinished(view: WebView, url: String) = captureWhenReady(view, url)
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                captureWhenReady(view, request.url.toString())
                                return false
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                                if (!tokenCaptured && message.isNotBlank() && message != "error" && message != "null") {
                                    tokenCaptured = true
                                    onToken(message)
                                }
                                result.confirm()
                                return true
                            }
                        }
                        setBackgroundColor(android.graphics.Color.BLACK)
                        loadUrl("https://discord.com/login")
                    }
                },
                update = { webView = it },
            )
            Text(
                stringResource(R.string.ui_your_discord_session_token_is_stored_encrypted_on_this),
                color = AppleTextSecondary.copy(alpha = 0.8f), fontSize = 11.sp, lineHeight = 15.sp,
                modifier = Modifier.padding(top = 11.dp, bottom = 7.dp),
            )
            Text(
                stringResource(R.string.cancel), color = AppleTextSecondary, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.End).clip(RoundedCornerShape(8.dp)).clickable(enabled = !busy) {
                    webView?.loadUrl("about:blank")
                    onDismiss()
                }.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun LastFmSignInDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onBrowserSignIn: () -> Unit,
    onDirectSignIn: (username: String, password: String) -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.9f).clip(RoundedCornerShape(26.dp)).background(Color.Black)
                .border(1.dp, Color(0xFF2C2C2E), RoundedCornerShape(26.dp)).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(54.dp).clip(CircleShape).background(Color(0xFFD51007)), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.ui_l), color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.ui_connect_last_fm), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.ui_sign_in_with_your_last_fm_account), color = AppleTextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 15.dp))
            CookieInput("Username", username, { username = it })
            CookieInput("Password", password, { password = it }, secure = true)
            Text(
                stringResource(R.string.ui_your_password_is_sent_to_last_fm_only_to_create_a_sess),
                color = AppleTextSecondary.copy(alpha = 0.8f), fontSize = 11.5.sp, lineHeight = 16.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
            )
            Spacer(Modifier.height(17.dp))
            CookieDialogAction(if (busy) "Signing in…" else "Sign In", primary = true, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                onDirectSignIn(username.trim(), password)
            }
            Text(
                stringResource(R.string.ui_continue_in_browser_instead),
                color = Color(0xFF0A84FF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 14.dp).clip(RoundedCornerShape(8.dp)).clickable(enabled = !busy, onClick = onBrowserSignIn).padding(7.dp),
            )
            Text(
                stringResource(R.string.cancel), color = AppleTextSecondary, fontSize = 14.sp,
                modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(8.dp)).clickable(enabled = !busy, onClick = onDismiss).padding(7.dp),
            )
        }
    }
}

@Composable
private fun AdvancedGoogleCookieLoginDialog(
    initialCookie: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (cookie: String, visitorData: String) -> Unit,
) {
    val initial = remember(initialCookie) { com.darkxvenom.itune.data.auth.parseCookieHeader(initialCookie) }
    var rawMode by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var sapisid by remember { mutableStateOf(initial["SAPISID"] ?: initial["__Secure-3PAPISID"].orEmpty()) }
    var hsid by remember { mutableStateOf(initial["HSID"].orEmpty()) }
    var ssid by remember { mutableStateOf(initial["SSID"].orEmpty()) }
    var sid by remember { mutableStateOf(initial["SID"].orEmpty()) }
    var loginInfo by remember { mutableStateOf(initial["LOGIN_INFO"].orEmpty()) }
    var rawCookie by remember { mutableStateOf(initialCookie) }
    var visitorData by remember { mutableStateOf("") }
    val cookie = remember(sapisid, hsid, ssid, sid, loginInfo, rawMode, rawCookie) {
        if (rawMode) rawCookie.trim() else buildList {
            if (sapisid.isNotBlank()) add("SAPISID=${sapisid.trim()}")
            if (hsid.isNotBlank()) add("HSID=${hsid.trim()}")
            if (ssid.isNotBlank()) add("SSID=${ssid.trim()}")
            if (sid.isNotBlank()) add("SID=${sid.trim()}")
            if (loginInfo.isNotBlank()) add("LOGIN_INFO=${loginInfo.trim()}")
        }.joinToString("; ")
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier.fillMaxWidth(0.92f).heightIn(max = 700.dp).clip(RoundedCornerShape(26.dp))
                .background(Color.Black).border(1.dp, Color(0xFF2C2C2E), RoundedCornerShape(26.dp))
                .verticalScroll(rememberScrollState()).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(54.dp).clip(CircleShape).background(Color(0xFF1C1C1E)), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.ui_g), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.ui_advanced_login_3fde9a4d), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.ui_youtube_music_session_cookies), color = AppleTextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF111113)).padding(14.dp)) {
                Row(
                    Modifier.fillMaxWidth().clickable { showHelp = !showHelp },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.ui_how_to_get_your_cookies), color = Color(0xFF0A84FF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (showHelp) "⌃" else "⌄", color = AppleTextSecondary, fontSize = 18.sp)
                }
                if (showHelp) {
                    Text(
                        stringResource(R.string.ui_sign_in_to_music_youtube_com_in_a_desktop_browser_open),
                        color = AppleTextSecondary, fontSize = 12.5.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 9.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.End) {
                Text(
                    if (rawMode) "Use individual fields" else "Use raw cookie",
                    color = Color(0xFF0A84FF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { rawMode = !rawMode }.padding(8.dp),
                )
            }
            if (rawMode) {
                CookieInput("InnerTube cookie", rawCookie, { rawCookie = it }, multiline = true)
            } else {
                CookieInput("SAPISID / __Secure-3PAPISID", sapisid, { sapisid = it })
                CookieInput("HSID", hsid, { hsid = it })
                CookieInput("SSID", ssid, { ssid = it })
                CookieInput("SID", sid, { sid = it })
                CookieInput("LOGIN_INFO", loginInfo, { loginInfo = it })
            }
            CookieInput("Visitor Data (optional)", visitorData, { visitorData = it })
            Text(
                stringResource(R.string.ui_cookie_credentials_are_stored_in_itunes_encrypted_acco),
                color = AppleTextSecondary.copy(alpha = 0.8f), fontSize = 11.5.sp, lineHeight = 16.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
            )
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CookieDialogAction("Cancel", enabled = !busy, modifier = Modifier.weight(1f), onClick = onDismiss)
                CookieDialogAction(if (busy) "Checking…" else "Connect", primary = true, enabled = !busy, modifier = Modifier.weight(1f)) {
                    onSave(cookie, visitorData.trim())
                }
            }
        }
    }
}

@Composable
private fun CookieInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    multiline: Boolean = false,
    secure: Boolean = false,
) {
    Column(Modifier.fillMaxWidth().padding(top = 7.dp)) {
        Text(label, color = AppleTextSecondary, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, bottom = 5.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = !multiline,
            visualTransformation = if (secure) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            cursorBrush = SolidColor(Color(0xFF0A84FF)),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF1C1C1E)).padding(12.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(stringResource(R.string.ui_enter, label), color = Color.White.copy(alpha = 0.3f), fontSize = 14.sp)
                    inner()
                }
            },
        )
    }
}

@Composable
private fun CookieDialogAction(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier.height(46.dp).clip(RoundedCornerShape(13.dp))
            .background(if (primary) Color(0xFF0A84FF).copy(alpha = if (enabled) 1f else 0.45f) else Color(0xFF1C1C1E))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (primary) Color.White else AppleTextSecondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

private val DEFAULT_GOOGLE_CARD_PHRASES = listOf(
    "Sync your YouTube Music playlists",
    "Sync liked songs, artists & history",
    "Get your personalized YouTube Music feed",
)

private val SWITCH_GOOGLE_CARD_PHRASES = listOf(
    "Add another Google account",
    "Keep existing accounts & switch feeds",
    "Switch YouTube Music channels anytime",
)

/**
 * Red Apple Settings-styled "Continue with Google" card with Google G logo and
 * animated typewriter headline + cycling benefits copy.
 */
@Composable
fun ContinueWithGoogleRedCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Continue with Google",
    subtitle: String = "Sync your YouTube Music account",
    subtitlePhrases: List<String>? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsStateOrStatic(pressed)
    val resolvedPhrases = remember(subtitle, subtitlePhrases) {
        subtitlePhrases?.takeIf { it.isNotEmpty() } ?: listOf(subtitle)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(GoogleRedStart, GoogleRedEnd),
                ),
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_google_logo),
                    contentDescription = stringResource(R.string.ui_google),
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                CardTypewriterLine(
                    phrases = listOf(title),
                    loop = false,
                    color = Color.White,
                    fontSize = 16.5.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
                CardTypewriterLine(
                    phrases = resolvedPhrases,
                    loop = true,
                    startDelayMs = 420L,
                    holdMs = 2600L,
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 2,
                    modifier = Modifier.heightIn(min = 18.dp),
                )
            }

            Spacer(Modifier.width(8.dp))

            Icon(
                imageVector = SfIcons.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.88f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun CardTypewriterLine(
    phrases: List<String>,
    loop: Boolean,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight,
    maxLines: Int,
    modifier: Modifier = Modifier,
    startDelayMs: Long = 120L,
    holdMs: Long = 2600L,
) {
    val cleanPhrases = remember(phrases) { phrases.ifEmpty { listOf("") } }
    var phraseIdx by remember(cleanPhrases) { mutableIntStateOf(0) }
    var charCount by remember(cleanPhrases) { mutableIntStateOf(0) }
    var cursorActive by remember(cleanPhrases) { mutableStateOf(true) }
    var activelyTyping by remember(cleanPhrases) { mutableStateOf(false) }
    var textLayout by remember(cleanPhrases) { mutableStateOf<TextLayoutResult?>(null) }

    val activeTarget = cleanPhrases[phraseIdx.coerceIn(cleanPhrases.indices)]

    LaunchedEffect(cleanPhrases, loop, startDelayMs, holdMs) {
        phraseIdx = 0
        charCount = 0
        cursorActive = true
        activelyTyping = false
        if (startDelayMs > 0L) delay(startDelayMs)

        while (true) {
            val target = cleanPhrases[phraseIdx.coerceIn(cleanPhrases.indices)]
            cursorActive = true
            activelyTyping = true
            for (i in 0 until target.length) {
                delay(welcomeTypingKeystrokeDelayMs(target, i))
                charCount = i + 1
            }
            activelyTyping = false
            delay(260L)
            cursorActive = false

            if (!loop) break
            delay(holdMs)

            cursorActive = true
            delay(180L)
            activelyTyping = true
            for (deleted in 0 until target.length) {
                delay(welcomeBackspaceDelayMs(deleted))
                charCount = (target.length - 1 - deleted).coerceAtLeast(0)
            }
            activelyTyping = false
            delay(160L)
            phraseIdx = (phraseIdx + 1) % cleanPhrases.size
        }
    }

    val blinkTransition = rememberInfiniteTransition(label = "cardTypewriterBlink")
    val blinkAlpha by blinkTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 440, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cardCaretBlink",
    )
    val cursorVisibility by animateFloatAsState(
        targetValue = if (cursorActive) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "cardCaretVisibility",
    )

    val visibleText = activeTarget.take(charCount.coerceIn(0, activeTarget.length))

    Text(
        text = visibleText,
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { textLayout = it },
        modifier = modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                val alpha = ((if (activelyTyping) 1f else blinkAlpha) * cursorVisibility)
                    .coerceIn(0f, 1f)
                if (alpha > 0.01f) {
                    val caretW = 1.8.dp.toPx()
                    val caretGap = 2.dp.toPx()
                    val layout = textLayout
                    val lastLine = ((layout?.lineCount ?: 1) - 1).coerceAtLeast(0)
                    val caretX = if (visibleText.isEmpty() || layout == null || layout.lineCount == 0) {
                        0.5.dp.toPx()
                    } else {
                        (layout.getLineRight(lastLine) + caretGap).coerceAtMost(size.width - caretW)
                    }
                    val top = if (layout != null && layout.lineCount > 0 && visibleText.isNotEmpty()) {
                        layout.getLineTop(lastLine)
                    } else {
                        0f
                    }
                    val bottom = if (layout != null && layout.lineCount > 0 && visibleText.isNotEmpty()) {
                        layout.getLineBottom(lastLine)
                    } else {
                        lineHeight.toPx()
                    }
                    val lineH = (bottom - top).coerceAtLeast(14.dp.toPx())
                    val caretH = minOf(fontSize.toPx() * 1.05f, lineH * 0.82f)
                    val caretTop = top + (lineH - caretH) / 2f
                    drawRoundRect(
                        color = color.copy(alpha = alpha),
                        topLeft = Offset(caretX, caretTop),
                        size = Size(caretW, caretH),
                        cornerRadius = CornerRadius(caretW / 2f, caretW / 2f),
                    )
                }
            },
    )
}

@Composable
private fun SavedAccountsAndChannelsCard(
    accounts: List<GoogleAccountSession>,
    activeAccountId: String?,
    activeProfileId: String?,
    onSelectProfile: (GoogleAccountSession, YouTubeProfile) -> Unit,
    onRemoveAccount: (GoogleAccountSession) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ProfileCardGrey),
    ) {
        accounts.forEachIndexed { accIndex, account ->
            if (accIndex > 0) {
                HorizontalDivider(
                    thickness = 0.7.dp,
                    color = ProfileDivider,
                )
            }
            val profiles = account.profiles.ifEmpty {
                listOf(
                    YouTubeProfile(
                        profileId = account.activeProfileId ?: account.accountId,
                        name = account.name.ifBlank { "Google Account" },
                        handle = account.email,
                    ),
                )
            }
            if (accounts.size > 1 && profiles.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = account.email.ifBlank { account.name.ifBlank { "Google Account" } },
                        color = AppleTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { onRemoveAccount(account) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.ui_remove_account),
                            tint = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            profiles.forEachIndexed { profileIndex, profile ->
                if (profileIndex > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 66.dp),
                        thickness = 0.5.dp,
                        color = ProfileDivider,
                    )
                }
                val isSelected = account.accountId == activeAccountId &&
                    (profile.profileId == activeProfileId || (activeProfileId == null && profileIndex == 0))
                SavedProfileSwitchRow(
                    session = account,
                    profile = profile,
                    selected = isSelected,
                    showInlineRemove = accounts.size > 1 && profiles.size == 1,
                    onClick = { onSelectProfile(account, profile) },
                    onRemoveAccount = { onRemoveAccount(account) },
                )
            }
        }
    }
}

@Composable
private fun SavedProfileSwitchRow(
    session: GoogleAccountSession,
    profile: YouTubeProfile,
    selected: Boolean,
    showInlineRemove: Boolean,
    onClick: () -> Unit,
    onRemoveAccount: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = if (showInlineRemove) 8.dp else 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White)
                .then(
                    if (selected) Modifier.border(1.8.dp, AppleMusicRed, CircleShape) else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (!profile.avatar.isNullOrBlank()) {
                AsyncImage(
                    model = profile.avatar,
                    contentDescription = profile.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_google_logo),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.name.ifBlank { session.name.ifBlank { "Google Account" } },
                color = Color.White,
                fontSize = 15.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = buildString {
                val base = profile.handle.ifBlank { session.email }.ifBlank { "YouTube Music" }
                append(base)
                if (profile.isBrandAccount) append(" • Channel")
            }
            Text(
                text = sub,
                color = AppleTextSecondary,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (selected) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF34C759).copy(alpha = 0.16f))
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.ui_active),
                        tint = Color(0xFF34C759),
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = stringResource(R.string.ui_active),
                        color = Color(0xFF34C759),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        } else {
            Text(
                text = stringResource(R.string.ui_switch),
                color = AppleMusicRed,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (showInlineRemove) {
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onRemoveAccount,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.ui_remove_account),
                    tint = Color.White.copy(alpha = 0.44f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun animateFloatAsStateOrStatic(pressed: Boolean) =
    androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
        ),
        label = "googleCardScale",
    )

// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

@Composable
private fun AccountActionMenuDialog(
    account: UserAccount,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() }
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* absorb inner click */ }
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF141416))
                    .border(1.dp, Color(0xFF2C2C2E), RoundedCornerShape(22.dp))
                    .padding(vertical = 18.dp, horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Account Avatar & Name Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppleMemojiAvatar(
                        size = 38.dp,
                        avatarId = account.avatarId,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = account.displayName,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.ui_account_options),
                            color = AppleTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)
                Spacer(Modifier.height(6.dp))

                // Rename Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onRename() }
                        .padding(horizontal = 10.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.ui_rename),
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = stringResource(R.string.ui_rename),
                        color = Color.White,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Delete Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = canDelete) { onDelete() }
                        .padding(horizontal = 10.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = if (canDelete) AppleMusicRed else AppleMusicRed.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.delete),
                            color = if (canDelete) AppleMusicRed else AppleMusicRed.copy(alpha = 0.4f),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (!canDelete) {
                            Text(
                                text = stringResource(R.string.ui_cannot_delete_only_account),
                                color = AppleTextSecondary.copy(alpha = 0.7f),
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)
                Spacer(Modifier.height(8.dp))

                // Cancel
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onDismiss() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

@Composable
private fun AccountRenameDialog(
    account: UserAccount,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(account.displayName) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() }
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* absorb */ }
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF141416))
                    .border(1.dp, Color(0xFF2C2C2E), RoundedCornerShape(22.dp))
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.ui_rename_account),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.ui_enter_a_new_name_for_this_account),
                    color = AppleTextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(18.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E1E22))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        cursorBrush = SolidColor(AppleMusicRed),
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (text.isNotBlank()) onConfirm(text.trim())
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(22.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cancel
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Save
                    val canSave = text.isNotBlank()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (canSave) AppleMusicRed else AppleMusicRed.copy(alpha = 0.4f))
                            .clickable(enabled = canSave) {
                                onConfirm(text.trim())
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.save),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

@Composable
private fun AccountDeleteConfirmDialog(
    account: UserAccount,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() }
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* absorb */ }
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF141416))
                    .border(1.dp, Color(0xFF2C2C2E), RoundedCornerShape(22.dp))
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Red trash icon container
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(AppleMusicRed.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = AppleMusicRed,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = stringResource(R.string.ui_delete_account_9bce384b),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.ui_are_you_sure_you_want_to_delete_all_data_playlists, account.displayName),
                    color = AppleTextSecondary,
                    fontSize = 13.5.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )

                Spacer(Modifier.height(24.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Delete Button (Red destructive)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppleMusicRed)
                            .clickable { onConfirm() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.ui_delete_account),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Cancel Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
