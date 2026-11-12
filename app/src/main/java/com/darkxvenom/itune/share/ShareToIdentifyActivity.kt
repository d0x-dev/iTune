package com.darkxvenom.itune.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.darkxvenom.itune.MainActivity
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import com.darkxvenom.itune.ui.theme.ITuneTheme
import kotlinx.coroutines.launch

class ShareToIdentifyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ITuneTheme {
                ShareIdentifyScreen(
                    sharedIntent = intent,
                    onClose = { finish() }
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ShareIdentifyScreen(
    sharedIntent: Intent,
    onClose: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var isPlayingTrack by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var track by remember { mutableStateOf<IdentifiedTrack?>(null) }

    LaunchedEffect(sharedIntent) {
        loading = true
        error = null
        track = null
        try {
            val input = ShareIntentParser.parse(context, sharedIntent)
                ?: throw IllegalArgumentException("Share an audio file, video, or a supported media link to identify its music.")
            track = SharedMusicIdentifier(context.applicationContext).identify(input)
        } catch (e: Exception) {
            error = e.message ?: "Couldn't identify the music."
        } finally {
            loading = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AppleOledBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppleOledBlack)
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Identify Music",
                color = Color.White,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(28.dp))

            if (loading) {
                CircularProgressIndicator(color = Color(0xFF1A73E8), modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(18.dp))
                Text("Listening to the shared audio…", color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Extracting a short audio fingerprint",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            } else {
                val result = track
                if (result != null) {
                    AsyncImage(
                        model = result.artworkUrl,
                        contentDescription = "Album artwork",
                        modifier = Modifier
                            .size(190.dp)
                            .background(Color(0xFF171717), RoundedCornerShape(18.dp))
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        result.title,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(result.artist, color = Color.LightGray, fontSize = 16.sp, textAlign = TextAlign.Center)
                    if (!result.album.isNullOrBlank()) {
                        Spacer(Modifier.height(5.dp))
                        Text(result.album, color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(28.dp))
                    Text("Identified with Shazam", color = Color(0xFF8AB4F8), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            if (!isPlayingTrack) {
                                scope.launch {
                                    isPlayingTrack = true
                                    try {
                                        val query = "${result.title} ${result.artist}"
                                        val tracks = MusicBackend.searchMusic(query)
                                        val match = tracks.firstOrNull()
                                        if (match != null) {
                                            MusicPlayerState.play(match, tracks, expandPlayer = true)
                                            val mainIntent = Intent(context, MainActivity::class.java).apply {
                                                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                            }
                                            context.startActivity(mainIntent)
                                            onClose()
                                        } else {
                                            Toast.makeText(context, "Song not found in iTune", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Playback failed", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isPlayingTrack = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFA2D48)),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        if (isPlayingTrack) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_itune_waves),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Color.White
                            )
                        }
                        Spacer(Modifier.size(8.dp))
                        Text("Play in iTune", fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                } else {
                    Text(
                        error ?: "No music found",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            Spacer(Modifier.height(36.dp))
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text(if (loading) "Cancel" else "Done")
            }
        }
    }
}
