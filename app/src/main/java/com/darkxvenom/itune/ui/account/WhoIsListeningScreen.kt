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

package com.darkxvenom.itune.ui.account

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkxvenom.itune.R
import com.darkxvenom.itune.data.account.UserAccount
import com.darkxvenom.itune.ui.components.AppleMemojiAvatar
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import com.darkxvenom.itune.ui.welcome.WelcomeMeshBackground
import com.darkxvenom.itune.ui.welcome.sampleWelcomeAmbientFrame
import com.darkxvenom.itune.ui.welcome.welcomeBackspaceDelayMs
import com.darkxvenom.itune.ui.welcome.welcomeTypingKeystrokeDelayMs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI

private enum class ListeningPhase {
    CHOOSING,
    FOCUS_SELECTED,
    ERASE_NAME,
    TYPE_WELCOME,
    HIDE_PROFILE,
    TRAVEL_TO_CENTER,
    SHOW_ITUNE_CENTER,
    WAIT_BEFORE_AIRBEATS,
    TYPING_AIRBEATS,
    TYPING_BACK_ITUNE,
    REVEAL_HOME_DIM_BG,
    TRAVEL_TO_HOME_BAR,
    DONE,
}

@Composable
fun WhoIsListeningScreen(
    accounts: List<UserAccount>,
    homeIconBounds: Rect? = null,
    homeTitleBounds: Rect? = null,
    onRevealHomeUnderlay: (UserAccount) -> Unit = {},
    onAccountSelected: (UserAccount) -> Unit,
    onAddNewAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    var phase by remember { mutableStateOf(ListeningPhase.CHOOSING) }
    var selectedAccount by remember { mutableStateOf<UserAccount?>(null) }

    var animatedNameText by remember { mutableStateOf("") }
    var centerBrandText by remember { mutableStateOf("iTune") }

    val avatarSlideProgress = remember { Animatable(0f) }
    val avatarNameAlpha = remember { Animatable(0f) }
    val profileAlpha = remember { Animatable(1f) }
    val cornerToCenterProgress = remember { Animatable(0f) }
    val backgroundAlpha = remember { Animatable(1f) }
    val centerToHomeProgress = remember { Animatable(0f) }

    var rootSizePx by remember { mutableStateOf(IntSize.Zero) }
    var cornerIconRect by remember { mutableStateOf<Rect?>(null) }
    var cornerTitleRect by remember { mutableStateOf<Rect?>(null) }
    val accountAvatarBounds = remember { mutableStateMapOf<String, Rect>() }

    // Ambient morphing mesh loop matching WelcomeScreen
    val infiniteTransition = rememberInfiniteTransition(label = "listeningMeshLoop")
    val colorProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 35_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ambientColorCycle",
    )
    val orbitPhaseA by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 15_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbitPhaseA",
    )
    val orbitPhaseB by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 21_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbitPhaseB",
    )
    val ambient = sampleWelcomeAmbientFrame(colorProgress)

    suspend fun typeOut(target: String, update: (String) -> Unit) {
        update("")
        delay(60L)
        for (i in 0 until target.length) {
            delay(32L)
            update(target.take(i + 1))
        }
    }

    suspend fun eraseOut(source: String, update: (String) -> Unit) {
        delay(50L)
        for (deleted in 0 until source.length) {
            delay(24L)
            update(source.dropLast(deleted + 1))
        }
    }

    fun selectAccount(account: UserAccount) {
        if (phase != ListeningPhase.CHOOSING) return
        selectedAccount = account
        animatedNameText = account.displayName
        phase = ListeningPhase.FOCUS_SELECTED

        coroutineScope.launch {
            // 1. Smoothly slide the selected avatar from card position to screen center
            avatarSlideProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
            )
            avatarNameAlpha.animateTo(1f, tween(durationMillis = 140))
            delay(80L)

            // 2. Erase name with quick backspace animation
            phase = ListeningPhase.ERASE_NAME
            eraseOut(account.displayName) { animatedNameText = it }
            delay(60L)

            // 3. Write "Welcome" with snappy typing animation
            phase = ListeningPhase.TYPE_WELCOME
            typeOut("Welcome") { animatedNameText = it }
            delay(180L)

            // 4. Hide both profile and "Welcome", only keep top-left icon and top-right "iTune" text
            phase = ListeningPhase.HIDE_PROFILE
            profileAlpha.animateTo(0f, tween(durationMillis = 200))
            avatarNameAlpha.animateTo(0f, tween(durationMillis = 150))
            delay(60L)

            // 5. Bring them both from the corners to middle of screen (Icon ABOVE, below it text)
            phase = ListeningPhase.TRAVEL_TO_CENTER
            centerBrandText = "iTune"
            cornerToCenterProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            )

            // 6. Centered "iTune" -> erase "iTune" -> type "AirBeats" -> erase -> type "iTune"
            phase = ListeningPhase.SHOW_ITUNE_CENTER
            delay(100L)
            eraseOut("iTune") { centerBrandText = it }

            phase = ListeningPhase.WAIT_BEFORE_AIRBEATS
            delay(120L)

            phase = ListeningPhase.TYPING_AIRBEATS
            typeOut("AirBeats") { centerBrandText = it }
            delay(220L)
            eraseOut("AirBeats") { centerBrandText = it }
            delay(80L)

            phase = ListeningPhase.TYPING_BACK_ITUNE
            typeOut("iTune") { centerBrandText = it }
            delay(120L)

            // 7. Hide background, revealing home screen underneath while traveling to home top bar
            phase = ListeningPhase.REVEAL_HOME_DIM_BG
            onRevealHomeUnderlay(account)
            launch {
                backgroundAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                )
            }

            // 8. Send both icon and title from center to home screen top bar
            phase = ListeningPhase.TRAVEL_TO_HOME_BAR
            centerToHomeProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
            )

            phase = ListeningPhase.DONE
            onAccountSelected(account)
        }
    }
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { rootSizePx = it.size },
    ) {
        // Morphing Ambient Mesh Background with dynamic fade-out revealing HomeScreen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = backgroundAlpha.value }
                .background(AppleOledBlack),
        ) {
            WelcomeMeshBackground(
                ambient = ambient,
                orbitPhaseA = orbitPhaseA,
                orbitPhaseB = orbitPhaseB,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // ── TOP CORNER HEADER ──
        // Splash middle icon goes to top-left corner, "iTune" brand goes to top-right corner
        val isBrandTraveling = phase.ordinal >= ListeningPhase.TRAVEL_TO_CENTER.ordinal

        AnimatedVisibility(
            visible = !isBrandTraveling,
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(200)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Top-left ic_itune_waves XML icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .onGloballyPositioned { coords -> cornerIconRect = coords.boundsInRoot() },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_itune_waves),
                        contentDescription = "iTune Waves",
                        modifier = Modifier.fillMaxSize(),
                        colorFilter = ColorFilter.tint(Color.White),
                    )
                }

                // Top-right "iTune" wordmark
                Text(
                    text = "iTune",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    fontFamily = FontFamily(Font(R.font.sf_pro_display_bold)),
                    modifier = Modifier.onGloballyPositioned { coords -> cornerTitleRect = coords.boundsInRoot() },
                )
            }
        }

        // ── MIDDLE: "Who is Listening?" and Accounts Selection Row ──
        if (phase == ListeningPhase.CHOOSING) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Who is Listening?",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.6).sp,
                    fontFamily = FontFamily(Font(R.font.sf_pro_display_bold)),
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "Choose your profile to continue",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(36.dp))

                // Account Avatars Row
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(accounts, key = { it.id }) { acc ->
                        AccountPickerCard(
                            account = acc,
                            onBoundsChanged = { rect -> accountAvatarBounds[acc.id] = rect },
                            onClick = { selectAccount(acc) },
                        )
                    }

                    // "+ Add Account" option
                    item(key = "add_account_button") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(96.dp)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) { onAddNewAccount() },
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .border(1.2.dp, Color.White.copy(alpha = 0.28f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Add Account",
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp),
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Add Account",
                                color = Color.White.copy(alpha = 0.82f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        // ── FOCUSED PROFILE IN MIDDLE: SLIDES FROM CARD POSITION TO CENTER ──
        if (phase in ListeningPhase.FOCUS_SELECTED..ListeningPhase.HIDE_PROFILE && selectedAccount != null) {
            val acc = selectedAccount!!
            val rootW = rootSizePx.width.toFloat()
            val rootH = rootSizePx.height.toFloat()

            if (rootW > 0f && rootH > 0f) {
                val slideT = avatarSlideProgress.value
                val startC = accountAvatarBounds[acc.id]?.center ?: Offset(rootW / 2f, rootH * 0.5f)
                val startS = accountAvatarBounds[acc.id]?.width ?: with(density) { 76.dp.toPx() }
                val endC = Offset(rootW / 2f, rootH * 0.44f)
                val endS = with(density) { 104.dp.toPx() }

                val curC = Offset(
                    androidx.compose.ui.util.lerp(startC.x, endC.x, slideT),
                    androidx.compose.ui.util.lerp(startC.y, endC.y, slideT),
                )
                val curS = androidx.compose.ui.util.lerp(startS, endS, slideT)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = profileAlpha.value },
                ) {
                    // Sliding avatar
                    Box(
                        modifier = Modifier
                            .size(with(density) { curS.toDp() })
                            .graphicsLayer {
                                translationX = curC.x - curS / 2f
                                translationY = curC.y - curS / 2f
                            }
                            .clip(CircleShape)
                            .border(2.5.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppleMemojiAvatar(
                            size = with(density) { (curS - with(density) { 8.dp.toPx() }).coerceAtLeast(0f).toDp() },
                            avatarId = acc.avatarId,
                        )
                    }

                    // Name / "Welcome" below avatar
                    if (avatarNameAlpha.value > 0.01f) {
                        Text(
                            text = animatedNameText,
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.4).sp,
                            fontFamily = FontFamily(Font(R.font.sf_pro_display_bold)),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier
                                .graphicsLayer {
                                    alpha = avatarNameAlpha.value * profileAlpha.value
                                    translationX = endC.x - size.width / 2f
                                    translationY = endC.y + endS / 2f + with(density) { 16.dp.toPx() }
                                },
                        )
                    }
                }
            }
        }

        // ── TRAVEL TO CENTER & TRAVEL TO HOME BAR ANIMATION ──
        // Exact same lockup styling & size as SplashScreen (icon ABOVE, below it text)
        if (isBrandTraveling && rootSizePx.width > 0 && rootSizePx.height > 0) {
            val rootW = rootSizePx.width.toFloat()
            val rootH = rootSizePx.height.toFloat()

            // 1. Center hero dimensions matching SplashScreen exactly
            val centerIconPx = with(density) { 92.dp.toPx() }
            val heroGapPx = with(density) { 18.dp.toPx() }
            val baseHeroTextH = with(density) { 52.dp.toPx() }
            val totalCenterH = centerIconPx + heroGapPx + baseHeroTextH

            // Icon is ABOVE, text is BELOW it (both centered horizontally)
            val centerLockupIconCenter = Offset(
                x = rootW / 2f,
                y = (rootH - totalCenterH) / 2f + centerIconPx / 2f,
            )
            val centerLockupTitleCenter = Offset(
                x = rootW / 2f,
                y = (rootH - totalCenterH) / 2f + centerIconPx + heroGapPx + baseHeroTextH / 2f,
            )

            // Start positions: top corners
            val startIconCenter = cornerIconRect?.center ?: Offset(rootW * 0.12f, rootH * 0.08f)
            val startTitleCenter = cornerTitleRect?.center ?: Offset(rootW * 0.88f, rootH * 0.08f)

            // Target positions: Home TopHeaderBar
            val targetIconCenter = homeIconBounds?.center ?: Offset(rootW * 0.12f, rootH * 0.06f)
            val targetTitleCenter = homeTitleBounds?.center ?: Offset(rootW * 0.32f, rootH * 0.06f)

            val c2c = cornerToCenterProgress.value
            val c2h = centerToHomeProgress.value

            val currentIconCenter = if (c2h > 0.001f) {
                Offset(
                    androidx.compose.ui.util.lerp(centerLockupIconCenter.x, targetIconCenter.x, c2h),
                    androidx.compose.ui.util.lerp(centerLockupIconCenter.y, targetIconCenter.y, c2h),
                )
            } else {
                Offset(
                    androidx.compose.ui.util.lerp(startIconCenter.x, centerLockupIconCenter.x, c2c),
                    androidx.compose.ui.util.lerp(startIconCenter.y, centerLockupIconCenter.y, c2c),
                )
            }

            val currentTitleCenter = if (c2h > 0.001f) {
                Offset(
                    androidx.compose.ui.util.lerp(centerLockupTitleCenter.x, targetTitleCenter.x, c2h),
                    androidx.compose.ui.util.lerp(centerLockupTitleCenter.y, targetTitleCenter.y, c2h),
                )
            } else {
                Offset(
                    androidx.compose.ui.util.lerp(startTitleCenter.x, centerLockupTitleCenter.x, c2c),
                    androidx.compose.ui.util.lerp(startTitleCenter.y, centerLockupTitleCenter.y, c2c),
                )
            }

            val iconScale = if (c2h > 0.001f) {
                val endScale = (homeIconBounds?.width ?: with(density) { 34.dp.toPx() }) / centerIconPx
                androidx.compose.ui.util.lerp(1f, endScale, c2h)
            } else {
                val startScale = with(density) { 36.dp.toPx() } / centerIconPx
                androidx.compose.ui.util.lerp(startScale, 1f, c2c)
            }

            val titleFontSize = if (c2h > 0.001f) {
                androidx.compose.ui.util.lerp(52f, 22f, c2h).sp
            } else {
                androidx.compose.ui.util.lerp(24f, 52f, c2c).sp
            }

            // Traveling ic_itune_waves (centered icon)
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .graphicsLayer {
                        translationX = currentIconCenter.x - centerIconPx / 2f
                        translationY = currentIconCenter.y - centerIconPx / 2f
                        scaleX = iconScale
                        scaleY = iconScale
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_itune_waves),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    colorFilter = ColorFilter.tint(Color.White),
                )
            }

            // Traveling title with typing & erasing (below icon)
            Text(
                text = centerBrandText,
                color = Color.White,
                fontSize = titleFontSize,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.75).sp,
                fontFamily = FontFamily(Font(R.font.sf_pro_display_bold)),
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    translationX = currentTitleCenter.x - size.width / 2f
                    translationY = currentTitleCenter.y - size.height / 2f
                },
            )
        }
    }
}

@Composable
private fun AccountPickerCard(
    account: UserAccount,
    onBoundsChanged: (Rect) -> Unit = {},
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.75f),
        label = "account_card_press_scale"
    )

    val handleSelect = remember(account.id) {
        {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = handleSelect,
            ),
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .onGloballyPositioned { coords -> onBoundsChanged(coords.boundsInRoot()) }
                .clip(CircleShape)
                .border(
                    width = if (isPressed) 3.dp else 2.dp,
                    color = if (isPressed) Color.White else Color.White.copy(alpha = 0.5f),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = handleSelect,
                ),
            contentAlignment = Alignment.Center,
        ) {
            AppleMemojiAvatar(
                size = 70.dp,
                avatarId = account.avatarId,
                onClick = handleSelect,
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = account.displayName,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = handleSelect,
            ),
        )
    }
}
