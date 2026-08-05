package com.neonote.engine

import com.neonote.model.TableNode

public data class TableGridCellKey(val rowIndex: Int, val columnIndex: Int)

public data class TableGridCellPlacement(
    val key: TableGridCellKey,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

public data class SpanAwareTableLayoutPlan(
    val width: Int,
    val height: Int,
    val rowHeights: List<Int>,
    val placements: List<TableGridCellPlacement>,
)

/** Deterministic pixel geometry shared by editable and read-only table renderers. */
public object SpanAwareTableLayout {
    public fun calculate(
        table: TableNode,
        columnWidths: List<Int>,
        intrinsicHeights: Map<TableGridCellKey, Int>,
        minimumRowHeight: Int,
    ): SpanAwareTableLayoutPlan {
        val normalized = table.normalizedGrid()
        val columns = normalized.gridColumnCount()
        require(columnWidths.size == columns)
        val rowHeights = MutableList(normalized.rows.size) { minimumRowHeight.coerceAtLeast(1) }
        val anchors = normalized.rows.flatMapIndexed { rowIndex, row ->
            row.mapIndexedNotNull { columnIndex, cell ->
                if (cell.mergedInto == null) TableGridCellKey(rowIndex, columnIndex) else null
            }
        }

        anchors.filter { normalized.rows[it.rowIndex][it.columnIndex].rowSpan == 1 }.forEach { key ->
            rowHeights[key.rowIndex] = maxOf(rowHeights[key.rowIndex], intrinsicHeights[key] ?: minimumRowHeight)
        }
        anchors.filter { normalized.rows[it.rowIndex][it.columnIndex].rowSpan > 1 }.forEach { key ->
            val cell = normalized.rows[key.rowIndex][key.columnIndex]
            val rows = key.rowIndex until (key.rowIndex + cell.rowSpan).coerceAtMost(rowHeights.size)
            val current = rows.sumOf(rowHeights::get)
            val required = intrinsicHeights[key] ?: minimumRowHeight
            var deficit = (required - current).coerceAtLeast(0)
            var remaining = rows.count()
            rows.forEach { rowIndex ->
                val addition = if (remaining == 0) 0 else (deficit + remaining - 1) / remaining
                rowHeights[rowIndex] += addition
                deficit -= addition
                remaining -= 1
            }
        }

        val columnOffsets = columnWidths.runningOffsets()
        val rowOffsets = rowHeights.runningOffsets()
        val placements = anchors.map { key ->
            val cell = normalized.rows[key.rowIndex][key.columnIndex]
            val columnEnd = (key.columnIndex + cell.columnSpan).coerceAtMost(columns)
            val rowEnd = (key.rowIndex + cell.rowSpan).coerceAtMost(rowHeights.size)
            TableGridCellPlacement(
                key = key,
                x = columnOffsets[key.columnIndex],
                y = rowOffsets[key.rowIndex],
                width = columnWidths.subList(key.columnIndex, columnEnd).sum(),
                height = rowHeights.subList(key.rowIndex, rowEnd).sum(),
            )
        }
        return SpanAwareTableLayoutPlan(
            width = columnWidths.sum(),
            height = rowHeights.sum(),
            rowHeights = rowHeights,
            placements = placements,
        )
    }
}

private fun List<Int>.runningOffsets(): List<Int> = buildList {
    var offset = 0
    this@runningOffsets.forEach { value ->
        add(offset)
        offset += value
    }
}
