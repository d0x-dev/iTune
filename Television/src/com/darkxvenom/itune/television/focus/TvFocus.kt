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

package com.darkxvenom.itune.television.focus

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.darkxvenom.itune.R
import com.darkxvenom.itune.ui.theme.AppleMusicRed

// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

val SFProDisplay = com.darkxvenom.itune.ui.artist.theme.SFProDisplay

@Composable
fun TvBigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leadingIconRes: Int? = null,
    focusRequester: FocusRequester? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else if (isFocused) 1.05f else 1.0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "tvBigBtnScale",
    )

    Box(
        modifier = modifier
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
            )
            .shadow(
                elevation = if (isFocused) 20.dp else 8.dp,
                shape = CircleShape,
                ambientColor = if (isFocused) Color.White.copy(alpha = 0.35f) else AppleMusicRed.copy(alpha = 0.25f),
                spotColor = if (isFocused) Color.White.copy(alpha = 0.50f) else AppleMusicRed.copy(alpha = 0.40f),
            )
            .clip(CircleShape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFFFA2446), Color(0xFFC70024)),
                )
            )
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = CircleShape,
            )
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (leadingIconRes != null) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(leadingIconRes),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = text,
                color = Color.White,
                fontSize = 17.sp,
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.Bold,
            )
            if (icon != null) {
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
fun TvSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else if (isFocused) 1.05f else 1.0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "tvSecBtnScale",
    )

    Box(
        modifier = modifier
            .height(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
            )
            .clip(CircleShape)
            .background(if (isFocused) Color(0x35FFFFFF) else Color(0x18FFFFFF))
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.25f),
                shape = CircleShape,
            )
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 16.sp,
            fontFamily = SFProDisplay,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

object TvDeviceUtils {
    @Volatile
    private var cachedIsTv: Boolean? = null

    fun isTv(context: Context): Boolean {
        cachedIsTv?.let { return it }
        val appContext = context.applicationContext
        val uiModeManager = appContext.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isUiModeTv = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val pm = appContext.packageManager
        val hasLeanback = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val hasTelevision = pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
        val result = isUiModeTv || hasLeanback || hasTelevision
        cachedIsTv = result
        return result
    }
}

/**
 * Universal TV focus modifier that equips any Compose component with:
 * 1. Prominent animated focus border / outline (visible from 10 feet away)
 * 2. Subtle spring scale magnification on focus
 * 3. Elevated glowing shadow when highlighted
 * 4. Hardware DPAD_CENTER / ENTER key event handling for Android TV remotes
 */
fun Modifier.tvFocusable(
    shape: Shape = RoundedCornerShape(12.dp),
    focusedBorderColor: Color = Color.White,
    unfocusedBorderColor: Color = Color.Transparent,
    focusedBorderWidth: Dp = 2.5.dp,
    unfocusedBorderWidth: Dp = 0.dp,
    scaleOnFocus: Float = 1.05f,
    focusRequester: FocusRequester? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: (() -> Unit)? = null,
): Modifier = composed {
    val actualInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isFocused by actualInteractionSource.collectIsFocusedAsState()
    val isPressed by actualInteractionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.97f
            isFocused -> scaleOnFocus
            else -> 1.0f
        },
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 400f),
        label = "tvFocusScale",
    )

    val currentBorderColor by animateColorAsState(
        targetValue = if (isFocused) focusedBorderColor else unfocusedBorderColor,
        animationSpec = tween(durationMillis = 180),
        label = "tvFocusBorderColor",
    )

    val currentBorderWidth by animateDpAsState(
        targetValue = if (isFocused) focusedBorderWidth else unfocusedBorderWidth,
        animationSpec = tween(durationMillis = 180),
        label = "tvFocusBorderWidth",
    )

    val shadowElevation by animateDpAsState(
        targetValue = if (isFocused) 18.dp else 0.dp,
        animationSpec = tween(durationMillis = 180),
        label = "tvFocusShadow",
    )

    var mod = this
    if (focusRequester != null) {
        mod = mod.focusRequester(focusRequester)
    }

    mod
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (shadowElevation > 0.dp) {
                Modifier.shadow(
                    elevation = shadowElevation,
                    shape = shape,
                    ambientColor = focusedBorderColor.copy(alpha = 0.35f),
                    spotColor = focusedBorderColor.copy(alpha = 0.45f),
                )
            } else {
                Modifier
            }
        )
        .border(
            width = currentBorderWidth,
            color = currentBorderColor,
            shape = shape,
        )
        .then(
            if (onClick != null) {
                Modifier
                    .onPreviewKeyEvent { keyEvent ->
                        val isCenterOrEnter = keyEvent.key == Key.DirectionCenter ||
                            keyEvent.key == Key.Enter ||
                            keyEvent.key == Key.NumPadEnter
                        if (isCenterOrEnter && keyEvent.type == KeyEventType.KeyUp) {
                            onClick()
                            true
                        } else {
                            false
                        }
                    }
                    .clickable(
                        interactionSource = actualInteractionSource,
                        indication = null,
                        onClick = onClick,
                    )
            } else {
                Modifier.focusable(interactionSource = actualInteractionSource)
            }
        )
}

/**
 * Reusable TV Card container with smooth focus zoom and glowing border.
 */
@Composable
fun TvCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    focusedBorderColor: Color = Color.White,
    scaleOnFocus: Float = 1.04f,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.(isFocused: Boolean) -> Unit,
) {
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .tvFocusable(
                shape = shape,
                focusedBorderColor = focusedBorderColor,
                scaleOnFocus = scaleOnFocus,
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .clip(shape),
    ) {
        content(isFocused)
    }
}
