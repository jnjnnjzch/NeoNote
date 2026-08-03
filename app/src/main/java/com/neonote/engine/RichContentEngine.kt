package com.neonote.engine

import com.neonote.model.BlockFormula
import com.neonote.model.BlockImage
import com.neonote.model.BlockNode
import com.neonote.model.InlineFormula
import com.neonote.model.InlineImage
import com.neonote.model.InlineLineBreak
import com.neonote.model.InlineNode
import com.neonote.model.InlineText
import com.neonote.model.ListItemMetadata
import com.neonote.model.ListKind
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox
import com.neonote.model.TableCell
import com.neonote.model.TableNode
import com.neonote.model.TableCellAddress
import com.neonote.model.TextCursorPosition
import com.neonote.model.TextRange
import com.neonote.model.TextSelection

/**
 * Pure helpers for transforming RichContentBox content.
 */
public class RichContentEngine {
    public fun execute(box: RichContentBox, command: RichContentCommand): RichContentCommandResult = when (command) {
        is RichContentCommand.InsertText -> insertText(box, command.text, command.selection, command.typingStyle)
        is RichContentCommand.PastePlainText -> pastePlainText(box, command.text, command.selection, command.typingStyle)
        is RichContentCommand.DeleteBackward -> deleteBackward(box, command.selection)
        is RichContentCommand.DeleteSelection -> deleteSelection(box, command.selection)
        is RichContentCommand.InsertParagraph -> if (command.selection == null) {
            insertParagraphBlock(box, command.text, command.index)
        } else {
            insertParagraph(box, command.selection)
        }
        is RichContentCommand.ToggleBold -> toggleStyle(box, command.selection, InlineStyle.Bold)
        is RichContentCommand.ToggleItalic -> toggleStyle(box, command.selection, InlineStyle.Italic)
        is RichContentCommand.ToggleUnderline -> toggleStyle(box, command.selection, InlineStyle.Underline)
        is RichContentCommand.ToggleBulletList -> toggleList(box, command.selection, ListKind.Bullet)
        is RichContentCommand.ToggleNumberedList -> toggleList(box, command.selection, ListKind.Numbered)
        is RichContentCommand.ToggleTodo -> toggleList(box, command.selection, ListKind.Todo)
        is RichContentCommand.ToggleTodoCheckedState -> toggleTodoCheckedState(box, command.blockIndex)
        is RichContentCommand.InsertInlineFormula -> insertInlineFormula(box, command.expression, command.selection)
        is RichContentCommand.InsertBlockFormula -> insertBlockFormula(box, command.expression, command.index)
        is RichContentCommand.ReplaceBlockFormulaExpression -> replaceBlockFormulaExpression(box, command.blockIndex, command.expression)
        is RichContentCommand.InsertInlineImage -> insertInlineImage(box, command.assetId, command.altText, command.selection)
        is RichContentCommand.InsertBlockImage -> insertBlockImage(box, command.assetId, command.altText, command.index)
        is RichContentCommand.InsertTable -> insertTable(box, command.rows, command.columns, command.index)
        is RichContentCommand.ReplacePlainText -> replacePlainText(box, command.text)
        is RichContentCommand.ReplaceParagraphText -> replaceParagraphText(box, command.blockIndex, command.text)
        is RichContentCommand.ReplaceTableCellParagraphText -> replaceTableCellParagraphText(box, command.address, command.text)
    }

    /** Backward-compatible block insertion path used by canvas-level actions. */
    public fun insertParagraph(box: RichContentBox, text: String, index: Int? = null): RichContentCommandResult.ContentInserted =
        insertParagraphBlock(box, text, index)

    public fun insertText(
        box: RichContentBox,
        text: String,
        selection: TextSelection,
        typingStyle: TypingStyle = TypingStyle(),
    ): RichContentCommandResult.ContentEdited {
        val prepared = box.ensureEditableSelection(selection)
        val deleteResult = prepared.box.deleteSelection(prepared.selection)
        val insertResult = deleteResult.box.insertPlainTextAt(deleteResult.selection.start, text, typingStyle)
        return RichContentCommandResult.ContentEdited(box = insertResult.box, selection = insertResult.selection)
    }

    public fun pastePlainText(
        box: RichContentBox,
        text: String,
        selection: TextSelection,
        typingStyle: TypingStyle = TypingStyle(),
    ): RichContentCommandResult.ContentEdited = insertText(box, text.normalizePlainTextNewlines(), selection, typingStyle)

    public fun deleteSelection(box: RichContentBox, selection: TextSelection): RichContentCommandResult.ContentEdited {
        val prepared = box.ensureEditableSelection(selection)
        val deleteResult = prepared.box.deleteSelection(prepared.selection)
        return RichContentCommandResult.ContentEdited(box = deleteResult.box, selection = deleteResult.selection)
    }

    public fun deleteBackward(box: RichContentBox, selection: TextSelection): RichContentCommandResult.ContentEdited {
        val prepared = box.ensureEditableSelection(selection)
        if (!prepared.selection.isCollapsed) {
            return deleteSelection(prepared.box, prepared.selection)
        }

        val cursor = prepared.selection.start
        if (cursor.inlineOffset > 0) {
            val start = cursor.copy(inlineOffset = cursor.inlineOffset - 1)
            val deleteResult = prepared.box.deleteSelection(TextSelection(TextRange(start, cursor)))
            return RichContentCommandResult.ContentEdited(box = deleteResult.box, selection = deleteResult.selection)
        }

        if (cursor.blockIndex == 0) {
            return RichContentCommandResult.ContentEdited(box = prepared.box, selection = prepared.selection)
        }

        val blocks = prepared.box.content.blocks
        val previous = blocks[cursor.blockIndex - 1] as? ParagraphNode
            ?: return RichContentCommandResult.ContentEdited(box = prepared.box, selection = prepared.selection)
        val current = blocks[cursor.blockIndex] as? ParagraphNode
            ?: return RichContentCommandResult.ContentEdited(box = prepared.box, selection = prepared.selection)
        val previousLength = previous.textLength()
        val merged = previous.copy(inlines = previous.toStyledChars().plus(current.toStyledChars()).toInlineTextNodes())
        val updatedBlocks = blocks.take(cursor.blockIndex - 1) + merged + blocks.drop(cursor.blockIndex + 1)
        val updatedCursor = TextCursorPosition(blockIndex = cursor.blockIndex - 1, inlineOffset = previousLength)
        return RichContentCommandResult.ContentEdited(
            box = prepared.box.copy(content = RichContent(blocks = updatedBlocks)),
            selection = TextSelection.cursor(updatedCursor),
        )
    }

    public fun insertParagraph(box: RichContentBox, selection: TextSelection): RichContentCommandResult.ContentEdited {
        val prepared = box.ensureEditableSelection(selection)
        val deleteResult = prepared.box.deleteSelection(prepared.selection)
        val cursor = deleteResult.selection.start
        val blocks = deleteResult.box.content.blocks
        val paragraph = blocks[cursor.blockIndex] as ParagraphNode
        val chars = paragraph.toStyledChars()
        val before = chars.take(cursor.inlineOffset).toInlineTextNodes()
        val after = chars.drop(cursor.inlineOffset).toInlineTextNodes()
        val nextListMetadata = paragraph.listMetadata?.let { metadata ->
            if (metadata.kind == ListKind.Todo) metadata.copy(checked = false) else metadata
        }
        val updatedBlocks = blocks.take(cursor.blockIndex) +
            paragraph.copy(inlines = before) +
            paragraph.copy(inlines = after, listMetadata = nextListMetadata) +
            blocks.drop(cursor.blockIndex + 1)
        val updatedCursor = TextCursorPosition(blockIndex = cursor.blockIndex + 1, inlineOffset = 0)
        return RichContentCommandResult.ContentEdited(
            box = deleteResult.box.copy(content = RichContent(blocks = updatedBlocks)),
            selection = TextSelection.cursor(updatedCursor),
        )
    }

    public fun toggleStyle(
        box: RichContentBox,
        selection: TextSelection,
        style: InlineStyle,
    ): RichContentCommandResult.ContentEdited {
        val prepared = box.ensureEditableSelection(selection)
        val ordered = prepared.selection.ordered()
        if (ordered.isCollapsed) return RichContentCommandResult.ContentEdited(box = prepared.box, selection = prepared.selection)

        val blocks = prepared.box.content.blocks
        val targetValue = blocks.selectedChars(ordered).any { char -> !char.hasStyle(style) }
        val updatedBlocks = blocks.mapIndexed { index, block ->
            val paragraph = block as? ParagraphNode ?: return@mapIndexed block
            if (index !in ordered.start.blockIndex..ordered.end.blockIndex) return@mapIndexed block

            val from = if (index == ordered.start.blockIndex) ordered.start.inlineOffset else 0
            val to = if (index == ordered.end.blockIndex) ordered.end.inlineOffset else paragraph.textLength()
            if (from >= to) return@mapIndexed block

            val updatedChars = paragraph.toStyledChars().mapIndexed { charIndex, char ->
                if (charIndex in from until to) char.withStyle(style, targetValue) else char
            }
            paragraph.copy(inlines = updatedChars.toInlineTextNodes())
        }
        return RichContentCommandResult.ContentEdited(
            box = prepared.box.copy(content = RichContent(blocks = updatedBlocks)),
            selection = ordered,
        )
    }

    public fun toggleList(
        box: RichContentBox,
        selection: TextSelection,
        kind: ListKind,
    ): RichContentCommandResult.ContentEdited {
        val prepared = box.ensureEditableSelection(selection)
        val ordered = prepared.selection.ordered()
        val blockRange = ordered.listTargetBlockRange()
        val blocks = prepared.box.content.blocks
        val allAlreadyTargetKind = blockRange.all { index ->
            (blocks[index] as? ParagraphNode)?.listMetadata?.kind == kind
        }
        val updatedBlocks = blocks.mapIndexed { index, block ->
            val paragraph = block as? ParagraphNode ?: return@mapIndexed block
            if (index !in blockRange) return@mapIndexed block
            val nextMetadata = if (allAlreadyTargetKind) {
                null
            } else {
                ListItemMetadata(kind = kind, checked = kind == ListKind.Todo && paragraph.listMetadata?.checked == true)
            }
            paragraph.copy(listMetadata = nextMetadata)
        }
        return RichContentCommandResult.ContentEdited(
            box = prepared.box.copy(content = RichContent(blocks = updatedBlocks)),
            selection = ordered,
        )
    }

    public fun toggleTodoCheckedState(box: RichContentBox, blockIndex: Int): RichContentCommandResult.ContentEdited {
        require(blockIndex in box.content.blocks.indices) { "blockIndex must address an existing block" }
        val block = box.content.blocks[blockIndex] as? ParagraphNode
            ?: return RichContentCommandResult.ContentEdited(
                box = box,
                selection = TextSelection.cursor(TextCursorPosition(blockIndex = blockIndex, inlineOffset = 0)),
            )
        val metadata = block.listMetadata
        if (metadata?.kind != ListKind.Todo) {
            return RichContentCommandResult.ContentEdited(
                box = box,
                selection = TextSelection.cursor(TextCursorPosition(blockIndex = blockIndex, inlineOffset = 0)),
            )
        }
        val updatedBlock = block.copy(listMetadata = metadata.copy(checked = !metadata.checked))
        val cursor = TextCursorPosition(blockIndex = blockIndex, inlineOffset = block.textLength())
        return RichContentCommandResult.ContentEdited(
            box = box.copy(content = RichContent(blocks = box.content.blocks.replaceAt(blockIndex, updatedBlock))),
            selection = TextSelection.cursor(cursor),
        )
    }

    /**
     * Replaces editable plain text in a box using the minimal RichContent text
     * mapping: newline characters split paragraphs. InlineLineBreak remains
     * available for future soft-break editing, but plain TextField edits use
     * paragraph blocks so top-level line navigation stays simple.
     */
    public fun replacePlainText(box: RichContentBox, text: String): RichContentCommandResult.ContentReplaced {
        val paragraphs = text.normalizePlainTextNewlines().split("\n").map { line ->
            ParagraphNode(inlines = listOf(InlineText(line)))
        }
        return RichContentCommandResult.ContentReplaced(box = box.copy(content = RichContent(blocks = paragraphs)))
    }

    /**
     * Replace only one paragraph from a platform paragraph input adapter snapshot. This is
     * intentionally conservative for IME composition: active composing text can
     * be accepted without rebuilding unrelated rich blocks in the same box.
     */
    public fun replaceParagraphText(
        box: RichContentBox,
        blockIndex: Int,
        text: String,
    ): RichContentCommandResult.ContentEdited {
        if (box.content.blocks.isEmpty()) {
            return insertText(
                box = box,
                text = text,
                selection = TextSelection.cursor(TextCursorPosition(blockIndex = 0, inlineOffset = 0)),
            )
        }
        require(blockIndex in box.content.blocks.indices) { "blockIndex must address an existing block" }
        val paragraph = box.content.blocks[blockIndex] as? ParagraphNode
            ?: return RichContentCommandResult.ContentEdited(
                box = box,
                selection = TextSelection.cursor(TextCursorPosition(blockIndex = blockIndex, inlineOffset = 0)),
            )
        val fullParagraphSelection = TextSelection(
            TextRange(
                start = TextCursorPosition(blockIndex = blockIndex, inlineOffset = 0),
                end = TextCursorPosition(blockIndex = blockIndex, inlineOffset = paragraph.textLength()),
            ),
        )
        val deleteResult = box.deleteSelection(fullParagraphSelection)
        val insertResult = deleteResult.box.insertPlainTextAt(
            position = deleteResult.selection.start,
            text = text,
            typingStyle = TypingStyle(),
        )
        return RichContentCommandResult.ContentEdited(box = insertResult.box, selection = insertResult.selection)
    }


    public fun replaceTableCellParagraphText(
        box: RichContentBox,
        address: TableCellAddress,
        text: String,
    ): RichContentCommandResult.ContentEdited {
        require(address.blockIndex in box.content.blocks.indices) {
            "blockIndex must address an existing block"
        }
        val table = box.content.blocks[address.blockIndex] as? TableNode
            ?: return RichContentCommandResult.ContentEdited(
                box = box,
                selection = TextSelection.cursor(
                    TextCursorPosition(blockIndex = address.blockIndex, inlineOffset = 0),
                ),
            )
        require(address.rowIndex in table.rows.indices) {
            "rowIndex must address an existing row"
        }
        val row = table.rows[address.rowIndex]
        require(address.columnIndex in row.indices) {
            "columnIndex must address an existing cell"
        }

        val normalizedText = text.normalizePlainTextNewlines()
        val cell = row[address.columnIndex]
        val cellBlocks = cell.content.blocks
        require(address.contentBlockIndex in 0..cellBlocks.size) {
            "contentBlockIndex must address an existing block or the append position"
        }

        val existingBlock = cellBlocks.getOrNull(address.contentBlockIndex)
        if (existingBlock != null && existingBlock !is ParagraphNode) {
            return RichContentCommandResult.ContentEdited(
                box = box,
                selection = TextSelection.cursor(
                    TextCursorPosition(blockIndex = address.blockIndex, inlineOffset = 0),
                ),
            )
        }

        val paragraph = (existingBlock as? ParagraphNode)
            ?.copy(inlines = listOf(InlineText(normalizedText)))
            ?: ParagraphNode(inlines = listOf(InlineText(normalizedText)))
        val updatedCellBlocks = if (address.contentBlockIndex == cellBlocks.size) {
            cellBlocks + paragraph
        } else {
            cellBlocks.replaceAt(address.contentBlockIndex, paragraph)
        }
        val updatedCell = cell.copy(content = RichContent(blocks = updatedCellBlocks))
        val updatedRow = row.replaceAt(address.columnIndex, updatedCell)
        val updatedTable = table.copy(rows = table.rows.replaceAt(address.rowIndex, updatedRow))
        val updatedBlocks = box.content.blocks.replaceAt(address.blockIndex, updatedTable)

        return RichContentCommandResult.ContentEdited(
            box = box.copy(content = RichContent(blocks = updatedBlocks)),
            selection = TextSelection.cursor(
                TextCursorPosition(blockIndex = address.blockIndex, inlineOffset = 0),
            ),
        )
    }

    public fun insertInlineFormula(
        box: RichContentBox,
        expression: String,
        selection: TextSelection,
    ): RichContentCommandResult.ContentEdited = insertInlineNode(box, InlineFormula(expression = expression), selection)

    public fun insertInlineImage(
        box: RichContentBox,
        assetId: String,
        altText: String? = null,
        selection: TextSelection,
    ): RichContentCommandResult.ContentEdited = insertInlineNode(box, InlineImage(assetId = assetId, altText = altText), selection)

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

    public fun replaceBlockFormulaExpression(
        box: RichContentBox,
        blockIndex: Int,
        expression: String,
    ): RichContentCommandResult.ContentEdited {
        require(blockIndex in box.content.blocks.indices) { "blockIndex must address an existing block" }
        val block = box.content.blocks[blockIndex] as? BlockFormula
            ?: return RichContentCommandResult.ContentEdited(
                box = box,
                selection = TextSelection.cursor(TextCursorPosition(blockIndex = blockIndex, inlineOffset = 0)),
            )
        val updatedBlock = block.copy(expression = expression)
        return RichContentCommandResult.ContentEdited(
            box = box.copy(content = RichContent(blocks = box.content.blocks.replaceAt(blockIndex, updatedBlock))),
            selection = TextSelection.cursor(TextCursorPosition(blockIndex = blockIndex, inlineOffset = expression.length)),
        )
    }

    private fun insertParagraphBlock(box: RichContentBox, text: String, index: Int? = null): RichContentCommandResult.ContentInserted =
        insertBlock(box, ParagraphNode(inlines = listOf(InlineText(text))), index)

    private fun insertInlineNode(
        box: RichContentBox,
        inline: InlineNode,
        selection: TextSelection,
    ): RichContentCommandResult.ContentEdited {
        val prepared = box.ensureEditableSelection(selection)
        val deleteResult = prepared.box.deleteSelection(prepared.selection)
        val cursor = deleteResult.selection.start
        val blocks = deleteResult.box.content.blocks
        val paragraph = blocks[cursor.blockIndex] as ParagraphNode
        val updatedParagraph = paragraph.insertInlineAt(cursor.inlineOffset, inline)
        val updatedCursor = cursor.copy(inlineOffset = cursor.inlineOffset + inline.placeholderLength())
        return RichContentCommandResult.ContentEdited(
            box = deleteResult.box.copy(content = RichContent(blocks = blocks.replaceAt(cursor.blockIndex, updatedParagraph))),
            selection = TextSelection.cursor(updatedCursor),
        )
    }

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
                is InlineFormula, is InlineImage -> "\uFFFC"
            }
        }
        else -> ""
    }
}

public enum class InlineStyle { Bold, Italic, Underline }

public sealed interface RichContentCommand {
    public data class InsertText(
        val text: String,
        val selection: TextSelection,
        val typingStyle: TypingStyle = TypingStyle(),
    ) : RichContentCommand
    public data class PastePlainText(
        val text: String,
        val selection: TextSelection,
        val typingStyle: TypingStyle = TypingStyle(),
    ) : RichContentCommand
    public data class DeleteBackward(val selection: TextSelection) : RichContentCommand
    public data class DeleteSelection(val selection: TextSelection) : RichContentCommand

    /**
     * If [selection] is supplied, split text at that selection. If omitted,
     * insert a new paragraph block for backward-compatible canvas commands.
     */
    public data class InsertParagraph(
        val text: String = "",
        val index: Int? = null,
        val selection: TextSelection? = null,
    ) : RichContentCommand

    public data class ToggleBold(val selection: TextSelection) : RichContentCommand
    public data class ToggleItalic(val selection: TextSelection) : RichContentCommand
    public data class ToggleUnderline(val selection: TextSelection) : RichContentCommand
    public data class ToggleBulletList(val selection: TextSelection) : RichContentCommand
    public data class ToggleNumberedList(val selection: TextSelection) : RichContentCommand
    public data class ToggleTodo(val selection: TextSelection) : RichContentCommand
    public data class ToggleTodoCheckedState(val blockIndex: Int) : RichContentCommand
    public data class InsertInlineFormula(val expression: String, val selection: TextSelection) : RichContentCommand
    public data class InsertBlockFormula(val expression: String, val index: Int? = null) : RichContentCommand
    public data class ReplaceBlockFormulaExpression(val blockIndex: Int, val expression: String) : RichContentCommand
    public data class InsertInlineImage(val assetId: String, val altText: String? = null, val selection: TextSelection) : RichContentCommand
    public data class InsertBlockImage(val assetId: String, val altText: String? = null, val index: Int? = null) : RichContentCommand
    public data class InsertTable(val rows: Int, val columns: Int, val index: Int? = null) : RichContentCommand
    public data class ReplacePlainText(val text: String) : RichContentCommand
    public data class ReplaceParagraphText(val blockIndex: Int, val text: String) : RichContentCommand
    public data class ReplaceTableCellParagraphText(val address: TableCellAddress, val text: String) : RichContentCommand
}

public sealed interface RichContentCommandResult {
    public data class ContentInserted(val box: RichContentBox, val block: BlockNode) : RichContentCommandResult
    public data class ContentReplaced(val box: RichContentBox) : RichContentCommandResult
    public data class ContentEdited(val box: RichContentBox, val selection: TextSelection) : RichContentCommandResult
}

private data class EditableSelection(val box: RichContentBox, val selection: TextSelection)

private data class EditOperationResult(val box: RichContentBox, val selection: TextSelection)

private data class StyledChar(
    val value: Char,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val atom: InlineNode? = null,
) {
    fun hasStyle(style: InlineStyle): Boolean = atom == null && when (style) {
        InlineStyle.Bold -> bold
        InlineStyle.Italic -> italic
        InlineStyle.Underline -> underline
    }

    fun withStyle(style: InlineStyle, enabled: Boolean): StyledChar {
        if (atom != null) return this
        return when (style) {
            InlineStyle.Bold -> copy(bold = enabled)
            InlineStyle.Italic -> copy(italic = enabled)
            InlineStyle.Underline -> copy(underline = enabled)
        }
    }
}

private fun RichContentBox.ensureEditableSelection(selection: TextSelection): EditableSelection {
    val boxWithParagraph = if (content.blocks.isEmpty()) {
        copy(content = RichContent(blocks = listOf(ParagraphNode())))
    } else {
        this
    }
    boxWithParagraph.validateSelection(selection)
    return EditableSelection(box = boxWithParagraph, selection = selection)
}

private fun RichContentBox.validateSelection(selection: TextSelection) {
    validatePosition(selection.start)
    validatePosition(selection.end)
}

private fun RichContentBox.validatePosition(position: TextCursorPosition) {
    require(position.blockIndex in content.blocks.indices) { "blockIndex must address an existing block" }
    val paragraph = content.blocks[position.blockIndex] as? ParagraphNode
    require(paragraph != null) { "text editing commands require paragraph blocks" }
    require(position.inlineOffset in 0..paragraph.textLength()) { "inlineOffset must be inside the paragraph text" }
}

private fun RichContentBox.deleteSelection(selection: TextSelection): EditOperationResult {
    val ordered = selection.ordered()
    if (ordered.isCollapsed) return EditOperationResult(box = this, selection = ordered)

    val blocks = content.blocks
    val start = ordered.start
    val end = ordered.end
    val startParagraph = blocks[start.blockIndex] as ParagraphNode
    val endParagraph = blocks[end.blockIndex] as ParagraphNode
    val updatedBlocks = if (start.blockIndex == end.blockIndex) {
        val chars = startParagraph.toStyledChars()
        blocks.replaceAt(
            start.blockIndex,
            startParagraph.copy(inlines = (chars.take(start.inlineOffset) + chars.drop(end.inlineOffset)).toInlineTextNodes()),
        )
    } else {
        val mergedChars = startParagraph.toStyledChars().take(start.inlineOffset) +
            endParagraph.toStyledChars().drop(end.inlineOffset)
        blocks.take(start.blockIndex) +
            startParagraph.copy(inlines = mergedChars.toInlineTextNodes()) +
            blocks.drop(end.blockIndex + 1)
    }
    return EditOperationResult(
        box = copy(content = RichContent(blocks = updatedBlocks)),
        selection = TextSelection.cursor(start),
    )
}

private fun RichContentBox.insertPlainTextAt(position: TextCursorPosition, text: String, typingStyle: TypingStyle): EditOperationResult {
    val normalizedText = text.normalizePlainTextNewlines()
    if (normalizedText.isEmpty()) return EditOperationResult(box = this, selection = TextSelection.cursor(position))

    val blocks = content.blocks
    val paragraph = blocks[position.blockIndex] as ParagraphNode
    val existingChars = paragraph.toStyledChars()
    val before = existingChars.take(position.inlineOffset)
    val after = existingChars.drop(position.inlineOffset)
    val effectiveTypingStyle = typingStyle.effectiveAtInsertionPoint(before = before, after = after)
    val lines = normalizedText.split("\n")

    if (lines.size == 1) {
        val inserted = lines.single().toStyledChars(effectiveTypingStyle)
        val updatedParagraph = paragraph.copy(inlines = (before + inserted + after).toInlineTextNodes())
        val cursor = position.copy(inlineOffset = position.inlineOffset + inserted.size)
        return EditOperationResult(
            box = copy(content = RichContent(blocks = blocks.replaceAt(position.blockIndex, updatedParagraph))),
            selection = TextSelection.cursor(cursor),
        )
    }

    val insertedParagraphs = buildList {
        add(paragraph.copy(inlines = (before + lines.first().toStyledChars(effectiveTypingStyle)).toInlineTextNodes()))
        lines.drop(1).dropLast(1).forEach { line ->
            add(ParagraphNode(inlines = line.toStyledChars(effectiveTypingStyle).toInlineTextNodes(), listMetadata = paragraph.listMetadata))
        }
        add(paragraph.copy(inlines = (lines.last().toStyledChars(effectiveTypingStyle) + after).toInlineTextNodes()))
    }
    val updatedBlocks = blocks.take(position.blockIndex) + insertedParagraphs + blocks.drop(position.blockIndex + 1)
    val cursor = TextCursorPosition(
        blockIndex = position.blockIndex + insertedParagraphs.lastIndex,
        inlineOffset = lines.last().length,
    )
    return EditOperationResult(
        box = copy(content = RichContent(blocks = updatedBlocks)),
        selection = TextSelection.cursor(cursor),
    )
}

private fun ParagraphNode.textLength(): Int = inlines.sumOf { inline -> inline.placeholderLength() }

private fun InlineNode.placeholderLength(): Int = when (this) {
    is InlineText -> text.length
    InlineLineBreak -> 1
    is InlineFormula -> 1
    is InlineImage -> 1
}

private fun ParagraphNode.insertInlineAt(offset: Int, inline: InlineNode): ParagraphNode {
    require(offset in 0..textLength()) { "inlineOffset must be inside the paragraph text" }
    var remaining = offset
    val updated = mutableListOf<InlineNode>()
    var inserted = false
    inlines.forEach { current ->
        if (inserted) {
            updated += current
            return@forEach
        }
        when (current) {
            is InlineText -> {
                if (remaining <= current.text.length) {
                    if (remaining > 0) updated += current.copy(text = current.text.take(remaining))
                    updated += inline
                    if (remaining < current.text.length) updated += current.copy(text = current.text.drop(remaining))
                    inserted = true
                } else {
                    updated += current
                    remaining -= current.text.length
                }
            }
            else -> {
                if (remaining == 0) {
                    updated += inline
                    updated += current
                    inserted = true
                } else {
                    updated += current
                    remaining -= current.placeholderLength()
                }
            }
        }
    }
    if (!inserted) updated += inline
    return copy(inlines = updated)
}

private fun ParagraphNode.toStyledChars(): List<StyledChar> = inlines.flatMap { inline ->
    when (inline) {
        is InlineText -> inline.text.map { char ->
            StyledChar(
                value = char,
                bold = inline.bold,
                italic = inline.italic,
                underline = inline.underline,
            )
        }
        InlineLineBreak -> listOf(StyledChar('\n'))
        is InlineFormula -> listOf(StyledChar(value = '\uFFFC', atom = inline))
        is InlineImage -> listOf(StyledChar(value = '\uFFFC', atom = inline))
    }
}


private fun String.normalizePlainTextNewlines(): String = replace("\r\n", "\n").replace('\r', '\n')

private fun TypingStyle.effectiveAtInsertionPoint(before: List<StyledChar>, after: List<StyledChar>): TypingStyle {
    if (bold || italic || underline) return this
    val surrounding = before.lastOrNull() ?: after.firstOrNull() ?: return this
    return TypingStyle(
        bold = surrounding.bold,
        italic = surrounding.italic,
        underline = surrounding.underline,
    )
}

private fun String.toStyledChars(typingStyle: TypingStyle = TypingStyle()): List<StyledChar> = map {
    StyledChar(
        value = it,
        bold = typingStyle.bold,
        italic = typingStyle.italic,
        underline = typingStyle.underline,
    )
}

private fun List<StyledChar>.toInlineTextNodes(): List<InlineNode> {
    if (isEmpty()) return emptyList()
    val nodes = mutableListOf<InlineNode>()
    var currentText: InlineText? = null
    fun flushText() {
        currentText?.let { nodes += it }
        currentText = null
    }
    forEach { char ->
        val atom = char.atom
        if (atom != null) {
            flushText()
            nodes += atom
            return@forEach
        }
        val current = currentText
        currentText = if (current != null && current.bold == char.bold && current.italic == char.italic && current.underline == char.underline) {
            current.copy(text = current.text + char.value)
        } else {
            flushText()
            char.asInlineText()
        }
    }
    flushText()
    return nodes
}

private fun StyledChar.asInlineText(): InlineText = InlineText(
    text = value.toString(),
    bold = bold,
    italic = italic,
    underline = underline,
)

private fun TextSelection.listTargetBlockRange(): IntRange {
    val last = if (end.blockIndex > start.blockIndex && end.inlineOffset == 0) end.blockIndex - 1 else end.blockIndex
    return start.blockIndex..last.coerceAtLeast(start.blockIndex)
}

private fun List<BlockNode>.selectedChars(selection: TextSelection): List<StyledChar> = flatMapIndexed { index, block ->
    val paragraph = block as? ParagraphNode ?: return@flatMapIndexed emptyList()
    if (index !in selection.start.blockIndex..selection.end.blockIndex) return@flatMapIndexed emptyList()
    val from = if (index == selection.start.blockIndex) selection.start.inlineOffset else 0
    val to = if (index == selection.end.blockIndex) selection.end.inlineOffset else paragraph.textLength()
    paragraph.toStyledChars().subList(from, to)
}

private fun <T> List<T>.replaceAt(index: Int, item: T): List<T> =
    mapIndexed { currentIndex, currentItem -> if (currentIndex == index) item else currentItem }
