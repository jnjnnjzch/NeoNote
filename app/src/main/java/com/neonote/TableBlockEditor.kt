package com.neonote

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.neonote.engine.ActiveRichContentTarget
import com.neonote.engine.RichContentMeasurer
import com.neonote.model.RichContentBox
import com.neonote.model.TableCell
import com.neonote.model.TableCellAddress
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
    val layout = RichContentMeasurer().measure(box).tableLayouts.firstOrNull { it.blockIndex == blockIndex }
    val rowCount = layout?.rowHeights?.size ?: table.rows.size
    val columnCount = layout?.columnWidths?.size ?: (table.rows.maxOfOrNull { it.size } ?: 0)
    val activeAddress = (controller.activeRichContentTarget(box.id) as? ActiveRichContentTarget.TableCell)?.address

    Column(modifier = modifier) {
        repeat(rowCount) { rowIndex ->
            Row {
                repeat(columnCount) { columnIndex ->
                    val address = TableCellAddress(blockIndex = blockIndex, rowIndex = rowIndex, columnIndex = columnIndex)
                    val cell = table.rows.getOrNull(rowIndex)?.getOrNull(columnIndex) ?: TableCell()
                    TableCellEditor(
                        boxId = box.id,
                        address = address,
                        cell = cell,
                        active = activeAddress == address,
                        selectionMode = selectionMode,
                        selected = selected,
                        controller = controller,
                        modifier = Modifier
                            .width((layout?.columnWidths?.getOrNull(columnIndex) ?: 80f).dp)
                            .height((layout?.rowHeights?.getOrNull(rowIndex) ?: 32f).dp)
                            .border(width = 1.dp, color = if (activeAddress == address) Color(0xFF7C3AED) else Color(0xFFCBD5E1))
                            .clickable(enabled = !selectionMode && !selected) {
                                controller.focusRichContentTableCell(boxId = box.id, address = address)
                            },
                    )
                }
            }
        }
    }
}
