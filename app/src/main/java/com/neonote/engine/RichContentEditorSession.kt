package com.neonote.engine

import com.neonote.model.InlineText
import com.neonote.model.ListKind
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContentBox
import com.neonote.model.TextCursorPosition
import com.neonote.model.TextRange
import com.neonote.model.TextSelection

/**
 * Editing-session state for a focused [RichContentBox].
 *
 * Compose TextField/BasicTextField remains only the platform input adapter: it
 * reports text snapshots, focus, and IME composition state. This session owns the
 * editor semantics (caret/selection, typing style, local buffer, and command
 * queue) and translates basic input into [RichContentCommand]s for
 * [RichContentEngine].
 *
 * Boundary: Android IME composition (notably Chinese pinyin conversion) is not a
 * complete rich-text composition engine here. While the platform reports an
 * active composing region, [replaceFromPlatformCompositionFallback] preserves
 * platform behavior with a whole plain-text replacement. Once composition is
 * committed, normal insert/paragraph/backspace commands resume.
 */
public class RichContentEditorSession(
    initialBox: RichContentBox,
    initialSelection: TextSelection = TextSelection.cursor(initialBox.content.endCursorPosition()),
    private val engine: RichContentEngine = RichContentEngine(),
) {
    public var box: RichContentBox = initialBox
        private set

    public var selection: TextSelection = initialSelection.coerceInto(box)
        private set

    public var typingStyle: TypingStyle = TypingStyle()
        private set

    public var localEditableBuffer: String = box.toPlainText()
        private set

    public var activeBlockIndex: Int = initialSelection.start.blockIndex.coerceAtLeast(0)
        private set

    public var activeParagraphSelection: ParagraphTextSelection = ParagraphTextSelection.cursor(initialSelection.start.inlineOffset)
        private set

    public val activeParagraphCaret: Int?
        get() = activeParagraphSelection.takeIf { it.isCollapsed }?.start

    public var localParagraphEditableBuffer: String = box.paragraphPlainTextOrEmpty(activeBlockIndex)
        private set

    private val mutablePendingCommands: MutableList<RichContentCommand> = mutableListOf()

    public val pendingCommands: List<RichContentCommand>
        get() = mutablePendingCommands.toList()

    public fun focus(updatedBox: RichContentBox = box) {
        box = updatedBox
        localEditableBuffer = updatedBox.toPlainText()
        selection = selection.coerceInto(updatedBox)
        activeBlockIndex = box.coerceParagraphBlockIndex(activeBlockIndex)
        syncActiveParagraphFromSelection()
    }

    public fun focusParagraph(blockIndex: Int, selectionStart: Int = 0, selectionEnd: Int = selectionStart) {
        activeBlockIndex = box.coerceParagraphBlockIndex(blockIndex)
        setActiveParagraphSelection(selectionStart, selectionEnd)
    }

    public fun setActiveParagraphSelection(start: Int, end: Int = start) {
        val paragraphLength = box.paragraphTextLength(activeBlockIndex)
        activeParagraphSelection = ParagraphTextSelection(
            start = start.coerceIn(0, paragraphLength),
            end = end.coerceIn(0, paragraphLength),
        )
        selection = TextSelection(
            TextRange(
                start = TextCursorPosition(activeBlockIndex, activeParagraphSelection.start),
                end = TextCursorPosition(activeBlockIndex, activeParagraphSelection.end),
            ),
        )
        localParagraphEditableBuffer = box.paragraphPlainTextOrEmpty(activeBlockIndex)
    }

    public fun blurCommit(updatedBox: RichContentBox = box): RichContentEditorCommit {
        if (updatedBox != box) focus(updatedBox)
        val commands = drainPendingCommands()
        return RichContentEditorCommit(box = box, selection = selection, committedCommands = commands)
    }

    public fun setSelectionFromPlainOffsets(start: Int, end: Int = start) {
        selection = TextSelection(
            TextRange(
                start = box.cursorPositionAtPlainOffset(start),
                end = box.cursorPositionAtPlainOffset(end),
            ),
        )
        activeBlockIndex = box.coerceParagraphBlockIndex(selection.start.blockIndex)
        syncActiveParagraphFromSelection()
    }

    public fun insertText(text: String): RichContentEditorEdit = applyCommand(
        RichContentCommand.InsertText(text = text, selection = selection, typingStyle = typingStyle),
    )

    public fun pastePlainText(text: String): RichContentEditorEdit = applyCommand(
        RichContentCommand.PastePlainText(text = text, selection = selection, typingStyle = typingStyle),
    )

    public fun insertParagraph(): RichContentEditorEdit = applyCommand(
        RichContentCommand.InsertParagraph(selection = selection),
    )

    public fun deleteBackward(): RichContentEditorEdit = applyCommand(
        RichContentCommand.DeleteBackward(selection = selection),
    )

    public fun deleteSelectedRange(): RichContentEditorEdit = applyCommand(
        RichContentCommand.DeleteSelection(selection = selection),
    )

    public fun toggleStyle(style: InlineStyle): RichContentEditorEdit {
        if (selection.isCollapsed) {
            typingStyle = typingStyle.toggled(style)
            return RichContentEditorEdit(box = box, selection = selection, commands = emptyList())
        }

        return applyCommand(
            when (style) {
                InlineStyle.Bold -> RichContentCommand.ToggleBold(selection)
                InlineStyle.Italic -> RichContentCommand.ToggleItalic(selection)
                InlineStyle.Underline -> RichContentCommand.ToggleUnderline(selection)
            },
        )
    }

    public fun toggleBold(): RichContentEditorEdit = toggleStyle(InlineStyle.Bold)

    public fun toggleItalic(): RichContentEditorEdit = toggleStyle(InlineStyle.Italic)

    public fun toggleUnderline(): RichContentEditorEdit = toggleStyle(InlineStyle.Underline)

    public fun toggleList(kind: ListKind): RichContentEditorEdit = applyCommand(
        when (kind) {
            ListKind.Bullet -> RichContentCommand.ToggleBulletList(selection)
            ListKind.Numbered -> RichContentCommand.ToggleNumberedList(selection)
            ListKind.Todo -> RichContentCommand.ToggleTodo(selection)
        },
    )

    public fun toggleBulletList(): RichContentEditorEdit = toggleList(ListKind.Bullet)

    public fun toggleNumberedList(): RichContentEditorEdit = toggleList(ListKind.Numbered)

    public fun toggleTodo(): RichContentEditorEdit = toggleList(ListKind.Todo)

    public fun toggleTodoCheckedState(blockIndex: Int): RichContentEditorEdit = applyCommand(
        RichContentCommand.ToggleTodoCheckedState(blockIndex = blockIndex),
    )

    public fun insertTablePlaceholder(rows: Int = 2, columns: Int = 2): RichContentEditorEdit = applyCommand(
        RichContentCommand.InsertTable(rows = rows, columns = columns),
    )

    public fun insertBlockFormulaPlaceholder(expression: String = ""): RichContentEditorEdit = applyCommand(
        RichContentCommand.InsertBlockFormula(expression = expression),
    )

    /**
     * Translate a paragraph-local BasicTextField snapshot into semantic rich
     * content commands. The field owns only one paragraph, so offsets are local
     * to [blockIndex] and active IME composition can avoid rebuilding sibling
     * rich blocks.
     */
    public fun replaceFromPlatformParagraphInput(
        blockIndex: Int,
        previousText: String,
        nextText: String,
        selectionStartOffset: Int,
        selectionEndOffset: Int = selectionStartOffset,
    ): RichContentEditorEdit {
        focusParagraph(blockIndex, activeParagraphSelection.start, activeParagraphSelection.end)
        if (previousText != localParagraphEditableBuffer) {
            localParagraphEditableBuffer = previousText
        }
        if (nextText == localParagraphEditableBuffer) {
            setActiveParagraphSelection(selectionStartOffset, selectionEndOffset)
            return RichContentEditorEdit(box = box, selection = selection, commands = emptyList())
        }

        val diff = TextDiff.between(oldText = localParagraphEditableBuffer, newText = nextText)
        selection = TextSelection(
            TextRange(
                start = TextCursorPosition(activeBlockIndex, diff.deletedStart),
                end = TextCursorPosition(activeBlockIndex, diff.deletedEnd),
            ),
        )
        activeParagraphSelection = ParagraphTextSelection(diff.deletedStart, diff.deletedEnd)

        val appliedCommands = mutableListOf<RichContentCommand>()
        when {
            diff.insertedText == "\n" && diff.deletedStart == diff.deletedEnd -> {
                appliedCommands += applyCommand(RichContentCommand.InsertParagraph(selection = selection)).commands
            }
            diff.insertedText.isEmpty() && diff.deletedEnd > diff.deletedStart -> {
                if (diff.deletedEnd - diff.deletedStart == 1) {
                    selection = TextSelection.cursor(TextCursorPosition(activeBlockIndex, diff.deletedEnd))
                    appliedCommands += applyCommand(RichContentCommand.DeleteBackward(selection = selection)).commands
                } else {
                    appliedCommands += applyCommand(RichContentCommand.DeleteSelection(selection = selection)).commands
                }
            }
            else -> {
                val command = if ('\n' in diff.insertedText || '\r' in diff.insertedText) {
                    RichContentCommand.PastePlainText(text = diff.insertedText, selection = selection, typingStyle = typingStyle)
                } else {
                    RichContentCommand.InsertText(text = diff.insertedText, selection = selection, typingStyle = typingStyle)
                }
                appliedCommands += applyCommand(command).commands
            }
        }

        val selectionBlock = selection.start.blockIndex
        activeBlockIndex = box.coerceParagraphBlockIndex(selectionBlock)
        val localStart = if (activeBlockIndex == selectionBlock) selection.start.inlineOffset else selectionStartOffset
        val localEnd = if (activeBlockIndex == selection.end.blockIndex) selection.end.inlineOffset else localStart
        setActiveParagraphSelection(localStart, localEnd)
        return RichContentEditorEdit(box = box, selection = selection, commands = appliedCommands)
    }

    /** Paragraph-local fallback path for active platform IME composition. */
    public fun replaceParagraphFromPlatformCompositionFallback(
        blockIndex: Int,
        nextText: String,
        selectionStartOffset: Int,
        selectionEndOffset: Int = selectionStartOffset,
    ): RichContentEditorEdit {
        activeBlockIndex = box.coerceParagraphBlockIndex(blockIndex)
        val command = RichContentCommand.ReplaceParagraphText(activeBlockIndex, nextText)
        val result = engine.execute(box, command) as RichContentCommandResult.ContentEdited
        box = result.box
        localEditableBuffer = box.toPlainText()
        activeBlockIndex = box.coerceParagraphBlockIndex(result.selection.start.blockIndex)
        setActiveParagraphSelection(selectionStartOffset, selectionEndOffset)
        mutablePendingCommands += command
        return RichContentEditorEdit(box = box, selection = selection, commands = listOf(command))
    }

    /**
     * Translate a platform TextField text snapshot into semantic commands.
     */
    public fun replaceFromPlatformInput(
        previousText: String,
        nextText: String,
        selectionStartPlainOffset: Int,
        selectionEndPlainOffset: Int = selectionStartPlainOffset,
    ): RichContentEditorEdit {
        if (previousText != localEditableBuffer) {
            localEditableBuffer = previousText
        }
        if (nextText == localEditableBuffer) {
            setSelectionFromPlainOffsets(selectionStartPlainOffset, selectionEndPlainOffset)
            return RichContentEditorEdit(box = box, selection = selection, commands = emptyList())
        }

        val diff = TextDiff.between(oldText = localEditableBuffer, newText = nextText)
        val oldSelection = TextSelection(
            TextRange(
                start = box.cursorPositionAtPlainOffset(diff.deletedStart),
                end = box.cursorPositionAtPlainOffset(diff.deletedEnd),
            ),
        )
        selection = oldSelection

        val appliedCommands = mutableListOf<RichContentCommand>()
        when {
            diff.insertedText == "\n" && diff.deletedStart == diff.deletedEnd -> {
                appliedCommands += applyCommand(RichContentCommand.InsertParagraph(selection = selection)).commands
            }
            diff.insertedText.isEmpty() && diff.deletedEnd > diff.deletedStart -> {
                if (diff.deletedEnd - diff.deletedStart == 1) {
                    selection = TextSelection.cursor(box.cursorPositionAtPlainOffset(diff.deletedEnd))
                    appliedCommands += applyCommand(RichContentCommand.DeleteBackward(selection = selection)).commands
                } else {
                    appliedCommands += applyCommand(RichContentCommand.DeleteSelection(selection = selection)).commands
                }
            }
            else -> {
                val command = if ('\n' in diff.insertedText || '\r' in diff.insertedText) {
                    RichContentCommand.PastePlainText(text = diff.insertedText, selection = selection, typingStyle = typingStyle)
                } else {
                    RichContentCommand.InsertText(text = diff.insertedText, selection = selection, typingStyle = typingStyle)
                }
                appliedCommands += applyCommand(command).commands
            }
        }

        setSelectionFromPlainOffsets(
            nextText.modelPlainOffsetOf(selectionStartPlainOffset),
            nextText.modelPlainOffsetOf(selectionEndPlainOffset),
        )
        return RichContentEditorEdit(box = box, selection = selection, commands = appliedCommands)
    }

    /**
     * Fallback path for active platform IME composition.
     */
    public fun replaceFromPlatformCompositionFallback(
        nextText: String,
        selectionStartPlainOffset: Int,
        selectionEndPlainOffset: Int = selectionStartPlainOffset,
    ): RichContentEditorEdit {
        val command = RichContentCommand.ReplacePlainText(nextText)
        val result = engine.execute(box, command) as RichContentCommandResult.ContentReplaced
        box = result.box
        localEditableBuffer = box.toPlainText()
        setSelectionFromPlainOffsets(selectionStartPlainOffset, selectionEndPlainOffset)
        mutablePendingCommands += command
        return RichContentEditorEdit(box = box, selection = selection, commands = listOf(command))
    }

    public fun drainPendingCommands(): List<RichContentCommand> {
        val commands = pendingCommands
        mutablePendingCommands.clear()
        return commands
    }

    private fun applyCommand(command: RichContentCommand): RichContentEditorEdit {
        val result = engine.execute(box, command)
        when (result) {
            is RichContentCommandResult.ContentEdited -> {
                box = result.box
                selection = result.selection
            }
            is RichContentCommandResult.ContentReplaced -> {
                box = result.box
                selection = TextSelection.cursor(box.content.endCursorPosition())
            }
            is RichContentCommandResult.ContentInserted -> box = result.box
        }
        localEditableBuffer = box.toPlainText()
        activeBlockIndex = box.coerceParagraphBlockIndex(selection.start.blockIndex)
        syncActiveParagraphFromSelection()
        mutablePendingCommands += command
        return RichContentEditorEdit(box = box, selection = selection, commands = listOf(command))
    }

    private fun syncActiveParagraphFromSelection() {
        val paragraphLength = box.paragraphTextLength(activeBlockIndex)
        activeParagraphSelection = ParagraphTextSelection(
            start = if (selection.start.blockIndex == activeBlockIndex) selection.start.inlineOffset.coerceIn(0, paragraphLength) else 0,
            end = if (selection.end.blockIndex == activeBlockIndex) selection.end.inlineOffset.coerceIn(0, paragraphLength) else 0,
        )
        localParagraphEditableBuffer = box.paragraphPlainTextOrEmpty(activeBlockIndex)
    }
}

public data class ParagraphTextSelection(val start: Int, val end: Int = start) {
    public val isCollapsed: Boolean get() = start == end

    public companion object {
        public fun cursor(offset: Int): ParagraphTextSelection = ParagraphTextSelection(offset, offset)
    }
}

public data class TypingStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
) {
    public fun toggled(style: InlineStyle): TypingStyle = when (style) {
        InlineStyle.Bold -> copy(bold = !bold)
        InlineStyle.Italic -> copy(italic = !italic)
        InlineStyle.Underline -> copy(underline = !underline)
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

private data class TextDiff(
    val deletedStart: Int,
    val deletedEnd: Int,
    val insertedText: String,
) {
    companion object {
        fun between(oldText: String, newText: String): TextDiff {
            var prefix = 0
            val minLength = minOf(oldText.length, newText.length)
            while (prefix < minLength && oldText[prefix] == newText[prefix]) prefix++

            var suffix = 0
            while (
                suffix < oldText.length - prefix &&
                suffix < newText.length - prefix &&
                oldText[oldText.lastIndex - suffix] == newText[newText.lastIndex - suffix]
            ) {
                suffix++
            }

            return TextDiff(
                deletedStart = prefix,
                deletedEnd = oldText.length - suffix,
                insertedText = newText.substring(prefix, newText.length - suffix),
            )
        }
    }
}

private fun String.modelPlainOffsetOf(platformOffset: Int): Int =
    take(platformOffset.coerceIn(0, length)).replace("\r\n", "\n").replace('\r', '\n').length

private fun TextSelection.coerceInto(box: RichContentBox): TextSelection = TextSelection(
    TextRange(
        start = box.cursorPositionAtPlainOffset(box.plainOffsetOf(start)),
        end = box.cursorPositionAtPlainOffset(box.plainOffsetOf(end)),
    ),
)

private fun RichContentBox.cursorPositionAtPlainOffset(offset: Int): TextCursorPosition = content.cursorPositionAtPlainOffset(offset)

private fun com.neonote.model.RichContent.cursorPositionAtPlainOffset(offset: Int): TextCursorPosition {
    if (blocks.isEmpty()) return TextCursorPosition(blockIndex = 0, inlineOffset = 0)
    val target = offset.coerceIn(0, toPlainText().length)
    var consumed = 0
    blocks.forEachIndexed { index, block ->
        val paragraph = block as? ParagraphNode
        val length = paragraph?.textLength() ?: 0
        if (target <= consumed + length) {
            return TextCursorPosition(blockIndex = index, inlineOffset = target - consumed)
        }
        consumed += length
        if (index < blocks.lastIndex) {
            if (target == consumed) return TextCursorPosition(blockIndex = index, inlineOffset = length)
            consumed += 1
            if (target <= consumed) return TextCursorPosition(blockIndex = (index + 1).coerceAtMost(blocks.lastIndex), inlineOffset = 0)
        }
    }
    val lastIndex = blocks.lastIndex
    val lastParagraph = blocks[lastIndex] as? ParagraphNode
    return TextCursorPosition(blockIndex = lastIndex, inlineOffset = lastParagraph?.textLength() ?: 0)
}

private fun com.neonote.model.RichContent.endCursorPosition(): TextCursorPosition = cursorPositionAtPlainOffset(toPlainText().length)

private fun RichContentBox.plainOffsetOf(position: TextCursorPosition): Int {
    if (content.blocks.isEmpty()) return 0
    var offset = 0
    content.blocks.forEachIndexed { index, block ->
        val paragraph = block as? ParagraphNode
        if (index == position.blockIndex) return offset + position.inlineOffset.coerceIn(0, paragraph?.textLength() ?: 0)
        offset += paragraph?.textLength() ?: 0
        if (index < content.blocks.lastIndex) offset += 1
    }
    return content.toPlainText().length
}

private fun RichContentBox.coerceParagraphBlockIndex(blockIndex: Int): Int {
    val blocks = content.blocks
    if (blocks.isEmpty()) return 0
    if (blockIndex in blocks.indices && blocks[blockIndex] is ParagraphNode) return blockIndex
    return blocks.indexOfFirst { it is ParagraphNode }.takeIf { it >= 0 } ?: blockIndex.coerceIn(blocks.indices)
}

private fun RichContentBox.paragraphPlainTextOrEmpty(blockIndex: Int): String =
    (content.blocks.getOrNull(blockIndex) as? ParagraphNode)?.plainText().orEmpty()

private fun RichContentBox.paragraphTextLength(blockIndex: Int): Int =
    (content.blocks.getOrNull(blockIndex) as? ParagraphNode)?.textLength() ?: 0

private fun ParagraphNode.plainText(): String = inlines.joinToString("") { inline ->
    when (inline) {
        is InlineText -> inline.text
        com.neonote.model.InlineLineBreak -> "\n"
        else -> ""
    }
}

private fun ParagraphNode.textLength(): Int = inlines.sumOf { inline ->
    when (inline) {
        is InlineText -> inline.text.length
        com.neonote.model.InlineLineBreak -> 1
        else -> 0
    }
}
