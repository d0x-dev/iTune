package com.darkxvenom.itune.ui.settings

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Sync
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.darkxvenom.itune.R
import com.darkxvenom.itune.data.auth.GoogleAccountSession
import com.darkxvenom.itune.data.auth.GoogleAuthStore
import com.darkxvenom.itune.data.auth.WebSessionMode
import com.darkxvenom.itune.data.auth.YouTubeLibrarySyncManager
import com.darkxvenom.itune.data.auth.YouTubeProfile
import com.darkxvenom.itune.data.library.SettingsStore
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val haptics = rememberHaptics()
    val settingsState by SettingsStore.state.collectAsStateWithLifecycle()
    val ytAccount by GoogleAuthStore.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        GoogleAuthStore.ensureInit(context)
    }

    var nameInput by remember(settingsState.displayName) { mutableStateOf(settingsState.displayName) }
    val cleanName = nameInput.trim()
    val nameDirty = cleanName.isNotEmpty() && cleanName != settingsState.displayName

    var loginMode by remember { mutableStateOf<WebSessionMode?>(null) }

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
        focusManager.clearFocus()
        Toast.makeText(context, "Name saved", Toast.LENGTH_SHORT).show()
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
                        text = "Account",
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
                                    contentDescription = "Choose Avatar",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Tap avatar to customize",
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
                            text = "PROFILE",
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
                                    text = "Name",
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
                                                    text = "Your Name",
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
                                                contentDescription = "Save name",
                                                tint = Color.White,
                                                modifier = Modifier.size(13.dp),
                                            )
                                            Spacer(Modifier.width(3.dp))
                                            Text(
                                                text = "Save",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Editable Name",
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
                                    text = "Avatar",
                                    color = Color.White,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "Memoji & Style",
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

                // Google / YouTube Music Card Section
                item(key = "google_account_section") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Text(
                            text = "YOUTUBE MUSIC & GOOGLE",
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
                                                contentDescription = "Google",
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
                                                text = "Channel: ${ytAccount.channelName}",
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
                                            Toast.makeText(context, "Syncing YouTube Music library...", Toast.LENGTH_SHORT).show()
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
                                            text = "Choose YouTube Channel",
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
                                                Toast.makeText(context, "Signed out (local likes preserved)", Toast.LENGTH_SHORT).show()
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
                                                Toast.makeText(context, "Signed out of all Google accounts", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Spacer(Modifier.width(41.dp))
                                        Text(
                                            text = "Sign Out All Accounts",
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
                                            Toast.makeText(context, "Switched feed to $label", Toast.LENGTH_SHORT).show()
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
                            text = "Likes added while signed in are saved both locally and on YouTube Music, so they remain in your Library even after signing out.",
                            color = AppleTextSecondary.copy(alpha = 0.72f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(horizontal = 10.dp),
                        )
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
                                text = "Account",
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
                    contentDescription = "Google",
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
                            contentDescription = "Remove account",
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
                        contentDescription = "Active",
                        tint = Color(0xFF34C759),
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "Active",
                        color = Color(0xFF34C759),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        } else {
            Text(
                text = "Switch",
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
                    contentDescription = "Remove account",
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
