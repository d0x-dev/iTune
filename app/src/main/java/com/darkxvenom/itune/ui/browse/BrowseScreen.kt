package com.darkxvenom.itune.ui.browse

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.data.library.BrowseMoodsRepository
import com.darkxvenom.itune.data.library.ContentLocaleCatalog
import com.darkxvenom.itune.data.library.NewReleasesRepository
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.data.model.BrowseCategoryItem
import com.darkxvenom.itune.data.model.BrowseSegment
import com.darkxvenom.itune.data.model.BrowseType
import com.darkxvenom.itune.data.model.CARD_ART_PX
import com.darkxvenom.itune.data.model.PendingArtistNav
import com.darkxvenom.itune.data.model.artworkAt
import com.darkxvenom.itune.ui.components.AppleMemojiAvatar
import com.darkxvenom.itune.ui.components.TopSpringLoadingBar
import com.darkxvenom.itune.ui.components.rememberSpringOverscrollState
import com.darkxvenom.itune.ui.components.springOverscroll
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import com.darkxvenom.itune.ui.theme.AppleTextPrimary
import com.darkxvenom.itune.ui.theme.AppleTextSecondary
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun BrowseScreen(
    modifier: Modifier = Modifier,
    onCategoryClick: (BrowseCategoryItem) -> Unit = {},
    onProfileClick: () -> Unit = {},
    onOpenContentSettings: () -> Unit = onProfileClick,
) {
    val context = LocalContext.current
    val gridState = rememberLazyGridState()
    val overscrollState = rememberSpringOverscrollState()
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedSegment by remember { mutableStateOf(BrowseSegment.CATALOG) }
    val categories by BrowseMoodsRepository.categories.collectAsStateWithLifecycle()
    val moodsSyncing by BrowseMoodsRepository.isRefreshing.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        BrowseMoodsRepository.ensureLoaded()
    }

    // ONLY trigger loading when pulling down from the very top of the grid
    LaunchedEffect(overscrollState.offset.value) {
        if (gridState.firstVisibleItemIndex == 0 && overscrollState.offset.value > 25f && !isRefreshing) {
            isRefreshing = true
            BrowseMoodsRepository.refresh()
        }
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            delay(3500)
            isRefreshing = false
        }
    }

    // Top black fade shade on scroll
    val isScrolled by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 4
        }
    }

    val topShadeAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "BrowseTopShadeAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppleOledBlack)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .springOverscroll(overscrollState),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 190.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Section: Serif "Browse" Title + Profile Avatar + Segmented Tab Control
            item(span = { GridItemSpan(2) }, key = "browse_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 8.dp, bottom = 14.dp)
                ) {
                    // Title Row: Display Serif "Browse" + Profile Avatar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Browse",
                            color = AppleTextPrimary,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )

                        AppleMemojiAvatar(
                            size = 38.dp,
                            onClick = onProfileClick
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Apple Segmented Control: Catalog | Playlists | Instruments
                    BrowseSegmentedControl(
                        selectedSegment = selectedSegment,
                        onSegmentSelected = { segment ->
                            selectedSegment = segment
                        }
                    )
                }
            }

            // 2-Column AirBeats Moods & Moments Cards with Pre-set 3D Artwork Images
            items(categories, key = { it.id }) { item ->
                BrowseCategoryCardItem(
                    item = item,
                    onClick = {
                        onCategoryClick(item)
                    }
                )
            }
        }

        // Top Status Bar Area Black Blur Shade Scrim: Only appears when scrolling down
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(110.dp)
                .graphicsLayer { alpha = topShadeAlpha }
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            AppleOledBlack,
                            AppleOledBlack.copy(alpha = 0.94f),
                            AppleOledBlack.copy(alpha = 0.65f),
                            AppleOledBlack.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Sleek Left-to-Right Animated Loading Line: ONLY visible when at the top
        TopSpringLoadingBar(
            isVisible = (isRefreshing || moodsSyncing) && gridState.firstVisibleItemIndex == 0,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun BrowseSegmentedControl(
    selectedSegment: BrowseSegment,
    onSegmentSelected: (BrowseSegment) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0xFF1C1C1E))
            .padding(2.5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrowseSegment.entries.forEach { segment ->
                val isSelected = segment == selectedSegment

                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFF38383A) else Color.Transparent,
                    animationSpec = tween(durationMillis = 160),
                    label = "SegmentBgColor"
                )

                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else AppleTextSecondary,
                    animationSpec = tween(durationMillis = 160),
                    label = "SegmentTextColor"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(7.dp))
                        .background(bgColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSegmentSelected(segment) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = segment.title,
                        color = textColor,
                        fontSize = 13.5.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        letterSpacing = (-0.2).sp
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowseCategoryCardItem(
    item: BrowseCategoryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.448f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E1E24))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
    ) {
        AsyncImage(
            model = item.imageUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Apple Classical category title in crisp San Francisco typography with soft shadow
        Text(
            text = item.title,
            color = Color.White,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
            style = androidx.compose.ui.text.TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color.Black.copy(alpha = 0.65f),
                    blurRadius = 6f
                )
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 9.dp)
        )
    }
}
