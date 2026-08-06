package com.neonote.engine

import com.neonote.model.BlockFormula
import com.neonote.model.BlockImage
import com.neonote.model.BlockNode
import com.neonote.model.InlineFormula
import com.neonote.model.InlineImage
import com.neonote.model.InlineLineBreak
import com.neonote.model.InlineText
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox
import com.neonote.model.TableCell
import com.neonote.model.TableNode

/** Lifecycle rules for provisional note content. */
public fun RichContent.hasMeaningfulContent(): Boolean = blocks.any(BlockNode::hasMeaningfulContent)
public fun RichContentBox.hasMeaningfulContent(): Boolean = content.hasMeaningfulContent()

public fun RichContent.normalizedForCommit(): RichContent {
    val normalized = blocks.mapNotNull(BlockNode::normalizedForCommit)
    if (normalized.isEmpty()) return RichContent()
    val compact = if (normalized.any { it !is ParagraphNode }) {
        normalized.dropWhile { it is ParagraphNode && !it.hasMeaningfulContent() }
            .dropLastWhile { it is ParagraphNode && !it.hasMeaningfulContent() }
    } else normalized
    return RichContent(compact)
}

public fun RichContentBox.normalizedForCommit(): RichContentBox = copy(
    content = content.normalizedForCommit(),
    isFocused = false,
)

private fun BlockNode.hasMeaningfulContent(): Boolean = when (this) {
    is ParagraphNode -> inlines.any { inline ->
        when (inline) {
            is InlineText -> inline.text.any { !it.isWhitespace() }
            InlineLineBreak -> false
            is InlineFormula -> inline.expression.isNotBlank()
            is InlineImage -> true
        }
    }
    is BlockFormula -> expression.isNotBlank()
    is BlockImage -> true
    is TableNode -> true
}

private fun BlockNode.normalizedForCommit(): BlockNode? = when (this) {
    is ParagraphNode -> copy(inlines = inlines.filterNot { it is InlineText && it.text.isEmpty() })
    is BlockFormula -> takeIf { expression.isNotBlank() }
    is BlockImage -> this
    is TableNode -> copy(rows = rows.map { row -> row.map(TableCell::normalizedForCommit) })
}

private fun TableCell.normalizedForCommit(): TableCell = copy(content = content.normalizedForNestedCell())
private fun RichContent.normalizedForNestedCell(): RichContent {
    val normalized = blocks.mapNotNull(BlockNode::normalizedForCommit)
    return RichContent(normalized.ifEmpty { listOf(ParagraphNode()) })
}
