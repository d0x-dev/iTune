package com.darkxvenom.itune.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.darkxvenom.itune.R
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
internal fun LiveLogBubble() {
    val title = stringResource(R.string.real_time_logs)
    var x by rememberSaveable { mutableFloatStateOf(0.97f) }
    var y by rememberSaveable { mutableFloatStateOf(0.42f) }
    var show by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(4.dp)) {
        val diameter = 48.dp
        val density = LocalDensity.current
        val travelX = with(density) { (maxWidth - diameter).toPx().coerceAtLeast(0f) }
        val travelY = with(density) { (maxHeight - diameter).toPx().coerceAtLeast(0f) }
        Surface(
            modifier = Modifier.absoluteOffset { IntOffset((travelX * x).roundToInt(), (travelY * y).roundToInt()) }
                .size(diameter).clip(CircleShape)
                .pointerInput(travelX, travelY) {
                    detectDragGestures { change, delta ->
                        change.consume()
                        if (travelX > 0) x = (x + delta.x / travelX).coerceIn(0f, 1f)
                        if (travelY > 0) y = (y + delta.y / travelY).coerceIn(0f, 1f)
                    }
                }.clickable { show = true },
            shape = CircleShape, color = Color(0xFF161618), shadowElevation = 5.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.32f)),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Code, title, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
    if (show) LiveLogDialog(title, x, y) { show = false }
}

@Composable
private fun LiveLogDialog(title: String, x: Float, y: Float, onClosed: () -> Unit) {
    val context = LocalContext.current
    val motion = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var lines by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(closing) {
        motion.animateTo(if (closing) 0f else 1f, spring(dampingRatio = 0.78f, stiffness = 480f))
        if (closing) onClosed()
    }
    LaunchedEffect(Unit) {
        var version = -1L
        while (true) {
            LiveLogs.snapshot(version)?.let { (next, entries) -> version = next; lines = entries }
            delay(300)
        }
    }
    fun export(share: Boolean) {
        val text = lines.asReversed().joinToString("\n")
        runCatching {
            if (share) {
                val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(Intent.createChooser(intent, title))
            } else {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(title, text))
                if (android.os.Build.VERSION.SDK_INT < 33)
                    Toast.makeText(context, R.string.toast_copied_to_clipboard, Toast.LENGTH_SHORT).show()
            }
        }.onFailure { Toast.makeText(context, R.string.crash_action_failed, Toast.LENGTH_SHORT).show() }
    }
    Dialog(onDismissRequest = { closing = true }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), contentAlignment = Alignment.Center) {
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val viewport = (maxHeight * 0.36f).coerceAtMost(224.dp)
            Box(Modifier.matchParentSize().clickable(interactionSource = null, indication = null) { closing = true })
            Surface(
                modifier = Modifier.widthIn(max = 370.dp).fillMaxWidth().graphicsLayer {
                    val p = motion.value
                    translationX = (x - 0.5f) * width * (1 - p)
                    translationY = (y - 0.5f) * height * (1 - p)
                    scaleX = 0.12f + 0.88f * p
                    scaleY = 0.20f + 0.80f * p
                    alpha = (p * 2).coerceIn(0f, 1f)
                },
                shape = RoundedCornerShape(26.dp), color = Color(0xFF101012),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(reverseLayout = true, modifier = Modifier.fillMaxWidth().height(viewport)
                        .clip(RoundedCornerShape(12.dp)).background(Color.Black).padding(10.dp)) {
                        items(lines) { entry ->
                            Text(entry, color = Color(0xFFDBDBE0), fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(bottom = 4.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(onClick = { export(false) }, enabled = lines.isNotEmpty()) {
                            Text(stringResource(R.string.ui_copy), color = Color(0xFF74B4FF))
                        }
                        TextButton(onClick = { export(true) }, enabled = lines.isNotEmpty()) {
                            Text(stringResource(R.string.share), color = Color(0xFF74B4FF))
                        }
                        TextButton(onClick = { closing = true }) { Text(stringResource(R.string.close), color = Color.White) }
                    }
                }
            }
        }
    }
}
