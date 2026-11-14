package com.darkxvenom.itune.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkxvenom.itune.widget.ITuneWidgetUpdater
import com.darkxvenom.itune.widget.WidgetPreferences
import com.darkxvenom.itune.ui.artist.components.FrostedTopBar
import com.darkxvenom.itune.ui.artist.components.FrostedTopBarTitlePill
import com.darkxvenom.itune.ui.artist.components.TopFadeBlur
import com.darkxvenom.itune.ui.artist.components.topBarContentPadding
import com.darkxvenom.itune.ui.artist.haptics.Haptic
import com.darkxvenom.itune.ui.artist.haptics.rememberHaptics
import com.darkxvenom.itune.ui.components.rememberSpringOverscrollState
import com.darkxvenom.itune.ui.components.springOverscroll
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@Composable
fun WidgetSettingsScreen(
    hasMiniPlayer: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var size by remember { mutableStateOf(WidgetPreferences.size(context)) }
    var style by remember { mutableStateOf(WidgetPreferences.style(context)) }
    var artwork by remember { mutableStateOf(WidgetPreferences.showArtwork(context)) }
    var controls by remember { mutableStateOf(WidgetPreferences.showControls(context)) }
    var favorite by remember { mutableStateOf(WidgetPreferences.showFavorite(context)) }
    var blurredArtwork by remember { mutableStateOf(WidgetPreferences.blurArtworkBackground(context)) }
    val scrollState = rememberScrollState()
    val overscrollState = rememberSpringOverscrollState()
    val hazeState = remember { HazeState() }
    val haptics = rememberHaptics()
    fun refresh() = ITuneWidgetUpdater.refreshAll(context)
    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize().background(AppleOledBlack).hazeSource(hazeState)) {
      Column(
        modifier = Modifier.fillMaxSize().navigationBarsPadding()
            .springOverscroll(overscrollState).verticalScroll(scrollState)
            .padding(start = 20.dp, end = 20.dp, top = topBarContentPadding() + 8.dp, bottom = 28.dp),
      ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Customize your Home Screen player", color = Color.White, fontSize = 27.sp,
                lineHeight = 32.sp, fontWeight = FontWeight.Bold)
        }

        Text("PREVIEW", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        WidgetPreview(size = size, style = style, artwork = artwork, controls = controls, favorite = favorite, blurredArtwork = blurredArtwork)
        Text("Choose how iTune appears on your Home Screen. Resize the widget from your launcher.",
            color = Color(0xFF9A9A9E), fontSize = 13.sp, lineHeight = 18.sp,
            modifier = Modifier.padding(top = 10.dp, bottom = 24.dp))

        Text("LAYOUT", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        WidgetChoiceRow(
            options = listOf("Compact", "Standard", "Expanded"),
            selected = when (size) {
                WidgetPreferences.SIZE_COMPACT -> "Compact"
                WidgetPreferences.SIZE_EXPANDED -> "Expanded"
                else -> "Standard"
            },
        ) { chosen ->
            size = when (chosen) {
                "Compact" -> WidgetPreferences.SIZE_COMPACT
                "Expanded" -> WidgetPreferences.SIZE_EXPANDED
                else -> WidgetPreferences.SIZE_STANDARD
            }
            WidgetPreferences.setSize(context, size); refresh()
        }

        Text("STYLE", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 20.dp))
        WidgetChoiceRow(
            options = listOf("Artwork", "Dark", "Light"),
            selected = when (style) {
                WidgetPreferences.STYLE_DARK -> "Dark"
                WidgetPreferences.STYLE_LIGHT -> "Light"
                else -> "Artwork"
            },
        ) { chosen ->
            style = when (chosen) {
                "Dark" -> WidgetPreferences.STYLE_DARK
                "Light" -> WidgetPreferences.STYLE_LIGHT
                else -> WidgetPreferences.STYLE_ARTWORK
            }
            WidgetPreferences.setStyle(context, style); refresh()
        }

        Text("OPTIONS", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 22.dp, bottom = 8.dp))
        Column(Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF1C1C1E))) {
            WidgetOptionRow("Album artwork", "Show the current song cover", artwork) {
                artwork = it; WidgetPreferences.setArtwork(context, it); refresh()
            }
            WidgetOptionRow("Playback controls", "Play, pause, previous and next", controls) {
                controls = it; WidgetPreferences.setControls(context, it); refresh()
            }
            WidgetOptionRow("Favorite button", "Like the current song from the widget", favorite) {
                favorite = it; WidgetPreferences.setFavorite(context, it); refresh()
            }
        WidgetOptionRow("Blurred artwork background", "Use a softened cover image behind the player", blurredArtwork) {
            blurredArtwork = it; WidgetPreferences.setBlurArtworkBackground(context, it); refresh()
        }
        }
        if (hasMiniPlayer) Spacer(Modifier.height(112.dp))
      }
      TopFadeBlur(hazeState = hazeState, pageColor = AppleOledBlack, modifier = Modifier.align(Alignment.TopCenter))
      FrostedTopBar(
          transparentBackdrop = true,
          artworkPageChrome = true,
          backButtonHazeState = hazeState,
          onBack = { haptics.play(Haptic.Tap); onBack() },
          modifier = Modifier.align(Alignment.TopCenter),
          centerContent = {
              FrostedTopBarTitlePill(hazeState = hazeState) {
                  Text("Widget", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                      maxLines = 1, textAlign = TextAlign.Center)
              }
          },
      )
    }
}

@Composable
private fun WidgetChoiceRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1C1C1E)).padding(4.dp)) {
        options.forEach { option ->
            val active = option == selected
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                    .background(if (active) Color(0xFF48484A) else Color.Transparent)
                    .clickable { onSelect(option) }.padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(option, color = if (active) Color.White else Color(0xFFB8B8BC), fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
            }
        }
    }
}

@Composable
private fun WidgetOptionRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = Color(0xFF98989D), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun WidgetPreview(size: String, style: String, artwork: Boolean, controls: Boolean, favorite: Boolean, blurredArtwork: Boolean) {
    val bg = when (style) {
        WidgetPreferences.STYLE_DARK -> Color(0xFF171719)
        WidgetPreferences.STYLE_LIGHT -> Color(0xFFE8E8EA)
        else -> Color(0xFF333044)
    }
    val fg = if (style == WidgetPreferences.STYLE_LIGHT) Color(0xFF171719) else Color.White
    val compact = size == WidgetPreferences.SIZE_COMPACT
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(24.dp))
            .background(if (blurredArtwork && artwork) Color(0xFF4A3945) else if (style == WidgetPreferences.STYLE_ARTWORK) Color(0xFF40333E) else bg)
            .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(24.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(if (compact) 72.dp else 92.dp).clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF49464E)), contentAlignment = Alignment.Center,
        ) {
            if (artwork) Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("iTune", color = fg.copy(alpha = .72f), fontSize = 11.sp, modifier = Modifier.weight(1f))
                if (favorite) Icon(Icons.Rounded.StarBorder, "Favorite", tint = fg, modifier = Modifier.size(19.dp))
            }
            Text("Now Playing", color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("Artist name", color = fg.copy(alpha = .72f), fontSize = 12.sp, maxLines = 1)
            if (!compact) Text("━━━ ━━━━━", color = fg.copy(alpha = .45f), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
            if (controls) Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (!compact) Icon(Icons.Rounded.SkipPrevious, "Previous", tint = fg, modifier = Modifier.size(19.dp))
                Icon(Icons.Rounded.Pause, "Play or pause", tint = fg, modifier = Modifier.size(19.dp))
                Icon(Icons.Rounded.SkipNext, "Next", tint = fg, modifier = Modifier.size(19.dp))
            }
        }
    }
}
