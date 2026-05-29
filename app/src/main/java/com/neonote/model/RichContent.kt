package com.neonote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Structured rich content. Formulas exist here as inline or display/block rich
 * content nodes, not as default top-level canvas objects.
 */
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
) : BlockNode

@Serializable
@SerialName("table")
data class TableNode(
    val rows: List<List<TableCell>> = emptyList(),
    val id: String = "",
) : BlockNode

@Serializable
data class TableCell(
    val content: RichContent = RichContent(),
)

@Serializable
@SerialName("text")
data class InlineText(
    val text: String,
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
) : InlineNode

@Serializable
@SerialName("blockFormula")
data class BlockFormula(
    val expression: String,
    val id: String = "",
) : BlockNode

@Serializable
@SerialName("blockImage")
data class BlockImage(
    val assetId: String,
    val altText: String? = null,
    val id: String = "",
) : BlockNode
