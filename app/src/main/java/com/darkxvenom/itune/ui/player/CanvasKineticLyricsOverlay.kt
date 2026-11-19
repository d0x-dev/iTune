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

package com.darkxvenom.itune.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.LyricsLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val CanvasKineticSans = FontFamily(
    Font(R.font.canvas_lyric_sans, FontWeight.Bold),
)

private val CanvasKineticSerif = FontFamily(
    Font(R.font.canvas_lyric_serif, FontWeight.ExtraBold),
)

private val KineticCrimson = Color(0xFFEC1717)
private val KineticRedGlow = Color(0xFFFF1A1A)

private enum class KineticBeatStyle {
    /** Single glowing white uppercase word centered (e.g. BABY, COLD, AND). */
    SINGLE_WHITE,

    /** Giant bold condensed red serif word behind + same word in glowing white centered over it (e.g. LOOK, EMPTY). */
    WHITE_OVER_RED,

    /** Vertical stack of previous + current glowing white words, up to max 3 (e.g. AROUND -> AROUND/SIN -> AROUND/SIN/CITY'S). */
    WHITE_STACK,

    /** Same short word repeated 3 times vertically in bold condensed red serif (e.g. ME / ME / ME). */
    TRIPLE_RED,

    /** Single massive bold condensed red serif impact word (e.g. GONE). */
    GIANT_RED_SOLO,
}

private data class KineticCue(
    val lineIndex: Int,
    val wordIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val word: String,
    val style: KineticBeatStyle,
    /** For WHITE_STACK, holds 1..3 accumulated words (oldest on top, newest below). */
    val stackWords: List<String> = emptyList(),
)

private data class TimedWordSlice(
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

/**
 * Kinetic word-by-word synced lyrics overlay displayed over Canvas when player
 * controls are minimized. Splits each synced lyric line into word-level timings
 * and renders the exact 5 kinetic styles:
 *   1. Single glowing white word
 *   2. Glowing white word centered over giant red condensed serif of the same word
 *   3. Progressive vertical white word stack (1..3 words max, newest appended below)
 *   4. Triple vertical red condensed serif stack of the same word
 *   5. Giant solo red condensed serif impact word
 */
@Composable
fun CanvasKineticLyricsOverlay(
    lyrics: List<LyricsLine>,
    positionMs: Long,
    lyricsOffsetMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    visible: Boolean,
    overlayAlpha: Float,
    translatedLyrics: List<String>? = null,
    modifier: Modifier = Modifier,
) {
    if (overlayAlpha <= 0.005f || lyrics.isEmpty()) return

    // If translated lyrics are available for this song, substitute each non-blank line with its translation
    val effectiveLyrics = remember(lyrics, translatedLyrics) {
        if (!translatedLyrics.isNullOrEmpty()) {
            var nonBlankIdx = 0
            lyrics.map { line ->
                if (line.text.isNotBlank()) {
                    val translatedText = translatedLyrics.getOrNull(nonBlankIdx)?.trim()
                    nonBlankIdx++
                    if (!translatedText.isNullOrEmpty()) {
                        line.copy(text = translatedText)
                    } else {
                        line
                    }
                } else {
                    line
                }
            }
        } else {
            lyrics
        }
    }

    val isSynced = remember(effectiveLyrics) {
        effectiveLyrics.size > 1 && effectiveLyrics.any { it.timeMs > 0L }
    }
    if (!isSynced) return

    // Pre-build deterministic word-level cues for every synced line once per lyrics list.
    val cues = remember(effectiveLyrics, durationMs) {
        buildKineticCues(effectiveLyrics, durationMs)
    }
    if (cues.isEmpty()) return

    // Frame-accurate playback clock anchored to MusicPlayerState.positionMs so rapid
    // word cuts land cleanly at 60fps without waiting for coarser position ticks.
    var livePosMs by remember { mutableLongStateOf(positionMs) }
    LaunchedEffect(positionMs, isPlaying, visible) {
        livePosMs = positionMs
        if (!isPlaying || !visible) return@LaunchedEffect
        val anchorPos = positionMs
        var anchorFrameMs = -1L
        while (true) {
            withFrameMillis { frameMs ->
                if (anchorFrameMs < 0L) anchorFrameMs = frameMs
                livePosMs = anchorPos + (frameMs - anchorFrameMs)
            }
        }
    }

    val syncedPosMs = (livePosMs + lyricsOffsetMs).coerceAtLeast(0L)
    val activeCue = remember(cues, syncedPosMs) {
        findActiveKineticCue(cues, syncedPosMs)
    }

    if (activeCue == null) return

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = overlayAlpha.coerceIn(0f, 1f)
                clip = false
            },
        contentAlignment = Alignment.Center,
    ) {
        // Keep the exact same sizing basis as before, without padding-clipping the rendered glyphs.
        val availableWidthDp = (maxWidth - 40.dp).coerceAtLeast(120.dp)

        when (activeCue.style) {
            KineticBeatStyle.SINGLE_WHITE -> {
                key(activeCue.lineIndex, activeCue.wordIndex, activeCue.style) {
                    SingleWhiteCueView(
                        word = activeCue.word,
                        maxWidthDp = availableWidthDp,
                    )
                }
            }

            KineticBeatStyle.WHITE_OVER_RED -> {
                key(activeCue.lineIndex, activeCue.wordIndex, activeCue.style) {
                    WhiteOverRedCueView(
                        word = activeCue.word,
                        maxWidthDp = availableWidthDp,
                    )
                }
            }

            KineticBeatStyle.WHITE_STACK -> {
                val stackId = activeCue.lineIndex * 1000 + (activeCue.wordIndex - activeCue.stackWords.size + 1)
                key(stackId) {
                    WhiteStackCueView(
                        words = activeCue.stackWords,
                        maxWidthDp = availableWidthDp,
                    )
                }
            }

            KineticBeatStyle.TRIPLE_RED -> {
                key(activeCue.lineIndex, activeCue.wordIndex, activeCue.style) {
                    TripleRedCueView(
                        word = activeCue.word,
                        maxWidthDp = availableWidthDp,
                    )
                }
            }

            KineticBeatStyle.GIANT_RED_SOLO -> {
                key(activeCue.lineIndex, activeCue.wordIndex, activeCue.style) {
                    GiantRedSoloCueView(
                        word = activeCue.word,
                        maxWidthDp = availableWidthDp,
                    )
                }
            }
        }
    }
}

// ── Pattern 1: Single Glowing White Word (BABY / COLD / AND) ──────────────────

@Composable
private fun SingleWhiteCueView(
    word: String,
    maxWidthDp: Dp,
) {
    val scale = remember { Animatable(1.10f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, tween(durationMillis = 95, easing = FastOutSlowInEasing)) }
        launch { alpha.animateTo(1f, tween(durationMillis = 55, easing = FastOutSlowInEasing)) }
    }

    val fontSize = fitSansFontSize(word = word, maxWidthDp = maxWidthDp, baseSp = 56f, minSp = 30f)
    GlowingWhiteKineticText(
        text = word,
        fontSize = fontSize,
        modifier = Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            this.alpha = alpha.value
        },
    )
}

// ── Pattern 2: White Over Giant Red Same-Word (LOOK / EMPTY) ──────────────────

@Composable
private fun WhiteOverRedCueView(
    word: String,
    maxWidthDp: Dp,
) {
    val redScale = remember { Animatable(1.18f) }
    val redAlpha = remember { Animatable(0f) }
    val whiteScale = remember { Animatable(0.92f) }
    val whiteAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { redScale.animateTo(1f, tween(durationMillis = 125, easing = FastOutSlowInEasing)) }
        launch { redAlpha.animateTo(0.96f, tween(durationMillis = 70, easing = FastOutSlowInEasing)) }
        launch { whiteScale.animateTo(1f, tween(durationMillis = 105, easing = FastOutSlowInEasing)) }
        launch { whiteAlpha.animateTo(1f, tween(durationMillis = 65, easing = FastOutSlowInEasing)) }
    }

    val redSp = fitSerifFontSize(word = word, maxWidthDp = maxWidthDp, baseSp = 124f, minSp = 58f)
    val whiteSp = (redSp.value * 0.44f).coerceIn(26f, 52f).sp

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        GlowingRedSerifText(
            text = word,
            fontSize = redSp,
            modifier = Modifier.graphicsLayer {
                scaleX = redScale.value * 0.80f
                scaleY = redScale.value
                alpha = redAlpha.value
            },
        )
        GlowingWhiteKineticText(
            text = word,
            fontSize = whiteSp,
            modifier = Modifier.graphicsLayer {
                scaleX = whiteScale.value
                scaleY = whiteScale.value
                alpha = whiteAlpha.value
            },
        )
    }
}

// ── Pattern 3: Progressive Vertical Stack (AROUND -> AROUND/SIN -> AROUND/SIN/CITY'S) ──

@Composable
private fun WhiteStackCueView(
    words: List<String>,
    maxWidthDp: Dp,
) {
    val longest = remember(words) { words.maxByOrNull { it.length }.orEmpty() }
    val fontSize = fitSansFontSize(word = longest, maxWidthDp = maxWidthDp, baseSp = 54f, minSp = 28f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((-4).dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        words.forEachIndexed { idx, w ->
            key(idx, w) {
                val enterScale = remember { Animatable(1.08f) }
                val enterAlpha = remember { Animatable(0f) }
                val enterOffsetY = remember { Animatable(12f) }
                LaunchedEffect(Unit) {
                    launch { enterScale.animateTo(1f, tween(durationMillis = 95, easing = FastOutSlowInEasing)) }
                    launch { enterAlpha.animateTo(1f, tween(durationMillis = 60, easing = FastOutSlowInEasing)) }
                    launch { enterOffsetY.animateTo(0f, tween(durationMillis = 95, easing = FastOutSlowInEasing)) }
                }
                GlowingWhiteKineticText(
                    text = w,
                    fontSize = fontSize,
                    modifier = Modifier.graphicsLayer {
                        scaleX = enterScale.value
                        scaleY = enterScale.value
                        translationY = enterOffsetY.value
                        alpha = enterAlpha.value
                    },
                )
            }
        }
    }
}

// ── Pattern 4: Triple Vertical Red Stack (ME / ME / ME) ───────────────────────

@Composable
private fun TripleRedCueView(
    word: String,
    maxWidthDp: Dp,
) {
    val fontSize = fitSerifFontSize(word = word, maxWidthDp = maxWidthDp, baseSp = 84f, minSp = 44f)
    val tightSpacing = (-(fontSize.value * 0.20f)).dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(tightSpacing),
        modifier = Modifier.fillMaxWidth(),
    ) {
        for (row in 0 until 3) {
            val scale = remember(row) { Animatable(1.14f) }
            val alpha = remember(row) { Animatable(0f) }
            LaunchedEffect(row) {
                if (row > 0) delay(row * 45L)
                launch { scale.animateTo(1f, tween(durationMillis = 100, easing = FastOutSlowInEasing)) }
                launch { alpha.animateTo(1f, tween(durationMillis = 60, easing = FastOutSlowInEasing)) }
            }
            GlowingRedSerifText(
                text = word,
                fontSize = fontSize,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale.value * 0.82f
                    scaleY = scale.value
                    this.alpha = alpha.value
                },
            )
        }
    }
}

// ── Pattern 5: Giant Red Serif Solo Impact Word (GONE) ────────────────────────

@Composable
private fun GiantRedSoloCueView(
    word: String,
    maxWidthDp: Dp,
) {
    val scale = remember { Animatable(1.18f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, tween(durationMillis = 130, easing = FastOutSlowInEasing)) }
        launch { alpha.animateTo(1f, tween(durationMillis = 70, easing = FastOutSlowInEasing)) }
    }

    val redSp = fitSerifFontSize(word = word, maxWidthDp = maxWidthDp, baseSp = 114f, minSp = 54f)
    GlowingRedSerifText(
        text = word,
        fontSize = redSp,
        modifier = Modifier.graphicsLayer {
            scaleX = scale.value * 0.82f
            scaleY = scale.value
            this.alpha = alpha.value
        },
    )
}

// ── Luminous Multi-Pass Glowing Text Renderers ────────────────────────────────
// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

@Composable
private fun GlowingWhiteKineticText(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val baseStyle = TextStyle(
        fontFamily = CanvasKineticSans,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        lineHeight = (fontSize.value * 1.02f).sp,
        letterSpacing = (fontSize.value * 0.035f).sp,
        textAlign = TextAlign.Center,
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .wrapContentWidth(unbounded = true, align = Alignment.CenterHorizontally)
            .then(modifier),
    ) {
        // Outer luminous atmospheric halo bloom
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.48f),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            style = baseStyle.copy(
                shadow = Shadow(
                    color = Color.White.copy(alpha = 0.88f),
                    offset = Offset.Zero,
                    blurRadius = 34f,
                ),
            ),
        )
        // Tight neon white rim glow
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            style = baseStyle.copy(
                shadow = Shadow(
                    color = Color.White,
                    offset = Offset.Zero,
                    blurRadius = 13f,
                ),
            ),
        )
        // Crisp bright white foreground core with subtle contrast anchor over bright Canvas frames
        Text(
            text = text,
            color = Color.White,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            style = baseStyle.copy(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.32f),
                    offset = Offset(0f, 1.5f),
                    blurRadius = 4f,
                ),
            ),
        )
    }
}

@Composable
private fun GlowingRedSerifText(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val baseStyle = TextStyle(
        fontFamily = CanvasKineticSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = fontSize,
        lineHeight = (fontSize.value * 0.92f).sp,
        letterSpacing = (-fontSize.value * 0.02f).sp,
        textAlign = TextAlign.Center,
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .wrapContentWidth(unbounded = true, align = Alignment.CenterHorizontally)
            .then(modifier),
    ) {
        // Wide crimson neon bloom
        Text(
            text = text,
            color = KineticRedGlow.copy(alpha = 0.50f),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            style = baseStyle.copy(
                shadow = Shadow(
                    color = KineticRedGlow.copy(alpha = 0.80f),
                    offset = Offset.Zero,
                    blurRadius = 32f,
                ),
            ),
        )
        // Tight rich red editorial serif core
        Text(
            text = text,
            color = KineticCrimson,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            style = baseStyle.copy(
                shadow = Shadow(
                    color = Color(0xFFFF0000).copy(alpha = 0.72f),
                    offset = Offset.Zero,
                    blurRadius = 11f,
                ),
            ),
        )
    }
}

// ── Auto-Fit Font Sizing (Guarantees Single-Line Fit Without Clipping) ────────

private fun fitSansFontSize(
    word: String,
    maxWidthDp: Dp,
    baseSp: Float,
    minSp: Float,
): TextUnit {
    val chars = word.length.coerceAtLeast(1)
    // Barlow Condensed Bold average glyph advance is ~0.52 * fontSize
    val maxForWidth = (maxWidthDp.value * 0.90f) / (chars * 0.54f)
    return minOf(baseSp, maxForWidth).coerceIn(minSp, baseSp).sp
}

private fun fitSerifFontSize(
    word: String,
    maxWidthDp: Dp,
    baseSp: Float,
    minSp: Float,
): TextUnit {
    val chars = word.length.coerceAtLeast(1)
    // Playfair Display ExtraBold condensed at scaleX=0.80 has effective advance ~0.58 * fontSize
    val maxForWidth = (maxWidthDp.value * 0.92f) / (chars * 0.58f)
    return minOf(baseSp, maxForWidth).coerceIn(minSp, baseSp).sp
}

// ── Synced Line -> Word Cutting, Time Division & Choreography Planner ─────────

private fun estimateNormalMsPerWord(lyrics: List<LyricsLine>): Double {
    val samples = mutableListOf<Double>()
    for (i in 0 until lyrics.lastIndex) {
        val line = lyrics[i]
        val raw = line.text.trim()
        if (raw.isBlank() || isInstrumentalMarker(raw)) continue
        val nextStart = lyrics[i + 1].timeMs
        val gap = nextStart - line.timeMs
        // Only sample normal consecutive sung lines (skip long instrumental gaps > 6s)
        if (gap !in 600L..6_000L) continue
        val wc = raw.split(Regex("\\s+")).count { cleanDisplayWord(it).isNotBlank() }
        if (wc <= 0) continue
        val perWord = gap.toDouble() / wc
        if (perWord in 140.0..950.0) {
            samples += perWord
        }
    }
    if (samples.isEmpty()) return 400.0
    samples.sort()
    val median = samples[samples.size / 2]
    return median.coerceIn(240.0, 620.0)
}

/**
 * Detects whether [line.words] carries genuine provider-supplied end timing (e.g. TTML / Apple Music
 * syllable sync) rather than `LyricsHelper.parseLrc`'s synthetic equal-split fallback (which blindly
 * divides `(nextTime - curTime).coerceIn(1200L, 8000L)` across words).
 */
private fun realProviderLineEndMs(
    line: LyricsLine,
    nextStartMs: Long?,
): Long? {
    val wt = line.words
    if (wt.isEmpty()) return null
    val firstStart = wt.first().startMs
    val lastEnd = wt.last().endMs
    if (lastEnd <= line.timeMs + 250L) return null

    // Synthetic LRC fallback always starts at exact line.timeMs and fills exactly
    // (nextStart - line.timeMs).coerceIn(1200L, 8000L) (or +4000L for the final line)
    // with identical integer wordDuration on every word.
    val rawGap = nextStartMs?.let { it - line.timeMs }
    val syntheticDur = rawGap?.coerceIn(1200L, 8000L) ?: 4000L
    val expectedSyntheticStep = syntheticDur / wt.size.coerceAtLeast(1)
    val looksSynthetic = firstStart == line.timeMs &&
        wt.all { (it.endMs - it.startMs) == expectedSyntheticStep }
    if (looksSynthetic) return null

    // Genuine provider timing: trust its actual end time (clamped before nextStartMs if present).
    return if (nextStartMs != null && nextStartMs > line.timeMs) {
        minOf(lastEnd, nextStartMs)
    } else {
        lastEnd
    }
}

private fun buildKineticCues(
    lyrics: List<LyricsLine>,
    durationMs: Long,
): List<KineticCue> {
    val cues = mutableListOf<KineticCue>()
    val normalMsPerWord = estimateNormalMsPerWord(lyrics)

    for (lineIdx in lyrics.indices) {
        val line = lyrics[lineIdx]
        val rawText = line.text.trim()
        if (rawText.isBlank()) continue
        if (isInstrumentalMarker(rawText)) continue

        val words = rawText.split(Regex("\\s+"))
            .map(::cleanDisplayWord)
            .filter { it.isNotBlank() }
        if (words.isEmpty()) continue

        val naturalDurationMs = (words.size * normalMsPerWord).toLong().coerceIn(800L, 5_000L)
        val maxNonGapDurationMs = (naturalDurationMs * 1.35).toLong()

        val nextStart = lyrics.getOrNull(lineIdx + 1)?.timeMs
        val providerEnd = realProviderLineEndMs(line, nextStart)

        val lineEndMs = when {
            providerEnd != null && providerEnd > line.timeMs + 250L -> providerEnd
            nextStart != null && nextStart > line.timeMs -> {
                val rawGap = nextStart - line.timeMs
                // If there is a long empty/instrumental gap before the next line (e.g. 00:30 -> 00:40),
                // keep the exact normal singing speed instead of stretching this line across the gap.
                if (rawGap > maxNonGapDurationMs) {
                    line.timeMs + naturalDurationMs
                } else {
                    nextStart
                }
            }
            durationMs > line.timeMs -> {
                minOf(durationMs, line.timeMs + naturalDurationMs)
            }
            else -> line.timeMs + naturalDurationMs
        }

        val slices = divideLineEqually(words, line.timeMs, lineEndMs)
        if (slices.isEmpty()) continue

        cues += assignLineChoreography(lineIdx, slices)
    }
    return cues
}

/**
 * Divides the synced line's `[lineStartMs, lineEndMs]` window strictly equally across every word
 * so every word (first, middle, and last) gets the exact same time — never blinking the last word.
 */
private fun divideLineEqually(
    words: List<String>,
    lineStartMs: Long,
    lineEndMs: Long,
): List<TimedWordSlice> {
    if (words.isEmpty()) return emptyList()
    val safeEnd = lineEndMs.coerceAtLeast(lineStartMs + words.size * 150L)
    val totalDurationMs = safeEnd - lineStartMs
    val stepMs = totalDurationMs.toDouble() / words.size

    return words.mapIndexed { idx, word ->
        val start = (lineStartMs + stepMs * idx).toLong()
        val end = if (idx == words.lastIndex) {
            safeEnd
        } else {
            (lineStartMs + stepMs * (idx + 1)).toLong().coerceAtLeast(start + 80L)
        }
        TimedWordSlice(
            text = word,
            startMs = start,
            endMs = end,
        )
    }
}

/**
 * Deterministic choreography matching the exact 5 reference styles:
 * - Single glowing white words (max 1)
 * - Progressive vertical stacks of old + new white words (max 3, newest appended below)
 * - White-over-giant-red same-word emphasis
 * - Triple vertical red stack on punchy short words (e.g. ME / ME / ME)
 * - Giant red solo serif on dramatic line-ending emphasis words (e.g. GONE)
 */
private fun assignLineChoreography(
    lineIndex: Int,
    words: List<TimedWordSlice>,
): List<KineticCue> {
    val n = words.size
    if (n == 0) return emptyList()

    // 1-word line (e.g. "BABY" or "GONE" or "ME")
    if (n == 1) {
        val w = words[0]
        val style = when {
            w.text.length <= 3 && lineIndex % 3 == 1 -> KineticBeatStyle.TRIPLE_RED
            lineIndex % 4 == 3 -> KineticBeatStyle.GIANT_RED_SOLO
            lineIndex % 5 == 4 -> KineticBeatStyle.WHITE_OVER_RED
            else -> KineticBeatStyle.SINGLE_WHITE
        }
        return listOf(KineticCue(lineIndex, 0, w.startMs, w.endMs, w.text, style))
    }

    // 2-word line
    if (n == 2) {
        val w0 = words[0]
        val w1 = words[1]
        return if (lineIndex % 2 == 0) {
            listOf(
                KineticCue(lineIndex, 0, w0.startMs, w0.endMs, w0.text, KineticBeatStyle.SINGLE_WHITE),
                KineticCue(
                    lineIndex, 1, w1.startMs, w1.endMs, w1.text,
                    if (w1.text.length <= 4 && lineIndex % 4 == 2) KineticBeatStyle.TRIPLE_RED
                    else KineticBeatStyle.WHITE_OVER_RED,
                ),
            )
        } else {
            listOf(
                KineticCue(lineIndex, 0, w0.startMs, w0.endMs, w0.text, KineticBeatStyle.WHITE_STACK, listOf(w0.text)),
                KineticCue(lineIndex, 1, w1.startMs, w1.endMs, w1.text, KineticBeatStyle.WHITE_STACK, listOf(w0.text, w1.text)),
            )
        }
    }

    // Pick optional early red pivot word on long lines (e.g. "LOOK" in "I look around and Sin City's cold and empty")
    val earlyRedIdx = if (n >= 6) {
        (1..2).firstOrNull { words[it].text.length >= 4 } ?: -1
    } else {
        -1
    }

    // Pick optional 3-word vertical stack window (max 3 consecutive words before the last word)
    val stackStartIdx = when {
        n >= 7 && earlyRedIdx >= 1 -> {
            // Place a 3-word stack right after the early pivot (e.g. AROUND / SIN / CITY'S)
            val candidate = earlyRedIdx + 1
            if (candidate + 2 < n - 1) candidate else -1
        }
        n >= 5 && lineIndex % 2 == 0 -> {
            val candidate = (n - 4).coerceAtLeast(1)
            if (candidate + 2 < n) candidate else -1
        }
        n == 4 && lineIndex % 3 == 0 -> 0 // 3 words stacked, 4th word red punch
        else -> -1
    }
    val stackEndIdx = if (stackStartIdx >= 0) minOf(stackStartIdx + 2, n - 2) else -1

    val lastWord = words.last().text
    val finalRedStyle = when {
        lastWord.length <= 4 && lineIndex % 3 == 1 -> KineticBeatStyle.TRIPLE_RED
        lineIndex % 2 == 0 -> KineticBeatStyle.WHITE_OVER_RED
        else -> KineticBeatStyle.GIANT_RED_SOLO
    }

    val out = ArrayList<KineticCue>(n)
    val activeStack = mutableListOf<String>()

    for (i in 0 until n) {
        val w = words[i]
        when {
            i == n - 1 -> {
                activeStack.clear()
                out += KineticCue(lineIndex, i, w.startMs, w.endMs, w.text, finalRedStyle)
            }
            i == earlyRedIdx -> {
                activeStack.clear()
                out += KineticCue(lineIndex, i, w.startMs, w.endMs, w.text, KineticBeatStyle.WHITE_OVER_RED)
            }
            stackStartIdx >= 0 && i in stackStartIdx..stackEndIdx -> {
                if (activeStack.size >= 3) activeStack.clear()
                activeStack += w.text
                out += KineticCue(
                    lineIndex = lineIndex,
                    wordIndex = i,
                    startMs = w.startMs,
                    endMs = w.endMs,
                    word = w.text,
                    style = KineticBeatStyle.WHITE_STACK,
                    stackWords = activeStack.toList(),
                )
            }
            else -> {
                activeStack.clear()
                out += KineticCue(lineIndex, i, w.startMs, w.endMs, w.text, KineticBeatStyle.SINGLE_WHITE)
            }
        }
    }
    return out
}

private fun findActiveKineticCue(
    cues: List<KineticCue>,
    positionMs: Long,
): KineticCue? {
    var lo = 0
    var hi = cues.lastIndex
    var candidateIdx = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (cues[mid].startMs <= positionMs) {
            candidateIdx = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    if (candidateIdx < 0) return null
    val cue = cues[candidateIdx]
    return if (positionMs <= cue.endMs) cue else null
}

private fun cleanDisplayWord(raw: String): String {
    val trimmed = raw.trim()
        .trim(',', '.', ';', ':', '!', '?', '"', '(', ')', '[', ']', '“', '”', '♪', '♫', '-', '—')
        .replace('’', '\'')
        .trim()
    return trimmed.uppercase(Locale.ROOT)
}

private fun isInstrumentalMarker(text: String): Boolean {
    val t = text.trim()
    return t == "♪" || t == "♫" || t == "•••" || t == "..." ||
        t.equals("[instrumental]", ignoreCase = true) ||
        t.equals("(instrumental)", ignoreCase = true) ||
        t.equals("instrumental", ignoreCase = true)
}
