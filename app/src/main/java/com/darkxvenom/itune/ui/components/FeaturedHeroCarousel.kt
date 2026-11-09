package com.darkxvenom.itune.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkxvenom.itune.data.model.FeaturedCard
import com.darkxvenom.itune.ui.theme.AppleBorder
import com.darkxvenom.itune.ui.theme.AppleTextPrimary
import com.darkxvenom.itune.ui.theme.AppleTextSecondary

@Composable
fun FeaturedHeroCarousel(
    cards: List<FeaturedCard>,
    modifier: Modifier = Modifier,
    onCardClick: (FeaturedCard) -> Unit = {}
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(cards, key = { it.id }) { card ->
            FeaturedHeroCardItem(card = card, onClick = { onCardClick(card) })
        }
    }
}

@Composable
fun FeaturedHeroCardItem(
    card: FeaturedCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(320.dp)
            .clickable { onClick() }
    ) {
        // Pre-header: Category
        Text(
            text = card.category.uppercase(),
            color = AppleTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Main Title
        Text(
            text = card.title,
            color = AppleTextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 26.sp
        )

        Spacer(modifier = Modifier.height(1.dp))

        // Subtitle / Curator
        Text(
            text = card.subtitle,
            color = AppleTextSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Large Banner Card Box
        Box(
            modifier = Modifier
                .width(320.dp)
                .height(210.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(18.dp))
        ) {
            // Artwork layer
            AlbumArtwork(
                type = card.heroType,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 18.dp
            )

            // Overlaid embossed title (matching "Today's Hits" gold typography)
            Column(
                modifier = Modifier
                    .padding(start = 18.dp, top = 16.dp)
                    .align(Alignment.TopStart)
            ) {
                val displayTitle = if (card.title == "Today's Hits") "Today's\nHits" else card.title
                Text(
                    text = displayTitle,
                    color = card.accentColor,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp,
                    lineHeight = 32.sp
                )
            }

            // Bottom description banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xE6000000))
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = card.caption,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 17.sp,
                    maxLines = 2
                )
            }
        }
    }
}
