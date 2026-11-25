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

package com.darkxvenom.itune.ui.player


import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.darkxvenom.itune.R
import com.darkxvenom.itune.data.saavn.SaavnStatus

/**
 * Player capsule content while the JioSaavn provider is on:
 * "Finding a better stream" (breathing pulse while searching) → "High Quality AAC" when found.
 */
@Composable
fun SaavnQualityLabel(status: SaavnStatus) {
    val upgrading = status == SaavnStatus.SEARCHING
    val pulse = if (upgrading) {
        rememberInfiniteTransition(label = "saavnPulse").animateFloat(
            initialValue = 1f, targetValue = 0.4f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "saavnPulseAlpha",
        )
    } else null
    Row(
        modifier = Modifier.graphicsLayer {
            alpha = pulse?.value ?: 1f
            compositingStrategy = CompositingStrategy.ModulateAlpha
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_lossless),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.White.copy(alpha = if (upgrading) 0.6f else 0.92f)),
            modifier = Modifier.size(width = 16.dp, height = 12.dp),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
        val style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
        if (upgrading) {
            Text(stringResource(R.string.ui_finding_a_better_stream), style = style, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
        } else {
            ShimmerLabel("High Quality AAC", style)
        }
    }
}

/** Highlight band sweeping across the text every ~3s — a confirmed result, not just a label. */
@Composable
private fun ShimmerLabel(text: String, style: TextStyle) {
    val t = rememberInfiniteTransition(label = "saavnShimmer").animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "saavnShimmerPos",
    )
    // Soft breathing glow behind the confirmed "Lossless" text.
    val glow = rememberInfiniteTransition(label = "saavnGlow").animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "saavnGlowAlpha",
    )
    val base = Color.White.copy(alpha = 0.7f)
    val brush = Brush.linearGradient(
        colors = listOf(base, Color.White, base),
        start = androidx.compose.ui.geometry.Offset(t.value * 160f, 0f),
        end = androidx.compose.ui.geometry.Offset(t.value * 160f + 120f, 0f),
    )
    Text(
        text,
        style = style.copy(
            brush = brush,
            shadow = androidx.compose.ui.graphics.Shadow(
                color = Color(0xFFBFE3FF).copy(alpha = glow.value),
                offset = androidx.compose.ui.geometry.Offset.Zero,
                blurRadius = 8f + 14f * glow.value,
            ),
        ),
        maxLines = 1,
    )
}
