/*
 * Copyright (C) 2026 DxV Studio ~~ Dark
 *
 * Originally created by DxV Studio ~~ Dark.
 * Credits: DxV Studio, AirBeats, and Dark.
 *
 * This file is part of iTune and is licensed under the GNU General Public License v3.0 (GPL-3.0).
 * Any copying, modification, or redistribution of this code must strictly comply with the
 * terms of the project LICENSE file and retain full author credit.
 */

package com.darkxvenom.itune

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.darkxvenom.itune.backend.AudioDownloader
import com.darkxvenom.itune.backend.AutoSleepTimerManager
import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.data.auth.GoogleAuthStore
import com.darkxvenom.itune.data.auth.YouTubeLibrarySyncManager
import com.darkxvenom.itune.data.canvas.SpotifyToken
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.data.lastfm.LastFmIntegration
import com.darkxvenom.itune.data.spotify.SpotifyLibrary
import com.darkxvenom.itune.data.spotify.SpotifyPlaylistAutoSyncManager
import com.darkxvenom.itune.data.news.DeveloperNewsManager
import com.darkxvenom.itune.data.updates.AppUpdateManager
import com.darkxvenom.itune.data.repository.AvatarManager
import com.darkxvenom.itune.radio.OnlineRadioController
import com.darkxvenom.itune.ishare.IShareSyncManager
import com.darkxvenom.itune.island.DynamicIslandForegroundTracker
import com.darkxvenom.itune.island.DynamicIslandStore
import com.darkxvenom.itune.recognition.MusicRecognitionController
import com.darkxvenom.itune.recognition.MusicRecognitionTileService
import com.darkxvenom.itune.ui.aod.AlwaysOnDisplayManager
import com.darkxvenom.itune.ui.aod.AppleAlwaysOnDisplayOverlay
import com.darkxvenom.itune.ui.home.HomeScreen
import com.darkxvenom.itune.ui.player.AppleVolumeManager
import com.darkxvenom.itune.ui.player.AppleVolumeOverlay
import com.darkxvenom.itune.ui.splash.SplashScreen
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import com.darkxvenom.itune.ui.theme.ITuneTheme
import com.darkxvenom.itune.ui.welcome.WelcomeScreen
import com.darkxvenom.itune.television.focus.TvDeviceUtils
import com.darkxvenom.itune.television.scaffold.TvMainScaffold
import com.darkxvenom.itune.television.ui.welcome.TvWelcomeScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AvatarManager.init(this)
        SettingsStore.ensureInit(this)
        SpotifyToken.init(this)
        SpotifyLibrary.auth = SpotifyLibrary.Auth {
            SpotifyToken.accessToken()?.let { SpotifyLibrary.Tokens(it, SpotifyToken.clientToken()) }
        }
        LastFmIntegration.initialize(this)
        handleLastFmCallback(intent)
        AutoSleepTimerManager.start(this)
        GoogleAuthStore.ensureInit(this)
        MusicPlayerState.init(this)
        AppleVolumeManager.init(this)
        AlwaysOnDisplayManager.init(this)
        AlwaysOnDisplayManager.bindActivity(this)
        YouTubeLibrarySyncManager.launchFullSync(this)
        OnlineRadioController.ensureInit(this)
        DeveloperNewsManager.init(this)
        AppUpdateManager.checkOnAppOpen()
        DynamicIslandStore.ensureInit(this)
        DynamicIslandStore.syncServiceState(this)
        AudioDownloader.ensureChannel(this)
        handleIncomingShareIntent(intent)
        handleRecognitionIntent(intent)
        enableEdgeToEdge()
        setContent {
            val settingsState by SettingsStore.state.collectAsStateWithLifecycle()
            LaunchedEffect(settingsState.disableScreenshot) {
                if (settingsState.disableScreenshot) {
                    window.setFlags(
                        android.view.WindowManager.LayoutParams.FLAG_SECURE,
                        android.view.WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            val isTv = remember { TvDeviceUtils.isTv(this@MainActivity) }
            ITuneTheme(appFont = settingsState.appFont) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    if (isTv) {
                        val splashEnabled = settingsState.splashScreenEnabled
                        var showSplash by rememberSaveable { mutableStateOf(splashEnabled) }
                        var revealHomeUnderlay by remember(settingsState.welcomeCompleted, splashEnabled) {
                            mutableStateOf(settingsState.welcomeCompleted && !showSplash)
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
                                TvMainScaffold(
                                    showTopBarBrand = homeBrandVisible,
                                    onTopBarBrandBoundsChanged = { iconR, titleR ->
                                        homeIconBounds = iconR
                                        homeTitleBounds = titleR
                                    },
                                )
                            }
                            if (showWelcomeFlow && !showSplash) {
                                TvWelcomeScreen(
                                    initialName = "",
                                    fromSplash = splashEnabled,
                                    homeIconBounds = homeIconBounds,
                                    homeTitleBounds = homeTitleBounds,
                                    onRevealHomeUnderlay = { enteredName: String ->
                                        SettingsStore.setDisplayName(this@MainActivity, enteredName)
                                        IShareSyncManager.setDisplayName(enteredName)
                                        revealHomeUnderlay = true
                                    },
                                    onContinue = { enteredName: String ->
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
                    } else {
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
                            AppleVolumeOverlay()
                            AppleAlwaysOnDisplayOverlay()
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
        handleRecognitionIntent(intent)
        handleLastFmCallback(intent)
    }

    private fun handleLastFmCallback(intent: android.content.Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "itune" || uri.host != "auth-callback") return
        val token = uri.getQueryParameter("token").orEmpty()
        lifecycleScope.launch(Dispatchers.IO) {
            LastFmIntegration.completeSignIn(this@MainActivity, token)
        }
    }

    private fun handleRecognitionIntent(intent: android.content.Intent?) {
        val action = intent?.action ?: return
        when (action) {
            MusicRecognitionTileService.ACTION_START_RECOGNITION -> {
                MusicRecognitionController.requestOpen()
            }
            MusicRecognitionTileService.ACTION_PLAY_RECOGNIZED -> {
                val title = intent.getStringExtra(MusicRecognitionTileService.EXTRA_SONG_TITLE) ?: return
                val artist = intent.getStringExtra(MusicRecognitionTileService.EXTRA_SONG_ARTIST) ?: ""
                playRecognizedSong(title, artist)
            }
        }
    }

    private fun playRecognizedSong(title: String, artist: String) {
        android.widget.Toast.makeText(this, "Playing $title…", android.widget.Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val query = "$title $artist".trim()
                val tracks = MusicBackend.searchMusic(query)
                val track = tracks.firstOrNull()
                if (track != null) {
                    withContext(Dispatchers.Main) {
                        MusicPlayerState.play(track, tracks, expandPlayer = true)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(this@MainActivity, "Song not found in iTune", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(this@MainActivity, "Playback failed, try again", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleIncomingShareIntent(intent: android.content.Intent?) {
        val action = intent?.action ?: return
        if (action == android.content.Intent.ACTION_SEND || action == android.content.Intent.ACTION_SEND_MULTIPLE) {
            val forwardIntent = android.content.Intent(intent).apply {
                setClass(this@MainActivity, com.darkxvenom.itune.share.ShareToIdentifyActivity::class.java)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(forwardIntent)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                AppleVolumeManager.onVolumeKeyDown(this, keyCode)
                return true
            } else if (event.action == KeyEvent.ACTION_UP) {
                AppleVolumeManager.onVolumeKeyUp(keyCode)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                AppleVolumeManager.onVolumeKeyDown(this, keyCode)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                MusicPlayerState.togglePlayPause()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                if (!MusicPlayerState.isPlaying) {
                    MusicPlayerState.togglePlayPause()
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                MusicPlayerState.pause()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                MusicPlayerState.skipNext()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                MusicPlayerState.skipPrevious()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                MusicPlayerState.pause()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                MusicPlayerState.seekTo(MusicPlayerState.positionMs + 10_000L)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                MusicPlayerState.seekTo((MusicPlayerState.positionMs - 10_000L).coerceAtLeast(0L))
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onStart() {
        super.onStart()
        DynamicIslandForegroundTracker.setForeground(true)
        AppleVolumeManager.setForeground(true)
        SpotifyPlaylistAutoSyncManager.launchSyncOnAppOpen(this)
        com.darkxvenom.itune.data.spotify.SpotifyLibrarySyncManager.launchSync(this)
    }

    override fun onResume() {
        super.onResume()
        DynamicIslandForegroundTracker.setForeground(true)
        AppleVolumeManager.setForeground(true)
        AlwaysOnDisplayManager.setAppForeground(true)
        DynamicIslandStore.syncServiceState(this)
    }

    override fun onStop() {
        super.onStop()
        DynamicIslandForegroundTracker.setForeground(false)
        AppleVolumeManager.setForeground(false)
        AlwaysOnDisplayManager.setAppForeground(false)
        MusicPlayerState.savePlaybackSession(sync = true)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        AlwaysOnDisplayManager.onUserInteraction()
    }

    override fun onDestroy() {
        AlwaysOnDisplayManager.unbindActivity()
        DynamicIslandForegroundTracker.setForeground(false)
        MusicPlayerState.savePlaybackSession(sync = true)
        super.onDestroy()
        // Keep playback alive after the task is cleared; PlaybackService handles the optional stop.
        val keepAlive = MusicPlayerState.isPlaying || OnlineRadioController.state.value.isPlaying
        if (!keepAlive) {
            OnlineRadioController.release()
            MusicPlayerState.release()
            runCatching {
                stopService(Intent(this, com.darkxvenom.itune.island.DynamicIslandService::class.java))
            }
        }
    }
}
// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun HomeScreenPreview() {
    ITuneTheme {
        HomeScreen()
    }
}
