package com.darkxvenom.itune.ui.aod

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.data.library.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages Apple iOS / StandBy styled Always-On Display (AOD) mode.
 * - Supports manual one-tap activation from Player's 3-dot popup.
 * - Supports automatic activation when song is playing and no user interaction occurs for the configured timeout.
 * - Activates only when the app is in the foreground ("only inside the app, outside app don't activate").
 * - Supports Immersive full-screen mode by hiding status bar and navigation bar.
 * - Supports keeping screen awake (FLAG_KEEP_SCREEN_ON).
 */
object AlwaysOnDisplayManager {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var idleJob: Job? = null

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private var lastInteractionTimeMs: Long = System.currentTimeMillis()
    private var isAppForeground: Boolean = true
    private var attachedActivity: Activity? = null

    fun init(context: Context) {
        lastInteractionTimeMs = System.currentTimeMillis()
        startIdleWatcher()
    }

    fun bindActivity(activity: Activity) {
        attachedActivity = activity
    }

    fun unbindActivity() {
        attachedActivity = null
    }

    fun setAppForeground(foreground: Boolean) {
        isAppForeground = foreground
        lastInteractionTimeMs = System.currentTimeMillis()
        if (!foreground && _isActive.value) {
            // Dismiss if app went to background
            dismiss()
        }
    }

    fun onUserInteraction() {
        lastInteractionTimeMs = System.currentTimeMillis()
    }

    fun activate() {
        _isActive.value = true
        lastInteractionTimeMs = System.currentTimeMillis()
        attachedActivity?.let { applyWindowFlags(it, true) }
    }

    fun dismiss() {
        _isActive.value = false
        lastInteractionTimeMs = System.currentTimeMillis()
        attachedActivity?.let { applyWindowFlags(it, false) }
    }

    fun toggle() {
        if (_isActive.value) dismiss() else activate()
    }

    private fun startIdleWatcher() {
        idleJob?.cancel()
        idleJob = scope.launch {
            while (isActive) {
                delay(1000)
                try {
                    checkIdleTimeout()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun checkIdleTimeout() {
        if (!isAppForeground) return
        if (_isActive.value) return

        val aodEnabled = SettingsStore.isAodEnabled()
        if (!aodEnabled) return

        val timeoutSec = SettingsStore.getAodTimeoutSeconds()
        if (timeoutSec <= 0) return // 0 = Manual only

        val isMusicPlaying = MusicPlayerState.isPlaying
        if (!isMusicPlaying) {
            lastInteractionTimeMs = System.currentTimeMillis()
            return
        }

        val elapsed = System.currentTimeMillis() - lastInteractionTimeMs
        if (elapsed >= timeoutSec * 1000L) {
            activate()
        }
    }

    private fun applyWindowFlags(activity: Activity, active: Boolean) {
        val window = activity.window ?: return
        val immersive = SettingsStore.isAodImmersive()
        val keepScreenOn = SettingsStore.isAodKeepScreenOn()

        val controller = WindowInsetsControllerCompat(window, window.decorView)

        if (active) {
            if (keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            if (immersive) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
