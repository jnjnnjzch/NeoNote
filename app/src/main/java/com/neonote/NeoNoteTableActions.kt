package com.neonote

import com.neonote.engine.ActiveRichContentTarget
import com.neonote.engine.RichContentMeasurer
import com.neonote.engine.RichContentTree
import com.neonote.engine.TableMergeEngine
import com.neonote.engine.gridColumnCount
import com.neonote.engine.normalizedGrid
import com.neonote.engine.resolveAnchor
import com.neonote.model.RichContentBox
import com.neonote.model.TableCell
import com.neonote.model.TableCellAddress
import com.neonote.model.TableColumnPolicy
import com.neonote.model.TableCellVerticalAlignment
import com.neonote.model.TableNode

internal fun NeoNoteEditorController.addActiveTableRowSafely(boxId: String) =
    mutateActiveTable(boxId) { source, address ->
        val table = TableMergeEngine.splitAll(source).normalizedGrid()
        val step = address.path.last()
        val insertion = (step.rowIndex + 1).coerceIn(0, table.rows.size)
        table.copy(rows = table.rows.take(insertion) + listOf(List(table.gridColumnCount()) { TableCell() }) + table.rows.drop(insertion))
    }

internal fun NeoNoteEditorController.deleteActiveTableRowSafely(boxId: String) =
    mutateActiveTable(boxId) { source, address ->
        val table = TableMergeEngine.splitAll(source).normalizedGrid()
        if (table.rows.size <= 1) table
        else table.copy(rows = table.rows.filterIndexed { index, _ -> index != address.path.last().rowIndex.coerceIn(table.rows.indices) })
    }

internal fun NeoNoteEditorController.addActiveTableColumnSafely(boxId: String) =
    mutateActiveTable(boxId) { source, address ->
        val table = TableMergeEngine.splitAll(source).normalizedGrid()
        val columns = table.gridColumnCount()
        val insertion = (address.path.last().columnIndex + 1).coerceIn(0, columns)
        table.copy(
            rows = table.rows.map { row -> row.take(insertion) + TableCell() + row.drop(insertion) },
            columnPolicies = table.columnPolicies.normalizedPolicies(columns).let { policies ->
                policies.take(insertion) + TableColumnPolicy() + policies.drop(insertion)
            },
        )
    }

internal fun NeoNoteEditorController.deleteActiveTableColumnSafely(boxId: String) =
    mutateActiveTable(boxId) { source, address ->
        val table = TableMergeEngine.splitAll(source).normalizedGrid()
        val columns = table.gridColumnCount()
        if (columns <= 1) table
        else {
            val target = address.path.last().columnIndex.coerceIn(0, columns - 1)
            table.copy(
                rows = table.rows.map { row -> row.filterIndexed { index, _ -> index != target } },
                columnPolicies = table.columnPolicies.normalizedPolicies(columns).filterIndexed { index, _ -> index != target },
            )
        }
    }

internal fun NeoNoteEditorController.mergeActiveTableCellRight(boxId: String) =
    mutateActiveTable(boxId) { table, address ->
        val step = address.path.last()
        TableMergeEngine.mergeRight(table, step.rowIndex, step.columnIndex)
    }

internal fun NeoNoteEditorController.mergeActiveTableCellDown(boxId: String) =
    mutateActiveTable(boxId) { table, address ->
        val step = address.path.last()
        TableMergeEngine.mergeDown(table, step.rowIndex, step.columnIndex)
    }

internal fun NeoNoteEditorController.splitActiveTableCell(boxId: String) =
    mutateActiveTable(boxId) { table, address ->
        val step = address.path.last()
        TableMergeEngine.splitAt(table, step.rowIndex, step.columnIndex)
    }

internal fun NeoNoteEditorController.toggleActiveTableHeader(boxId: String) =
    mutateActiveTable(boxId) { table, _ -> table.copy(headerRowCount = if (table.headerRowCount > 0) 0 else 1) }

internal fun NeoNoteEditorController.toggleActiveTableBorders(boxId: String) =
    mutateActiveTable(boxId) { table, _ -> table.copy(showBorders = !table.showBorders) }

internal fun NeoNoteEditorController.toggleActiveTableCellShade(boxId: String) =
    mutateActiveCell(boxId) { cell ->
        cell.copy(backgroundColorArgb = if (cell.backgroundColorArgb == null) 0xFFFFF3C4.toInt() else null)
    }

internal fun NeoNoteEditorController.cycleActiveTableCellAlignment(boxId: String) =
    mutateActiveCell(boxId) { cell ->
        cell.copy(verticalAlignment = when (cell.verticalAlignment) {
            TableCellVerticalAlignment.Top -> TableCellVerticalAlignment.Center
            TableCellVerticalAlignment.Center -> TableCellVerticalAlignment.Bottom
            TableCellVerticalAlignment.Bottom -> TableCellVerticalAlignment.Top
        })
    }

private inline fun NeoNoteEditorController.mutateActiveTable(
    boxId: String,
    crossinline transform: (TableNode, TableCellAddress) -> TableNode,
) {
    val address = (activeRichContentTarget(boxId) as? ActiveRichContentTarget.TableCell)?.address ?: return
    mutateTableDocument(boxId, address) { content ->
        RichContentTree.updateTable(content, address) { table -> transform(table, address) }
    }
}

private inline fun NeoNoteEditorController.mutateActiveCell(
    boxId: String,
    crossinline transform: (com.neonote.model.TableCell) -> com.neonote.model.TableCell,
) {
    val active = (activeRichContentTarget(boxId) as? ActiveRichContentTarget.TableCell)?.address ?: return
    val table = RichContentTree.table(
        currentCanvas.objects.filterIsInstance<RichContentBox>().firstOrNull { it.id == boxId }?.content ?: return,
        active,
    ) ?: return
    val step = active.path.last()
    val anchor = table.resolveAnchor(step.rowIndex, step.columnIndex)
    val anchorAddress = active.withLastCell(anchor.first, anchor.second)
    mutateTableDocument(boxId, anchorAddress) { content -> RichContentTree.updateCell(content, anchorAddress, transform) }
}

private inline fun NeoNoteEditorController.mutateTableDocument(
    boxId: String,
    focusAddress: TableCellAddress,
    crossinline transform: (com.neonote.model.RichContent) -> com.neonote.model.RichContent,
) {
    val original = state
    val pageId = original.currentPageId ?: return
    val pageIndex = original.document.pages.indexOfFirst { it.id == pageId }
    if (pageIndex < 0) return
    val page = original.document.pages[pageIndex]
    var changed = false
    val objects = page.canvas.objects.map { value ->
        if (value !is RichContentBox || value.id != boxId || value.isLocked) return@map value
        val nextContent = transform(value.content)
        if (nextContent == value.content) return@map value
        changed = true
        val updated = value.copy(content = nextContent)
        if (updated.autoSizeHeight) RichContentMeasurer().resizeBoxToMeasuredContent(updated) else updated
    }
    if (!changed) return
    val now = System.currentTimeMillis()
    val nextPage = page.copy(canvas = page.canvas.copy(objects = objects), updatedAtEpochMillis = now)
    val document = original.document.copy(
        pages = original.document.pages.mapIndexed { index, item -> if (index == pageIndex) nextPage else item },
        revision = original.document.revision + 1,
        updatedAtEpochMillis = now,
    )
    replaceDocument(document, recordHistory = true)
    if (state.currentPageId != pageId) switchPage(pageId)
    setTool(com.neonote.model.EditorTool.Text)
    focusRichContentTableCell(boxId, focusAddress)
}

private fun TableCellAddress.withLastCell(row: Int, column: Int): TableCellAddress =
    if (nestedPath.isEmpty()) copy(rowIndex = row, columnIndex = column)
    else copy(nestedPath = nestedPath.dropLast(1) + nestedPath.last().copy(rowIndex = row, columnIndex = column))

private fun List<TableColumnPolicy>.normalizedPolicies(columns: Int): List<TableColumnPolicy> =
    take(columns) + List((columns - size).coerceAtLeast(0)) { TableColumnPolicy() }
