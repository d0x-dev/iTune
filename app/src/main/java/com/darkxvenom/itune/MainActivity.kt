package com.darkxvenom.itune

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkxvenom.itune.backend.AudioDownloader
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.data.auth.GoogleAuthStore
import com.darkxvenom.itune.data.auth.YouTubeLibrarySyncManager
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.data.repository.AvatarManager
import com.darkxvenom.itune.radio.OnlineRadioController
import com.darkxvenom.itune.ishare.IShareSyncManager
import com.darkxvenom.itune.island.DynamicIslandForegroundTracker
import com.darkxvenom.itune.island.DynamicIslandStore
import com.darkxvenom.itune.ui.home.HomeScreen
import com.darkxvenom.itune.ui.splash.SplashScreen
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import com.darkxvenom.itune.ui.theme.ITuneTheme
import com.darkxvenom.itune.ui.welcome.WelcomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AvatarManager.init(this)
        SettingsStore.ensureInit(this)
        GoogleAuthStore.ensureInit(this)
        MusicPlayerState.init(this)
        YouTubeLibrarySyncManager.launchFullSync(this)
        OnlineRadioController.ensureInit(this)
        DynamicIslandStore.ensureInit(this)
        DynamicIslandStore.syncServiceState(this)
        AudioDownloader.ensureChannel(this)
        enableEdgeToEdge()
        setContent {
            val settingsState by SettingsStore.state.collectAsStateWithLifecycle()
            ITuneTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AppleOledBlack
                ) {
                    val splashEnabled = settingsState.splashScreenEnabled
                    var showSplash by rememberSaveable { mutableStateOf(splashEnabled) }
                    var revealHomeUnderlay by remember(settingsState.welcomeCompleted, splashEnabled) {
                        mutableStateOf(settingsState.welcomeCompleted)
                    }
                    var showWelcomeFlow by remember(settingsState.welcomeCompleted) {
                        mutableStateOf(!settingsState.welcomeCompleted)
                    }
                    var homeBrandVisible by remember(settingsState.welcomeCompleted, splashEnabled) {
                        mutableStateOf(settingsState.welcomeCompleted && !showSplash)
                    }
                    var homeIconBounds by remember { mutableStateOf<Rect?>(null) }
                    var homeTitleBounds by remember { mutableStateOf<Rect?>(null) }

                    Box(modifier = Modifier.fillMaxSize()) {
                        if (revealHomeUnderlay || settingsState.welcomeCompleted) {
                            HomeScreen(
                                showTopBarBrand = homeBrandVisible,
                                deferStartupPrompts = showWelcomeFlow || showSplash,
                                onTopBarBrandBoundsChanged = { iconR, titleR ->
                                    homeIconBounds = iconR
                                    homeTitleBounds = titleR
                                },
                            )
                        }
                        if (showWelcomeFlow && !showSplash) {
                            WelcomeScreen(
                                initialName = "",
                                fromSplash = splashEnabled,
                                homeIconBounds = homeIconBounds,
                                homeTitleBounds = homeTitleBounds,
                                onRevealHomeUnderlay = { enteredName ->
                                    SettingsStore.setDisplayName(this@MainActivity, enteredName)
                                    IShareSyncManager.setDisplayName(enteredName)
                                    revealHomeUnderlay = true
                                },
                                onContinue = { enteredName ->
                                    homeBrandVisible = true
                                    showWelcomeFlow = false
                                    SettingsStore.completeWelcome(this@MainActivity, enteredName)
                                    IShareSyncManager.setDisplayName(enteredName)
                                },
                            )
                        }
                        if (showSplash) {
                            SplashScreen(
                                isNewUser = !settingsState.welcomeCompleted,
                                homeIconBounds = homeIconBounds,
                                homeTitleBounds = homeTitleBounds,
                                onRevealUnderlay = {
                                    revealHomeUnderlay = true
                                },
                                onSplashFinished = {
                                    if (settingsState.welcomeCompleted) {
                                        homeBrandVisible = true
                                        showSplash = false
                                    } else {
                                        showSplash = false
                                        showWelcomeFlow = true
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        DynamicIslandForegroundTracker.setForeground(true)
    }

    override fun onResume() {
        super.onResume()
        DynamicIslandForegroundTracker.setForeground(true)
        DynamicIslandStore.syncServiceState(this)
    }

    override fun onStop() {
        super.onStop()
        DynamicIslandForegroundTracker.setForeground(false)
        MusicPlayerState.savePlaybackSession(sync = true)
    }

    override fun onDestroy() {
        DynamicIslandForegroundTracker.setForeground(false)
        MusicPlayerState.savePlaybackSession(sync = true)
        super.onDestroy()
        OnlineRadioController.release()
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
