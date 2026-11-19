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

package com.darkxvenom.itune.television.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.darkxvenom.itune.ui.welcome.WelcomeAmbientFrame
import com.darkxvenom.itune.ui.welcome.WelcomeAmbientKeyframes
import com.darkxvenom.itune.ui.welcome.WelcomeMeshBackground
import kotlin.math.PI
import kotlin.math.cos

/** Only the dark welcome keyframes (violet/amber, ruby, sapphire, amethyst) so white text stays readable. */
private val DarkAmbientFrames: List<WelcomeAmbientFrame> = listOf(0, 1, 2, 6).map { WelcomeAmbientKeyframes[it] }

private fun sampleDarkAmbient(progress: Float): WelcomeAmbientFrame {
    val n = DarkAmbientFrames.size
    val scaled = (((progress % 1f) + 1f) % 1f) * n
    val i = scaled.toInt().coerceIn(0, n - 1)
    val t = 0.5f - 0.5f * cos((scaled - i) * PI.toFloat())
    val a = DarkAmbientFrames[i]
    val b = DarkAmbientFrames[(i + 1) % n]
    return WelcomeAmbientFrame(
        baseColor = lerp(a.baseColor, b.baseColor, t),
        centerWash = lerp(a.centerWash, b.centerWash, t),
        topRightGlow = lerp(a.topRightGlow, b.topRightGlow, t),
        bottomLeftGlow = lerp(a.bottomLeftGlow, b.bottomLeftGlow, t),
        bottomRightGlow = lerp(a.bottomRightGlow, b.bottomRightGlow, t),
        lightFactor = 0f,
    )
}

/**
 * The same slowly colour-cycling mesh gradient as the Welcome screen, tuned for the TV player,
 * lyrics, queue and settings screens. [dim] darkens it for text legibility.
 */
@Composable
fun TvAmbientBackground(modifier: Modifier = Modifier, dim: Float = 0.18f) {
    val transition = rememberInfiniteTransition(label = "tvAmbient")
    val cycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(32_000, easing = LinearEasing), RepeatMode.Restart),
        label = "tvAmbientCycle",
    )
    val orbitA by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(15_000, easing = LinearEasing), RepeatMode.Restart),
        label = "tvOrbitA",
    )
    val orbitB by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(21_000, easing = LinearEasing), RepeatMode.Restart),
        label = "tvOrbitB",
    )
    val frame = remember(cycle) { sampleDarkAmbient(cycle) }
    Box(modifier) {
        WelcomeMeshBackground(frame, orbitA, orbitB, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
    }
}
