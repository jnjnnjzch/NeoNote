package com.neonote.engine

import com.neonote.model.BlockNode
import com.neonote.model.RichContent
import com.neonote.model.TableCell
import com.neonote.model.TableCellAddress
import com.neonote.model.TableCellPathSegment
import com.neonote.model.TableNode

/**
 * Pure recursive navigation for rich content stored in table cells.
 * All functions return unchanged content when a path is invalid; commands can
 * choose whether invalid input should be ignored or rejected before calling.
 */
public object RichContentTree {
    public fun cell(content: RichContent, address: TableCellAddress): TableCell? =
        cell(content, address.path)

    public fun cellContent(content: RichContent, address: TableCellAddress): RichContent? =
        cell(content, address)?.content

    public fun table(content: RichContent, address: TableCellAddress): TableNode? =
        table(content, address.path)

    public fun block(content: RichContent, address: TableCellAddress): BlockNode? =
        cellContent(content, address)?.blocks?.getOrNull(address.contentBlockIndex)

    public fun updateCell(
        content: RichContent,
        address: TableCellAddress,
        transform: (TableCell) -> TableCell,
    ): RichContent = updateCell(content, address.path, transform)

    public fun updateCellContent(
        content: RichContent,
        address: TableCellAddress,
        transform: (RichContent) -> RichContent,
    ): RichContent = updateCell(content, address) { cell ->
        cell.copy(content = transform(cell.content))
    }

    /** Update the table containing the addressed cell, at any nesting depth. */
    public fun updateTable(
        content: RichContent,
        address: TableCellAddress,
        transform: (TableNode) -> TableNode,
    ): RichContent = updateTable(content, address.path, transform)

    public fun replaceBlock(
        content: RichContent,
        address: TableCellAddress,
        replacement: BlockNode,
    ): RichContent = updateCellContent(content, address) { cellContent ->
        val blocks = cellContent.blocks
        if (address.contentBlockIndex !in blocks.indices) cellContent
        else cellContent.copy(blocks = blocks.replaceAt(address.contentBlockIndex, replacement))
    }

    public fun insertBlock(
        content: RichContent,
        address: TableCellAddress,
        block: BlockNode,
        index: Int = address.contentBlockIndex,
    ): RichContent = updateCellContent(content, address) { cellContent ->
        val safeIndex = index.coerceIn(0, cellContent.blocks.size)
        cellContent.copy(blocks = cellContent.blocks.take(safeIndex) + block + cellContent.blocks.drop(safeIndex))
    }

    public fun deleteBlock(content: RichContent, address: TableCellAddress): RichContent =
        updateCellContent(content, address) { cellContent ->
            if (address.contentBlockIndex !in cellContent.blocks.indices) cellContent
            else cellContent.copy(blocks = cellContent.blocks.filterIndexed { index, _ -> index != address.contentBlockIndex })
        }

    public fun isValid(content: RichContent, address: TableCellAddress): Boolean =
        cell(content, address) != null

    private fun cell(content: RichContent, path: List<TableCellPathSegment>): TableCell? {
        if (path.isEmpty()) return null
        val segment = path.first()
        val table = content.blocks.getOrNull(segment.tableBlockIndex) as? TableNode ?: return null
        val cell = table.rows.getOrNull(segment.rowIndex)?.getOrNull(segment.columnIndex) ?: return null
        return if (path.size == 1) cell else cell(cell.content, path.drop(1))
    }

    private fun table(content: RichContent, path: List<TableCellPathSegment>): TableNode? {
        if (path.isEmpty()) return null
        val segment = path.first()
        val table = content.blocks.getOrNull(segment.tableBlockIndex) as? TableNode ?: return null
        if (path.size == 1) return table
        val cell = table.rows.getOrNull(segment.rowIndex)?.getOrNull(segment.columnIndex) ?: return null
        return table(cell.content, path.drop(1))
    }

    private fun updateCell(
        content: RichContent,
        path: List<TableCellPathSegment>,
        transform: (TableCell) -> TableCell,
    ): RichContent {
        if (path.isEmpty()) return content
        val segment = path.first()
        val table = content.blocks.getOrNull(segment.tableBlockIndex) as? TableNode ?: return content
        val row = table.rows.getOrNull(segment.rowIndex) ?: return content
        val currentCell = row.getOrNull(segment.columnIndex) ?: return content
        val nextCell = if (path.size == 1) {
            transform(currentCell)
        } else {
            currentCell.copy(content = updateCell(currentCell.content, path.drop(1), transform))
        }
        if (nextCell == currentCell) return content
        val nextRow = row.replaceAt(segment.columnIndex, nextCell)
        val nextTable = table.copy(rows = table.rows.replaceAt(segment.rowIndex, nextRow))
        return content.copy(blocks = content.blocks.replaceAt(segment.tableBlockIndex, nextTable))
    }

    private fun updateTable(
        content: RichContent,
        path: List<TableCellPathSegment>,
        transform: (TableNode) -> TableNode,
    ): RichContent {
        if (path.isEmpty()) return content
        val segment = path.first()
        val table = content.blocks.getOrNull(segment.tableBlockIndex) as? TableNode ?: return content
        if (path.size == 1) {
            val nextTable = transform(table)
            return if (nextTable == table) content
            else content.copy(blocks = content.blocks.replaceAt(segment.tableBlockIndex, nextTable))
        }
        val row = table.rows.getOrNull(segment.rowIndex) ?: return content
        val currentCell = row.getOrNull(segment.columnIndex) ?: return content
        val nextContent = updateTable(currentCell.content, path.drop(1), transform)
        if (nextContent == currentCell.content) return content
        val nextCell = currentCell.copy(content = nextContent)
        val nextRow = row.replaceAt(segment.columnIndex, nextCell)
        val nextTable = table.copy(rows = table.rows.replaceAt(segment.rowIndex, nextRow))
        return content.copy(blocks = content.blocks.replaceAt(segment.tableBlockIndex, nextTable))
    }
}

internal fun <T> List<T>.replaceAt(index: Int, item: T): List<T> =
    mapIndexed { currentIndex, currentItem -> if (currentIndex == index) item else currentItem }
