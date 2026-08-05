package com.neonote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Structured, persistable content inside a [RichContentBox] or [TableCell]. */
@Serializable
data class RichContent(
    val blocks: List<BlockNode> = emptyList(),
)

@Serializable
sealed interface BlockNode

@Serializable
sealed interface InlineNode

@Serializable
@SerialName("paragraph")
data class ParagraphNode(
    val inlines: List<InlineNode> = emptyList(),
    val id: String = "",
    val listMetadata: ListItemMetadata? = null,
    val style: ParagraphStyle = ParagraphStyle(),
) : BlockNode

@Serializable
data class ParagraphStyle(
    val alignment: TextAlignment = TextAlignment.Start,
    val indentLevel: Int = 0,
    /** 0 is body text; 1..3 are product-supported heading levels. */
    val headingLevel: Int = 0,
)

@Serializable
enum class TextAlignment {
    @SerialName("start") Start,
    @SerialName("center") Center,
    @SerialName("end") End,
}

@Serializable
data class ListItemMetadata(
    val kind: ListKind,
    val checked: Boolean = false,
)

@Serializable
enum class ListKind {
    @SerialName("bullet") Bullet,
    @SerialName("numbered") Numbered,
    @SerialName("todo") Todo,
}

/**
 * A recursively editable table. Every cell owns [RichContent], therefore a cell
 * can contain paragraphs, images, formulas, and another [TableNode].
 */
@Serializable
@SerialName("table")
data class TableNode(
    val rows: List<List<TableCell>> = emptyList(),
    val id: String = "",
    val columnPolicies: List<TableColumnPolicy> = emptyList(),
    val headerRowCount: Int = 0,
    val showBorders: Boolean = true,
) : BlockNode

@Serializable
data class TableColumnPolicy(
    val minWidth: Float = 0f,
    val preferredWidth: Float? = null,
    val maxWidth: Float? = null,
    val manualWidth: Float? = null,
    val mode: TableColumnWidthMode = TableColumnWidthMode.Auto,
)

@Serializable
enum class TableColumnWidthMode {
    @SerialName("auto") Auto,
    @SerialName("manual") Manual,
}

@Serializable
data class TableCell(
    val content: RichContent = RichContent(),
    val backgroundColorArgb: Int? = null,
    val verticalAlignment: TableCellVerticalAlignment = TableCellVerticalAlignment.Top,
    /** Size of an anchor cell. Continuation cells keep both values at one. */
    val rowSpan: Int = 1,
    val columnSpan: Int = 1,
    /** Non-null only for cells visually covered by another anchor cell. */
    val mergedInto: TableCellMergeAnchor? = null,
)

@Serializable
data class TableCellMergeAnchor(
    val rowIndex: Int,
    val columnIndex: Int,
)

@Serializable
enum class TableCellVerticalAlignment {
    @SerialName("top") Top,
    @SerialName("center") Center,
    @SerialName("bottom") Bottom,
}

/** Persistable character run and all formatting exposed by the editor. */
@Serializable
@SerialName("text")
data class InlineText(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val textColorArgb: Int? = null,
    val highlightColorArgb: Int? = null,
    val fontScale: Float = 1f,
    val link: String? = null,
) : InlineNode

@Serializable
@SerialName("lineBreak")
data object InlineLineBreak : InlineNode

@Serializable
@SerialName("formula")
data class InlineFormula(
    val expression: String,
) : InlineNode

@Serializable
@SerialName("image")
data class InlineImage(
    val assetId: String,
    val altText: String? = null,
    val width: Float? = null,
    val height: Float? = null,
) : InlineNode

@Serializable
@SerialName("blockFormula")
data class BlockFormula(
    val expression: String,
    val id: String = "",
    val displayMode: FormulaDisplayMode = FormulaDisplayMode.Display,
    val numbered: Boolean = false,
) : BlockNode

@Serializable
enum class FormulaDisplayMode {
    @SerialName("display") Display,
    @SerialName("compact") Compact,
}

@Serializable
@SerialName("blockImage")
data class BlockImage(
    val assetId: String,
    val altText: String? = null,
    val id: String = "",
    /** Requested content width in document units; null means fit the text box. */
    val width: Float? = null,
    /** Requested content height in document units; null preserves source ratio. */
    val height: Float? = null,
    val rotationDegrees: Float = 0f,
    val crop: ImageCrop = ImageCrop(),
    val caption: String? = null,
) : BlockNode

@Serializable
data class ImageCrop(
    val leftFraction: Float = 0f,
    val topFraction: Float = 0f,
    val rightFraction: Float = 1f,
    val bottomFraction: Float = 1f,
) {
    fun normalized(): ImageCrop {
        val left = leftFraction.coerceIn(0f, 1f)
        val top = topFraction.coerceIn(0f, 1f)
        val right = rightFraction.coerceIn(left, 1f)
        val bottom = bottomFraction.coerceIn(top, 1f)
        return copy(
            leftFraction = left,
            topFraction = top,
            rightFraction = right,
            bottomFraction = bottom,
        )
    }
}

@Serializable
data class TextCursorPosition(
    val blockIndex: Int,
    val inlineOffset: Int,
) : Comparable<TextCursorPosition> {
    override fun compareTo(other: TextCursorPosition): Int =
        compareValuesBy(this, other, TextCursorPosition::blockIndex, TextCursorPosition::inlineOffset)
}

@Serializable
data class TextRange(
    val start: TextCursorPosition,
    val end: TextCursorPosition,
) {
    val isCollapsed: Boolean
        get() = start == end

    fun ordered(): TextRange = if (start <= end) this else TextRange(start = end, end = start)
}

/** One recursive step from a [RichContent] table block into one cell. */
@Serializable
data class TableCellPathSegment(
    val tableBlockIndex: Int,
    val rowIndex: Int,
    val columnIndex: Int,
)

/**
 * Stable recursive table address.
 *
 * The original top-level fields are retained for backward-compatible JSON and
 * call sites. [nestedPath] then descends through tables stored inside cells.
 */
@Serializable
data class TableCellAddress(
    val blockIndex: Int,
    val rowIndex: Int,
    val columnIndex: Int,
    val contentBlockIndex: Int = 0,
    val nestedPath: List<TableCellPathSegment> = emptyList(),
) {
    val path: List<TableCellPathSegment>
        get() = listOf(TableCellPathSegment(blockIndex, rowIndex, columnIndex)) + nestedPath

    val depth: Int
        get() = path.size

    fun child(
        tableBlockIndex: Int,
        rowIndex: Int,
        columnIndex: Int,
        contentBlockIndex: Int = 0,
    ): TableCellAddress = copy(
        contentBlockIndex = contentBlockIndex,
        nestedPath = nestedPath + TableCellPathSegment(tableBlockIndex, rowIndex, columnIndex),
    )

    fun withContentBlock(index: Int): TableCellAddress = copy(contentBlockIndex = index)
}

@Serializable
data class TextSelection(
    val range: TextRange,
) {
    val start: TextCursorPosition
        get() = range.start

    val end: TextCursorPosition
        get() = range.end

    val isCollapsed: Boolean
        get() = range.isCollapsed

    fun ordered(): TextSelection = TextSelection(range = range.ordered())

    companion object {
        fun cursor(position: TextCursorPosition): TextSelection = TextSelection(TextRange(position, position))
    }
}
