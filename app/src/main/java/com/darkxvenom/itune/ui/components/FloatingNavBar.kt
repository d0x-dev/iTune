package com.darkxvenom.itune.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkxvenom.itune.R
import com.darkxvenom.itune.ui.theme.AppleGlassBorder
import com.darkxvenom.itune.ui.theme.AppleGlassDock
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class NavTab {
    HOME,
    NEW,
    RADIO,
    LIBRARY
}

@Composable
fun FloatingNavBar(
    selectedTab: NavTab?,
    isSearchActive: Boolean = false,
    onTabSelected: (NavTab) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dockHeight = 58.dp
    val containerHeight = 88.dp

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    var dockWidthPx by remember { mutableFloatStateOf(0f) }
    var isHolding by remember { mutableStateOf(false) }
    var dragFingerX by remember { mutableFloatStateOf(0f) }
    var hoveredIndex by remember { mutableIntStateOf(selectedTab?.ordinal ?: 0) }

    val bubbleScale = remember { Animatable(1.0f) }

    LaunchedEffect(selectedTab, isHolding, isSearchActive) {
        if (!isHolding && selectedTab != null) {
            hoveredIndex = selectedTab.ordinal
        }
    }

    // Dynamic morphing between:
    // 1. Resting Shell: subtle inside-dock oval capsule (58dp x 46dp) matching media_1794248305825_dc654f82.png
    // 2. Glassy Water Droplet: 3D protruding liquid bubble (72dp x 65dp) matching media_1794246658446_33556023.png on tap-and-hold
    val currentBubbleWidthDp by animateDpAsState(
        targetValue = if (isHolding) 72.dp else 58.dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 340f),
        label = "BubbleWidth"
    )

    val currentBubbleHeightDp by animateDpAsState(
        targetValue = if (isHolding) 65.dp else 46.dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 340f),
        label = "BubbleHeight"
    )

    val holdingProgress by animateFloatAsState(
        targetValue = if (isHolding) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.70f, stiffness = 300f),
        label = "HoldingProgress"
    )

    val tabCount = 4
    val slotWidthPx = if (dockWidthPx > 0f) dockWidthPx / tabCount else 0f
    val bubbleWidthPx = with(density) { currentBubbleWidthDp.toPx() }

    val activeSlot = if (isHolding) hoveredIndex else (selectedTab?.ordinal ?: 0)
    val slotCenterX = if (slotWidthPx > 0f) activeSlot * slotWidthPx + slotWidthPx / 2f else 0f
    val restingX = slotCenterX - bubbleWidthPx / 2f

    val targetBubbleX = if (isHolding) {
        (dragFingerX - bubbleWidthPx / 2f).coerceIn(-12f, dockWidthPx - bubbleWidthPx + 12f)
    } else {
        restingX
    }

    val currentBubbleX by animateFloatAsState(
        targetValue = targetBubbleX,
        animationSpec = if (isHolding) {
            spring(dampingRatio = 0.85f, stiffness = 750f)
        } else {
            spring(dampingRatio = 0.65f, stiffness = 380f)
        },
        label = "BubbleX"
    )

    val targetAlpha = when {
        isSearchActive -> 0.0f
        selectedTab != null || isHolding -> 1.0f
        else -> 0.0f
    }

    val bubbleAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 150),
        label = "BubbleAlpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(containerHeight)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Main Navigation Dock Area
        Box(
            modifier = Modifier
                .weight(1f)
                .height(containerHeight)
                .onSizeChanged { size ->
                    dockWidthPx = size.width.toFloat()
                }
                .pointerInput(selectedTab, slotWidthPx, isSearchActive) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val sWidth = if (dockWidthPx > 0f) dockWidthPx / tabCount else size.width.toFloat() / tabCount
                        val targetSlot = (down.position.x / sWidth).toInt().coerceIn(0, tabCount - 1)

                        // 1. Immediately switch tab on touch (0ms latency, zero delay!)
                        onTabSelected(NavTab.entries[targetSlot])
                        dragFingerX = down.position.x
                        hoveredIndex = targetSlot

                        // 2. Tap-and-hold triggers the 3D liquid glassy water droplet bubble!
                        var isDragging = false
                        val holdJob = coroutineScope.launch {
                            delay(110)
                            isHolding = true
                            bubbleScale.animateTo(
                                targetValue = 1.28f,
                                animationSpec = spring(
                                    dampingRatio = 0.62f,
                                    stiffness = 220f
                                )
                            )
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break

                            val moveDelta = abs(change.position.x - down.position.x)
                            if (moveDelta > 8f) {
                                isDragging = true
                                dragFingerX = change.position.x
                                val currentSlot = (change.position.x / sWidth).toInt().coerceIn(0, tabCount - 1)
                                hoveredIndex = currentSlot

                                if (!isHolding) {
                                    isHolding = true
                                    coroutineScope.launch {
                                        bubbleScale.animateTo(
                                            targetValue = 1.28f,
                                            animationSpec = spring(
                                                dampingRatio = 0.62f,
                                                stiffness = 220f
                                            )
                                        )
                                    }
                                }
                                change.consume()
                            }
                        }

                        holdJob.cancel()
                        val finalFingerX = dragFingerX
                        val wasHolding = isHolding
                        isHolding = false

                        coroutineScope.launch {
                            bubbleScale.animateTo(
                                targetValue = 1.0f,
                                animationSpec = spring(
                                    dampingRatio = 0.55f,
                                    stiffness = 280f
                                )
                            )
                        }

                        if (isDragging || wasHolding) {
                            val destinationSlot = (finalFingerX / sWidth).toInt().coerceIn(0, tabCount - 1)
                            onTabSelected(NavTab.entries[destinationSlot])
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // Layer 1: Dock Capsule Background (58.dp height)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dockHeight)
                    .align(Alignment.Center)
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(percent = 50),
                        ambientColor = Color.Black,
                        spotColor = Color.Black
                    )
                    .background(AppleGlassDock, shape = RoundedCornerShape(percent = 50))
                    .border(0.75.dp, AppleGlassBorder, shape = RoundedCornerShape(percent = 50))
            )

            // Layer 2: Selected Indicator (Morphs between resting shell & liquid glassy water droplet on hold)
            if (dockWidthPx > 0f && (selectedTab != null || isHolding)) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(currentBubbleX.roundToInt(), 0) }
                        .align(Alignment.CenterStart)
                        .width(currentBubbleWidthDp)
                        .height(currentBubbleHeightDp)
                        .graphicsLayer {
                            scaleX = bubbleScale.value
                            scaleY = bubbleScale.value
                            alpha = bubbleAlpha
                        }
                        .shadow(
                            elevation = (18.dp * holdingProgress),
                            shape = RoundedCornerShape(percent = 50),
                            ambientColor = Color.Black.copy(alpha = 0.70f * holdingProgress),
                            spotColor = Color.Black.copy(alpha = 0.85f * holdingProgress)
                        )
                        .clip(RoundedCornerShape(percent = 50))
                        // Resting Shell Background (Subtle dark translucent oval inside dock, matches media_1794248305825_dc654f82.png)
                        .background(
                            Color.White.copy(alpha = 0.12f * (1f - holdingProgress * 0.4f)),
                            shape = RoundedCornerShape(percent = 50)
                        )
                ) {
                    // Glassy Water Droplet Overlay (Comes alive on tap-and-hold, matches media_1794246658446_33556023.png)
                    if (holdingProgress > 0.01f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = holdingProgress }
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.28f),
                                            Color.White.copy(alpha = 0.10f),
                                            Color(0x35000000),
                                            Color.White.copy(alpha = 0.15f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(percent = 50)
                                )
                                .border(
                                    width = 1.25.dp,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.85f),
                                            Color.White.copy(alpha = 0.45f),
                                            Color.White.copy(alpha = 0.18f),
                                            Color.White.copy(alpha = 0.38f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(percent = 50)
                                )
                        ) {
                            // Top Specular Lens Glare Arc (Convex Water Droplet Lens Highlight)
                            Box(
                                modifier = Modifier
                                    .padding(top = 3.dp)
                                    .align(Alignment.TopCenter)
                                    .width(34.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.45f),
                                                Color.White.copy(alpha = 0.0f)
                                            )
                                        )
                                    )
                            )

                            // Bottom Ambient Meniscus Light Bounce
                            Box(
                                modifier = Modifier
                                    .padding(bottom = 3.dp)
                                    .align(Alignment.BottomCenter)
                                    .width(26.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.0f),
                                                Color.White.copy(alpha = 0.22f)
                                            )
                                        )
                                    )
                            )
                        }
                    }
                }
            }

            // Layer 3: Tabs Row (Red on resting shell, White inside glassy bubble when holding)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dockHeight)
                    .align(Alignment.Center),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppleNavTabItem(
                    label = "Home",
                    iconRes = R.drawable.ic_nav_home,
                    isHighlighted = !isSearchActive && (if (isHolding) hoveredIndex == 0 else selectedTab == NavTab.HOME),
                    isHolding = isHolding,
                    modifier = Modifier.weight(1f)
                )

                AppleNavTabItem(
                    label = "New",
                    iconRes = R.drawable.ic_nav_new,
                    isHighlighted = !isSearchActive && (if (isHolding) hoveredIndex == 1 else selectedTab == NavTab.NEW),
                    isHolding = isHolding,
                    modifier = Modifier.weight(1f)
                )

                AppleNavTabItem(
                    label = "Radio",
                    iconRes = R.drawable.ic_nav_radio,
                    isHighlighted = !isSearchActive && (if (isHolding) hoveredIndex == 2 else selectedTab == NavTab.RADIO),
                    isHolding = isHolding,
                    modifier = Modifier.weight(1f)
                )

                AppleNavTabItem(
                    label = "Library",
                    iconRes = R.drawable.ic_nav_library,
                    isHighlighted = !isSearchActive && (if (isHolding) hoveredIndex == 3 else selectedTab == NavTab.LIBRARY),
                    isHolding = isHolding,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Circular Apple Search Button (Highlighted in Apple Music Red when Search Screen is active)
        Box(
            modifier = Modifier
                .size(dockHeight)
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    ambientColor = if (isSearchActive) AppleMusicRed.copy(alpha = 0.35f) else Color.Black,
                    spotColor = if (isSearchActive) AppleMusicRed.copy(alpha = 0.35f) else Color.Black
                )
                .clip(CircleShape)
                .background(
                    if (isSearchActive) Color(0x38FFFFFF) else AppleGlassDock
                )
                .border(
                    width = 0.75.dp,
                    color = if (isSearchActive) AppleMusicRed.copy(alpha = 0.60f) else AppleGlassBorder,
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onSearchClick() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_nav_search),
                    contentDescription = "Search",
                    tint = if (isSearchActive) AppleMusicRed else Color.White.copy(alpha = 0.95f),
                    modifier = Modifier.size(if (isSearchActive) 20.dp else 23.dp)
                )
                if (isSearchActive) {
                    Spacer(modifier = Modifier.height(1.dp))
                    @Suppress("DEPRECATION")
                    Text(
                        text = "Search",
                        color = AppleMusicRed,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                    )
                }
            }
        }
    }
}

@Composable
private fun AppleNavTabItem(
    label: String,
    iconRes: Int,
    isHighlighted: Boolean,
    isHolding: Boolean,
    modifier: Modifier = Modifier
) {
    // 1. Initially selected shell at rest: Apple Music Red (matches media_1794248305825_dc654f82.png)
    // 2. When tap-and-held inside 3D glassy water droplet: Color.White (matches media_1794246658446_33556023.png)
    // 3. Unselected tabs: White with 0.85 alpha
    val targetColor = when {
        isHighlighted && isHolding -> Color.White
        isHighlighted -> AppleMusicRed
        else -> Color.White.copy(alpha = 0.85f)
    }

    val contentColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 140),
        label = "TabContentColor"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(21.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            @Suppress("DEPRECATION")
            Text(
                text = label,
                color = contentColor,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
                letterSpacing = (-0.2).sp,
                style = TextStyle(
                    platformStyle = PlatformTextStyle(
                        includeFontPadding = false
                    )
                )
            )
        }
    }
}
