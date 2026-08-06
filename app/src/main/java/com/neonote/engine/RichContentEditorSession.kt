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

    public var activeTableCellSelection: ParagraphTextSelection = ParagraphTextSelection.cursor(0)
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

    public fun focusTableCell(
        address: TableCellAddress,
        selectionStart: Int = activeTableCellSelection.start,
        selectionEnd: Int = selectionStart,
    ) {
        if (!RichContentTree.isValid(box.content, address)) return
        activeBlockIndex = address.blockIndex.coerceAtLeast(0)
        activeTarget = ActiveRichContentTarget.TableCell(address)
        localParagraphEditableBuffer = tableCellPlainText(address)
        val length = localParagraphEditableBuffer.length
        activeTableCellSelection = ParagraphTextSelection(
            selectionStart.coerceIn(0, length),
            selectionEnd.coerceIn(0, length),
        )
    }

    public fun setActiveTableCellSelection(address: TableCellAddress, start: Int, end: Int = start) {
        focusTableCell(address, start, end)
    }

    public fun activeListKind(): ListKind? = when (val target = activeTarget) {
        is ActiveRichContentTarget.Paragraph ->
            (box.content.blocks.getOrNull(target.blockIndex) as? ParagraphNode)?.listMetadata?.kind
        is ActiveRichContentTarget.TableCell ->
            (RichContentTree.block(box.content, target.address) as? ParagraphNode)?.listMetadata?.kind
        else -> null
    }

    public fun activeTableDimensions(): Pair<Int, Int>? {
        val address = (activeTarget as? ActiveRichContentTarget.TableCell)?.address ?: return null
        val table = RichContentTree.table(box.content, address) ?: return null
        return table.rows.size to (table.rows.maxOfOrNull { it.size } ?: 0)
    }

    /**
     * Keyboard traversal follows the visual cell order. Tab on the last cell creates
     * one new row and moves into its first cell; Shift+Tab at the first cell stays put.
     */
    public fun moveTableCellFocus(forward: Boolean): RichContentEditorEdit {
        val address = (activeTarget as? ActiveRichContentTarget.TableCell)?.address ?: return noCommandEdit()
        val table = RichContentTree.table(box.content, address) ?: return noCommandEdit()
        val rowCount = table.rows.size.coerceAtLeast(1)
        val columnCount = (table.rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
        val current = address.path.last()
        val currentFlat = current.rowIndex.coerceIn(0, rowCount - 1) * columnCount +
            current.columnIndex.coerceIn(0, columnCount - 1)
        var targetFlat = currentFlat + if (forward) 1 else -1
        var edit = noCommandEdit()
        if (!forward && targetFlat < 0) {
            focusTableCell(address.withContainingCell(0, 0).withContentBlock(0), 0)
            return noCommandEdit()
        }
        if (forward && targetFlat >= rowCount * columnCount) {
            edit = addTableRow(address)
            targetFlat = rowCount * columnCount
        }
        val targetRow = targetFlat / columnCount
        val targetColumn = targetFlat % columnCount
        val targetCell = address.withContainingCell(targetRow, targetColumn)
        val firstBlock = RichContentTree.cellContent(box.content, targetCell)
            ?.blocks
            ?.indexOfFirst { it is ParagraphNode }
            ?.takeIf { it >= 0 }
            ?: 0
        focusTableCell(targetCell.withContentBlock(firstBlock), 0)
        return edit.copy(box = box, selection = selection)
    }

    public fun unifiedPlainSelection(): ParagraphTextSelection = ParagraphTextSelection(
        start = box.plainOffsetOf(selection.start),
        end = box.plainOffsetOf(selection.end),
    )

    public fun paragraphPlatformSelection(blockIndex: Int): ParagraphTextSelection? =
        activeParagraphSelection.takeIf {
            (activeTarget as? ActiveRichContentTarget.Paragraph)?.blockIndex == blockIndex
        }

    public fun tableCellPlatformSelection(address: TableCellAddress): ParagraphTextSelection? =
        activeTableCellSelection.takeIf {
            (activeTarget as? ActiveRichContentTarget.TableCell)?.address == address
        }

    /**
     * Formatting reflected by the current caret or selection. Explicit typing
     * overrides win; otherwise the surrounding content drives toolbar state.
     */
    public fun contextualTypingStyle(): TypingStyle = when (val target = activeTarget) {
        is ActiveRichContentTarget.TableCell -> {
            val localSelection = activeTableCellTextSelection(target.address)
            val content = RichContentTree.cellContent(box.content, target.address)
            content?.contextualTypingStyle(localSelection, typingStyle) ?: typingStyle
        }
        is ActiveRichContentTarget.Paragraph -> box.content.contextualTypingStyle(selection, typingStyle)
        else -> typingStyle
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
        val cell = activeTarget as? ActiveRichContentTarget.TableCell
        if (cell != null) {
            if (activeTableCellSelection.isCollapsed) {
                typingStyle = typingStyle.withStyle(style, !contextualTypingStyle().hasStyle(style))
                return noCommandEdit()
            }
            val local = activeTableCellTextSelection(cell.address)
            return applyCellCommand(cell.address, when (style) {
                InlineStyle.Bold -> RichContentCommand.ToggleBold(local)
                InlineStyle.Italic -> RichContentCommand.ToggleItalic(local)
                InlineStyle.Underline -> RichContentCommand.ToggleUnderline(local)
                InlineStyle.Strikethrough -> RichContentCommand.ToggleStrikethrough(local)
            })
        }
        if (selection.isCollapsed) {
            typingStyle = typingStyle.withStyle(style, !contextualTypingStyle().hasStyle(style))
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
        val cell = activeTarget as? ActiveRichContentTarget.TableCell
        if (cell != null) {
            if (activeTableCellSelection.isCollapsed) {
                typingStyle = typingStyle.copy(textColorArgb = colorArgb)
                return noCommandEdit()
            }
            return applyCellCommand(cell.address, RichContentCommand.SetTextColor(activeTableCellTextSelection(cell.address), colorArgb))
        }
        if (selection.isCollapsed) {
            typingStyle = typingStyle.copy(textColorArgb = colorArgb)
            return noCommandEdit()
        }
        return applyCommand(RichContentCommand.SetTextColor(selection, colorArgb))
    }

    public fun setHighlightColor(colorArgb: Int?): RichContentEditorEdit {
        val cell = activeTarget as? ActiveRichContentTarget.TableCell
        if (cell != null) {
            if (activeTableCellSelection.isCollapsed) {
                typingStyle = typingStyle.copy(highlightColorArgb = colorArgb)
                return noCommandEdit()
            }
            return applyCellCommand(cell.address, RichContentCommand.SetHighlightColor(activeTableCellTextSelection(cell.address), colorArgb))
        }
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
        val cell = activeTarget as? ActiveRichContentTarget.TableCell
        if (cell != null) {
            if (activeTableCellSelection.isCollapsed) {
                typingStyle = typingStyle.copy(link = url?.takeIf(String::isNotBlank))
                return noCommandEdit()
            }
            return applyCellCommand(cell.address, RichContentCommand.SetLink(activeTableCellTextSelection(cell.address), url))
        }
        if (selection.isCollapsed) {
            typingStyle = typingStyle.copy(link = url?.takeIf(String::isNotBlank))
            return noCommandEdit()
        }
        return applyCommand(RichContentCommand.SetLink(selection, url))
    }

    public fun toggleList(kind: ListKind): RichContentEditorEdit {
        val cell = activeTarget as? ActiveRichContentTarget.TableCell
        val targetSelection = if (cell != null) activeTableCellTextSelection(cell.address) else selection
        val command = when (kind) {
            ListKind.Bullet -> RichContentCommand.ToggleBulletList(targetSelection)
            ListKind.Numbered -> RichContentCommand.ToggleNumberedList(targetSelection)
            ListKind.Todo -> RichContentCommand.ToggleTodo(targetSelection)
        }
        return if (cell != null) applyCellCommand(cell.address, command) else applyCommand(command)
    }

    public fun toggleBulletList(): RichContentEditorEdit = toggleList(ListKind.Bullet)
    public fun toggleNumberedList(): RichContentEditorEdit = toggleList(ListKind.Numbered)
    public fun toggleTodo(): RichContentEditorEdit = toggleList(ListKind.Todo)
    public fun toggleTodoCheckedState(blockIndex: Int): RichContentEditorEdit =
        applyCommand(RichContentCommand.ToggleTodoCheckedState(blockIndex))

    public fun toggleActiveTodoCheckedState(): RichContentEditorEdit {
        val cell = activeTarget as? ActiveRichContentTarget.TableCell
            ?: return toggleTodoCheckedState(activeBlockIndex)
        return applyCellCommand(cell.address, RichContentCommand.ToggleTodoCheckedState(cell.address.contentBlockIndex))
    }

    public fun setParagraphAlignment(alignment: TextAlignment): RichContentEditorEdit = contextualParagraphCommand {
        RichContentCommand.SetParagraphAlignment(it, alignment)
    }

    public fun setHeadingLevel(level: Int): RichContentEditorEdit = contextualParagraphCommand {
        RichContentCommand.SetHeadingLevel(it, level)
    }

    public fun changeIndent(delta: Int): RichContentEditorEdit = contextualParagraphCommand {
        RichContentCommand.ChangeIndent(it, delta)
    }

    public fun insertLineBreak(): RichContentEditorEdit = contextualParagraphCommand {
        RichContentCommand.InsertLineBreak(it)
    }

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

    public fun insertFormulaInCell(address: TableCellAddress, expression: String = ""): RichContentEditorEdit {
        focusTableCell(address)
        val insertion = address.contentBlockIndex.coerceAtLeast(0)
        val next = RichContentTree.insertBlock(box.content, address, BlockFormula(expression = expression), insertion)
        if (next == box.content) return noCommandEdit()
        box = box.copy(content = next)
        activeTarget = ActiveRichContentTarget.TableCell(address.copy(contentBlockIndex = insertion))
        localEditableBuffer = box.toPlainText()
        localParagraphEditableBuffer = tableCellPlainText(address)
        return RichContentEditorEdit(box, selection, emptyList())
    }

    public fun insertImageInCell(address: TableCellAddress, assetId: String, altText: String? = null): RichContentEditorEdit {
        focusTableCell(address)
        val insertion = address.contentBlockIndex.coerceAtLeast(0)
        val next = RichContentTree.insertBlock(box.content, address, BlockImage(assetId = assetId, altText = altText), insertion)
        if (next == box.content) return noCommandEdit()
        box = box.copy(content = next)
        activeTarget = ActiveRichContentTarget.TableCell(address.copy(contentBlockIndex = insertion))
        localEditableBuffer = box.toPlainText()
        localParagraphEditableBuffer = tableCellPlainText(address)
        return RichContentEditorEdit(box, selection, emptyList())
    }

    public fun updateNestedFormula(
        address: TableCellAddress,
        expression: String? = null,
        displayMode: FormulaDisplayMode? = null,
        numbered: Boolean? = null,
    ): RichContentEditorEdit {
        val formula = RichContentTree.block(box.content, address) as? BlockFormula ?: return noCommandEdit()
        val replacement = formula.copy(
            expression = expression ?: formula.expression,
            displayMode = displayMode ?: formula.displayMode,
            numbered = numbered ?: formula.numbered,
        )
        val next = RichContentTree.replaceBlock(box.content, address, replacement)
        if (next == box.content) return noCommandEdit()
        box = box.copy(content = next)
        activeTarget = ActiveRichContentTarget.TableCell(address)
        activeBlockIndex = address.blockIndex
        localEditableBuffer = box.toPlainText()
        return RichContentEditorEdit(box, selection, emptyList())
    }

    public fun updateNestedImage(
        address: TableCellAddress,
        width: Float? = null,
        height: Float? = null,
        rotationDegrees: Float? = null,
        crop: ImageCrop? = null,
        caption: String? = null,
        replacementAssetId: String? = null,
    ): RichContentEditorEdit {
        val image = RichContentTree.block(box.content, address) as? BlockImage ?: return noCommandEdit()
        val replacement = image.copy(
            assetId = replacementAssetId ?: image.assetId,
            width = width ?: image.width,
            height = height ?: image.height,
            rotationDegrees = rotationDegrees ?: image.rotationDegrees,
            crop = (crop ?: image.crop).normalized(),
            caption = caption ?: image.caption,
        )
        val next = RichContentTree.replaceBlock(box.content, address, replacement)
        if (next == box.content) return noCommandEdit()
        box = box.copy(content = next)
        activeTarget = ActiveRichContentTarget.TableCell(address)
        activeBlockIndex = address.blockIndex
        localEditableBuffer = box.toPlainText()
        return RichContentEditorEdit(box, selection, emptyList())
    }

    public fun deleteNestedBlock(address: TableCellAddress): RichContentEditorEdit {
        var next = RichContentTree.deleteBlock(box.content, address)
        val cellContent = RichContentTree.cellContent(next, address)
        if (cellContent != null && cellContent.blocks.isEmpty()) {
            next = RichContentTree.updateCellContent(next, address) { RichContent(listOf(ParagraphNode())) }
        }
        if (next == box.content) return noCommandEdit()
        box = box.copy(content = next)
        val parentAddress = address.withContentBlock(0)
        activeTarget = if (RichContentTree.isValid(box.content, parentAddress)) {
            ActiveRichContentTarget.TableCell(parentAddress)
        } else ActiveRichContentTarget.Paragraph(box.coerceParagraphBlockIndex(address.blockIndex))
        activeBlockIndex = activeTarget.blockIndexForSession()
        localEditableBuffer = box.toPlainText()
        syncActiveParagraphFromSelection()
        return RichContentEditorEdit(box, selection, emptyList())
    }

    public fun deleteTable(address: TableCellAddress): RichContentEditorEdit {
        val path = address.path
        if (path.isEmpty()) return noCommandEdit()
        if (path.size == 1) return deleteBlock(path.first().tableBlockIndex)
        val first = path.first()
        val parentPath = path.dropLast(1)
        val currentTable = path.last()
        val tableBlockInParent = TableCellAddress(
            blockIndex = first.tableBlockIndex,
            rowIndex = first.rowIndex,
            columnIndex = first.columnIndex,
            contentBlockIndex = currentTable.tableBlockIndex,
            nestedPath = parentPath.drop(1),
        )
        val next = RichContentTree.deleteBlock(box.content, tableBlockInParent)
        if (next == box.content) return noCommandEdit()
        box = box.copy(content = next)
        val parentCell = tableBlockInParent.copy(contentBlockIndex = 0)
        activeTarget = if (RichContentTree.isValid(box.content, parentCell)) {
            ActiveRichContentTarget.TableCell(parentCell)
        } else ActiveRichContentTarget.Paragraph(box.coerceParagraphBlockIndex(first.tableBlockIndex))
        activeBlockIndex = activeTarget.blockIndexForSession()
        localEditableBuffer = box.toPlainText()
        syncActiveParagraphFromSelection()
        return RichContentEditorEdit(box, selection, emptyList())
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

    public fun continueAfterFormula(blockIndex: Int): RichContentEditorEdit {
        if (box.content.blocks.getOrNull(blockIndex) !is BlockFormula) return noCommandEdit()
        val targetIndex = (blockIndex + 1).coerceAtMost(box.content.blocks.size)
        val edit = if (box.content.blocks.getOrNull(targetIndex) is ParagraphNode) {
            noCommandEdit()
        } else {
            ensureTrailingParagraphAfter(blockIndex)
        }
        focusParagraph(targetIndex.coerceIn(0, box.content.blocks.lastIndex.coerceAtLeast(0)), 0)
        return edit.copy(box = box, selection = selection)
    }

    public fun continueAfterNestedBlock(address: TableCellAddress): RichContentEditorEdit {
        val cellContent = RichContentTree.cellContent(box.content, address) ?: return noCommandEdit()
        val targetIndex = (address.contentBlockIndex + 1).coerceIn(0, cellContent.blocks.size)
        val nextContent = if (cellContent.blocks.getOrNull(targetIndex) is ParagraphNode) {
            box.content
        } else {
            RichContentTree.insertBlock(box.content, address, ParagraphNode(), targetIndex)
        }
        if (nextContent != box.content) box = box.copy(content = nextContent)
        val target = address.withContentBlock(targetIndex.coerceIn(0,
            (RichContentTree.cellContent(box.content, address)?.blocks?.lastIndex ?: 0).coerceAtLeast(0)))
        focusTableCell(target, 0)
        localEditableBuffer = box.toPlainText()
        return RichContentEditorEdit(box, selection, emptyList())
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
        previousText: String,
        nextText: String,
        selectionStart: Int,
        selectionEnd: Int = selectionStart,
    ): RichContentEditorEdit {
        focusTableCell(address, activeTableCellSelection.start, activeTableCellSelection.end)
        if (previousText != localParagraphEditableBuffer) localParagraphEditableBuffer = previousText
        if (nextText == localParagraphEditableBuffer) {
            setActiveTableCellSelection(address, selectionStart, selectionEnd)
            return noCommandEdit()
        }
        val diff = TextDiff.between(localParagraphEditableBuffer, nextText)
        val localSelection = TextSelection(TextRange(
            TextCursorPosition(address.contentBlockIndex, diff.deletedStart),
            TextCursorPosition(address.contentBlockIndex, diff.deletedEnd),
        ))
        val command = when {
            diff.insertedText == "\n" && diff.deletedStart == diff.deletedEnd ->
                RichContentCommand.InsertParagraph(selection = localSelection)
            diff.insertedText.isEmpty() && diff.deletedEnd > diff.deletedStart -> {
                if (diff.deletedEnd - diff.deletedStart == 1) {
                    RichContentCommand.DeleteBackward(TextSelection.cursor(localSelection.end))
                } else RichContentCommand.DeleteSelection(localSelection)
            }
            '\n' in diff.insertedText || '\r' in diff.insertedText ->
                RichContentCommand.PastePlainText(diff.insertedText, localSelection, typingStyle)
            else -> RichContentCommand.InsertText(diff.insertedText, localSelection, typingStyle)
        }
        val edit = applyCellCommand(address, command)
        val nextAddress = (activeTarget as? ActiveRichContentTarget.TableCell)?.address ?: address
        setActiveTableCellSelection(nextAddress, selectionStart, selectionEnd)
        localParagraphEditableBuffer = tableCellPlainText(nextAddress)
        return edit
    }

    public fun tableCellPlainText(address: TableCellAddress): String =
        (RichContentTree.block(box.content, address) as? ParagraphNode)?.plainText().orEmpty()

    private fun activeTableCellTextSelection(address: TableCellAddress): TextSelection = TextSelection(TextRange(
        TextCursorPosition(address.contentBlockIndex, activeTableCellSelection.start),
        TextCursorPosition(address.contentBlockIndex, activeTableCellSelection.end),
    ))

    private fun contextualParagraphCommand(factory: (TextSelection) -> RichContentCommand): RichContentEditorEdit {
        val cell = activeTarget as? ActiveRichContentTarget.TableCell
        return if (cell != null) applyCellCommand(cell.address, factory(activeTableCellTextSelection(cell.address)))
        else applyCommand(factory(selection))
    }

    private fun applyCellCommand(address: TableCellAddress, command: RichContentCommand): RichContentEditorEdit {
        val cellContent = RichContentTree.cellContent(box.content, address) ?: return noCommandEdit()
        val cellBox = RichContentBox(id = "cell-editor", content = cellContent)
        val result = engine.execute(cellBox, command)
        val editedBox = when (result) {
            is RichContentCommandResult.ContentEdited -> result.box
            is RichContentCommandResult.ContentInserted -> result.box
            is RichContentCommandResult.ContentReplaced -> result.box
        }
        val editedSelection = when (result) {
            is RichContentCommandResult.ContentEdited -> result.selection
            is RichContentCommandResult.ContentInserted -> TextSelection.cursor(editedBox.content.endCursorPosition())
            is RichContentCommandResult.ContentReplaced -> TextSelection.cursor(editedBox.content.endCursorPosition())
        }
        val nextContent = RichContentTree.updateCellContent(box.content, address) { editedBox.content }
        box = box.copy(content = nextContent)
        val nextBlockIndex = editedSelection.start.blockIndex.coerceIn(0, editedBox.content.blocks.lastIndex.coerceAtLeast(0))
        val nextAddress = address.copy(contentBlockIndex = nextBlockIndex)
        activeTarget = ActiveRichContentTarget.TableCell(nextAddress)
        activeBlockIndex = address.blockIndex
        val nextLength = tableCellPlainText(nextAddress).length
        activeTableCellSelection = ParagraphTextSelection(
            editedSelection.start.inlineOffset.coerceIn(0, nextLength),
            editedSelection.end.inlineOffset.coerceIn(0, nextLength),
        )
        localEditableBuffer = box.toPlainText()
        localParagraphEditableBuffer = tableCellPlainText(nextAddress)
        mutablePendingCommands += command
        return RichContentEditorEdit(box, selection, listOf(command))
    }

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

private fun TableCellAddress.withContainingCell(rowIndex: Int, columnIndex: Int): TableCellAddress =
    if (nestedPath.isEmpty()) {
        copy(rowIndex = rowIndex, columnIndex = columnIndex, contentBlockIndex = 0)
    } else {
        copy(
            nestedPath = nestedPath.dropLast(1) + nestedPath.last().copy(
                rowIndex = rowIndex,
                columnIndex = columnIndex,
            ),
            contentBlockIndex = 0,
        )
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
    public fun toggled(style: InlineStyle): TypingStyle = withStyle(style, !hasStyle(style))

    public fun hasStyle(style: InlineStyle): Boolean = when (style) {
        InlineStyle.Bold -> bold
        InlineStyle.Italic -> italic
        InlineStyle.Underline -> underline
        InlineStyle.Strikethrough -> strikethrough
    }

    public fun withStyle(style: InlineStyle, enabled: Boolean): TypingStyle = when (style) {
        InlineStyle.Bold -> copy(bold = enabled, isExplicitBold = true)
        InlineStyle.Italic -> copy(italic = enabled, isExplicitItalic = true)
        InlineStyle.Underline -> copy(underline = enabled, isExplicitUnderline = true)
        InlineStyle.Strikethrough -> copy(strikethrough = enabled, isExplicitStrikethrough = true)
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

private data class ContextualInlineStyle(
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val strikethrough: Boolean,
    val textColorArgb: Int?,
    val highlightColorArgb: Int?,
    val fontScale: Float,
    val link: String?,
)

private fun InlineText.contextualStyles(): List<ContextualInlineStyle> = List(text.length) {
    ContextualInlineStyle(
        bold = bold,
        italic = italic,
        underline = underline,
        strikethrough = strikethrough,
        textColorArgb = textColorArgb,
        highlightColorArgb = highlightColorArgb,
        fontScale = fontScale,
        link = link,
    )
}

private fun ParagraphNode.contextualStyles(): List<ContextualInlineStyle?> = inlines.flatMap { inline ->
    when (inline) {
        is InlineText -> inline.contextualStyles()
        InlineLineBreak, is InlineFormula, is InlineImage -> listOf(null)
    }
}

private fun com.neonote.model.RichContent.contextualTypingStyle(
    rawSelection: TextSelection,
    fallback: TypingStyle,
): TypingStyle {
    if (blocks.isEmpty()) return fallback
    val selection = rawSelection.ordered()
    val styles = mutableListOf<ContextualInlineStyle>()
    if (selection.isCollapsed) {
        val paragraph = blocks.getOrNull(selection.start.blockIndex) as? ParagraphNode ?: return fallback
        val chars = paragraph.contextualStyles()
        if (chars.isEmpty()) return fallback
        val caret = selection.start.inlineOffset.coerceIn(0, chars.size)
        val nearby = (caret - 1 downTo 0).firstNotNullOfOrNull { chars[it] }
            ?: (caret until chars.size).firstNotNullOfOrNull { chars[it] }
        nearby?.let(styles::add)
    } else {
        for (blockIndex in selection.start.blockIndex..selection.end.blockIndex) {
            val paragraph = blocks.getOrNull(blockIndex) as? ParagraphNode ?: continue
            val chars = paragraph.contextualStyles()
            val from = if (blockIndex == selection.start.blockIndex) selection.start.inlineOffset else 0
            val to = if (blockIndex == selection.end.blockIndex) selection.end.inlineOffset else chars.size
            chars.subList(from.coerceIn(0, chars.size), to.coerceIn(0, chars.size))
                .filterNotNullTo(styles)
        }
    }
    if (styles.isEmpty()) return fallback
    fun <T> common(selector: (ContextualInlineStyle) -> T): T? {
        val first = selector(styles.first())
        return first.takeIf { styles.all { style -> selector(style) == first } }
    }
    return fallback.copy(
        bold = if (fallback.isExplicitBold) fallback.bold else styles.all { it.bold },
        italic = if (fallback.isExplicitItalic) fallback.italic else styles.all { it.italic },
        underline = if (fallback.isExplicitUnderline) fallback.underline else styles.all { it.underline },
        strikethrough = if (fallback.isExplicitStrikethrough) fallback.strikethrough else styles.all { it.strikethrough },
        textColorArgb = fallback.textColorArgb ?: common { it.textColorArgb },
        highlightColorArgb = fallback.highlightColorArgb ?: common { it.highlightColorArgb },
        fontScale = fallback.fontScale ?: common { it.fontScale },
        link = fallback.link ?: common { it.link },
    )
}

private fun String.modelPlainOffsetOf(platformOffset: Int): Int =
    take(platformOffset.coerceIn(0, length)).replace("\r\n", "\n").replace('\r', '\n').length
