package com.neonote.engine

import com.neonote.model.RichContent
import com.neonote.model.TableCell
import com.neonote.model.TableCellMergeAnchor
import com.neonote.model.TableNode

/** Pure rectangular merge/split operations used at every nesting depth. */
public object TableMergeEngine {
    public fun merge(
        table: TableNode,
        firstRow: Int,
        lastRow: Int,
        firstColumn: Int,
        lastColumn: Int,
    ): TableNode {
        val normalized = table.normalizedGrid()
        require(firstRow in normalized.rows.indices && lastRow in normalized.rows.indices && firstRow <= lastRow)
        val columns = normalized.gridColumnCount()
        require(firstColumn in 0 until columns && lastColumn in 0 until columns && firstColumn <= lastColumn)

        val selected = buildSet {
            for (row in firstRow..lastRow) for (column in firstColumn..lastColumn) add(row to column)
        }
        selected.forEach { coordinate ->
            val (row, column) = coordinate
            val cell = normalized.rows[row][column]
            val anchor = cell.mergedInto?.let { it.rowIndex to it.columnIndex } ?: coordinate
            require(anchor in selected) { "Selection intersects an existing merge" }
            val anchorCell = normalized.rows[anchor.first][anchor.second]
            require(anchorCell.coveredCoordinates(anchor.first, anchor.second).all(selected::contains)) {
                "Selection must include complete merged cells"
            }
        }

        val anchors = selected
            .map { coordinate ->
                val cell = normalized.rows[coordinate.first][coordinate.second]
                cell.mergedInto?.let { it.rowIndex to it.columnIndex } ?: coordinate
            }
            .distinct()
            .sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
        val mergedBlocks = anchors.flatMap { (row, column) -> normalized.rows[row][column].content.blocks }
        val anchorCoordinate = firstRow to firstColumn
        val nextRows = normalized.rows.mapIndexed { rowIndex, row ->
            row.mapIndexed { columnIndex, cell ->
                val coordinate = rowIndex to columnIndex
                when {
                    coordinate !in selected -> cell
                    coordinate == anchorCoordinate -> cell.copy(
                        content = RichContent(mergedBlocks),
                        rowSpan = lastRow - firstRow + 1,
                        columnSpan = lastColumn - firstColumn + 1,
                        mergedInto = null,
                    )
                    else -> TableCell(
                        content = RichContent(),
                        backgroundColorArgb = cell.backgroundColorArgb,
                        verticalAlignment = cell.verticalAlignment,
                        mergedInto = TableCellMergeAnchor(firstRow, firstColumn),
                    )
                }
            }
        }
        return normalized.copy(rows = nextRows)
    }

    public fun mergeRight(table: TableNode, row: Int, column: Int): TableNode {
        val normalized = table.normalizedGrid()
        val (anchorRow, anchorColumn) = normalized.resolveAnchor(row, column)
        val anchor = normalized.rows[anchorRow][anchorColumn]
        val nextColumn = anchorColumn + anchor.columnSpan
        if (nextColumn >= normalized.gridColumnCount()) return normalized
        return merge(
            normalized,
            anchorRow,
            anchorRow + anchor.rowSpan - 1,
            anchorColumn,
            nextColumn,
        )
    }

    public fun mergeDown(table: TableNode, row: Int, column: Int): TableNode {
        val normalized = table.normalizedGrid()
        val (anchorRow, anchorColumn) = normalized.resolveAnchor(row, column)
        val anchor = normalized.rows[anchorRow][anchorColumn]
        val nextRow = anchorRow + anchor.rowSpan
        if (nextRow >= normalized.rows.size) return normalized
        return merge(
            normalized,
            anchorRow,
            nextRow,
            anchorColumn,
            anchorColumn + anchor.columnSpan - 1,
        )
    }

    public fun splitAt(table: TableNode, row: Int, column: Int): TableNode {
        val normalized = table.normalizedGrid()
        val (anchorRow, anchorColumn) = normalized.resolveAnchor(row, column)
        val anchor = normalized.rows[anchorRow][anchorColumn]
        if (anchor.rowSpan == 1 && anchor.columnSpan == 1) return normalized
        val covered = anchor.coveredCoordinates(anchorRow, anchorColumn).toSet()
        return normalized.copy(rows = normalized.rows.mapIndexed { rowIndex, currentRow ->
            currentRow.mapIndexed { columnIndex, cell ->
                val coordinate = rowIndex to columnIndex
                if (coordinate !in covered) cell
                else if (coordinate == anchorRow to anchorColumn) cell.copy(
                    rowSpan = 1,
                    columnSpan = 1,
                    mergedInto = null,
                ) else cell.copy(mergedInto = null, rowSpan = 1, columnSpan = 1)
            }
        })
    }

    /** Structural row/column edits deliberately clear merges instead of leaving invalid anchors. */
    public fun splitAll(table: TableNode): TableNode {
        var result = table.normalizedGrid()
        result.rows.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { columnIndex, cell ->
                if (cell.mergedInto == null && (cell.rowSpan > 1 || cell.columnSpan > 1)) {
                    result = splitAt(result, rowIndex, columnIndex)
                }
            }
        }
        return result
    }
}

public fun TableNode.normalizedGrid(): TableNode {
    val rowCount = rows.size.coerceAtLeast(1)
    val columns = gridColumnCount().coerceAtLeast(1)
    val grid = MutableList(rowCount) { rowIndex ->
        val row = rows.getOrNull(rowIndex).orEmpty()
        MutableList(columns) { columnIndex ->
            val cell = row.getOrNull(columnIndex) ?: TableCell()
            cell.copy(
                rowSpan = if (cell.mergedInto == null) cell.rowSpan.coerceAtLeast(1).coerceAtMost(rowCount - rowIndex) else 1,
                columnSpan = if (cell.mergedInto == null) cell.columnSpan.coerceAtLeast(1).coerceAtMost(columns - columnIndex) else 1,
                mergedInto = null,
            )
        }
    }
    val occupied = mutableSetOf<Pair<Int, Int>>()
    for (rowIndex in 0 until rowCount) {
        for (columnIndex in 0 until columns) {
            val coordinate = rowIndex to columnIndex
            val source = rows.getOrNull(rowIndex)?.getOrNull(columnIndex)
            if (coordinate in occupied || source?.mergedInto != null) continue
            val anchor = grid[rowIndex][columnIndex]
            val covered = anchor.coveredCoordinates(rowIndex, columnIndex)
                .filter { (row, column) -> row in 0 until rowCount && column in 0 until columns }
                .toList()
            if (covered.drop(1).any(occupied::contains)) {
                grid[rowIndex][columnIndex] = anchor.copy(rowSpan = 1, columnSpan = 1)
                continue
            }
            covered.forEach { coveredCoordinate ->
                occupied += coveredCoordinate
                if (coveredCoordinate != coordinate) {
                    val (coveredRow, coveredColumn) = coveredCoordinate
                    grid[coveredRow][coveredColumn] = grid[coveredRow][coveredColumn].copy(
                        rowSpan = 1,
                        columnSpan = 1,
                        mergedInto = TableCellMergeAnchor(rowIndex, columnIndex),
                    )
                }
            }
        }
    }
    return copy(rows = grid.map(List<TableCell>::toList))
}

public fun TableNode.gridColumnCount(): Int = rows.maxOfOrNull(List<TableCell>::size) ?: 0

public fun TableNode.resolveAnchor(row: Int, column: Int): Pair<Int, Int> {
    val cell = rows.getOrNull(row)?.getOrNull(column) ?: return row to column
    return cell.mergedInto?.let { it.rowIndex to it.columnIndex } ?: row to column
}

private fun TableCell.coveredCoordinates(row: Int, column: Int): Sequence<Pair<Int, Int>> = sequence {
    for (coveredRow in row until row + rowSpan.coerceAtLeast(1)) {
        for (coveredColumn in column until column + columnSpan.coerceAtLeast(1)) {
            yield(coveredRow to coveredColumn)
        }
    }
}
