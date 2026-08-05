package com.neonote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.neonote.engine.SpanAwareTableLayout
import com.neonote.engine.TableGridCellKey
import com.neonote.engine.gridColumnCount
import com.neonote.engine.normalizedGrid
import com.neonote.model.TableCell
import com.neonote.model.TableColumnWidthMode
import com.neonote.model.TableNode
import kotlin.math.roundToInt

@Composable
internal fun SpanAwareTableGrid(
    table: TableNode,
    modifier: Modifier = Modifier,
    cellContent: @Composable (rowIndex: Int, columnIndex: Int, cell: TableCell) -> Unit,
) {
    val normalized = table.normalizedGrid()
    val anchors = normalized.rows.flatMapIndexed { rowIndex, row ->
        row.mapIndexedNotNull { columnIndex, cell ->
            if (cell.mergedInto == null) TableGridCellKey(rowIndex, columnIndex) else null
        }
    }
    Layout(
        content = {
            anchors.forEach { key ->
                Box(Modifier.fillMaxSize()) {
                    cellContent(key.rowIndex, key.columnIndex, normalized.rows[key.rowIndex][key.columnIndex])
                }
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val columns = normalized.gridColumnCount()
        val columnWidths = List(columns) { columnIndex ->
            val policy = normalized.columnPolicies.getOrNull(columnIndex)
            val widthDp = when (policy?.mode) {
                TableColumnWidthMode.Manual -> policy.manualWidth ?: 120f
                else -> policy?.preferredWidth ?: 120f
            }.coerceIn(72f, 420f)
            (widthDp.dp.toPx()).roundToInt().coerceAtLeast(1)
        }
        val intrinsic = anchors.mapIndexed { index, key ->
            val cell = normalized.rows[key.rowIndex][key.columnIndex]
            val spanWidth = columnWidths.subList(
                key.columnIndex,
                (key.columnIndex + cell.columnSpan).coerceAtMost(columns),
            ).sum()
            key to measurables[index].minIntrinsicHeight(spanWidth)
        }.toMap()
        val plan = SpanAwareTableLayout.calculate(
            normalized,
            columnWidths,
            intrinsic,
            minimumRowHeight = 44.dp.roundToPx(),
        )
        val placeables = measurables.mapIndexed { index, measurable ->
            val placement = plan.placements[index]
            measurable.measure(Constraints.fixed(placement.width, placement.height))
        }
        layout(
            width = plan.width.coerceIn(constraints.minWidth, constraints.maxWidth),
            height = plan.height.coerceIn(constraints.minHeight, constraints.maxHeight),
        ) {
            placeables.forEachIndexed { index, placeable ->
                val placement = plan.placements[index]
                placeable.placeRelative(placement.x, placement.y)
            }
        }
    }
}
