package com.darkxvenom.itune.ui.player

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.AudioTranscoder
import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.backend.MusicCacheManager
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.backend.Net
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleTextPrimary
import com.darkxvenom.itune.ui.theme.AppleTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

internal enum class PlayerToolMode { RINGTONE, SNIPPET }

internal enum class RingtoneTarget(val label: String, val systemType: Int, val directory: String) {
    PHONE("Phone", RingtoneManager.TYPE_RINGTONE, Environment.DIRECTORY_RINGTONES),
    NOTIFICATION("Notification", RingtoneManager.TYPE_NOTIFICATION, Environment.DIRECTORY_NOTIFICATIONS),
    ALARM("Alarm", RingtoneManager.TYPE_ALARM, Environment.DIRECTORY_ALARMS)
}

internal enum class SnippetAction(val label: String) {
    SAVE("Save to Library"),
    SHARE("Share Audio Clip")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerToolsDialog(
    track: MusicTrack,
    mode: PlayerToolMode,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var source by remember(track.id) { mutableStateOf<File?>(null) }
    var loading by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }
    var isPreviewPlaying by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var selectedRingtoneTarget by remember { mutableStateOf(RingtoneTarget.PHONE) }
    var selectedSnippetAction by remember { mutableStateOf(SnippetAction.SAVE) }

    var durationMs by remember {
        mutableFloatStateOf((track.durationSec * 1000L).coerceAtLeast(30_000L).toFloat())
    }
    val defaultMaxClip = if (mode == PlayerToolMode.RINGTONE) 30_000f else 60_000f
    var range by remember(track.id) {
        val maxDuration = (track.durationSec * 1000L).takeIf { it > 0 }?.toFloat() ?: 30_000f
        mutableStateOf(0f..(defaultMaxClip.coerceAtMost(maxDuration)))
    }
    var currentPlayPosMs by remember { mutableLongStateOf(0L) }

    val player = remember(context) {
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_OFF }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    player.seekTo(range.start.toLong())
                    player.play()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPreviewPlaying = isPlaying
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(track.id) {
        loading = true
        error = null
        try {
            val file = PlayerToolsAudio.prepareSource(context, track.id)
            source = file
            val actualDuration = PlayerToolsAudio.duration(file)
            if (actualDuration > 0) {
                durationMs = actualDuration.toFloat()
                val end = minOf(defaultMaxClip, actualDuration.toFloat())
                range = 0f..end
            }
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            player.prepare()
        } catch (e: Exception) {
            error = e.message ?: "Unable to load audio"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(isPreviewPlaying, range) {
        while (isPreviewPlaying && isActive) {
            val pos = player.currentPosition
            currentPlayPosMs = pos
            if (pos >= range.endInclusive.toLong()) {
                player.seekTo(range.start.toLong())
                currentPlayPosMs = range.start.toLong()
            }
            delay(50)
        }
    }

    Dialog(
        onDismissRequest = {
            player.stop()
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { win ->
                win.setDimAmount(0.20f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    win.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    player.stop()
                    onDismiss()
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .widthIn(max = 350.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* Catch clicks inside card */ }
                    .shadow(
                        elevation = 28.dp,
                        shape = RoundedCornerShape(22.dp),
                        ambientColor = Color.Black,
                        spotColor = Color.Black
                    )
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF1E1E22))
                    .border(
                        width = 0.75.dp,
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(22.dp)
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                // Top Icon Badge
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(0.75.dp, Color.White.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(
                            if (mode == PlayerToolMode.RINGTONE) R.drawable.ic_settings_notifications
                            else R.drawable.ic_itune_waves
                        ),
                        contentDescription = null,
                        tint = AppleMusicRed,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Modal Title
                Text(
                    text = if (mode == PlayerToolMode.RINGTONE) "Set Ringtone" else "Audio Snippet",
                    color = AppleTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Song Title & Artists
                Text(
                    text = track.title,
                    color = AppleTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Text(
                    text = track.artists,
                    color = AppleTextSecondary,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Segmented Pill Option Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .padding(2.5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (mode == PlayerToolMode.RINGTONE) {
                        RingtoneTarget.values().forEach { target ->
                            val isSelected = target == selectedRingtoneTarget
                            val bg by animateColorAsState(
                                targetValue = if (isSelected) Color.White.copy(alpha = 0.18f) else Color.Transparent,
                                label = "RingtoneSegmentBg"
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bg)
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        selectedRingtoneTarget = target
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = target.label,
                                    color = if (isSelected) AppleTextPrimary else AppleTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        }
                    } else {
                        SnippetAction.values().forEach { action ->
                            val isSelected = action == selectedSnippetAction
                            val bg by animateColorAsState(
                                targetValue = if (isSelected) Color.White.copy(alpha = 0.18f) else Color.Transparent,
                                label = "SnippetSegmentBg"
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bg)
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        selectedSnippetAction = action
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = action.label,
                                    color = if (isSelected) AppleTextPrimary else AppleTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Audio Preview & Range Selector Container
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF141416))
                        .border(0.75.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when {
                        loading -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = AppleMusicRed
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Preparing audio preview…",
                                    color = AppleTextSecondary,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                        error != null -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = error ?: "Audio unavailable",
                                    color = AppleMusicRed,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        else -> {
                            // Audio Preview Play Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val playInteractionSource = remember { MutableInteractionSource() }
                                val isPlayPressed by playInteractionSource.collectIsPressedAsState()
                                val playScale by animateFloatAsState(
                                    targetValue = if (isPlayPressed) 0.88f else 1.0f,
                                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 600f),
                                    label = "PlayBtnScale"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .scale(playScale)
                                        .shadow(4.dp, CircleShape, ambientColor = AppleMusicRed, spotColor = AppleMusicRed)
                                        .clip(CircleShape)
                                        .background(AppleMusicRed)
                                        .clickable(
                                            interactionSource = playInteractionSource,
                                            indication = null
                                        ) {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            if (isPreviewPlaying) {
                                                player.pause()
                                            } else {
                                                player.seekTo(range.start.toLong())
                                                player.play()
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPreviewPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPreviewPlaying) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isPreviewPlaying) "Playing Preview" else "Tap to Preview",
                                            color = AppleTextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        val clipSec = ((range.endInclusive - range.start) / 1000f).toInt()
                                        Text(
                                            text = "${clipSec}s clip",
                                            color = AppleMusicRed,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(3.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = PlayerToolsAudio.time(if (isPreviewPlaying) currentPlayPosMs else range.start.toLong()),
                                            color = AppleTextSecondary,
                                            fontSize = 11.5.sp
                                        )
                                        Text(
                                            text = PlayerToolsAudio.time(range.endInclusive.toLong()),
                                            color = AppleTextSecondary,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Dual-thumb Range Slider
                            RangeSlider(
                                value = range,
                                onValueChange = { next ->
                                    val maxLen = if (mode == PlayerToolMode.RINGTONE) 30_000f else 60_000f
                                    val start = next.start.coerceAtLeast(0f)
                                    val end = next.endInclusive.coerceAtMost(durationMs)
                                    val boundedEnd = end.coerceAtMost(start + maxLen)
                                    range = if (boundedEnd - start < 3_000f) {
                                        start..(start + 3_000f).coerceAtMost(durationMs)
                                    } else {
                                        start..boundedEnd
                                    }
                                    player.seekTo(range.start.toLong())
                                    currentPlayPosMs = range.start.toLong()
                                },
                                valueRange = 0f..durationMs.coerceAtLeast(3_000f),
                                steps = 0,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = AppleMusicRed,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(30.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Bottom Divided Action Buttons (Apple Modal Alert Style)
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = Color.White.copy(alpha = 0.12f)
                )

                // Primary Action Button (Set Ringtone / Save / Share)
                val primaryLabel = when (mode) {
                    PlayerToolMode.RINGTONE -> "Set as ${selectedRingtoneTarget.label}"
                    PlayerToolMode.SNIPPET -> {
                        if (selectedSnippetAction == SnippetAction.SAVE) "Save Snippet"
                        else "Share Snippet"
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable(enabled = source != null && !loading && !working && error == null) {
                            val input = source ?: return@clickable
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            working = true
                            player.pause()
                            scope.launch {
                                val output = withContext(Dispatchers.IO) {
                                    PlayerToolsAudio.trim(
                                        input,
                                        range.start.toLong(),
                                        range.endInclusive.toLong(),
                                        context.cacheDir
                                    )
                                }
                                val resultUri = if (output == null) null else withContext(Dispatchers.IO) {
                                    if (mode == PlayerToolMode.RINGTONE) {
                                        PlayerToolsAudio.saveRingtone(
                                            context,
                                            output,
                                            track.title,
                                            track.artists,
                                            selectedRingtoneTarget
                                        )
                                    } else {
                                        PlayerToolsAudio.saveSnippet(
                                            context,
                                            output,
                                            track.title,
                                            track.artists
                                        )
                                    }
                                }

                                working = false
                                if (resultUri == null) {
                                    Toast.makeText(context, "Could not create audio clip", Toast.LENGTH_LONG).show()
                                } else if (mode == PlayerToolMode.RINGTONE) {
                                    val applied = PlayerToolsAudio.applyAsSystemRingtone(
                                        context,
                                        resultUri,
                                        selectedRingtoneTarget
                                    )
                                    if (applied) {
                                        Toast.makeText(
                                            context,
                                            "Set as default ${selectedRingtoneTarget.label.lowercase()}!",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Added to ${selectedRingtoneTarget.label.lowercase()}s. Choose it in Sound settings.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
                                            runCatching {
                                                val manageIntent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                                    data = Uri.parse("package:" + context.packageName)
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(manageIntent)
                                            }
                                        }
                                        runCatching {
                                            context.startActivity(
                                                Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                                    putExtra(
                                                        RingtoneManager.EXTRA_RINGTONE_TYPE,
                                                        selectedRingtoneTarget.systemType
                                                    )
                                                    putExtra(
                                                        RingtoneManager.EXTRA_RINGTONE_TITLE,
                                                        "Select ${selectedRingtoneTarget.label}"
                                                    )
                                                    putExtra(
                                                        RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                                        resultUri
                                                    )
                                                    putExtra(
                                                        RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,
                                                        true
                                                    )
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                            )
                                        }
                                    }
                                    onDismiss()
                                } else {
                                    if (selectedSnippetAction == SnippetAction.SAVE) {
                                        Toast.makeText(
                                            context,
                                            "Snippet saved to Music/iTune/Snippets",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        val share = Intent(Intent.ACTION_SEND).apply {
                                            type = "audio/mp4"
                                            putExtra(Intent.EXTRA_STREAM, resultUri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        runCatching {
                                            context.startActivity(Intent.createChooser(share, "Share Snippet"))
                                        }
                                    }
                                    onDismiss()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (working) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = AppleMusicRed
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Exporting Audio…",
                                color = AppleMusicRed,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Text(
                            text = primaryLabel,
                            color = if (source != null && !loading && error == null) AppleMusicRed
                            else AppleMusicRed.copy(alpha = 0.4f),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.2).sp
                        )
                    }
                }

                // Divider 2
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = Color.White.copy(alpha = 0.12f)
                )

                // Cancel Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            player.stop()
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Cancel",
                        color = AppleTextSecondary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = (-0.2).sp
                    )
                }
            }
        }
    }
}

private object PlayerToolsAudio {
    suspend fun prepareSource(context: Context, videoId: String): File = withContext(Dispatchers.IO) {
        // 1. Check if cached audio file already exists via MusicCacheManager
        val cached = runCatching { MusicCacheManager.getCachedAudioFile(videoId) }.getOrNull()
        if (cached != null && cached.exists() && cached.length() > 0) {
            return@withContext cached
        }

        // 2. Check if previously prepared tool source exists in cacheDir
        val toolFile = File(context.cacheDir, "player_tool_source_${videoId.hashCode()}.m4a")
        if (toolFile.exists() && toolFile.length() > 0) {
            return@withContext toolFile
        }

        // 3. Otherwise resolve and download
        val url = MusicBackend.resolveAudioStream(videoId, targetQuality = "medium")
            ?: throw IllegalStateException("Could not resolve this song's audio stream")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", Net.USER_AGENT)
            .build()
        Net.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Audio download failed: HTTP ${response.code}")
            val body = response.body ?: throw IllegalStateException("Empty audio response")
            if (body.contentLength() > 150L * 1024L * 1024L) throw IllegalStateException("Audio file is too large")
            body.byteStream().use { input -> toolFile.outputStream().use { output -> input.copyTo(output) } }
            if (toolFile.length() == 0L) throw IllegalStateException("Downloaded audio is empty")
            toolFile
        }
    }

    fun duration(file: File): Long {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount).firstNotNullOfOrNull { index ->
                val format = extractor.getTrackFormat(index)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true &&
                    format.containsKey(MediaFormat.KEY_DURATION)
                ) {
                    format.getLong(MediaFormat.KEY_DURATION) / 1000L
                } else null
            } ?: 0L
        } finally {
            extractor.release()
        }
    }

    fun trim(source: File, startMs: Long, endMs: Long, cacheDir: File): File? {
        val out = File(cacheDir, "itune_clip_${System.currentTimeMillis()}.m4a")

        // 1. High-fidelity transcode trim (PCM slicing & AAC re-encoding)
        // This universally supports Opus, Vorbis, AAC, WebM, MP3 without MediaMuxer container errors.
        val transcodeSuccess = AudioTranscoder.trimAndTranscodeToAac(
            input = source,
            output = out,
            startMs = startMs,
            endMs = endMs,
            targetBitrateBps = 192_000
        )
        if (transcodeSuccess && out.exists() && out.length() > 0L) {
            return out
        }

        // 2. Fallback to direct bitstream muxing if transcode was skipped
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(source.absolutePath)
            val audioTrack = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(audioTrack)
            val format = extractor.getTrackFormat(audioTrack)
            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outTrack = muxer.addTrack(format)
            muxer.start()
            extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val info = MediaCodec.BufferInfo()
            val buffer = java.nio.ByteBuffer.allocate(1024 * 1024)
            val endUs = endMs * 1000L
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0 || extractor.sampleTime < 0 || extractor.sampleTime >= endUs) break
                if (extractor.sampleTime >= startMs * 1000L) {
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = extractor.sampleTime - startMs * 1000L
                    info.flags = extractor.sampleFlags
                    muxer.writeSampleData(outTrack, buffer, info)
                }
                extractor.advance()
            }
            muxer.stop()
            if (out.exists() && out.length() > 0L) out else null
        } catch (_: Exception) {
            out.delete()
            null
        } finally {
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    fun saveRingtone(
        context: Context,
        file: File,
        title: String,
        artist: String,
        target: RingtoneTarget
    ): Uri? {
        val relativeDir = "${target.directory}/iTune"
        return saveToMediaStore(
            context = context,
            file = file,
            title = title,
            artist = artist,
            path = relativeDir,
            target = target
        )
    }

    fun saveSnippet(
        context: Context,
        file: File,
        title: String,
        artist: String
    ): Uri? {
        return saveToMediaStore(
            context = context,
            file = file,
            title = title,
            artist = artist,
            path = "${Environment.DIRECTORY_MUSIC}/iTune/Snippets",
            target = null
        )
    }

    fun applyAsSystemRingtone(context: Context, uri: Uri, target: RingtoneTarget): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.System.canWrite(context)) {
                    RingtoneManager.setActualDefaultRingtoneUri(context, target.systemType, uri)
                    true
                } else {
                    false
                }
            } else {
                RingtoneManager.setActualDefaultRingtoneUri(context, target.systemType, uri)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun saveToMediaStore(
        context: Context,
        file: File,
        title: String,
        artist: String,
        path: String,
        target: RingtoneTarget?
    ): Uri? {
        return try {
            val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(100)
            val fileName = "$safeTitle (iTune).m4a"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.TITLE, safeTitle)
                    put(MediaStore.Audio.Media.ARTIST, artist)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, path)
                    when (target) {
                        RingtoneTarget.PHONE -> put(MediaStore.Audio.Media.IS_RINGTONE, 1)
                        RingtoneTarget.NOTIFICATION -> put(MediaStore.Audio.Media.IS_NOTIFICATION, 1)
                        RingtoneTarget.ALARM -> put(MediaStore.Audio.Media.IS_ALARM, 1)
                        null -> {
                            put(MediaStore.Audio.Media.IS_MUSIC, 1)
                        }
                    }
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    file.inputStream().use { it.copyTo(output) }
                } ?: return null
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                uri
            } else {
                val baseDir = when (target) {
                    RingtoneTarget.PHONE -> Environment.DIRECTORY_RINGTONES
                    RingtoneTarget.NOTIFICATION -> Environment.DIRECTORY_NOTIFICATIONS
                    RingtoneTarget.ALARM -> Environment.DIRECTORY_ALARMS
                    null -> Environment.DIRECTORY_MUSIC
                }
                val dir = File(Environment.getExternalStoragePublicDirectory(baseDir), "iTune").apply {
                    if (!exists()) mkdirs()
                }
                val dest = File(dir, fileName)
                file.copyTo(dest, overwrite = true)
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DATA, dest.absolutePath)
                    put(MediaStore.Audio.Media.TITLE, safeTitle)
                    put(MediaStore.Audio.Media.ARTIST, artist)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    when (target) {
                        RingtoneTarget.PHONE -> put(MediaStore.Audio.Media.IS_RINGTONE, 1)
                        RingtoneTarget.NOTIFICATION -> put(MediaStore.Audio.Media.IS_NOTIFICATION, 1)
                        RingtoneTarget.ALARM -> put(MediaStore.Audio.Media.IS_ALARM, 1)
                        null -> put(MediaStore.Audio.Media.IS_MUSIC, 1)
                    }
                }
                context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun time(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        return "%d:%02d".format(total / 60L, total % 60L)
    }
}
