package com.darkxvenom.itune.ui.components

import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.darkxvenom.itune.data.repository.AvatarManager
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleTextPrimary
import com.darkxvenom.itune.ui.theme.AppleTextSecondary

/**
 * iOS-styled Modal Pop-Up Dialog for choosing a profile avatar:
 * Features a frosted dark modal card, title and subtitle, 4-column avatar picker grid
 * with smooth haptic feedback, and stacked action buttons separated by subtle divider lines
 * matching Apple alert dialog specifications.
 */
@Composable
fun AvatarSelectionDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentSavedId = AvatarManager.currentAvatarId.value
    var selectedId by remember { mutableIntStateOf(currentSavedId) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { win ->
                // Subtle Apple-style scrim (20% dimming, zero blur) so the background remains sharp and clear
                win.setDimAmount(0.20f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    win.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                }
            }
        }

        // Lightweight subtle backdrop - matches screenshot (background content stays clear and visible)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            // Centered iPhone Modal Dialog Card
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .widthIn(max = 340.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* Catch clicks inside card */ }
                    .shadow(
                        elevation = 28.dp,
                        shape = RoundedCornerShape(22.dp),
                        ambientColor = Color.Black,
                        spotColor = Color.Black
                    )
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF1E1E22))
                    .border(
                        width = 0.75.dp,
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(22.dp)
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Area
                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Choose Avatar",
                    color = AppleTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Select a profile avatar to personalize your iTune experience across all screens.",
                    color = AppleTextSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Avatar Grid (4 Columns x 5 Rows = 20 Avatars)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items((1..AvatarManager.TOTAL_AVATARS).toList()) { avatarId ->
                            val isSelected = avatarId == selectedId
                            val avatarUrl = remember(avatarId) { AvatarManager.getAvatarUrl(avatarId) }

                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()
                            val itemScale by animateFloatAsState(
                                targetValue = if (isPressed) 0.90f else if (isSelected) 1.05f else 1.0f,
                                animationSpec = spring(dampingRatio = 0.75f, stiffness = 600f),
                                label = "AvatarScale"
                            )

                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .scale(itemScale)
                                    .shadow(
                                        elevation = if (isSelected) 10.dp else 2.dp,
                                        shape = RoundedCornerShape(16.dp),
                                        ambientColor = if (isSelected) AppleMusicRed.copy(alpha = 0.5f) else Color.Black,
                                        spotColor = if (isSelected) AppleMusicRed else Color.Black
                                    )
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF141416))
                                    .border(
                                        width = if (isSelected) 2.5.dp else 0.75.dp,
                                        color = if (isSelected) AppleMusicRed else Color.White.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null
                                    ) {
                                        selectedId = avatarId
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                SubcomposeAsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(avatarUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Avatar $avatarId",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Checkmark badge for currently active selection
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .size(16.dp)
                                            .background(AppleMusicRed, CircleShape)
                                            .border(1.2.dp, Color(0xFF1E1E22), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Divided Action Buttons (Directly matching iOS modal in screenshot)
                // Divider 1
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = Color.White.copy(alpha = 0.12f)
                )

                // "Save" Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable {
                            AvatarManager.setAvatar(context, selectedId)
                            Toast.makeText(context, "Profile avatar updated", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Save",
                        color = AppleMusicRed,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp
                    )
                }

                // Divider 2
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = Color.White.copy(alpha = 0.12f)
                )

                // "Cancel" Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable {
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Cancel",
                        color = Color(0xFF8E8E93),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = (-0.2).sp
                    )
                }
            }
        }
    }
}
