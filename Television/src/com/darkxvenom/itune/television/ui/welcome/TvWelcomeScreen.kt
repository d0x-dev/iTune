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

package com.darkxvenom.itune.television.ui.welcome


import androidx.compose.ui.res.stringResource
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.darkxvenom.itune.R
import com.darkxvenom.itune.data.auth.WebSessionMode
import com.darkxvenom.itune.ui.artist.haptics.Haptic
import com.darkxvenom.itune.ui.artist.haptics.rememberHaptics
import com.darkxvenom.itune.ui.auth.GoogleLoginScreen
import com.darkxvenom.itune.ui.player.SFProDisplay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

private val WelcomeCrimson = Color(0xFFF21E3E)
private val WelcomeCrimsonDeep = Color(0xFFE01435)
private val WelcomeDarkInk = Color(0xFF0D0D10)
private val WelcomeSFPro = FontFamily(
    Font(R.font.sf_pro_display_regular, FontWeight.Normal),
    Font(R.font.sf_pro_display_medium, FontWeight.Medium),
    Font(R.font.sf_pro_display_semibold, FontWeight.SemiBold),
    Font(R.font.sf_pro_display_bold, FontWeight.Bold),
)

internal data class TvWelcomeAmbientFrame(
    val baseColor: Color,
    val centerWash: Color,
    val topRightGlow: Color,
    val bottomLeftGlow: Color,
    val bottomRightGlow: Color,
    val lightFactor: Float,
)

internal val TvWelcomeAmbientKeyframes = listOf(
    TvWelcomeAmbientFrame(
        baseColor = Color(0xFF0A031D),
        centerWash = Color(0xFF280659),
        topRightGlow = Color(0xFFFF0080),
        bottomLeftGlow = Color(0xFFFF8800),
        bottomRightGlow = Color(0xFF130533),
        lightFactor = 0.0f,
    ),
    TvWelcomeAmbientFrame(
        baseColor = Color(0xFF030304),
        centerWash = Color(0xFF140204),
        topRightGlow = Color(0xFFDF0024),
        bottomLeftGlow = Color(0xFFCE001E),
        bottomRightGlow = Color(0xFF040406),
        lightFactor = 0.0f,
    ),
    TvWelcomeAmbientFrame(
        baseColor = Color(0xFF020309),
        centerWash = Color(0xFF040B22),
        topRightGlow = Color(0xFF0047FF),
        bottomLeftGlow = Color(0xFF0043FF),
        bottomRightGlow = Color(0xFF02050E),
        lightFactor = 0.0f,
    ),
    TvWelcomeAmbientFrame(
        baseColor = Color(0xFF63264D),
        centerWash = Color(0xFF532149),
        topRightGlow = Color(0xFFFF629C),
        bottomLeftGlow = Color(0xFFFFB26B),
        bottomRightGlow = Color(0xFF663D7F),
        lightFactor = 0.44f,
    ),
    TvWelcomeAmbientFrame(
        baseColor = Color(0xFFFFFDFD),
        centerWash = Color(0xFFFFF6F9),
        topRightGlow = Color(0xFFFF9BBE),
        bottomLeftGlow = Color(0xFFFFDF9B),
        bottomRightGlow = Color(0xFFFFF2F7),
        lightFactor = 1.0f,
    ),
    TvWelcomeAmbientFrame(
        baseColor = Color(0xFFFFFFFF),
        centerWash = Color(0xFFFDFBFF),
        topRightGlow = Color(0xFFFF96BE),
        bottomLeftGlow = Color(0xFFFFE3A2),
        bottomRightGlow = Color(0xFFD6C3FF),
        lightFactor = 1.0f,
    ),
    TvWelcomeAmbientFrame(
        baseColor = Color(0xFF281040),
        centerWash = Color(0xFF331350),
        topRightGlow = Color(0xFFFF3A88),
        bottomLeftGlow = Color(0xFFFF9A38),
        bottomRightGlow = Color(0xFF231144),
        lightFactor = 0.24f,
    ),
)

internal const val TV_WELCOME_HEADLINE_TEXT = "Welcome to iTune"
internal const val TV_WELCOME_GOOGLE_TEXT = "Continue with your Google account to get your personalised feed."
internal const val TV_WELCOME_CURSOR_INITIAL_CUE_MS = 700L
internal const val TV_WELCOME_CURSOR_SETTLE_MS = 280L
internal const val TV_WELCOME_TEXT_HOLD_HIDDEN_MS = 5_500L
internal const val TV_WELCOME_CURSOR_REAPPEAR_CUE_MS = 750L
internal const val TV_WELCOME_CLEARED_PAUSE_MS = 520L

private enum class TvWelcomeStep {
    NAME,
    GOOGLE_CONNECT,
    OUTRO,
}

private enum class TvOutroPhase {
    TYPING_SIGNING_IN,
    TYPING_GREETING,
    TYPING_CUSTOMISING,
    TRAVEL_TO_CENTER,
    SHOW_ITUNE_CENTER,
    WAIT_BEFORE_AIRBEATS,
    TYPING_AIRBEATS,
    TYPING_BACK_ITUNE,
    REVEAL_HOME_DIM_BG,
    TRAVEL_TO_HOME_BAR,
    DONE,
}

internal fun tvWelcomeKeystrokeDelayMs(text: String, charIndex: Int): Long {
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

internal fun tvWelcomeBackspaceDelayMs(deletedStep: Int): Long = when (deletedStep) {
    0 -> 86L
    1 -> 62L
    2 -> 48L
    else -> 34L + (deletedStep % 3) * 4L
}

internal fun sampleTvWelcomeAmbientFrame(progress: Float): TvWelcomeAmbientFrame {
    val wrapped = ((progress % 1f) + 1f) % 1f
    val count = TvWelcomeAmbientKeyframes.size
    val scaled = wrapped * count
    val index = scaled.toInt().coerceIn(0, count - 1)
    val nextIndex = (index + 1) % count
    val rawT = scaled - index
    val smoothT = (0.5f - 0.5f * cos(rawT * PI.toFloat())).coerceIn(0f, 1f)

    val from = TvWelcomeAmbientKeyframes[index]
    val to = TvWelcomeAmbientKeyframes[nextIndex]
    return TvWelcomeAmbientFrame(
        baseColor = lerp(from.baseColor, to.baseColor, smoothT),
        centerWash = lerp(from.centerWash, to.centerWash, smoothT),
        topRightGlow = lerp(from.topRightGlow, to.topRightGlow, smoothT),
        bottomLeftGlow = lerp(from.bottomLeftGlow, to.bottomLeftGlow, smoothT),
        bottomRightGlow = lerp(from.bottomRightGlow, to.bottomRightGlow, smoothT),
        lightFactor = from.lightFactor + (to.lightFactor - from.lightFactor) * smoothT,
    )
}

/**
 * Android TV Edition of the Welcome screen with full D-Pad navigation, remote OK/Enter handling,
 * and high-visibility focus borders for TV remotes.
 */
@Composable
fun TvWelcomeScreen(
    initialName: String = "",
    fromSplash: Boolean = false,
    homeIconBounds: Rect? = null,
    homeTitleBounds: Rect? = null,
    onRevealHomeUnderlay: (String) -> Unit = {},
    onContinue: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    val focusRequester = remember { FocusRequester() }
    val continueButtonFocusRequester = remember { FocusRequester() }
    val googleButtonFocusRequester = remember { FocusRequester() }
    val skipButtonFocusRequester = remember { FocusRequester() }

    var nameText by rememberSaveable(initialName) { mutableStateOf(initialName) }
    var isFocused by remember { mutableStateOf(false) }
    var showEmptyHint by remember { mutableStateOf(false) }
    var enteredScreen by remember { mutableStateOf(false) }
    var currentStep by remember { mutableStateOf(TvWelcomeStep.NAME) }
    var outroPhase by remember { mutableStateOf(TvOutroPhase.TYPING_SIGNING_IN) }
    var outroText by remember { mutableStateOf("") }
    var outroCursorVisible by remember { mutableStateOf(true) }
    var outroActivelyTyping by remember { mutableStateOf(false) }
    var outroShowLoader by remember { mutableStateOf(false) }
    var showGoogleLogin by remember { mutableStateOf(false) }

    var rootSizePx by remember { mutableStateOf(IntSize.Zero) }
    var cornerIconRect by remember { mutableStateOf<Rect?>(null) }
    var cornerTitleRect by remember { mutableStateOf<Rect?>(null) }
    var heroTitleSizePx by remember { mutableStateOf(IntSize.Zero) }

    val shakeOffsetPx = remember { Animatable(0f) }
    val cornerToCenterProgress = remember { Animatable(0f) }
    val backgroundAlpha = remember { Animatable(1f) }
    val centerToHomeProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        enteredScreen = true
        delay(300L)
        focusRequester.requestFocus()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "tvWelcomeMeshLoop")
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

    val ambient = sampleTvWelcomeAmbientFrame(colorProgress)
    val lightFactor = ambient.lightFactor
    val contrastT = ((lightFactor - 0.50f) / 0.28f).coerceIn(0f, 1f)
    val inkFactor = contrastT * contrastT * (3f - 2f * contrastT)

    TvWelcomeSystemBarsSync(isLightBackground = inkFactor > 0.5f)

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
        targetValue = if (isFocused || showEmptyHint) 2.2.dp else 1.2.dp,
        animationSpec = tween(durationMillis = 200),
        label = "underlineThickness",
    )

    val submitName: () -> Unit = {
        val cleaned = nameText.trim()
        if (cleaned.isNotEmpty()) {
            haptics.play(Haptic.Select)
            keyboardController?.hide()
            currentStep = TvWelcomeStep.GOOGLE_CONNECT
            coroutineScope.launch {
                delay(150L)
                googleButtonFocusRequester.requestFocus()
            }
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

    BackHandler(enabled = currentStep == TvWelcomeStep.GOOGLE_CONNECT && !showGoogleLogin) {
        currentStep = TvWelcomeStep.NAME
    }
    BackHandler(enabled = currentStep == TvWelcomeStep.OUTRO) {
    }

    LaunchedEffect(currentStep) {
        if (currentStep != TvWelcomeStep.OUTRO) return@LaunchedEffect

        suspend fun typeOut(target: String) {
            outroActivelyTyping = false
            outroCursorVisible = true
            outroText = ""
            delay(360L)
            outroActivelyTyping = true
            for (i in 0 until target.length) {
                delay(tvWelcomeKeystrokeDelayMs(target, i))
                outroText = target.take(i + 1)
            }
            outroActivelyTyping = false
        }

        suspend fun eraseOut(source: String) {
            outroCursorVisible = true
            delay(320L)
            outroActivelyTyping = true
            for (deleted in 0 until source.length) {
                delay(tvWelcomeBackspaceDelayMs(deleted))
                outroText = source.dropLast(deleted + 1)
            }
            outroActivelyTyping = false
        }

        val cleaned = nameText.trim().ifBlank { "Listener" }
        cornerToCenterProgress.snapTo(0f)
        backgroundAlpha.snapTo(1f)
        centerToHomeProgress.snapTo(0f)

        outroPhase = TvOutroPhase.TYPING_SIGNING_IN
        outroShowLoader = true
        typeOut("Signing In")
        outroCursorVisible = false
        delay(1050L)
        eraseOut("Signing In")
        outroShowLoader = false
        delay(220L)

        outroPhase = TvOutroPhase.TYPING_GREETING
        val greeting = "Hi, $cleaned, We Got You!"
        typeOut(greeting)
        outroCursorVisible = false
        delay(1250L)
        eraseOut(greeting)
        delay(220L)

        outroPhase = TvOutroPhase.TYPING_CUSTOMISING
        val customising = "Customising Your feed.."
        typeOut(customising)
        outroCursorVisible = false
        delay(1150L)
        eraseOut(customising)
        outroCursorVisible = false
        delay(240L)

        outroText = "iTune"
        outroCursorVisible = false
        outroPhase = TvOutroPhase.TRAVEL_TO_CENTER
        cornerToCenterProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 880, easing = FastOutSlowInEasing),
        )

        outroPhase = TvOutroPhase.SHOW_ITUNE_CENTER
        outroCursorVisible = false
        delay(1200L)
        eraseOut("iTune")
        delay(2000L)

        outroPhase = TvOutroPhase.TYPING_AIRBEATS
        typeOut("AirBeats")
        outroCursorVisible = false
        delay(1200L)
        eraseOut("AirBeats")
        delay(220L)

        outroPhase = TvOutroPhase.TYPING_BACK_ITUNE
        typeOut("iTune")
        outroCursorVisible = false
        delay(600L)

        outroPhase = TvOutroPhase.REVEAL_HOME_DIM_BG
        onRevealHomeUnderlay(nameText.trim())
        backgroundAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 620, easing = LinearEasing),
        )

        outroPhase = TvOutroPhase.TRAVEL_TO_HOME_BAR
        centerToHomeProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 720, easing = FastOutSlowInEasing),
        )
        outroPhase = TvOutroPhase.DONE
        onContinue(nameText.trim())
    }

    val isBrandTraveling = outroPhase in setOf(
        TvOutroPhase.TRAVEL_TO_CENTER,
        TvOutroPhase.SHOW_ITUNE_CENTER,
        TvOutroPhase.WAIT_BEFORE_AIRBEATS,
        TvOutroPhase.TYPING_AIRBEATS,
        TvOutroPhase.TYPING_BACK_ITUNE,
        TvOutroPhase.REVEAL_HOME_DIM_BG,
        TvOutroPhase.TRAVEL_TO_HOME_BAR,
        TvOutroPhase.DONE,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { rootSizePx = it.size },
    ) {
        TvWelcomeMeshBackground(
            ambient = ambient,
            orbitPhaseA = orbitPhaseA,
            orbitPhaseB = orbitPhaseB,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = backgroundAlpha.value },
        )

        // Top Brand Header
        if (!isBrandTraveling) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 48.dp, vertical = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_itune_waves),
                    contentDescription = stringResource(R.string.ui_itune_waves),
                    colorFilter = ColorFilter.tint(cornerWaveColor),
                    modifier = Modifier
                        .size(34.dp)
                        .onGloballyPositioned { coords ->
                            if (!isBrandTraveling) cornerIconRect = coords.boundsInRoot()
                        },
                )

                Text(
                    text = stringResource(R.string.app_name),
                    color = cornerBrandColor,
                    fontFamily = SFProDisplay,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                    modifier = Modifier.onGloballyPositioned { coords ->
                        if (!isBrandTraveling) cornerTitleRect = coords.boundsInRoot()
                    },
                )
            }
        }

        // Animated Onboarding Content
        AnimatedVisibility(
            visible = enteredScreen,
            enter = fadeIn(tween(750)) + slideInVertically(tween(750, easing = FastOutSlowInEasing)) { it / 6 },
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .navigationBarsPadding(),
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val slideIn = slideInHorizontally(
                        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                    ) { if (forward) it else -it } + fadeIn(tween(340))
                    val slideOut = slideOutHorizontally(
                        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                    ) { if (forward) -it else it } + fadeOut(tween(240))
                    slideIn togetherWith slideOut
                },
                label = "tvWelcomeStepSlide",
                modifier = Modifier.fillMaxSize(),
            ) { step ->
                when (step) {
                    TvWelcomeStep.NAME -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 64.dp)
                                .padding(top = 64.dp, bottom = 40.dp),
                            verticalArrangement = Arrangement.Bottom,
                            horizontalAlignment = Alignment.Start,
                        ) {
                            TvWelcomeTypingHeadline(
                                targetText = TV_WELCOME_HEADLINE_TEXT,
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
                                        fontSize = 19.sp,
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
                                        .focusProperties {
                                            down = continueButtonFocusRequester
                                        }
                                        .onFocusChanged { focusState -> isFocused = focusState.isFocused }
                                        .onPreviewKeyEvent { event ->
                                            if (event.type == KeyEventType.KeyUp &&
                                                (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter)
                                            ) {
                                                submitName()
                                                true
                                            } else if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                                continueButtonFocusRequester.requestFocus()
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        .padding(vertical = 10.dp),
                                    decorationBox = { innerTextField ->
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.CenterStart,
                                        ) {
                                            if (nameText.isEmpty()) {
                                                Text(
                                                    text = stringResource(R.string.ui_your_name),
                                                    color = placeholderColor,
                                                    fontFamily = WelcomeSFPro,
                                                    fontSize = 17.sp,
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
                                        text = stringResource(R.string.ui_enter_your_name_to_personalize_itune),
                                        color = WelcomeCrimson,
                                        fontFamily = WelcomeSFPro,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(28.dp))

                            TvWelcomeContinueButton(
                                onClick = submitName,
                                focusRequester = continueButtonFocusRequester,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusProperties {
                                        up = focusRequester
                                    },
                            )
                        }
                    }

                    TvWelcomeStep.GOOGLE_CONNECT -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 64.dp)
                                .padding(top = 64.dp, bottom = 40.dp),
                            verticalArrangement = Arrangement.Top,
                            horizontalAlignment = Alignment.Start,
                        ) {
                            Spacer(modifier = Modifier.height(10.dp))

                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .shadow(
                                        elevation = 10.dp,
                                        shape = CircleShape,
                                        ambientColor = Color.Black.copy(alpha = 0.25f),
                                        spotColor = Color.Black.copy(alpha = 0.25f),
                                    )
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.ic_google_logo),
                                    contentDescription = stringResource(R.string.ui_google),
                                    modifier = Modifier.size(30.dp),
                                )
                            }

                            Spacer(modifier = Modifier.height(26.dp))

                            TvWelcomeTypingHeadline(
                                targetText = TV_WELCOME_GOOGLE_TEXT,
                                textColor = headlineColor,
                                cursorColor = headlineCursorColor,
                                fontSize = 28.sp,
                                lineHeight = 36.sp,
                                maxLines = 4,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 148.dp),
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            TvWelcomeGoogleContinueButton(
                                onClick = {
                                    haptics.play(Haptic.Select)
                                    showGoogleLogin = true
                                },
                                focusRequester = googleButtonFocusRequester,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusProperties {
                                        down = skipButtonFocusRequester
                                    },
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            TvWelcomeSkipSignInButton(
                                isLightBackground = inkFactor > 0.5f,
                                onClick = {
                                    haptics.play(Haptic.Tap)
                                    keyboardController?.hide()
                                    currentStep = TvWelcomeStep.OUTRO
                                },
                                focusRequester = skipButtonFocusRequester,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusProperties {
                                        up = googleButtonFocusRequester
                                    },
                            )
                        }
                    }

                    TvWelcomeStep.OUTRO -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AnimatedVisibility(
                                visible = !isBrandTraveling,
                                enter = fadeIn(tween(180)),
                                exit = fadeOut(tween(160)),
                            ) {
                                TvWelcomeOutroCenterMessage(
                                    text = outroText,
                                    showCursor = outroCursorVisible,
                                    activelyTyping = outroActivelyTyping,
                                    showLoader = outroShowLoader,
                                    textColor = headlineColor,
                                    cursorColor = headlineCursorColor,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isBrandTraveling && outroPhase != TvOutroPhase.DONE) {
            TvWelcomeOutroTravelingBrand(
                rootSizePx = rootSizePx,
                cornerIconRect = cornerIconRect,
                cornerTitleRect = cornerTitleRect,
                homeIconBounds = homeIconBounds,
                homeTitleBounds = homeTitleBounds,
                cornerWaveColor = cornerWaveColor,
                cornerBrandColor = cornerBrandColor,
                headlineColor = headlineColor,
                cursorColor = headlineCursorColor,
                cornerToCenter = cornerToCenterProgress.value,
                dimProgress = 1f - backgroundAlpha.value,
                centerToHome = centerToHomeProgress.value,
                titleText = outroText,
                showCursor = outroCursorVisible &&
                    outroPhase in setOf(
                        TvOutroPhase.SHOW_ITUNE_CENTER,
                        TvOutroPhase.WAIT_BEFORE_AIRBEATS,
                        TvOutroPhase.TYPING_AIRBEATS,
                        TvOutroPhase.TYPING_BACK_ITUNE,
                    ),
                activelyTyping = outroActivelyTyping,
                heroTitleSizePx = heroTitleSizePx,
                onHeroTitleMeasured = { heroTitleSizePx = it },
            )
        }

        if (showGoogleLogin) {
            GoogleLoginScreen(
                mode = WebSessionMode.SIGN_IN,
                returnImmediatelyOnCapture = true,
                onSignedIn = {
                    showGoogleLogin = false
                    keyboardController?.hide()
                    currentStep = TvWelcomeStep.OUTRO
                },
                onClose = { showGoogleLogin = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun TvWelcomeOutroCenterMessage(
    text: String,
    showCursor: Boolean,
    activelyTyping: Boolean,
    showLoader: Boolean,
    textColor: Color,
    cursorColor: Color,
) {
    val blinkTransition = rememberInfiniteTransition(label = "tvOutroCenterCursorBlink")
    val blinkAlpha by blinkTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 440, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "tvOutroCursorAlpha",
    )
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            color = textColor,
            fontFamily = WelcomeSFPro,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.W800,
            letterSpacing = (-0.6).sp,
            textAlign = TextAlign.Center,
            onTextLayout = { textLayout = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 42.dp)
                .drawWithContent {
                    drawContent()
                    val alpha = if (showCursor) (if (activelyTyping) 1f else blinkAlpha) else 0f
                    if (alpha > 0.01f) {
                        val caretW = 3.dp.toPx()
                        val gap = 3.dp.toPx()
                        val layout = textLayout
                        val lastLine = ((layout?.lineCount ?: 1) - 1).coerceAtLeast(0)
                        val caretX = if (text.isEmpty() || layout == null || layout.lineCount == 0) {
                            size.width / 2f
                        } else {
                            (layout.getLineRight(lastLine) + gap).coerceAtMost(size.width - caretW)
                        }
                        val top = if (layout != null && layout.lineCount > 0 && text.isNotEmpty()) {
                            layout.getLineTop(lastLine)
                        } else {
                            0f
                        }
                        val bottom = if (layout != null && layout.lineCount > 0 && text.isNotEmpty()) {
                            layout.getLineBottom(lastLine)
                        } else {
                            40.sp.toPx()
                        }
                        val lineH = (bottom - top).coerceAtLeast(24.dp.toPx())
                        val caretH = minOf(32.dp.toPx(), lineH * 0.78f)
                        val caretTop = top + (lineH - caretH) / 2f
                        drawRoundRect(
                            color = cursorColor.copy(alpha = alpha),
                            topLeft = Offset(caretX, caretTop),
                            size = Size(caretW, caretH),
                            cornerRadius = CornerRadius(caretW / 2f, caretW / 2f),
                        )
                    }
                },
        )

        AnimatedVisibility(
            visible = showLoader,
            enter = fadeIn(tween(240)),
            exit = fadeOut(tween(200)),
        ) {
            CircularProgressIndicator(
                color = WelcomeCrimson,
                trackColor = textColor.copy(alpha = 0.16f),
                strokeWidth = 3.dp,
                modifier = Modifier
                    .padding(top = 22.dp)
                    .size(28.dp),
            )
        }
    }
}

@Composable
private fun TvWelcomeOutroTravelingBrand(
    rootSizePx: IntSize,
    cornerIconRect: Rect?,
    cornerTitleRect: Rect?,
    homeIconBounds: Rect?,
    homeTitleBounds: Rect?,
    cornerWaveColor: Color,
    cornerBrandColor: Color,
    headlineColor: Color,
    cursorColor: Color,
    cornerToCenter: Float,
    dimProgress: Float,
    centerToHome: Float,
    titleText: String,
    showCursor: Boolean,
    activelyTyping: Boolean,
    heroTitleSizePx: IntSize,
    onHeroTitleMeasured: (IntSize) -> Unit,
) {
    if (rootSizePx.width <= 0 || rootSizePx.height <= 0) return
    val density = LocalDensity.current
    val blinkTransition = rememberInfiniteTransition(label = "tvOutroHeroCursorBlink")
    val blinkAlpha by blinkTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 440, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "heroCursorAlpha",
    )
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    val rootW = rootSizePx.width.toFloat()
    val rootH = rootSizePx.height.toFloat()

    val fallbackStartIcon = with(density) { Rect(48.dp.toPx(), 42.dp.toPx(), 82.dp.toPx(), 76.dp.toPx()) }
    val startIcon = cornerIconRect ?: fallbackStartIcon
    val startTitle = cornerTitleRect ?: with(density) {
        Rect(rootW - 100.dp.toPx(), 44.dp.toPx(), rootW - 48.dp.toPx(), 70.dp.toPx())
    }
    val endIcon = homeIconBounds ?: with(density) { Rect(48.dp.toPx(), 46.dp.toPx(), 80.dp.toPx(), 78.dp.toPx()) }
    val endTitle = homeTitleBounds ?: with(density) { Rect(90.dp.toPx(), 48.dp.toPx(), 160.dp.toPx(), 76.dp.toPx()) }

    val centerIconPx = with(density) { 110.dp.toPx() }
    val heroGapPx = with(density) { 20.dp.toPx() }
    val fallbackHeroTextH = with(density) { 58.dp.toPx() }
    val baseHeroH = heroTitleSizePx.height.takeIf { it > 0 }?.toFloat() ?: fallbackHeroTextH

    val totalHeroH = centerIconPx + heroGapPx + baseHeroH
    val centerIconTopLeft = Offset(
        x = (rootW - centerIconPx) / 2f,
        y = (rootH - totalHeroH) / 2f,
    )
    val centerTitleCenter = Offset(
        x = rootW / 2f,
        y = centerIconTopLeft.y + centerIconPx + heroGapPx + baseHeroH / 2f,
    )

    val travelingHome = centerToHome > 0f
    val iconTopLeft = if (!travelingHome) {
        Offset(
            x = startIcon.left + (centerIconTopLeft.x - startIcon.left) * cornerToCenter,
            y = startIcon.top + (centerIconTopLeft.y - startIcon.top) * cornerToCenter,
        )
    } else {
        Offset(
            x = centerIconTopLeft.x + (endIcon.left - centerIconTopLeft.x) * centerToHome,
            y = centerIconTopLeft.y + (endIcon.top - centerIconTopLeft.y) * centerToHome,
        )
    }
    val iconSizePx = if (!travelingHome) {
        startIcon.width + (centerIconPx - startIcon.width) * cornerToCenter
    } else {
        centerIconPx + (endIcon.width - centerIconPx) * centerToHome
    }
    val iconColor = if (!travelingHome) {
        val midIconColor = lerp(headlineColor, Color.White, dimProgress)
        lerp(cornerWaveColor, midIconColor, cornerToCenter)
    } else {
        lerp(lerp(headlineColor, Color.White, dimProgress), Color.White, centerToHome)
    }

    val startTitleScale = (startTitle.height / baseHeroH).coerceIn(0.32f, 0.72f)
    val endTitleScale = (endTitle.height / baseHeroH).coerceIn(0.35f, 0.75f)
    val titleScale = if (!travelingHome) {
        startTitleScale + (1f - startTitleScale) * cornerToCenter
    } else {
        1f + (endTitleScale - 1f) * centerToHome
    }
    val titleCenter = if (!travelingHome) {
        Offset(
            x = startTitle.center.x + (centerTitleCenter.x - startTitle.center.x) * cornerToCenter,
            y = startTitle.center.y + (centerTitleCenter.y - startTitle.center.y) * cornerToCenter,
        )
    } else {
        Offset(
            x = centerTitleCenter.x + (endTitle.center.x - centerTitleCenter.x) * centerToHome,
            y = centerTitleCenter.y + (endTitle.center.y - centerTitleCenter.y) * centerToHome,
        )
    }
    val textColor = if (!travelingHome) {
        val midTextColor = lerp(headlineColor, Color.White, dimProgress)
        lerp(cornerBrandColor, midTextColor, cornerToCenter)
    } else {
        lerp(lerp(headlineColor, Color.White, dimProgress), Color.White, centerToHome)
    }
    val letterSpacingSp = if (!travelingHome) {
        (-0.4f + (-0.8f - -0.4f) * cornerToCenter).sp
    } else {
        (-0.8f + (-0.5f - -0.8f) * centerToHome).sp
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.ic_itune_waves),
            contentDescription = stringResource(R.string.ui_itune_waves),
            colorFilter = ColorFilter.tint(iconColor),
            modifier = Modifier
                .offset { IntOffset(iconTopLeft.x.roundToInt(), iconTopLeft.y.roundToInt()) }
                .size(with(density) { iconSizePx.toDp() }),
        )

        Text(
            text = titleText,
            color = textColor,
            fontSize = 50.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = letterSpacingSp,
            maxLines = 1,
            onTextLayout = { textLayout = it },
            modifier = Modifier
                .onGloballyPositioned { coords ->
                    if (coords.size.width > 0 && coords.size.height > 0 &&
                        (titleText == "iTune" || heroTitleSizePx == IntSize.Zero)
                    ) {
                        onHeroTitleMeasured(coords.size)
                    } else if (titleText != "iTune" && heroTitleSizePx.height == 0 && coords.size.height > 0) {
                        onHeroTitleMeasured(coords.size)
                    }
                }
                .graphicsLayer {
                    translationX = titleCenter.x - size.width / 2f
                    translationY = titleCenter.y - size.height / 2f
                    scaleX = titleScale
                    scaleY = titleScale
                    transformOrigin = TransformOrigin.Center
                }
                .heightIn(min = with(density) { baseHeroH.toDp() })
                .drawWithContent {
                    drawContent()
                    val alpha = if (showCursor) (if (activelyTyping) 1f else blinkAlpha) else 0f
                    if (alpha > 0.01f) {
                        val caretW = 3.5.dp.toPx()
                        val gap = 4.dp.toPx()
                        val layout = textLayout
                        val caretX = if (titleText.isEmpty() || layout == null || layout.lineCount == 0) {
                            size.width / 2f - caretW / 2f
                        } else {
                            layout.getLineRight(0) + gap
                        }
                        val lineH = (layout?.getLineBottom(0)?.minus(layout.getLineTop(0)))
                            ?.coerceAtLeast(36.dp.toPx())
                            ?: size.height
                        val caretH = minOf(44.dp.toPx(), lineH * 0.78f)
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

@Composable
internal fun TvWelcomeMeshBackground(
    ambient: TvWelcomeAmbientFrame,
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
private fun TvWelcomeContinueButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else if (isFocused) 1.05f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
        label = "welcomeContinueScale",
    )

    Box(
        modifier = modifier
            .height(58.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (isFocused) 22.dp else 14.dp,
                shape = CircleShape,
                ambientColor = if (isFocused) Color.White.copy(alpha = 0.40f) else WelcomeCrimson.copy(alpha = 0.35f),
                spotColor = if (isFocused) Color.White.copy(alpha = 0.55f) else WelcomeCrimson.copy(alpha = 0.35f),
            )
            .clip(CircleShape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFFFA2446), WelcomeCrimsonDeep),
                ),
            )
            .border(
                width = if (isFocused) 2.8.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = CircleShape,
            )
            .focusable(interactionSource = interactionSource)
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
            .padding(horizontal = 28.dp),
    ) {
        Text(
            text = stringResource(R.string.ui_continue),
            color = Color.White,
            fontFamily = SFProDisplay,
            fontSize = 17.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = stringResource(R.string.ui_continue),
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(22.dp),
        )
    }
}

@Composable
private fun TvWelcomeGoogleContinueButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else if (isFocused) 1.05f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
        label = "welcomeGoogleScale",
    )

    Box(
        modifier = modifier
            .height(58.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (isFocused) 22.dp else 14.dp,
                shape = CircleShape,
                ambientColor = if (isFocused) Color.White.copy(alpha = 0.40f) else WelcomeCrimson.copy(alpha = 0.35f),
                spotColor = if (isFocused) Color.White.copy(alpha = 0.55f) else WelcomeCrimson.copy(alpha = 0.35f),
            )
            .clip(CircleShape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFFFA2446), WelcomeCrimsonDeep),
                ),
            )
            .border(
                width = if (isFocused) 2.8.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = CircleShape,
            )
            .focusable(interactionSource = interactionSource)
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
    ) {
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_google_logo),
                    contentDescription = stringResource(R.string.ui_google),
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = stringResource(R.string.ui_continue_with_google),
                color = Color.White,
                fontFamily = SFProDisplay,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun TvWelcomeSkipSignInButton(
    isLightBackground: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else if (isFocused) 1.05f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
        label = "welcomeSkipScale",
    )
    val bgColor = if (isLightBackground) Color(0x14000000) else Color.White.copy(alpha = 0.12f)
    val borderColor = if (isFocused) Color.White else if (isLightBackground) Color(0x22000000) else Color.White.copy(alpha = 0.22f)
    val textColor = if (isLightBackground) WelcomeDarkInk else Color.White

    Box(
        modifier = modifier
            .height(52.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(bgColor)
            .border(if (isFocused) 2.8.dp else 0.8.dp, borderColor, CircleShape)
            .focusable(interactionSource = interactionSource)
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
            text = stringResource(R.string.ui_skip_sign_in),
            color = textColor,
            fontFamily = SFProDisplay,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TvWelcomeSystemBarsSync(isLightBackground: Boolean) {
    // Android TV has no system status/nav bars; kept as no-op to prevent window transaction overhead.
}

private tailrec fun Context.findTvWelcomeActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findTvWelcomeActivity()
    else -> null
}

@Composable
private fun TvWelcomeTypingHeadline(
    targetText: String,
    textColor: Color,
    cursorColor: Color,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 32.sp,
    lineHeight: androidx.compose.ui.unit.TextUnit = 40.sp,
    maxLines: Int = 1,
) {
    var charCount by remember(targetText) { mutableIntStateOf(0) }
    var cursorActive by remember(targetText) { mutableStateOf(true) }
    var isActivelyStroking by remember(targetText) { mutableStateOf(false) }
    var textLayoutResult by remember(targetText) { mutableStateOf<TextLayoutResult?>(null) }

    LaunchedEffect(targetText) {
        while (true) {
            cursorActive = true
            isActivelyStroking = false
            charCount = 0
            delay(TV_WELCOME_CURSOR_INITIAL_CUE_MS)

            isActivelyStroking = true
            for (i in 0 until targetText.length) {
                delay(tvWelcomeKeystrokeDelayMs(targetText, i))
                charCount = i + 1
            }
            isActivelyStroking = false

            delay(TV_WELCOME_CURSOR_SETTLE_MS)
            cursorActive = false
            delay(TV_WELCOME_TEXT_HOLD_HIDDEN_MS)

            cursorActive = true
            delay(TV_WELCOME_CURSOR_REAPPEAR_CUE_MS)

            isActivelyStroking = true
            for (deleted in 0 until targetText.length) {
                delay(tvWelcomeBackspaceDelayMs(deleted))
                charCount = (targetText.length - 1 - deleted).coerceAtLeast(0)
            }
            isActivelyStroking = false

            delay(TV_WELCOME_CLEARED_PAUSE_MS)
        }
    }

    val blinkTransition = rememberInfiniteTransition(label = "tvHeadlineCursorBlink")
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

    val visibleText = targetText.take(charCount)

    Box(
        modifier = modifier.heightIn(min = 46.dp),
        contentAlignment = if (maxLines > 1) Alignment.TopStart else Alignment.CenterStart,
    ) {
        Text(
            text = visibleText,
            color = textColor,
            fontFamily = SFProDisplay,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = FontWeight.W800,
            letterSpacing = (-0.6).sp,
            maxLines = maxLines,
            softWrap = maxLines > 1,
            onTextLayout = { textLayoutResult = it },
            modifier = Modifier.drawWithContent {
                drawContent()
                val effectiveAlpha =
                    ((if (isActivelyStroking) 1f else blinkAlpha) * cursorVisibility)
                        .coerceIn(0f, 1f)
                if (effectiveAlpha > 0.01f) {
                    val caretWidth = 2.8.dp.toPx()
                    val caretGap = 3.dp.toPx()
                    val layout = textLayoutResult
                    val lastLine = ((layout?.lineCount ?: 1) - 1).coerceAtLeast(0)
                    val caretX = if (visibleText.isEmpty()) {
                        1.dp.toPx()
                    } else if (layout != null && layout.lineCount > 0) {
                        layout.getLineRight(lastLine) + caretGap
                    } else {
                        size.width + caretGap
                    }
                    val lineTop = if (layout != null && layout.lineCount > 0 && visibleText.isNotEmpty()) {
                        layout.getLineTop(lastLine)
                    } else {
                        0f
                    }
                    val lineBottom = if (layout != null && layout.lineCount > 0 && visibleText.isNotEmpty()) {
                        layout.getLineBottom(lastLine)
                    } else {
                        lineHeight.toPx()
                    }
                    val lineH = (lineBottom - lineTop).coerceAtLeast(24.dp.toPx())
                    val caretHeight = minOf(30.dp.toPx(), lineH * 0.78f)
                    val caretTop = lineTop + (lineH - caretHeight) / 2f
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
