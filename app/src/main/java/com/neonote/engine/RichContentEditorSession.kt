package com.neonote.engine

import com.neonote.model.BlockFormula
import com.neonote.model.BlockImage
import com.neonote.model.FormulaDisplayMode
import com.neonote.model.ImageCrop
import com.neonote.model.InlineFormula
import com.neonote.model.InlineImage
import com.neonote.model.InlineLineBreak
import com.neonote.model.InlineText
import com.neonote.model.ListKind
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContentBox
import com.neonote.model.TableCellAddress
import com.neonote.model.TextAlignment
import com.neonote.model.TextCursorPosition
import com.neonote.model.TextRange
import com.neonote.model.TextSelection

/** Stateful platform-input adapter over the pure [RichContentEngine]. */
public class RichContentEditorSession(
    initialBox: RichContentBox,
    initialSelection: TextSelection = TextSelection.cursor(initialBox.content.endCursorPosition()),
    private val engine: RichContentEngine = RichContentEngine(),
) {
    public var box: RichContentBox = initialBox
        private set

    public var selection: TextSelection = initialSelection.coerceInto(initialBox)
        private set

    public var typingStyle: TypingStyle = TypingStyle()
        private set

    public var localEditableBuffer: String = initialBox.toPlainText()
        private set

    public var activeBlockIndex: Int = initialSelection.start.blockIndex.coerceAtLeast(0)
        private set

    public var activeTarget: ActiveRichContentTarget = ActiveRichContentTarget.Paragraph(activeBlockIndex)
        private set

    public val activeFormulaBlockIndex: Int?
        get() = (activeTarget as? ActiveRichContentTarget.FormulaBlock)?.blockIndex

    public var activeParagraphSelection: ParagraphTextSelection =
        ParagraphTextSelection.cursor(initialSelection.start.inlineOffset)
        private set

    public val activeParagraphCaret: Int?
        get() = activeParagraphSelection.takeIf { it.isCollapsed }?.start

    public var localParagraphEditableBuffer: String = initialBox.paragraphPlainTextOrEmpty(activeBlockIndex)
        private set

    private val mutablePendingCommands: MutableList<RichContentCommand> = mutableListOf()
    public val pendingCommands: List<RichContentCommand> get() = mutablePendingCommands.toList()

    public fun focus(updatedBox: RichContentBox = box) {
        box = updatedBox
        localEditableBuffer = updatedBox.toPlainText()
        selection = selection.coerceInto(updatedBox)
        activeTarget = activeTarget.coerceInto(updatedBox)
        activeBlockIndex = activeTarget.blockIndexForSession()
        syncActiveParagraphFromSelection()
    }

    public fun focusParagraph(blockIndex: Int, selectionStart: Int = 0, selectionEnd: Int = selectionStart) {
        activeBlockIndex = box.coerceParagraphBlockIndex(blockIndex)
        activeTarget = ActiveRichContentTarget.Paragraph(activeBlockIndex)
        setActiveParagraphSelection(selectionStart, selectionEnd)
    }

    public fun focusTableCell(address: TableCellAddress) {
        if (!RichContentTree.isValid(box.content, address)) return
        activeBlockIndex = address.blockIndex.coerceAtLeast(0)
        activeTarget = ActiveRichContentTarget.TableCell(address)
        localParagraphEditableBuffer = tableCellPlainText(address)
    }

    public fun focusFormulaBlock(blockIndex: Int) {
        if (box.content.blocks.getOrNull(blockIndex) !is BlockFormula) return
        activeBlockIndex = blockIndex
        activeTarget = ActiveRichContentTarget.FormulaBlock(blockIndex)
        selection = TextSelection.cursor(TextCursorPosition(blockIndex, 0))
        syncActiveParagraphFromSelection()
    }

    public fun blurFormulaBlock(blockIndex: Int? = null) {
        val target = activeTarget as? ActiveRichContentTarget.FormulaBlock ?: return
        if (blockIndex != null && target.blockIndex != blockIndex) return
        activeBlockIndex = box.coerceParagraphBlockIndex(target.blockIndex)
        activeTarget = ActiveRichContentTarget.Paragraph(activeBlockIndex)
        syncActiveParagraphFromSelection()
    }

    public fun setActiveParagraphSelection(start: Int, end: Int = start) {
        activeTarget = ActiveRichContentTarget.Paragraph(activeBlockIndex)
        val length = box.paragraphTextLength(activeBlockIndex)
        activeParagraphSelection = ParagraphTextSelection(start.coerceIn(0, length), end.coerceIn(0, length))
        selection = TextSelection(TextRange(
            TextCursorPosition(activeBlockIndex, activeParagraphSelection.start),
            TextCursorPosition(activeBlockIndex, activeParagraphSelection.end),
        ))
        localParagraphEditableBuffer = box.paragraphPlainTextOrEmpty(activeBlockIndex)
    }

    public fun setSelection(selection: TextSelection) {
        this.selection = selection.coerceInto(box)
        activeBlockIndex = box.coerceParagraphBlockIndex(this.selection.start.blockIndex)
        activeTarget = ActiveRichContentTarget.Paragraph(activeBlockIndex)
        syncActiveParagraphFromSelection()
    }

    public fun setSelectionFromPlainOffsets(start: Int, end: Int = start) {
        setSelection(TextSelection(TextRange(
            box.cursorPositionAtPlainOffset(start),
            box.cursorPositionAtPlainOffset(end),
        )))
    }

    public fun blurCommit(updatedBox: RichContentBox = box): RichContentEditorCommit {
        if (updatedBox != box) focus(updatedBox)
        return RichContentEditorCommit(box, selection, drainPendingCommands())
    }

    public fun insertText(text: String): RichContentEditorEdit =
        applyCommand(RichContentCommand.InsertText(text, selection, typingStyle))

    public fun pastePlainText(text: String): RichContentEditorEdit =
        applyCommand(RichContentCommand.PastePlainText(text, selection, typingStyle))

    public fun insertParagraph(): RichContentEditorEdit =
        applyCommand(RichContentCommand.InsertParagraph(selection = selection))

    public fun deleteBackward(): RichContentEditorEdit =
        applyCommand(RichContentCommand.DeleteBackward(selection))

    public fun deleteSelectedRange(): RichContentEditorEdit =
        applyCommand(RichContentCommand.DeleteSelection(selection))

    public fun toggleStyle(style: InlineStyle): RichContentEditorEdit {
        if (selection.isCollapsed) {
            typingStyle = typingStyle.toggled(style)
            return noCommandEdit()
        }
        return applyCommand(when (style) {
            InlineStyle.Bold -> RichContentCommand.ToggleBold(selection)
            InlineStyle.Italic -> RichContentCommand.ToggleItalic(selection)
            InlineStyle.Underline -> RichContentCommand.ToggleUnderline(selection)
            InlineStyle.Strikethrough -> RichContentCommand.ToggleStrikethrough(selection)
        })
    }

    public fun toggleBold(): RichContentEditorEdit = toggleStyle(InlineStyle.Bold)
    public fun toggleItalic(): RichContentEditorEdit = toggleStyle(InlineStyle.Italic)
    public fun toggleUnderline(): RichContentEditorEdit = toggleStyle(InlineStyle.Underline)
    public fun toggleStrikethrough(): RichContentEditorEdit = toggleStyle(InlineStyle.Strikethrough)

    public fun setTextColor(colorArgb: Int?): RichContentEditorEdit {
        if (selection.isCollapsed) {
            typingStyle = typingStyle.copy(textColorArgb = colorArgb)
            return noCommandEdit()
        }
        return applyCommand(RichContentCommand.SetTextColor(selection, colorArgb))
    }

    public fun setHighlightColor(colorArgb: Int?): RichContentEditorEdit {
        if (selection.isCollapsed) {
            typingStyle = typingStyle.copy(highlightColorArgb = colorArgb)
            return noCommandEdit()
        }
        return applyCommand(RichContentCommand.SetHighlightColor(selection, colorArgb))
    }

    public fun setFontScale(scale: Float): RichContentEditorEdit {
        if (selection.isCollapsed) {
            typingStyle = typingStyle.copy(fontScale = scale.coerceIn(0.5f, 4f))
            return noCommandEdit()
        }
        return applyCommand(RichContentCommand.SetFontScale(selection, scale))
    }

    public fun setLink(url: String?): RichContentEditorEdit {
        if (selection.isCollapsed) {
            typingStyle = typingStyle.copy(link = url?.takeIf(String::isNotBlank))
            return noCommandEdit()
        }
        return applyCommand(RichContentCommand.SetLink(selection, url))
    }

    public fun toggleList(kind: ListKind): RichContentEditorEdit = applyCommand(when (kind) {
        ListKind.Bullet -> RichContentCommand.ToggleBulletList(selection)
        ListKind.Numbered -> RichContentCommand.ToggleNumberedList(selection)
        ListKind.Todo -> RichContentCommand.ToggleTodo(selection)
    })

    public fun toggleBulletList(): RichContentEditorEdit = toggleList(ListKind.Bullet)
    public fun toggleNumberedList(): RichContentEditorEdit = toggleList(ListKind.Numbered)
    public fun toggleTodo(): RichContentEditorEdit = toggleList(ListKind.Todo)
    public fun toggleTodoCheckedState(blockIndex: Int): RichContentEditorEdit =
        applyCommand(RichContentCommand.ToggleTodoCheckedState(blockIndex))

    public fun setParagraphAlignment(alignment: TextAlignment): RichContentEditorEdit =
        applyCommand(RichContentCommand.SetParagraphAlignment(selection, alignment))

    public fun setHeadingLevel(level: Int): RichContentEditorEdit =
        applyCommand(RichContentCommand.SetHeadingLevel(selection, level))

    public fun changeIndent(delta: Int): RichContentEditorEdit =
        applyCommand(RichContentCommand.ChangeIndent(selection, delta))

    public fun insertTablePlaceholder(rows: Int = 2, columns: Int = 2): RichContentEditorEdit {
        val index = activeBlockInsertionIndex()
        val commands = mutableListOf<RichContentCommand>()
        commands += applyCommand(RichContentCommand.InsertTable(rows, columns, index)).commands
        commands += ensureTrailingParagraphAfter(index).commands
        activeBlockIndex = index
        activeTarget = ActiveRichContentTarget.TableCell(TableCellAddress(index, 0, 0))
        return RichContentEditorEdit(box, selection, commands)
    }

    public fun insertNestedTable(address: TableCellAddress, rows: Int = 2, columns: Int = 2): RichContentEditorEdit {
        focusTableCell(address)
        val edit = applyCommandPreservingTarget(RichContentCommand.InsertNestedTable(address, rows, columns))
        val nestedAddress = address.child(
            tableBlockIndex = address.contentBlockIndex,
            rowIndex = 0,
            columnIndex = 0,
        )
        if (RichContentTree.isValid(box.content, nestedAddress)) {
            activeTarget = ActiveRichContentTarget.TableCell(nestedAddress)
            activeBlockIndex = nestedAddress.blockIndex
        }
        return edit
    }

    public fun addTableRow(address: TableCellAddress, after: Boolean = true): RichContentEditorEdit =
        tableCommand(address, RichContentCommand.AddTableRow(address, after))

    public fun deleteTableRow(address: TableCellAddress): RichContentEditorEdit =
        tableCommand(address, RichContentCommand.DeleteTableRow(address))

    public fun addTableColumn(address: TableCellAddress, after: Boolean = true): RichContentEditorEdit =
        tableCommand(address, RichContentCommand.AddTableColumn(address, after))

    public fun deleteTableColumn(address: TableCellAddress): RichContentEditorEdit =
        tableCommand(address, RichContentCommand.DeleteTableColumn(address))

    public fun setTableColumnWidth(address: TableCellAddress, width: Float?): RichContentEditorEdit =
        tableCommand(address, RichContentCommand.SetTableColumnWidth(address, width))

    private fun tableCommand(address: TableCellAddress, command: RichContentCommand): RichContentEditorEdit {
        focusTableCell(address)
        val edit = applyCommandPreservingTarget(command)
        activeTarget = if (RichContentTree.isValid(box.content, address)) {
            ActiveRichContentTarget.TableCell(address)
        } else {
            ActiveRichContentTarget.Paragraph(box.coerceParagraphBlockIndex(address.blockIndex))
        }
        return edit
    }

    public fun insertBlockFormulaPlaceholder(expression: String = ""): RichContentEditorEdit {
        val index = activeBlockInsertionIndex()
        val commands = mutableListOf<RichContentCommand>()
        commands += applyCommand(RichContentCommand.InsertBlockFormula(expression, index)).commands
        commands += ensureTrailingParagraphAfter(index).commands
        activeBlockIndex = index
        activeTarget = ActiveRichContentTarget.FormulaBlock(index)
        selection = TextSelection.cursor(TextCursorPosition(index, expression.length))
        return RichContentEditorEdit(box, selection, commands)
    }

    public fun insertBlockImagePlaceholder(assetId: String, altText: String? = null): RichContentEditorEdit {
        val index = activeBlockInsertionIndex()
        val commands = mutableListOf<RichContentCommand>()
        commands += applyCommand(RichContentCommand.InsertBlockImage(assetId, altText, index)).commands
        commands += ensureTrailingParagraphAfter(index).commands
        activeBlockIndex = (index + 1).coerceAtMost(box.content.blocks.lastIndex)
        activeTarget = ActiveRichContentTarget.Paragraph(box.coerceParagraphBlockIndex(activeBlockIndex))
        syncActiveParagraphFromSelection()
        return RichContentEditorEdit(box, selection, commands)
    }

    public fun replaceFormulaExpression(blockIndex: Int, expression: String): RichContentEditorEdit {
        focusFormulaBlock(blockIndex)
        return applyCommandPreservingTarget(RichContentCommand.ReplaceBlockFormulaExpression(blockIndex, expression)).also {
            activeBlockIndex = blockIndex
            activeTarget = ActiveRichContentTarget.FormulaBlock(blockIndex)
        }
    }

    public fun updateFormula(
        blockIndex: Int,
        expression: String? = null,
        displayMode: FormulaDisplayMode? = null,
        numbered: Boolean? = null,
    ): RichContentEditorEdit {
        focusFormulaBlock(blockIndex)
        return applyCommandPreservingTarget(
            RichContentCommand.UpdateBlockFormula(blockIndex, expression, displayMode, numbered),
        ).also { activeTarget = ActiveRichContentTarget.FormulaBlock(blockIndex) }
    }

    public fun updateImage(
        blockIndex: Int,
        assetId: String? = null,
        altText: String? = null,
        width: Float? = null,
        height: Float? = null,
        rotationDegrees: Float? = null,
        crop: ImageCrop? = null,
        caption: String? = null,
    ): RichContentEditorEdit = applyCommand(
        RichContentCommand.UpdateBlockImage(
            blockIndex, assetId, altText, width, height, rotationDegrees, crop, caption,
        ),
    )

    public fun deleteBlock(blockIndex: Int): RichContentEditorEdit =
        applyCommand(RichContentCommand.DeleteBlock(blockIndex))

    public fun moveBlock(fromIndex: Int, toIndex: Int): RichContentEditorEdit =
        applyCommand(RichContentCommand.MoveBlock(fromIndex, toIndex))

    public fun replaceFromPlatformParagraphInput(
        blockIndex: Int,
        previousText: String,
        nextText: String,
        selectionStartOffset: Int,
        selectionEndOffset: Int = selectionStartOffset,
    ): RichContentEditorEdit {
        focusParagraph(blockIndex, activeParagraphSelection.start, activeParagraphSelection.end)
        if (previousText != localParagraphEditableBuffer) localParagraphEditableBuffer = previousText
        if (nextText == localParagraphEditableBuffer) {
            setActiveParagraphSelection(selectionStartOffset, selectionEndOffset)
            return noCommandEdit()
        }
        val diff = TextDiff.between(localParagraphEditableBuffer, nextText)
        selection = TextSelection(TextRange(
            TextCursorPosition(activeBlockIndex, diff.deletedStart),
            TextCursorPosition(activeBlockIndex, diff.deletedEnd),
        ))
        val edit = applyPlatformDiff(diff)
        val targetBlock = selection.start.blockIndex
        activeBlockIndex = box.coerceParagraphBlockIndex(targetBlock)
        val start = if (activeBlockIndex == targetBlock) selection.start.inlineOffset else selectionStartOffset
        val end = if (activeBlockIndex == selection.end.blockIndex) selection.end.inlineOffset else start
        setActiveParagraphSelection(start, end)
        return edit
    }

    public fun replaceParagraphFromPlatformCompositionFallback(
        blockIndex: Int,
        nextText: String,
        selectionStartOffset: Int,
        selectionEndOffset: Int = selectionStartOffset,
    ): RichContentEditorEdit {
        activeBlockIndex = box.coerceParagraphBlockIndex(blockIndex)
        activeTarget = ActiveRichContentTarget.Paragraph(activeBlockIndex)
        if (box.paragraphHasInlineAtom(activeBlockIndex)) {
            setActiveParagraphSelection(selectionStartOffset, selectionEndOffset)
            return noCommandEdit()
        }
        val edit = applyCommand(RichContentCommand.ReplaceParagraphText(activeBlockIndex, nextText))
        activeBlockIndex = box.coerceParagraphBlockIndex(edit.selection.start.blockIndex)
        activeTarget = ActiveRichContentTarget.Paragraph(activeBlockIndex)
        setActiveParagraphSelection(selectionStartOffset, selectionEndOffset)
        return edit
    }

    public fun replaceFromPlatformInput(
        previousText: String,
        nextText: String,
        selectionStartPlainOffset: Int,
        selectionEndPlainOffset: Int = selectionStartPlainOffset,
    ): RichContentEditorEdit {
        if (previousText != localEditableBuffer) localEditableBuffer = previousText
        if (nextText == localEditableBuffer) {
            setSelectionFromPlainOffsets(selectionStartPlainOffset, selectionEndPlainOffset)
            return noCommandEdit()
        }
        val diff = TextDiff.between(localEditableBuffer, nextText)
        selection = TextSelection(TextRange(
            box.cursorPositionAtPlainOffset(diff.deletedStart),
            box.cursorPositionAtPlainOffset(diff.deletedEnd),
        ))
        val edit = applyPlatformDiff(diff)
        setSelectionFromPlainOffsets(
            nextText.modelPlainOffsetOf(selectionStartPlainOffset),
            nextText.modelPlainOffsetOf(selectionEndPlainOffset),
        )
        return edit.copy(selection = selection)
    }

    public fun replaceFromPlatformCompositionFallback(
        nextText: String,
        selectionStartPlainOffset: Int,
        selectionEndPlainOffset: Int = selectionStartPlainOffset,
    ): RichContentEditorEdit {
        val edit = applyCommand(RichContentCommand.ReplacePlainText(nextText))
        setSelectionFromPlainOffsets(selectionStartPlainOffset, selectionEndPlainOffset)
        return edit.copy(selection = selection)
    }

    private fun applyPlatformDiff(diff: TextDiff): RichContentEditorEdit = when {
        diff.insertedText == "\n" && diff.deletedStart == diff.deletedEnd ->
            applyCommand(RichContentCommand.InsertParagraph(selection = selection))
        diff.insertedText.isEmpty() && diff.deletedEnd > diff.deletedStart -> {
            if (diff.deletedEnd - diff.deletedStart == 1) {
                selection = TextSelection.cursor(selection.end)
                applyCommand(RichContentCommand.DeleteBackward(selection))
            } else {
                applyCommand(RichContentCommand.DeleteSelection(selection))
            }
        }
        else -> applyCommand(
            if ('\n' in diff.insertedText || '\r' in diff.insertedText) {
                RichContentCommand.PastePlainText(diff.insertedText, selection, typingStyle)
            } else {
                RichContentCommand.InsertText(diff.insertedText, selection, typingStyle)
            },
        )
    }

    public fun replaceTableCellParagraphFromPlatformInput(
        address: TableCellAddress,
        nextText: String,
    ): RichContentEditorEdit {
        focusTableCell(address)
        val edit = applyCommandPreservingTarget(RichContentCommand.ReplaceTableCellParagraphText(address, nextText))
        activeBlockIndex = address.blockIndex
        activeTarget = ActiveRichContentTarget.TableCell(address)
        localParagraphEditableBuffer = nextText
        return edit
    }

    public fun tableCellPlainText(address: TableCellAddress): String =
        (RichContentTree.block(box.content, address) as? ParagraphNode)?.plainText().orEmpty()

    public fun drainPendingCommands(): List<RichContentCommand> = pendingCommands.also { mutablePendingCommands.clear() }

    public fun activeBlockInsertionIndex(): Int =
        (activeTarget.blockIndexForSession() + 1).coerceIn(0, box.content.blocks.size)

    private fun ensureTrailingParagraphAfter(blockIndex: Int): RichContentEditorEdit {
        val index = (blockIndex + 1).coerceIn(0, box.content.blocks.size)
        return if (box.content.blocks.getOrNull(index) is ParagraphNode) noCommandEdit()
        else applyCommand(RichContentCommand.InsertParagraph(index = index))
    }

    private fun applyCommand(command: RichContentCommand): RichContentEditorEdit {
        val result = engine.execute(box, command)
        updateFromResult(result)
        activeBlockIndex = box.coerceParagraphBlockIndex(selection.start.blockIndex)
        activeTarget = ActiveRichContentTarget.Paragraph(activeBlockIndex)
        syncActiveParagraphFromSelection()
        mutablePendingCommands += command
        return RichContentEditorEdit(box, selection, listOf(command))
    }

    private fun applyCommandPreservingTarget(command: RichContentCommand): RichContentEditorEdit {
        val previousTarget = activeTarget
        val result = engine.execute(box, command)
        updateFromResult(result)
        activeTarget = previousTarget.coerceInto(box)
        activeBlockIndex = activeTarget.blockIndexForSession()
        mutablePendingCommands += command
        return RichContentEditorEdit(box, selection, listOf(command))
    }

    private fun updateFromResult(result: RichContentCommandResult) {
        when (result) {
            is RichContentCommandResult.ContentEdited -> {
                box = result.box
                selection = result.selection
            }
            is RichContentCommandResult.ContentInserted -> box = result.box
            is RichContentCommandResult.ContentReplaced -> {
                box = result.box
                selection = TextSelection.cursor(box.content.endCursorPosition())
            }
        }
        localEditableBuffer = box.toPlainText()
    }

    private fun syncActiveParagraphFromSelection() {
        val length = box.paragraphTextLength(activeBlockIndex)
        activeParagraphSelection = ParagraphTextSelection(
            if (selection.start.blockIndex == activeBlockIndex) selection.start.inlineOffset.coerceIn(0, length) else 0,
            if (selection.end.blockIndex == activeBlockIndex) selection.end.inlineOffset.coerceIn(0, length) else 0,
        )
        localParagraphEditableBuffer = box.paragraphPlainTextOrEmpty(activeBlockIndex)
    }

    private fun noCommandEdit(): RichContentEditorEdit = RichContentEditorEdit(box, selection, emptyList())
}

public data class ParagraphTextSelection(val start: Int, val end: Int = start) {
    public val isCollapsed: Boolean get() = start == end
    public companion object { public fun cursor(offset: Int): ParagraphTextSelection = ParagraphTextSelection(offset) }
}

public data class TypingStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val textColorArgb: Int? = null,
    val highlightColorArgb: Int? = null,
    val fontScale: Float? = null,
    val link: String? = null,
    val isExplicitBold: Boolean = bold,
    val isExplicitItalic: Boolean = italic,
    val isExplicitUnderline: Boolean = underline,
    val isExplicitStrikethrough: Boolean = strikethrough,
) {
    public fun toggled(style: InlineStyle): TypingStyle = when (style) {
        InlineStyle.Bold -> copy(bold = !bold, isExplicitBold = true)
        InlineStyle.Italic -> copy(italic = !italic, isExplicitItalic = true)
        InlineStyle.Underline -> copy(underline = !underline, isExplicitUnderline = true)
        InlineStyle.Strikethrough -> copy(strikethrough = !strikethrough, isExplicitStrikethrough = true)
    }
}

public data class RichContentEditorEdit(
    val box: RichContentBox,
    val selection: TextSelection,
    val commands: List<RichContentCommand>,
)

public data class RichContentEditorCommit(
    val box: RichContentBox,
    val selection: TextSelection,
    val committedCommands: List<RichContentCommand>,
)

public sealed interface ActiveRichContentTarget {
    public data class Paragraph(val blockIndex: Int) : ActiveRichContentTarget
    public data class TableCell(val address: TableCellAddress) : ActiveRichContentTarget
    public data class FormulaBlock(val blockIndex: Int) : ActiveRichContentTarget
    public data class ImageBlock(val blockIndex: Int) : ActiveRichContentTarget
}

private data class TextDiff(val deletedStart: Int, val deletedEnd: Int, val insertedText: String) {
    companion object {
        fun between(oldText: String, newText: String): TextDiff {
            var prefix = 0
            val length = minOf(oldText.length, newText.length)
            while (prefix < length && oldText[prefix] == newText[prefix]) prefix++
            var suffix = 0
            while (
                suffix < oldText.length - prefix && suffix < newText.length - prefix &&
                oldText[oldText.lastIndex - suffix] == newText[newText.lastIndex - suffix]
            ) suffix++
            return TextDiff(prefix, oldText.length - suffix, newText.substring(prefix, newText.length - suffix))
        }
    }
}

private fun ActiveRichContentTarget.blockIndexForSession(): Int = when (this) {
    is ActiveRichContentTarget.Paragraph -> blockIndex
    is ActiveRichContentTarget.TableCell -> address.blockIndex
    is ActiveRichContentTarget.FormulaBlock -> blockIndex
    is ActiveRichContentTarget.ImageBlock -> blockIndex
}

private fun ActiveRichContentTarget.coerceInto(box: RichContentBox): ActiveRichContentTarget = when (this) {
    is ActiveRichContentTarget.Paragraph -> ActiveRichContentTarget.Paragraph(box.coerceParagraphBlockIndex(blockIndex))
    is ActiveRichContentTarget.TableCell -> if (RichContentTree.isValid(box.content, address)) this
        else ActiveRichContentTarget.Paragraph(box.coerceParagraphBlockIndex(address.blockIndex))
    is ActiveRichContentTarget.FormulaBlock -> if (box.content.blocks.getOrNull(blockIndex) is BlockFormula) this
        else ActiveRichContentTarget.Paragraph(box.coerceParagraphBlockIndex(blockIndex))
    is ActiveRichContentTarget.ImageBlock -> if (box.content.blocks.getOrNull(blockIndex) is BlockImage) this
        else ActiveRichContentTarget.Paragraph(box.coerceParagraphBlockIndex(blockIndex))
}

private fun TextSelection.coerceInto(box: RichContentBox): TextSelection = TextSelection(TextRange(
    box.cursorPositionAtPlainOffset(box.plainOffsetOf(start)),
    box.cursorPositionAtPlainOffset(box.plainOffsetOf(end)),
))

private fun RichContentBox.cursorPositionAtPlainOffset(offset: Int): TextCursorPosition =
    content.cursorPositionAtPlainOffset(offset)

private fun com.neonote.model.RichContent.cursorPositionAtPlainOffset(offset: Int): TextCursorPosition {
    if (blocks.isEmpty()) return TextCursorPosition(0, 0)
    val target = offset.coerceIn(0, toPlainText().length)
    var consumed = 0
    blocks.forEachIndexed { index, block ->
        val length = (block as? ParagraphNode)?.textLength() ?: 0
        if (target <= consumed + length) return TextCursorPosition(index, target - consumed)
        consumed += length
        if (index < blocks.lastIndex) {
            consumed += 1
            if (target <= consumed) return TextCursorPosition((index + 1).coerceAtMost(blocks.lastIndex), 0)
        }
    }
    val last = blocks.lastIndex
    return TextCursorPosition(last, (blocks[last] as? ParagraphNode)?.textLength() ?: 0)
}

private fun com.neonote.model.RichContent.endCursorPosition(): TextCursorPosition =
    cursorPositionAtPlainOffset(toPlainText().length)

private fun RichContentBox.plainOffsetOf(position: TextCursorPosition): Int {
    var offset = 0
    content.blocks.forEachIndexed { index, block ->
        val length = (block as? ParagraphNode)?.textLength() ?: 0
        if (index == position.blockIndex) return offset + position.inlineOffset.coerceIn(0, length)
        offset += length
        if (index < content.blocks.lastIndex) offset += 1
    }
    return content.toPlainText().length
}

private fun RichContentBox.coerceParagraphBlockIndex(blockIndex: Int): Int {
    if (content.blocks.isEmpty()) return 0
    if (content.blocks.getOrNull(blockIndex) is ParagraphNode) return blockIndex
    return content.blocks.indexOfFirst { it is ParagraphNode }.takeIf { it >= 0 }
        ?: blockIndex.coerceIn(content.blocks.indices)
}

private fun RichContentBox.paragraphPlainTextOrEmpty(blockIndex: Int): String =
    (content.blocks.getOrNull(blockIndex) as? ParagraphNode)?.plainText().orEmpty()

private fun RichContentBox.paragraphTextLength(blockIndex: Int): Int =
    (content.blocks.getOrNull(blockIndex) as? ParagraphNode)?.textLength() ?: 0

private fun RichContentBox.paragraphHasInlineAtom(blockIndex: Int): Boolean =
    (content.blocks.getOrNull(blockIndex) as? ParagraphNode)?.inlines?.any { it is InlineFormula || it is InlineImage } == true

private fun ParagraphNode.plainText(): String = inlines.joinToString("") { inline -> when (inline) {
    is InlineText -> inline.text
    InlineLineBreak -> "\n"
    is InlineFormula, is InlineImage -> "\uFFFC"
} }

private fun ParagraphNode.textLength(): Int = inlines.sumOf { inline -> when (inline) {
    is InlineText -> inline.text.length
    InlineLineBreak, is InlineFormula, is InlineImage -> 1
} }

private fun String.modelPlainOffsetOf(platformOffset: Int): Int =
    take(platformOffset.coerceIn(0, length)).replace("\r\n", "\n").replace('\r', '\n').length
