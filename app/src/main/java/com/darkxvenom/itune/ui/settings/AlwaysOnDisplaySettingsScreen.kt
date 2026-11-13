package com.darkxvenom.itune.ui.settings

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkxvenom.itune.R
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.ui.aod.AlwaysOnDisplayManager
import com.darkxvenom.itune.ui.artist.components.FrostedTopBar
import com.darkxvenom.itune.ui.artist.components.FrostedTopBarTitlePill
import com.darkxvenom.itune.ui.artist.components.TopFadeBlur
import com.darkxvenom.itune.ui.artist.components.topBarContentPadding
import com.darkxvenom.itune.ui.artist.haptics.Haptic
import com.darkxvenom.itune.ui.artist.haptics.rememberHaptics
import com.darkxvenom.itune.ui.components.rememberSpringOverscrollState
import com.darkxvenom.itune.ui.components.springOverscroll
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

private val AodCardBg = Color(0xFF1C1C1E)
private val AodAppleRed = Color(0xFFFA2D48)

@Composable
fun AlwaysOnDisplaySettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptics = rememberHaptics()
    val settingsState by SettingsStore.state.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val overscrollState = rememberSpringOverscrollState()
    val hazeState = remember { HazeState() }

    BackHandler(onBack = onBack)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppleOledBlack),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .springOverscroll(overscrollState)
                .verticalScroll(scrollState)
                .hazeSource(state = hazeState)
                .padding(
                    top = topBarContentPadding() + 8.dp,
                    bottom = 24.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Card 1: Master Enable / Disable Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AodCardBg)
                    .border(0.6.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (settingsState.aodEnabled) AodAppleRed else Color(0xFF2C2C2E)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_menu_aod),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Always On Display",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = if (settingsState.aodEnabled) "Active on song playback & idle" else "Disabled",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 12.5.sp,
                            )
                        }
                    }

                    Switch(
                        checked = settingsState.aodEnabled,
                        onCheckedChange = { checked ->
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            SettingsStore.setBoolean(context, SettingsStore.KEY_AOD_ENABLED, checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF34C759),
                            uncheckedThumbColor = Color(0xFFE5E5EA),
                            uncheckedTrackColor = Color(0xFF3A3A3C),
                            uncheckedBorderColor = Color.Transparent,
                        ),
                    )
                }
            }

            // Card 2: Auto-Activation Idle Timeout Section
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "AUTO-ACTIVATION TIMEOUT",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp),
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AodCardBg)
                        .border(0.6.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val timeoutOptions = listOf(
                            5 to "5 Seconds",
                            10 to "10 Seconds",
                            20 to "20 Seconds",
                            30 to "30 Seconds",
                            50 to "50 Seconds",
                            60 to "60 Seconds",
                            0 to "Never (Manual Only)",
                        )

                        timeoutOptions.forEachIndexed { index, (seconds, label) ->
                            val isSelected = settingsState.aodTimeoutSeconds == seconds

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        SettingsStore.setInt(context, SettingsStore.KEY_AOD_TIMEOUT, seconds)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f),
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                )

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = AodAppleRed,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }

                            if (index < timeoutOptions.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp)
                                        .height(0.6.dp)
                                        .background(Color.White.copy(alpha = 0.08f))
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "When music is playing, Always On Display turns on if no touch occurs on the screen for the chosen time. Only activates while inside the app.",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 2.dp),
                )
            }

            // Card 3: Display & Full-Screen Options
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "DISPLAY & FULL SCREEN",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp),
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AodCardBg)
                        .border(0.6.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Immersive Mode Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f).padding(end = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "Immersive Mode (Full Screen)",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = "Hides Android status bar and navigation bar for seamless pure OLED black.",
                                    color = Color.White.copy(alpha = 0.50f),
                                    fontSize = 12.sp,
                                    lineHeight = 15.sp,
                                )
                            }

                            Switch(
                                checked = settingsState.aodImmersive,
                                onCheckedChange = { checked ->
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    SettingsStore.setBoolean(context, SettingsStore.KEY_AOD_IMMERSIVE, checked)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF34C759),
                                    uncheckedThumbColor = Color(0xFFE5E5EA),
                                    uncheckedTrackColor = Color(0xFF3A3A3C),
                                    uncheckedBorderColor = Color.Transparent,
                                ),
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp)
                                .height(0.6.dp)
                                .background(Color.White.copy(alpha = 0.08f))
                        )

                        // Keep Screen Awake Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f).padding(end = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "Keep Screen On",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = "Prevents device display from turning off while Always On Display is active.",
                                    color = Color.White.copy(alpha = 0.50f),
                                    fontSize = 12.sp,
                                    lineHeight = 15.sp,
                                )
                            }

                            Switch(
                                checked = settingsState.aodKeepScreenOn,
                                onCheckedChange = { checked ->
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    SettingsStore.setBoolean(context, SettingsStore.KEY_AOD_KEEP_SCREEN_ON, checked)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF34C759),
                                    uncheckedThumbColor = Color(0xFFE5E5EA),
                                    uncheckedTrackColor = Color(0xFF3A3A3C),
                                    uncheckedBorderColor = Color.Transparent,
                                ),
                            )
                        }
                    }
                }
            }

            // Card 4: Preview Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AodCardBg)
                    .border(0.6.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        AlwaysOnDisplayManager.activate()
                    }
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Preview Always On Display",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Tap to immediately activate Always On Display now",
                            color = Color.White.copy(alpha = 0.50f),
                            fontSize = 12.sp,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(AodAppleRed.copy(alpha = 0.18f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "Activate",
                            color = AodAppleRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        TopFadeBlur(
            hazeState = hazeState,
            pageColor = AppleOledBlack,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        FrostedTopBar(
            transparentBackdrop = true,
            artworkPageChrome = true,
            backButtonHazeState = hazeState,
            onBack = {
                haptics.play(Haptic.Tap)
                onBack()
            },
            modifier = Modifier.align(Alignment.TopCenter),
            centerContent = {
                FrostedTopBarTitlePill(hazeState = hazeState) {
                    Text(
                        text = "Always On Display",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
        )
    }
}
