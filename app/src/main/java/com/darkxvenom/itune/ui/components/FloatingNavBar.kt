package com.darkxvenom.itune.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
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

    val tabCount = 4
    val slotWidthPx = if (dockWidthPx > 0f) dockWidthPx / tabCount else 0f

    val bubbleWidthPx = with(density) { 58.dp.toPx() }
    val bubbleHeightPx = with(density) { 48.dp.toPx() }

    val restingX = if (slotWidthPx > 0f && selectedTab != null) {
        selectedTab.ordinal * slotWidthPx + (slotWidthPx - bubbleWidthPx) / 2f
    } else 0f

    val targetBubbleX = if (isHolding) {
        (dragFingerX - bubbleWidthPx / 2f).coerceIn(-14f, dockWidthPx - bubbleWidthPx + 14f)
    } else {
        restingX
    }

    val currentBubbleX by animateFloatAsState(
        targetValue = targetBubbleX,
        animationSpec = if (isHolding) {
            spring(dampingRatio = 0.85f, stiffness = 650f)
        } else {
            spring(dampingRatio = 0.55f, stiffness = 220f)
        },
        label = "BubbleX"
    )

    val targetAlpha = when {
        isHolding -> 0.52f
        isSearchActive -> 0.0f
        selectedTab != null -> 0.22f
        else -> 0.0f
    }

    val bubbleAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 180),
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
                        val currentTabStart = (selectedTab?.ordinal ?: -1) * sWidth
                        val currentTabEnd = currentTabStart + sWidth

                        if (selectedTab != null && !isSearchActive && down.position.x in currentTabStart..currentTabEnd) {
                            var isDragging = false
                            dragFingerX = down.position.x
                            hoveredIndex = selectedTab.ordinal

                            val holdJob = coroutineScope.launch {
                                delay(120)
                                isHolding = true
                                bubbleScale.animateTo(
                                    targetValue = 1.44f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = 160f
                                    )
                                )
                            }

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break

                                val moveDelta = abs(change.position.x - down.position.x)
                                if (moveDelta > 6f) {
                                    isDragging = true
                                    dragFingerX = change.position.x
                                    val currentSlot = (change.position.x / sWidth).toInt().coerceIn(0, 3)
                                    hoveredIndex = currentSlot

                                    if (!isHolding) {
                                        isHolding = true
                                        coroutineScope.launch {
                                            bubbleScale.animateTo(
                                                targetValue = 1.44f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = 160f
                                                )
                                            )
                                        }
                                    }
                                    change.consume()
                                }
                            }

                            holdJob.cancel()
                            val finalFingerX = dragFingerX
                            isHolding = false

                            coroutineScope.launch {
                                bubbleScale.animateTo(
                                    targetValue = 1.0f,
                                    animationSpec = spring(
                                        dampingRatio = 0.52f,
                                        stiffness = 200f
                                    )
                                )
                            }

                            if (isDragging) {
                                val destinationSlot = (finalFingerX / sWidth).toInt().coerceIn(0, 3)
                                onTabSelected(NavTab.entries[destinationSlot])
                            }
                        } else {
                            val targetSlot = (down.position.x / sWidth).toInt().coerceIn(0, 3)
                            var isDrag = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    if (!isDrag) {
                                        onTabSelected(NavTab.entries[targetSlot])
                                    }
                                    break
                                }
                                if (abs(change.position.x - down.position.x) > 10f) {
                                    isDrag = true
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // Layer 1: Dock Capsule Background
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

            // Layer 2: Selected Highlight / Water-Drop Bubble Indicator
            if (dockWidthPx > 0f && (selectedTab != null || isHolding)) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(currentBubbleX.roundToInt(), 0) }
                        .align(Alignment.CenterStart)
                        .width(with(density) { bubbleWidthPx.toDp() })
                        .height(with(density) { bubbleHeightPx.toDp() })
                        .graphicsLayer {
                            scaleX = bubbleScale.value
                            scaleY = bubbleScale.value
                            alpha = bubbleAlpha
                        }
                        .shadow(
                            elevation = if (isHolding) 18.dp else 0.dp,
                            shape = RoundedCornerShape(percent = 50),
                            ambientColor = if (isHolding) AppleMusicRed.copy(alpha = 0.50f) else Color.Transparent,
                            spotColor = if (isHolding) Color.White.copy(alpha = 0.55f) else Color.Transparent
                        )
                        .clip(RoundedCornerShape(percent = 50))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = bubbleAlpha),
                                    Color.White.copy(alpha = bubbleAlpha * 0.70f)
                                )
                            )
                        )
                        .border(
                            width = if (isHolding) 1.2.dp else 0.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (isHolding) 0.85f else 0f),
                                    Color.White.copy(alpha = if (isHolding) 0.40f else 0f)
                                )
                            ),
                            shape = RoundedCornerShape(percent = 50)
                        )
                )
            }

            // Layer 3: Tabs Row
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
                    onClick = { onTabSelected(NavTab.HOME) },
                    modifier = Modifier.weight(1f)
                )

                AppleNavTabItem(
                    label = "New",
                    iconRes = R.drawable.ic_nav_new,
                    isHighlighted = !isSearchActive && (if (isHolding) hoveredIndex == 1 else selectedTab == NavTab.NEW),
                    onClick = { onTabSelected(NavTab.NEW) },
                    modifier = Modifier.weight(1f)
                )

                AppleNavTabItem(
                    label = "Radio",
                    iconRes = R.drawable.ic_nav_radio,
                    isHighlighted = !isSearchActive && (if (isHolding) hoveredIndex == 2 else selectedTab == NavTab.RADIO),
                    onClick = { onTabSelected(NavTab.RADIO) },
                    modifier = Modifier.weight(1f)
                )

                AppleNavTabItem(
                    label = "Library",
                    iconRes = R.drawable.ic_nav_library,
                    isHighlighted = !isSearchActive && (if (isHolding) hoveredIndex == 3 else selectedTab == NavTab.LIBRARY),
                    onClick = { onTabSelected(NavTab.LIBRARY) },
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = AppleMusicRed
    val inactiveColor = Color.White.copy(alpha = 0.92f)

    val contentColor by animateColorAsState(
        targetValue = if (isHighlighted) activeColor else inactiveColor,
        animationSpec = tween(durationMillis = 160),
        label = "TabContentColor"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
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
