package com.darkxvenom.itune.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.PowerManager
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.runtime.snapshotFlow
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.Player
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.darkxvenom.itune.MainActivity
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.backend.PlaybackSessionStore
import com.darkxvenom.itune.backend.highQualityThumbnail
import com.darkxvenom.itune.radio.OnlineRadioController
import com.darkxvenom.itune.radio.OnlineRadioStation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Draws the home-screen widget and handles its buttons.
 *
 * - A single observer (started with the player, or when a widget is added) watches the real
 *   player state and pushes a full update whenever the song, play state, shuffle, repeat or
 *   favourite changes.
 * - While music plays and the screen is on, only the progress bar and times are refreshed once a
 *   second through a partial update, which keeps the widget cheap.
 * - Button taps arrive as broadcasts. If the app process was gone, the player is initialised first,
 *   which restores the last session, so Play resumes where you stopped.
 * - Online radio is supported the same way the notification supports it.
 */
object ITuneWidgetUpdater {

    private const val ACTION_PREFIX = "com.darkxvenom.itune.widget.action."
    const val ACTION_TOGGLE = ACTION_PREFIX + "TOGGLE"
    const val ACTION_NEXT = ACTION_PREFIX + "NEXT"
    const val ACTION_PREVIOUS = ACTION_PREFIX + "PREVIOUS"
    const val ACTION_SHUFFLE = ACTION_PREFIX + "SHUFFLE"
    const val ACTION_REPEAT = ACTION_PREFIX + "REPEAT"
    const val ACTION_FAVORITE = ACTION_PREFIX + "FAVORITE"

    val CONTROL_ACTIONS = setOf(
        ACTION_TOGGLE, ACTION_NEXT, ACTION_PREVIOUS, ACTION_SHUFFLE, ACTION_REPEAT, ACTION_FAVORITE,
    )

    private const val ART_PX = 320
    private const val PROGRESS_MAX = 1000

    private data class Snap(
        val track: MusicTrack?,
        val playing: Boolean,
        val shuffle: Boolean,
        val repeat: Int,
        val favorite: Boolean,
        val durationMs: Long,
        val station: OnlineRadioStation?,
        val radioPlaying: Boolean,
    ) {
        val radioMode: Boolean get() = radioPlaying || (track == null && station != null)
    }

    private data class Display(
        val title: String,
        val subtitle: String,
        val artUrl: String?,
        val playing: Boolean,
        val radio: Boolean,
        val hasContent: Boolean,
        val shuffle: Boolean,
        val repeat: Int,
        val favorite: Boolean,
        val positionMs: Long,
        val durationMs: Long,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var appContext: Context? = null
    private var observing = false
    private var lastSnap: Snap? = null

    private var artUrl: String? = null
    private var artBitmap: Bitmap? = null
    private var blurredArtBitmap: Bitmap? = null
    private var loadingUrl: String? = null
    private var artJob: Job? = null

    // ── Observation ──

    fun ensureObserving(context: Context) {
        if (observing) return
        observing = true
        val app = context.applicationContext
        appContext = app

        scope.launch {
            val musicFlow = snapshotFlow {
                val track = MusicPlayerState.currentTrack
                Snap(
                    track = track,
                    playing = MusicPlayerState.isPlaying,
                    shuffle = MusicPlayerState.shuffleEnabled,
                    repeat = MusicPlayerState.repeatMode,
                    favorite = track != null && MusicPlayerState.isFavorite(track.id),
                    durationMs = MusicPlayerState.durationMs,
                    station = null,
                    radioPlaying = false,
                )
            }
            val radioFlow = OnlineRadioController.state
                .map { it.currentStation to it.isPlaying }
                .distinctUntilChanged()
            combine(musicFlow, radioFlow) { music, radio ->
                music.copy(station = radio.first, radioPlaying = radio.second)
            }
                .distinctUntilChanged()
                .collect { pushFull(it) }
        }

        scope.launch {
            while (isActive) {
                delay(1_000L)
                pushProgressOnly()
            }
        }
    }

    /** Called by the provider (widget added, resized, boot) to redraw with the current state. */
    fun refreshAll(context: Context) {
        ensureObserving(context)
        pushFull(currentSnap())
    }

    private fun currentSnap(): Snap {
        val radio = OnlineRadioController.state.value
        val track = MusicPlayerState.currentTrack
        return Snap(
            track = track,
            playing = MusicPlayerState.isPlaying,
            shuffle = MusicPlayerState.shuffleEnabled,
            repeat = MusicPlayerState.repeatMode,
            favorite = track != null && MusicPlayerState.isFavorite(track.id),
            durationMs = MusicPlayerState.durationMs,
            station = radio.currentStation,
            radioPlaying = radio.isPlaying,
        )
    }

    // ── Button handling ──

    fun handleControl(context: Context, action: String, onDone: () -> Unit) {
        val app = context.applicationContext
        ensureObserving(app)
        scope.launch {
            try {
                // Idempotent. After a cold start this also restores the last played song.
                MusicPlayerState.init(app)
                val radioState = OnlineRadioController.state.value
                val radio = radioState.isPlaying ||
                    (MusicPlayerState.currentTrack == null && radioState.currentStation != null)
                when (action) {
                    ACTION_TOGGLE ->
                        if (radio) OnlineRadioController.togglePlayPause() else MusicPlayerState.togglePlayPause()
                    ACTION_NEXT ->
                        if (radio) OnlineRadioController.skipNextStation() else MusicPlayerState.skipNext()
                    ACTION_PREVIOUS ->
                        if (radio) OnlineRadioController.skipPreviousStation() else MusicPlayerState.skipPrevious()
                    ACTION_SHUFFLE -> if (!radio) MusicPlayerState.toggleShuffle()
                    ACTION_REPEAT -> if (!radio) MusicPlayerState.toggleRepeat()
                    ACTION_FAVORITE ->
                        if (!radio) MusicPlayerState.currentTrack?.let { MusicPlayerState.toggleFavorite(it) }
                }
                pushFull(currentSnap())
                delay(3_500L)
            } finally {
                onDone()
            }
        }
    }

    // ── Drawing ──

    private fun widgetIds(ctx: Context): IntArray =
        AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, ITuneWidgetProvider::class.java))

    private fun pushFull(snap: Snap) {
        val ctx = appContext ?: return
        lastSnap = snap
        val ids = widgetIds(ctx)
        if (ids.isEmpty()) return

        val display = display(ctx, snap)
        val wantedArt = display.artUrl
        if (!wantedArt.isNullOrBlank() && wantedArt != artUrl && wantedArt != loadingUrl) {
            loadArtwork(ctx, wantedArt)
        }

        val views = RemoteViews(ctx.packageName, R.layout.widget_itune_player)
        val open = openAppIntent(ctx)

        val widgetStyle = WidgetPreferences.style(ctx)
        val widgetSize = WidgetPreferences.size(ctx)
        val compact = widgetSize == WidgetPreferences.SIZE_COMPACT
        val blurArtworkEnabled = WidgetPreferences.blurArtworkBackground(ctx)
        views.setInt(
            R.id.widget_root,
            "setBackgroundResource",
            when {
                blurArtworkEnabled -> R.drawable.widget_itune_bg_dark
                widgetStyle == WidgetPreferences.STYLE_DARK -> R.drawable.widget_itune_bg_dark
                widgetStyle == WidgetPreferences.STYLE_LIGHT -> R.drawable.widget_itune_bg_light
                else -> R.drawable.widget_itune_bg
            },
        )
        val textColor = if (widgetStyle == WidgetPreferences.STYLE_LIGHT) Color.BLACK else Color.WHITE
        val secondaryTextColor = if (widgetStyle == WidgetPreferences.STYLE_LIGHT) 0x99000000.toInt() else 0xB3FFFFFF.toInt()
        views.setTextColor(R.id.widget_title, textColor)
        views.setTextColor(R.id.widget_artist, secondaryTextColor)
        views.setTextColor(R.id.widget_time_elapsed, secondaryTextColor)
        views.setTextColor(R.id.widget_time_total, secondaryTextColor)
        when (widgetSize) {
            WidgetPreferences.SIZE_COMPACT -> {
                views.setViewPadding(R.id.widget_root, dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
                resizeView(views, R.id.widget_art_frame, 66f, 56f)
                resizeView(views, R.id.widget_art, 56f, 56f)
                resizeView(views, R.id.widget_vinyl, 56f, 56f)
                resizeView(views, R.id.widget_controls, -1f, 34f)
                views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, 14f)
                views.setTextViewTextSize(R.id.widget_artist, TypedValue.COMPLEX_UNIT_SP, 11f)
            }
            WidgetPreferences.SIZE_EXPANDED -> {
                views.setViewPadding(R.id.widget_root, dp(ctx, 18), dp(ctx, 14), dp(ctx, 18), dp(ctx, 12))
                resizeView(views, R.id.widget_art_frame, 116f, 92f)
                resizeView(views, R.id.widget_art, 92f, 92f)
                resizeView(views, R.id.widget_vinyl, 92f, 92f)
                resizeView(views, R.id.widget_controls, -1f, 48f)
                views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, 17f)
                views.setTextViewTextSize(R.id.widget_artist, TypedValue.COMPLEX_UNIT_SP, 13f)
            }
            else -> {
                views.setViewPadding(R.id.widget_root, dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 6))
                resizeView(views, R.id.widget_art_frame, 96f, 72f)
                resizeView(views, R.id.widget_art, 72f, 72f)
                resizeView(views, R.id.widget_vinyl, 72f, 72f)
                resizeView(views, R.id.widget_controls, -1f, 40f)
                views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, 15f)
                views.setTextViewTextSize(R.id.widget_artist, TypedValue.COMPLEX_UNIT_SP, 12f)
            }
        }

        views.setTextViewText(R.id.widget_title, display.title)
        views.setTextViewText(R.id.widget_artist, display.subtitle)

        if (!WidgetPreferences.showArtwork(ctx)) {
            views.setViewVisibility(R.id.widget_art, View.INVISIBLE)
            views.setViewVisibility(R.id.widget_vinyl, View.INVISIBLE)
        } else if (!wantedArt.isNullOrBlank() && wantedArt == artUrl && artBitmap != null) {
            views.setImageViewBitmap(R.id.widget_art, artBitmap)
            views.setViewVisibility(R.id.widget_art, View.VISIBLE)
            views.setViewVisibility(R.id.widget_vinyl, View.VISIBLE)
        } else {
            views.setImageViewResource(R.id.widget_art, R.drawable.widget_art_placeholder)
            views.setViewVisibility(R.id.widget_art, View.VISIBLE)
            views.setViewVisibility(R.id.widget_vinyl, View.VISIBLE)
        }
        val currentBlurredArt = blurredArtBitmap
        val blurredBackgroundEnabled = blurArtworkEnabled && currentBlurredArt != null
        if (currentBlurredArt != null && blurredBackgroundEnabled) {
            views.setImageViewBitmap(R.id.widget_blurred_artwork_background, currentBlurredArt)
            views.setViewVisibility(R.id.widget_blurred_artwork_background, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_blurred_artwork_background, View.GONE)
        }
        views.setViewVisibility(R.id.widget_blur_scrim, if (blurredBackgroundEnabled) View.VISIBLE else View.GONE)

        views.setImageViewResource(R.id.widget_play, if (display.playing) R.drawable.pause else R.drawable.play)
        views.setImageViewResource(R.id.widget_shuffle, if (display.shuffle) R.drawable.shuffle_on else R.drawable.shuffle)
        views.setImageViewResource(
            R.id.widget_repeat,
            when (display.repeat) {
                Player.REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                Player.REPEAT_MODE_ALL -> R.drawable.repeat_on
                else -> R.drawable.repeat
            },
        )
        views.setImageViewResource(
            R.id.widget_favorite,
            if (display.favorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline,
        )

        // Radio has no queue modes, favourites or timeline.
        val musicOnly = if (display.radio) View.INVISIBLE else View.VISIBLE
        views.setViewVisibility(R.id.widget_shuffle, musicOnly)
        views.setViewVisibility(R.id.widget_repeat, musicOnly)
        views.setViewVisibility(
            R.id.widget_favorite,
            if (!WidgetPreferences.showFavorite(ctx) || display.radio || !display.hasContent) View.GONE else View.VISIBLE,
        )
        val compactHidden = if (compact) View.GONE else musicOnly
        views.setViewVisibility(R.id.widget_progress, compactHidden)
        views.setViewVisibility(R.id.widget_time_elapsed, compactHidden)
        views.setViewVisibility(R.id.widget_time_total, compactHidden)
        val controlsVisibility = if (WidgetPreferences.showControls(ctx)) View.VISIBLE else View.GONE
        listOf(R.id.widget_shuffle, R.id.widget_prev, R.id.widget_play, R.id.widget_next, R.id.widget_repeat)
            .forEach { views.setViewVisibility(it, controlsVisibility) }
        if (compact) {
            views.setViewVisibility(R.id.widget_shuffle, View.GONE)
            views.setViewVisibility(R.id.widget_repeat, View.GONE)
        }
        applyProgress(views, display)

        // With nothing to control yet, every button simply opens the app.
        fun control(viewId: Int, action: String, code: Int) {
            views.setOnClickPendingIntent(viewId, if (display.hasContent) controlIntent(ctx, action, code) else open)
        }
        control(R.id.widget_play, ACTION_TOGGLE, 1)
        control(R.id.widget_next, ACTION_NEXT, 2)
        control(R.id.widget_prev, ACTION_PREVIOUS, 3)
        control(R.id.widget_shuffle, ACTION_SHUFFLE, 4)
        control(R.id.widget_repeat, ACTION_REPEAT, 5)
        control(R.id.widget_favorite, ACTION_FAVORITE, 6)
        views.setOnClickPendingIntent(R.id.widget_root, open)
        views.setOnClickPendingIntent(R.id.widget_art, open)
        views.setOnClickPendingIntent(R.id.widget_title, open)
        views.setOnClickPendingIntent(R.id.widget_artist, open)

        AppWidgetManager.getInstance(ctx).updateAppWidget(ids, views)
    }

    private fun pushProgressOnly() {
        val ctx = appContext ?: return
        val snap = lastSnap ?: return
        if (!snap.playing || snap.radioMode) return
        val power = ctx.getSystemService(PowerManager::class.java)
        if (power != null && !power.isInteractive) return
        val ids = widgetIds(ctx)
        if (ids.isEmpty()) return
        val views = RemoteViews(ctx.packageName, R.layout.widget_itune_player)
        applyProgress(views, display(ctx, snap))
        AppWidgetManager.getInstance(ctx).partiallyUpdateAppWidget(ids, views)
    }

    private fun applyProgress(views: RemoteViews, display: Display) {
        val duration = display.durationMs
        val position = display.positionMs.coerceIn(0L, if (duration > 0L) duration else Long.MAX_VALUE)
        val progress = if (duration > 0L) ((position * PROGRESS_MAX) / duration).toInt() else 0
        views.setProgressBar(R.id.widget_progress, PROGRESS_MAX, progress.coerceIn(0, PROGRESS_MAX), false)
        views.setTextViewText(R.id.widget_time_elapsed, formatTime(position))
        views.setTextViewText(R.id.widget_time_total, formatTime(duration))
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun resizeView(views: RemoteViews, viewId: Int, widthDp: Float, heightDp: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (widthDp >= 0f) views.setViewLayoutWidth(viewId, widthDp, TypedValue.COMPLEX_UNIT_DIP)
            if (heightDp >= 0f) views.setViewLayoutHeight(viewId, heightDp, TypedValue.COMPLEX_UNIT_DIP)
        }
    }

    private fun display(ctx: Context, snap: Snap): Display {
        if (snap.radioMode) {
            val station = snap.station
            return Display(
                title = station?.name ?: "iTune Radio",
                subtitle = "Online Radio",
                artUrl = station?.faviconUrl,
                playing = snap.radioPlaying,
                radio = true,
                hasContent = true,
                shuffle = false,
                repeat = Player.REPEAT_MODE_OFF,
                favorite = false,
                positionMs = 0L,
                durationMs = 0L,
            )
        }
        val track = snap.track
        if (track != null) {
            return Display(
                title = track.title,
                subtitle = track.artists,
                artUrl = track.thumbnailUrl?.highQualityThumbnail() ?: track.thumbnailUrl,
                playing = snap.playing,
                radio = false,
                hasContent = true,
                shuffle = snap.shuffle,
                repeat = snap.repeat,
                favorite = snap.favorite,
                positionMs = MusicPlayerState.positionMs,
                durationMs = durationOf(track, snap.durationMs),
            )
        }
        // Nothing loaded in this process yet: show the last session so the widget is never empty.
        val saved = runCatching { PlaybackSessionStore.load(ctx) }.getOrNull()
        if (saved != null) {
            val savedTrack = saved.track
            return Display(
                title = savedTrack.title,
                subtitle = savedTrack.artists,
                artUrl = savedTrack.thumbnailUrl?.highQualityThumbnail() ?: savedTrack.thumbnailUrl,
                playing = false,
                radio = false,
                hasContent = true,
                shuffle = saved.shuffleEnabled,
                repeat = saved.repeatMode,
                favorite = MusicPlayerState.isFavorite(savedTrack.id),
                positionMs = saved.positionMs,
                durationMs = durationOf(savedTrack, saved.durationMs),
            )
        }
        return Display(
            title = "iTune",
            subtitle = "Tap to start listening",
            artUrl = null,
            playing = false,
            radio = false,
            hasContent = false,
            shuffle = false,
            repeat = Player.REPEAT_MODE_OFF,
            favorite = false,
            positionMs = 0L,
            durationMs = 0L,
        )
    }

    private fun durationOf(track: MusicTrack, rawMs: Long): Long = when {
        rawMs > 0L -> rawMs
        track.durationSec > 0L -> track.durationSec * 1000L
        else -> 0L
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "$minutes:${seconds.toString().padStart(2, '0')}"
    }

    // ── Artwork ──

    private fun loadArtwork(ctx: Context, url: String) {
        artJob?.cancel()
        loadingUrl = url
        artJob = scope.launch {
            val result = runCatching {
                Coil.imageLoader(ctx).execute(
                    ImageRequest.Builder(ctx)
                        .data(url)
                        .allowHardware(false)
                        .size(ART_PX)
                        .build(),
                )
            }.getOrNull()
            loadingUrl = null
            if (result is SuccessResult) {
                val source = (result.drawable as? BitmapDrawable)?.bitmap ?: result.drawable.toBitmap()
                artBitmap = roundedSquare(source, ART_PX, ART_PX * 0.2f)
                blurredArtBitmap = makeSoftBackdrop(source)
                artUrl = url
                lastSnap?.let { pushFull(it) }
            }
        }
    }

    /** Center-crops [source] to a square with rounded corners (RemoteViews cannot clip images). */
    private fun roundedSquare(source: Bitmap, size: Int, radius: Float): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val side = min(source.width, source.height)
        val left = (source.width - side) / 2
        val top = (source.height - side) / 2
        canvas.drawBitmap(source, Rect(left, top, left + side, top + side), Rect(0, 0, size, size), paint)
        return out
    }

    /** Low-resolution resampling creates a lightweight soft cover backdrop suitable for RemoteViews. */
    private fun makeSoftBackdrop(source: Bitmap): Bitmap {
        val tiny = Bitmap.createScaledBitmap(source, 42, 42, true)
        val soft = Bitmap.createBitmap(ART_PX, ART_PX, Bitmap.Config.ARGB_8888)
        Canvas(soft).apply {
            drawColor(Color.BLACK)
            drawBitmap(tiny, null, Rect(0, 0, ART_PX, ART_PX), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        if (tiny !== source && tiny !== soft) tiny.recycle()
        return soft
    }

    // ── Pending intents ──

    private fun controlIntent(ctx: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            code,
            Intent(ctx, ITuneWidgetProvider::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openAppIntent(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx,
            9301,
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
