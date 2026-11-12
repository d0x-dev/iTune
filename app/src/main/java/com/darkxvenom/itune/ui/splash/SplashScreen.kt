package com.darkxvenom.itune.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkxvenom.itune.R
import com.darkxvenom.itune.ui.player.SFProDisplay
import com.darkxvenom.itune.ui.welcome.WelcomeMeshBackground
import com.darkxvenom.itune.ui.welcome.sampleWelcomeAmbientFrame
import kotlinx.coroutines.delay
import kotlin.math.PI

/**
 * Signature iTune Splash Screen with playful fluid brand typing animation and seamless top-bar docking.
 *
 * Sequence:
 *  1. Displays centered iTune Waves brand icon with ambient mesh-gradient background.
 *  2. Simulates keystrokes:
 *     - Types "iTune", pauses, erases.
 *     - Types "AirBeats", pauses, erases.
 *     - Types "Naah!", pauses, erases.
 *     - Types "iTune" (retained as permanent brand).
 *  3. Seamless Transition:
 *     - Fresh / New user: waves icon animates toward top-left corner and "iTune" title toward
 *       top-right corner, aligning with WelcomeScreen's onboarding header.
 *     - Existing user: background fades out revealing HomeScreen while centered icon and title
 *       glide to TopHeaderBar's exact bounds.
 */
@Composable
fun SplashScreen(
    isNewUser: Boolean,
    homeIconBounds: Rect?,
    homeTitleBounds: Rect?,
    onRevealUnderlay: () -> Unit = {},
    onSplashFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val statusBarInsets = WindowInsets.statusBars
    val statusBarTopPx = with(density) { statusBarInsets.getTop(density).toFloat() }

    var rootSizePx by remember { mutableStateOf(IntSize.Zero) }
    var displayedText by remember { mutableStateOf("") }
    var cursorVisible by remember { mutableStateOf(true) }
    var activelyTyping by remember { mutableStateOf(false) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val backgroundAlpha = remember { Animatable(1f) }
    val travelProgress = remember { Animatable(0f) }

    // Ambient loop matching WelcomeScreen
    val infiniteTransition = rememberInfiniteTransition(label = "splashMeshLoop")
    val colorProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 35_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "splashAmbientCycle",
    )
    val orbitPhaseA by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 15_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "splashOrbitPhaseA",
    )
    val orbitPhaseB by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 21_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "splashOrbitPhaseB",
    )

    val ambient = sampleWelcomeAmbientFrame(colorProgress)
    val lightFactor = ambient.lightFactor
    val contrastT = ((lightFactor - 0.50f) / 0.28f).coerceIn(0f, 1f)
    val inkFactor = contrastT * contrastT * (3f - 2f * contrastT)

    val headlineColor = lerp(Color.White, Color(0xFF0D0D10), inkFactor)
    val cursorColor = lerp(Color.White, Color(0xFFF21E3E), inkFactor)

    // Cursor blink transition
    val blinkTransition = rememberInfiniteTransition(label = "splashCursorBlink")
    val blinkAlpha by blinkTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 440, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "splashCursorAlpha",
    )

    suspend fun typeText(text: String, charDelayMs: Long = 72L) {
        activelyTyping = true
        cursorVisible = true
        for (i in 1..text.length) {
            displayedText = text.substring(0, i)
            delay(charDelayMs + ((i * 17) % 25))
        }
        activelyTyping = false
    }

    suspend fun eraseText(charDelayMs: Long = 48L) {
        activelyTyping = true
        cursorVisible = true
        val len = displayedText.length
        for (i in len - 1 downTo 0) {
            displayedText = displayedText.substring(0, i)
            delay(charDelayMs)
        }
        activelyTyping = false
    }

    // Orchestrate typing sequence & dock travel
    LaunchedEffect(Unit) {
        delay(220L)

        // 1. Type "iTune", hold, erase
        typeText("iTune", 75L)
        delay(550L)
        eraseText(48L)
        delay(200L)

        // 2. Type "AirBeats", hold, erase
        typeText("AirBeats", 68L)
        delay(600L)
        eraseText(45L)
        delay(200L)

        // 3. Type "Naah!", hold, erase
        typeText("Naah!", 72L)
        delay(600L)
        eraseText(48L)
        delay(200L)

        // 4. Final permanent brand: "iTune"
        typeText("iTune", 80L)
        cursorVisible = false
        delay(480L)

        // 5. Transition:
        if (isNewUser) {
            // Animate wave icon and iTune title directly to WelcomeScreen's corner positions
            travelProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            )
            onSplashFinished()
        } else {
            // Fade out splash background revealing HomeScreen underlay
            onRevealUnderlay()
            backgroundAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing),
            )
            delay(80L)

            // Glide both icon and title from middle into TopHeaderBar
            travelProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            )
            onSplashFinished()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {}
            .onGloballyPositioned { rootSizePx = it.size }
    ) {
        // Mesh background with dynamic fade-out
        WelcomeMeshBackground(
            ambient = ambient,
            orbitPhaseA = orbitPhaseA,
            orbitPhaseB = orbitPhaseB,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = backgroundAlpha.value },
        )

        val rootW = rootSizePx.width.toFloat()
        val rootH = rootSizePx.height.toFloat()

        if (rootW > 0f && rootH > 0f) {
            val centerIconPx = with(density) { 92.dp.toPx() }
            val heroGapPx = with(density) { 18.dp.toPx() }
            val baseHeroTextH = with(density) { 52.dp.toPx() }
            val totalCenterH = centerIconPx + heroGapPx + baseHeroTextH

            val centerIconTopLeft = Offset(
                x = (rootW - centerIconPx) / 2f,
                y = (rootH - totalCenterH) / 2f,
            )
            val centerTitleCenter = Offset(
                x = rootW / 2f,
                y = centerIconTopLeft.y + centerIconPx + heroGapPx + baseHeroTextH / 2f,
            )

            val t = travelProgress.value

            // Compute Target coordinates:
            val targetIconTopLeft: Offset
            val targetIconSizePx: Float
            val targetTitleCenter: Offset
            val targetTitleScale: Float

            if (isNewUser) {
                // Land at WelcomeScreen top corners (icon top-left, title top-right)
                val cornerPadX = with(density) { 24.dp.toPx() }
                val cornerPadY = statusBarTopPx + with(density) { 14.dp.toPx() }
                val cornerIconSizePx = with(density) { 34.dp.toPx() }
                val cornerTitleSizePx = with(density) { 22.dp.toPx() }

                targetIconTopLeft = Offset(cornerPadX, cornerPadY)
                targetIconSizePx = cornerIconSizePx

                targetTitleCenter = Offset(
                    x = rootW - cornerPadX - with(density) { 28.dp.toPx() },
                    y = cornerPadY + cornerIconSizePx / 2f,
                )
                targetTitleScale = cornerTitleSizePx / baseHeroTextH
            } else {
                // Land at HomeScreen TopHeaderBar (icon at left, title immediately adjacent)
                val fallbackIcon = with(density) { Rect(20.dp.toPx(), (statusBarTopPx + 14.dp.toPx()), 52.dp.toPx(), (statusBarTopPx + 46.dp.toPx())) }
                val fallbackTitle = with(density) { Rect(62.dp.toPx(), (statusBarTopPx + 14.dp.toPx()), 128.dp.toPx(), (statusBarTopPx + 46.dp.toPx())) }

                val endIcon = homeIconBounds ?: fallbackIcon
                val endTitle = homeTitleBounds ?: fallbackTitle

                targetIconTopLeft = endIcon.topLeft
                targetIconSizePx = endIcon.width

                targetTitleCenter = endTitle.center
                targetTitleScale = (endTitle.height / baseHeroTextH).coerceIn(0.38f, 0.72f)
            }

            // Interpolate current icon & title positions
            val currentIconTopLeft = Offset(
                x = centerIconTopLeft.x + (targetIconTopLeft.x - centerIconTopLeft.x) * t,
                y = centerIconTopLeft.y + (targetIconTopLeft.y - centerIconTopLeft.y) * t,
            )
            val currentIconSizePx = centerIconPx + (targetIconSizePx - centerIconPx) * t

            val currentTitleCenter = Offset(
                x = centerTitleCenter.x + (targetTitleCenter.x - centerTitleCenter.x) * t,
                y = centerTitleCenter.y + (targetTitleCenter.y - centerTitleCenter.y) * t,
            )
            val currentTitleScale = 1f + (targetTitleScale - 1f) * t

            val progressAfterFade = if (!isNewUser) {
                maxOf(1f - backgroundAlpha.value, t)
            } else {
                0f
            }
            val currentIconColor = lerp(headlineColor, Color.White, progressAfterFade)
            val currentTextColor = lerp(headlineColor, Color.White, progressAfterFade)

            // 1. Animated Brand Waves Icon
            Image(
                painter = painterResource(id = R.drawable.ic_itune_waves),
                contentDescription = "iTune Waves",
                colorFilter = ColorFilter.tint(currentIconColor),
                modifier = Modifier
                    .size(with(density) { currentIconSizePx.toDp() })
                    .graphicsLayer {
                        translationX = currentIconTopLeft.x
                        translationY = currentIconTopLeft.y
                    },
            )

            // 2. Animated Title with typing cursor
            Text(
                text = displayedText,
                color = currentTextColor,
                fontFamily = SFProDisplay,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.8).sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                onTextLayout = { textLayoutResult = it },
                modifier = Modifier
                    .graphicsLayer {
                        translationX = currentTitleCenter.x - size.width / 2f
                        translationY = currentTitleCenter.y - size.height / 2f
                        scaleX = currentTitleScale
                        scaleY = currentTitleScale
                        transformOrigin = TransformOrigin.Center
                    }
                    .heightIn(min = with(density) { baseHeroTextH.toDp() })
                    .drawWithContent {
                        drawContent()
                        val alpha = if (cursorVisible) (if (activelyTyping) 1f else blinkAlpha) else 0f
                        if (alpha > 0.01f) {
                            val caretW = 3.2.dp.toPx()
                            val gap = 4.dp.toPx()
                            val layout = textLayoutResult
                            val caretX = if (displayedText.isEmpty() || layout == null || layout.lineCount == 0) {
                                size.width / 2f - caretW / 2f
                            } else {
                                layout.getLineRight(0) + gap
                            }
                            val lineH = layout?.getLineBottom(0)?.minus(layout.getLineTop(0)) ?: size.height
                            val caretH = minOf(38.dp.toPx(), lineH * 0.78f)
                            val caretTop = (size.height - caretH) / 2f
                            drawRoundRect(
                                color = cursorColor.copy(alpha = alpha),
                                topLeft = Offset(caretX, caretTop),
                                size = Size(caretW, caretH),
                                cornerRadius = CornerRadius(caretW / 2f, caretW / 2f),
                            )
                        }
                    },
            )
        }
    }
}
