package com.darkxvenom.itune.recognition

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.Coil
import coil.request.ImageRequest
import com.darkxvenom.itune.MainActivity
import com.darkxvenom.itune.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AirBeats-matching floating bottom recognition overlay.
 * Displays directly on top of the screen / over any running application.
 */
object MusicRecognitionOverlayManager {

    private const val TAG = "RecognitionOverlay"

    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null
    private var isShowing = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var recognitionJob: Job? = null
    private var animators: MutableList<ValueAnimator> = mutableListOf()

    private var currentResult: IRecognitionResult? = null
    private val autoDismissRunnable = Runnable { dismiss() }

    @SuppressLint("ClickableViewAccessibility")
    fun show(context: Context) {
        mainHandler.post {
            if (!Settings.canDrawOverlays(context)) {
                Log.w(TAG, "Cannot draw overlay: permission not granted")
                return@post
            }

            if (isShowing && overlayView != null) {
                // Already showing: restart recognition
                startRecognition(context)
                return@post
            }

            dismiss() // ensure any leftover view is removed

            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager = wm

            val dm = context.resources.displayMetrics
            fun dp(value: Float): Int = (value * dm.density).toInt()

            val maxWidth = dp(380f)
            val availableWidth = dm.widthPixels - dp(32f)
            val pillWidth = minOf(maxWidth, availableWidth)

            // Root Pill Container
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                elevation = dp(16f).toFloat()
                clipToPadding = false

                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(24f).toFloat()
                    setColor(Color.parseColor("#14131E"))
                    setStroke(dp(1f), Color.parseColor("#2C2B3E"))
                }
                background = bg
                setPadding(dp(14f), dp(10f), dp(12f), dp(10f))
            }

            // Left Section: Waveform visualizer container / Artwork
            val visualizerLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(36f), dp(36f))
            }

            val barViews = mutableListOf<View>()
            val barHeights = listOf(14f, 22f, 28f, 18f)
            animators.clear()

            for (h in barHeights) {
                val bar = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(4f), dp(h)).apply {
                        marginStart = dp(2f)
                        marginEnd = dp(2f)
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(2f).toFloat()
                        setColor(Color.parseColor("#8364FF"))
                    }
                }
                barViews.add(bar)
                visualizerLayout.addView(bar)

                // Pulse animator
                val anim = ValueAnimator.ofFloat(0.3f, 1.0f).apply {
                    duration = 350L + (Math.random() * 250).toLong()
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener { va ->
                        val scale = va.animatedValue as Float
                        bar.scaleY = scale
                    }
                }
                animators.add(anim)
                anim.start()
            }

            val artworkView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(36f), dp(36f))
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, dp(8f).toFloat())
                    }
                }
                visibility = View.GONE
            }

            val leftContainer = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(36f), dp(36f))
                addView(visualizerLayout)
                addView(artworkView)
            }
            root.addView(leftContainer)

            // Center Section: Song Title & Status / Artist
            val textLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                    marginStart = dp(12f)
                    marginEnd = dp(8f)
                }
            }

            val titleText = TextView(context).apply {
                text = "Listening for music…"
                setTextColor(Color.WHITE)
                textSize = 14f
                paint.isFakeBoldText = true
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.MARQUEE
                marqueeRepeatLimit = -1
                isSelected = true
                isFocusable = true
                setHorizontallyScrolling(true)
            }

            val subtitleText = TextView(context).apply {
                text = "Identifying with Shazam…"
                setTextColor(Color.parseColor("#A0A0B2"))
                textSize = 12f
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.MARQUEE
                marqueeRepeatLimit = -1
                isSelected = true
                isFocusable = true
                setHorizontallyScrolling(true)
                setPadding(0, dp(2f), 0, 0)
            }

            textLayout.addView(titleText)
            textLayout.addView(subtitleText)
            root.addView(textLayout)

            // Right Section: Play Button (hidden initially)
            val playButton = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(36f), dp(36f)).apply {
                    marginEnd = dp(6f)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#FC3C44")) // Apple Music Red
                }
                visibility = View.GONE

                val playIcon = ImageView(context).apply {
                    setImageResource(R.drawable.play)
                    setColorFilter(Color.WHITE)
                    layoutParams = FrameLayout.LayoutParams(dp(18f), dp(18f), Gravity.CENTER)
                }
                addView(playIcon)
            }
            root.addView(playButton)

            // Right Section: Close Button
            val closeButton = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(32f), dp(32f))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#252433"))
                }

                val closeIcon = ImageView(context).apply {
                    setImageResource(R.drawable.close)
                    setColorFilter(Color.parseColor("#D0D0E0"))
                    layoutParams = FrameLayout.LayoutParams(dp(14f), dp(14f), Gravity.CENTER)
                }
                addView(closeIcon)
                setOnClickListener { dismiss() }
            }
            root.addView(closeButton)

            // Window LayoutParams
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                pillWidth,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = dp(24f)
                windowAnimations = android.R.style.Animation_Dialog
            }

            try {
                wm.addView(root, params)
                overlayView = root
                isShowing = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add overlay view", e)
                return@post
            }

            // Click on center or play button plays song
            val playAction = View.OnClickListener {
                currentResult?.let { res ->
                    playRecognizedSongInITune(context, res)
                    dismiss()
                } ?: run {
                    // Retry if currently in error state
                    if (subtitleText.text.contains("retry", ignoreCase = true)) {
                        startRecognition(context)
                    }
                }
            }

            textLayout.setOnClickListener(playAction)
            playButton.setOnClickListener(playAction)

            startRecognition(context)
        }
    }

    private fun startRecognition(context: Context) {
        val root = overlayView ?: return
        mainHandler.removeCallbacks(autoDismissRunnable)

        val leftContainer = root.getChildAt(0) as? FrameLayout
        val visualizer = leftContainer?.getChildAt(0) as? LinearLayout
        val artwork = leftContainer?.getChildAt(1) as? ImageView
        val textLayout = root.getChildAt(1) as? LinearLayout
        val titleText = textLayout?.getChildAt(0) as? TextView
        val subtitleText = textLayout?.getChildAt(1) as? TextView
        val playButton = root.getChildAt(2) as? FrameLayout

        titleText?.text = "Listening for music…"
        subtitleText?.text = "Identifying with Shazam…"
        subtitleText?.setTextColor(Color.parseColor("#A0A0B2"))

        visualizer?.visibility = View.VISIBLE
        artwork?.visibility = View.GONE
        playButton?.visibility = View.GONE
        animators.forEach { if (!it.isStarted) it.start() }

        currentResult = null
        recognitionJob?.cancel()

        recognitionJob = scope.launch {
            try {
                val signature = IRecognitionRepository.captureAndFingerprint(context)
                titleText?.text = "Finding song…"
                subtitleText?.text = "Comparing audio fingerprint…"

                val result = IRecognitionRepository.recognize(signature)
                currentResult = result
                MusicRecognitionController.lastResult.value = result

                // Success!
                animators.forEach { it.cancel() }
                visualizer?.visibility = View.GONE

                titleText?.text = result.title
                subtitleText?.text = result.artist
                subtitleText?.setTextColor(Color.parseColor("#FC3C44")) // Apple Music Red

                // Load artwork if available
                result.artworkUrl?.let { url ->
                    try {
                        val imageLoader = Coil.imageLoader(context)
                        val req = ImageRequest.Builder(context)
                            .data(url)
                            .allowHardware(false)
                            .build()
                        val drawable = withContext(Dispatchers.IO) {
                            imageLoader.execute(req).drawable
                        }
                        drawable?.let {
                            artwork?.setImageDrawable(it)
                            artwork?.visibility = View.VISIBLE
                        }
                    } catch (_: Exception) {}
                }

                playButton?.visibility = View.VISIBLE

                // Haptic feedback on match!
                triggerHapticFeedback(context)

                // Auto dismiss after 10 seconds
                mainHandler.postDelayed(autoDismissRunnable, 10_000L)

            } catch (_: CancellationException) {
                // Cancelled
            } catch (_: RecognitionNoMatchException) {
                animators.forEach { it.cancel() }
                titleText?.text = "No match found"
                subtitleText?.text = "Tap to retry"
                subtitleText?.setTextColor(Color.parseColor("#FC3C44"))
                mainHandler.postDelayed(autoDismissRunnable, 6_000L)
            } catch (e: Exception) {
                animators.forEach { it.cancel() }
                titleText?.text = "Recognition error"
                subtitleText?.text = "Tap to retry (${e.message ?: "error"})"
                subtitleText?.setTextColor(Color.parseColor("#FC3C44"))
                mainHandler.postDelayed(autoDismissRunnable, 6_000L)
            }
        }
    }

    private fun triggerHapticFeedback(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(120)
            }
        } catch (_: Exception) {}
    }

    private fun playRecognizedSongInITune(context: Context, result: IRecognitionResult) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MusicRecognitionTileService.ACTION_PLAY_RECOGNIZED
            putExtra(MusicRecognitionTileService.EXTRA_SONG_TITLE, result.title)
            putExtra(MusicRecognitionTileService.EXTRA_SONG_ARTIST, result.artist)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(intent)
    }

    fun dismiss() {
        mainHandler.removeCallbacks(autoDismissRunnable)
        recognitionJob?.cancel()
        animators.forEach { it.cancel() }
        animators.clear()

        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {}
        }
        overlayView = null
        windowManager = null
        isShowing = false
        currentResult = null
    }
}
