package com.darkxvenom.itune.ui.player

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.AudioDownloadOption
import com.darkxvenom.itune.backend.AudioDownloader
import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.data.model.BrowseType
import com.darkxvenom.itune.data.model.PendingArtistNav
import com.darkxvenom.itune.backend.formatMusicDuration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

private enum class MorePopupPage {
    MAIN,
    DOWNLOAD,
    LYRICS_MENU,
    LYRICS_SEARCH,
    LYRICS_EDIT,
    LYRICS_OFFSET,
    DETAILS,
    SLEEP_TIMER
}

private fun lerpFloatPopup(start: Float, stop: Float, fraction: Float): Float {
    val f = fraction.coerceIn(0f, 1f)
    return start + (stop - start) * f
}

private fun lerpDpPopup(start: Dp, stop: Dp, fraction: Float): Dp =
    (start.value + (stop.value - start.value) * fraction.coerceIn(0f, 1f)).coerceAtLeast(0f).dp

/**
 * Apple Music styled floating More Options card anchored with a fluid water-drop bloom
 * & ripple wave originating from the 3-dot button, tinted dynamically with a blurred copy
 * of the active track's artwork.
 */
/** Shared Apple frosted liquid glass popup overlay with water-drop spring bloom, ripples, blurred art backdrop, marquee header, and drag-dismiss. */
@Composable
internal fun AppleFrostedPopupOverlay(
    visible: Boolean,
    title: String,
    subtitle: String,
    artUrl: String?,
    anchorCenterXPx: Float,
    anchorCenterYPx: Float,
    maxHeightDp: Dp,
    statusBarTopDp: Dp,
    rootCoordsProvider: () -> LayoutCoordinates?,
    onDismiss: () -> Unit,
    onBack: () -> Unit = onDismiss,
    isTopAnchored: Boolean = false,
    bottomClearanceDp: Dp? = null,
    hasInputFocusPage: Boolean = false,
    onExited: () -> Unit = {},
    headerActions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    menuContent: @Composable () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var shouldRender by remember { mutableStateOf(false) }
    var dragDismissOffsetPx by remember { mutableFloatStateOf(0f) }

    val openProgress = remember { Animatable(0f) }
    val rippleProgress = remember { Animatable(0f) }
    val liquidFlowProgress = remember { Animatable(0f) }

    var cardCenterXPx by remember { mutableFloatStateOf(0f) }
    var cardCenterYPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(visible) {
        if (visible) {
            dragDismissOffsetPx = 0f
            shouldRender = true
            launch {
                rippleProgress.snapTo(0f)
                rippleProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 680,
                        easing = CubicBezierEasing(0.14f, 0.86f, 0.28f, 1f)
                    )
                )
            }
            launch {
                liquidFlowProgress.snapTo(0f)
                liquidFlowProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 560,
                        easing = FastOutSlowInEasing
                    )
                )
            }
            openProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = 200f
                )
            )
        } else if (shouldRender) {
            launch {
                liquidFlowProgress.animateTo(0f, tween(220))
            }
            openProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 280,
                    easing = FastOutSlowInEasing
                )
            )
            shouldRender = false
            dragDismissOffsetPx = 0f
            onExited()
        }
    }

    BackHandler(enabled = visible, onBack = onBack)

    if (!shouldRender) return

    val p = openProgress.value
    val pClamped = p.coerceIn(0f, 1f)
    val rippleP = rippleProgress.value.coerceIn(0f, 1f)
    val flowP = liquidFlowProgress.value.coerceIn(0f, 1f)

    // Subtle fluid droplet stretch during bloom (elongates slightly along flow path before settling)
    val stretchFactor = (sin(pClamped * PI).toFloat() * 0.055f).coerceAtLeast(0f)
    val cardScaleX = (lerpFloatPopup(0.08f, 1f, p) + (p - pClamped) * 0.6f) * (1f - stretchFactor * 0.35f)
    val cardScaleY = (lerpFloatPopup(0.08f, 1f, p) + (p - pClamped) * 0.6f) * (1f + stretchFactor)
    val cardAlpha = (pClamped / 0.18f).coerceIn(0f, 1f) * (1f - (dragDismissOffsetPx / 420f).coerceIn(0f, 0.45f))
    val cornerDp = lerpDpPopup(84.dp, 28.dp, pClamped)
    val cardShape = RoundedCornerShape(cornerDp)

    val cardAnchorDx = if (cardCenterXPx > 0f && anchorCenterXPx > 0f) (anchorCenterXPx - cardCenterXPx) * (1f - pClamped) else 0f
    val cardAnchorDy = if (cardCenterYPx > 0f && anchorCenterYPx > 0f) (anchorCenterYPx - cardCenterYPx) * (1f - pClamped) else 0f

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // 1. Soft Dimming Backdrop Scrim (tapping outside dismisses cleanly)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.46f * pClamped))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    onDismiss()
                }
        )

        // 2. Water-Drop Concentric Flow Ripples radiating from the 3-dot button center
        if (rippleP > 0.001f && rippleP < 0.999f && anchorCenterXPx > 0f && anchorCenterYPx > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val origin = Offset(anchorCenterXPx, anchorCenterYPx)
                val maxRadius1 = 290.dp.toPx()
                val r1 = 16.dp.toPx() + (maxRadius1 - 16.dp.toPx()) * rippleP
                val a1 = (1f - rippleP) * (1f - rippleP) * 0.46f

                drawCircle(
                    color = Color.White.copy(alpha = a1 * 0.25f),
                    radius = r1 * 0.55f,
                    center = origin
                )
                drawCircle(
                    color = Color.White.copy(alpha = a1),
                    radius = r1,
                    center = origin,
                    style = Stroke(width = (3.2f * (1f - rippleP) + 0.6f).dp.toPx())
                )

                val wave2P = ((rippleP - 0.16f) / 0.84f).coerceIn(0f, 1f)
                if (wave2P > 0f) {
                    val maxRadius2 = 210.dp.toPx()
                    val r2 = 12.dp.toPx() + (maxRadius2 - 12.dp.toPx()) * wave2P
                    val a2 = (1f - wave2P) * (1f - wave2P) * 0.36f
                    drawCircle(
                        color = Color.White.copy(alpha = a2),
                        radius = r2,
                        center = origin,
                        style = Stroke(width = (2.4f * (1f - wave2P) + 0.5f).dp.toPx())
                    )
                }
            }
        }

        // 3. Un-transformed Positioning Wrapper (keeps onGloballyPositioned stable during zoom animation)
        val placementModifier = when {
            isTopAnchored -> {
                Modifier
                    .align(Alignment.TopCenter)
                    .then(if (hasInputFocusPage) Modifier.imePadding() else Modifier)
                    .padding(top = statusBarTopDp + 98.dp, start = 16.dp, end = 16.dp, bottom = 20.dp)
            }
            bottomClearanceDp != null -> {
                Modifier
                    .align(Alignment.BottomCenter)
                    .then(if (hasInputFocusPage) Modifier.imePadding() else Modifier)
                    .padding(
                        top = statusBarTopDp + 16.dp,
                        start = 16.dp,
                        end = 16.dp,
                        bottom = if (hasInputFocusPage) 20.dp else bottomClearanceDp
                    )
            }
            else -> {
                Modifier
                    .align(Alignment.Center)
                    .then(if (hasInputFocusPage) Modifier.imePadding() else Modifier)
                    .padding(top = statusBarTopDp + 16.dp, start = 16.dp, end = 16.dp, bottom = 24.dp)
            }
        }

        Box(
            modifier = placementModifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    val root = rootCoordsProvider()
                    if (root != null && root.isAttached && coords.isAttached) {
                        val pos = root.localPositionOf(coords, Offset.Zero)
                        cardCenterXPx = pos.x + coords.size.width / 2f
                        cardCenterYPx = pos.y + coords.size.height / 2f
                    }
                }
        ) {
            // 4. Liquid Frosted Popup Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationX = cardAnchorDx
                        translationY = cardAnchorDy + dragDismissOffsetPx
                        scaleX = cardScaleX.coerceAtLeast(0.01f)
                        scaleY = cardScaleY.coerceAtLeast(0.01f)
                        alpha = cardAlpha
                    }
                    .shadow(
                        elevation = (24.dp * pClamped).coerceAtLeast(0.dp),
                        shape = cardShape,
                        ambientColor = Color.Black,
                        spotColor = Color.Black
                    )
                    .clip(cardShape)
                    .pointerInput(visible) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragDismissOffsetPx > 110f) {
                                    onDismiss()
                                } else {
                                    scope.launch {
                                        val startDrag = dragDismissOffsetPx
                                        androidx.compose.animation.core.animate(
                                            initialValue = startDrag,
                                            targetValue = 0f,
                                            animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f)
                                        ) { v, _ -> dragDismissOffsetPx = v }
                                    }
                                }
                            },
                            onDragCancel = { dragDismissOffsetPx = 0f },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val next = dragDismissOffsetPx + dragAmount
                                dragDismissOffsetPx = if (next < 0f) next * 0.25f else next
                            }
                        )
                    }
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* consume taps inside card */ }
            ) {
                // Background Layer: Heavily blurred current track artwork matching card color
                if (!artUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(artUrl)
                            .size(Size.ORIGINAL)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .blur(64.dp)
                            .graphicsLayer {
                                scaleX = 1.45f
                                scaleY = 1.45f
                                alpha = 0.94f
                            }
                    )
                }

                // Translucent Apple Frosted Glass Tint Overlay preserving dynamic album hues
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF28262A).copy(alpha = 0.46f),
                                    Color(0xFF1C1B1E).copy(alpha = 0.56f),
                                    Color(0xFF141316).copy(alpha = 0.62f)
                                )
                            )
                        )
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to Color.White.copy(alpha = 0.11f),
                                    0.35f to Color.White.copy(alpha = 0.03f),
                                    1f to Color.Transparent
                                )
                            )
                        )
                        // Caustic water-flow light wave traveling across glass during droplet bloom
                        .drawWithContent {
                            drawContent()
                            if (flowP > 0.01f && flowP < 0.99f) {
                                val waveFade = sin(flowP * PI).toFloat() * 0.18f
                                val cx = size.width * (1f - flowP * 0.85f)
                                val cy = size.height * (if (isTopAnchored) flowP else 1f - flowP)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = waveFade),
                                            Color.Transparent
                                        ),
                                        center = Offset(cx, cy),
                                        radius = size.maxDimension * 0.65f
                                    ),
                                    radius = size.maxDimension * 0.65f,
                                    center = Offset(cx, cy)
                                )
                            }
                        }
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.24f),
                                    Color.White.copy(alpha = 0.09f)
                                )
                            ),
                            shape = cardShape
                        )
                )

                // Card Foreground Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = (maxHeightDp - statusBarTopDp - 110.dp).coerceAtLeast(280.dp))
                        .verticalScroll(rememberScrollState())
                ) {
                    // Top Grabber Handle Pill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 36.dp, height = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.44f))
                        )
                    }

                    // Header Summary (Artwork + Single-Line Fading Marquee Title/Subtitle + Action Buttons)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(66.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(0.75.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(artUrl)
                                    .size(Size.ORIGINAL)
                                    .build(),
                                contentDescription = title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            PopupMarqueeFadeText(
                                text = title,
                                style = TextStyle(
                                    fontFamily = SFProDisplay,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    lineHeight = 20.sp,
                                    letterSpacing = (-0.25).sp
                                ),
                                color = Color.White
                            )
                            if (subtitle.isNotBlank()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                PopupMarqueeFadeText(
                                    text = subtitle,
                                    style = TextStyle(
                                        fontFamily = SFProDisplay,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 14.sp,
                                        lineHeight = 17.sp
                                    ),
                                    color = Color.White.copy(alpha = 0.66f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            content = headerActions
                        )
                    }

                    // Inner Dark Translucent Container holding menu options / sub-pages
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.Black.copy(alpha = 0.24f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                    ) {
                        menuContent()
                    }
                }
            }
        }
    }
}

@Composable
fun AppleMoreOptionsPopup(
    visible: Boolean,
    track: MusicTrack,
    artUrl: String?,
    anchorCenterXPx: Float,
    anchorCenterYPx: Float,
    isPanelOpen: Boolean,
    statusBarTopDp: Dp,
    titleRowTopDp: Dp,
    maxHeightDp: Dp,
    rootCoordsProvider: () -> LayoutCoordinates?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    var activePage by remember { mutableStateOf(MorePopupPage.MAIN) }

    LaunchedEffect(visible) {
        if (visible) activePage = MorePopupPage.MAIN
    }

    val bottomClearanceDp = if (isPanelOpen) {
        0.dp
    } else {
        (maxHeightDp - titleRowTopDp + 16.dp).coerceIn(180.dp, maxHeightDp - statusBarTopDp - 140.dp)
    }

    AppleFrostedPopupOverlay(
        visible = visible,
        title = track.title,
        subtitle = track.artists,
        artUrl = artUrl,
        anchorCenterXPx = anchorCenterXPx,
        anchorCenterYPx = anchorCenterYPx,
        maxHeightDp = maxHeightDp,
        statusBarTopDp = statusBarTopDp,
        rootCoordsProvider = rootCoordsProvider,
        onDismiss = onDismiss,
        onBack = {
            when (activePage) {
                MorePopupPage.LYRICS_SEARCH,
                MorePopupPage.LYRICS_EDIT,
                MorePopupPage.LYRICS_OFFSET -> activePage = MorePopupPage.LYRICS_MENU
                MorePopupPage.MAIN -> onDismiss()
                else -> activePage = MorePopupPage.MAIN
            }
        },
        isTopAnchored = isPanelOpen,
        bottomClearanceDp = bottomClearanceDp,
        hasInputFocusPage = activePage == MorePopupPage.LYRICS_SEARCH || activePage == MorePopupPage.LYRICS_EDIT,
        modifier = modifier,
        headerActions = {
            val isFavorite = MusicPlayerState.isFavorite(track.id)
            HeaderCircleActionButton(
                iconRes = R.drawable.ic_menu_share,
                contentDescription = "Share",
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    val link = "https://music.youtube.com/watch?v=${track.id}"
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Listen to ${track.title} by ${track.artists}\n$link"
                        )
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Song"))
                    onDismiss()
                }
            )
            HeaderCircleActionButton(
                iconRes = R.drawable.ic_menu_copy_link,
                contentDescription = "Copy link",
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    val link = "https://music.youtube.com/watch?v=${track.id}"
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Track Link", link))
                    Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            )
            HeaderCircleActionButton(
                iconRes = if (isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline,
                contentDescription = if (isFavorite) "Favorited" else "Favorite",
                highlighted = isFavorite,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    MusicPlayerState.toggleFavorite(track.id)
                }
            )
        }
    ) {
                        AnimatedContent(
                            targetState = activePage,
                            transitionSpec = {
                                val navigatingBack = targetState == MorePopupPage.MAIN ||
                                    (targetState == MorePopupPage.LYRICS_MENU &&
                                        (initialState == MorePopupPage.LYRICS_SEARCH ||
                                            initialState == MorePopupPage.LYRICS_EDIT ||
                                            initialState == MorePopupPage.LYRICS_OFFSET))
                                if (navigatingBack) {
                                    (slideInHorizontally(tween(240)) { -it / 4 } + fadeIn(tween(200))) togetherWith
                                        (slideOutHorizontally(tween(200)) { it / 4 } + fadeOut(tween(160)))
                                } else {
                                    (slideInHorizontally(tween(240)) { it / 4 } + fadeIn(tween(200))) togetherWith
                                        (slideOutHorizontally(tween(200)) { -it / 4 } + fadeOut(tween(160)))
                                }
                            },
                            label = "morePopupSubPage"
                        ) { page ->
                            when (page) {
                                MorePopupPage.MAIN -> {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        MorePopupOptionRow(
                                            iconRes = R.drawable.ic_menu_download,
                                            title = "Download",
                                            onClick = {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                activePage = MorePopupPage.DOWNLOAD
                                            }
                                        )
                                        PopupMenuDivider()

                                        val lyricsBadge = when {
                                            MusicPlayerState.lyricsOffsetMs != 0L -> formatLyricsOffsetLabel(MusicPlayerState.lyricsOffsetMs)
                                            MusicPlayerState.hasCustomLyricsOverride -> "Custom"
                                            else -> null
                                        }
                                        MorePopupOptionRow(
                                            iconRes = R.drawable.ic_menu_lyrics,
                                            title = "Lyrics",
                                            trailingBadge = lyricsBadge,
                                            onClick = {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                activePage = MorePopupPage.LYRICS_MENU
                                            }
                                        )
                                        PopupMenuDivider()

                                        MorePopupOptionRow(
                                            iconRes = R.drawable.ic_menu_details,
                                            title = "Details",
                                            onClick = {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                activePage = MorePopupPage.DETAILS
                                            }
                                        )
                                        PopupMenuDivider()

                                        MorePopupOptionRow(
                                            iconRes = R.drawable.ic_menu_artist,
                                            title = "Go to artist",
                                            onClick = {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                val primaryArtist = track.artists.substringBefore(",").trim()
                                                    .ifBlank { track.artists }
                                                onDismiss()
                                                MusicPlayerState.lyricsOpen = false
                                                MusicPlayerState.queueOpen = false
                                                MusicPlayerState.expanded = false
                                                MusicPlayerState.pendingArtistNav = PendingArtistNav(
                                                    browseId = track.artistId,
                                                    name = primaryArtist,
                                                    thumbnailUrl = track.thumbnailUrl?.ifBlank { null },
                                                    initialType = BrowseType.ARTIST,
                                                )
                                            }
                                        )
                                        PopupMenuDivider()

                                        val timerBadge = when {
                                            MusicPlayerState.sleepTimerEndOfTrack -> "End of song"
                                            MusicPlayerState.sleepTimerRemainingMs > 0L -> {
                                                val mins = ((MusicPlayerState.sleepTimerRemainingMs + 59_999L) / 60_000L)
                                                "${mins}m"
                                            }
                                            else -> null
                                        }
                                        MorePopupOptionRow(
                                            iconRes = R.drawable.ic_menu_sleep_timer,
                                            title = "Sleep Timer",
                                            trailingBadge = timerBadge,
                                            onClick = {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                activePage = MorePopupPage.SLEEP_TIMER
                                            }
                                        )
                                    }
                                }

                                MorePopupPage.DOWNLOAD -> {
                                    MorePopupDownloadSection(
                                        track = track,
                                        onBack = { activePage = MorePopupPage.MAIN },
                                        onDownloadStarted = { onDismiss() }
                                    )
                                }

                                MorePopupPage.LYRICS_MENU -> {
                                    MorePopupLyricsMenuSection(
                                        onBack = { activePage = MorePopupPage.MAIN },
                                        onOpenSearch = { activePage = MorePopupPage.LYRICS_SEARCH },
                                        onOpenEdit = { activePage = MorePopupPage.LYRICS_EDIT },
                                        onOpenOffset = { activePage = MorePopupPage.LYRICS_OFFSET }
                                    )
                                }

                                MorePopupPage.LYRICS_SEARCH -> {
                                    MorePopupLyricsSearchSection(
                                        track = track,
                                        onBack = { activePage = MorePopupPage.LYRICS_MENU },
                                        onLyricsSelected = { onDismiss() }
                                    )
                                }

                                MorePopupPage.LYRICS_EDIT -> {
                                    MorePopupLyricsEditSection(
                                        track = track,
                                        onBack = { activePage = MorePopupPage.LYRICS_MENU },
                                        onSaved = { onDismiss() }
                                    )
                                }

                                MorePopupPage.LYRICS_OFFSET -> {
                                    MorePopupLyricsOffsetSection(
                                        onBack = { activePage = MorePopupPage.LYRICS_MENU }
                                    )
                                }

                                MorePopupPage.DETAILS -> {
                                    MorePopupDetailsSection(
                                        track = track,
                                        onBack = { activePage = MorePopupPage.MAIN }
                                    )
                                }

                                MorePopupPage.SLEEP_TIMER -> {
                                    MorePopupSleepTimerSection(
                                        onBack = { activePage = MorePopupPage.MAIN },
                                        onSelectDone = { onDismiss() }
                                    )
                                }
                            }
                        }
    }
}

@Composable
internal fun HeaderCircleActionButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(33.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (highlighted) 0.24f else 0.14f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(Color.White.copy(alpha = if (highlighted) 1f else 0.90f)),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun MorePopupRowFrame(
    title: String,
    trailingBadge: String? = null,
    onClick: () -> Unit,
    leadingIcon: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leadingIcon()
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = TextStyle(
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.Medium,
                fontSize = 15.5.sp,
                letterSpacing = (-0.15).sp
            ),
            color = Color.White.copy(alpha = 0.94f),
            modifier = Modifier.weight(1f)
        )
        if (!trailingBadge.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.16f))
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Text(
                    text = trailingBadge,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
internal fun MorePopupOptionRow(
    @DrawableRes iconRes: Int,
    title: String,
    trailingBadge: String? = null,
    onClick: () -> Unit
) {
    MorePopupRowFrame(title = title, trailingBadge = trailingBadge, onClick = onClick) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = title,
            colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.92f)),
            modifier = Modifier.size(21.dp)
        )
    }
}

@Composable
internal fun MorePopupVectorOptionRow(
    icon: ImageVector,
    title: String,
    iconTint: Color = Color.White.copy(alpha = 0.92f),
    trailingBadge: String? = null,
    onClick: () -> Unit
) {
    MorePopupRowFrame(title = title, trailingBadge = trailingBadge, onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconTint,
            modifier = Modifier.size(21.dp)
        )
    }
}

@Composable
internal fun PopupMenuDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = Color.White.copy(alpha = 0.12f)
    )
}

@Composable
internal fun PopupSubPageHeader(
    title: String,
    trailingBadge: String? = null,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .padding(start = 6.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = title,
            fontFamily = SFProDisplay,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.5.sp,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        if (!trailingBadge.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.14f))
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Text(
                    text = trailingBadge,
                    fontFamily = SFProDisplay,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.88f)
                )
            }
        }
    }
}

@Composable
private fun MorePopupDownloadSection(
    track: MusicTrack,
    onBack: () -> Unit,
    onDownloadStarted: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    var qualities by remember(track.id) { mutableStateOf<List<AudioDownloadOption>?>(null) }
    var isLoading by remember(track.id) { mutableStateOf(true) }
    var loadError by remember(track.id) { mutableStateOf<String?>(null) }
    var reloadNonce by remember { mutableIntStateOf(0) }
    var pendingOption by remember { mutableStateOf<AudioDownloadOption?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val opt = pendingOption
        pendingOption = null
        if (opt != null) {
            AudioDownloader.download(context, track, opt)
            Toast.makeText(
                context,
                if (granted) "Download started · Check notification" else "Download started (allow notifications to view progress)",
                Toast.LENGTH_SHORT
            ).show()
            onDownloadStarted()
        }
    }

    fun triggerDownload(option: AudioDownloadOption) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingOption = option
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            AudioDownloader.download(context, track, option)
            Toast.makeText(context, "Download started · Check notification", Toast.LENGTH_SHORT).show()
            onDownloadStarted()
        }
    }

    LaunchedEffect(track.id, reloadNonce) {
        isLoading = true
        loadError = null
        try {
            val extracted = MusicBackend.extractAudioQualities(track.id)
            qualities = extracted
            if (extracted.isEmpty()) {
                loadError = "No downloadable audio streams available"
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            loadError = e.message?.takeIf { it.isNotBlank() } ?: "Could not load audio qualities"
        } finally {
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        PopupSubPageHeader(
            title = "Download Audio",
            trailingBadge = "Audio Only",
            onBack = onBack
        )
        PopupMenuDivider()

        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(26.dp)
                        )
                        Text(
                            text = "Fetching audio qualities…",
                            fontFamily = SFProDisplay,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.68f)
                        )
                    }
                }
            }

            qualities.isNullOrEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = loadError ?: "No audio qualities found",
                        fontFamily = SFProDisplay,
                        fontSize = 13.5.sp,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.16f))
                            .clickable { reloadNonce++ }
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "Retry",
                            fontFamily = SFProDisplay,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }

            else -> {
                qualities.orEmpty().forEachIndexed { idx, opt ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { triggerDownload(opt) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (opt.isHighQuality) Color(0xFFFA2D48).copy(alpha = 0.22f)
                                    else Color.White.copy(alpha = 0.12f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (opt.isHighQuality) Icons.Rounded.Headphones else Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = if (opt.isHighQuality) Color(0xFFFF6477) else Color.White.copy(alpha = 0.90f),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(13.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = opt.label,
                                    fontFamily = SFProDisplay,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = Color.White.copy(alpha = 0.96f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (opt.isHighQuality) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.White.copy(alpha = 0.18f))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "HQ",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${opt.formatLabel}${opt.approxSize?.let { " · ~$it" } ?: ""}",
                                fontFamily = SFProDisplay,
                                fontSize = 12.5.sp,
                                color = Color.White.copy(alpha = 0.62f)
                            )
                        }

                        Image(
                            painter = painterResource(id = R.drawable.ic_menu_download),
                            contentDescription = "Download ${opt.label}",
                            colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.85f)),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    if (idx < qualities.orEmpty().lastIndex) {
                        PopupMenuDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun MorePopupDetailsSection(
    track: MusicTrack,
    onBack: () -> Unit
) {
    val durText = formatMusicDuration(
        if (MusicPlayerState.durationMs > 0) MusicPlayerState.durationMs / 1000L else track.durationSec
    ).ifBlank { "Live / Unknown" }

    Column(modifier = Modifier.fillMaxWidth()) {
        PopupSubPageHeader(title = "Track Details", onBack = onBack)
        PopupMenuDivider()
        DetailInfoRow(label = "Title", value = track.title)
        PopupMenuDivider()
        DetailInfoRow(label = "Artist", value = track.artists)
        if (!track.album.isNullOrBlank()) {
            PopupMenuDivider()
            DetailInfoRow(label = "Album", value = track.album)
        }
        PopupMenuDivider()
        DetailInfoRow(label = "Duration", value = durText)
        PopupMenuDivider()
        DetailInfoRow(label = "Quality", value = "iMusic · 128k AAC")
        PopupMenuDivider()
        DetailInfoRow(label = "Track ID", value = track.id)
    }
}

@Composable
private fun DetailInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontFamily = SFProDisplay,
            fontSize = 13.5.sp,
            color = Color.White.copy(alpha = 0.56f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            fontFamily = SFProDisplay,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.94f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MorePopupSleepTimerSection(
    onBack: () -> Unit,
    onSelectDone: () -> Unit
) {
    val context = LocalContext.current
    val durations = listOf(10, 15, 30, 45, 60)
    val isActive = MusicPlayerState.sleepTimerRemainingMs > 0L || MusicPlayerState.sleepTimerEndOfTrack

    Column(modifier = Modifier.fillMaxWidth()) {
        PopupSubPageHeader(title = "Sleep Timer", onBack = onBack)
        PopupMenuDivider()

        durations.forEach { mins ->
            val selected = !MusicPlayerState.sleepTimerEndOfTrack &&
                MusicPlayerState.sleepTimerRemainingMs in ((mins - 1) * 60_000L + 1L)..(mins * 60_000L)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clickable {
                        MusicPlayerState.setSleepTimer(mins)
                        Toast.makeText(context, "Sleep timer set for $mins min", Toast.LENGTH_SHORT).show()
                        onSelectDone()
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (mins == 60) "1 hour" else "$mins minutes",
                    fontFamily = SFProDisplay,
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.92f)
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color(0xFFFA2D48),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            PopupMenuDivider()
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clickable {
                    MusicPlayerState.setSleepTimerEndOfTrack()
                    Toast.makeText(context, "Playback will stop after current track", Toast.LENGTH_SHORT).show()
                    onSelectDone()
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "End of current track",
                fontFamily = SFProDisplay,
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.92f)
            )
            if (MusicPlayerState.sleepTimerEndOfTrack) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color(0xFFFA2D48),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (isActive) {
            PopupMenuDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clickable {
                        MusicPlayerState.cancelSleepTimer()
                        Toast.makeText(context, "Sleep timer turned off", Toast.LENGTH_SHORT).show()
                        onSelectDone()
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Turn Off Timer",
                    fontFamily = SFProDisplay,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = Color(0xFFFA2D48)
                )
            }
        }
    }
}

/** Single-line header text that gently fades edges into the blurred popup card and smoothly scrolls when overflowing. */
@Composable
internal fun PopupMarqueeFadeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val maxPx = constraints.maxWidth
        val textLayout = remember(text, style, maxPx) {
            textMeasurer.measure(
                text = text,
                style = style,
                maxLines = 1,
                softWrap = false
            )
        }
        val singleWidthPx = textLayout.size.width.toFloat()
        val overflows = maxPx > 0 && singleWidthPx > maxPx

        if (!overflows) {
            Text(
                text = text,
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        } else {
            val gapPx = with(density) { 32.dp.toPx() }
            val cyclePx = singleWidthPx + gapPx
            val speedPxPerSec = with(density) { 28.dp.toPx() }.coerceAtLeast(1f)
            val scrollOffset = remember(text, singleWidthPx, maxPx) { Animatable(0f) }

            LaunchedEffect(text, singleWidthPx, maxPx) {
                scrollOffset.snapTo(0f)
                while (true) {
                    delay(1200L)
                    val durationMs = ((cyclePx / speedPxPerSec) * 1000f).toInt().coerceAtLeast(1200)
                    scrollOffset.animateTo(
                        targetValue = cyclePx,
                        animationSpec = tween(durationMillis = durationMs, easing = LinearEasing)
                    )
                    scrollOffset.snapTo(0f)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val w = size.width
                        if (w > 1f) {
                            val curOffset = scrollOffset.value
                            // Keep opening characters sharp at rest; fade left edge softly only during active travel
                            val leftFadeProgress = when {
                                curOffset <= 2f -> 0f
                                curOffset >= cyclePx - 12.dp.toPx() ->
                                    ((cyclePx - curOffset) / 12.dp.toPx()).coerceIn(0f, 1f)
                                else -> (curOffset / 10.dp.toPx()).coerceIn(0f, 1f)
                            }
                            val leftFadeFrac = ((12.dp.toPx() * leftFadeProgress) / w).coerceIn(0f, 0.22f)
                            val rightFadeFrac = (20.dp.toPx() / w).coerceIn(0.02f, 0.28f)
                            val rightStart = (1f - rightFadeFrac).coerceAtLeast(leftFadeFrac + 0.05f)
                            val stops = buildList {
                                if (leftFadeFrac > 0.004f) {
                                    add(0f to Color.Transparent)
                                    add(leftFadeFrac to Color.Black)
                                } else {
                                    add(0f to Color.Black)
                                }
                                add(rightStart to Color.Black)
                                add(1f to Color.Transparent)
                            }.toTypedArray()
                            drawRect(
                                brush = Brush.horizontalGradient(colorStops = stops),
                                blendMode = BlendMode.DstIn
                            )
                        }
                    }
            ) {
                val tx = -scrollOffset.value
                Layout(
                    content = {
                        Text(
                            text = text,
                            style = style,
                            color = color,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Visible,
                            modifier = Modifier.graphicsLayer { translationX = tx }
                        )
                        Text(
                            text = text,
                            style = style,
                            color = color,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Visible,
                            modifier = Modifier.graphicsLayer { translationX = tx + cyclePx }
                        )
                    }
                ) { measurables, constraints ->
                    val unbounded = constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)
                    val first = measurables[0].measure(unbounded)
                    val second = measurables[1].measure(unbounded)
                    val layoutW = constraints.maxWidth.coerceAtLeast(1)
                    val layoutH = maxOf(first.height, second.height)
                    layout(layoutW, layoutH) {
                        first.placeRelative(0, 0)
                        second.placeRelative(0, 0)
                    }
                }
            }
        }
    }
}
