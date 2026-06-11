package com.neonote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Structured rich content. Formula, image, and table nodes in this model are
 * lightweight placeholders for document content inside a [RichContentBox].
 *
 * These placeholders intentionally do not provide production formula rendering,
 * a full spreadsheet/Office-style table editor, nested table UI, image paste, or
 * a binary asset pipeline. Canvas-level floating media remains modeled by
 * [FloatingImage] in CanvasObject.kt instead of by these RichContent nodes.
 *
 * The block list is the source of truth for the document. Editing commands
 * should transform these blocks instead of maintaining a parallel plain string.
 */
@Serializable
data class RichContent(
    val blocks: List<BlockNode> = emptyList(),
)

@Serializable
sealed interface BlockNode

@Serializable
sealed interface InlineNode

/**
 * A block-level text unit. Paragraphs own ordered inline nodes and are the
 * smallest top-level block edited by text commands.
 */
@Serializable
@SerialName("paragraph")
data class ParagraphNode(
    val inlines: List<InlineNode> = emptyList(),
    val id: String = "",
    val listMetadata: ListItemMetadata? = null,
) : BlockNode

@Serializable
data class ListItemMetadata(
    val kind: ListKind,
    val checked: Boolean = false,
)

@Serializable
enum class ListKind {
    @SerialName("bullet")
    Bullet,

    @SerialName("numbered")
    Numbered,

    @SerialName("todo")
    Todo,
}

/**
 * Block-level table content. Cells keep nested [RichContent]. [columnPolicies]
 * reserves deterministic sizing inputs for future manual-width table editing while
 * the current editor still renders a simple static grid preview.
 */
@Serializable
@SerialName("table")
data class TableNode(
    val rows: List<List<TableCell>> = emptyList(),
    val id: String = "",
    val columnPolicies: List<TableColumnPolicy> = emptyList(),
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
    @SerialName("auto")
    Auto,

    @SerialName("manual")
    Manual,
}

/** A table cell owns RichContent so serialization can preserve rich nested cell content. */
@Serializable
data class TableCell(
    val content: RichContent = RichContent(),
)

/** Inline text carries persistable presentation marks for shortcut-driven rich text editing. */
@Serializable
@SerialName("text")
data class InlineText(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
) : InlineNode

@Serializable
@SerialName("lineBreak")
data object InlineLineBreak : InlineNode

/** Inline formula placeholder that displays the raw expression text. */
@Serializable
@SerialName("formula")
data class InlineFormula(
    val expression: String,
) : InlineNode

/** Inline image placeholder referencing an asset id plus optional alt text. */
@Serializable
@SerialName("image")
data class InlineImage(
    val assetId: String,
    val altText: String? = null,
) : InlineNode

/** Block formula placeholder that displays the raw expression text. */
@Serializable
@SerialName("blockFormula")
data class BlockFormula(
    val expression: String,
    val id: String = "",
) : BlockNode

/** Block image placeholder referencing an asset id plus optional alt text. */
@Serializable
@SerialName("blockImage")
data class BlockImage(
    val assetId: String,
    val altText: String? = null,
    val id: String = "",
) : BlockNode

/**
 * A caret location inside a paragraph block. [blockIndex] addresses
 * [RichContent.blocks]; [inlineOffset] is a UTF-16 character offset through the
 * paragraph's inline text stream.
 */
@Serializable
data class TextCursorPosition(
    val blockIndex: Int,
    val inlineOffset: Int,
) : Comparable<TextCursorPosition> {
    override fun compareTo(other: TextCursorPosition): Int =
        compareValuesBy(this, other, TextCursorPosition::blockIndex, TextCursorPosition::inlineOffset)
}

/** A half-open text range from [start] to [end]. */
@Serializable
data class TextRange(
    val start: TextCursorPosition,
    val end: TextCursorPosition,
) {
    val isCollapsed: Boolean
        get() = start == end

    fun ordered(): TextRange = if (start <= end) this else TextRange(start = end, end = start)
}

/** Current text selection. Collapsed selections represent a caret. */
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
