package com.neonote.engine

import com.neonote.model.BlockFormula
import com.neonote.model.BlockImage
import com.neonote.model.BlockNode
import com.neonote.model.CanvasRect
import com.neonote.model.CanvasSize
import com.neonote.model.InlineFormula
import com.neonote.model.InlineImage
import com.neonote.model.InlineLineBreak
import com.neonote.model.InlineText
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox
import com.neonote.model.TableNode
import kotlin.math.max

/**
 * Deterministic document-space layout for rich content boxes.
 *
 * The box width is owned by the canvas object. This engine consumes that
 * available width and reports the content-driven height plus lightweight block
 * and line metadata that selection and persistence code can use without
 * depending on Android text measurement.
 */
public class RichContentLayoutEngine(
    private val metrics: RichContentLayoutMetrics = RichContentLayoutMetrics(),
) {
    public fun layout(content: RichContent, availableWidth: Float): RichContentLayoutResult {
        val safeWidth = availableWidth.coerceAtLeast(metrics.minimumMeasuredWidth)
        val contentWidth = (safeWidth - metrics.horizontalPadding * 2f).coerceAtLeast(metrics.minimumContentWidth)
        val lineCapacity = max(1, (contentWidth / metrics.characterWidth).toInt())
        val blockRects = mutableListOf<RichContentBlockRect>()
        val lineRects = mutableListOf<RichContentLineRect>()
        var top = metrics.verticalPadding

        val blocks = if (content.blocks.isEmpty()) listOf(ParagraphNode()) else content.blocks
        blocks.forEachIndexed { index, block ->
            val blockTop = top
            val blockLines = measureBlockLines(
                block = block,
                blockIndex = index,
                contentLeft = metrics.horizontalPadding,
                contentWidth = contentWidth,
                lineCapacity = lineCapacity,
                lineTop = top,
            )
            lineRects += blockLines
            val blockHeight = if (blockLines.isEmpty()) metrics.lineHeight else blockLines.sumOfFloat { it.rect.bottom - it.rect.top }
            val blockBottom = blockTop + blockHeight
            blockRects += RichContentBlockRect(
                blockIndex = index,
                rect = CanvasRect(
                    left = metrics.horizontalPadding,
                    top = blockTop,
                    right = metrics.horizontalPadding + contentWidth,
                    bottom = blockBottom,
                ),
            )
            top = blockBottom
            if (index != blocks.lastIndex) top += metrics.blockSpacing
        }

        val measuredHeight = (top + metrics.verticalPadding).coerceAtLeast(metrics.minimumMeasuredHeight)
        return RichContentLayoutResult(
            measuredSize = CanvasSize(width = safeWidth, height = measuredHeight),
            blockRects = blockRects,
            lineRects = lineRects,
        )
    }

    private fun measureBlockLines(
        block: BlockNode,
        blockIndex: Int,
        contentLeft: Float,
        contentWidth: Float,
        lineCapacity: Int,
        lineTop: Float,
    ): List<RichContentLineRect> = when (block) {
        is ParagraphNode -> measureParagraphLines(block, blockIndex, contentLeft, contentWidth, lineCapacity, lineTop)
        is BlockFormula -> listOf(blockLine(blockIndex, 0, contentLeft, contentWidth, lineTop, metrics.blockFormulaHeight, 0, block.expression.length))
        is BlockImage -> listOf(blockLine(blockIndex, 0, contentLeft, contentWidth, lineTop, metrics.blockImageHeight, 0, 0))
        is TableNode -> listOf(blockLine(blockIndex, 0, contentLeft, contentWidth, lineTop, metrics.tablePlaceholderHeight, 0, 0))
    }

    private fun measureParagraphLines(
        paragraph: ParagraphNode,
        blockIndex: Int,
        contentLeft: Float,
        contentWidth: Float,
        lineCapacity: Int,
        lineTop: Float,
    ): List<RichContentLineRect> {
        val hardLines = paragraph.inlineTextForMeasurement().split('\n')
        val lines = mutableListOf<RichContentLineRect>()
        var top = lineTop
        var paragraphOffset = 0
        hardLines.forEach { hardLine ->
            if (hardLine.isEmpty()) {
                lines += blockLine(blockIndex, lines.size, contentLeft, contentWidth, top, metrics.lineHeight, paragraphOffset, paragraphOffset)
                top += metrics.lineHeight
                paragraphOffset += 1
                return@forEach
            }
            var localOffset = 0
            hardLine.chunked(lineCapacity).forEach { visualLine ->
                val start = paragraphOffset + localOffset
                val end = start + visualLine.length
                val lineWidth = (visualLine.length * metrics.characterWidth).coerceAtMost(contentWidth)
                lines += RichContentLineRect(
                    blockIndex = blockIndex,
                    lineIndex = lines.size,
                    inlineStart = start,
                    inlineEnd = end,
                    rect = CanvasRect(
                        left = contentLeft,
                        top = top,
                        right = contentLeft + lineWidth,
                        bottom = top + metrics.lineHeight,
                    ),
                )
                top += metrics.lineHeight
                localOffset += visualLine.length
            }
            paragraphOffset += hardLine.length + 1
        }
        return lines.ifEmpty {
            listOf(blockLine(blockIndex, 0, contentLeft, contentWidth, lineTop, metrics.lineHeight, 0, 0))
        }
    }

    private fun blockLine(
        blockIndex: Int,
        lineIndex: Int,
        left: Float,
        width: Float,
        top: Float,
        height: Float,
        inlineStart: Int,
        inlineEnd: Int,
    ): RichContentLineRect = RichContentLineRect(
        blockIndex = blockIndex,
        lineIndex = lineIndex,
        inlineStart = inlineStart,
        inlineEnd = inlineEnd,
        rect = CanvasRect(left = left, top = top, right = left + width, bottom = top + height),
    )

    private fun ParagraphNode.inlineTextForMeasurement(): String = inlines.joinToString("") { inline ->
        when (inline) {
            is InlineText -> inline.text
            InlineLineBreak -> "\n"
            is InlineFormula -> inline.expression.ifBlank { "□" }
            is InlineImage -> inline.altText?.takeIf { it.isNotBlank() } ?: "□"
        }
    }
}

public data class RichContentLayoutMetrics(
    val characterWidth: Float = 8f,
    val lineHeight: Float = 24f,
    val horizontalPadding: Float = 16f,
    val verticalPadding: Float = 16f,
    val blockSpacing: Float = 4f,
    val blockFormulaHeight: Float = 32f,
    val blockImageHeight: Float = 96f,
    val tablePlaceholderHeight: Float = 64f,
    val minimumMeasuredWidth: Float = 1f,
    val minimumContentWidth: Float = 1f,
    val minimumMeasuredHeight: Float = 56f,
)

public data class RichContentLayoutResult(
    val measuredSize: CanvasSize,
    val blockRects: List<RichContentBlockRect>,
    val lineRects: List<RichContentLineRect>,
)

public data class RichContentBlockRect(
    val blockIndex: Int,
    val rect: CanvasRect,
)

public data class RichContentLineRect(
    val blockIndex: Int,
    val lineIndex: Int,
    val inlineStart: Int,
    val inlineEnd: Int,
    val rect: CanvasRect,
)

private inline fun <T> Iterable<T>.sumOfFloat(selector: (T) -> Float): Float {
    var sum = 0f
    for (item in this) sum += selector(item)
    return sum
}
