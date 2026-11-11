package com.darkxvenom.itune.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
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
import com.darkxvenom.itune.data.auth.GoogleAuthStore
import com.darkxvenom.itune.data.auth.WebSessionMode
import com.darkxvenom.itune.data.auth.YouTubeLibrarySyncManager
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

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

                                // Sign Out
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            haptics.play(Haptic.Select)
                                            YouTubeLibrarySyncManager.signOutAndPreserveLocal(context)
                                            Toast.makeText(context, "Signed out (local likes preserved)", Toast.LENGTH_SHORT).show()
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
                                        text = "Sign Out of Google",
                                        color = AppleMusicRed,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            ContinueWithGoogleRedCard(
                                title = "Switch Google Account or Channel",
                                subtitle = "Sign in with another Google account or brand channel",
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
                initialCookie = GoogleAuthStore.activeCookie(),
                onSignedIn = {
                    loginMode = null
                    Toast.makeText(context, "Google account connected", Toast.LENGTH_SHORT).show()
                },
                onClose = { loginMode = null },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Red Apple Settings-styled "Continue with Google" card with Google G logo.
 */
@Composable
fun ContinueWithGoogleRedCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Continue with Google",
    subtitle: String = "Sync your YouTube Music account",
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsStateOrStatic(pressed)

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
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.86f),
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
private fun animateFloatAsStateOrStatic(pressed: Boolean) =
    androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
        ),
        label = "googleCardScale",
    )
