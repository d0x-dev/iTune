package com.darkxvenom.itune.ui.settings

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.darkxvenom.itune.R
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleTextPrimary
import com.darkxvenom.itune.ui.theme.AppleTextSecondary

private val DAYS_OF_WEEK = listOf("All", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutoSleepTimerDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val currentConfig = SettingsStore.state.value

    var enabled by remember { mutableStateOf(currentConfig.autoSleepTimerEnabled) }

    // Start Time (On Time)
    var startHour24 by remember { mutableIntStateOf(currentConfig.autoSleepStartHour) }
    var startMinute by remember { mutableIntStateOf(currentConfig.autoSleepStartMinute) }

    // End Time (Off Time)
    var endHour24 by remember { mutableIntStateOf(currentConfig.autoSleepEndHour) }
    var endMinute by remember { mutableIntStateOf(currentConfig.autoSleepEndMinute) }

    // Selected Days
    var selectedDays by remember {
        mutableStateOf(if (currentConfig.autoSleepDays.isEmpty()) setOf("All") else currentConfig.autoSleepDays.toSet())
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 380.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* absorb clicks */ }
                    .shadow(32.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1C1C1E))
                    .border(0.75.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                // Icon Badge
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(AppleMusicRed.copy(alpha = 0.15f))
                        .border(0.75.dp, AppleMusicRed.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings_sleep_timer),
                        contentDescription = null,
                        tint = AppleMusicRed,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Auto Sleep Timer",
                    color = AppleTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp
                )

                Text(
                    text = "Recurring sleep schedule · auto pauses playback",
                    color = AppleTextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Master Toggle Container
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Enable Auto Sleep",
                            color = AppleTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (enabled) "Schedule is active" else "Schedule is paused",
                            color = if (enabled) AppleMusicRed else AppleTextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            enabled = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AppleMusicRed,
                            uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                            uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Time Pickers Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF141416))
                        .border(0.75.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    // Turn On / Sleep Time
                    AppleTimeSelectorRow(
                        label = "Start Sleep (On)",
                        hour24 = startHour24,
                        minute = startMinute,
                        onTimeChange = { h, m ->
                            startHour24 = h
                            startMinute = m
                        }
                    )

                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    // Turn Off / Wake Time
                    AppleTimeSelectorRow(
                        label = "End Sleep (Off)",
                        hour24 = endHour24,
                        minute = endMinute,
                        onTimeChange = { h, m ->
                            endHour24 = h
                            endMinute = m
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Days Selection Title
                Text(
                    text = "ACTIVE DAYS",
                    color = AppleTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Day Chips (FlowRow)
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DAYS_OF_WEEK.forEach { day ->
                        val isSelected = if (day == "All") {
                            selectedDays.contains("All") || selectedDays.size >= 7
                        } else {
                            selectedDays.contains("All") || selectedDays.contains(day)
                        }

                        val chipBg by animateColorAsState(
                            targetValue = if (isSelected) AppleMusicRed else Color.White.copy(alpha = 0.07f),
                            label = "DayChipBg"
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(chipBg)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    selectedDays = if (day == "All") {
                                        if (selectedDays.contains("All")) emptySet() else setOf("All")
                                    } else {
                                        val next = selectedDays.toMutableSet()
                                        next.remove("All")
                                        if (next.contains(day)) next.remove(day) else next.add(day)
                                        if (next.size == 7) setOf("All") else next
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day,
                                color = if (isSelected) Color.White else AppleTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions Divider
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = Color.White.copy(alpha = 0.12f)
                )

                // Save Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            val finalDays = if (selectedDays.isEmpty()) setOf("All") else selectedDays
                            SettingsStore.setAutoSleepConfig(
                                context = context,
                                enabled = enabled,
                                startH = startHour24,
                                startM = startMinute,
                                endH = endHour24,
                                endM = endMinute,
                                days = finalDays
                            )
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Save Schedule",
                        color = AppleMusicRed,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp
                    )
                }

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = Color.White.copy(alpha = 0.12f)
                )

                // Cancel Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Cancel",
                        color = AppleTextSecondary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = (-0.2).sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AppleTimeSelectorRow(
    label: String,
    hour24: Int,
    minute: Int,
    onTimeChange: (hour24: Int, minute: Int) -> Unit
) {
    val isPm = hour24 >= 12
    val hour12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = AppleTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Hour Controller
            AppleStepperBox(
                value = "%02d".format(hour12),
                onIncrement = {
                    val next12 = if (hour12 >= 12) 1 else hour12 + 1
                    val new24 = if (isPm) (if (next12 == 12) 12 else next12 + 12) else (if (next12 == 12) 0 else next12)
                    onTimeChange(new24, minute)
                },
                onDecrement = {
                    val next12 = if (hour12 <= 1) 12 else hour12 - 1
                    val new24 = if (isPm) (if (next12 == 12) 12 else next12 + 12) else (if (next12 == 12) 0 else next12)
                    onTimeChange(new24, minute)
                }
            )

            Text(text = ":", color = AppleTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)

            // Minute Controller
            AppleStepperBox(
                value = "%02d".format(minute),
                onIncrement = {
                    val nextM = (minute + 5) % 60
                    onTimeChange(hour24, nextM)
                },
                onDecrement = {
                    val nextM = if (minute - 5 < 0) 55 else minute - 5
                    onTimeChange(hour24, nextM)
                }
            )

            Spacer(modifier = Modifier.width(4.dp))

            // AM / PM Toggle Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (!isPm) AppleMusicRed else Color.Transparent)
                        .clickable {
                            if (isPm) {
                                val new24 = if (hour24 == 12) 0 else hour24 - 12
                                onTimeChange(new24, minute)
                            }
                        }
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "AM",
                        color = if (!isPm) Color.White else AppleTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isPm) AppleMusicRed else Color.Transparent)
                        .clickable {
                            if (!isPm) {
                                val new24 = if (hour24 == 0) 12 else hour24 + 12
                                onTimeChange(new24, minute)
                            }
                        }
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "PM",
                        color = if (isPm) Color.White else AppleTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun AppleStepperBox(
    value: String,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    val view = LocalView.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.09f))
            .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onDecrement()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = AppleTextSecondary, modifier = Modifier.size(12.dp))
        }

        Text(
            text = value,
            color = AppleTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Box(
            modifier = Modifier
                .size(20.dp)
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onIncrement()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = "Increase", tint = AppleTextSecondary, modifier = Modifier.size(12.dp))
        }
    }
}
