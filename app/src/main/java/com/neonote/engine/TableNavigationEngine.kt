package com.neonote.engine

import com.neonote.model.TableCell
import com.neonote.model.TableNode

public data class TableNavigationResult(
    val table: TableNode,
    val rowIndex: Int,
    val columnIndex: Int,
    val appendedRow: Boolean,
)

/** Spreadsheet-like Tab/Shift+Tab navigation that skips merged continuation cells. */
public object TableNavigationEngine {
    public fun navigate(
        source: TableNode,
        rowIndex: Int,
        columnIndex: Int,
        backwards: Boolean,
    ): TableNavigationResult {
        val table = source.normalizedGrid()
        val anchors = table.anchorCoordinates()
        if (anchors.isEmpty()) {
            val repaired = table.copy(rows = listOf(listOf(TableCell())))
            return TableNavigationResult(repaired, 0, 0, appendedRow = true)
        }
        val resolved = table.resolveAnchor(rowIndex, columnIndex)
        val current = anchors.indexOf(resolved).takeIf { it >= 0 } ?: 0
        if (backwards) {
            val target = anchors[(current - 1).coerceAtLeast(0)]
            return TableNavigationResult(table, target.first, target.second, appendedRow = false)
        }
        if (current < anchors.lastIndex) {
            val target = anchors[current + 1]
            return TableNavigationResult(table, target.first, target.second, appendedRow = false)
        }

        val columns = table.gridColumnCount().coerceAtLeast(1)
        val appended = table.copy(rows = table.rows + listOf(List(columns) { TableCell() }))
        return TableNavigationResult(
            table = appended,
            rowIndex = appended.rows.lastIndex,
            columnIndex = 0,
            appendedRow = true,
        )
    }
}

private fun TableNode.anchorCoordinates(): List<Pair<Int, Int>> = buildList {
    rows.forEachIndexed { rowIndex, row ->
        row.forEachIndexed { columnIndex, cell ->
            if (cell.mergedInto == null) add(rowIndex to columnIndex)
        }
    }
}
