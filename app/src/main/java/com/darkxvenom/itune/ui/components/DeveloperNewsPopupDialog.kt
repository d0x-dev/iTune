package com.darkxvenom.itune.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.darkxvenom.itune.R
import com.darkxvenom.itune.data.news.DeveloperNewsManager
import com.darkxvenom.itune.ui.player.SFProDisplay
import dev.jeziellago.compose.markdowntext.MarkdownText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PopupCardBg = Color(0xFF161618)
private val AppleCrimson = Color(0xFFFC3C44)
private val AppleCrimsonSoft = Color(0xFFFF375F)
private val FrostedBorder = Color.White.copy(alpha = 0.12f)

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
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            shape = RoundedCornerShape(26.dp),
            color = PopupCardBg,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, FrostedBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Banner Image if present
                if (!item.imageUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    ) {
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth()
                        )
                        // Gradient shadow at bottom of image
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, PopupCardBg)
                                    )
                                )
                        )

                        // Floating Tag Badge
                        val tagText = item.tag?.takeIf { it.isNotBlank() } ?: "Announcement"
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = AppleCrimson.copy(alpha = 0.22f),
                            border = BorderStroke(1.dp, AppleCrimson.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = tagText,
                                style = TextStyle(
                                    fontFamily = SFProDisplay,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.3.sp
                                ),
                                color = AppleCrimsonSoft,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(24.dp))
                    // Icon Badge if no image
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(64.dp)
                            .scale(pulseScale)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            AppleCrimson.copy(alpha = 0.25f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(AppleCrimson.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.newspaper),
                                contentDescription = "News",
                                tint = AppleCrimsonSoft,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val tagText = item.tag?.takeIf { it.isNotBlank() } ?: "News from Developer"
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = AppleCrimson.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, AppleCrimson.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = tagText,
                            style = TextStyle(
                                fontFamily = SFProDisplay,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = AppleCrimsonSoft,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 16.dp),
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
                            fontFamily = SFProDisplay,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        ),
                        color = Color.White.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Title
                    Text(
                        text = item.title,
                        style = TextStyle(
                            fontFamily = SFProDisplay,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            letterSpacing = (-0.3).sp
                        ),
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Markdown Body Text in dark frosted container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(14.dp)
                    ) {
                        MarkdownText(
                            markdown = item.message,
                            style = TextStyle(
                                color = Color.White.copy(alpha = 0.88f),
                                fontSize = 14.sp,
                                lineHeight = 21.sp
                            ),
                            linkColor = AppleCrimsonSoft
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Primary Action Button (if actionUrl is provided)
                    if (!item.actionUrl.isNullOrBlank()) {
                        Button(
                            onClick = {
                                try {
                                    uriHandler.openUri(item.actionUrl)
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppleCrimson,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.open_in_new),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item.actionText?.takeIf { it.isNotBlank() } ?: "Learn More",
                                style = TextStyle(
                                    fontFamily = SFProDisplay,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Secondary actions: Dismiss / All Announcements
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                DeveloperNewsManager.dismissPopup(item.id)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
                        ) {
                            Text(
                                text = "Dismiss",
                                style = TextStyle(
                                    fontFamily = SFProDisplay,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                ),
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }

                        Button(
                            onClick = {
                                DeveloperNewsManager.dismissPopup(item.id)
                                onNavigateToNews()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.12f),
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = "All News",
                                style = TextStyle(
                                    fontFamily = SFProDisplay,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                ),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
