package com.neonote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.neonote.engine.ActiveRichContentTarget
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (active?.path?.firstOrNull()?.tableBlockIndex == blockIndex && !selectionMode && !selected) {
            TableContextBar(box.id, active, controller)
        }
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
        )
    }
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
    val rowCount = table.rows.size.coerceAtLeast(1)
    val columnCount = (table.rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
    Column(
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        repeat(rowCount) { rowIndex ->
            Row {
                repeat(columnCount) { columnIndex ->
                    val address = if (parentCellAddress == null) {
                        TableCellAddress(rootBlockIndex, rowIndex, columnIndex)
                    } else {
                        parentCellAddress.child(tableBlockIndexInParent, rowIndex, columnIndex)
                    }
                    val cell = table.rows.getOrNull(rowIndex)?.getOrNull(columnIndex) ?: TableCell()
                    val activeContentBlockIndex = activeAddress
                        ?.takeIf { it.addressesSameCell(address) }
                        ?.contentBlockIndex
                    val policy = table.columnPolicies.getOrNull(columnIndex)
                    val width = when (policy?.mode) {
                        TableColumnWidthMode.Manual -> policy.manualWidth ?: 120f
                        else -> policy?.preferredWidth ?: 120f
                    }.coerceIn(72f, 420f)
                    val isHeader = rowIndex < table.headerRowCount
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
                            .heightIn(min = 44.dp)
                            .background(if (isHeader) Color(0xFFF1EEF9) else cell.backgroundColorArgb?.let(::Color) ?: Color.White)
                            .border(
                                1.dp,
                                if (activeContentBlockIndex != null) Color(0xFF6D4AFF) else Color(0xFFD3CEDD),
                            )
                            .clickable(enabled = !selectionMode && !selected) {
                                controller.focusRichContentTableCell(
                                    box.id,
                                    address.withContentBlock(cell.firstEditableParagraphIndex()),
                                )
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun TableContextBar(
    boxId: String,
    address: TableCellAddress,
    controller: NeoNoteEditorController,
) {
    Row(
        modifier = Modifier
            .background(Color(0xFFF7F5FC), RoundedCornerShape(10.dp))
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TextButton(onClick = { controller.addActiveRichContentTableRow(boxId) }) { Text("+ Row") }
        TextButton(onClick = { controller.deleteActiveRichContentTableRow(boxId) }) { Text("− Row") }
        TextButton(onClick = { controller.addActiveRichContentTableColumn(boxId) }) { Text("+ Column") }
        TextButton(onClick = { controller.deleteActiveRichContentTableColumn(boxId) }) { Text("− Column") }
        TextButton(onClick = { controller.setActiveRichContentTableColumnWidth(boxId, 160f) }) { Text("Width") }
        TextButton(onClick = { controller.setActiveRichContentTableColumnWidth(boxId, null) }) { Text("Auto") }
        TextButton(onClick = { controller.insertNestedTable(boxId, address, 2, 2) }) { Text("Nested") }
    }
}

private fun TableCellAddress.addressesSameCell(other: TableCellAddress): Boolean = path == other.path
