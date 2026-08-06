package com.neonote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.neonote.engine.ActiveRichContentTarget
import com.neonote.model.BlockFormula
import com.neonote.model.BlockImage
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContentBox
import com.neonote.model.TableCell
import com.neonote.model.TableCellAddress
import com.neonote.model.TableColumnWidthMode
import com.neonote.model.TableNode

@Composable
internal fun TableBlockEditor(
    box: RichContentBox,
    blockIndex: Int,
    table: TableNode,
    selectionMode: Boolean,
    selected: Boolean,
    controller: NeoNoteEditorController,
    modifier: Modifier = Modifier,
) {
    val active = (controller.activeRichContentTarget(box.id) as? ActiveRichContentTarget.TableCell)?.address
    EditableTableGrid(
        box = box,
        rootBlockIndex = blockIndex,
        table = table,
        parentCellAddress = null,
        tableBlockIndexInParent = blockIndex,
        activeAddress = active,
        selectionMode = selectionMode,
        selected = selected,
        controller = controller,
        modifier = modifier,
    )
}

@Composable
internal fun EditableTableGrid(
    box: RichContentBox,
    rootBlockIndex: Int,
    table: TableNode,
    parentCellAddress: TableCellAddress?,
    tableBlockIndexInParent: Int,
    activeAddress: TableCellAddress?,
    selectionMode: Boolean,
    selected: Boolean,
    controller: NeoNoteEditorController,
    modifier: Modifier = Modifier,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val rowCount = table.rows.size.coerceAtLeast(1)
    val columnCount = (table.rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
    Column(modifier = modifier.horizontalScroll(rememberScrollState())) {
        repeat(rowCount) { rowIndex ->
            Row {
                repeat(columnCount) { columnIndex ->
                    val address = if (parentCellAddress == null) {
                        TableCellAddress(rootBlockIndex, rowIndex, columnIndex)
                    } else parentCellAddress.child(tableBlockIndexInParent, rowIndex, columnIndex)
                    val cell = table.rows.getOrNull(rowIndex)?.getOrNull(columnIndex) ?: TableCell()
                    val activeContentBlockIndex = activeAddress
                        ?.takeIf { it.path == address.path }
                        ?.contentBlockIndex
                    val policy = table.columnPolicies.getOrNull(columnIndex)
                    val width = when (policy?.mode) {
                        TableColumnWidthMode.Manual -> policy.manualWidth ?: 120f
                        else -> policy?.preferredWidth ?: 120f
                    }.coerceIn(72f, 420f)
                    val activeCell = activeContentBlockIndex != null
                    val isHeader = rowIndex < table.headerRowCount
                    var resizeWidth by remember(address, width) { mutableFloatStateOf(width) }
                    Box(Modifier.width(width.dp)) {
                        TableCellEditor(
                            boxId = box.id,
                            rootBlockIndex = rootBlockIndex,
                            address = address,
                            cell = cell,
                            activeContentBlockIndex = activeContentBlockIndex,
                            selectionMode = selectionMode,
                            selected = selected,
                            controller = controller,
                            modifier = Modifier
                                .width(width.dp)
                                .heightIn(min = 34.dp)
                                .background(
                                    when {
                                        isHeader -> Color(0xFFF4F1FA)
                                        cell.backgroundColorArgb != null -> Color(cell.backgroundColorArgb)
                                        else -> Color.Transparent
                                    },
                                )
                                .border(
                                    if (activeCell) 1.5.dp else 0.75.dp,
                                    if (activeCell) Color(0xFF6D4AFF) else Color(0xFFD8D4DF),
                                ),
                        )
                        if (activeCell && !selectionMode && !selected) {
                            Box(
                                Modifier
                                    .align(Alignment.CenterEnd)
                                    .width(14.dp)
                                    .height(38.dp)
                                    .pointerInput(address) {
                                        detectHorizontalDragGestures(
                                            onDragStart = {
                                                resizeWidth = width
                                                controller.beginRichContentGesture(box.id)
                                            },
                                            onDragCancel = { controller.cancelRichContentGesture(box.id) },
                                            onDragEnd = { controller.endRichContentGesture(box.id) },
                                            onHorizontalDrag = { change, dragAmount ->
                                                change.consume()
                                                resizeWidth = (resizeWidth + dragAmount / density).coerceIn(72f, 420f)
                                                controller.setActiveRichContentTableColumnWidth(box.id, resizeWidth)
                                            },
                                        )
                                    },
                            ) {
                                Box(
                                    Modifier
                                        .align(Alignment.Center)
                                        .width(2.dp)
                                        .height(28.dp)
                                        .background(Color(0xFF6D4AFF).copy(alpha = .65f)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


/** Content-aware width used by the live editor when a column is in automatic mode. */
internal fun TableNode.suggestedEditorColumnWidth(columnIndex: Int): Float {
    val widest = rows.maxOfOrNull { row -> row.getOrNull(columnIndex)?.suggestedEditorCellWidth() ?: 0f } ?: 0f
    return widest.coerceIn(112f, 280f)
}

private fun TableCell.suggestedEditorCellWidth(): Float {
    val blockWidth = content.blocks.maxOfOrNull { block ->
        when (block) {
            is ParagraphNode -> {
                val longestLine = block.inlines.joinToString("") { inline ->
                    when (inline) {
                        is com.neonote.model.InlineText -> inline.text
                        com.neonote.model.InlineLineBreak -> "\n"
                        else -> "□"
                    }
                }.lineSequence().maxOfOrNull(String::length) ?: 0
                val structuralIndent = block.style.indentLevel + if (block.listMetadata != null) 1 else 0
                28f + longestLine.coerceAtMost(30) * 8.2f + structuralIndent * 14f
            }
            is BlockFormula -> 44f + block.expression.length.coerceAtMost(24) * 8f
            is BlockImage -> 168f
            is TableNode -> 188f
        }
    } ?: 0f
    return blockWidth
}
