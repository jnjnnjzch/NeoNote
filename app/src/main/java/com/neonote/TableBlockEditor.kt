package com.neonote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.neonote.engine.ActiveRichContentTarget
import com.neonote.model.RichContentBox
import com.neonote.model.TableCell
import com.neonote.model.TableCellAddress
import com.neonote.model.TableCellVerticalAlignment
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
    SpanAwareTableGrid(
        table = table,
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) { rowIndex, columnIndex, cell ->
        val address = if (parentCellAddress == null) {
            TableCellAddress(rootBlockIndex, rowIndex, columnIndex)
        } else {
            parentCellAddress.child(tableBlockIndexInParent, rowIndex, columnIndex)
        }
        val activeContentBlockIndex = activeAddress
            ?.takeIf { it.addressesSameCell(address) }
            ?.contentBlockIndex
        val isHeader = rowIndex < table.headerRowCount
        val borderModifier = if (table.showBorders) {
            Modifier.border(
                1.dp,
                if (activeContentBlockIndex != null) Color(0xFF6D4AFF) else Color(0xFFD3CEDD),
            )
        } else Modifier
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isHeader) Color(0xFFF1EEF9) else cell.backgroundColorArgb?.let(::Color) ?: Color.White)
                .then(borderModifier)
                .clickable(enabled = !selectionMode && !selected) {
                    controller.focusRichContentTableCell(
                        box.id,
                        address.withContentBlock(cell.firstEditableParagraphIndex()),
                    )
                },
            contentAlignment = when (cell.verticalAlignment) {
                TableCellVerticalAlignment.Top -> Alignment.TopStart
                TableCellVerticalAlignment.Center -> Alignment.CenterStart
                TableCellVerticalAlignment.Bottom -> Alignment.BottomStart
            },
        ) {
            TableCellEditor(
                boxId = box.id,
                rootBlockIndex = rootBlockIndex,
                address = address,
                cell = cell,
                activeContentBlockIndex = activeContentBlockIndex,
                selectionMode = selectionMode,
                selected = selected,
                controller = controller,
                modifier = Modifier.fillMaxWidth(),
            )
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
            .horizontalScroll(rememberScrollState())
            .background(Color(0xFFF7F5FC), RoundedCornerShape(10.dp))
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TextButton(onClick = { controller.addActiveTableRowSafely(boxId) }) { Text("+ Row") }
        TextButton(onClick = { controller.deleteActiveTableRowSafely(boxId) }) { Text("− Row") }
        TextButton(onClick = { controller.addActiveTableColumnSafely(boxId) }) { Text("+ Column") }
        TextButton(onClick = { controller.deleteActiveTableColumnSafely(boxId) }) { Text("− Column") }
        TextButton(onClick = { controller.setActiveRichContentTableColumnWidth(boxId, 160f) }) { Text("Width") }
        TextButton(onClick = { controller.setActiveRichContentTableColumnWidth(boxId, null) }) { Text("Auto") }
        TextButton(onClick = { controller.mergeActiveTableCellRight(boxId) }) { Text("Merge →") }
        TextButton(onClick = { controller.mergeActiveTableCellDown(boxId) }) { Text("Merge ↓") }
        TextButton(onClick = { controller.splitActiveTableCell(boxId) }) { Text("Split") }
        TextButton(onClick = { controller.toggleActiveTableHeader(boxId) }) { Text("Header") }
        TextButton(onClick = { controller.toggleActiveTableCellShade(boxId) }) { Text("Shade") }
        TextButton(onClick = { controller.cycleActiveTableCellAlignment(boxId) }) { Text("Align") }
        TextButton(onClick = { controller.toggleActiveTableBorders(boxId) }) { Text("Borders") }
        TextButton(onClick = { controller.insertNestedTable(boxId, address, 2, 2) }) { Text("Nested") }
    }
}

private fun TableCellAddress.addressesSameCell(other: TableCellAddress): Boolean = path == other.path
