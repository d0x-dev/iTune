package com.darkxvenom.itune.ui.components

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil.compose.AsyncImage
import com.darkxvenom.itune.R
import com.darkxvenom.itune.data.news.DeveloperNewsManager
import com.darkxvenom.itune.ui.theme.LocalAppFontFamily
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import com.darkxvenom.itune.ui.theme.AppleTextPrimary
import com.darkxvenom.itune.ui.theme.AppleTextSecondary
import dev.jeziellago.compose.markdowntext.MarkdownText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DeveloperNewsPopupDialog(
    onNavigateToNews: () -> Unit = {}
) {
    val activePopup by DeveloperNewsManager.activePopup.collectAsState()
    val item = activePopup ?: return

    val uriHandler = LocalUriHandler.current

    val infiniteTransition = rememberInfiniteTransition(label = "news_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "newsPulseScale"
    )

    Dialog(
        onDismissRequest = {
            DeveloperNewsManager.dismissPopup(item.id)
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { win ->
                win.setDimAmount(0.35f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    win.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { DeveloperNewsManager.dismissPopup(item.id) },
            contentAlignment = Alignment.Center
        ) {
            // Centered Pure Black Apple-styled Modal Dialog Card
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .widthIn(max = 360.dp)
                    .heightIn(max = 580.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* Catch clicks inside */ }
                    .shadow(
                        elevation = 32.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = Color.Black,
                        spotColor = Color.Black
                    )
                    .clip(RoundedCornerShape(24.dp))
                    .background(AppleOledBlack)
                    .border(
                        width = 0.8.dp,
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(24.dp)
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Scrollable Content Column (Image, Tag, Author, Title, Message)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Banner Image if present
                    if (!item.imageUrl.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp)
                                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        ) {
                            AsyncImage(
                                model = item.imageUrl,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Gradient shadow at bottom of image
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, AppleOledBlack)
                                        )
                                    )
                            )

                            // Tag badge
                            val tagText = item.tag?.takeIf { it.isNotBlank() } ?: "Announcement"
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(14.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color.Black.copy(alpha = 0.70f))
                                    .border(0.6.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = tagText,
                                    style = TextStyle(
                                        fontFamily = LocalAppFontFamily.current,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        letterSpacing = 0.3.sp
                                    ),
                                    color = AppleMusicRed
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(24.dp))
                        // Pulsing Newspaper Icon Badge
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(56.dp)
                                .scale(pulseScale)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(AppleMusicRed.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.newspaper),
                                    contentDescription = "News",
                                    tint = AppleMusicRed,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val tagText = item.tag?.takeIf { it.isNotBlank() } ?: "News from Developer"
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(0.6.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = tagText,
                                style = TextStyle(
                                    fontFamily = LocalAppFontFamily.current,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.5.sp
                                ),
                                color = AppleMusicRed
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Author & Date
                        val authorName = item.author?.takeIf { it.isNotBlank() } ?: "Developer"
                        val dateFormatted = try {
                            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
                            sdf.format(Date(item.timestamp))
                        } catch (_: Exception) { "" }
                        val authorDateStr = if (dateFormatted.isNotBlank()) "$authorName • $dateFormatted" else authorName

                        Text(
                            text = authorDateStr,
                            style = TextStyle(
                                fontFamily = LocalAppFontFamily.current,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            ),
                            color = AppleTextSecondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Title
                        Text(
                            text = item.title,
                            style = TextStyle(
                                fontFamily = LocalAppFontFamily.current,
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                                letterSpacing = (-0.3).sp,
                                lineHeight = 24.sp
                            ),
                            color = AppleTextPrimary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Markdown Message Content
                        MarkdownText(
                            markdown = item.message,
                            style = TextStyle(
                                fontFamily = LocalAppFontFamily.current,
                                color = Color.White.copy(alpha = 0.90f),
                                fontSize = 14.sp,
                                lineHeight = 21.sp
                            ),
                            linkColor = AppleMusicRed,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Apple Alert Style Action Rows with 0.5.dp Dividers (matching AvatarSelectionDialog)
                if (!item.actionUrl.isNullOrBlank()) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = Color.White.copy(alpha = 0.12f)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable {
                                try {
                                    uriHandler.openUri(item.actionUrl)
                                } catch (_: Exception) {}
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = item.actionText?.takeIf { it.isNotBlank() } ?: "Learn More",
                                color = AppleMusicRed,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = LocalAppFontFamily.current,
                                letterSpacing = (-0.2).sp
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                painter = painterResource(R.drawable.open_in_new),
                                contentDescription = null,
                                tint = AppleMusicRed,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = Color.White.copy(alpha = 0.12f)
                )

                // "All News" Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable {
                            DeveloperNewsManager.dismissPopup(item.id)
                            onNavigateToNews()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "All News",
                        color = AppleMusicRed,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = LocalAppFontFamily.current,
                        letterSpacing = (-0.2).sp
                    )
                }

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = Color.White.copy(alpha = 0.12f)
                )

                // "Dismiss" Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable {
                            DeveloperNewsManager.dismissPopup(item.id)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Dismiss",
                        color = Color(0xFF8E8E93),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = LocalAppFontFamily.current,
                        letterSpacing = (-0.2).sp
                    )
                }
            }
        }
    }
}
