package com.darkxvenom.itune.ui.welcome

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.darkxvenom.itune.R
import com.darkxvenom.itune.ui.artist.haptics.Haptic
import com.darkxvenom.itune.ui.artist.haptics.rememberHaptics
import com.darkxvenom.itune.ui.player.SFProDisplay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

private val WelcomeCrimson = Color(0xFFF21E3E)
private val WelcomeCrimsonDeep = Color(0xFFE01435)
private val WelcomeDarkInk = Color(0xFF0D0D10)
private val WelcomeSFPro = FontFamily(
    Font(R.font.sf_pro_display_regular, FontWeight.Normal),
    Font(R.font.sf_pro_display_medium, FontWeight.Medium),
    Font(R.font.sf_pro_display_semibold, FontWeight.SemiBold),
    Font(R.font.sf_pro_display_bold, FontWeight.Bold),
)

/**
 * Snapshot of one ambient mesh-gradient lighting theme on the Welcome screen.
 */
internal data class WelcomeAmbientFrame(
    val baseColor: Color,
    val centerWash: Color,
    val topRightGlow: Color,
    val bottomLeftGlow: Color,
    val bottomRightGlow: Color,
    val lightFactor: Float,
)

/**
 * Curated dark -> light -> dark keyframe cycle reproducing the signature Welcome looks:
 * 1. Twilight Violet + Neon Magenta / Warm Amber
 * 2. Obsidian Black + Crimson Ruby
 * 3. Abyss Midnight + Electric Sapphire Blue
 * 4. Sunset Rose-Plum bridge (smooth warm transition without gray mud)
 * 5. Pearlescent Blush White + Sakura Pink / Warm Honey Gold
 * 6. Prismatic Pearl White + Pink / Warm Cream / Soft Lavender
 * 7. Amethyst Dawn bridge (smooth return into twilight dark)
 */
internal val WelcomeAmbientKeyframes = listOf(
    WelcomeAmbientFrame(
        baseColor = Color(0xFF0A031D),
        centerWash = Color(0xFF280659),
        topRightGlow = Color(0xFFFF0080),
        bottomLeftGlow = Color(0xFFFF8800),
        bottomRightGlow = Color(0xFF130533),
        lightFactor = 0.0f,
    ),
    WelcomeAmbientFrame(
        baseColor = Color(0xFF030304),
        centerWash = Color(0xFF140204),
        topRightGlow = Color(0xFFDF0024),
        bottomLeftGlow = Color(0xFFCE001E),
        bottomRightGlow = Color(0xFF040406),
        lightFactor = 0.0f,
    ),
    WelcomeAmbientFrame(
        baseColor = Color(0xFF020309),
        centerWash = Color(0xFF040B22),
        topRightGlow = Color(0xFF0047FF),
        bottomLeftGlow = Color(0xFF0043FF),
        bottomRightGlow = Color(0xFF02050E),
        lightFactor = 0.0f,
    ),
    WelcomeAmbientFrame(
        baseColor = Color(0xFF63264D),
        centerWash = Color(0xFF532149),
        topRightGlow = Color(0xFFFF629C),
        bottomLeftGlow = Color(0xFFFFB26B),
        bottomRightGlow = Color(0xFF663D7F),
        lightFactor = 0.44f,
    ),
    WelcomeAmbientFrame(
        baseColor = Color(0xFFFFFDFD),
        centerWash = Color(0xFFFFF6F9),
        topRightGlow = Color(0xFFFF9BBE),
        bottomLeftGlow = Color(0xFFFFDF9B),
        bottomRightGlow = Color(0xFFFFF2F7),
        lightFactor = 1.0f,
    ),
    WelcomeAmbientFrame(
        baseColor = Color(0xFFFFFFFF),
        centerWash = Color(0xFFFDFBFF),
        topRightGlow = Color(0xFFFF96BE),
        bottomLeftGlow = Color(0xFFFFE3A2),
        bottomRightGlow = Color(0xFFD6C3FF),
        lightFactor = 1.0f,
    ),
    WelcomeAmbientFrame(
        baseColor = Color(0xFF281040),
        centerWash = Color(0xFF331350),
        topRightGlow = Color(0xFFFF3A88),
        bottomLeftGlow = Color(0xFFFF9A38),
        bottomRightGlow = Color(0xFF231144),
        lightFactor = 0.24f,
    ),
)

internal const val WELCOME_HEADLINE_TEXT = "Welcome to iTune"
internal const val WELCOME_CURSOR_INITIAL_CUE_MS = 700L
internal const val WELCOME_CURSOR_SETTLE_MS = 280L
internal const val WELCOME_TEXT_HOLD_HIDDEN_MS = 5_500L
internal const val WELCOME_CURSOR_REAPPEAR_CUE_MS = 750L
internal const val WELCOME_CLEARED_PAUSE_MS = 520L

/**
 * Computes realistic keystroke timing for typing `"Welcome to iTune"`, adding natural
 * micro-pauses around spaces and uppercase transitions.
 */
internal fun welcomeTypingKeystrokeDelayMs(text: String, charIndex: Int): Long {
    val ch = text.getOrNull(charIndex) ?: return 70L
    val prev = text.getOrNull(charIndex - 1)
    val base = 62L + ((charIndex * 23) % 32)
    val wordBoundaryPause = when {
        prev == ' ' -> 74L
        ch.isUpperCase() && charIndex > 0 -> 48L
        ch == ' ' -> 38L
        else -> 0L
    }
    return base + wordBoundaryPause
}

/**
 * Computes realistic accelerated key-repeat timing when backspacing text.
 */
internal fun welcomeBackspaceDelayMs(deletedStep: Int): Long = when (deletedStep) {
    0 -> 86L
    1 -> 62L
    2 -> 48L
    else -> 34L + (deletedStep % 3) * 4L
}

/**
 * Smoothly samples a continuous [WelcomeAmbientFrame] for a normalized cycle [progress] in `[0f, 1f)`.
 */
internal fun sampleWelcomeAmbientFrame(progress: Float): WelcomeAmbientFrame {
    val wrapped = ((progress % 1f) + 1f) % 1f
    val count = WelcomeAmbientKeyframes.size
    val scaled = wrapped * count
    val index = scaled.toInt().coerceIn(0, count - 1)
    val nextIndex = (index + 1) % count
    val rawT = scaled - index
    val smoothT = (0.5f - 0.5f * cos(rawT * PI.toFloat())).coerceIn(0f, 1f)

    val from = WelcomeAmbientKeyframes[index]
    val to = WelcomeAmbientKeyframes[nextIndex]
    return WelcomeAmbientFrame(
        baseColor = lerp(from.baseColor, to.baseColor, smoothT),
        centerWash = lerp(from.centerWash, to.centerWash, smoothT),
        topRightGlow = lerp(from.topRightGlow, to.topRightGlow, smoothT),
        bottomLeftGlow = lerp(from.bottomLeftGlow, to.bottomLeftGlow, smoothT),
        bottomRightGlow = lerp(from.bottomRightGlow, to.bottomRightGlow, smoothT),
        lightFactor = from.lightFactor + (to.lightFactor - from.lightFactor) * smoothT,
    )
}

/**
 * Stylish Apple Music-inspired Welcome onboarding screen with slowly morphing
 * dark-to-light-to-dark mesh corner glows, adaptive typography/status bars,
 * `ic_itune_waves` top-left branding, underline name input, and crimson pill CTA.
 */
@Composable
fun WelcomeScreen(
    initialName: String = "",
    onContinue: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    var nameText by rememberSaveable(initialName) { mutableStateOf(initialName) }
    var isFocused by remember { mutableStateOf(false) }
    var showEmptyHint by remember { mutableStateOf(false) }
    var enteredScreen by remember { mutableStateOf(false) }
    val shakeOffsetPx = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        enteredScreen = true
    }

    val infiniteTransition = rememberInfiniteTransition(label = "welcomeMeshLoop")
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
    val lightFactor = ambient.lightFactor
    // Keep foreground crisp pure white across dark and twilight phases; crossfade cleanly when bright pearl blooms
    val contrastT = ((lightFactor - 0.50f) / 0.28f).coerceIn(0f, 1f)
    val inkFactor = contrastT * contrastT * (3f - 2f * contrastT)

    WelcomeSystemBarsSync(isLightBackground = inkFactor > 0.5f)

    val cornerWaveColor = lerp(Color.White, WelcomeDarkInk, inkFactor)
    val cornerBrandColor = lerp(Color.White, WelcomeCrimson, inkFactor)
    val headlineColor = lerp(Color.White, WelcomeDarkInk, inkFactor)
    val headlineCursorColor = lerp(Color.White, WelcomeCrimson, inkFactor)
    val placeholderColor = lerp(
        Color.White.copy(alpha = 0.64f),
        Color(0xFF63636A),
        inkFactor,
    )
    val inputTextColor = lerp(Color.White, WelcomeDarkInk, inkFactor)

    val idleUnderlineColor = lerp(
        Color.White.copy(alpha = 0.42f),
        Color(0xFF1C1C1E).copy(alpha = 0.24f),
        inkFactor,
    )
    val focusedUnderlineColor = lerp(
        Color.White.copy(alpha = 0.92f),
        WelcomeCrimson.copy(alpha = 0.88f),
        inkFactor,
    )
    val underlineColor by animateColorAsState(
        targetValue = when {
            showEmptyHint -> WelcomeCrimson
            isFocused -> focusedUnderlineColor
            else -> idleUnderlineColor
        },
        animationSpec = tween(durationMillis = 220),
        label = "underlineColor",
    )
    val underlineThickness by animateDpAsState(
        targetValue = if (isFocused || showEmptyHint) 1.6.dp else 1.0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "underlineThickness",
    )

    val submitName: () -> Unit = {
        val cleaned = nameText.trim()
        if (cleaned.isNotEmpty()) {
            haptics.play(Haptic.Select)
            keyboardController?.hide()
            onContinue(cleaned)
        } else {
            showEmptyHint = true
            haptics.play(Haptic.Tick)
            focusRequester.requestFocus()
            keyboardController?.show()
            coroutineScope.launch {
                val pattern = listOf(-18f, 18f, -14f, 14f, -8f, 8f, 0f)
                for (target in pattern) {
                    shakeOffsetPx.animateTo(target, tween(durationMillis = 48))
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        WelcomeMeshBackground(
            ambient = ambient,
            orbitPhaseA = orbitPhaseA,
            orbitPhaseB = orbitPhaseB,
            modifier = Modifier.fillMaxSize(),
        )

        // Corner Header: ic_itune_waves top-left, iTune brand wordmark top-right
        AnimatedVisibility(
            visible = enteredScreen,
            enter = fadeIn(tween(650)) + slideInVertically(tween(650, easing = FastOutSlowInEasing)) { -it / 2 },
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_itune_waves),
                    contentDescription = "iTune Waves",
                    colorFilter = ColorFilter.tint(cornerWaveColor),
                    modifier = Modifier.size(34.dp),
                )
                Text(
                    text = "iTune",
                    color = cornerBrandColor,
                    fontFamily = SFProDisplay,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                )
            }
        }

        // Bottom-Anchored Left-Aligned Welcome Headline, Underline Name Field & Crimson Continue Pill
        AnimatedVisibility(
            visible = enteredScreen,
            enter = fadeIn(tween(750)) + slideInVertically(tween(750, easing = FastOutSlowInEasing)) { it / 6 },
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp)
                    .padding(top = 64.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.Start,
            ) {
                WelcomeTypingHeadline(
                    textColor = headlineColor,
                    cursorColor = headlineCursorColor,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(26.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(shakeOffsetPx.value.roundToInt(), 0) },
                ) {
                    BasicTextField(
                        value = nameText,
                        onValueChange = { updated ->
                            nameText = updated
                            if (showEmptyHint && updated.isNotBlank()) {
                                showEmptyHint = false
                            }
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = inputTextColor,
                            fontFamily = WelcomeSFPro,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        cursorBrush = SolidColor(WelcomeCrimson),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { submitName() },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { focusState -> isFocused = focusState.isFocused }
                            .padding(vertical = 8.dp),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (nameText.isEmpty()) {
                                    Text(
                                        text = "Your Name",
                                        color = placeholderColor,
                                        fontFamily = WelcomeSFPro,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Normal,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(underlineThickness)
                            .background(underlineColor),
                    )

                    AnimatedVisibility(
                        visible = showEmptyHint,
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(180)),
                    ) {
                        Text(
                            text = "Enter your name to personalize iTune",
                            color = WelcomeCrimson,
                            fontFamily = WelcomeSFPro,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                WelcomeContinueButton(
                    onClick = submitName,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun WelcomeMeshBackground(
    ambient: WelcomeAmbientFrame,
    orbitPhaseA: Float,
    orbitPhaseB: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val maxDim = max(w, h)

        drawRect(color = ambient.baseColor)

        val driftX = sin(orbitPhaseA) * (w * 0.055f)
        val driftY = cos(orbitPhaseB) * (h * 0.045f)
        val radiusScaleA = 1f + 0.07f * sin(orbitPhaseB)
        val radiusScaleB = 1f + 0.07f * cos(orbitPhaseA)

        // Subtle mid-screen ambient bloom
        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to ambient.centerWash.copy(alpha = 0.58f),
                    0.55f to ambient.centerWash.copy(alpha = 0.22f),
                    1.0f to Color.Transparent,
                ),
                center = Offset(w * 0.50f + driftX * 0.6f, h * 0.48f - driftY * 0.6f),
                radius = maxDim * 0.85f,
            ),
        )

        // Bottom-right secondary halo (gives pearlescent lilac & deep twilight depth)
        val brCenter = Offset(w * 0.92f - driftX * 0.5f, h * 0.90f + driftY * 0.5f)
        val brRadius = w * 1.10f * radiusScaleB
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to ambient.bottomRightGlow.copy(alpha = 0.72f),
                    0.45f to ambient.bottomRightGlow.copy(alpha = 0.30f),
                    1.0f to Color.Transparent,
                ),
                center = brCenter,
                radius = brRadius,
            ),
            radius = brRadius,
            center = brCenter,
        )

        // Prominent Top-Right Corner Glow Bloom
        val trCenter = Offset(w * 0.94f + driftX, h * 0.04f + driftY)
        val trRadius = max(w * 1.18f, h * 0.52f) * radiusScaleA
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to ambient.topRightGlow.copy(alpha = 0.96f),
                    0.34f to ambient.topRightGlow.copy(alpha = 0.62f),
                    0.68f to ambient.topRightGlow.copy(alpha = 0.20f),
                    1.0f to Color.Transparent,
                ),
                center = trCenter,
                radius = trRadius,
            ),
            radius = trRadius,
            center = trCenter,
        )

        // Prominent Bottom-Left Corner Glow Bloom
        val blCenter = Offset(w * 0.04f - driftX, h * 0.96f - driftY)
        val blRadius = max(w * 1.12f, h * 0.50f) * radiusScaleB
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to ambient.bottomLeftGlow.copy(alpha = 0.96f),
                    0.34f to ambient.bottomLeftGlow.copy(alpha = 0.60f),
                    0.68f to ambient.bottomLeftGlow.copy(alpha = 0.18f),
                    1.0f to Color.Transparent,
                ),
                center = blCenter,
                radius = blRadius,
            ),
            radius = blRadius,
            center = blCenter,
        )
    }
}

@Composable
private fun WelcomeContinueButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
        label = "welcomeContinueScale",
    )

    Box(
        modifier = modifier
            .height(54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = 14.dp,
                shape = CircleShape,
                ambientColor = WelcomeCrimson.copy(alpha = 0.35f),
                spotColor = WelcomeCrimson.copy(alpha = 0.35f),
            )
            .clip(CircleShape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFFFA2446), WelcomeCrimsonDeep),
                ),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 24.dp),
    ) {
        Text(
            text = "Continue",
            color = Color.White,
            fontFamily = SFProDisplay,
            fontSize = 16.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = "Continue",
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(20.dp),
        )
    }
}

@Composable
private fun WelcomeSystemBarsSync(isLightBackground: Boolean) {
    val view = LocalView.current
    val activity = remember(view) { view.context.findWelcomeActivity() }

    LaunchedEffect(activity, isLightBackground) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = isLightBackground
        controller.isAppearanceLightNavigationBars = isLightBackground
    }

    DisposableEffect(activity) {
        onDispose {
            val window = activity?.window ?: return@onDispose
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }
}

private tailrec fun Context.findWelcomeActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findWelcomeActivity()
    else -> null
}

/**
 * Typewriter headline for "Welcome to iTune":
 * 1) Shows blinking cursor indication first.
 * 2) Types each character with realistic keystroke cadence.
 * 3) Hides the cursor completely and holds the finished headline for ~5.5 seconds.
 * 4) Re-shows the cursor indication, then smoothly backspaces all characters before restarting.
 */
@Composable
private fun WelcomeTypingHeadline(
    textColor: Color,
    cursorColor: Color,
    modifier: Modifier = Modifier,
) {
    var charCount by remember { mutableIntStateOf(0) }
    var cursorActive by remember { mutableStateOf(true) }
    var isActivelyStroking by remember { mutableStateOf(false) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            cursorActive = true
            isActivelyStroking = false
            charCount = 0
            delay(WELCOME_CURSOR_INITIAL_CUE_MS)

            isActivelyStroking = true
            for (i in 0 until WELCOME_HEADLINE_TEXT.length) {
                delay(welcomeTypingKeystrokeDelayMs(WELCOME_HEADLINE_TEXT, i))
                charCount = i + 1
            }
            isActivelyStroking = false

            delay(WELCOME_CURSOR_SETTLE_MS)
            cursorActive = false
            delay(WELCOME_TEXT_HOLD_HIDDEN_MS)

            cursorActive = true
            delay(WELCOME_CURSOR_REAPPEAR_CUE_MS)

            isActivelyStroking = true
            for (deleted in 0 until WELCOME_HEADLINE_TEXT.length) {
                delay(welcomeBackspaceDelayMs(deleted))
                charCount = (WELCOME_HEADLINE_TEXT.length - 1 - deleted).coerceAtLeast(0)
            }
            isActivelyStroking = false

            delay(WELCOME_CLEARED_PAUSE_MS)
        }
    }

    val blinkTransition = rememberInfiniteTransition(label = "welcomeHeadlineCursorBlink")
    val blinkAlpha by blinkTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 440, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursorBlinkAlpha",
    )
    val cursorVisibility by animateFloatAsState(
        targetValue = if (cursorActive) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "cursorVisibilityAlpha",
    )

    val visibleText = WELCOME_HEADLINE_TEXT.take(charCount)

    Box(
        modifier = modifier.heightIn(min = 44.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = visibleText,
            color = textColor,
            fontFamily = SFProDisplay,
            fontSize = 31.sp,
            fontWeight = FontWeight.W800,
            letterSpacing = (-0.6).sp,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { textLayoutResult = it },
            modifier = Modifier.drawWithContent {
                drawContent()
                val effectiveAlpha =
                    ((if (isActivelyStroking) 1f else blinkAlpha) * cursorVisibility)
                        .coerceIn(0f, 1f)
                if (effectiveAlpha > 0.01f) {
                    val caretWidth = 2.6.dp.toPx()
                    val caretGap = 3.dp.toPx()
                    val caretX = if (visibleText.isEmpty()) {
                        1.dp.toPx()
                    } else {
                        val layout = textLayoutResult
                        val rightEdge = if (layout != null && layout.lineCount > 0) {
                            layout.getLineRight(layout.lineCount - 1)
                        } else {
                            size.width
                        }
                        rightEdge + caretGap
                    }
                    val caretHeight = minOf(29.dp.toPx(), size.height * 0.78f)
                    val caretTop = (size.height - caretHeight) / 2f + 1.dp.toPx()
                    drawRoundRect(
                        color = cursorColor.copy(alpha = effectiveAlpha),
                        topLeft = Offset(caretX, caretTop),
                        size = Size(caretWidth, caretHeight),
                        cornerRadius = CornerRadius(caretWidth / 2f, caretWidth / 2f),
                    )
                }
            },
        )
    }
}

