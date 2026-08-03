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
import com.neonote.model.TableCell
import com.neonote.model.TableColumnWidthMode
import com.neonote.model.TableNode
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

private const val MaxNestedTableDepth = 8

/**
 * Deterministic document-space layout for rich content boxes.
 *
 * The box width is owned by the canvas object. This engine consumes that
 * available width and reports the content-driven height plus lightweight block
 * and line metadata that selection and persistence code can use without
 * depending on Android text measurement.
 *
 * Table cells are measured recursively from their nested [RichContent]. This is
 * the layout boundary that makes an inner paragraph, formula, image, or nested
 * table grow its containing row, outer tables, and finally the canvas box.
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
        val tableLayouts = mutableListOf<RichContentTableLayout>()
        var top = metrics.verticalPadding

        val blocks = if (content.blocks.isEmpty()) listOf(ParagraphNode()) else content.blocks
        blocks.forEachIndexed { index, block ->
            val blockTop = top
            val tableLayout = (block as? TableNode)?.let { table ->
                measureTableLayout(
                    table = table,
                    blockIndex = index,
                    contentLeft = metrics.horizontalPadding,
                    contentWidth = contentWidth,
                    tableTop = top,
                    nestingDepth = 0,
                )
            }
            if (tableLayout != null) tableLayouts += tableLayout
            val blockLines = measureBlockLines(
                block = block,
                blockIndex = index,
                contentLeft = metrics.horizontalPadding,
                contentWidth = contentWidth,
                lineCapacity = lineCapacity,
                lineTop = top,
                tableLayout = tableLayout,
            )
            lineRects += blockLines
            val blockHeight = tableLayout?.tableHeight
                ?: if (blockLines.isEmpty()) metrics.lineHeight else blockLines.sumOfFloat { it.rect.bottom - it.rect.top }
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
            tableLayouts = tableLayouts,
        )
    }

    private fun measureBlockLines(
        block: BlockNode,
        blockIndex: Int,
        contentLeft: Float,
        contentWidth: Float,
        lineCapacity: Int,
        lineTop: Float,
        tableLayout: RichContentTableLayout? = null,
    ): List<RichContentLineRect> = when (block) {
        is ParagraphNode -> measureParagraphLines(block, blockIndex, contentLeft, contentWidth, lineCapacity, lineTop)
        is BlockFormula -> listOf(
            blockLine(
                blockIndex,
                0,
                contentLeft,
                contentWidth,
                lineTop,
                metrics.blockFormulaHeight,
                0,
                block.expression.length,
            ),
        )
        is BlockImage -> listOf(
            blockLine(
                blockIndex,
                0,
                contentLeft,
                contentWidth,
                lineTop,
                metrics.blockImageHeight,
                0,
                0,
            ),
        )
        is TableNode -> listOf(
            blockLine(
                blockIndex,
                0,
                contentLeft,
                contentWidth,
                lineTop,
                tableLayout?.tableHeight ?: metrics.tablePreviewHeight(block),
                0,
                0,
            ),
        )
    }

    private fun measureTableLayout(
        table: TableNode,
        blockIndex: Int,
        contentLeft: Float,
        contentWidth: Float,
        tableTop: Float,
        nestingDepth: Int,
    ): RichContentTableLayout {
        val rowCount = table.rows.size.coerceAtLeast(metrics.tableMinimumLayoutRows)
        val columnCount = table.columnCount().coerceAtLeast(metrics.tableMinimumLayoutColumns)
        val preferredWidths = (0 until columnCount).map { columnIndex ->
            val policy = table.columnPolicies.getOrNull(columnIndex)
            val measuredPreferredWidth = table.rows.maxOfOrNull { row ->
                row.getOrNull(columnIndex)?.preferredCellWidth(nestingDepth) ?: metrics.tableMinCellWidth
            } ?: metrics.tableMinCellWidth

            val policyMin = policy?.minWidth?.takeIf { it > 0f } ?: metrics.tableMinCellWidth
            val policyMax = policy?.maxWidth?.takeIf { it > 0f } ?: metrics.tableMaxColumnWidth
            val autoPreferred = policy?.preferredWidth?.takeIf { it > 0f } ?: measuredPreferredWidth
            val preferred = if (policy?.mode == TableColumnWidthMode.Manual) {
                policy.manualWidth?.takeIf { it > 0f } ?: autoPreferred
            } else {
                autoPreferred
            }
            preferred.coerceIn(policyMin, policyMax)
        }
        val minWidths = (0 until columnCount).map { columnIndex ->
            val policy = table.columnPolicies.getOrNull(columnIndex)
            policy?.minWidth?.takeIf { it > 0f } ?: metrics.tableMinCellWidth
        }
        val columnWidths = fitColumnWidthsToContentWidth(
            preferredWidths = preferredWidths,
            minWidths = minWidths,
            contentWidth = contentWidth,
        )

        val rowHeights = (0 until rowCount).map { rowIndex ->
            (0 until columnCount).maxOfOrNull { columnIndex ->
                val cell = table.rows.getOrNull(rowIndex)?.getOrNull(columnIndex) ?: TableCell()
                cell.measuredCellHeight(columnWidths[columnIndex], nestingDepth)
            } ?: metrics.tableMinCellHeight
        }

        val cellRects = mutableListOf<List<CanvasRect>>()
        var rowTop = tableTop
        rowHeights.forEach { rowHeight ->
            var columnLeft = contentLeft
            val rowRects = columnWidths.map { columnWidth ->
                val rect = CanvasRect(
                    left = columnLeft,
                    top = rowTop,
                    right = columnLeft + columnWidth,
                    bottom = rowTop + rowHeight,
                )
                columnLeft += columnWidth
                rect
            }
            cellRects += rowRects
            rowTop += rowHeight
        }

        val tableHeight = rowHeights.sum()
        return RichContentTableLayout(
            blockIndex = blockIndex,
            columnWidths = columnWidths,
            rowHeights = rowHeights,
            tableWidth = columnWidths.sum(),
            tableHeight = tableHeight,
            cellRects = cellRects,
        )
    }

    private fun fitColumnWidthsToContentWidth(
        preferredWidths: List<Float>,
        minWidths: List<Float>,
        contentWidth: Float,
    ): List<Float> {
        if (preferredWidths.isEmpty()) return emptyList()
        val preferredTotal = preferredWidths.sum()
        if (preferredTotal <= contentWidth) {
            val extra = (contentWidth - preferredTotal) / preferredWidths.size
            return preferredWidths.map { it + extra }
        }

        val minTotal = minWidths.sum()
        if (minTotal >= contentWidth) {
            val fallbackWidth = contentWidth / preferredWidths.size
            return minWidths.map { minWidth -> min(minWidth, fallbackWidth) }
        }

        val shrinkableTotal = preferredWidths.zip(minWidths).sumOfFloat { (preferred, minWidth) ->
            (preferred - minWidth).coerceAtLeast(0f)
        }
        if (shrinkableTotal <= 0f) {
            return List(preferredWidths.size) { contentWidth / preferredWidths.size }
        }

        val shrinkNeeded = preferredTotal - contentWidth
        return preferredWidths.zip(minWidths).map { (preferred, minWidth) ->
            val shrinkable = (preferred - minWidth).coerceAtLeast(0f)
            preferred - shrinkNeeded * (shrinkable / shrinkableTotal)
        }
    }

    private fun TableNode.columnCount(): Int = max(
        rows.maxOfOrNull { it.size } ?: 0,
        columnPolicies.size,
    )

    private fun TableCell.preferredCellWidth(nestingDepth: Int): Float =
        (content.preferredContentWidth(nestingDepth) + metrics.tableCellHorizontalPadding * 2f)
            .coerceAtLeast(metrics.tableMinCellWidth)

    private fun RichContent.preferredContentWidth(nestingDepth: Int): Float {
        val measuredBlocks = if (blocks.isEmpty()) listOf<BlockNode>(ParagraphNode()) else blocks
        return measuredBlocks.maxOfOrNull { block ->
            when (block) {
                is ParagraphNode -> {
                    val maxLineLength = block.inlineTextForMeasurement()
                        .split('\n')
                        .maxOfOrNull { it.length }
                        ?: 0
                    maxLineLength * metrics.characterWidth
                }
                is BlockFormula -> block.expression.length.coerceAtLeast(1) * metrics.characterWidth
                is BlockImage -> {
                    val labelLength = block.altText?.takeIf { it.isNotBlank() }?.length ?: 1
                    max(metrics.tableMinCellWidth, labelLength * metrics.characterWidth)
                }
                is TableNode -> {
                    if (nestingDepth >= MaxNestedTableDepth) {
                        metrics.tableMinCellWidth
                    } else {
                        block.preferredTableWidth(nestingDepth + 1)
                    }
                }
            }
        }?.coerceAtLeast(metrics.characterWidth) ?: metrics.characterWidth
    }

    private fun TableNode.preferredTableWidth(nestingDepth: Int): Float {
        val columnCount = columnCount().coerceAtLeast(metrics.tableMinimumLayoutColumns)
        return (0 until columnCount).sumOfFloat { columnIndex ->
            val policy = columnPolicies.getOrNull(columnIndex)
            val measuredPreferredWidth = rows.maxOfOrNull { row ->
                row.getOrNull(columnIndex)?.preferredCellWidth(nestingDepth) ?: metrics.tableMinCellWidth
            } ?: metrics.tableMinCellWidth
            val policyMin = policy?.minWidth?.takeIf { it > 0f } ?: metrics.tableMinCellWidth
            val policyMax = policy?.maxWidth?.takeIf { it > 0f } ?: metrics.tableMaxColumnWidth
            val autoPreferred = policy?.preferredWidth?.takeIf { it > 0f } ?: measuredPreferredWidth
            val preferred = if (policy?.mode == TableColumnWidthMode.Manual) {
                policy.manualWidth?.takeIf { it > 0f } ?: autoPreferred
            } else {
                autoPreferred
            }
            preferred.coerceIn(policyMin, policyMax)
        }
    }

    private fun TableCell.measuredCellHeight(columnWidth: Float, nestingDepth: Int): Float {
        val contentWidth = (columnWidth - metrics.tableCellHorizontalPadding * 2f)
            .coerceAtLeast(metrics.characterWidth)
        val contentHeight = content.measuredNestedContentHeight(
            availableWidth = contentWidth,
            nestingDepth = nestingDepth,
        )
        return (contentHeight + metrics.tableCellVerticalPadding * 2f)
            .coerceAtLeast(metrics.tableMinCellHeight)
    }

    private fun RichContent.measuredNestedContentHeight(
        availableWidth: Float,
        nestingDepth: Int,
    ): Float {
        val measuredBlocks = if (blocks.isEmpty()) listOf<BlockNode>(ParagraphNode()) else blocks
        return measuredBlocks.map { block ->
            when (block) {
                is ParagraphNode -> block.visualLineCount(availableWidth) * metrics.lineHeight
                is BlockFormula -> metrics.blockFormulaHeight
                is BlockImage -> metrics.blockImageHeight
                is TableNode -> {
                    if (nestingDepth >= MaxNestedTableDepth) {
                        metrics.tableMinCellHeight
                    } else {
                        measureTableLayout(
                            table = block,
                            blockIndex = 0,
                            contentLeft = 0f,
                            contentWidth = availableWidth,
                            tableTop = 0f,
                            nestingDepth = nestingDepth + 1,
                        ).tableHeight
                    }
                }
            }
        }.sum() + metrics.blockSpacing * (measuredBlocks.size - 1).coerceAtLeast(0)
    }

    private fun ParagraphNode.visualLineCount(availableWidth: Float): Int {
        val lineCapacity = max(1, (availableWidth / metrics.characterWidth).toInt())
        return inlineTextForMeasurement()
            .split('\n')
            .sumOf { hardLine ->
                if (hardLine.isEmpty()) {
                    1
                } else {
                    ceil(hardLine.length / lineCapacity.toFloat()).toInt().coerceAtLeast(1)
                }
            }
            .coerceAtLeast(1)
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
                lines += blockLine(
                    blockIndex,
                    lines.size,
                    contentLeft,
                    contentWidth,
                    top,
                    metrics.lineHeight,
                    paragraphOffset,
                    paragraphOffset,
                )
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
    val characterWidth: Float = RichContentLayoutDefaults.CharacterWidth,
    val lineHeight: Float = RichContentLayoutDefaults.LineHeight,
    val horizontalPadding: Float = RichContentLayoutDefaults.HorizontalPadding,
    val verticalPadding: Float = RichContentLayoutDefaults.VerticalPadding,
    val blockSpacing: Float = RichContentLayoutDefaults.BlockSpacing,
    val blockFormulaHeight: Float = RichContentLayoutDefaults.FormulaCardHeight,
    val blockImageHeight: Float = RichContentLayoutDefaults.ImageCardHeight,
    val tableHeaderPreviewHeight: Float = RichContentLayoutDefaults.TableHeaderPreviewHeight,
    val tableRowPreviewHeight: Float = RichContentLayoutDefaults.TableRowPreviewHeight,
    val tableCellPreviewHeight: Float = RichContentLayoutDefaults.TableCellPreviewHeight,
    val tableMinimumPreviewRows: Int = RichContentLayoutDefaults.TableMinimumPreviewRows,
    val tableMinimumLayoutRows: Int = RichContentLayoutDefaults.TableMinimumLayoutRows,
    val tableMinimumLayoutColumns: Int = RichContentLayoutDefaults.TableMinimumLayoutColumns,
    val tableMinCellWidth: Float = RichContentLayoutDefaults.TableMinCellWidth,
    val tableMinCellHeight: Float = RichContentLayoutDefaults.TableMinCellHeight,
    val tableMaxColumnWidth: Float = RichContentLayoutDefaults.TableMaxColumnWidth,
    val tableCellHorizontalPadding: Float = RichContentLayoutDefaults.TableCellHorizontalPadding,
    val tableCellVerticalPadding: Float = RichContentLayoutDefaults.TableCellVerticalPadding,
    val minimumMeasuredWidth: Float = RichContentLayoutDefaults.MinimumMeasuredWidth,
    val minimumContentWidth: Float = RichContentLayoutDefaults.MinimumContentWidth,
    val minimumMeasuredHeight: Float = RichContentLayoutDefaults.MinimumBoxHeight,
) {
    public fun tablePreviewHeight(table: TableNode): Float {
        val rowCount = table.rows.size.coerceAtLeast(tableMinimumPreviewRows)
        val rowHeight = max(tableRowPreviewHeight, tableCellPreviewHeight)
        return tableHeaderPreviewHeight + rowCount * rowHeight
    }
}

public object RichContentLayoutDefaults {
    public const val CharacterWidth: Float = 8f
    public const val LineHeight: Float = 24f
    public const val HorizontalPadding: Float = 16f
    public const val VerticalPadding: Float = 16f
    public const val BlockSpacing: Float = 4f
    public const val FormulaCardHeight: Float = 56f
    public const val ImageCardHeight: Float = 220f
    public const val TableHeaderPreviewHeight: Float = 32f
    public const val TableRowPreviewHeight: Float = 32f
    public const val TableCellPreviewHeight: Float = 32f
    public const val TableMinimumPreviewRows: Int = 1
    public const val TableMinimumLayoutRows: Int = 1
    public const val TableMinimumLayoutColumns: Int = 1
    public const val TableMinCellWidth: Float = 48f
    public const val TableMinCellHeight: Float = 32f
    public const val TableMaxColumnWidth: Float = 240f
    public const val TableCellHorizontalPadding: Float = 8f
    public const val TableCellVerticalPadding: Float = 4f
    public const val MinimumMeasuredWidth: Float = 1f
    public const val MinimumContentWidth: Float = 1f
    public const val MinimumBoxHeight: Float = 56f
    public const val BoxChromePadding: Float = 8f
    public const val RendererHorizontalPadding: Float = 8f
    public const val RendererVerticalPadding: Float = 6f
}

public data class RichContentLayoutResult(
    val measuredSize: CanvasSize,
    val blockRects: List<RichContentBlockRect>,
    val lineRects: List<RichContentLineRect>,
    val tableLayouts: List<RichContentTableLayout> = emptyList(),
)

public data class RichContentBlockRect(
    val blockIndex: Int,
    val rect: CanvasRect,
)

public data class RichContentTableLayout(
    val blockIndex: Int,
    val columnWidths: List<Float>,
    val rowHeights: List<Float>,
    val tableWidth: Float,
    val tableHeight: Float,
    val cellRects: List<List<CanvasRect>>,
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
