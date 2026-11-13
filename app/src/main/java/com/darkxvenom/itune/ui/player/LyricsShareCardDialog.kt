package com.darkxvenom.itune.ui.player

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.darkxvenom.itune.R
import com.darkxvenom.itune.ui.theme.AppFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

internal data class ShareLyricsLine(val timeMs: Long, val text: String)

private val ShareTeal = Color(0xFF63E6C2)
private enum class ShareCardStyle(val label: String, val tint: Color, val text: Color, val secondary: Color, val overlay: Float, val blur: Int) {
    FROSTED_DARK("Frosted Dark", Color.Black, Color.White, Color.White.copy(alpha = .72f), .48f, 24),
    FROSTED_LIGHT("Frosted Light", Color.White, Color(0xFF17191B), Color(0xFF17191B).copy(alpha = .68f), .38f, 24),
    CLEAR_GLASS("Clear Glass", Color.White, Color.White, Color.White.copy(alpha = .76f), .18f, 17),
    DEEP_BLUR("Deep Blur", Color(0xFF0A0A14), Color.White, Color.White.copy(alpha = .65f), .64f, 34),
    VIVID_GLOW("Vivid Glow", Color(0xFF0E2928), Color.White, Color(0xFFD1FFF4), .38f, 26),
}
private enum class ShareCardLayout(val label: String) {
    GLASS("Glass Card"), MINIMAL("Minimal"), COVER_FOCUSED("Cover Focus"), CENTERED("Centered"), BLUR_WASH("Blur Wash"), STREAMING("Streaming")
}
private enum class ShareCardBackground(val label: String) { ALBUM_ART("Album Art"), DARK("Dark"), LIGHT("Light"), GRADIENT("Gradient") }

@Composable
internal fun LyricsShareCardDialog(
    lines: List<ShareLyricsLine>,
    songTitle: String,
    artists: String,
    artworkUrl: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val captureLayer = rememberGraphicsLayer()
    var selectedStyle by remember { mutableStateOf(ShareCardStyle.FROSTED_DARK) }
    var selectedLayout by remember { mutableStateOf(ShareCardLayout.GLASS) }
    var selectedBackground by remember { mutableStateOf(ShareCardBackground.ALBUM_ART) }
    var textScale by remember { mutableFloatStateOf(1f) }
    var padding by remember { mutableFloatStateOf(24f) }
    var alignment by remember { mutableStateOf(TextAlign.Center) }
    var showTitle by remember { mutableStateOf(true) }
    var showArtist by remember { mutableStateOf(true) }
    var showArtwork by remember { mutableStateOf(true) }
    var showBranding by remember { mutableStateOf(true) }
    var appFont by remember { mutableStateOf(AppFont.SF_PRO) }
    var isSharing by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(horizontal = 16.dp),
            shape = RoundedCornerShape(28.dp), color = Color(0xFF101112), tonalElevation = 0.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 22.dp, end = 14.dp, top = 16.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Share Lyrics", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text("Customize your iTune card", color = Color.White.copy(alpha = .6f), fontSize = 13.sp)
                    }
                    Icon(Icons.Rounded.Close, "Close", tint = Color.White.copy(alpha = .75f), modifier = Modifier
                        .clip(CircleShape).clickable(onClick = onDismiss).padding(9.dp).size(20.dp))
                }

                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedContent(
                        targetState = selectedStyle to selectedLayout,
                        transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "lyrics_share_preview_style",
                    ) { (style, layout) ->
                        Box(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp).aspectRatio(1f).clip(RoundedCornerShape(22.dp))
                                .drawWithContent { captureLayer.record { this@drawWithContent.drawContent() }; drawLayer(captureLayer) },
                        ) {
                            LyricsShareCardPreview(
                                lines, songTitle, artists, artworkUrl, style, layout, selectedBackground, textScale, alignment, padding,
                                showTitle, showArtist, showArtwork, showBranding, appFont.family(),
                            )
                        }
                    }

                    ShareCardSection("LAYOUT")
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShareCardLayout.values().forEach { layout ->
                            ChoiceChip(layout.label, selectedLayout == layout, Modifier, onClick = { selectedLayout = layout })
                        }
                    }
                    ShareCardSection("FROSTED STYLE")
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShareCardStyle.values().forEach { style ->
                            ChoiceChip(style.label, selectedStyle == style, Modifier, swatch = style.tint, onClick = { selectedStyle = style })
                        }
                    }

                    ShareCardSection("BACKGROUND")
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShareCardBackground.values().forEach { background ->
                            ChoiceChip(background.label, selectedBackground == background, Modifier,
                                onClick = { selectedBackground = background })
                        }
                    }

                    ShareCardSection("CUSTOMIZE")
                    ShareCardSetting("Lyrics size", "${(textScale * 100).toInt()}%") {
                        Slider(textScale, { textScale = it }, valueRange = .6f..1.5f, steps = 17,
                            colors = SliderDefaults.colors(thumbColor = ShareTeal, activeTrackColor = ShareTeal))
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(TextAlign.Start to "Left", TextAlign.Center to "Center", TextAlign.End to "Right").forEach { (value, label) ->
                            ChoiceChip(label, alignment == value, Modifier.weight(1f), onClick = { alignment = value })
                        }
                    }
                    ShareCardSetting("Card spacing", "${padding.toInt()} dp") {
                        Slider(padding, { padding = it }, valueRange = 12f..36f, steps = 11,
                            colors = SliderDefaults.colors(thumbColor = ShareTeal, activeTrackColor = ShareTeal))
                    }
                    ShareCardSwitch("Show song title", showTitle) { showTitle = it }
                    ShareCardSwitch("Show artist", showArtist) { showArtist = it }
                    ShareCardSwitch("Show album cover", showArtwork) { showArtwork = it }
                    ShareCardSwitch("Show iTune logo", showBranding) { showBranding = it }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(AppFont.SF_PRO, AppFont.LINOTTE, AppFont.POPPINS).forEach { font ->
                            ChoiceChip(font.label, appFont == font, Modifier, onClick = { appFont = font })
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Copy text", color = Color.White, textAlign = TextAlign.Center, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .1f)).clickable {
                            val text = lines.joinToString("\n") { it.text } + "\n\n$songTitle · $artists"
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Lyrics", text)); onDismiss()
                        }.padding(vertical = 15.dp),
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                isSharing = true
                                runCatching {
                                    val bitmap = captureLayer.toImageBitmap().asAndroidBitmap()
                                    val uri = withContext(Dispatchers.IO) {
                                        val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
                                        val file = File(dir, "lyrics_${System.currentTimeMillis()}.png")
                                        FileOutputStream(file).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    }
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_TEXT, "$songTitle · $artists")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Lyrics")); onDismiss()
                                }.onFailure { android.widget.Toast.makeText(context, "Couldn't create lyrics card", android.widget.Toast.LENGTH_SHORT).show() }
                                isSharing = false
                            }
                        }, enabled = !isSharing, modifier = Modifier.weight(1.2f), shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ShareTeal, contentColor = Color.Black),
                    ) {
                        Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text(if (isSharing) "Preparing…" else "Share image", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsShareCardPreview(
    lines: List<ShareLyricsLine>, songTitle: String, artists: String, artworkUrl: String?, style: ShareCardStyle,
    layout: ShareCardLayout, cardBackground: ShareCardBackground, textScale: Float, alignment: TextAlign, padding: Float,
    showTitle: Boolean, showArtist: Boolean, showArtwork: Boolean, showBranding: Boolean, fontFamily: FontFamily,
) {
    val centered = layout == ShareCardLayout.CENTERED
    val coverFocus = layout == ShareCardLayout.COVER_FOCUSED && showArtwork && !artworkUrl.isNullOrBlank()
    val minimal = layout == ShareCardLayout.MINIMAL
    val baseColor = when (cardBackground) {
        ShareCardBackground.DARK -> Color(0xFF101114)
        ShareCardBackground.LIGHT -> Color(0xFFF3F3F1)
        ShareCardBackground.ALBUM_ART, ShareCardBackground.GRADIENT -> style.tint
    }
    Box(Modifier.fillMaxSize().background(
        if (cardBackground == ShareCardBackground.GRADIENT) Brush.verticalGradient(listOf(baseColor, ShareTeal.copy(alpha = .5f), Color(0xFF101114))) else Brush.verticalGradient(listOf(baseColor, baseColor))
    )) {
        if (cardBackground == ShareCardBackground.ALBUM_ART && !artworkUrl.isNullOrBlank() && (layout == ShareCardLayout.GLASS || layout == ShareCardLayout.BLUR_WASH || layout == ShareCardLayout.STREAMING)) {
            AsyncImage(artworkUrl, null, Modifier.fillMaxSize().blur(style.blur.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        }
        val overlayColor = if (cardBackground == ShareCardBackground.LIGHT) Color.White else style.tint
        Box(Modifier.fillMaxSize().background(overlayColor.copy(alpha = if (cardBackground == ShareCardBackground.DARK || cardBackground == ShareCardBackground.LIGHT) 0f else style.overlay)))
        if (coverFocus) {
            AsyncImage(artworkUrl, null, Modifier.fillMaxWidth().fillMaxHeight(.56f).align(Alignment.TopCenter)
                .clip(RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            Box(Modifier.fillMaxWidth().fillMaxHeight(.56f).align(Alignment.TopCenter).background(Color.Black.copy(alpha = .24f)))
        }
        Column(
            Modifier.fillMaxSize().padding(padding.dp),
            horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            if (showArtwork && !artworkUrl.isNullOrBlank() && !coverFocus && layout != ShareCardLayout.BLUR_WASH && cardBackground != ShareCardBackground.ALBUM_ART) {
                AsyncImage(artworkUrl, null, Modifier.size(if (coverFocus) 84.dp else 52.dp).clip(RoundedCornerShape(13.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                Spacer(Modifier.height(12.dp))
            }
            if (showTitle || showArtist) {
                if (showTitle) Text(songTitle, color = style.text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = if (centered) TextAlign.Center else TextAlign.Start)
                if (showArtist) Text(artists, color = style.secondary, fontSize = 12.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = if (centered) TextAlign.Center else TextAlign.Start)
                Spacer(Modifier.height(16.dp))
            }

            if (layout == ShareCardLayout.GLASS || layout == ShareCardLayout.STREAMING) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(style.tint.copy(alpha = if (style == ShareCardStyle.CLEAR_GLASS) .20f else .42f))
                    .border(1.dp, style.text.copy(alpha = .14f), RoundedCornerShape(20.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    LyricsText(lines, style, textScale, alignment, fontFamily)
                }
            } else {
                LyricsText(lines, style, textScale, if (centered) TextAlign.Center else alignment, fontFamily)
            }
            Spacer(Modifier.weight(1f))
            if (showBranding) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = if (centered) Modifier.align(Alignment.CenterHorizontally) else Modifier) {
                    Image(painterResource(R.drawable.ic_itune_waves), null, Modifier.size(22.dp))
                    Text("iTune", color = ShareTeal, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
private fun LyricsText(lines: List<ShareLyricsLine>, style: ShareCardStyle, textScale: Float, alignment: TextAlign, fontFamily: FontFamily) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        lines.forEach { line ->
            Text(line.text, color = style.text, fontFamily = fontFamily, fontSize = (22f * textScale).sp,
                lineHeight = (27f * textScale).sp, fontWeight = FontWeight.SemiBold, textAlign = alignment,
                modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ShareCardSection(title: String) {
    Text(title, color = Color.White.copy(alpha = .52f), fontSize = 11.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp, modifier = Modifier.fillMaxWidth().padding(start = 22.dp, top = 20.dp, bottom = 9.dp))
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, modifier: Modifier = Modifier, swatch: Color? = null, onClick: () -> Unit) {
    Row(
        modifier.clip(CircleShape).border(1.dp, if (selected) ShareTeal else Color.White.copy(alpha = .16f), CircleShape)
            .background(if (selected) ShareTeal.copy(alpha = .18f) else Color.White.copy(alpha = .045f))
            .clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (swatch != null) Box(Modifier.size(12.dp).clip(CircleShape).background(swatch).border(1.dp, Color.White.copy(alpha = .3f), CircleShape))
        Text(label, color = if (selected) ShareTeal else Color.White.copy(alpha = .76f), fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}

@Composable
private fun ShareCardSetting(title: String, value: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(value, color = ShareTeal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        content()
    }
}

@Composable
private fun ShareCardSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Switch(checked, onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ShareTeal))
    }
}
