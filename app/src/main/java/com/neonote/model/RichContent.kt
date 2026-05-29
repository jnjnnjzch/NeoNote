package com.neonote.model

/**
 * Structured rich content. Formulas exist here as inline or block nodes.
 */
data class RichContent(
    val blocks: List<BlockNode> = emptyList(),
)

sealed interface BlockNode

sealed interface InlineNode

data class ParagraphNode(
    val id: String,
    val inlines: List<InlineNode> = emptyList(),
) : BlockNode

data class TableNode(
    val id: String,
    val rows: List<List<TableCell>> = emptyList(),
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
    val id: String,
    val expression: String,
) : BlockNode

data class BlockImage(
    val id: String,
    val assetId: String,
    val altText: String? = null,
) : BlockNode
