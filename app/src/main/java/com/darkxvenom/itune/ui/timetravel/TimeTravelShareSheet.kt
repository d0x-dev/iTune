package com.darkxvenom.itune.ui.timetravel

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.darkxvenom.itune.data.timetravel.TimeTravelStats
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import com.darkxvenom.itune.ui.theme.AppleTextPrimary
import com.darkxvenom.itune.ui.theme.AppleTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeTravelShareSheet(
    stats: TimeTravelStats,
    holder: String,
    memberSince: String? = "11/26",
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var poster by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(stats) {
        withContext(Dispatchers.IO) {
            runCatching {
                renderReplayPoster(context, stats, holder, memberSince)
            }.onSuccess {
                poster = it
            }.onFailure {
                failed = true
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppleOledBlack,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Drag handle pill
            Box(
                modifier = Modifier
                    .size(width = 38.dp, height = 4.5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.25f)),
            )

            Spacer(Modifier.height(18.dp))

            Text(
                text = "Share my Replay",
                color = AppleTextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "A full picture with top songs, artists, albums and habits.",
                color = AppleTextSecondary,
                fontSize = 13.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(18.dp))

            // 9:16 Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.52f)
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                val image = poster
                when {
                    image != null -> {
                        Image(
                            bitmap = image.asImageBitmap(),
                            contentDescription = "Replay Poster",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    failed -> {
                        Text(
                            text = "Could not render replay image",
                            color = AppleTextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                    else -> {
                        CircularProgressIndicator(
                            color = Color(0xFFFC3C44),
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            // Share Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFFC3C44))
                    .clickable {
                        val img = poster ?: return@clickable
                        scope.launch {
                            shareBitmap(context, img, stats.period.label)
                            onDismiss()
                        }
                    }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.IosShare,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Share Replay",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .clickable {
                        val img = poster ?: return@clickable
                        scope.launch {
                            saveBitmapToGallery(context, img, "iTune_Replay_${System.currentTimeMillis()}")
                            Toast.makeText(context, "Saved to Gallery!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    }
                    .padding(vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    tint = AppleTextPrimary,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Save to Gallery",
                    color = AppleTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private suspend fun shareBitmap(context: Context, bitmap: Bitmap, periodLabel: String) = withContext(Dispatchers.IO) {
    try {
        val cachePath = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File(cachePath, "itune_replay_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Check out my $periodLabel Replay on iTune! 🎵")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(intent, "Share Replay").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap, title: String) = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "$title.png")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/iTune")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }

    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    if (uri != null) {
        resolver.openOutputStream(uri).use { stream ->
            if (stream != null) bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }
    }
}

/**
 * Renders high-quality 1080x1920 poster matching BitChord's poster layout.
 */
suspend fun renderReplayPoster(
    context: Context,
    stats: TimeTravelStats,
    holder: String,
    memberSince: String?,
): Bitmap = withContext(Dispatchers.IO) {
    val width = 1080
    val height = 1920
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Load top song artwork
    val leadArtUrl = stats.topSongs.firstOrNull()?.artworkUrl
    var leadArtBitmap: Bitmap? = null
    if (!leadArtUrl.isNullOrBlank()) {
        runCatching {
            val req = ImageRequest.Builder(context)
                .data(leadArtUrl)
                .size(256)
                .allowHardware(false)
                .build()
            val res = context.imageLoader.execute(req)
            if (res is SuccessResult) {
                leadArtBitmap = res.drawable.toBitmap(256, 256, Bitmap.Config.ARGB_8888)
            }
        }
    }

    // 1. Mesh-style gradient backdrop
    val bgPaint = Paint().apply { isAntiAlias = true }
    bgPaint.shader = LinearGradient(
        0f, 0f, 0f, height.toFloat(),
        intArrayOf(
            AndroidColor.parseColor("#1C0A28"),
            AndroidColor.parseColor("#0C0714"),
            AndroidColor.parseColor("#050208"),
        ),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

    // Luminous orbs
    val orbPaint = Paint().apply {
        isAntiAlias = true
        alpha = 140
    }
    orbPaint.shader = android.graphics.RadialGradient(
        240f, 320f, 550f,
        intArrayOf(AndroidColor.parseColor("#FC3C44"), AndroidColor.TRANSPARENT),
        null,
        Shader.TileMode.CLAMP,
    )
    canvas.drawCircle(240f, 320f, 550f, orbPaint)

    orbPaint.shader = android.graphics.RadialGradient(
        840f, 950f, 600f,
        intArrayOf(AndroidColor.parseColor("#7928CA"), AndroidColor.TRANSPARENT),
        null,
        Shader.TileMode.CLAMP,
    )
    canvas.drawCircle(840f, 950f, 600f, orbPaint)

    // Semi-transparent dark wash
    val washPaint = Paint().apply {
        isAntiAlias = true
        color = AndroidColor.argb(170, 0, 0, 0)
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), washPaint)

    val textPaint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    // 2. Header
    textPaint.textSize = 28f
    textPaint.color = AndroidColor.parseColor("#FC3C44")
    canvas.drawText("iTune REPLAY", 80f, 130f, textPaint)

    textPaint.textSize = 66f
    textPaint.color = AndroidColor.WHITE
    canvas.drawText("Your ${stats.period.label}", 80f, 210f, textPaint)

    textPaint.textSize = 28f
    textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    textPaint.color = AndroidColor.argb(180, 255, 255, 255)
    canvas.drawText("${holder.ifBlank { "iTune Listener" }} · Member Since ${memberSince ?: "2026"}", 80f, 260f, textPaint)

    // 3. Stat Highlights Row
    val cardPaint = Paint().apply {
        isAntiAlias = true
        color = AndroidColor.argb(55, 255, 255, 255)
    }
    canvas.drawRoundRect(RectF(80f, 300f, 400f, 440f), 24f, 24f, cardPaint)
    canvas.drawRoundRect(RectF(430f, 300f, 750f, 440f), 24f, 24f, cardPaint)
    canvas.drawRoundRect(RectF(780f, 300f, 1000f, 440f), 24f, 24f, cardPaint)

    textPaint.textSize = 46f
    textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    textPaint.color = AndroidColor.WHITE
    canvas.drawText("${stats.totalListeningSec / 60}", 110f, 365f, textPaint)
    canvas.drawText("${stats.totalPlays}", 460f, 365f, textPaint)
    canvas.drawText("${stats.distinctSongsCount}", 810f, 365f, textPaint)

    textPaint.textSize = 22f
    textPaint.color = AndroidColor.argb(170, 255, 255, 255)
    canvas.drawText("MINUTES", 110f, 410f, textPaint)
    canvas.drawText("PLAYS", 460f, 410f, textPaint)
    canvas.drawText("SONGS", 810f, 410f, textPaint)

    // 4. Top Songs Section
    textPaint.textSize = 36f
    textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    textPaint.color = AndroidColor.WHITE
    canvas.drawText("TOP SONGS", 80f, 520f, textPaint)

    var songY = 580f
    stats.topSongs.take(5).forEachIndexed { idx, s ->
        textPaint.textSize = 32f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.color = if (idx == 0) AndroidColor.parseColor("#FC3C44") else AndroidColor.argb(140, 255, 255, 255)
        canvas.drawText("${idx + 1}", 80f, songY + 36f, textPaint)

        textPaint.color = AndroidColor.WHITE
        val title = if (s.title.length > 36) s.title.take(33) + "..." else s.title
        canvas.drawText(title, 140f, songY + 24f, textPaint)

        textPaint.textSize = 22f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.color = AndroidColor.argb(160, 255, 255, 255)
        canvas.drawText("${s.artist} · ${s.playCount} plays", 140f, songY + 54f, textPaint)

        songY += 86f
    }

    // 5. Top Artists Section
    var artistY = songY + 40f
    textPaint.textSize = 36f
    textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    textPaint.color = AndroidColor.WHITE
    canvas.drawText("TOP ARTISTS", 80f, artistY, textPaint)

    artistY += 60f
    stats.topArtists.take(4).forEachIndexed { idx, a ->
        textPaint.textSize = 32f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.color = if (idx == 0) AndroidColor.parseColor("#FC3C44") else AndroidColor.argb(140, 255, 255, 255)
        canvas.drawText("${idx + 1}", 80f, artistY + 36f, textPaint)

        textPaint.color = AndroidColor.WHITE
        canvas.drawText(a.artistName, 140f, artistY + 24f, textPaint)

        textPaint.textSize = 22f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.color = AndroidColor.argb(160, 255, 255, 255)
        canvas.drawText("${a.playCount} plays", 140f, artistY + 54f, textPaint)

        artistY += 86f
    }

    // 6. Footer branding
    val footerPaint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 26f
        color = AndroidColor.argb(180, 255, 255, 255)
    }
    canvas.drawText("iTune for Android", 80f, height - 70f, footerPaint)

    bitmap
}
