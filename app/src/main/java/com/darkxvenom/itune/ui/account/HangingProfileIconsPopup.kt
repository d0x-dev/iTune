/*
 * Copyright (C) 2026 DxV Studio ~~ Dark
 *
 * Originally created by DxV Studio ~~ Dark.
 * Credits: DxV Studio, AirBeats, and Dark.
 *
 * This file is part of iTune and is licensed under the GNU General Public License v3.0 (GPL-3.0).
 * Any copying, modification, or redistribution of this code must strictly comply with the
 * terms of the project LICENSE file and retain full author credit.
 */

package com.darkxvenom.itune.ui.account

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.darkxvenom.itune.data.account.UserAccount
import com.darkxvenom.itune.ui.components.AppleMemojiAvatar
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleOledBlack

/**
 * Hanging profile icons quick switcher opened by tapping and holding (long press)
 * on the top-right profile avatar.
 */
@Composable
fun HangingProfileIconsPopup(
    accounts: List<UserAccount>,
    activeAccount: UserAccount?,
    onSelectAccount: (UserAccount, Rect?) -> Unit,
    onAddNewAccount: () -> Unit,
    onDismiss: () -> Unit,
) {
    val avatarBoundsMap = remember { mutableStateMapOf<String, Rect>() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onDismiss() },
        ) {
            // Hanging card positioned near top-right
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 54.dp, end = 16.dp)
                    .widthIn(min = 210.dp, max = 250.dp)
                    .shadow(18.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF1E1E22).copy(alpha = 0.94f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { /* prevent dismissal on clicking inside */ },
            ) {
                Text(
                    text = "ACCOUNTS",
                    color = Color.White.copy(alpha = 0.52f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp),
                )

                accounts.forEachIndexed { index, acc ->
                    val isActive = acc.id == activeAccount?.id

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val bounds = avatarBoundsMap[acc.id]
                                onDismiss()
                                if (!isActive) onSelectAccount(acc, bounds)
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Box(
                            modifier = Modifier.onGloballyPositioned { coords ->
                                avatarBoundsMap[acc.id] = coords.boundsInRoot()
                            }
                        ) {
                            AppleMemojiAvatar(
                                size = 36.dp,
                                avatarId = acc.avatarId,
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = acc.displayName,
                                color = Color.White,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (isActive) {
                                Text(
                                    text = "Active Profile",
                                    color = AppleMusicRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                )
                            }
                        }

                        if (isActive) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Active",
                                tint = AppleMusicRed,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    if (index < accounts.lastIndex) {
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.08f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(start = 62.dp),
                        )
                    }
                }

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.10f),
                    thickness = 0.5.dp,
                )

                // "+ Add Account" option
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onAddNewAccount()
                        }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Add Account",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Text(
                        text = "Add Account",
                        color = Color.White,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.
