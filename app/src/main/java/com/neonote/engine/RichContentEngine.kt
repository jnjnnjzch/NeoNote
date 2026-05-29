package com.neonote.engine

import com.neonote.model.BlockFormula
import com.neonote.model.BlockImage
import com.neonote.model.BlockNode
import com.neonote.model.InlineLineBreak
import com.neonote.model.InlineText
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox
import com.neonote.model.TableCell
import com.neonote.model.TableNode

/**
 * Pure helpers for inserting block content into a RichContentBox.
 */
public class RichContentEngine {
    public fun execute(box: RichContentBox, command: RichContentCommand): RichContentCommandResult = when (command) {
        is RichContentCommand.InsertParagraph -> insertParagraph(box, command.text, command.index)
        is RichContentCommand.InsertTable -> insertTable(box, command.rows, command.columns, command.index)
        is RichContentCommand.InsertBlockImage -> insertBlockImage(box, command.assetId, command.altText, command.index)
        is RichContentCommand.InsertBlockFormula -> insertBlockFormula(box, command.expression, command.index)
        is RichContentCommand.ReplacePlainText -> replacePlainText(box, command.text)
    }

    public fun insertParagraph(box: RichContentBox, text: String, index: Int? = null): RichContentCommandResult.ContentInserted =
        insertBlock(box, ParagraphNode(inlines = listOf(InlineText(text))), index)

    /**
     * Replaces editable plain text in a box using the minimal RichContent text
     * mapping: newline characters split paragraphs. InlineLineBreak remains
     * available for future soft-break editing, but plain TextField edits use
     * paragraph blocks so top-level line navigation stays simple.
     */
    public fun replacePlainText(box: RichContentBox, text: String): RichContentCommandResult.ContentReplaced {
        val paragraphs = text.split("\n").map { line ->
            ParagraphNode(inlines = listOf(InlineText(line)))
        }
        return RichContentCommandResult.ContentReplaced(box = box.copy(content = RichContent(blocks = paragraphs)))
    }

    public fun insertTable(box: RichContentBox, rows: Int, columns: Int, index: Int? = null): RichContentCommandResult.ContentInserted {
        require(rows >= 0) { "rows must be non-negative" }
        require(columns >= 0) { "columns must be non-negative" }
        val tableRows = List(rows) { List(columns) { TableCell() } }
        return insertBlock(box, TableNode(rows = tableRows), index)
    }

    public fun insertBlockImage(
        box: RichContentBox,
        assetId: String,
        altText: String? = null,
        index: Int? = null,
    ): RichContentCommandResult.ContentInserted = insertBlock(box, BlockImage(assetId = assetId, altText = altText), index)

    public fun insertBlockFormula(box: RichContentBox, expression: String, index: Int? = null): RichContentCommandResult.ContentInserted =
        insertBlock(box, BlockFormula(expression = expression), index)

    private fun insertBlock(box: RichContentBox, block: BlockNode, index: Int?): RichContentCommandResult.ContentInserted {
        val blocks = box.content.blocks.insertAt(index ?: box.content.blocks.size, block)
        val updatedBox = box.copy(content = RichContent(blocks = blocks))
        return RichContentCommandResult.ContentInserted(box = updatedBox, block = block)
    }

    private fun <T> List<T>.insertAt(index: Int, item: T): List<T> {
        require(index in 0..size) { "index must be between 0 and $size" }
        return take(index) + item + drop(index)
    }
}

public fun RichContentBox.toPlainText(): String = content.toPlainText()

public fun RichContent.toPlainText(): String = blocks.joinToString("\n") { block ->
    when (block) {
        is ParagraphNode -> block.inlines.joinToString("") { inline ->
            when (inline) {
                is InlineText -> inline.text
                InlineLineBreak -> "\n"
                else -> ""
            }
        }
        else -> ""
    }
}

public sealed interface RichContentCommand {
    public data class InsertParagraph(val text: String, val index: Int? = null) : RichContentCommand
    public data class InsertTable(val rows: Int, val columns: Int, val index: Int? = null) : RichContentCommand
    public data class InsertBlockImage(val assetId: String, val altText: String? = null, val index: Int? = null) : RichContentCommand
    public data class InsertBlockFormula(val expression: String, val index: Int? = null) : RichContentCommand
    public data class ReplacePlainText(val text: String) : RichContentCommand
}

public sealed interface RichContentCommandResult {
    public data class ContentInserted(val box: RichContentBox, val block: BlockNode) : RichContentCommandResult
    public data class ContentReplaced(val box: RichContentBox) : RichContentCommandResult
}
