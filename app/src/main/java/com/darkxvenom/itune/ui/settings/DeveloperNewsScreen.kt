package com.darkxvenom.itune.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.darkxvenom.itune.R
import com.darkxvenom.itune.data.news.DeveloperNewsItem
import com.darkxvenom.itune.data.news.DeveloperNewsManager
import com.darkxvenom.itune.ui.artist.components.FrostedTopBar
import com.darkxvenom.itune.ui.artist.icons.SfIcons
import com.darkxvenom.itune.ui.player.SFProDisplay
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AppleCrimson = Color(0xFFFC3C44)
private val AppleCrimsonSoft = Color(0xFFFF375F)
private val CardBackground = Color(0xFF1C1C1E)
private val FrostedBorderColor = Color.White.copy(alpha = 0.08f)

@Composable
fun DeveloperNewsScreen(
    hasMiniPlayer: Boolean = false,
    onBack: () -> Unit
) {
    val newsList by DeveloperNewsManager.newsList.collectAsState()
    val isSyncing by DeveloperNewsManager.isSyncing.collectAsState()

    var isInitialLoading by remember { mutableStateOf(newsList.isEmpty()) }

    LaunchedEffect(Unit) {
        if (newsList.isEmpty()) {
            isInitialLoading = true
            DeveloperNewsManager.refresh()
            delay(3500)
            isInitialLoading = false
        }
    }

    LaunchedEffect(isSyncing) {
        if (!isSyncing && isInitialLoading) {
            delay(200)
            isInitialLoading = false
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "spin_refresh")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    var selectedNewsItem by remember { mutableStateOf<DeveloperNewsItem?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val filteredNews = remember(newsList, searchQuery) {
        if (searchQuery.isBlank()) {
            newsList
        } else {
            val q = searchQuery.trim().lowercase()
            newsList.filter { item ->
                item.title.lowercase().contains(q) ||
                item.message.lowercase().contains(q) ||
                (item.author?.lowercase()?.contains(q) == true) ||
                (item.tag?.lowercase()?.contains(q) == true)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleOledBlack)
    ) {
        AnimatedContent(
            targetState = selectedNewsItem,
            transitionSpec = {
                if (targetState != null) {
                    (slideInHorizontally(animationSpec = tween(350)) { width -> width } + fadeIn(animationSpec = tween(350)))
                        .togetherWith(slideOutHorizontally(animationSpec = tween(350)) { width -> -width } + fadeOut(animationSpec = tween(200)))
                } else {
                    (slideInHorizontally(animationSpec = tween(350)) { width -> -width } + fadeIn(animationSpec = tween(350)))
                        .togetherWith(slideOutHorizontally(animationSpec = tween(350)) { width -> width } + fadeOut(animationSpec = tween(200)))
                }
            },
            label = "news_transition"
        ) { activeItem ->
            if (activeItem != null) {
                DeveloperNewsDetailContent(
                    item = activeItem,
                    hasMiniPlayer = hasMiniPlayer,
                    onBack = { selectedNewsItem = null }
                )
            } else {
                DeveloperNewsListContent(
                    newsList = filteredNews,
                    totalCount = newsList.size,
                    isInitialLoading = isInitialLoading,
                    isSyncing = isSyncing,
                    rotation = rotation,
                    isSearchActive = isSearchActive,
                    searchQuery = searchQuery,
                    hasMiniPlayer = hasMiniPlayer,
                    onSearchToggle = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) searchQuery = ""
                    },
                    onSearchQueryChange = { searchQuery = it },
                    onRefresh = { DeveloperNewsManager.refresh() },
                    onBack = onBack,
                    onSelectNews = { selectedNewsItem = it }
                )
            }
        }
    }
}

@Composable
private fun DeveloperNewsListContent(
    newsList: List<DeveloperNewsItem>,
    totalCount: Int,
    isInitialLoading: Boolean,
    isSyncing: Boolean,
    rotation: Float,
    isSearchActive: Boolean,
    searchQuery: String,
    hasMiniPlayer: Boolean,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onSelectNews: (DeveloperNewsItem) -> Unit
) {
    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize()) {
        FrostedTopBar(
            onBack = onBack,
            centerContent = {
                Text(
                    text = "News from dev",
                    style = TextStyle(
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    ),
                    color = Color.White
                )
            },
            actions = {
                IconButton(onClick = onSearchToggle) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = if (isSearchActive) AppleCrimson else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    enabled = !isSyncing
                ) {
                    Icon(
                        painter = painterResource(R.drawable.newspaper),
                        contentDescription = "Refresh",
                        tint = AppleCrimsonSoft,
                        modifier = Modifier
                            .size(20.dp)
                            .then(if (isSyncing) Modifier.rotate(rotation) else Modifier)
                    )
                }
            }
        )

        // Animated Search Input Bar
        AnimatedVisibility(visible = isSearchActive) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, FrostedBorderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = SFProDisplay,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush = SolidColor(AppleCrimson),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search announcements…",
                                    color = Color.White.copy(alpha = 0.45f),
                                    fontSize = 15.sp,
                                    fontFamily = SFProDisplay
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = SfIcons.Close,
                                contentDescription = "Clear",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            if (isInitialLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = AppleCrimson,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = "Loading announcements…",
                            style = TextStyle(
                                fontFamily = SFProDisplay,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            ),
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                }
            } else if (newsList.isEmpty()) {
                DeveloperNewsEmptyState(isSyncing = isSyncing, hasQuery = searchQuery.isNotBlank())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = if (hasMiniPlayer) 160.dp else 100.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        AnnouncementsHeroCard(articleCount = totalCount)
                    }

                    items(newsList, key = { it.id }) { item ->
                        DeveloperNewsListItemCard(
                            item = item,
                            onClick = { onSelectNews(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnouncementsHeroCard(
    articleCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        ),
        border = BorderStroke(1.dp, FrostedBorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(AppleCrimson.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.newspaper),
                    contentDescription = null,
                    tint = AppleCrimsonSoft,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Developer Announcements",
                    style = TextStyle(
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        letterSpacing = (-0.2).sp
                    ),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (articleCount == 1) "1 active release & update" else "$articleCount active releases & updates",
                    style = TextStyle(
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    ),
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun DeveloperNewsListItemCard(
    item: DeveloperNewsItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val listImageRequest = remember(item.imageUrl) {
        item.imageUrl?.let {
            ImageRequest.Builder(context)
                .data(it)
                .crossfade(true)
                .build()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        ),
        border = BorderStroke(1.dp, FrostedBorderColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (!item.imageUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    AsyncImage(
                        model = listImageRequest,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                val authorName = item.author?.takeIf { it.isNotBlank() }
                    ?: item.tag?.takeIf { it.isNotBlank() }
                    ?: "Developer"
                val dateStr = formatNewsDate(item.timestamp)
                val pillText = "$authorName • $dateStr"

                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = pillText,
                        style = TextStyle(
                            fontFamily = SFProDisplay,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            letterSpacing = 0.2.sp
                        ),
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = item.title,
                    style = TextStyle(
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = (-0.3).sp
                    ),
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                val teaser = extractTeaserText(item.message)
                Text(
                    text = teaser,
                    style = TextStyle(
                        fontFamily = SFProDisplay,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    ),
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = AppleCrimson.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, AppleCrimson.copy(alpha = 0.35f)),
                        modifier = Modifier.clickable(onClick = onClick)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "Read More",
                                style = TextStyle(
                                    fontFamily = SFProDisplay,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = AppleCrimsonSoft
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = SfIcons.ChevronRight,
                                contentDescription = null,
                                tint = AppleCrimsonSoft,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeveloperNewsDetailContent(
    item: DeveloperNewsItem,
    hasMiniPlayer: Boolean,
    onBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val detailImageRequest = remember(item.imageUrl) {
        item.imageUrl?.let {
            ImageRequest.Builder(context)
                .data(it)
                .crossfade(true)
                .build()
        }
    }

    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize()) {
        FrostedTopBar(
            onBack = onBack,
            centerContent = {
                Text(
                    text = item.tag?.takeIf { it.isNotBlank() } ?: "Announcement",
                    style = TextStyle(
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            actions = {
                if (!item.actionUrl.isNullOrBlank()) {
                    IconButton(onClick = {
                        try { uriHandler.openUri(item.actionUrl) } catch (_: Exception) {}
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.open_in_new),
                            contentDescription = "Open Link",
                            tint = AppleCrimsonSoft,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 18.dp,
                    end = 18.dp,
                    top = 12.dp,
                    bottom = if (hasMiniPlayer) 160.dp else 100.dp
                )
        ) {
            // Headline
            Text(
                text = item.title,
                style = TextStyle(
                    fontFamily = SFProDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    letterSpacing = (-0.5).sp
                ),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Author & Date Pill
            val authorName = item.author?.takeIf { it.isNotBlank() }
                ?: item.tag?.takeIf { it.isNotBlank() }
                ?: "Developer"
            val dateStr = formatNewsDate(item.timestamp)
            val pillText = "$authorName • $dateStr"

            Surface(
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.08f)
            ) {
                Text(
                    text = pillText,
                    style = TextStyle(
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        letterSpacing = 0.2.sp
                    ),
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Featured Image
            if (!item.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = detailImageRequest,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 300.dp)
                        .clip(RoundedCornerShape(20.dp))
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // GitHub Markdown Rendered Content
            GitHubMarkdownContent(markdown = item.message)

            // Primary Action Button
            if (!item.actionUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(28.dp))
                Button(
                    onClick = {
                        try {
                            uriHandler.openUri(item.actionUrl)
                        } catch (_: Exception) {}
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppleCrimson,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.open_in_new),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.actionText?.takeIf { it.isNotBlank() } ?: "Open Link",
                        style = TextStyle(
                            fontFamily = SFProDisplay,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun GitHubMarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val lines = remember(markdown) { markdown.lines() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        var inCodeBlock = false
        var codeContent = ""
        val currentList = mutableListOf<String>()
        var inList = false

        for (line in lines) {
            val trimmed = line.trim()

            // Code block delimiter
            if (trimmed.startsWith("\u0060\u0060\u0060")) {
                if (inList) {
                    RenderMarkdownList(currentList.toList())
                    currentList.clear()
                    inList = false
                }
                if (inCodeBlock) {
                    RenderCodeBlock(codeContent.trimEnd())
                    codeContent = ""
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                }
                continue
            }

            if (inCodeBlock) {
                codeContent += line + "\n"
                continue
            }

            // Headings with divider line under H1 & H2
            if (trimmed.matches(Regex("^#{1,6}\\s+.*"))) {
                if (inList) {
                    RenderMarkdownList(currentList.toList())
                    currentList.clear()
                    inList = false
                }
                val level = trimmed.takeWhile { it == '#' }.length
                val title = trimmed.substring(level).trim()
                RenderGitHubHeading(title = title, level = level)
                continue
            }

            // Horizontal rule
            if (trimmed.matches(Regex("^[-*_]{3,}$"))) {
                if (inList) {
                    RenderMarkdownList(currentList.toList())
                    currentList.clear()
                    inList = false
                }
                HorizontalDivider(
                    thickness = 0.8.dp,
                    color = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                continue
            }

            // Blockquote
            if (trimmed.startsWith("> ")) {
                if (inList) {
                    RenderMarkdownList(currentList.toList())
                    currentList.clear()
                    inList = false
                }
                RenderBlockQuote(trimmed.substring(2).trim())
                continue
            }

            // Unordered list
            if (trimmed.matches(Regex("^[-*•]\\s+.*"))) {
                val itemText = trimmed.replace(Regex("^[-*•]\\s+"), "")
                inList = true
                currentList.add(itemText)
                continue
            }

            // Ordered list
            if (trimmed.matches(Regex("^\\d+\\.\\s+.*"))) {
                val itemText = trimmed.replace(Regex("^\\d+\\.\\s+"), "")
                inList = true
                currentList.add(itemText)
                continue
            }

            // Flush list if paragraph
            if (inList) {
                RenderMarkdownList(currentList.toList())
                currentList.clear()
                inList = false
            }

            if (trimmed.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                continue
            }

            // Rich Markdown Paragraph
            MarkdownText(
                markdown = trimmed,
                style = TextStyle(
                    fontFamily = SFProDisplay,
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                ),
                linkColor = AppleCrimsonSoft,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (inList && currentList.isNotEmpty()) {
            RenderMarkdownList(currentList.toList())
        }
        if (inCodeBlock && codeContent.isNotEmpty()) {
            RenderCodeBlock(codeContent.trimEnd())
        }
    }
}

@Composable
private fun RenderGitHubHeading(title: String, level: Int) {
    val fontSize = when (level) {
        1 -> 22.sp
        2 -> 19.sp
        3 -> 17.sp
        else -> 15.sp
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp)
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = fontSize,
                letterSpacing = (-0.3).sp
            ),
            color = Color.White
        )
        if (level <= 2) {
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 0.8.dp,
                color = Color.White.copy(alpha = 0.18f)
            )
        }
    }
}

@Composable
private fun RenderCodeBlock(code: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, FrostedBorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            Text(
                text = code,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = Color.White.copy(alpha = 0.95f)
                )
            )
        }
    }
}

@Composable
private fun RenderBlockQuote(quote: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.5.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AppleCrimson)
        )
        Spacer(modifier = Modifier.width(12.dp))
        MarkdownText(
            markdown = quote,
            style = TextStyle(
                fontFamily = SFProDisplay,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.5.sp
            ),
            linkColor = AppleCrimsonSoft
        )
    }
}

@Composable
private fun RenderMarkdownList(items: List<String>) {
    Column(
        modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(AppleCrimson)
                )
                Spacer(modifier = Modifier.width(12.dp))
                MarkdownText(
                    markdown = item,
                    style = TextStyle(
                        fontFamily = SFProDisplay,
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp
                    ),
                    linkColor = AppleCrimsonSoft,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun extractTeaserText(rawMarkdown: String): String {
    return rawMarkdown
        .lines()
        .filter { !it.trim().startsWith("#") && !it.trim().startsWith("\u0060\u0060\u0060") && !it.trim().startsWith("---") }
        .joinToString(" ")
        .replace(Regex("""\*\*([^*]+)\*\*"""), "$1")
        .replace(Regex("""\*([^*]+)\*"""), "$1")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("""\[([^\]]+)\]\([^)]*\)"""), "$1")
        .trim()
        .ifEmpty { rawMarkdown }
}

fun formatNewsDate(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
        sdf.format(Date(timestamp))
    } catch (_: Exception) {
        ""
    }
}

@Composable
private fun DeveloperNewsEmptyState(isSyncing: Boolean, hasQuery: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(90.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                AppleCrimson.copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        )
                    )
            )

            if (isSyncing) {
                CircularProgressIndicator(
                    color = AppleCrimson,
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.newspaper),
                    contentDescription = null,
                    tint = AppleCrimsonSoft,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = if (isSyncing) "Checking for News…" else if (hasQuery) "No Announcements Match" else "No News Yet",
            style = TextStyle(
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            ),
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isSyncing)
                "Connecting to Firebase to fetch developer announcements."
            else if (hasQuery)
                "Try searching with different keywords."
            else
                "Stay tuned! Announcements and updates from the developers will appear here.",
            style = TextStyle(
                fontFamily = SFProDisplay,
                fontSize = 13.5.sp,
                lineHeight = 19.sp
            ),
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
