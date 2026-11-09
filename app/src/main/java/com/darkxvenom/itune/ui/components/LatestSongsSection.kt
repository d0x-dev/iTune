package com.darkxvenom.itune.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkxvenom.itune.data.model.Track
import com.darkxvenom.itune.ui.theme.AppleDivider
import com.darkxvenom.itune.ui.theme.AppleExplicitBadge
import com.darkxvenom.itune.ui.theme.AppleTextPrimary
import com.darkxvenom.itune.ui.theme.AppleTextSecondary

@Composable
fun LatestSongsSection(
    songColumns: List<List<Track>>,
    modifier: Modifier = Modifier,
    onSeeAllClick: () -> Unit = {},
    onTrackClick: (Track) -> Unit = {},
    onTrackMenuClick: (Track) -> Unit = {}
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Section Header with Chevron >
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSeeAllClick() }
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Latest Songs",
                color = AppleTextPrimary,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "See all latest songs",
                tint = AppleTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Horizontal Paging of 4-track Columns
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(songColumns) { columnTracks ->
                SongColumn(
                    tracks = columnTracks,
                    onTrackClick = onTrackClick,
                    onTrackMenuClick = onTrackMenuClick
                )
            }
        }
    }
}

@Composable
fun SongColumn(
    tracks: List<Track>,
    modifier: Modifier = Modifier,
    onTrackClick: (Track) -> Unit = {},
    onTrackMenuClick: (Track) -> Unit = {}
) {
    Column(
        modifier = modifier.width(310.dp)
    ) {
        tracks.forEachIndexed { index, track ->
            SongRowItem(
                track = track,
                onClick = { onTrackClick(track) },
                onMenuClick = { onTrackMenuClick(track) }
            )
            if (index < tracks.size - 1) {
                // Indented divider matching Apple Music
                HorizontalDivider(
                    modifier = Modifier.padding(start = 60.dp),
                    thickness = 0.5.dp,
                    color = AppleDivider
                )
            }
        }
    }
}

@Composable
fun SongRowItem(
    track: Track,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album thumbnail
        AlbumArtwork(
            type = track.coverType,
            size = 48.dp,
            cornerRadius = 8.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Song Name + Artist + Explicit badge
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = track.title,
                    color = AppleTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (track.isExplicit) {
                    Spacer(modifier = Modifier.width(6.dp))
                    ExplicitBadge()
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = track.artist,
                color = AppleTextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // More options button (...)
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = "Options for ${track.title}",
                tint = AppleTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun ExplicitBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF333336))
            .border(0.5.dp, AppleExplicitBadge.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "E",
            color = AppleExplicitBadge,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 10.sp
        )
    }
}
