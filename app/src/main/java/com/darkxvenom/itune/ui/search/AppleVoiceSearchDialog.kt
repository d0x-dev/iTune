package com.darkxvenom.itune.ui.search

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat

/**
 * Apple iOS Action Sheet Pop-up presenting two choices when the search bar mic is tapped:
 * 1. Voice Search (speech-to-text song query)
 * 2. iRecognition (ambient music identification)
 */
@Composable
fun AppleVoiceOptionsDialog(
    onVoiceSearch: () -> Unit,
    onIRecognition: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Action options card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF1E1E20))
                        .border(
                            width = 0.5.dp,
                            color = Color.White.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(18.dp),
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Search & Identify",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.45f),
                            letterSpacing = 0.2.sp,
                        )
                    }

                    // Option 1: Voice Search
                    AppleVoiceOptionRow(
                        title = "Voice Search",
                        subtitle = "Search by speaking title, artist, or lyrics",
                        icon = Icons.Default.Mic,
                        iconTint = Color.White,
                        iconContainerBrush = Brush.linearGradient(
                            listOf(Color(0xFFFF375F), Color(0xFFFA2D48)),
                        ),
                        onClick = {
                            onDismiss()
                            onVoiceSearch()
                        },
                    )

                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.08f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 66.dp),
                    )

                    // Option 2: iRecognition
                    AppleVoiceOptionRow(
                        title = "iRecognition",
                        subtitle = "Identify music playing around you",
                        icon = Icons.Default.GraphicEq,
                        iconTint = Color.White,
                        iconContainerBrush = Brush.linearGradient(
                            listOf(Color(0xFF007AFF), Color(0xFF5856D6)),
                        ),
                        onClick = {
                            onDismiss()
                            onIRecognition()
                        },
                    )
                }

                // Cancel button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF1E1E20))
                        .border(
                            width = 0.5.dp,
                            color = Color.White.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(18.dp),
                        )
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFA2D48),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppleVoiceOptionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    iconContainerBrush: Brush,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .background(if (pressed) Color.White.copy(alpha = 0.06f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(iconContainerBrush),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.5.sp,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.30f),
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Apple-style full-screen voice listening dialog with pulsing microphone animation,
 * real-time speech-to-text recognition, and instant query execution.
 */
@Composable
fun AppleVoiceSearchDialog(
    onQueryRecognized: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    var recognizedText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var rmsLevel by remember { mutableFloatStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Fallback system speech recognition activity launcher
    val systemSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val query = matches?.firstOrNull()?.trim()
            if (!query.isNullOrBlank()) {
                onQueryRecognized(query)
                onDismiss()
            }
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (!granted) {
            errorMessage = "Microphone access is required for voice search."
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // SpeechRecognizer integration
    DisposableEffect(hasPermission) {
        var speechRecognizer: SpeechRecognizer? = null
        if (hasPermission) {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                try {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {
                                isListening = true
                                errorMessage = null
                            }

                            override fun onBeginningOfSpeech() {
                                isListening = true
                            }

                            override fun onRmsChanged(rmsdB: Float) {
                                rmsLevel = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                            }

                            override fun onBufferReceived(buffer: ByteArray?) {}

                            override fun onEndOfSpeech() {
                                isListening = false
                            }

                            override fun onError(error: Int) {
                                isListening = false
                                val msg = when (error) {
                                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Tap to try again."
                                    SpeechRecognizer.ERROR_NETWORK -> "Network error. Check connection."
                                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out. Tap to retry."
                                    else -> "Didn't catch that. Tap microphone to retry."
                                }
                                errorMessage = msg
                            }

                            override fun onResults(results: Bundle?) {
                                isListening = false
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val query = matches?.firstOrNull()?.trim()
                                if (!query.isNullOrBlank()) {
                                    recognizedText = query
                                    onQueryRecognized(query)
                                    onDismiss()
                                } else {
                                    errorMessage = "No speech detected. Tap to retry."
                                }
                            }

                            override fun onPartialResults(partialResults: Bundle?) {
                                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                matches?.firstOrNull()?.let {
                                    if (it.isNotBlank()) recognizedText = it
                                }
                            }

                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })

                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        }
                        startListening(intent)
                    }
                } catch (_: Exception) {
                    errorMessage = "Tap microphone to search"
                }
            } else {
                // Launch system speech intent fallback
                try {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a song title or artist")
                    }
                    systemSpeechLauncher.launch(intent)
                } catch (_: Exception) {
                    errorMessage = "Speech recognition not available on this device"
                }
            }
        }

        onDispose {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
        }
    }

    // Pulse wave animations
    val infiniteTransition = rememberInfiniteTransition(label = "voicePulse")
    val waveScale1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave1",
    )
    val waveAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "waveAlpha1",
    )

    val waveScale2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave2",
    )
    val waveAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "waveAlpha2",
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF0050505)),
        ) {
            // Close button at top right
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, end = 20.dp),
                contentAlignment = Alignment.TopEnd,
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f)),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel Voice Search",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Center content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Pulsing Apple Microphone
                Box(
                    modifier = Modifier.size(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isListening) {
                        // Pulse wave 2
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .scale(waveScale2)
                                .clip(CircleShape)
                                .background(Color(0xFFFA2D48).copy(alpha = waveAlpha2)),
                        )
                        // Pulse wave 1
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .scale(waveScale1)
                                .clip(CircleShape)
                                .background(Color(0xFFFF375F).copy(alpha = waveAlpha1)),
                        )
                    }

                    // Main glowing Apple microphone button
                    val micScale = 1f + (rmsLevel * 0.15f)
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .scale(micScale)
                            .shadow(
                                elevation = 24.dp,
                                shape = CircleShape,
                                ambientColor = Color(0xFFFA2D48),
                                spotColor = Color(0xFFFF375F),
                            )
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0xFFFF375F), Color(0xFFFA2D48)),
                                ),
                            )
                            .border(1.5.dp, Color.White.copy(alpha = 0.30f), CircleShape)
                            .clickable {
                                // Tap to restart recognition
                                try {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a song title or artist")
                                    }
                                    systemSpeechLauncher.launch(intent)
                                } catch (_: Exception) {}
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Microphone",
                            tint = Color.White,
                            modifier = Modifier.size(46.dp),
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Recognized text or Listening status
                Text(
                    text = when {
                        recognizedText.isNotBlank() -> "\"$recognizedText\""
                        errorMessage != null -> errorMessage!!
                        isListening -> "Listening..."
                        else -> "Tap microphone to speak"
                    },
                    fontSize = if (recognizedText.isNotBlank()) 24.sp else 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 30.sp,
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "Say a song, artist, album, or lyrics",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.50f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
