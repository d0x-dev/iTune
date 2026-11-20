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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkxvenom.itune.R
import com.darkxvenom.itune.data.account.MultiAccountManager
import com.darkxvenom.itune.data.account.UserAccount
import com.darkxvenom.itune.ui.components.AppleMemojiAvatar
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import com.darkxvenom.itune.ui.player.SFProDisplay
import com.darkxvenom.itune.ui.welcome.WelcomeMeshBackground
import com.darkxvenom.itune.ui.welcome.sampleWelcomeAmbientFrame
import com.darkxvenom.itune.ui.welcome.welcomeBackspaceDelayMs
import com.darkxvenom.itune.ui.welcome.welcomeTypingKeystrokeDelayMs
import kotlinx.coroutines.delay
import kotlin.math.PI

/**
 * Animated fullscreen transition displayed when switching accounts from Settings or
 * via the long-press Quick Switcher.
 *
 * Sequence:
 *  1. Tapped avatar smoothly travels/slides from its origin position directly into screen center.
 *  2. Name fades in below the avatar in the center.
 *  3. Avatar zooms across the entire screen with blooming welcome mesh background.
 *  4. Data switches in the background.
 *  5. Centered iTune Waves icon (above) and brand text (below) execute typing/erasing loop
 *     with exact splash styling and size.
 *  6. Seamlessly reveals the new account's Home Screen.
 */
@Composable
fun AccountSwitchTransitionOverlay(
    targetAccount: UserAccount,
    sourceAvatarBounds: Rect? = null,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    var rootSizePx by remember { mutableStateOf(IntSize.Zero) }

    val slideProgress = remember { Animatable(0f) }
    val nameAlpha = remember { Animatable(0f) }
    val megaZoomScale = remember { Animatable(1f) }
    val megaZoomAlpha = remember { Animatable(1f) }

    val iconAlpha = remember { Animatable(0f) }
    val backgroundAlpha = remember { Animatable(1f) }

    var centerBrandText by remember { mutableStateOf("iTune") }
    var showSplashLockup by remember { mutableStateOf(false) }

    // Ambient Welcome morphing mesh transition
    val infiniteTransition = rememberInfiniteTransition(label = "switchMeshLoop")
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

    // Sequence execution
    LaunchedEffect(targetAccount) {
        // 1. Smoothly slide the actual tapped avatar from its position to screen center
        slideProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        )
        nameAlpha.animateTo(1f, tween(durationMillis = 140))
        delay(80L)

        // 2. Zoom the profile avatar fully all over the screen with colorful welcome mesh bloom
        megaZoomScale.animateTo(
            targetValue = 8f,
            animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        )
        megaZoomAlpha.animateTo(0f, tween(durationMillis = 220))

        // 3. Switch account data in background
        MultiAccountManager.switchAccount(context, targetAccount.id)

        // 4. In middle, show ic_itune_waves (above) + typing brand text (below) - exact splash size
        showSplashLockup = true
        iconAlpha.animateTo(1f, tween(durationMillis = 200))

        delay(100L)
        eraseOut("iTune") { centerBrandText = it }

        delay(120L)
        typeOut("AirBeats") { centerBrandText = it }
        delay(220L)
        eraseOut("AirBeats") { centerBrandText = it }

        delay(80L)
        typeOut("iTune") { centerBrandText = it }
        delay(120L)

        // 5. Hide background and reveal fresh home screen of the new account
        backgroundAlpha.animateTo(0f, tween(durationMillis = 350, easing = FastOutSlowInEasing))
        delay(50L)
        onComplete()
    }
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { rootSizePx = it.size },
    ) {
        // Welcome Mesh Background with blooming color effects fading out to reveal fresh Home Screen
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

        val rootW = rootSizePx.width.toFloat()
        val rootH = rootSizePx.height.toFloat()

        if (rootW > 0f && rootH > 0f) {
            val slideT = slideProgress.value
            val startCenter = sourceAvatarBounds?.center ?: Offset(rootW * 0.85f, rootH * 0.12f)
            val startSize = sourceAvatarBounds?.width ?: with(density) { 36.dp.toPx() }
            val centerAvatarCenter = Offset(rootW / 2f, rootH * 0.44f)
            val centerAvatarSize = with(density) { 108.dp.toPx() }

            val curCenter = Offset(
                androidx.compose.ui.util.lerp(startCenter.x, centerAvatarCenter.x, slideT),
                androidx.compose.ui.util.lerp(startCenter.y, centerAvatarCenter.y, slideT),
            )
            val curSize = androidx.compose.ui.util.lerp(startSize, centerAvatarSize, slideT)

            val baseBoxDp = 108.dp
            val baseBoxPx = with(density) { baseBoxDp.toPx() }

            // Zooming profile avatar and name
            if (megaZoomAlpha.value > 0.01f) {
                // Sliding / mega-zooming avatar with TransformOrigin.Center for symmetrical fullscreen zoom
                Box(
                    modifier = Modifier
                        .size(baseBoxDp)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin.Center
                            translationX = curCenter.x - baseBoxPx / 2f
                            translationY = curCenter.y - baseBoxPx / 2f
                            val travelScale = (curSize / baseBoxPx).coerceAtLeast(0.01f)
                            scaleX = travelScale * megaZoomScale.value
                            scaleY = travelScale * megaZoomScale.value
                            alpha = megaZoomAlpha.value
                        }
                        .clip(CircleShape)
                        .border(2.5.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    AppleMemojiAvatar(
                        size = 100.dp,
                        avatarId = targetAccount.avatarId,
                    )
                }

                // Name below avatar in center
                if (nameAlpha.value > 0.01f && slideT > 0.5f) {
                    Text(
                        text = targetAccount.displayName,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily(Font(R.font.sf_pro_display_bold)),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.graphicsLayer {
                            alpha = nameAlpha.value * megaZoomAlpha.value
                            translationX = centerAvatarCenter.x - size.width / 2f
                            translationY = centerAvatarCenter.y + centerAvatarSize / 2f + with(density) { 18.dp.toPx() }
                        },
                    )
                }
            }

            // Center splash lockup: ic_itune_waves (above) + typing brand text (below)
            // Exact same dimensions as SplashScreen
            if (showSplashLockup) {
                val centerIconPx = with(density) { 92.dp.toPx() }
                val heroGapPx = with(density) { 18.dp.toPx() }
                val baseHeroTextH = with(density) { 52.dp.toPx() }
                val totalCenterH = centerIconPx + heroGapPx + baseHeroTextH

                val iconCenter = Offset(
                    x = rootW / 2f,
                    y = (rootH - totalCenterH) / 2f + centerIconPx / 2f,
                )
                val titleCenter = Offset(
                    x = rootW / 2f,
                    y = (rootH - totalCenterH) / 2f + centerIconPx + heroGapPx + baseHeroTextH / 2f,
                )

                // 92.dp Icon directly above
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .graphicsLayer {
                            alpha = iconAlpha.value * backgroundAlpha.value
                            translationX = iconCenter.x - centerIconPx / 2f
                            translationY = iconCenter.y - centerIconPx / 2f
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_itune_waves),
                        contentDescription = "iTune Waves",
                        modifier = Modifier.fillMaxSize(),
                        colorFilter = ColorFilter.tint(Color.White),
                    )
                }

                // 42.sp Hero Text directly below - matching SplashScreen hero typography exactly
                Text(
                    text = centerBrandText,
                    color = Color.White,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.8).sp,
                    fontFamily = SFProDisplay,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.graphicsLayer {
                        alpha = iconAlpha.value * backgroundAlpha.value
                        translationX = titleCenter.x - size.width / 2f
                        translationY = titleCenter.y - size.height / 2f
                    },
                )
            }
        }
    }
}
