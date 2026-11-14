package com.darkxvenom.itune.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil.compose.AsyncImage
import androidx.compose.foundation.Image
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import com.darkxvenom.itune.R
import com.darkxvenom.itune.data.updates.AppUpdateManager
import com.darkxvenom.itune.data.updates.GithubReleaseInfo
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import com.darkxvenom.itune.ui.theme.LocalAppFontFamily
import dev.jeziellago.compose.markdowntext.MarkdownText
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkxvenom.itune.data.updates.UpdateDownloadState

@Composable
fun AppUpdatePopupDialog(
    release: GithubReleaseInfo,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val downloadState by AppUpdateManager.downloadState.collectAsStateWithLifecycle()
    val downloading = downloadState as? UpdateDownloadState.Downloading
    val completed = downloadState as? UpdateDownloadState.Completed
    val failed = downloadState as? UpdateDownloadState.Failed

    LaunchedEffect(completed?.apkFile) {
        val apk = completed?.apkFile ?: return@LaunchedEffect
        if (!AppUpdateManager.canRequestPackageInstalls(context)) {
            AppUpdateManager.launchInstallerFlow(context, apk)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { window ->
                window.setDimAmount(0.35f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                }
            }
        }
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .widthIn(max = 360.dp)
                    .heightIn(max = maxHeight * 0.9f)
                    .shadow(32.dp, RoundedCornerShape(26.dp), ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(RoundedCornerShape(26.dp))
                    .background(AppleOledBlack)
                    .border(0.8.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(26.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(24.dp))
                    Box(
                        modifier = Modifier
                            .size(78.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.05f))
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_itune_waves),
                            contentDescription = "iTune",
                            modifier = Modifier.size(46.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "iTune",
                        color = Color.White,
                        fontFamily = LocalAppFontFamily.current,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = "A new version is ready",
                        color = Color.White.copy(alpha = 0.72f),
                        fontFamily = LocalAppFontFamily.current,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(18.dp))

                    if (release.notes.isNotBlank()) {
                        Text(
                            text = "WHAT’S NEW",
                            color = Color.White.copy(alpha = 0.50f),
                            fontFamily = LocalAppFontFamily.current,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.align(Alignment.Start),
                        )
                        Spacer(Modifier.height(7.dp))
                        ReleaseNotesMarkdown(release.notes)
                    }

                    Spacer(Modifier.height(16.dp))
                }

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                downloading?.let { state ->
                    val downloaded = formatUpdateBytes(state.downloadedBytes)
                    val total = state.totalBytes.takeIf { it > 0L }?.let(::formatUpdateBytes)
                    Text(
                        text = when {
                            state.downloadedBytes == 0L && total == null -> "Connecting to GitHub…"
                            state.downloadedBytes == 0L -> "Connected · preparing download…"
                            total == null -> "Downloading update · $downloaded"
                            else -> "Downloading update · $downloaded of $total"
                        },
                        color = Color.White.copy(alpha = 0.72f),
                        fontFamily = LocalAppFontFamily.current,
                        fontSize = 12.sp,
                    )
                    if (state.progressPercent < 0) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.14f),
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { state.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.14f),
                        )
                    }
                }
                failed?.let { state ->
                    Text(
                        text = state.message,
                        color = Color(0xFFFF6961),
                        fontFamily = LocalAppFontFamily.current,
                        fontSize = 12.sp,
                    )
                }
                UpdateActionButton(
                    title = when {
                        downloading != null -> "Downloading ${downloading.progressPercent.takeIf { it >= 0 }?.let { "$it%" } ?: "…"}"
                        completed != null && !AppUpdateManager.canRequestPackageInstalls(context) -> "Allow Install Permission"
                        completed != null -> "Install Update"
                        failed != null -> "Retry Download"
                        release.hasApkAsset -> "Download APK"
                        else -> "View Release"
                    },
                    tint = Color(0xFF0A84FF),
                    onClick = {
                        when {
                            downloading != null -> Unit
                            completed != null -> AppUpdateManager.launchInstallerFlow(context, completed.apkFile)
                            else -> {
                                AppUpdateManager.downloadApk(context, release)
                                if (!release.hasApkAsset) onDismiss()
                            }
                        }
                    },
                )
                UpdateActionButton(title = "Later", tint = Color.White.copy(alpha = 0.70f), onClick = onDismiss)
            }
        }
    }
}
}

private fun formatUpdateBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024f)
    bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024f * 1024f))
    else -> "%.2f GB".format(bytes / (1024f * 1024f * 1024f))
}

@Composable
private fun ReleaseNotesMarkdown(markdown: String) {
    val imagePattern = Regex(
        "(?i)<img\\b[^>]*\\bsrc\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>|!\\[([^\\]]*)]\\((https?://[^)]+)\\)"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppleOledBlack)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        var cursor = 0
        imagePattern.findAll(markdown).forEach { match ->
            val markdownBefore = markdown.substring(cursor, match.range.first).trim()
            if (markdownBefore.isNotBlank()) {
                MarkdownText(
                    markdown = markdownBefore,
                    style = TextStyle(
                        fontFamily = LocalAppFontFamily.current,
                        color = Color.White.copy(alpha = 0.90f),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    ),
                    linkColor = Color(0xFF64D2FF),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val imageUrl = match.groupValues[1].ifBlank { match.groupValues[3] }
            val imageDescription = match.groupValues[2].ifBlank { "Release image" }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = imageDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f)))),
                )
            }
            cursor = match.range.last + 1
        }

        val remainingMarkdown = markdown.substring(cursor).trim()
        if (remainingMarkdown.isNotBlank()) {
            MarkdownText(
                markdown = remainingMarkdown,
                style = TextStyle(
                    fontFamily = LocalAppFontFamily.current,
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                ),
                linkColor = Color(0xFF64D2FF),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun UpdateActionButton(title: String, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = if (title != "Later") 0.22f else 0.07f))
            .border(0.8.dp, tint.copy(alpha = 0.24f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, color = tint, fontFamily = LocalAppFontFamily.current, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
