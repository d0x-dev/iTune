package com.darkxvenom.itune.ui.timetravel

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ListeningActivityByDayChart(
    dailyActivity: Map<Int, Int>,
    modifier: Modifier = Modifier,
) {
    val dayLabels = listOf("S", "M", "T", "W", "T", "F", "S")
    val maxVal = maxOf(1, dailyActivity.values.maxOrNull() ?: 1)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF14131E))
            .padding(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Activity by Day",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            val peakDay = dailyActivity.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key
            val peakName = when (peakDay) {
                0 -> "Sunday"
                1 -> "Monday"
                2 -> "Tuesday"
                3 -> "Wednesday"
                4 -> "Thursday"
                5 -> "Friday"
                6 -> "Saturday"
                else -> "Active"
            }
            Text(
                text = "Peak: $peakName",
                color = Color(0xFFFC3C44),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(95.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            for (day in 0..6) {
                val count = dailyActivity[day] ?: 0
                val ratio = (count.toFloat() / maxVal).coerceIn(0.06f, 1f)
                val animatedRatio by animateFloatAsState(
                    targetValue = ratio,
                    animationSpec = tween(500),
                    label = "barRatio_$day",
                )
                val isPeak = count == maxVal && count > 0

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .fillMaxHeight(animatedRatio)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(
                                    if (isPeak) {
                                        Brush.verticalGradient(
                                            listOf(Color(0xFFFC3C44), Color(0xFF8B0018)),
                                        )
                                    } else {
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF2E2C44), Color(0xFF1E1C2E)),
                                        )
                                    },
                                ),
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = dayLabels[day],
                        color = if (isPeak) Color.White else Color.White.copy(alpha = 0.5f),
                        fontSize = 11.5.sp,
                        fontWeight = if (isPeak) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
fun ListeningActivityByHourChart(
    hourlyActivity: Map<Int, Int>,
    modifier: Modifier = Modifier,
) {
    val maxVal = maxOf(1, hourlyActivity.values.maxOrNull() ?: 1)
    val peakHour = hourlyActivity.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF14131E))
            .padding(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Rhythm by Hour",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            val peakText = when {
                peakHour == null -> "Steady"
                peakHour == 0 -> "Midnight"
                peakHour == 12 -> "Noon"
                peakHour > 12 -> "${peakHour - 12} PM"
                else -> "$peakHour AM"
            }
            Text(
                text = "Peak: $peakText",
                color = Color(0xFF7928CA),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(85.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            for (hr in 0..23) {
                val count = hourlyActivity[hr] ?: 0
                val ratio = (count.toFloat() / maxVal).coerceIn(0.06f, 1f)
                val animatedRatio by animateFloatAsState(
                    targetValue = ratio,
                    animationSpec = tween(500),
                    label = "hourRatio_$hr",
                )
                val isPeak = hr == peakHour

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(animatedRatio)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(
                                if (isPeak) {
                                    Color(0xFF7928CA)
                                } else if (count > 0) {
                                    Color(0xFF3F3B60)
                                } else {
                                    Color(0xFF222033)
                                },
                            ),
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("12 AM", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
            Text("6 AM", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
            Text("12 PM", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
            Text("6 PM", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
            Text("11 PM", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
        }
    }
}
