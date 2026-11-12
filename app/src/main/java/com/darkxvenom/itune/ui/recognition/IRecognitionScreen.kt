package com.darkxvenom.itune.ui.recognition

import android.Manifest
import android.widget.Toast
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.recognition.IRecognitionRepository
import com.darkxvenom.itune.recognition.IRecognitionResult
import com.darkxvenom.itune.recognition.RecognitionNoMatchException
import com.darkxvenom.itune.recognition.RecognitionPermissionException
import com.darkxvenom.itune.recognition.ShazamSignature
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private sealed interface IRecognitionUiState {
    data object Ready : IRecognitionUiState
    data object Listening : IRecognitionUiState
    data object Processing : IRecognitionUiState
    data object PermissionDenied : IRecognitionUiState
    data class Success(val result: IRecognitionResult) : IRecognitionUiState
    data object NoMatch : IRecognitionUiState
    data class Error(val message: String) : IRecognitionUiState
}

private val RecognitionBlack = Color(0xFF050505)
private val RecognitionRed = Color(0xFFFA2D48)
private val RecognitionSecondary = Color(0xFF98989F)
private val RecognitionSurface = Color(0xFF19191D)

@Composable
fun IRecognitionScreen(
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uiState by remember { mutableStateOf<IRecognitionUiState>(IRecognitionUiState.Ready) }
    var recognitionJob by remember { mutableStateOf<Job?>(null) }
    val preferences = remember(context) {
        context.getSharedPreferences("ireconization_permissions", android.content.Context.MODE_PRIVATE)
    }

    fun startRecognition() {
        recognitionJob?.cancel()
        recognitionJob = scope.launch {
            try {
                uiState = IRecognitionUiState.Listening
                val signature: ShazamSignature = IRecognitionRepository.captureAndFingerprint(context)
                uiState = IRecognitionUiState.Processing
                val result = IRecognitionRepository.recognize(signature)
                uiState = IRecognitionUiState.Success(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RecognitionNoMatchException) {
                uiState = IRecognitionUiState.NoMatch
            } catch (_: RecognitionPermissionException) {
                uiState = IRecognitionUiState.PermissionDenied
            } catch (error: Throwable) {
                uiState = IRecognitionUiState.Error(
                    error.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Please try again."
                )
            }
        }
    }

    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        preferences.edit().putBoolean("requested_before", true).apply()
        if (granted) startRecognition() else uiState = IRecognitionUiState.PermissionDenied
    }

    fun requestMicrophoneAndStart() {
        val permissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (permissionGranted) {
            startRecognition()
        } else {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun openApplicationSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )
        runCatching { context.startActivity(intent) }
    }

    BackHandler(onBack = onClose)

    DisposableEffect(Unit) {
        onDispose { recognitionJob?.cancel() }
    }

    val isListening = uiState is IRecognitionUiState.Listening
    val isProcessing = uiState is IRecognitionUiState.Processing
    val isBusy = isListening || isProcessing
    val infinite = rememberInfiniteTransition(label = "iReconizationPulse")
    val ringPulse by infinite.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_550),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ringPulse",
    )
    val buttonScale by animateFloatAsState(
        targetValue = if (isListening) 0.96f else 1f,
        animationSpec = tween(260),
        label = "recognitionButtonScale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RecognitionBlack)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Close iReconization",
                        tint = Color.White,
                    )
                }
                Text(
                    text = "iReconization",
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(48.dp))
            }

            Spacer(Modifier.height(26.dp))

            Text(
                text = when (uiState) {
                    IRecognitionUiState.Listening -> "Listening for music"
                    IRecognitionUiState.Processing -> "Identifying"
                    is IRecognitionUiState.Success -> "Song identified"
                    IRecognitionUiState.NoMatch -> "No match found"
                    IRecognitionUiState.PermissionDenied -> "Microphone access"
                    is IRecognitionUiState.Error -> "Recognition failed"
                    IRecognitionUiState.Ready -> "What’s that song?"
                },
                color = Color.White,
                fontSize = 29.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = when (uiState) {
                    IRecognitionUiState.Listening -> "Hold your phone near the music."
                    IRecognitionUiState.Processing -> "Comparing your audio fingerprint with Shazam."
                    is IRecognitionUiState.Success -> "Here’s what we found."
                    IRecognitionUiState.NoMatch -> "Try again when the music is clearer."
                    IRecognitionUiState.PermissionDenied -> "Allow microphone access to identify music around you."
                    is IRecognitionUiState.Error -> "We couldn’t complete this recognition."
                    IRecognitionUiState.Ready -> "Tap the button and let iTune listen."
                },
                color = RecognitionSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 32.dp),
            )

            Spacer(Modifier.height(24.dp))

            AnimatedContent(
                targetState = uiState,
                transitionSpec = {
                    (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.97f))
                        .togetherWith(fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 1.02f))
                },
                label = "iReconizationContent",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { target ->
                when (target) {
                    is IRecognitionUiState.Success -> RecognitionResultCard(
                        result = target.result,
                        onTryAgain = ::requestMicrophoneAndStart,
                        onClose = onClose,
                    )
                    IRecognitionUiState.PermissionDenied -> PermissionCard(
                        onRequest = {
                            val activity = context as? Activity
                            val requestedBefore = preferences.getBoolean("requested_before", false)
                            val shouldOpenSettings = requestedBefore &&
                                activity != null &&
                                !activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                                    PackageManager.PERMISSION_GRANTED
                            if (shouldOpenSettings) openApplicationSettings()
                            else microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                    )
                    IRecognitionUiState.NoMatch -> MessageCard(
                        icon = Icons.Rounded.Search,
                        title = "We couldn’t find that song",
                        body = "Move closer to the speaker, reduce background noise, and try again.",
                        actionLabel = "Try Again",
                        onAction = ::requestMicrophoneAndStart,
                    )
                    is IRecognitionUiState.Error -> MessageCard(
                        icon = Icons.Rounded.ErrorOutline,
                        title = "Recognition didn’t finish",
                        body = target.message,
                        actionLabel = "Try Again",
                        onAction = ::requestMicrophoneAndStart,
                    )
                    else -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box(
                                modifier = Modifier.size(292.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Canvas(Modifier.fillMaxSize()) {
                                    val center = androidx.compose.ui.geometry.Offset(
                                        size.width / 2f,
                                        size.height / 2f,
                                    )
                                    val baseRadius = size.minDimension * 0.33f
                                    val pulse = if (isListening) ringPulse else 1f
                                    drawCircle(
                                        color = RecognitionRed.copy(alpha = if (isListening) 0.18f else 0.05f),
                                        radius = baseRadius * 1.55f * pulse,
                                        center = center,
                                        style = Stroke(width = 1.2.dp.toPx()),
                                    )
                                    drawCircle(
                                        color = RecognitionRed.copy(alpha = if (isListening) 0.26f else 0.08f),
                                        radius = baseRadius * 1.27f * pulse,
                                        center = center,
                                        style = Stroke(width = 1.dp.toPx()),
                                    )
                                    drawCircle(
                                        color = RecognitionRed.copy(alpha = if (isListening) 0.12f else 0.04f),
                                        radius = baseRadius * 1.02f,
                                        center = center,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(178.dp)
                                        .scale(buttonScale)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    Color(0xFFFF5269),
                                                    RecognitionRed,
                                                    Color(0xFFB90F2B),
                                                ),
                                            ),
                                        )
                                        .border(1.dp, Color.White.copy(alpha = 0.24f), CircleShape)
                                        .clickable(enabled = !isBusy) { requestMicrophoneAndStart() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isProcessing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(50.dp),
                                            color = Color.White,
                                            strokeWidth = 3.dp,
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Mic,
                                            contentDescription = if (isListening) "Listening" else "Start recognition",
                                            tint = Color.White,
                                            modifier = Modifier.size(58.dp),
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(25.dp))
                            Text(
                                text = when (uiState) {
                                    IRecognitionUiState.Listening -> "Listening…"
                                    IRecognitionUiState.Processing -> "Finding your song…"
                                    else -> "Tap to recognize"
                                },
                                color = if (isListening || isProcessing) Color.White else RecognitionSecondary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            if (isBusy) {
                                Spacer(Modifier.height(16.dp))
                                TextButtonLike(
                                    label = "Cancel",
                                    onClick = {
                                        recognitionJob?.cancel()
                                        recognitionJob = null
                                        uiState = IRecognitionUiState.Ready
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (uiState !is IRecognitionUiState.Success &&
                uiState !is IRecognitionUiState.PermissionDenied &&
                uiState !is IRecognitionUiState.NoMatch &&
                uiState !is IRecognitionUiState.Error
            ) {
                Text(
                    text = "Identify music playing around you",
                    modifier = Modifier.padding(bottom = 25.dp),
                    color = Color(0xFF66666E),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun RecognitionResultCard(
    result: IRecognitionResult,
    onTryAgain: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isPlayLoading by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AsyncImage(
            model = result.artworkUrl,
            contentDescription = result.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(228.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(RecognitionSurface),
        )
        Spacer(Modifier.height(22.dp))
        Text(
            text = result.title,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = result.artist,
            color = Color(0xFFB8B8BE),
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val details = listOfNotNull(result.album, result.releaseDate, result.genre)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("  •  ")
        if (details.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = details,
                color = RecognitionSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (!isPlayLoading) {
                    scope.launch {
                        isPlayLoading = true
                        try {
                            val query = "${result.title} ${result.artist}"
                            val tracks = MusicBackend.searchMusic(query)
                            val track = tracks.firstOrNull()
                            if (track != null) {
                                MusicPlayerState.play(track, tracks, expandPlayer = true)
                                onClose()
                            } else {
                                Toast.makeText(context, "Song not found in iTune", Toast.LENGTH_SHORT).show()
                            }
                        } catch (_: Exception) {
                            Toast.makeText(context, "Playback failed, try again", Toast.LENGTH_SHORT).show()
                        } finally {
                            isPlayLoading = false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = RecognitionRed),
            shape = RoundedCornerShape(16.dp),
        ) {
            if (isPlayLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_itune_waves),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.size(8.dp))
            Text("Play", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
        }
        if (!result.shazamUrl.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { openExternalUrl(context, result.shazamUrl) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("View on Shazam", color = Color.White)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = Color.White)
            }
        }
        Spacer(Modifier.height(14.dp))
        TextButtonLike(label = "Recognize Another Song", onClick = onTryAgain)
    }
}

@Composable
private fun PermissionCard(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 30.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(RecognitionRed.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Mic, contentDescription = null, tint = RecognitionRed, modifier = Modifier.size(46.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text("Allow microphone access", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "iReconization listens for a few seconds to create a music fingerprint. Audio is not saved.",
            color = RecognitionSecondary,
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onRequest,
            colors = ButtonDefaults.buttonColors(containerColor = RecognitionRed),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Rounded.Settings, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Allow Microphone")
        }
    }
}

@Composable
private fun MessageCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = RecognitionSecondary, modifier = Modifier.size(54.dp))
        Spacer(Modifier.height(18.dp))
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, color = RecognitionSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onAction,
            colors = ButtonDefaults.buttonColors(containerColor = RecognitionRed),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null)
            Spacer(Modifier.size(7.dp))
            Text(actionLabel)
        }
    }
}

@Composable
private fun TextButtonLike(label: String, onClick: () -> Unit) {
    Surface(
        color = Color.White.copy(alpha = 0.08f),
        shape = RoundedCornerShape(50),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 19.dp, vertical = 11.dp),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun openExternalUrl(context: android.content.Context, url: String?) {
    if (url.isNullOrBlank()) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        // No browser or compatible music app is installed.
    } catch (_: SecurityException) {
        // Ignore an invalid external intent handler.
    }
}
