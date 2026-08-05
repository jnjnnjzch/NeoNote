package com.neonote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
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
                    }.coerceIn(88f, 420f)
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
                            .heightIn(min = 48.dp)
                            .background(
                                if (isHeader) Color(0xFFF1EEF9)
                                else cell.backgroundColorArgb?.let(::Color) ?: Color.White,
                            )
                            .border(
                                if (activeContentBlockIndex != null) 1.5.dp else 1.dp,
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
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(Color(0xFFF7F5FC), RoundedCornerShape(11.dp))
            .padding(horizontal = 5.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TableContextAction("＋ Row", "Add table row") { controller.addActiveRichContentTableRow(boxId) }
        TableContextAction("− Row", "Delete table row") { controller.deleteActiveRichContentTableRow(boxId) }
        TableContextAction("＋ Column", "Add table column") { controller.addActiveRichContentTableColumn(boxId) }
        TableContextAction("− Column", "Delete table column") { controller.deleteActiveRichContentTableColumn(boxId) }
        TableContextAction("Width", "Set table column width") {
            controller.setActiveRichContentTableColumnWidth(boxId, 160f)
        }
        TableContextAction("Auto", "Use automatic table column width") {
            controller.setActiveRichContentTableColumnWidth(boxId, null)
        }
        TableContextAction("Nested", "Insert nested table") {
            controller.insertNestedTable(boxId, address, 2, 2)
        }
    }
}

@Composable
private fun TableContextAction(
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        modifier = Modifier
            .defaultMinSize(minHeight = 42.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(9.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

private fun TableCellAddress.addressesSameCell(other: TableCellAddress): Boolean = path == other.path
