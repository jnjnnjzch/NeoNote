package com.neonote.engine

import com.neonote.model.BlockFormula
import com.neonote.model.BlockImage
import com.neonote.model.BlockNode
import com.neonote.model.FormulaDisplayMode
import com.neonote.model.ImageCrop
import com.neonote.model.InlineFormula
import com.neonote.model.InlineImage
import com.neonote.model.InlineLineBreak
import com.neonote.model.InlineNode
import com.neonote.model.InlineText
import com.neonote.model.ListItemMetadata
import com.neonote.model.ListKind
import com.neonote.model.ParagraphNode
import com.neonote.model.ParagraphStyle
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox
import com.neonote.model.TableCell
import com.neonote.model.TableCellAddress
import com.neonote.model.TableColumnPolicy
import com.neonote.model.TableColumnWidthMode
import com.neonote.model.TableNode
import com.neonote.model.TextAlignment
import com.neonote.model.TextCursorPosition
import com.neonote.model.TextRange
import com.neonote.model.TextSelection

/** Pure, deterministic transformations for every editable rich-content feature. */
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
        is RichContentCommand.ToggleStrikethrough -> toggleStyle(box, command.selection, InlineStyle.Strikethrough)
        is RichContentCommand.SetTextColor -> setTextAttribute(box, command.selection) { it.copy(textColorArgb = command.colorArgb) }
        is RichContentCommand.SetHighlightColor -> setTextAttribute(box, command.selection) { it.copy(highlightColorArgb = command.colorArgb) }
        is RichContentCommand.SetFontScale -> setTextAttribute(box, command.selection) { it.copy(fontScale = command.scale.coerceIn(0.5f, 4f)) }
        is RichContentCommand.SetLink -> setTextAttribute(box, command.selection) { it.copy(link = command.url?.takeIf(String::isNotBlank)) }
        is RichContentCommand.ToggleBulletList -> toggleList(box, command.selection, ListKind.Bullet)
        is RichContentCommand.ToggleNumberedList -> toggleList(box, command.selection, ListKind.Numbered)
        is RichContentCommand.ToggleTodo -> toggleList(box, command.selection, ListKind.Todo)
        is RichContentCommand.ToggleTodoCheckedState -> toggleTodoCheckedState(box, command.blockIndex)
        is RichContentCommand.SetParagraphAlignment -> setParagraphStyle(box, command.selection) { it.copy(alignment = command.alignment) }
        is RichContentCommand.SetHeadingLevel -> setParagraphStyle(box, command.selection) { it.copy(headingLevel = command.level.coerceIn(0, 3)) }
        is RichContentCommand.ChangeIndent -> setParagraphStyle(box, command.selection) {
            it.copy(indentLevel = (it.indentLevel + command.delta).coerceIn(0, 8))
        }
        is RichContentCommand.InsertInlineFormula -> insertInlineFormula(box, command.expression, command.selection)
        is RichContentCommand.InsertBlockFormula -> insertBlockFormula(box, command.expression, command.index)
        is RichContentCommand.ReplaceBlockFormulaExpression -> replaceBlockFormulaExpression(box, command.blockIndex, command.expression)
        is RichContentCommand.UpdateBlockFormula -> updateBlockFormula(box, command.blockIndex, command.expression, command.displayMode, command.numbered)
        is RichContentCommand.InsertInlineImage -> insertInlineImage(box, command.assetId, command.altText, command.selection)
        is RichContentCommand.InsertBlockImage -> insertBlockImage(box, command.assetId, command.altText, command.index)
        is RichContentCommand.UpdateBlockImage -> updateBlockImage(box, command)
        is RichContentCommand.InsertTable -> insertTable(box, command.rows, command.columns, command.index)
        is RichContentCommand.ReplacePlainText -> replacePlainText(box, command.text)
        is RichContentCommand.ReplaceParagraphText -> replaceParagraphText(box, command.blockIndex, command.text)
        is RichContentCommand.ReplaceTableCellParagraphText -> replaceTableCellParagraphText(box, command.address, command.text)
        is RichContentCommand.InsertNestedTable -> insertNestedTable(box, command.address, command.rows, command.columns)
        is RichContentCommand.AddTableRow -> addTableRow(box, command.address, command.after)
        is RichContentCommand.DeleteTableRow -> deleteTableRow(box, command.address)
        is RichContentCommand.AddTableColumn -> addTableColumn(box, command.address, command.after)
        is RichContentCommand.DeleteTableColumn -> deleteTableColumn(box, command.address)
        is RichContentCommand.SetTableColumnWidth -> setTableColumnWidth(box, command.address, command.width)
        is RichContentCommand.DeleteBlock -> deleteBlock(box, command.blockIndex)
        is RichContentCommand.MoveBlock -> moveBlock(box, command.fromIndex, command.toIndex)
    }

    public fun insertParagraph(
        box: RichContentBox,
        text: String,
        index: Int? = null,
    ): RichContentCommandResult.ContentInserted = insertParagraphBlock(box, text, index)

    public fun insertText(
        box: RichContentBox,
        text: String,
        selection: TextSelection,
        typingStyle: TypingStyle = TypingStyle(),
    ): RichContentCommandResult.ContentEdited {
        val prepared = box.ensureEditableSelection(selection)
        val deleted = prepared.box.deleteSelection(prepared.selection)
        val inserted = deleted.box.insertPlainTextAt(deleted.selection.start, text, typingStyle)
        return RichContentCommandResult.ContentEdited(inserted.box, inserted.selection)
    }

    public fun pastePlainText(
        box: RichContentBox,
        text: String,
        selection: TextSelection,
        typingStyle: TypingStyle = TypingStyle(),
    ): RichContentCommandResult.ContentEdited =
        insertText(box, text.normalizePlainTextNewlines(), selection, typingStyle)

    public fun deleteSelection(box: RichContentBox, selection: TextSelection): RichContentCommandResult.ContentEdited {
        val prepared = box.ensureEditableSelection(selection)
        val result = prepared.box.deleteSelection(prepared.selection)
        return RichContentCommandResult.ContentEdited(result.box, result.selection)
    }

    public fun deleteBackward(box: RichContentBox, selection: TextSelection): RichContentCommandResult.ContentEdited {
        val prepared = box.ensureEditableSelection(selection)
        if (!prepared.selection.isCollapsed) return deleteSelection(prepared.box, prepared.selection)
        val cursor = prepared.selection.start
        if (cursor.inlineOffset > 0) {
            return deleteSelection(
                prepared.box,
                TextSelection(TextRange(cursor.copy(inlineOffset = cursor.inlineOffset - 1), cursor)),
            )
        }
        if (cursor.blockIndex == 0) return RichContentCommandResult.ContentEdited(prepared.box, prepared.selection)
        val blocks = prepared.box.content.blocks
        val previous = blocks[cursor.blockIndex - 1] as? ParagraphNode
            ?: return RichContentCommandResult.ContentEdited(prepared.box, prepared.selection)
        val current = blocks[cursor.blockIndex] as? ParagraphNode
            ?: return RichContentCommandResult.ContentEdited(prepared.box, prepared.selection)
        val previousLength = previous.textLength()
        val merged = previous.copy(
            inlines = (previous.toStyledChars() + current.toStyledChars()).toInlineTextNodes(),
        )
        val nextBlocks = blocks.take(cursor.blockIndex - 1) + merged + blocks.drop(cursor.blockIndex + 1)
        return RichContentCommandResult.ContentEdited(
            prepared.box.copy(content = RichContent(nextBlocks)),
            TextSelection.cursor(TextCursorPosition(cursor.blockIndex - 1, previousLength)),
        )
    }

    public fun insertParagraph(box: RichContentBox, selection: TextSelection): RichContentCommandResult.ContentEdited {
        val prepared = box.ensureEditableSelection(selection)
        val deleted = prepared.box.deleteSelection(prepared.selection)
        val cursor = deleted.selection.start
        val blocks = deleted.box.content.blocks
        val paragraph = blocks[cursor.blockIndex] as ParagraphNode
        val chars = paragraph.toStyledChars()
        val nextList = paragraph.listMetadata?.let { if (it.kind == ListKind.Todo) it.copy(checked = false) else it }
        val nextBlocks = blocks.take(cursor.blockIndex) +
            paragraph.copy(inlines = chars.take(cursor.inlineOffset).toInlineTextNodes()) +
            paragraph.copy(inlines = chars.drop(cursor.inlineOffset).toInlineTextNodes(), listMetadata = nextList) +
            blocks.drop(cursor.blockIndex + 1)
        return RichContentCommandResult.ContentEdited(
            deleted.box.copy(content = RichContent(nextBlocks)),
            TextSelection.cursor(TextCursorPosition(cursor.blockIndex + 1, 0)),
        )
    }

    public fun toggleStyle(
        box: RichContentBox,
        selection: TextSelection,
        style: InlineStyle,
    ): RichContentCommandResult.ContentEdited {
        val prepared = box.ensureEditableSelection(selection)
        val ordered = prepared.selection.ordered()
        if (ordered.isCollapsed) return RichContentCommandResult.ContentEdited(prepared.box, ordered)
        val target = prepared.box.content.blocks.selectedChars(ordered).any { !it.hasStyle(style) }
        return setTextAttribute(prepared.box, ordered) { it.withStyle(style, target) }
    }

    public fun setTextAttribute(
        box: RichContentBox,
        selection: TextSelection,
        transform: (InlineTextFormat) -> InlineTextFormat,
    ): RichContentCommandResult.ContentEdited {
        val prepared = box.ensureEditableSelection(selection)
        val ordered = prepared.selection.ordered()
        if (ordered.isCollapsed) return RichContentCommandResult.ContentEdited(prepared.box, ordered)
        val nextBlocks = prepared.box.content.blocks.mapIndexed { index, block ->
            val paragraph = block as? ParagraphNode ?: return@mapIndexed block
            if (index !in ordered.start.blockIndex..ordered.end.blockIndex) return@mapIndexed block
            val from = if (index == ordered.start.blockIndex) ordered.start.inlineOffset else 0
            val to = if (index == ordered.end.blockIndex) ordered.end.inlineOffset else paragraph.textLength()
            if (from >= to) return@mapIndexed block
            paragraph.copy(
                inlines = paragraph.toStyledChars().mapIndexed { charIndex, char ->
                    if (charIndex in from until to && char.atom == null) char.copy(format = transform(char.format)) else char
                }.toInlineTextNodes(),
            )
        }
        return RichContentCommandResult.ContentEdited(
            prepared.box.copy(content = RichContent(nextBlocks)),
            ordered,
        )
    }

    public fun toggleList(
        box: RichContentBox,
        selection: TextSelection,
        kind: ListKind,
    ): RichContentCommandResult.ContentEdited {
        val prepared = box.ensureEditableSelection(selection)
        val ordered = prepared.selection.ordered()
        val range = ordered.listTargetBlockRange()
        val blocks = prepared.box.content.blocks
        val remove = range.all { (blocks.getOrNull(it) as? ParagraphNode)?.listMetadata?.kind == kind }
        val nextBlocks = blocks.mapIndexed { index, block ->
            val paragraph = block as? ParagraphNode ?: return@mapIndexed block
            if (index !in range) return@mapIndexed block
            paragraph.copy(
                listMetadata = if (remove) null else ListItemMetadata(
                    kind = kind,
                    checked = kind == ListKind.Todo && paragraph.listMetadata?.checked == true,
                ),
            )
        }
        return RichContentCommandResult.ContentEdited(prepared.box.copy(content = RichContent(nextBlocks)), ordered)
    }

    public fun toggleTodoCheckedState(box: RichContentBox, blockIndex: Int): RichContentCommandResult.ContentEdited {
        val block = box.content.blocks.getOrNull(blockIndex) as? ParagraphNode
            ?: return box.unchangedAt(blockIndex)
        val metadata = block.listMetadata?.takeIf { it.kind == ListKind.Todo }
            ?: return box.unchangedAt(blockIndex)
        val updated = block.copy(listMetadata = metadata.copy(checked = !metadata.checked))
        return RichContentCommandResult.ContentEdited(
            box.copy(content = RichContent(box.content.blocks.replaceAt(blockIndex, updated))),
            TextSelection.cursor(TextCursorPosition(blockIndex, block.textLength())),
        )
    }

    public fun setParagraphStyle(
        box: RichContentBox,
        selection: TextSelection,
        transform: (ParagraphStyle) -> ParagraphStyle,
    ): RichContentCommandResult.ContentEdited {
        val prepared = box.ensureEditableSelection(selection)
        val ordered = prepared.selection.ordered()
        val range = ordered.listTargetBlockRange()
        val nextBlocks = prepared.box.content.blocks.mapIndexed { index, block ->
            val paragraph = block as? ParagraphNode ?: return@mapIndexed block
            if (index in range) paragraph.copy(style = transform(paragraph.style)) else paragraph
        }
        return RichContentCommandResult.ContentEdited(prepared.box.copy(content = RichContent(nextBlocks)), ordered)
    }

    public fun replacePlainText(box: RichContentBox, text: String): RichContentCommandResult.ContentReplaced {
        val paragraphs = text.normalizePlainTextNewlines().split("\n").map { line ->
            ParagraphNode(inlines = listOf(InlineText(line)))
        }
        return RichContentCommandResult.ContentReplaced(box.copy(content = RichContent(paragraphs)))
    }

    public fun replaceParagraphText(
        box: RichContentBox,
        blockIndex: Int,
        text: String,
    ): RichContentCommandResult.ContentEdited {
        if (box.content.blocks.isEmpty()) {
            return insertText(
                box,
                text,
                TextSelection.cursor(TextCursorPosition(0, 0)),
            )
        }
        val paragraph = box.content.blocks.getOrNull(blockIndex) as? ParagraphNode
            ?: return box.unchangedAt(blockIndex.coerceAtLeast(0))
        val full = TextSelection(TextRange(
            TextCursorPosition(blockIndex, 0),
            TextCursorPosition(blockIndex, paragraph.textLength()),
        ))
        val deleted = box.deleteSelection(full)
        val inserted = deleted.box.insertPlainTextAt(deleted.selection.start, text, TypingStyle())
        return RichContentCommandResult.ContentEdited(inserted.box, inserted.selection)
    }

    public fun replaceTableCellParagraphText(
        box: RichContentBox,
        address: TableCellAddress,
        text: String,
    ): RichContentCommandResult.ContentEdited {
        if (!RichContentTree.isValid(box.content, address)) return box.unchangedAt(address.blockIndex)
        val normalized = text.normalizePlainTextNewlines()
        val nextContent = RichContentTree.updateCellContent(box.content, address) { cellContent ->
            val blocks = cellContent.blocks
            val index = address.contentBlockIndex.coerceIn(0, blocks.size)
            val existing = blocks.getOrNull(index)
            if (existing != null && existing !is ParagraphNode) return@updateCellContent cellContent
            val paragraph = (existing as? ParagraphNode)
                ?.copy(inlines = listOf(InlineText(normalized)))
                ?: ParagraphNode(inlines = listOf(InlineText(normalized)))
            val nextBlocks = if (index == blocks.size) blocks + paragraph else blocks.replaceAt(index, paragraph)
            cellContent.copy(blocks = nextBlocks)
        }
        return RichContentCommandResult.ContentEdited(
            box.copy(content = nextContent),
            TextSelection.cursor(TextCursorPosition(address.blockIndex, 0)),
        )
    }

    public fun insertInlineFormula(
        box: RichContentBox,
        expression: String,
        selection: TextSelection,
    ): RichContentCommandResult.ContentEdited = insertInlineNode(box, InlineFormula(expression), selection)

    public fun insertInlineImage(
        box: RichContentBox,
        assetId: String,
        altText: String? = null,
        selection: TextSelection,
    ): RichContentCommandResult.ContentEdited = insertInlineNode(box, InlineImage(assetId, altText), selection)

    public fun insertTable(
        box: RichContentBox,
        rows: Int,
        columns: Int,
        index: Int? = null,
    ): RichContentCommandResult.ContentInserted {
        require(rows > 0 && columns > 0) { "tables require at least one row and one column" }
        return insertBlock(box, TableNode(rows = List(rows) { List(columns) { TableCell() } }), index)
    }

    public fun insertNestedTable(
        box: RichContentBox,
        address: TableCellAddress,
        rows: Int,
        columns: Int,
    ): RichContentCommandResult.ContentEdited {
        require(rows > 0 && columns > 0) { "tables require at least one row and one column" }
        if (!RichContentTree.isValid(box.content, address)) return box.unchangedAt(address.blockIndex)
        val nested = TableNode(rows = List(rows) { List(columns) { TableCell() } })
        val next = RichContentTree.insertBlock(box.content, address, nested, address.contentBlockIndex)
        return RichContentCommandResult.ContentEdited(
            box.copy(content = next),
            TextSelection.cursor(TextCursorPosition(address.blockIndex, 0)),
        )
    }

    public fun addTableRow(
        box: RichContentBox,
        address: TableCellAddress,
        after: Boolean = true,
    ): RichContentCommandResult.ContentEdited = updateTable(box, address) { table ->
        val columns = table.rows.maxOfOrNull(List<TableCell>::size)?.coerceAtLeast(1) ?: 1
        val normalized = table.rows.map { row -> row + List((columns - row.size).coerceAtLeast(0)) { TableCell() } }
        val rowIndex = address.path.last().rowIndex.coerceIn(0, normalized.lastIndex.coerceAtLeast(0))
        val insertion = (rowIndex + if (after) 1 else 0).coerceIn(0, normalized.size)
        table.copy(rows = normalized.take(insertion) + listOf(List(columns) { TableCell() }) + normalized.drop(insertion))
    }

    public fun deleteTableRow(
        box: RichContentBox,
        address: TableCellAddress,
    ): RichContentCommandResult.ContentEdited = updateTable(box, address) { table ->
        if (table.rows.size <= 1) table
        else table.copy(rows = table.rows.filterIndexed { index, _ -> index != address.path.last().rowIndex })
    }

    public fun addTableColumn(
        box: RichContentBox,
        address: TableCellAddress,
        after: Boolean = true,
    ): RichContentCommandResult.ContentEdited = updateTable(box, address) { table ->
        val source = table.rows.ifEmpty { listOf(emptyList()) }
        val currentColumns = source.maxOfOrNull(List<TableCell>::size) ?: 0
        val column = address.path.last().columnIndex.coerceIn(0, currentColumns.coerceAtLeast(1) - 1)
        val insertion = (column + if (after) 1 else 0).coerceIn(0, currentColumns)
        val rows = source.map { row ->
            val normalized = row + List((currentColumns - row.size).coerceAtLeast(0)) { TableCell() }
            normalized.take(insertion) + TableCell() + normalized.drop(insertion)
        }
        val policies = table.columnPolicies.normalizedPolicies(currentColumns)
        table.copy(
            rows = rows,
            columnPolicies = policies.take(insertion) + TableColumnPolicy() + policies.drop(insertion),
        )
    }

    public fun deleteTableColumn(
        box: RichContentBox,
        address: TableCellAddress,
    ): RichContentCommandResult.ContentEdited = updateTable(box, address) { table ->
        val columns = table.rows.maxOfOrNull(List<TableCell>::size) ?: 0
        if (columns <= 1) table
        else {
            val target = address.path.last().columnIndex.coerceIn(0, columns - 1)
            table.copy(
                rows = table.rows.map { row -> row.filterIndexed { index, _ -> index != target } },
                columnPolicies = table.columnPolicies.normalizedPolicies(columns)
                    .filterIndexed { index, _ -> index != target },
            )
        }
    }

    public fun setTableColumnWidth(
        box: RichContentBox,
        address: TableCellAddress,
        width: Float?,
    ): RichContentCommandResult.ContentEdited = updateTable(box, address) { table ->
        val columns = table.rows.maxOfOrNull(List<TableCell>::size) ?: return@updateTable table
        val target = address.path.last().columnIndex.coerceIn(0, columns - 1)
        val policies = table.columnPolicies.normalizedPolicies(columns).toMutableList()
        policies[target] = if (width == null) {
            policies[target].copy(mode = TableColumnWidthMode.Auto, manualWidth = null)
        } else {
            policies[target].copy(mode = TableColumnWidthMode.Manual, manualWidth = width.coerceAtLeast(32f))
        }
        table.copy(columnPolicies = policies)
    }

    private fun updateTable(
        box: RichContentBox,
        address: TableCellAddress,
        transform: (TableNode) -> TableNode,
    ): RichContentCommandResult.ContentEdited {
        if (RichContentTree.table(box.content, address) == null) return box.unchangedAt(address.blockIndex)
        val next = RichContentTree.updateTable(box.content, address, transform)
        return RichContentCommandResult.ContentEdited(
            box.copy(content = next),
            TextSelection.cursor(TextCursorPosition(address.blockIndex, 0)),
        )
    }

    public fun insertBlockImage(
        box: RichContentBox,
        assetId: String,
        altText: String? = null,
        index: Int? = null,
    ): RichContentCommandResult.ContentInserted = insertBlock(box, BlockImage(assetId, altText), index)

    public fun updateBlockImage(
        box: RichContentBox,
        command: RichContentCommand.UpdateBlockImage,
    ): RichContentCommandResult.ContentEdited {
        val image = box.content.blocks.getOrNull(command.blockIndex) as? BlockImage
            ?: return box.unchangedAt(command.blockIndex)
        val updated = image.copy(
            assetId = command.assetId ?: image.assetId,
            altText = command.altText ?: image.altText,
            width = command.width ?: image.width,
            height = command.height ?: image.height,
            rotationDegrees = command.rotationDegrees ?: image.rotationDegrees,
            crop = (command.crop ?: image.crop).normalized(),
            caption = command.caption ?: image.caption,
        )
        return RichContentCommandResult.ContentEdited(
            box.copy(content = RichContent(box.content.blocks.replaceAt(command.blockIndex, updated))),
            TextSelection.cursor(TextCursorPosition(command.blockIndex, 0)),
        )
    }

    public fun insertBlockFormula(
        box: RichContentBox,
        expression: String,
        index: Int? = null,
    ): RichContentCommandResult.ContentInserted = insertBlock(box, BlockFormula(expression), index)

    public fun replaceBlockFormulaExpression(
        box: RichContentBox,
        blockIndex: Int,
        expression: String,
    ): RichContentCommandResult.ContentEdited =
        updateBlockFormula(box, blockIndex, expression, null, null)

    public fun updateBlockFormula(
        box: RichContentBox,
        blockIndex: Int,
        expression: String?,
        displayMode: FormulaDisplayMode?,
        numbered: Boolean?,
    ): RichContentCommandResult.ContentEdited {
        val formula = box.content.blocks.getOrNull(blockIndex) as? BlockFormula
            ?: return box.unchangedAt(blockIndex)
        val updated = formula.copy(
            expression = expression ?: formula.expression,
            displayMode = displayMode ?: formula.displayMode,
            numbered = numbered ?: formula.numbered,
        )
        return RichContentCommandResult.ContentEdited(
            box.copy(content = RichContent(box.content.blocks.replaceAt(blockIndex, updated))),
            TextSelection.cursor(TextCursorPosition(blockIndex, updated.expression.length)),
        )
    }

    public fun deleteBlock(box: RichContentBox, blockIndex: Int): RichContentCommandResult.ContentEdited {
        if (blockIndex !in box.content.blocks.indices) return box.unchangedAt(blockIndex.coerceAtLeast(0))
        var nextBlocks = box.content.blocks.filterIndexed { index, _ -> index != blockIndex }
        if (nextBlocks.isEmpty()) nextBlocks = listOf(ParagraphNode())
        val nextIndex = blockIndex.coerceAtMost(nextBlocks.lastIndex)
        val offset = (nextBlocks[nextIndex] as? ParagraphNode)?.textLength() ?: 0
        return RichContentCommandResult.ContentEdited(
            box.copy(content = RichContent(nextBlocks)),
            TextSelection.cursor(TextCursorPosition(nextIndex, offset)),
        )
    }

    public fun moveBlock(box: RichContentBox, fromIndex: Int, toIndex: Int): RichContentCommandResult.ContentEdited {
        if (fromIndex !in box.content.blocks.indices) return box.unchangedAt(fromIndex.coerceAtLeast(0))
        val without = box.content.blocks.toMutableList().also { it.removeAt(fromIndex) }
        val target = toIndex.coerceIn(0, without.size)
        without.add(target, box.content.blocks[fromIndex])
        return RichContentCommandResult.ContentEdited(
            box.copy(content = RichContent(without)),
            TextSelection.cursor(TextCursorPosition(target, 0)),
        )
    }

    private fun insertParagraphBlock(
        box: RichContentBox,
        text: String,
        index: Int? = null,
    ): RichContentCommandResult.ContentInserted = insertBlock(box, ParagraphNode(listOf(InlineText(text))), index)

    private fun insertInlineNode(
        box: RichContentBox,
        inline: InlineNode,
        selection: TextSelection,
    ): RichContentCommandResult.ContentEdited {
        val prepared = box.ensureEditableSelection(selection)
        val deleted = prepared.box.deleteSelection(prepared.selection)
        val cursor = deleted.selection.start
        val blocks = deleted.box.content.blocks
        val paragraph = blocks[cursor.blockIndex] as ParagraphNode
        val updated = paragraph.insertInlineAt(cursor.inlineOffset, inline)
        return RichContentCommandResult.ContentEdited(
            deleted.box.copy(content = RichContent(blocks.replaceAt(cursor.blockIndex, updated))),
            TextSelection.cursor(cursor.copy(inlineOffset = cursor.inlineOffset + inline.placeholderLength())),
        )
    }

    private fun insertBlock(
        box: RichContentBox,
        block: BlockNode,
        index: Int?,
    ): RichContentCommandResult.ContentInserted {
        val target = (index ?: box.content.blocks.size).coerceIn(0, box.content.blocks.size)
        val blocks = box.content.blocks.take(target) + block + box.content.blocks.drop(target)
        return RichContentCommandResult.ContentInserted(box.copy(content = RichContent(blocks)), block)
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

public enum class InlineStyle { Bold, Italic, Underline, Strikethrough }

public sealed interface RichContentCommand {
    public data class InsertText(val text: String, val selection: TextSelection, val typingStyle: TypingStyle = TypingStyle()) : RichContentCommand
    public data class PastePlainText(val text: String, val selection: TextSelection, val typingStyle: TypingStyle = TypingStyle()) : RichContentCommand
    public data class DeleteBackward(val selection: TextSelection) : RichContentCommand
    public data class DeleteSelection(val selection: TextSelection) : RichContentCommand
    public data class InsertParagraph(val text: String = "", val index: Int? = null, val selection: TextSelection? = null) : RichContentCommand
    public data class ToggleBold(val selection: TextSelection) : RichContentCommand
    public data class ToggleItalic(val selection: TextSelection) : RichContentCommand
    public data class ToggleUnderline(val selection: TextSelection) : RichContentCommand
    public data class ToggleStrikethrough(val selection: TextSelection) : RichContentCommand
    public data class SetTextColor(val selection: TextSelection, val colorArgb: Int?) : RichContentCommand
    public data class SetHighlightColor(val selection: TextSelection, val colorArgb: Int?) : RichContentCommand
    public data class SetFontScale(val selection: TextSelection, val scale: Float) : RichContentCommand
    public data class SetLink(val selection: TextSelection, val url: String?) : RichContentCommand
    public data class ToggleBulletList(val selection: TextSelection) : RichContentCommand
    public data class ToggleNumberedList(val selection: TextSelection) : RichContentCommand
    public data class ToggleTodo(val selection: TextSelection) : RichContentCommand
    public data class ToggleTodoCheckedState(val blockIndex: Int) : RichContentCommand
    public data class SetParagraphAlignment(val selection: TextSelection, val alignment: TextAlignment) : RichContentCommand
    public data class SetHeadingLevel(val selection: TextSelection, val level: Int) : RichContentCommand
    public data class ChangeIndent(val selection: TextSelection, val delta: Int) : RichContentCommand
    public data class InsertInlineFormula(val expression: String, val selection: TextSelection) : RichContentCommand
    public data class InsertBlockFormula(val expression: String, val index: Int? = null) : RichContentCommand
    public data class ReplaceBlockFormulaExpression(val blockIndex: Int, val expression: String) : RichContentCommand
    public data class UpdateBlockFormula(
        val blockIndex: Int,
        val expression: String? = null,
        val displayMode: FormulaDisplayMode? = null,
        val numbered: Boolean? = null,
    ) : RichContentCommand
    public data class InsertInlineImage(val assetId: String, val altText: String? = null, val selection: TextSelection) : RichContentCommand
    public data class InsertBlockImage(val assetId: String, val altText: String? = null, val index: Int? = null) : RichContentCommand
    public data class UpdateBlockImage(
        val blockIndex: Int,
        val assetId: String? = null,
        val altText: String? = null,
        val width: Float? = null,
        val height: Float? = null,
        val rotationDegrees: Float? = null,
        val crop: ImageCrop? = null,
        val caption: String? = null,
    ) : RichContentCommand
    public data class InsertTable(val rows: Int, val columns: Int, val index: Int? = null) : RichContentCommand
    public data class ReplacePlainText(val text: String) : RichContentCommand
    public data class ReplaceParagraphText(val blockIndex: Int, val text: String) : RichContentCommand
    public data class ReplaceTableCellParagraphText(val address: TableCellAddress, val text: String) : RichContentCommand
    public data class InsertNestedTable(val address: TableCellAddress, val rows: Int, val columns: Int) : RichContentCommand
    public data class AddTableRow(val address: TableCellAddress, val after: Boolean = true) : RichContentCommand
    public data class DeleteTableRow(val address: TableCellAddress) : RichContentCommand
    public data class AddTableColumn(val address: TableCellAddress, val after: Boolean = true) : RichContentCommand
    public data class DeleteTableColumn(val address: TableCellAddress) : RichContentCommand
    public data class SetTableColumnWidth(val address: TableCellAddress, val width: Float?) : RichContentCommand
    public data class DeleteBlock(val blockIndex: Int) : RichContentCommand
    public data class MoveBlock(val fromIndex: Int, val toIndex: Int) : RichContentCommand
}

public sealed interface RichContentCommandResult {
    public data class ContentInserted(val box: RichContentBox, val block: BlockNode) : RichContentCommandResult
    public data class ContentReplaced(val box: RichContentBox) : RichContentCommandResult
    public data class ContentEdited(val box: RichContentBox, val selection: TextSelection) : RichContentCommandResult
}

private data class EditableSelection(val box: RichContentBox, val selection: TextSelection)
private data class EditOperationResult(val box: RichContentBox, val selection: TextSelection)

public data class InlineTextFormat(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val textColorArgb: Int? = null,
    val highlightColorArgb: Int? = null,
    val fontScale: Float = 1f,
    val link: String? = null,
) {
    public fun hasStyle(style: InlineStyle): Boolean = when (style) {
        InlineStyle.Bold -> bold
        InlineStyle.Italic -> italic
        InlineStyle.Underline -> underline
        InlineStyle.Strikethrough -> strikethrough
    }

    public fun withStyle(style: InlineStyle, enabled: Boolean): InlineTextFormat = when (style) {
        InlineStyle.Bold -> copy(bold = enabled)
        InlineStyle.Italic -> copy(italic = enabled)
        InlineStyle.Underline -> copy(underline = enabled)
        InlineStyle.Strikethrough -> copy(strikethrough = enabled)
    }
}

private data class StyledChar(
    val value: Char,
    val format: InlineTextFormat = InlineTextFormat(),
    val atom: InlineNode? = null,
) {
    fun hasStyle(style: InlineStyle): Boolean = atom == null && format.hasStyle(style)
    fun withStyle(style: InlineStyle, enabled: Boolean): StyledChar =
        if (atom != null) this else copy(format = format.withStyle(style, enabled))
}

private fun RichContentBox.ensureEditableSelection(selection: TextSelection): EditableSelection {
    val next = if (content.blocks.isEmpty()) copy(content = RichContent(listOf(ParagraphNode()))) else this
    next.validatePosition(selection.start)
    next.validatePosition(selection.end)
    return EditableSelection(next, selection)
}

private fun RichContentBox.validatePosition(position: TextCursorPosition) {
    val paragraph = content.blocks.getOrNull(position.blockIndex) as? ParagraphNode
    require(paragraph != null) { "text editing commands require an existing paragraph block" }
    require(position.inlineOffset in 0..paragraph.textLength()) { "inlineOffset must be inside paragraph text" }
}

private fun RichContentBox.deleteSelection(selection: TextSelection): EditOperationResult {
    val ordered = selection.ordered()
    if (ordered.isCollapsed) return EditOperationResult(this, ordered)
    val blocks = content.blocks
    val start = ordered.start
    val end = ordered.end
    val startParagraph = blocks[start.blockIndex] as ParagraphNode
    val endParagraph = blocks[end.blockIndex] as ParagraphNode
    val nextBlocks = if (start.blockIndex == end.blockIndex) {
        val chars = startParagraph.toStyledChars()
        blocks.replaceAt(start.blockIndex, startParagraph.copy(
            inlines = (chars.take(start.inlineOffset) + chars.drop(end.inlineOffset)).toInlineTextNodes(),
        ))
    } else {
        val merged = startParagraph.toStyledChars().take(start.inlineOffset) + endParagraph.toStyledChars().drop(end.inlineOffset)
        blocks.take(start.blockIndex) + startParagraph.copy(inlines = merged.toInlineTextNodes()) + blocks.drop(end.blockIndex + 1)
    }
    return EditOperationResult(copy(content = RichContent(nextBlocks)), TextSelection.cursor(start))
}

private fun RichContentBox.insertPlainTextAt(
    position: TextCursorPosition,
    text: String,
    typingStyle: TypingStyle,
): EditOperationResult {
    val normalized = text.normalizePlainTextNewlines()
    if (normalized.isEmpty()) return EditOperationResult(this, TextSelection.cursor(position))
    val blocks = content.blocks
    val paragraph = blocks[position.blockIndex] as ParagraphNode
    val chars = paragraph.toStyledChars()
    val before = chars.take(position.inlineOffset)
    val after = chars.drop(position.inlineOffset)
    val format = typingStyle.effectiveFormat(before.lastOrNull()?.format ?: after.firstOrNull()?.format)
    val lines = normalized.split("\n")
    if (lines.size == 1) {
        val inserted = lines.single().toStyledChars(format)
        val updated = paragraph.copy(inlines = (before + inserted + after).toInlineTextNodes())
        return EditOperationResult(
            copy(content = RichContent(blocks.replaceAt(position.blockIndex, updated))),
            TextSelection.cursor(position.copy(inlineOffset = position.inlineOffset + inserted.size)),
        )
    }
    val paragraphs = buildList {
        add(paragraph.copy(inlines = (before + lines.first().toStyledChars(format)).toInlineTextNodes()))
        lines.drop(1).dropLast(1).forEach { line ->
            add(paragraph.copy(inlines = line.toStyledChars(format).toInlineTextNodes(), listMetadata = paragraph.listMetadata))
        }
        add(paragraph.copy(inlines = (lines.last().toStyledChars(format) + after).toInlineTextNodes()))
    }
    val nextBlocks = blocks.take(position.blockIndex) + paragraphs + blocks.drop(position.blockIndex + 1)
    return EditOperationResult(
        copy(content = RichContent(nextBlocks)),
        TextSelection.cursor(TextCursorPosition(position.blockIndex + paragraphs.lastIndex, lines.last().length)),
    )
}

private fun ParagraphNode.textLength(): Int = inlines.sumOf(InlineNode::placeholderLength)

private fun InlineNode.placeholderLength(): Int = when (this) {
    is InlineText -> text.length
    InlineLineBreak, is InlineFormula, is InlineImage -> 1
}

private fun ParagraphNode.insertInlineAt(offset: Int, inline: InlineNode): ParagraphNode {
    require(offset in 0..textLength())
    var remaining = offset
    val next = mutableListOf<InlineNode>()
    var inserted = false
    inlines.forEach { current ->
        if (inserted) {
            next += current
        } else if (current is InlineText) {
            if (remaining <= current.text.length) {
                if (remaining > 0) next += current.copy(text = current.text.take(remaining))
                next += inline
                if (remaining < current.text.length) next += current.copy(text = current.text.drop(remaining))
                inserted = true
            } else {
                next += current
                remaining -= current.text.length
            }
        } else if (remaining == 0) {
            next += inline
            next += current
            inserted = true
        } else {
            next += current
            remaining -= current.placeholderLength()
        }
    }
    if (!inserted) next += inline
    return copy(inlines = next)
}

private fun ParagraphNode.toStyledChars(): List<StyledChar> = inlines.flatMap { inline ->
    when (inline) {
        is InlineText -> inline.text.map { char ->
            StyledChar(char, InlineTextFormat(
                bold = inline.bold,
                italic = inline.italic,
                underline = inline.underline,
                strikethrough = inline.strikethrough,
                textColorArgb = inline.textColorArgb,
                highlightColorArgb = inline.highlightColorArgb,
                fontScale = inline.fontScale,
                link = inline.link,
            ))
        }
        InlineLineBreak -> listOf(StyledChar('\n'))
        is InlineFormula, is InlineImage -> listOf(StyledChar('\uFFFC', atom = inline))
    }
}

private fun List<StyledChar>.toInlineTextNodes(): List<InlineNode> {
    if (isEmpty()) return emptyList()
    val nodes = mutableListOf<InlineNode>()
    var current: InlineText? = null
    fun flush() {
        current?.let(nodes::add)
        current = null
    }
    forEach { char ->
        if (char.atom != null) {
            flush()
            nodes += char.atom
        } else {
            val f = char.format
            current = if (current?.matches(f) == true) {
                current!!.copy(text = current!!.text + char.value)
            } else {
                flush()
                InlineText(
                    text = char.value.toString(),
                    bold = f.bold,
                    italic = f.italic,
                    underline = f.underline,
                    strikethrough = f.strikethrough,
                    textColorArgb = f.textColorArgb,
                    highlightColorArgb = f.highlightColorArgb,
                    fontScale = f.fontScale,
                    link = f.link,
                )
            }
        }
    }
    flush()
    return nodes
}

private fun InlineText.matches(format: InlineTextFormat): Boolean =
    bold == format.bold && italic == format.italic && underline == format.underline &&
        strikethrough == format.strikethrough && textColorArgb == format.textColorArgb &&
        highlightColorArgb == format.highlightColorArgb && fontScale == format.fontScale && link == format.link

private fun String.toStyledChars(format: InlineTextFormat): List<StyledChar> = map { StyledChar(it, format) }

private fun TypingStyle.effectiveFormat(surrounding: InlineTextFormat?): InlineTextFormat {
    val base = surrounding ?: InlineTextFormat()
    return InlineTextFormat(
        bold = if (isExplicitBold) bold else base.bold,
        italic = if (isExplicitItalic) italic else base.italic,
        underline = if (isExplicitUnderline) underline else base.underline,
        strikethrough = if (isExplicitStrikethrough) strikethrough else base.strikethrough,
        textColorArgb = textColorArgb ?: base.textColorArgb,
        highlightColorArgb = highlightColorArgb ?: base.highlightColorArgb,
        fontScale = fontScale ?: base.fontScale,
        link = link ?: base.link,
    )
}

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

private fun RichContentBox.unchangedAt(blockIndex: Int): RichContentCommandResult.ContentEdited {
    val index = blockIndex.coerceIn(0, content.blocks.lastIndex.coerceAtLeast(0))
    return RichContentCommandResult.ContentEdited(this, TextSelection.cursor(TextCursorPosition(index, 0)))
}

private fun List<TableColumnPolicy>.normalizedPolicies(columns: Int): List<TableColumnPolicy> =
    take(columns) + List((columns - size).coerceAtLeast(0)) { TableColumnPolicy() }

private fun String.normalizePlainTextNewlines(): String = replace("\r\n", "\n").replace('\r', '\n')
