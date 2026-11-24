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
package com.darkxvenom.itune.ui.artist.components.backdrop.effects

// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

import androidx.compose.ui.graphics.RenderEffect
import com.darkxvenom.itune.ui.artist.components.backdrop.BackdropEffectScope
import com.darkxvenom.itune.ui.artist.components.backdrop.RuntimeShader
import com.darkxvenom.itune.ui.artist.components.backdrop.internal.RuntimeShaderEffect
import com.darkxvenom.itune.ui.artist.components.backdrop.internal.chain
import com.darkxvenom.itune.ui.artist.components.backdrop.isRenderEffectSupported
import com.darkxvenom.itune.ui.artist.components.backdrop.isRuntimeShaderSupported
import org.intellij.lang.annotations.Language
import kotlin.contracts.ExperimentalContracts

fun BackdropEffectScope.effect(effect: RenderEffect) {
    if (!isRenderEffectSupported()) return

    renderEffect = renderEffect.chain(effect)
}
// Integrated into iTune by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark

@OptIn(ExperimentalContracts::class)
fun BackdropEffectScope.runtimeShaderEffect(
    key: String,
    @Language("AGSL") shaderString: String,
    uniformShaderName: String,
    block: RuntimeShader.() -> Unit
) {
    if (!isRuntimeShaderSupported()) return

    val effect =
        RuntimeShaderEffect(
            runtimeShader = obtainRuntimeShader(key, shaderString).apply(block),
            uniformShaderName = uniformShaderName
        )
    renderEffect = renderEffect.chain(effect)
}
