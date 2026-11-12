package com.darkxvenom.itune.ui.timetravel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import coil.Coil
import coil.request.ImageRequest
import com.darkxvenom.itune.data.timetravel.TimeTravelStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object TimeTravelShareManager {

    private const val TAG = "TimeTravelShare"

    suspend fun shareReplay(context: Context, stats: TimeTravelStats, displayName: String) =
        withContext(Dispatchers.IO) {
            try {
                val file = generateReplayImage(context, stats, displayName)
                val uri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Check out my ${stats.period.label} Time Travel Replay on iTune! 🎵"
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                val chooser = Intent.createChooser(intent, "Share Time Travel Replay").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to share replay image", e)
            }
        }

    private suspend fun generateReplayImage(
        context: Context,
        stats: TimeTravelStats,
        displayName: String
    ): File {
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Background Gradient
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(
                    Color.parseColor("#0F0C1B"),
                    Color.parseColor("#1B142D"),
                    Color.parseColor("#090710")
                ),
                floatArrayOf(0f, 0.5f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Subtle ambient radial glow spots
        val glowPaint = Paint().apply {
            shader = RadialGradient(
                width * 0.8f, height * 0.15f, 500f,
                Color.parseColor("#44FC3C44"),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width * 0.8f, height * 0.15f, 500f, glowPaint)

        val glowPaint2 = Paint().apply {
            shader = RadialGradient(
                width * 0.2f, height * 0.55f, 600f,
                Color.parseColor("#337928CA"),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width * 0.2f, height * 0.55f, 600f, glowPaint2)

        // 2. Header Branding
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // Pill badge
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#26FFFFFF")
        }
        val pillRect = RectF(60f, 90f, 320f, 148f)
        canvas.drawRoundRect(pillRect, 29f, 29f, badgePaint)

        textPaint.textSize = 26f
        textPaint.color = Color.parseColor("#FC3C44")
        canvas.drawText("iTune REPLAY", 90f, 128f, textPaint)

        // Title: TIME TRAVEL
        textPaint.color = Color.WHITE
        textPaint.textSize = 68f
        canvas.drawText("Time Travel", 60f, 230f, textPaint)

        // Subtitle: User & Period
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A8A7BC")
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText("$displayName  •  ${stats.period.label}", 60f, 280f, subPaint)

        // 3. Top Metrics Banner (2 Cards)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#181628")
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#33FFFFFF")
        }

        val cardWidth = (width - 120f - 30f) / 2f
        val card1 = RectF(60f, 330f, 60f + cardWidth, 470f)
        val card2 = RectF(60f + cardWidth + 30f, 330f, width - 60f, 470f)

        canvas.drawRoundRect(card1, 28f, 28f, cardPaint)
        canvas.drawRoundRect(card1, 28f, 28f, borderPaint)
        canvas.drawRoundRect(card2, 28f, 28f, cardPaint)
        canvas.drawRoundRect(card2, 28f, 28f, borderPaint)

        // Card 1 Text
        subPaint.textSize = 24f
        subPaint.color = Color.parseColor("#8E8CA6")
        canvas.drawText("LISTENING TIME", card1.left + 32f, card1.top + 50f, subPaint)
        textPaint.textSize = 46f
        textPaint.color = Color.parseColor("#FC3C44")
        canvas.drawText(stats.formattedListeningTime, card1.left + 32f, card1.top + 106f, textPaint)

        // Card 2 Text
        canvas.drawText("TOTAL PLAYS", card2.left + 32f, card2.top + 50f, subPaint)
        textPaint.color = Color.parseColor("#00DF89")
        canvas.drawText("${stats.totalPlays}", card2.left + 32f, card2.top + 106f, textPaint)

        // 4. Hero #1 Track Showcase
        val heroTrack = stats.topSongs.firstOrNull()
        val heroCardRect = RectF(60f, 510f, width - 60f, 750f)
        val heroBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                heroCardRect.left, heroCardRect.top, heroCardRect.right, heroCardRect.bottom,
                Color.parseColor("#281630"),
                Color.parseColor("#171124"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(heroCardRect, 32f, 32f, heroBgPaint)
        canvas.drawRoundRect(heroCardRect, 32f, 32f, borderPaint)

        // Download hero artwork if present
        var heroBitmap: Bitmap? = null
        heroTrack?.artworkUrl?.let { url ->
            try {
                val loader = Coil.imageLoader(context)
                val req = ImageRequest.Builder(context).data(url).allowHardware(false).build()
                val d = loader.execute(req).drawable
                heroBitmap = (d as? BitmapDrawable)?.bitmap
            } catch (_: Exception) {}
        }

        val artSize = 180f
        val artRect = RectF(heroCardRect.left + 30f, heroCardRect.top + 30f, heroCardRect.left + 30f + artSize, heroCardRect.top + 30f + artSize)
        if (heroBitmap != null) {
            val clipPath = Path().apply { addRoundRect(artRect, 20f, 20f, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(clipPath)
            canvas.drawBitmap(heroBitmap!!, null, artRect, null)
            canvas.restore()
        } else {
            val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#33FFFFFF") }
            canvas.drawRoundRect(artRect, 20f, 20f, placeholderPaint)
        }

        // Hero details
        subPaint.textSize = 22f
        subPaint.color = Color.parseColor("#FC3C44")
        subPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("TOP SONG #1", heroCardRect.left + 240f, heroCardRect.top + 70f, subPaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 40f
        val songTitle = heroTrack?.title ?: "No plays yet"
        val clampedTitle = if (songTitle.length > 24) songTitle.take(22) + "…" else songTitle
        canvas.drawText(clampedTitle, heroCardRect.left + 240f, heroCardRect.top + 125f, textPaint)

        subPaint.color = Color.parseColor("#CCCCCC")
        subPaint.textSize = 28f
        subPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val artistText = heroTrack?.artist ?: "Start listening to songs"
        val clampedArtist = if (artistText.length > 28) artistText.take(26) + "…" else artistText
        canvas.drawText(clampedArtist, heroCardRect.left + 240f, heroCardRect.top + 170f, subPaint)

        subPaint.color = Color.parseColor("#8E8CA6")
        subPaint.textSize = 24f
        val playInfo = if (heroTrack != null) "${heroTrack.playCount} plays" else ""
        canvas.drawText(playInfo, heroCardRect.left + 240f, heroCardRect.top + 215f, subPaint)

        // 5. Top Songs (Rank 2 to 5)
        textPaint.textSize = 36f
        textPaint.color = Color.WHITE
        canvas.drawText("Top Tracks", 60f, 820f, textPaint)

        var songY = 880f
        val rankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FC3C44")
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        for (song in stats.topSongs.take(5)) {
            canvas.drawText("${song.rank}", 65f, songY, rankPaint)

            textPaint.textSize = 30f
            textPaint.color = Color.WHITE
            val sTitle = if (song.title.length > 30) song.title.take(28) + "…" else song.title
            canvas.drawText(sTitle, 125f, songY, textPaint)

            subPaint.textSize = 24f
            subPaint.color = Color.parseColor("#9D9AB2")
            val sArtist = if (song.artist.length > 32) song.artist.take(30) + "…" else song.artist
            canvas.drawText(sArtist, 125f, songY + 36f, subPaint)

            val pText = "${song.playCount} plays"
            val pWidth = subPaint.measureText(pText)
            canvas.drawText(pText, width - 60f - pWidth, songY + 18f, subPaint)

            songY += 85f
        }

        // 6. Top Artists Section
        textPaint.textSize = 36f
        textPaint.color = Color.WHITE
        canvas.drawText("Top Artists", 60f, 1340f, textPaint)

        var artistX = 60f
        val artChipWidth = (width - 120f - 40f) / 3f

        for (art in stats.topArtists.take(3)) {
            val artBox = RectF(artistX, 1370f, artistX + artChipWidth, 1500f)
            canvas.drawRoundRect(artBox, 24f, 24f, cardPaint)
            canvas.drawRoundRect(artBox, 24f, 24f, borderPaint)

            subPaint.textSize = 22f
            subPaint.color = Color.parseColor("#7928CA")
            subPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("#${art.rank}", artBox.left + 24f, artBox.top + 42f, subPaint)

            textPaint.textSize = 26f
            textPaint.color = Color.WHITE
            val name = if (art.artistName.length > 13) art.artistName.take(11) + "…" else art.artistName
            canvas.drawText(name, artBox.left + 24f, artBox.top + 80f, textPaint)

            subPaint.color = Color.parseColor("#8E8CA6")
            subPaint.textSize = 22f
            subPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("${art.playCount} plays", artBox.left + 24f, artBox.top + 115f, subPaint)

            artistX += artChipWidth + 20f
        }

        // 7. Listening Personality Banner
        val personRect = RectF(60f, 1530f, width - 60f, 1720f)
        val personPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                personRect.left, personRect.top, personRect.right, personRect.bottom,
                Color.parseColor("#25193B"),
                Color.parseColor("#150F24"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(personRect, 28f, 28f, personPaint)
        canvas.drawRoundRect(personRect, 28f, 28f, borderPaint)

        subPaint.textSize = 22f
        subPaint.color = Color.parseColor("#A882FF")
        subPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("YOUR LISTENING PERSONALITY", personRect.left + 36f, personRect.top + 50f, subPaint)

        textPaint.textSize = 42f
        textPaint.color = Color.WHITE
        canvas.drawText(stats.personality.title, personRect.left + 36f, personRect.top + 104f, textPaint)

        subPaint.textSize = 26f
        subPaint.color = Color.parseColor("#D5D3E5")
        subPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val desc = stats.personality.tagline
        canvas.drawText(desc, personRect.left + 36f, personRect.top + 148f, subPaint)

        // 8. Footer
        subPaint.textSize = 24f
        subPaint.color = Color.parseColor("#6A6880")
        val footer = "Generated by iTune  •  Apple Music Aesthetics"
        val fWidth = subPaint.measureText(footer)
        canvas.drawText(footer, (width - fWidth) / 2f, 1820f, subPaint)

        // Save to file
        val outputDir = File(context.cacheDir, "replay").apply { mkdirs() }
        val outputFile = File(outputDir, "itune_replay_${System.currentTimeMillis()}.png")
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return outputFile
    }
}
