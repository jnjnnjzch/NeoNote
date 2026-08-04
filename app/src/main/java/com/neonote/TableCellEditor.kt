package com.neonote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonote.engine.ActiveRichContentTarget
import com.neonote.engine.RichContentLayoutDefaults
import com.neonote.model.BlockFormula
import com.neonote.model.BlockImage
import com.neonote.model.InlineFormula
import com.neonote.model.InlineImage
import com.neonote.model.InlineLineBreak
import com.neonote.model.InlineText
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import com.neonote.model.TableCell
import com.neonote.model.TableCellAddress
import com.neonote.model.TableNode

@Composable
internal fun TableCellEditor(
    boxId: String,
    rootBlockIndex: Int,
    address: TableCellAddress,
    cell: TableCell,
    activeContentBlockIndex: Int?,
    selectionMode: Boolean,
    selected: Boolean,
    controller: NeoNoteEditorController,
    modifier: Modifier = Modifier,
) {
    val blocks = cell.content.blocks.ifEmpty { listOf(ParagraphNode()) }
    val activeAddress = (controller.activeRichContentTarget(boxId) as? ActiveRichContentTarget.TableCell)?.address
    Column(
        modifier = modifier.padding(horizontal = 6.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(RichContentLayoutDefaults.BlockSpacing.dp),
    ) {
        blocks.forEachIndexed { contentBlockIndex, block ->
            val blockAddress = address.withContentBlock(contentBlockIndex)
            when (block) {
                is ParagraphNode -> if (block.isPlatformEditable()) {
                    TableCellParagraphEditor(
                        boxId = boxId,
                        address = blockAddress,
                        paragraph = block,
                        active = activeContentBlockIndex == contentBlockIndex,
                        selectionMode = selectionMode,
                        selected = selected,
                        controller = controller,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    TableCellStaticBlock(boxId, blockAddress, RichContent(listOf(block)), selectionMode, selected, controller)
                }
                is TableNode -> EditableTableGrid(
                    box = controller.currentCanvas.objects.filterIsInstance<com.neonote.model.RichContentBox>()
                        .firstOrNull { it.id == boxId } ?: return@forEachIndexed,
                    rootBlockIndex = rootBlockIndex,
                    table = block,
                    parentCellAddress = blockAddress,
                    tableBlockIndexInParent = contentBlockIndex,
                    activeAddress = activeAddress,
                    selectionMode = selectionMode,
                    selected = selected,
                    controller = controller,
                    modifier = Modifier.fillMaxWidth(),
                )
                is BlockFormula, is BlockImage -> TableCellStaticBlock(
                    boxId, blockAddress, RichContent(listOf(block)), selectionMode, selected, controller,
                )
            }
        }
    }
}

@Composable
private fun TableCellParagraphEditor(
    boxId: String,
    address: TableCellAddress,
    paragraph: ParagraphNode,
    active: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    controller: NeoNoteEditorController,
    modifier: Modifier = Modifier,
) {
    val enabled = !selectionMode && !selected
    val requester = remember { FocusRequester() }
    val modelText = paragraph.plainTextForCellEditor()
    var value by remember(boxId, address) { mutableStateOf(TextFieldValue(modelText)) }
    LaunchedEffect(active, enabled) { if (active && enabled) requester.requestFocus() }
    LaunchedEffect(modelText) {
        if (value.text != modelText) value = TextFieldValue(modelText, TextRange(modelText.length))
    }
    BasicTextField(
        value = value,
        onValueChange = { next ->
            value = next
            controller.updateRichContentTableCellFromPlatformInput(boxId, address, next.text)
        },
        enabled = enabled,
        singleLine = false,
        minLines = 1,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Default,
        ),
        textStyle = LocalTextStyle.current.copy(
            color = Color(0xFF0F172A),
            lineHeight = RichContentLayoutDefaults.LineHeight.sp,
        ),
        cursorBrush = SolidColor(Color(0xFF6D4AFF)),
        modifier = modifier
            .heightIn(min = RichContentLayoutDefaults.LineHeight.dp)
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused && enabled) controller.focusRichContentTableCell(boxId, address) },
        decorationBox = { inner ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (value.text.isEmpty()) Text(" ", color = Color(0xFF94A3B8))
                inner()
            }
        },
    )
}

@Composable
private fun TableCellStaticBlock(
    boxId: String,
    address: TableCellAddress,
    content: RichContent,
    selectionMode: Boolean,
    selected: Boolean,
    controller: NeoNoteEditorController,
) {
    RichContentRenderer(
        content = content,
        selectionMode = selectionMode,
        selected = selected,
        onFocus = { controller.focusRichContentTableCell(boxId, address) },
        onToggleTodoChecked = {},
        modifier = Modifier.fillMaxWidth(),
        applyContentPadding = false,
        onObjectBlockFocus = { controller.focusRichContentTableCell(boxId, address) },
    )
}

internal fun TableCell.firstEditableParagraphIndex(): Int =
    content.blocks.indexOfFirst { it is ParagraphNode && it.isPlatformEditable() }.takeIf { it >= 0 } ?: 0

internal fun ParagraphNode.plainTextForCellEditor(): String = inlines.joinToString("") { inline -> when (inline) {
    is InlineText -> inline.text
    InlineLineBreak -> "\n"
    is InlineFormula, is InlineImage -> InlineAtomPlaceholder
} }

private fun ParagraphNode.isPlatformEditable(): Boolean = inlines.none { it is InlineFormula || it is InlineImage }

internal fun RichContent.previewTextForTableCell(): String = blocks.firstOrNull()?.let { block -> when (block) {
    is ParagraphNode -> block.plainTextForCellEditor()
    is BlockFormula -> block.expression.ifBlank { "formula" }
    is BlockImage -> block.altText?.takeIf(String::isNotBlank) ?: "image"
    is TableNode -> "nested table"
} }.orEmpty()

private const val InlineAtomPlaceholder: String = "\uFFFC"
