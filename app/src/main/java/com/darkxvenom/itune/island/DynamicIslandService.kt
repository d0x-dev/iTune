package com.darkxvenom.itune.island

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.util.LruCache
import android.util.SparseArray
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
import androidx.annotation.DrawableRes
import androidx.compose.runtime.snapshotFlow
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.darkxvenom.itune.MainActivity
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.backend.highQualityThumbnail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private data class IslandMediaSnapshot(
    val track: MusicTrack?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val shuffleEnabled: Boolean,
    val repeatMode: Int,
    val isFavorite: Boolean,
)

/**
 * Zero-lag hardware-accelerated Dynamic Island overlay service with cloth-like water-drop bloom,
 * concentric ripple waves, adaptive album glow, seekbar scrubbing, and full transport controls.
 * Strictly renders ONLY outside the app when [DynamicIslandConfig.showOutsideAppOnly] is enabled.
 */
class DynamicIslandService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private lateinit var windowManager: WindowManager
    private lateinit var islandView: DynamicIslandOverlayView

    private var isAdded = false
    private var isAppInForeground = DynamicIslandForegroundTracker.isForeground
    private var config = DynamicIslandConfig()
    private var lastLoadedArtUrl: String? = null
    private var pauseDismissJob: Job? = null
    private var autoCollapseJob: Job? = null

    private val artCache = LruCache<String, Pair<Bitmap, Int>>(16)
    private val imageLoader by lazy { ImageLoader(this) }

    private val foregroundListener: (Boolean) -> Unit = { foreground ->
        isAppInForeground = foreground
        syncVisibilityAndContent()
    }

    override fun onCreate() {
        super.onCreate()
        DynamicIslandStore.ensureInit(this)
        config = DynamicIslandStore.state.value
        isAppInForeground = DynamicIslandForegroundTracker.isForeground
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        islandView = DynamicIslandOverlayView(
            context = this,
            onExpandedChanged = { expanded ->
                updateWindowLayout()
                if (expanded) scheduleAutoCollapse() else cancelAutoCollapse()
            },
            onOpenApp = { openITuneApp() },
            onShuffle = {
                MusicPlayerState.toggleShuffle()
                scheduleAutoCollapse()
            },
            onPrevious = {
                MusicPlayerState.skipPrevious(forcePrevious = true)
                scheduleAutoCollapse()
            },
            onPlayPause = {
                MusicPlayerState.togglePlayPause()
                scheduleAutoCollapse()
            },
            onNext = {
                MusicPlayerState.skipNext()
                scheduleAutoCollapse()
            },
            onRepeat = {
                MusicPlayerState.toggleRepeat()
                scheduleAutoCollapse()
            },
            onToggleFavorite = {
                MusicPlayerState.currentTrack?.let { MusicPlayerState.toggleFavorite(it) }
                scheduleAutoCollapse()
            },
            onSeekTo = { posMs ->
                MusicPlayerState.seekTo(posMs)
                scheduleAutoCollapse()
            },
            onInteraction = { scheduleAutoCollapse() },
        )

        DynamicIslandForegroundTracker.addListener(foregroundListener)

        // React instantaneously to config + MusicPlayerState changes
        scope.launch {
            val mediaFlow = snapshotFlow {
                val t = MusicPlayerState.currentTrack
                IslandMediaSnapshot(
                    track = t,
                    isPlaying = MusicPlayerState.isPlaying,
                    positionMs = MusicPlayerState.positionMs,
                    durationMs = MusicPlayerState.durationMs,
                    shuffleEnabled = MusicPlayerState.shuffleEnabled,
                    repeatMode = MusicPlayerState.repeatMode,
                    isFavorite = t != null && MusicPlayerState.isFavorite(t.id),
                )
            }
            combine(DynamicIslandStore.state, mediaFlow) { cfg, media -> cfg to media }
                .distinctUntilChanged()
                .collectLatest { (cfg, media) ->
                    config = cfg
                    applyConfigToView()
                    applyMediaToView(media)
                    syncVisibilityAndContent()
                }
        }

        // Smooth lightweight timeline sync when visible outside app
        scope.launch {
            while (isActive) {
                if (isAdded && MusicPlayerState.isPlaying) {
                    islandView.updateProgressFast(
                        positionMs = MusicPlayerState.positionMs.coerceAtLeast(0L),
                        durationMs = resolveSafeDurationMs(MusicPlayerState.currentTrack, MusicPlayerState.durationMs),
                        isPlaying = true,
                    )
                }
                delay(280L)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        config = DynamicIslandStore.state.value
        if (!config.enabled || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        applyConfigToView()
        syncVisibilityAndContent()
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyConfigToView()
        updateWindowLayout()
    }

    override fun onDestroy() {
        DynamicIslandForegroundTracker.removeListener(foregroundListener)
        pauseDismissJob?.cancel()
        autoCollapseJob?.cancel()
        hideIslandImmediate()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "Dynamic Island",
                    NotificationManager.IMPORTANCE_MIN,
                ).apply {
                    description = "Keeps the outside-app Dynamic Island water bubble responsive"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(ch)
            }
        }

        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            this,
            9101,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_itune_waves)
            .setContentTitle("Dynamic Island active")
            .setContentText("Shows smoothly outside iTune during playback")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pi)
            .build()

        runCatching {
            val fgType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notif, fgType)
        }
    }

    private fun resolveSafeDurationMs(track: MusicTrack?, rawDurMs: Long): Long =
        when {
            rawDurMs > 0L -> rawDurMs
            track != null && track.durationSec > 0L -> track.durationSec * 1000L
            else -> 0L
        }

    private fun applyConfigToView() {
        val isLand = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val widthDp = if (isLand) config.landscapeWidth else config.portraitWidth
        val heightDp = if (isLand) config.landscapeHeight else config.portraitHeight
        val offsetYDp = if (isLand) config.landscapeOffsetY else config.portraitOffsetY
        islandView.applyConfig(
            widthDp = widthDp,
            heightDp = heightDp,
            offsetYDp = offsetYDp,
            statusBarPx = statusBarHeightPx(),
            config = config,
        )
    }

    private fun applyMediaToView(media: IslandMediaSnapshot) {
        val track = media.track
        val safeDur = resolveSafeDurationMs(track, media.durationMs)
        islandView.updateMedia(
            title = track?.title.orEmpty(),
            artist = track?.artists.orEmpty(),
            isPlaying = media.isPlaying,
            positionMs = media.positionMs.coerceAtLeast(0L),
            durationMs = safeDur,
            isShuffle = media.shuffleEnabled,
            repeatMode = media.repeatMode,
            isFavorite = media.isFavorite,
        )
        loadArtworkCached(track?.thumbnailUrl?.highQualityThumbnail() ?: track?.thumbnailUrl)
    }

    private fun syncVisibilityAndContent() {
        if (!config.enabled || !Settings.canDrawOverlays(this)) {
            hideIslandImmediate()
            return
        }

        // Strict guarantee: never display overlay inside app when outside-only is active
        if (config.showOutsideAppOnly && isAppInForeground) {
            pauseDismissJob?.cancel()
            cancelAutoCollapse()
            hideIslandImmediate()
            return
        }

        val track = MusicPlayerState.currentTrack
        if (track == null) {
            pauseDismissJob?.cancel()
            hideIslandImmediate()
            return
        }

        if (MusicPlayerState.isPlaying) {
            pauseDismissJob?.cancel()
            pauseDismissJob = null
            showIsland()
        } else {
            val keepSec = config.pauseKeepSec
            if (keepSec <= 0) {
                pauseDismissJob?.cancel()
                hideIslandImmediate()
            } else {
                showIsland()
                if (pauseDismissJob == null || pauseDismissJob?.isActive == false) {
                    pauseDismissJob = scope.launch {
                        delay(keepSec * 1000L)
                        if (!MusicPlayerState.isPlaying) {
                            hideIslandImmediate()
                        }
                    }
                }
            }
        }
    }

    private fun scheduleAutoCollapse() {
        autoCollapseJob?.cancel()
        val sec = config.autoCollapseSec
        if (sec <= 0 || !islandView.expanded) return
        autoCollapseJob = scope.launch {
            delay(sec * 1000L)
            if (isAdded && islandView.expanded) {
                islandView.collapseSmooth()
            }
        }
    }

    private fun cancelAutoCollapse() {
        autoCollapseJob?.cancel()
        autoCollapseJob = null
    }

    private fun loadArtworkCached(url: String?) {
        if (url.isNullOrBlank()) {
            lastLoadedArtUrl = null
            islandView.setArtwork(null, Color.rgb(250, 45, 72))
            return
        }
        if (url == lastLoadedArtUrl) return
        lastLoadedArtUrl = url

        artCache.get(url)?.let { (bmp, tint) ->
            islandView.setArtwork(bmp, tint)
            return
        }

        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val req = ImageRequest.Builder(this@DynamicIslandService)
                        .data(url)
                        .size(220)
                        .allowHardware(false)
                        .build()
                    val rawBmp = (imageLoader.execute(req) as? SuccessResult)?.drawable?.toBitmap() ?: return@runCatching null
                    val tint = resolveVibrantAccentColor(rawBmp)
                    rawBmp to tint
                }.getOrNull()
            }
            if (lastLoadedArtUrl == url && loaded != null) {
                artCache.put(url, loaded)
                islandView.setArtwork(loaded.first, loaded.second)
            }
        }
    }

    private fun openITuneApp() {
        if (islandView.expanded) islandView.collapseImmediate()
        runCatching {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }
    }

    private fun showIsland() {
        if (isAdded) {
            updateWindowLayout()
            islandView.invalidate()
            return
        }
        runCatching {
            windowManager.addView(islandView, createWindowParams())
            isAdded = true
            islandView.playEntryWaterDrop()
        }
    }

    private fun hideIslandImmediate() {
        if (!isAdded) return
        islandView.cancelAnimationsAndCollapse()
        runCatching { windowManager.removeView(islandView) }
        isAdded = false
    }

    private fun updateWindowLayout() {
        if (!isAdded) return
        runCatching { windowManager.updateViewLayout(islandView, createWindowParams()) }
    }

    private fun resolveVibrantAccentColor(bmp: Bitmap): Int {
        val fallback = Color.rgb(250, 45, 72)
        return runCatching {
            val pal = Palette.from(bmp).maximumColorCount(16).generate()
            val candidate = pal.vibrantSwatch?.rgb
                ?: pal.lightVibrantSwatch?.rgb
                ?: pal.darkVibrantSwatch?.rgb
                ?: pal.dominantSwatch?.rgb
                ?: fallback
            val hsv = FloatArray(3)
            Color.colorToHSV(candidate, hsv)
            if (hsv[1] < 0.22f || hsv[2] < 0.45f) {
                fallback
            } else {
                hsv[2] = hsv[2].coerceAtLeast(0.78f)
                Color.HSVToColor(hsv)
            }
        }.getOrDefault(fallback)
    }

    private fun statusBarHeightPx(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val top = runCatching {
                windowManager.currentWindowMetrics.windowInsets
                    .getInsetsIgnoringVisibility(android.view.WindowInsets.Type.statusBars()).top
            }.getOrDefault(0)
            if (top > 0) return top
        }
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else 32.dpPx
    }

    private fun createWindowParams(): WindowManager.LayoutParams {
        val isLand = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val offX = if (isLand) config.landscapeOffsetX else config.portraitOffsetX
        val offY = if (isLand) config.landscapeOffsetY else config.portraitOffsetY
        val wDp = if (isLand) config.landscapeWidth else config.portraitWidth
        val hDp = if (isLand) config.landscapeHeight else config.portraitHeight
        val isExpandedOrMorphing = islandView.expanded || islandView.isAnimatingMorph

        val collapsedW = wDp.coerceAtLeast(36).dpPx
        val visualCollapsedH = hDp.coerceAtLeast(22).dpPx
        // Ensure collapsed overlay extends safely below system status-bar touch interception band
        val minClearanceH = (statusBarHeightPx() - offY.dpPx + 24.dpPx).coerceAtLeast(visualCollapsedH)
        val collapsedTouchH = maxOf(visualCollapsedH + 18.dpPx, minClearanceH)

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            if (isExpandedOrMorphing) WindowManager.LayoutParams.MATCH_PARENT else collapsedW,
            if (isExpandedOrMorphing) WindowManager.LayoutParams.MATCH_PARENT else collapsedTouchH,
            type,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = if (isExpandedOrMorphing) 0 else offX.dpPx
            y = if (isExpandedOrMorphing) 0 else offY.dpPx
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private val Int.dpPx: Int
        get() = (this * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val CHANNEL_ID = "itune_dynamic_island_channel"
        private const val NOTIFICATION_ID = 9088
    }
}

/**
 * Hardware-accelerated Dynamic Island overlay View featuring asymmetric cloth-like water-drop morphing,
 * concentric ripple rings, specular liquid rim, BitmapShader artwork caching, and full playback gestures.
 */
private class DynamicIslandOverlayView(
    context: Context,
    private val onExpandedChanged: (Boolean) -> Unit,
    private val onOpenApp: () -> Unit,
    private val onShuffle: () -> Unit,
    private val onPrevious: () -> Unit,
    private val onPlayPause: () -> Unit,
    private val onNext: () -> Unit,
    private val onRepeat: () -> Unit,
    private val onToggleFavorite: () -> Unit,
    private val onSeekTo: (Long) -> Unit,
    private val onInteraction: () -> Unit,
) : View(context) {

    var expanded: Boolean = false
        private set

    val isAnimatingMorph: Boolean
        get() = morphAnimating

    private var cfg = DynamicIslandConfig()
    private var collapsedWidthDp = 172
    private var collapsedHeightDp = 36
    private var offsetYDp = 10
    private var statusBarClearancePx = 0

    private val density = resources.displayMetrics.density
    private val Int.dp: Float get() = this * density
    private val Float.dp: Float get() = this * density

    // Media snapshot
    private var titleText = "iTune"
    private var artistText = ""
    private var isPlaying = false
    private var positionMs = 0L
    private var durationMs = 0L
    private var isShuffleEnabled = false
    private var repeatMode = Player.REPEAT_MODE_OFF
    private var isFavorite = false

    // Artwork & palette cache (zero per-frame allocations)
    private var artworkBitmap: Bitmap? = null
    private var artworkShader: BitmapShader? = null
    private val shaderMatrix = Matrix()
    private var dynamicAccentColor = Color.rgb(250, 45, 72)
    private var cachedGlowShader: RadialGradient? = null
    private var cachedGlowKey: Long = -1L
    private var cachedSheenShader: LinearGradient? = null
    private var cachedSheenKey: Long = -1L

    // Cloth + Water-Drop animators
    private var morphProgress = 0f
    private var rippleProgress = 0f
    private var entryProgress = 1f
    private var morphAnimating = false
    private var rippleAnimating = false
    private var morphAnimator: ValueAnimator? = null
    private var rippleAnimator: ValueAnimator? = null
    private var entryAnimator: ValueAnimator? = null
    private var rotationAngle = 0f
    private var vsyncScheduled = false

    // Touch / drag tracking
    private var downX = 0f
    private var downY = 0f
    private var isTapCandidate = false
    private var isDraggingSeekbar = false
    private var scrubPosMs = 0L
    private var dragSwipeY = 0f

    // Pre-allocated Paints, Rects & Paths
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.0f.dp
    }
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val artPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val artworkFallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(34, 34, 38) }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 15.5f.dp
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 255, 255)
        textSize = 12.5f.dp
    }
    private val miniTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f.dp
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(185, 255, 255, 255)
        textSize = 11.5f.dp
    }
    private val trackBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(58, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val trackFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val waveFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 3.5f.dp
    }
    private val playBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(38, 255, 255, 255)
    }
    private val equalizerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
    }
    private val rippleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val rippleBloomPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val islandBounds = RectF()
    private val collapsedBounds = RectF()
    private val expandedBounds = RectF()
    private val artRect = RectF()
    private val tempRect = RectF()
    private val wavePath = Path()
    private val drawableCache = SparseArray<Drawable>()

    // Reusable interpolators
    private val clothFastOutSlowIn = PathInterpolator(0.18f, 0.96f, 0.24f, 1.0f)
    private val clothCollapseCurve = PathInterpolator(0.32f, 0.0f, 0.20f, 1.0f)
    private val rippleOutCurve = PathInterpolator(0.14f, 0.86f, 0.28f, 1.0f)

    private val vsyncRunnable = Runnable {
        vsyncScheduled = false
        val needsMoreFrames = isAttachedToWindow && (isPlaying || morphAnimating || rippleAnimating || entryAnimator?.isRunning == true)
        if (needsMoreFrames) {
            if (isPlaying && cfg.rotatingArtwork) {
                rotationAngle = (rotationAngle + 0.85f) % 360f
            }
            invalidate()
            scheduleVsync()
        }
    }

    init {
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun applyConfig(widthDp: Int, heightDp: Int, offsetYDp: Int, statusBarPx: Int, config: DynamicIslandConfig) {
        this.cfg = config
        this.collapsedWidthDp = widthDp
        this.collapsedHeightDp = heightDp
        this.offsetYDp = offsetYDp
        this.statusBarClearancePx = statusBarPx

        bgPaint.color = config.bgColor
        val tc = config.textColor
        titlePaint.color = tc
        miniTitlePaint.color = tc
        subPaint.color = Color.argb(178, Color.red(tc), Color.green(tc), Color.blue(tc))
        timePaint.color = Color.argb(182, Color.red(tc), Color.green(tc), Color.blue(tc))

        updateAccentShaders()
        invalidate()
    }

    fun updateMedia(
        title: String,
        artist: String,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        isShuffle: Boolean,
        repeatMode: Int,
        isFavorite: Boolean,
    ) {
        this.titleText = title.ifBlank { "iTune" }
        this.artistText = artist
        this.isPlaying = isPlaying
        if (!isDraggingSeekbar) this.positionMs = positionMs
        this.durationMs = durationMs
        this.isShuffleEnabled = isShuffle
        this.repeatMode = repeatMode
        this.isFavorite = isFavorite
        if (isPlaying || morphAnimating || rippleAnimating) scheduleVsync()
        invalidate()
    }

    fun updateProgressFast(positionMs: Long, durationMs: Long, isPlaying: Boolean) {
        if (!isDraggingSeekbar) this.positionMs = positionMs
        this.durationMs = durationMs
        this.isPlaying = isPlaying
        invalidate()
    }

    fun setArtwork(bitmap: Bitmap?, accentRgb: Int) {
        artworkBitmap = bitmap
        artworkShader = bitmap?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        dynamicAccentColor = accentRgb
        updateAccentShaders()
        invalidate()
    }

    private fun updateAccentShaders() {
        val activeAccent = if (cfg.adaptiveAlbumTint) dynamicAccentColor else cfg.accentColor
        waveFillPaint.color = activeAccent
        equalizerPaint.color = activeAccent
    }

    fun playEntryWaterDrop() {
        entryAnimator?.cancel()
        if (!cfg.waterDropEffect) {
            entryProgress = 1f
            invalidate()
            return
        }
        entryProgress = 0.01f
        triggerRippleWave()
        entryAnimator = ValueAnimator.ofFloat(0.01f, 1f).apply {
            duration = 380L
            interpolator = clothFastOutSlowIn
            addUpdateListener {
                entryProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        scheduleVsync()
    }

    fun collapseSmooth() {
        if (!expanded && morphProgress == 0f) return
        collapseWithWaterDrop()
    }

    fun collapseImmediate() {
        morphAnimator?.cancel()
        rippleAnimator?.cancel()
        morphAnimating = false
        rippleAnimating = false
        morphProgress = 0f
        rippleProgress = 0f
        expanded = false
        onExpandedChanged(false)
        invalidate()
    }

    fun cancelAnimationsAndCollapse() {
        morphAnimator?.cancel()
        rippleAnimator?.cancel()
        entryAnimator?.cancel()
        removeCallbacks(vsyncRunnable)
        vsyncScheduled = false
        morphAnimating = false
        rippleAnimating = false
        morphProgress = 0f
        expanded = false
    }

    private fun scheduleVsync() {
        if (!vsyncScheduled && isAttachedToWindow) {
            vsyncScheduled = true
            postOnAnimation(vsyncRunnable)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isPlaying || morphAnimating || rippleAnimating) scheduleVsync()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(vsyncRunnable)
        vsyncScheduled = false
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        updateBounds()

        val entryScale = if (entryProgress < 1f) (0.72f + 0.28f * entryProgress) else 1f
        val pivotX = islandBounds.centerX()
        val pivotY = islandBounds.top + islandBounds.height() * 0.2f

        val entryCheckpoint = canvas.save()
        if (entryProgress < 1f) {
            canvas.scale(entryScale, entryScale, pivotX, pivotY)
        }

        val collapsedRadius = islandBounds.height() / 2f
        val corner = if (!expanded && morphProgress <= 0.001f) {
            collapsedRadius
        } else {
            lerp(collapsedRadius, cfg.bubbleCurvatureDp.toFloat().dp, morphProgress.coerceIn(0f, 1f))
        }

        // 1. Concentric Water-Drop Ripples behind/around island bloom
        if (cfg.waterDropEffect && rippleAnimating && rippleProgress > 0f) {
            drawWaterRipples(canvas)
        }

        // 2. Main Water-Bubble Base
        canvas.drawRoundRect(islandBounds, corner, corner, bgPaint)

        // 3. Adaptive Album Ambient Tint inside bubble when expanded/blooming (zero alloc per-frame cache)
        if (cfg.adaptiveAlbumTint && cfg.liquidSheen && morphProgress > 0.08f) {
            val tintAlpha = (56 * morphProgress.coerceIn(0f, 1f)).toInt()
            if (tintAlpha > 0) {
                val gx = islandBounds.left + islandBounds.width() * 0.25f
                val gy = islandBounds.top + islandBounds.height() * 0.35f
                val gr = islandBounds.width().coerceAtLeast(1f) * 0.72f
                val gKey = (gx.roundToInt().toLong() shl 44) xor
                    (gy.roundToInt().toLong() shl 28) xor
                    (gr.roundToInt().toLong() shl 14) xor
                    ((dynamicAccentColor and 0xFFFFFF).toLong() shl 4) xor
                    tintAlpha.toLong()
                if (cachedGlowShader == null || cachedGlowKey != gKey) {
                    cachedGlowKey = gKey
                    cachedGlowShader = RadialGradient(
                        gx,
                        gy,
                        gr,
                        Color.argb(
                            tintAlpha,
                            Color.red(dynamicAccentColor),
                            Color.green(dynamicAccentColor),
                            Color.blue(dynamicAccentColor),
                        ),
                        Color.TRANSPARENT,
                        Shader.TileMode.CLAMP,
                    )
                }
                glowPaint.shader = cachedGlowShader
                canvas.drawRoundRect(islandBounds, corner, corner, glowPaint)
            }
        }

        // 4. Water-Drop Soft Core Bloom + Glass Specular Top Sheen (zero alloc per-frame cache)
        if (cfg.liquidSheen) {
            val topHighlightH = (islandBounds.height() * 0.34f).coerceIn(10f.dp, 64f.dp)
            val sheenAlpha = if (morphProgress > 0.3f) 32 else 22
            val sKey = (islandBounds.top.roundToInt().toLong() shl 32) xor
                (topHighlightH.roundToInt().toLong() shl 10) xor
                sheenAlpha.toLong()
            if (cachedSheenShader == null || cachedSheenKey != sKey) {
                cachedSheenKey = sKey
                cachedSheenShader = LinearGradient(
                    0f,
                    islandBounds.top,
                    0f,
                    islandBounds.top + topHighlightH,
                    Color.argb(sheenAlpha, 255, 255, 255),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP,
                )
            }
            sheenPaint.shader = cachedSheenShader
            canvas.drawRoundRect(islandBounds, corner, corner, sheenPaint)
        }

        // 5. Crisp Liquid Glass Border Rim
        val rimAlpha = lerp(64f, 52f, morphProgress.coerceIn(0f, 1f)).toInt()
        rimStrokePaint.color = Color.argb(rimAlpha, 255, 255, 255)
        tempRect.set(islandBounds)
        tempRect.inset(0.5f.dp, 0.5f.dp)
        canvas.drawRoundRect(tempRect, (corner - 0.5f.dp).coerceAtLeast(1f), (corner - 0.5f.dp).coerceAtLeast(1f), rimStrokePaint)

        // 6. Cross-fade between Collapsed Pill Content and Expanded Bubble Content
        val contentCheckpoint = canvas.save()
        canvas.translate(islandBounds.left, islandBounds.top)
        if (morphProgress < 0.36f) {
            val alpha = ((0.36f - morphProgress) / 0.36f * 255f).toInt().coerceIn(0, 255)
            drawCollapsedContent(canvas, alpha)
        }
        if (morphProgress > 0.28f) {
            val alpha = ((morphProgress - 0.28f) / 0.72f * 255f).toInt().coerceIn(0, 255)
            drawExpandedContent(canvas, alpha)
        }
        canvas.restoreToCount(contentCheckpoint)
        canvas.restoreToCount(entryCheckpoint)
    }

    private fun drawWaterRipples(canvas: Canvas) {
        val rp = rippleProgress.coerceIn(0f, 1f)
        val cx = islandBounds.centerX()
        val cy = islandBounds.top + islandBounds.height() * 0.46f

        val wave1Radius = lerp(24f.dp, islandBounds.width().coerceAtLeast(220f.dp) * 0.72f, rp)
        val alpha1 = ((1f - rp) * (1f - rp) * 140f).toInt().coerceIn(0, 255)
        if (alpha1 > 0) {
            rippleStrokePaint.color = Color.argb(alpha1, 255, 255, 255)
            rippleStrokePaint.strokeWidth = (3.2f * (1f - rp) + 0.6f).dp
            canvas.drawCircle(cx, cy, wave1Radius, rippleStrokePaint)
        }

        val wave2P = ((rp - 0.16f) / 0.84f).coerceIn(0f, 1f)
        if (wave2P > 0f) {
            val wave2Radius = lerp(16f.dp, islandBounds.width().coerceAtLeast(200f.dp) * 0.58f, wave2P)
            val alpha2 = ((1f - wave2P) * (1f - wave2P) * 95f).toInt().coerceIn(0, 255)
            if (alpha2 > 0) {
                rippleStrokePaint.color = Color.argb(alpha2, 255, 255, 255)
                rippleStrokePaint.strokeWidth = (2.4f * (1f - wave2P) + 0.5f).dp
                canvas.drawCircle(cx, cy, wave2Radius, rippleStrokePaint)
            }
        }

        val bloomAlpha = ((1f - rp) * 44f).toInt().coerceIn(0, 80)
        if (bloomAlpha > 0) {
            rippleBloomPaint.color = Color.argb(bloomAlpha, 255, 255, 255)
            canvas.drawCircle(cx, cy, lerp(14f.dp, islandBounds.width() * 0.40f, rp), rippleBloomPaint)
        }
    }

    private fun drawCollapsedContent(canvas: Canvas, alpha: Int) {
        val w = islandBounds.width()
        val h = islandBounds.height()

        if (w <= 54f.dp) {
            val pad = 3.5f.dp
            artRect.set(pad, pad, w - pad, h - pad)
            drawShaderArtwork(canvas, artRect, (h - pad * 2f) / 2f, rotate = true, alpha = alpha)
            return
        }

        val artSize = (h - 8f.dp).coerceAtLeast(16f.dp)
        val artTop = (h - artSize) / 2f
        val artLeft = if (w < 112f.dp) 4f.dp else 6f.dp
        artRect.set(artLeft, artTop, artLeft + artSize, artTop + artSize)
        drawShaderArtwork(canvas, artRect, artSize / 2f, rotate = true, alpha = alpha)

        if (w >= 145f.dp && !cfg.showVisualizer) {
            miniTitlePaint.alpha = alpha
            val maxChars = ((w - artRect.right - 20f.dp) / 6.8f.dp).toInt().coerceAtLeast(6)
            val label = ellipsize(titleText, maxChars)
            val textY = h / 2f + 4.2f.dp
            canvas.drawText(label, artRect.right + 8f.dp, textY, miniTitlePaint)
        } else if (w >= 175f.dp && cfg.showVisualizer) {
            miniTitlePaint.alpha = (alpha * 0.90f).toInt()
            val maxChars = ((w - artRect.right - 48f.dp) / 6.8f.dp).toInt().coerceAtLeast(5)
            val label = ellipsize(titleText, maxChars)
            canvas.drawText(label, artRect.right + 8f.dp, h / 2f + 4.2f.dp, miniTitlePaint)
        }

        if (cfg.showVisualizer) {
            drawVisualizerBars(
                canvas = canvas,
                cx = w - 20f.dp,
                cy = h / 2f,
                compact = true,
                alpha = alpha,
            )
        }
    }

    private fun drawExpandedContent(canvas: Canvas, alpha: Int) {
        val w = islandBounds.width()
        if (w < 140f.dp) return

        // Top Pull Indicator pill hint (subtle swipe-up affordability)
        trackBgPaint.alpha = (alpha * 0.34f).toInt()
        tempRect.set(w / 2f - 18f.dp, 8f.dp, w / 2f + 18f.dp, 11.5f.dp)
        canvas.drawRoundRect(tempRect, 2f.dp, 2f.dp, trackBgPaint)

        // Artwork + Title + Artist
        val artSize = 54f.dp
        artRect.set(22f.dp, 24f.dp, 22f.dp + artSize, 24f.dp + artSize)
        drawShaderArtwork(canvas, artRect, 13f.dp, rotate = false, alpha = alpha)

        titlePaint.alpha = alpha
        subPaint.alpha = (alpha * 0.72f).toInt()
        val textMaxChars = ((w - 156f.dp) / 8.2f.dp).toInt().coerceIn(12, 32)
        canvas.drawText(ellipsize(titleText, textMaxChars), 88f.dp, 46f.dp, titlePaint)
        canvas.drawText(ellipsize(artistText.ifBlank { "iTune" }, textMaxChars + 6), 88f.dp, 66f.dp, subPaint)

        // Right Header: Favorite Star + Visualizer
        val starIcon = if (isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
        val starTint = if (isFavorite) Color.argb(alpha, 250, 45, 72) else Color.argb((alpha * 0.70f).toInt(), 255, 255, 255)
        drawCachedDrawable(canvas, starIcon, w - 58f.dp, 50f.dp, 20f, starTint)

        if (cfg.showVisualizer) {
            drawVisualizerBars(canvas = canvas, cx = w - 28f.dp, cy = 50f.dp, compact = false, alpha = alpha)
        }

        // Progress Scrubber + Apple Music timing format (elapsed left, -remaining right)
        val pStart = 24f.dp
        val pEnd = w - 24f.dp
        val pY = 98f.dp
        val curMs = if (isDraggingSeekbar) scrubPosMs else positionMs.coerceAtLeast(0L)
        val durMs = durationMs.coerceAtLeast(1L)
        val fraction = if (durationMs > 0L) (curMs.toFloat() / durMs.toFloat()).coerceIn(0f, 1f) else 0f
        val fillX = pStart + (pEnd - pStart) * fraction

        if (cfg.capsuleSeekStyle) {
            val barH = if (isDraggingSeekbar) 8.5f.dp else 6.5f.dp
            val r = barH / 2f
            trackBgPaint.alpha = (alpha * 0.26f).toInt()
            tempRect.set(pStart, pY - r, pEnd, pY + r)
            canvas.drawRoundRect(tempRect, r, r, trackBgPaint)

            if (fillX > pStart + 2f.dp) {
                trackFillPaint.color = cfg.textColor
                trackFillPaint.alpha = (alpha * 0.90f).toInt()
                tempRect.set(pStart, pY - r, fillX, pY + r)
                canvas.drawRoundRect(tempRect, r, r, trackFillPaint)
            }
        } else {
            trackBgPaint.alpha = (alpha * 0.28f).toInt()
            tempRect.set(pStart, pY - 2f.dp, pEnd, pY + 2f.dp)
            canvas.drawRoundRect(tempRect, 2f.dp, 2f.dp, trackBgPaint)

            if (fillX > pStart) {
                waveFillPaint.alpha = alpha
                if (isPlaying && !isDraggingSeekbar) {
                    wavePath.reset()
                    wavePath.moveTo(pStart, pY)
                    val wl = 20f.dp
                    val freq = (2f * PI.toFloat()) / wl
                    val phase = (SystemClock.uptimeMillis() % 1000L) / 1000f * (2f * PI.toFloat())
                    val amp = 2.2f.dp
                    var sx = pStart
                    while (sx < fillX) {
                        wavePath.lineTo(sx, pY + sin((sx - pStart) * freq + phase) * amp)
                        sx += 2f.dp
                    }
                    wavePath.lineTo(fillX, pY)
                    canvas.drawPath(wavePath, waveFillPaint)
                } else {
                    canvas.drawLine(pStart, pY, fillX, pY, waveFillPaint)
                }
            }
        }

        // Timestamps
        timePaint.alpha = (alpha * 0.72f).toInt()
        val elapsedStr = formatMs(curMs)
        val remStr = if (durationMs > 0L) "-${formatMs((durationMs - curMs).coerceAtLeast(0L))}" else "--:--"
        canvas.drawText(elapsedStr, pStart, 118f.dp, timePaint)
        val remW = timePaint.measureText(remStr)
        canvas.drawText(remStr, pEnd - remW, 118f.dp, timePaint)

        // 5 Transport Controls: Shuffle | Previous | Play/Pause | Next | Repeat
        val ctrlY = 152f.dp
        val activeAccent = if (cfg.adaptiveAlbumTint) dynamicAccentColor else cfg.accentColor
        val tintNormal = Color.argb(alpha, Color.red(cfg.textColor), Color.green(cfg.textColor), Color.blue(cfg.textColor))
        val tintActive = Color.argb(alpha, Color.red(activeAccent), Color.green(activeAccent), Color.blue(activeAccent))

        val shuffleIcon = if (isShuffleEnabled) R.drawable.shuffle_on else R.drawable.shuffle
        drawCachedDrawable(canvas, shuffleIcon, 46f.dp, ctrlY, 21f, if (isShuffleEnabled) tintActive else tintNormal)

        drawCachedDrawable(canvas, R.drawable.skip_previous, w * 0.31f, ctrlY, 32f, tintNormal)

        playBgPaint.alpha = (alpha * 0.16f).toInt()
        val playCx = w * 0.50f
        canvas.drawCircle(playCx, ctrlY, 23f.dp, playBgPaint)
        drawCachedDrawable(
            canvas,
            if (isPlaying) R.drawable.pause else R.drawable.play,
            playCx,
            ctrlY,
            26f,
            tintNormal,
        )

        drawCachedDrawable(canvas, R.drawable.skip_next, w * 0.69f, ctrlY, 32f, tintNormal)

        val repeatIcon = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> R.drawable.repeat_one_on
            Player.REPEAT_MODE_ALL -> R.drawable.repeat_on
            else -> R.drawable.repeat
        }
        drawCachedDrawable(
            canvas,
            repeatIcon,
            w - 46f.dp,
            ctrlY,
            21f,
            if (repeatMode != Player.REPEAT_MODE_OFF) tintActive else tintNormal,
        )
    }

    private fun drawShaderArtwork(
        canvas: Canvas,
        rect: RectF,
        corner: Float,
        rotate: Boolean,
        alpha: Int,
    ) {
        val shader = artworkShader
        val bmp = artworkBitmap
        val cp = canvas.save()
        if (rotate && isPlaying && cfg.rotatingArtwork) {
            canvas.rotate(rotationAngle, rect.centerX(), rect.centerY())
        }
        if (shader == null || bmp == null) {
            artworkFallbackPaint.alpha = alpha
            canvas.drawRoundRect(rect, corner, corner, artworkFallbackPaint)
            drawCachedDrawable(canvas, R.drawable.ic_itune_waves, rect.centerX(), rect.centerY(), (rect.width() / density) * 0.56f, Color.argb(alpha, 255, 255, 255))
        } else {
            shaderMatrix.reset()
            val sx = rect.width() / bmp.width.coerceAtLeast(1)
            val sy = rect.height() / bmp.height.coerceAtLeast(1)
            val s = maxOf(sx, sy)
            val dx = rect.left + (rect.width() - bmp.width * s) / 2f
            val dy = rect.top + (rect.height() - bmp.height * s) / 2f
            shaderMatrix.setScale(s, s)
            shaderMatrix.postTranslate(dx, dy)
            shader.setLocalMatrix(shaderMatrix)
            artPaint.shader = shader
            artPaint.alpha = alpha
            canvas.drawRoundRect(rect, corner, corner, artPaint)
        }
        canvas.restoreToCount(cp)
    }

    private fun drawVisualizerBars(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        compact: Boolean,
        alpha: Int,
    ) {
        val bars = if (compact) floatArrayOf(6f, 12f, 17f, 10f, 14f) else floatArrayOf(8f, 15f, 21f, 12f, 17f)
        val availH = if (compact) (islandBounds.height() - 10f.dp).coerceAtLeast(8f.dp) else 22f.dp
        val scale = if (compact) (availH / 20f.dp).coerceIn(0.45f, 1f) else 1f
        val barStroke = if (compact) (2.1f * scale).coerceAtLeast(1.5f).dp else 2.6f.dp
        val gap = if (compact) (3.6f * scale).coerceAtLeast(2.5f).dp else 4.4f.dp
        val phase = SystemClock.uptimeMillis() / 135f
        val startX = cx - gap * 2f

        val baseColor = if (cfg.adaptiveAlbumTint) dynamicAccentColor else cfg.accentColor
        equalizerPaint.color = Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        equalizerPaint.strokeWidth = barStroke

        for (i in bars.indices) {
            val pulse = if (isPlaying) {
                0.45f + 0.55f * abs(sin(phase + i * 1.15f))
            } else {
                0.35f
            }
            val barH = (bars[i] * scale * pulse).dp.coerceAtMost(availH)
            val x = startX + gap * i
            canvas.drawLine(x, cy - barH / 2f, x, cy + barH / 2f, equalizerPaint)
        }
    }

    private fun drawCachedDrawable(
        canvas: Canvas,
        @DrawableRes resId: Int,
        cx: Float,
        cy: Float,
        sizeDp: Float,
        tintColor: Int,
    ) {
        var d = drawableCache.get(resId)
        if (d == null) {
            d = ContextCompat.getDrawable(context, resId)?.mutate() ?: return
            drawableCache.put(resId, d)
        }
        val sizePx = (sizeDp * density).roundToInt()
        val half = sizePx / 2
        d.setBounds(cx.roundToInt() - half, cy.roundToInt() - half, cx.roundToInt() + half, cy.roundToInt() + half)
        d.setTint(tintColor)
        d.draw(canvas)
    }

    private fun updateBounds() {
        val colW = collapsedWidthDp.toFloat().dp.coerceAtMost(width.toFloat().coerceAtLeast(1f))
        val colH = collapsedHeightDp.toFloat().dp.coerceAtLeast(1f)
        if (!expanded && !morphAnimating) {
            // Draw crisp collapsed pill at top even when window extends downward as a status-bar touch cushion
            val left = ((width - colW) / 2f).coerceAtLeast(0f)
            islandBounds.set(left, 0f, left + colW, colH)
            return
        }
        val topOff = offsetYDp.toFloat().dp
        val cxOff = (if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) cfg.landscapeOffsetX else cfg.portraitOffsetX).toFloat().dp

        collapsedBounds.set(
            (width - colW) / 2f + cxOff,
            topOff,
            (width + colW) / 2f + cxOff,
            topOff + colH,
        )

        val expW = (width - 28f.dp).coerceAtMost(386f.dp)
        val expLeft = (width - expW) / 2f
        val minStatusClearTop = if (statusBarClearancePx > 0) (statusBarClearancePx + 2f.dp).coerceIn(10f.dp, 72f.dp) else 10f.dp
        val expTop = maxOf(offsetYDp.coerceIn(6, 120).toFloat().dp, minStatusClearTop) + dragSwipeY
        expandedBounds.set(expLeft, expTop, expLeft + expW, expTop + 190f.dp)

        // Cloth-like dual-axis fluid stretch
        val raw = morphProgress.coerceIn(0f, 1.15f)
        val elasticity = cfg.bubbleElasticity.coerceIn(0.6f, 1.4f)
        val pY = (raw + sin(raw.coerceIn(0f, 1f) * PI).toFloat() * 0.055f * elasticity)
        val pX = ((raw - 0.04f) / 0.96f).coerceIn(0f, 1.12f)

        val curW = lerpUnclamped(collapsedBounds.width(), expandedBounds.width(), pX).coerceAtLeast(colW * 0.85f)
        val curH = lerpUnclamped(collapsedBounds.height(), expandedBounds.height(), pY).coerceAtLeast(colH * 0.85f)
        val curCx = lerpUnclamped(collapsedBounds.centerX(), expandedBounds.centerX(), pX)
        val curTop = lerpUnclamped(collapsedBounds.top, expandedBounds.top, pY)

        islandBounds.set(curCx - curW / 2f, curTop, curCx + curW / 2f, curTop + curH)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                isTapCandidate = true
                dragSwipeY = 0f

                if (expanded && !morphAnimating) {
                    onInteraction()
                    val lx = event.x - islandBounds.left
                    val ly = event.y - islandBounds.top
                    val pStart = 24f.dp
                    val pEnd = islandBounds.width() - 24f.dp
                    if (ly in 78f.dp..118f.dp && lx in (pStart - 16f.dp)..(pEnd + 16f.dp)) {
                        isDraggingSeekbar = true
                        val frac = ((lx - pStart) / (pEnd - pStart).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        scrubPosMs = (frac * durationMs).toLong()
                        invalidate()
                        return true
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (isDraggingSeekbar && expanded) {
                    val pStart = 24f.dp
                    val pEnd = islandBounds.width() - 24f.dp
                    val lx = event.x - islandBounds.left
                    val frac = ((lx - pStart) / (pEnd - pStart).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    scrubPosMs = (frac * durationMs).toLong()
                    invalidate()
                    return true
                }
                if (expanded && dy < -14f.dp && abs(dy) > abs(dx)) {
                    isTapCandidate = false
                    dragSwipeY = (dy * 0.45f).coerceAtLeast(-42f.dp)
                    invalidate()
                    return true
                }
                if (abs(dx) > 16f.dp || abs(dy) > 16f.dp) {
                    isTapCandidate = false
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY

                if (isDraggingSeekbar && expanded) {
                    isDraggingSeekbar = false
                    positionMs = scrubPosMs
                    onSeekTo(scrubPosMs)
                    invalidate()
                    return true
                }

                // Swipe-up collapses expanded water-bubble with fluid snap
                if (expanded && dy < -32f.dp && abs(dy) > abs(dx)) {
                    dragSwipeY = 0f
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    collapseWithWaterDrop()
                    return true
                }
                dragSwipeY = 0f

                // Swipe left / right on collapsed pill skips track
                if (!expanded && abs(dx) > 36f.dp && abs(dx) > abs(dy)) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (dx < 0f) onNext() else onPrevious()
                    return true
                }

                if (!isTapCandidate) {
                    invalidate()
                    return true
                }

                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                if (!expanded) {
                    if (cfg.tapOpensApp) onOpenApp() else expandWithWaterDrop()
                    return true
                }

                if (!islandBounds.contains(event.x, event.y)) {
                    collapseWithWaterDrop()
                    return true
                }

                onInteraction()
                val lx = event.x - islandBounds.left
                val ly = event.y - islandBounds.top
                val w = islandBounds.width()

                when {
                    // Header right heart / star button
                    ly in 20f.dp..78f.dp && lx in (w - 80f.dp)..(w - 36f.dp) -> onToggleFavorite()
                    // Header art/title opens iTune directly
                    ly in 20f.dp..78f.dp && lx < w - 82f.dp -> onOpenApp()
                    // Transport row
                    ly in 122f.dp..182f.dp -> {
                        when {
                            abs(lx - 46f.dp) < 28f.dp -> onShuffle()
                            abs(lx - (w - 46f.dp)) < 28f.dp -> onRepeat()
                            lx in (w * 0.20f)..(w * 0.40f) -> onPrevious()
                            lx in (w * 0.41f)..(w * 0.59f) -> onPlayPause()
                            lx in (w * 0.60f)..(w * 0.80f) -> onNext()
                        }
                    }
                    // Tap outside active controls near bottom edge collapses cleanly
                    ly > islandBounds.height() - 12f.dp -> collapseWithWaterDrop()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isTapCandidate = false
                isDraggingSeekbar = false
                dragSwipeY = 0f
                invalidate()
                return true
            }
        }
        return true
    }

    private fun expandWithWaterDrop() {
        if (expanded && morphProgress >= 1f) return
        expanded = true
        onExpandedChanged(true)
        if (cfg.waterDropEffect) triggerRippleWave()
        val duration = (420f / cfg.bubbleElasticity.coerceIn(0.6f, 1.4f)).toLong().coerceIn(280L, 520L)
        val overshoot = if (cfg.waterDropEffect) 0.055f * cfg.bubbleElasticity else 0f
        startMorphAnimator(
            from = morphProgress,
            to = 1f,
            durationMs = duration,
            interpolator = if (overshoot > 0f) {
                android.view.animation.OvershootInterpolator(0.85f * cfg.bubbleElasticity)
            } else {
                clothFastOutSlowIn
            },
        ) {
            morphProgress = 1f
            morphAnimating = false
            invalidate()
        }
    }

    private fun collapseWithWaterDrop() {
        if (cfg.waterDropEffect) triggerRippleWave()
        val duration = (280f / cfg.bubbleElasticity.coerceIn(0.6f, 1.4f)).toLong().coerceIn(200L, 380L)
        startMorphAnimator(
            from = morphProgress.coerceAtLeast(0.01f),
            to = 0f,
            durationMs = duration,
            interpolator = clothCollapseCurve,
        ) {
            morphProgress = 0f
            morphAnimating = false
            expanded = false
            onExpandedChanged(false)
            invalidate()
        }
    }

    private fun triggerRippleWave() {
        rippleAnimator?.cancel()
        rippleAnimating = true
        rippleProgress = 0f
        scheduleVsync()
        rippleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 620L
            interpolator = rippleOutCurve
            addUpdateListener {
                rippleProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    rippleAnimating = false
                    rippleProgress = 0f
                    invalidate()
                }
            })
            start()
        }
    }

    private fun startMorphAnimator(
        from: Float,
        to: Float,
        durationMs: Long,
        interpolator: android.animation.TimeInterpolator,
        onFinish: () -> Unit,
    ) {
        morphAnimator?.cancel()
        morphAnimating = true
        scheduleVsync()
        morphAnimator = ValueAnimator.ofFloat(from, to).apply {
            duration = durationMs
            this.interpolator = interpolator
            addUpdateListener {
                morphProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onFinish()
                }
            })
            start()
        }
    }

    private fun formatMs(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        return "%d:%02d".format(total / 60L, total % 60L)
    }

    private fun ellipsize(s: String, maxChars: Int): String =
        if (s.length <= maxChars) s else s.take((maxChars - 1).coerceAtLeast(1)) + "…"

    private fun lerp(a: Float, b: Float, f: Float): Float =
        a + (b - a) * f.coerceIn(0f, 1f)

    private fun lerpUnclamped(a: Float, b: Float, f: Float): Float =
        a + (b - a) * f
}
