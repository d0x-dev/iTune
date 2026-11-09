package com.darkxvenom.itune.ui.search

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.darkxvenom.itune.data.model.SearchCategoryType

@Composable
fun CategoryCardArtwork(
    type: SearchCategoryType,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        when (type) {
            SearchCategoryType.APPLE_MUSIC_LIVE -> {
                // Monochrome Battersea style architectural monolith towers
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F0F12), Color(0xFF1E1E24), Color(0xFF0A0A0C))
                    )
                )

                // Left tower
                val leftTower = Path().apply {
                    moveTo(w * 0.16f, h * 0.10f)
                    lineTo(w * 0.32f, h * 0.10f)
                    lineTo(w * 0.35f, h * 0.90f)
                    lineTo(w * 0.13f, h * 0.90f)
                    close()
                }
                drawPath(
                    leftTower,
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFF88888E), Color(0xFF44444A), Color(0xFF1C1C20))
                    )
                )

                // Right tower
                val rightTower = Path().apply {
                    moveTo(w * 0.68f, h * 0.10f)
                    lineTo(w * 0.84f, h * 0.10f)
                    lineTo(w * 0.87f, h * 0.90f)
                    lineTo(w * 0.65f, h * 0.90f)
                    close()
                }
                drawPath(
                    rightTower,
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFF1C1C20), Color(0xFF44444A), Color(0xFF88888E))
                    )
                )

                // Fluted chimney details
                for (i in 0..4) {
                    val y = h * (0.16f + i * 0.12f)
                    drawLine(Color(0x55FFFFFF), Offset(w * 0.15f, y), Offset(w * 0.33f, y), strokeWidth = 2f)
                    drawLine(Color(0x55FFFFFF), Offset(w * 0.67f, y), Offset(w * 0.85f, y), strokeWidth = 2f)
                }
            }

            SearchCategoryType.HITS -> {
                // Warm golden yellow with contemplative Taylor Swift profile
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFFD438), Color(0xFFE5AC15), Color(0xFFC78F0A)),
                        center = Offset(w * 0.70f, h * 0.40f),
                        radius = w * 0.70f
                    )
                )

                val shadowColor = Color(0xFF5A3E00)
                // Flowing hair mass
                drawOval(
                    color = shadowColor,
                    topLeft = Offset(w * 0.52f, h * 0.14f),
                    size = Size(w * 0.40f, h * 0.72f)
                )
                // Contemplative head profile
                drawOval(
                    color = shadowColor.copy(alpha = 0.90f),
                    topLeft = Offset(w * 0.60f, h * 0.18f),
                    size = Size(w * 0.28f, h * 0.45f)
                )
                // Shoulders
                drawOval(
                    color = shadowColor,
                    topLeft = Offset(w * 0.48f, h * 0.55f),
                    size = Size(w * 0.50f, h * 0.50f)
                )
            }

            SearchCategoryType.ALTERNATIVE -> {
                // Olive chartreuse with indie band trio
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFD6C832), Color(0xFFC4B526), Color(0xFFA19416)),
                        center = Offset(w * 0.65f, h * 0.40f),
                        radius = w * 0.75f
                    )
                )

                val shadowColor = Color(0xFF3F3700)
                // Center singer with sunglasses
                drawOval(shadowColor, Offset(w * 0.48f, h * 0.22f), Size(w * 0.24f, h * 0.40f))
                drawRoundRect(shadowColor, Offset(w * 0.40f, h * 0.52f), Size(w * 0.40f, h * 0.50f), CornerRadius(12f, 12f))

                // Left bandmate
                drawOval(shadowColor.copy(alpha = 0.85f), Offset(w * 0.28f, h * 0.28f), Size(w * 0.20f, h * 0.35f))
                drawOval(shadowColor.copy(alpha = 0.85f), Offset(w * 0.22f, h * 0.56f), Size(w * 0.30f, h * 0.45f))

                // Right bandmate
                drawOval(shadowColor.copy(alpha = 0.85f), Offset(w * 0.70f, h * 0.26f), Size(w * 0.20f, h * 0.35f))
                drawOval(shadowColor.copy(alpha = 0.85f), Offset(w * 0.64f, h * 0.54f), Size(w * 0.32f, h * 0.45f))
            }

            SearchCategoryType.POP -> {
                // Hot pink with Taylor Swift pop star portrait
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFF5288), Color(0xFFE63B6B), Color(0xFFB51A44)),
                        center = Offset(w * 0.70f, h * 0.40f),
                        radius = w * 0.75f
                    )
                )

                val shadowColor = Color(0xFF4A0517)
                // Updo hair silhouette
                drawOval(shadowColor, Offset(w * 0.60f, h * 0.16f), Size(w * 0.26f, h * 0.38f))
                // Head
                drawOval(shadowColor, Offset(w * 0.62f, h * 0.28f), Size(w * 0.22f, h * 0.32f))
                // Elegant neck and strappy top
                drawRect(shadowColor, Offset(w * 0.68f, h * 0.50f), Size(w * 0.10f, h * 0.20f))
                drawOval(shadowColor, Offset(w * 0.50f, h * 0.60f), Size(w * 0.46f, h * 0.45f))
            }

            SearchCategoryType.HIP_HOP -> {
                // Cornflower electric blue with duo silhouette
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF6B9DF5), Color(0xFF5087EC), Color(0xFF285ECA)),
                        center = Offset(w * 0.65f, h * 0.40f),
                        radius = w * 0.75f
                    )
                )

                val shadowColor = Color(0xFF0F265C)
                // Artist 1 (Braids/hoodie)
                drawOval(shadowColor, Offset(w * 0.42f, h * 0.22f), Size(w * 0.24f, h * 0.38f))
                drawOval(shadowColor, Offset(w * 0.32f, h * 0.50f), Size(w * 0.42f, h * 0.52f))

                // Artist 2 (Right)
                drawOval(shadowColor.copy(alpha = 0.90f), Offset(w * 0.66f, h * 0.20f), Size(w * 0.22f, h * 0.35f))
                drawOval(shadowColor.copy(alpha = 0.90f), Offset(w * 0.58f, h * 0.46f), Size(w * 0.38f, h * 0.56f))
            }

            SearchCategoryType.COUNTRY -> {
                // Amber saddle leather with cowboy hat silhouette
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFF08C35), Color(0xFFD97724), Color(0xFFAA530E)),
                        center = Offset(w * 0.70f, h * 0.40f),
                        radius = w * 0.75f
                    )
                )

                val shadowColor = Color(0xFF471F02)
                // Cowboy hat brim
                drawOval(shadowColor, Offset(w * 0.50f, h * 0.24f), Size(w * 0.44f, h * 0.22f))
                // Hat crown
                drawRoundRect(shadowColor, Offset(w * 0.60f, h * 0.16f), Size(w * 0.24f, h * 0.18f), CornerRadius(10f, 10f))
                // Face with mustache & denim jacket
                drawOval(shadowColor, Offset(w * 0.62f, h * 0.36f), Size(w * 0.20f, h * 0.30f))
                drawOval(shadowColor, Offset(w * 0.48f, h * 0.55f), Size(w * 0.50f, h * 0.48f))
            }

            SearchCategoryType.DANCE -> {
                // Electric neon emerald green with DJ duo
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF2CE06E), Color(0xFF0FB854), Color(0xFF097E38)),
                        center = Offset(w * 0.65f, h * 0.40f),
                        radius = w * 0.75f
                    )
                )

                val shadowColor = Color(0xFF023816)
                // DJ 1 (Buzzcut)
                drawOval(shadowColor, Offset(w * 0.44f, h * 0.26f), Size(w * 0.22f, h * 0.36f))
                drawOval(shadowColor, Offset(w * 0.32f, h * 0.52f), Size(w * 0.40f, h * 0.50f))

                // DJ 2 (Beard)
                drawOval(shadowColor, Offset(w * 0.68f, h * 0.30f), Size(w * 0.24f, h * 0.36f))
                drawOval(shadowColor, Offset(w * 0.58f, h * 0.54f), Size(w * 0.40f, h * 0.48f))
            }

            SearchCategoryType.ROCK -> {
                // Rust red with rock band in black suits
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFF2684D), Color(0xFFDC4E33), Color(0xFFA83018)),
                        center = Offset(w * 0.65f, h * 0.40f),
                        radius = w * 0.75f
                    )
                )

                val shadowColor = Color(0xFF4A1005)
                // Rock band 4 figures
                drawOval(shadowColor, Offset(w * 0.34f, h * 0.32f), Size(w * 0.16f, h * 0.28f))
                drawOval(shadowColor, Offset(w * 0.50f, h * 0.26f), Size(w * 0.18f, h * 0.30f))
                drawOval(shadowColor, Offset(w * 0.68f, h * 0.24f), Size(w * 0.18f, h * 0.32f))
                drawOval(shadowColor, Offset(w * 0.82f, h * 0.30f), Size(w * 0.16f, h * 0.28f))

                // Suits bodies
                drawRect(shadowColor, Offset(w * 0.28f, h * 0.52f), Size(w * 0.72f, h * 0.50f))
            }

            SearchCategoryType.LATIN -> {
                // Fuchsia pink with Latin duo
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFF54B8E), Color(0xFFD83375), Color(0xFFA61A52)),
                        center = Offset(w * 0.65f, h * 0.40f),
                        radius = w * 0.75f
                    )
                )

                val shadowColor = Color(0xFF47031D)
                // Male artist (Left)
                drawOval(shadowColor, Offset(w * 0.38f, h * 0.24f), Size(w * 0.22f, h * 0.34f))
                drawOval(shadowColor, Offset(w * 0.28f, h * 0.48f), Size(w * 0.38f, h * 0.54f))

                // Female artist with long hair (Right)
                drawOval(shadowColor, Offset(w * 0.62f, h * 0.22f), Size(w * 0.26f, h * 0.55f))
            }

            SearchCategoryType.RNB -> {
                // Electric violet purple with Drake looking down
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF9E8CF7), Color(0xFF826FE3), Color(0xFF5945BC)),
                        center = Offset(w * 0.65f, h * 0.40f),
                        radius = w * 0.75f
                    )
                )

                val shadowColor = Color(0xFF221654)
                // Contemplative head looking down
                drawOval(shadowColor, Offset(w * 0.56f, h * 0.24f), Size(w * 0.28f, h * 0.38f))
                // Beard & hair texture
                drawOval(shadowColor, Offset(w * 0.54f, h * 0.20f), Size(w * 0.32f, h * 0.24f))
                // Shoulders
                drawOval(shadowColor, Offset(w * 0.40f, h * 0.54f), Size(w * 0.58f, h * 0.48f))
            }

            SearchCategoryType.CLASSICAL -> {
                // Midnight blue with symphony grand piano
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF386CB5), Color(0xFF28559A), Color(0xFF163769)),
                        center = Offset(w * 0.65f, h * 0.40f),
                        radius = w * 0.75f
                    )
                )
                val shadowColor = Color(0xFF07142A)
                drawOval(shadowColor, Offset(w * 0.45f, h * 0.30f), Size(w * 0.50f, h * 0.60f))
            }

            SearchCategoryType.CHILL -> {
                // Turquoise mist ambient
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF459E9A), Color(0xFF2E7D7A), Color(0xFF1B5452)),
                        center = Offset(w * 0.65f, h * 0.40f),
                        radius = w * 0.75f
                    )
                )
                val shadowColor = Color(0xFF092928)
                drawOval(shadowColor, Offset(w * 0.50f, h * 0.35f), Size(w * 0.45f, h * 0.55f))
            }
        }

        // Bottom dark vignette overlay across ALL cards so category title is razor sharp
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color(0x77000000), Color(0xCC000000)),
                startY = h * 0.45f,
                endY = h
            )
        )
    }
}
