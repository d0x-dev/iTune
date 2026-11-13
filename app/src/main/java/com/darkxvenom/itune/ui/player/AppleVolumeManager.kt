package com.darkxvenom.itune.ui.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.view.KeyEvent
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.radio.OnlineRadioController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Manages Apple iOS-styled volume HUD overlay and intercepts hardware volume keys.
 * Completely suppresses Android system volume controls while applying custom volume changes
 * with fluid spring animations and real-time pause-on-zero enforcement.
 */
object AppleVolumeManager {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var hideJob: Job? = null
    private var registered = false
    private var isAppForeground = false

    private val _volumeFraction = MutableStateFlow(0.5f)
    val volumeFraction: StateFlow<Float> = _volumeFraction.asStateFlow()

    private val _isVisible = MutableStateFlow(false)
    val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    private val _isDragging = MutableStateFlow(false)
    val isDragging: StateFlow<Boolean> = _isDragging.asStateFlow()

    private var audioManager: AudioManager? = null

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                if (streamType == AudioManager.STREAM_MUSIC) {
                    val ctx = context ?: return
                    syncVolumeFromSystem(ctx)
                    if (isAppForeground && !_isDragging.value) {
                        showWithTimeout()
                    }
                }
            }
        }
    }

    fun init(context: Context) {
        val appContext = context.applicationContext
        audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        syncVolumeFromSystem(appContext)

        if (!registered) {
            try {
                val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
                appContext.registerReceiver(volumeReceiver, filter)
                registered = true
            } catch (_: Exception) {}
        }
    }

    fun setForeground(inForeground: Boolean) {
        isAppForeground = inForeground
        if (!inForeground) {
            _isVisible.value = false
            hideJob?.cancel()
        }
    }

    fun onVolumeKeyDown(context: Context, keyCode: Int): Boolean {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }
        val am = audioManager ?: (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager).also { audioManager = it }
            ?: return false

        val direction = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            AudioManager.ADJUST_RAISE
        } else {
            AudioManager.ADJUST_LOWER
        }

        // Adjust without FLAG_SHOW_UI (0) to suppress system volume slider entirely
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        syncVolumeFromSystem(context)
        showWithTimeout()
        return true
    }

    fun onVolumeKeyUp(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
    }

    fun setVolumeFraction(context: Context, fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        _volumeFraction.value = clamped

        val am = audioManager ?: (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager).also { audioManager = it }
        if (am != null) {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                am.getStreamMinVolume(AudioManager.STREAM_MUSIC)
            } else 0
            val target = (min + (clamped * (max - min)).roundToInt()).coerceIn(min, max)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        }

        checkPauseOnZero(clamped)
        showWithTimeout()
    }

    fun onUserInteractionStart() {
        _isDragging.value = true
        hideJob?.cancel()
        _isVisible.value = true
    }

    fun onUserInteractionOngoing() {
        hideJob?.cancel()
    }

    fun onUserInteractionEnd() {
        _isDragging.value = false
        showWithTimeout()
    }

    private fun showWithTimeout() {
        _isVisible.value = true
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(3500)
            if (!_isDragging.value) {
                _isVisible.value = false
            }
        }
    }

    private fun syncVolumeFromSystem(context: Context) {
        val am = audioManager ?: (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager).also { audioManager = it }
            ?: return
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            am.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        } else 0

        val fraction = ((current - min).toFloat() / (max - min).toFloat()).coerceIn(0f, 1f)
        _volumeFraction.value = fraction
        checkPauseOnZero(fraction)
    }

    private fun checkPauseOnZero(fraction: Float) {
        if (fraction <= 0.001f) {
            if (SettingsStore.isPauseOnZeroVolume()) {
                scope.launch {
                    if (MusicPlayerState.isPlaying) {
                        MusicPlayerState.pause()
                    }
                    if (OnlineRadioController.state.value.isPlaying) {
                        OnlineRadioController.pauseIfPlaying()
                    }
                }
            }
        }
    }
}
