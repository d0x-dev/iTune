package com.darkxvenom.itune.island

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

@Immutable
data class DynamicIslandConfig(
    val enabled: Boolean = false,
    val showOutsideAppOnly: Boolean = true,
    val portraitOffsetX: Int = 0,
    val portraitOffsetY: Int = 10,
    val portraitWidth: Int = 172,
    val portraitHeight: Int = 36,
    val landscapeOffsetX: Int = 0,
    val landscapeOffsetY: Int = 8,
    val landscapeWidth: Int = 172,
    val landscapeHeight: Int = 36,
    val bubbleCurvatureDp: Int = 28,
    val waterDropEffect: Boolean = true,
    val bubbleElasticity: Float = 1.0f,
    val liquidSheen: Boolean = true,
    val showVisualizer: Boolean = true,
    val rotatingArtwork: Boolean = true,
    val capsuleSeekStyle: Boolean = true,
    val adaptiveAlbumTint: Boolean = true,
    val tapOpensApp: Boolean = false,
    val autoCollapseSec: Int = 6,
    val pauseKeepSec: Int = 60,
    val bgColor: Int = AndroidColor.BLACK,
    val accentColor: Int = AndroidColor.rgb(250, 45, 72),
    val textColor: Int = AndroidColor.WHITE,
)

/**
 * Tracks whether iTune UI is currently in foreground so the floating Dynamic Island
 * hides inside the app and blooms with water-drop physics immediately outside the app.
 */
object DynamicIslandForegroundTracker {
    @Volatile
    var isForeground: Boolean = false
        private set

    private val listeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    fun setForeground(foreground: Boolean) {
        if (isForeground == foreground) return
        isForeground = foreground
        listeners.forEach { it(foreground) }
    }

    fun addListener(listener: (Boolean) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (Boolean) -> Unit) {
        listeners.remove(listener)
    }
}

/**
 * Reactive store & SharedPreferences persistence for Dynamic Island geometry, water-drop
 * elasticity physics, visual effects, and overlay service lifecycle.
 */
object DynamicIslandStore {
    private const val PREFS = "itune_dynamic_island_prefs"

    private const val K_ENABLED = "di_enabled"
    private const val K_OUTSIDE_ONLY = "di_outside_only"
    private const val K_PORT_X = "di_port_x"
    private const val K_PORT_Y = "di_port_y"
    private const val K_PORT_W = "di_port_w"
    private const val K_PORT_H = "di_port_h"
    private const val K_LAND_X = "di_land_x"
    private const val K_LAND_Y = "di_land_y"
    private const val K_LAND_W = "di_land_w"
    private const val K_LAND_H = "di_land_h"
    private const val K_CURVATURE = "di_curvature"
    private const val K_WATER_DROP = "di_water_drop"
    private const val K_ELASTICITY = "di_elasticity"
    private const val K_LIQUID_SHEEN = "di_liquid_sheen"
    private const val K_VISUALIZER = "di_visualizer"
    private const val K_ROTATE_ART = "di_rotate_art"
    private const val K_CAPSULE_SEEK = "di_capsule_seek"
    private const val K_ADAPTIVE_TINT = "di_adaptive_tint"
    private const val K_TAP_OPENS_APP = "di_tap_opens_app"
    private const val K_AUTO_COLLAPSE = "di_auto_collapse_sec"
    private const val K_PAUSE_KEEP = "di_pause_keep_sec"
    private const val K_BG_COLOR = "di_bg_color"
    private const val K_ACCENT_COLOR = "di_accent_color"
    private const val K_TEXT_COLOR = "di_text_color"

    private val _state = MutableStateFlow(DynamicIslandConfig())
    val state: StateFlow<DynamicIslandConfig> = _state.asStateFlow()

    @Volatile
    private var initialized = false

    fun ensureInit(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            _state.value = DynamicIslandConfig(
                enabled = p.getBoolean(K_ENABLED, false),
                showOutsideAppOnly = p.getBoolean(K_OUTSIDE_ONLY, true),
                portraitOffsetX = p.getInt(K_PORT_X, 0),
                portraitOffsetY = p.getInt(K_PORT_Y, 10),
                portraitWidth = p.getInt(K_PORT_W, 172),
                portraitHeight = p.getInt(K_PORT_H, 36),
                landscapeOffsetX = p.getInt(K_LAND_X, 0),
                landscapeOffsetY = p.getInt(K_LAND_Y, 8),
                landscapeWidth = p.getInt(K_LAND_W, 172),
                landscapeHeight = p.getInt(K_LAND_H, 36),
                bubbleCurvatureDp = p.getInt(K_CURVATURE, 28),
                waterDropEffect = p.getBoolean(K_WATER_DROP, true),
                bubbleElasticity = p.getFloat(K_ELASTICITY, 1.0f).coerceIn(0.6f, 1.4f),
                liquidSheen = p.getBoolean(K_LIQUID_SHEEN, true),
                showVisualizer = p.getBoolean(K_VISUALIZER, true),
                rotatingArtwork = p.getBoolean(K_ROTATE_ART, true),
                capsuleSeekStyle = p.getBoolean(K_CAPSULE_SEEK, true),
                adaptiveAlbumTint = p.getBoolean(K_ADAPTIVE_TINT, true),
                tapOpensApp = p.getBoolean(K_TAP_OPENS_APP, false),
                autoCollapseSec = p.getInt(K_AUTO_COLLAPSE, 6),
                pauseKeepSec = p.getInt(K_PAUSE_KEEP, 60),
                bgColor = p.getInt(K_BG_COLOR, AndroidColor.BLACK),
                accentColor = p.getInt(K_ACCENT_COLOR, AndroidColor.rgb(250, 45, 72)),
                textColor = p.getInt(K_TEXT_COLOR, AndroidColor.WHITE),
            )
            initialized = true
        }
    }

    fun update(context: Context, transform: (DynamicIslandConfig) -> DynamicIslandConfig) {
        ensureInit(context)
        val updated = transform(_state.value)
        _state.value = updated
        persist(context.applicationContext, updated)
        syncServiceState(context.applicationContext)
    }

    fun resetGeometry(context: Context, isLandscape: Boolean) {
        update(context) { cur ->
            if (isLandscape) {
                cur.copy(
                    landscapeOffsetX = 0,
                    landscapeOffsetY = 8,
                    landscapeWidth = 172,
                    landscapeHeight = 36,
                    bubbleCurvatureDp = 28,
                )
            } else {
                cur.copy(
                    portraitOffsetX = 0,
                    portraitOffsetY = 10,
                    portraitWidth = 172,
                    portraitHeight = 36,
                    bubbleCurvatureDp = 28,
                )
            }
        }
    }

    fun syncServiceState(context: Context) {
        ensureInit(context)
        val appCtx = context.applicationContext
        val cfg = _state.value
        val canDraw = Settings.canDrawOverlays(appCtx)
        val intent = Intent(appCtx, DynamicIslandService::class.java)
        runCatching {
            if (cfg.enabled && canDraw) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appCtx.startForegroundService(intent)
                } else {
                    appCtx.startService(intent)
                }
            } else {
                appCtx.stopService(intent)
            }
        }
    }

    private fun persist(context: Context, c: DynamicIslandConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(K_ENABLED, c.enabled)
            .putBoolean(K_OUTSIDE_ONLY, c.showOutsideAppOnly)
            .putInt(K_PORT_X, c.portraitOffsetX)
            .putInt(K_PORT_Y, c.portraitOffsetY)
            .putInt(K_PORT_W, c.portraitWidth)
            .putInt(K_PORT_H, c.portraitHeight)
            .putInt(K_LAND_X, c.landscapeOffsetX)
            .putInt(K_LAND_Y, c.landscapeOffsetY)
            .putInt(K_LAND_W, c.landscapeWidth)
            .putInt(K_LAND_H, c.landscapeHeight)
            .putInt(K_CURVATURE, c.bubbleCurvatureDp)
            .putBoolean(K_WATER_DROP, c.waterDropEffect)
            .putFloat(K_ELASTICITY, c.bubbleElasticity)
            .putBoolean(K_LIQUID_SHEEN, c.liquidSheen)
            .putBoolean(K_VISUALIZER, c.showVisualizer)
            .putBoolean(K_ROTATE_ART, c.rotatingArtwork)
            .putBoolean(K_CAPSULE_SEEK, c.capsuleSeekStyle)
            .putBoolean(K_ADAPTIVE_TINT, c.adaptiveAlbumTint)
            .putBoolean(K_TAP_OPENS_APP, c.tapOpensApp)
            .putInt(K_AUTO_COLLAPSE, c.autoCollapseSec)
            .putInt(K_PAUSE_KEEP, c.pauseKeepSec)
            .putInt(K_BG_COLOR, c.bgColor)
            .putInt(K_ACCENT_COLOR, c.accentColor)
            .putInt(K_TEXT_COLOR, c.textColor)
            .apply()
    }
}
