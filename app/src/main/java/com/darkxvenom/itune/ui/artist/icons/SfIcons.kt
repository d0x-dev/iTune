package com.darkxvenom.itune.ui.artist.icons

import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.dp
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFArrowDownCircle
import com.composables.sfsymbols.monochrome.SFArrowUpArrowDown
import com.composables.sfsymbols.monochrome.SFCheckmark
import com.composables.sfsymbols.monochrome.SFCheckmarkCircle
import com.composables.sfsymbols.monochrome.SFChevronBackward
import com.composables.sfsymbols.monochrome.SFChevronRight
import com.composables.sfsymbols.monochrome.SFDotRadiowavesLeftAndRight
import com.composables.sfsymbols.monochrome.SFExclamationmarkCircle
import com.composables.sfsymbols.monochrome.SFHeart
import com.composables.sfsymbols.monochrome.SFHeartFill
import com.composables.sfsymbols.monochrome.SFInfoCircle
import com.composables.sfsymbols.monochrome.SFMusicMicrophone
import com.composables.sfsymbols.monochrome.SFMusicNote
import com.composables.sfsymbols.monochrome.SFPlay
import com.composables.sfsymbols.monochrome.SFShuffle
import com.composables.sfsymbols.monochrome.SFSquareAndArrowUp
import com.composables.sfsymbols.monochrome.SFSquareStack
import com.composables.sfsymbols.monochrome.SFStar
import com.composables.sfsymbols.monochrome.SFStarFill
import com.composables.sfsymbols.monochrome.SFTextBadgePlus
import com.composables.sfsymbols.monochrome.SFTextLineFirstAndArrowtriangleForward
import com.composables.sfsymbols.monochrome.SFTextLineLastAndArrowtriangleForward
import com.composables.sfsymbols.monochrome.SFTrash
import com.composables.sfsymbols.monochrome.SFVideo
import com.composables.sfsymbols.monochrome.SFWaveform
import com.composables.sfsymbols.monochrome.SFXmark

object SfIcons {
    val ArrowBack: ImageVector by lazy { bold(SfSymbols.Monochrome.SFChevronBackward) }
    val Check: ImageVector by lazy { bold(SfSymbols.Monochrome.SFCheckmark) }
    val ChevronRight: ImageVector by lazy { bold(SfSymbols.Monochrome.SFChevronRight) }
    val Download: ImageVector by lazy { bold(SfSymbols.Monochrome.SFArrowDownCircle) }
    val Downloaded: ImageVector by lazy { bold(SfSymbols.Monochrome.SFCheckmarkCircle) }
    val ErrorOutline: ImageVector by lazy { bold(SfSymbols.Monochrome.SFExclamationmarkCircle) }
    val GraphicEq: ImageVector by lazy { bold(SfSymbols.Monochrome.SFWaveform) }
    val MusicNote: ImageVector by lazy { bold(SfSymbols.Monochrome.SFMusicNote) }
    val Share: ImageVector by lazy { bold(SfSymbols.Monochrome.SFSquareAndArrowUp) }

    val AddToPlaylist: ImageVector by lazy { bold(SfSymbols.Monochrome.SFTextBadgePlus) }
    val Album: ImageVector by lazy { bold(SfSymbols.Monochrome.SFSquareStack) }
    val Artist: ImageVector by lazy { bold(SfSymbols.Monochrome.SFMusicMicrophone) }
    val Close: ImageVector by lazy { bold(SfSymbols.Monochrome.SFXmark) }
    val Heart: ImageVector by lazy { bold(SfSymbols.Monochrome.SFHeart) }
    val HeartFill: ImageVector by lazy { bold(SfSymbols.Monochrome.SFHeartFill) }
    val Info: ImageVector by lazy { bold(SfSymbols.Monochrome.SFInfoCircle) }
    val Open: ImageVector by lazy { bold(SfSymbols.Monochrome.SFChevronRight) }
    val Play: ImageVector by lazy { bold(SfSymbols.Monochrome.SFPlay) }
    val PlayLast: ImageVector by lazy { bold(SfSymbols.Monochrome.SFTextLineLastAndArrowtriangleForward) }
    val PlayNext: ImageVector by lazy { bold(SfSymbols.Monochrome.SFTextLineFirstAndArrowtriangleForward) }
    val Radio: ImageVector by lazy { bold(SfSymbols.Monochrome.SFDotRadiowavesLeftAndRight) }
    val Reorder: ImageVector by lazy { bold(SfSymbols.Monochrome.SFArrowUpArrowDown) }
    val Shuffle: ImageVector by lazy { bold(SfSymbols.Monochrome.SFShuffle) }
    val Star: ImageVector by lazy { bold(SfSymbols.Monochrome.SFStar) }
    val StarFill: ImageVector by lazy { bold(SfSymbols.Monochrome.SFStarFill) }
    val Trash: ImageVector by lazy { bold(SfSymbols.Monochrome.SFTrash) }
    val Video: ImageVector by lazy { bold(SfSymbols.Monochrome.SFVideo) }

    internal const val BOLD_STROKE = 0.9f
    internal const val CANVAS = 31f

    internal inline fun canvas(
        name: String,
        width: Float,
        height: Float,
        autoMirror: Boolean = false,
        content: ImageVector.Builder.() -> Unit,
    ): ImageVector {
        val fit = minOf(1f, CANVAS / width, CANVAS / height)
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = CANVAS,
            viewportHeight = CANVAS,
            autoMirror = autoMirror,
        ).apply {
            addGroup(
                translationX = (CANVAS - width * fit) / 2f,
                translationY = (CANVAS - height * fit) / 2f,
                scaleX = fit,
                scaleY = fit,
            )
            content()
            clearGroup()
        }.build()
    }

    private fun bold(regular: ImageVector): ImageVector {
        val pad = BOLD_STROKE / 2f
        return canvas(
            name = regular.name + ".bold",
            width = regular.viewportWidth + pad * 2f,
            height = regular.viewportHeight + pad * 2f,
            autoMirror = regular.autoMirror,
        ) {
            addGroup(translationX = pad, translationY = pad)
            copyInto(this, regular.root)
            clearGroup()
        }
    }

    internal fun copyInto(builder: ImageVector.Builder, group: VectorGroup, extraStroke: Float = BOLD_STROKE) {
        for (node in group) {
            when (node) {
                is VectorGroup -> {
                    builder.addGroup(
                        name = node.name,
                        rotate = node.rotation,
                        pivotX = node.pivotX,
                        pivotY = node.pivotY,
                        scaleX = node.scaleX,
                        scaleY = node.scaleY,
                        translationX = node.translationX,
                        translationY = node.translationY,
                        clipPathData = node.clipPathData,
                    )
                    copyInto(builder, node, extraStroke)
                    builder.clearGroup()
                }
                is VectorPath -> builder.addPath(
                    pathData = node.pathData,
                    pathFillType = node.pathFillType,
                    name = node.name,
                    fill = node.fill,
                    fillAlpha = 1f,
                    stroke = node.stroke ?: node.fill,
                    strokeAlpha = 1f,
                    strokeLineWidth = node.strokeLineWidth + extraStroke,
                    strokeLineCap = node.strokeLineCap,
                    strokeLineJoin = StrokeJoin.Round,
                    strokeLineMiter = node.strokeLineMiter,
                )
            }
        }
    }
}
