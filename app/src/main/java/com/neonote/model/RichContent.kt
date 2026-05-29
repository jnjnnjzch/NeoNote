package com.neonote.model

/**
 * Structured rich content. Formulas exist here as inline or display/block rich
 * content nodes, not as default top-level canvas objects.
 */
data class RichContent(
    val blocks: List<BlockNode> = emptyList(),
)

sealed interface BlockNode

sealed interface InlineNode

data class ParagraphNode(
    val inlines: List<InlineNode> = emptyList(),
    val id: String = "",
) : BlockNode

data class TableNode(
    val rows: List<List<TableCell>> = emptyList(),
    val id: String = "",
) : BlockNode

data class TableCell(
    val content: RichContent = RichContent(),
)

data class InlineText(
    val text: String,
) : InlineNode

data object InlineLineBreak : InlineNode

data class InlineFormula(
    val expression: String,
) : InlineNode

data class InlineImage(
    val assetId: String,
    val altText: String? = null,
) : InlineNode

data class BlockFormula(
    val expression: String,
    val id: String = "",
) : BlockNode

data class BlockImage(
    val assetId: String,
    val altText: String? = null,
    val id: String = "",
) : BlockNode
