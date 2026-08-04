package com.neonote.engine

import com.neonote.model.TableCellAddress

/** Rectangular selection constrained to one concrete table path. */
public data class TableSelection(
    val anchor: TableCellAddress,
    val focus: TableCellAddress = anchor,
) {
    init {
        require(anchor.tablePathKey() == focus.tablePathKey()) {
            "A table selection cannot cross nested table boundaries"
        }
    }

    public val firstRow: Int get() = minOf(anchor.path.last().rowIndex, focus.path.last().rowIndex)
    public val lastRow: Int get() = maxOf(anchor.path.last().rowIndex, focus.path.last().rowIndex)
    public val firstColumn: Int get() = minOf(anchor.path.last().columnIndex, focus.path.last().columnIndex)
    public val lastColumn: Int get() = maxOf(anchor.path.last().columnIndex, focus.path.last().columnIndex)
    public val rowCount: Int get() = lastRow - firstRow + 1
    public val columnCount: Int get() = lastColumn - firstColumn + 1
    public val isSingleCell: Boolean get() = rowCount == 1 && columnCount == 1

    public fun contains(address: TableCellAddress): Boolean =
        address.tablePathKey() == anchor.tablePathKey() &&
            address.path.last().rowIndex in firstRow..lastRow &&
            address.path.last().columnIndex in firstColumn..lastColumn
}

private fun TableCellAddress.tablePathKey(): List<Any> = buildList {
    add(blockIndex)
    path.dropLast(1).forEach { step ->
        add(step.tableBlockIndex)
        add(step.rowIndex)
        add(step.columnIndex)
    }
    add(path.last().tableBlockIndex)
}
