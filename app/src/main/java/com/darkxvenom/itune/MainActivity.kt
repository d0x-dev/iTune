package com.darkxvenom.itune

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkxvenom.itune.backend.AudioDownloader
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.data.repository.AvatarManager
import com.darkxvenom.itune.radio.OnlineRadioController
import com.darkxvenom.itune.ishare.IShareSyncManager
import com.darkxvenom.itune.island.DynamicIslandForegroundTracker
import com.darkxvenom.itune.island.DynamicIslandStore
import com.darkxvenom.itune.ui.home.HomeScreen
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import com.darkxvenom.itune.ui.theme.ITuneTheme
import com.darkxvenom.itune.ui.welcome.WelcomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AvatarManager.init(this)
        SettingsStore.ensureInit(this)
        MusicPlayerState.init(this)
        AudioDownloader.ensureChannel(this)
        enableEdgeToEdge()
        setContent {
            ITuneTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AppleOledBlack
                ) {
                    HomeScreen()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        MusicPlayerState.savePlaybackSession(sync = true)
    }

    override fun onDestroy() {
        MusicPlayerState.savePlaybackSession(sync = true)
        super.onDestroy()
        MusicPlayerState.release()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun HomeScreenPreview() {
    ITuneTheme {
        HomeScreen()
    }
}
